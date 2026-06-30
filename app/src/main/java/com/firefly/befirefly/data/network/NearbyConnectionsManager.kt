package com.firefly.befirefly.data.network

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

class NearbyConnectionsManager(private val context: Context) {

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val strategy = Strategy.P2P_CLUSTER // M-to-N topology

    private val _connectedEndpoints = kotlinx.coroutines.flow.MutableStateFlow<Map<String, String>>(emptyMap())
    val connectedEndpoints: kotlinx.coroutines.flow.StateFlow<Map<String, String>> = _connectedEndpoints

    private val cloudGateway = MqttCloudGateway() // REAL GATEWAY
    private val cryptoManager = com.firefly.befirefly.data.crypto.CryptoManager(context)
    private val endpointSessions = java.util.concurrent.ConcurrentHashMap<String, ByteArray>() // EndpointId -> SharedSecret
    private val publicKeyToEndpointId = java.util.concurrent.ConcurrentHashMap<String, String>() // PublicKey -> EndpointId

    // E2E media encryption: derive the shared secret from MY key + the recipient's public key.
    // Same symmetric ECDH secret the recipient derives — no handshake required.
    private val mediaSecretCache = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
    private fun mediaSecretFor(otherPublicKey: String): ByteArray? {
        mediaSecretCache[otherPublicKey]?.let { return it }
        val kp = cryptoManager.getKeyPair() ?: return null
        return try {
            val secret = cryptoManager.deriveSharedSecret(kp.private, otherPublicKey)
            mediaSecretCache[otherPublicKey] = secret
            secret
        } catch (e: Exception) {
            Log.e("Nearby", "Failed to derive media secret for ${otherPublicKey.take(10)}...", e)
            null
        }
    }
    private var myUserId: String? = null
    
    @Volatile
    private var amIOnline: Boolean = false // Track my own status

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val networkMonitor = NetworkMonitor(context)
    
    // Offline Queue
    private var pendingMessageDao: com.firefly.befirefly.data.local.dao.PendingMessageDao? = null
    
    fun setDatabase(dao: com.firefly.befirefly.data.local.dao.PendingMessageDao) {
        this.pendingMessageDao = dao
    }

    // Track discovered users who have completed handshake: Map<PublicKey, Username>
    private val _discoveredUsers = kotlinx.coroutines.flow.MutableStateFlow<Map<String, String>>(emptyMap())
    val discoveredUsers: kotlinx.coroutines.flow.StateFlow<Map<String, String>> = _discoveredUsers

    // Debug States
    private val _isAdvertising = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isAdvertising: kotlinx.coroutines.flow.StateFlow<Boolean> = _isAdvertising

    private val _isDiscovering = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isDiscovering: kotlinx.coroutines.flow.StateFlow<Boolean> = _isDiscovering

    private val _lastError = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val lastError: kotlinx.coroutines.flow.StateFlow<String?> = _lastError

    val isCloudConnected: kotlinx.coroutines.flow.Flow<Boolean> = cloudGateway.isConnected

    private fun _isConnectedToCloud(): Boolean = cloudGateway.isConnected.value

    init {
        try {
            // Network callback registration can fail on some devices if permissions aren't ready
            scope.launch {
                try {
                    networkMonitor.isWifiConnected.collect { isConnected ->
                        Log.d("Nearby", "Network Status Changed: $isConnected")
                        val wasOffline = !amIOnline
                        amIOnline = isConnected
                        // If we just came online, try to flush the queue via cloud
                        if (isConnected && wasOffline) {
                            flushPendingQueue()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Nearby", "Error monitoring network", e)
                }
            }
        } catch (e: Exception) {
             Log.e("Nearby", "Fatal error in NearbyConnectionsManager init", e)
        }
    }

    // Track the cloud collector job so we can cancel it on re-call
    private var cloudCollectorJob: kotlinx.coroutines.Job? = null

    fun setMyIdentity(userId: String) {
        this.myUserId = userId
        Log.d("Nearby", "setMyIdentity called. userId=${userId.take(10)}... amIOnline=$amIOnline")

        // Subscribe for cloud messages (gateway handles connect timing internally)
        cloudGateway.subscribeForUser(userId)

        // Cancel any previous cloud collector to prevent duplicate message processing
        cloudCollectorJob?.cancel()
        cloudCollectorJob = scope.launch {
            try {
                // Collect errors from cloud gateway
                launch {
                    cloudGateway.lastError.collect { error ->
                        if (error != null) {
                            _lastError.value = error
                        }
                    }
                }

                Log.d("Nearby", "Collecting cloud messages for ${userId.take(10)}...")
                cloudGateway.incomingMessages().collect { packet ->
                    // DEDUP: Apply the same packetCache check as P2P
                    if (packetCache.get(packet.id) == true) {
                        Log.v("Nearby", "☁️ Dropping duplicate cloud packet: ${packet.id}")
                        return@collect
                    }
                    packetCache.put(packet.id, true)

                    Log.d("Nearby", "☁️ Received Cloud Packet! type=${packet.type} from=${packet.senderId.take(10)}... to=${packet.receiverId.take(10)}...")

                    // ✨ Dabbawala Logic ✨
                    if (packet.receiverId == myUserId) {
                        // It's for me – send to UI
                        _packetFlow.emit(packet)
                        Log.d("Nearby", "☁️ Forwarded to _packetFlow (UI)")
                    } else if (packet.ttl > 0) {
                        // It's for someone else – relay into BLE mesh
                        Log.d("Nearby", "🔁 Relaying cloud packet into mesh (TTL=${packet.ttl})")
                        relayPacket(packet, excludeEndpointId = null)
                    } else {
                        Log.w("Nearby", "☁️ Packet for other user but TTL=0. Dropping.")
                    }
                }
            } catch (e: Exception) {
                Log.e("Nearby", "Error collecting cloud messages", e)
                _lastError.value = "Cloud Listen Error: ${e.message}"
            }
        }
    }
    
    // ...

    fun sendPayload(receiverPublicKey: String, packet: NetworkPacket) {
        val endpointId = publicKeyToEndpointId[receiverPublicKey]
        if (endpointId != null) {
            // Direct P2P Connection Exists.
            // Payload is already end-to-end encrypted for the final recipient (done in the ViewModel).
            // The transport just moves the opaque blob — no hop-by-hop encryption.
            Log.d("Nearby", "📡 P2P endpoint found for ${receiverPublicKey.take(10)}... Sending directly.")
            sendRawPacket(endpointId, packet)
        } else {
            val cloudConnected = cloudGateway.isConnected.value
            Log.d("Nearby", "No P2P for ${receiverPublicKey.take(10)}... amIOnline=$amIOnline cloudConnected=$cloudConnected")
            
            // Use cloud if MQTT is connected (more reliable than amIOnline flag)
            if (cloudConnected) {
                Log.d("Nearby", "📤 Sending via Cloud Gateway to ${receiverPublicKey.take(10)}...")
                cloudGateway.sendToCloud(packet)
            } else if (amIOnline) {
                // NetworkMonitor says online but cloud not connected — try anyway
                Log.w("Nearby", "⚠️ Cloud not connected but network says online. Trying cloud anyway...")
                cloudGateway.sendToCloud(packet)
            } else {
                // Queue for later delivery
                Log.w("Nearby", "❌ Cannot send: No P2P and No Cloud. Queuing message.")
                queuePendingMessage(receiverPublicKey, packet)
            }
        }
    }

    fun sendFilePayload(receiverPublicKey: String, uri: android.net.Uri, fileName: String, mimeType: String, size: Long, packetId: String = java.util.UUID.randomUUID().toString(), packetType: PacketType = PacketType.FILE) {
        // Unified encrypted media path: chunk the file, encrypt each chunk for the final
        // recipient, and let sendPayload route each chunk over P2P / relay / cloud.
        // This works identically everywhere and never puts plaintext media on the wire.
        val secret = mediaSecretFor(receiverPublicKey)
        if (secret == null) {
            Log.e("Nearby", "❌ Cannot send media: no E2E secret for ${receiverPublicKey.take(10)}...")
            return
        }

        scope.launch {
            try {
                var chunkCount = 0
                com.firefly.befirefly.utils.FileChunker.streamFile(context, uri).collect { chunkPayload ->
                    // chunkPayload = "index|total|fileName|mimeType|fileSize:base64data"
                    val sealed = com.firefly.befirefly.utils.SecurityManager.encrypt(chunkPayload, secret)
                    if (sealed == null) {
                        Log.e("Nearby", "❌ Failed to encrypt media chunk $chunkCount of $fileName")
                        return@collect
                    }
                    val packet = NetworkPacket(
                        id = "$packetId-$chunkCount",
                        senderId = myUserId ?: "unknown",
                        receiverId = receiverPublicKey,
                        type = packetType, // IMAGE / AUDIO / FILE — drives receiver handling
                        encryptedPayload = sealed,
                        encryptionType = "E2E-AES-GCM"
                        // fileName/mimeType intentionally omitted from the packet — they travel
                        // encrypted inside the chunk header, so relays/broker can't read them.
                    )
                    sendPayload(receiverPublicKey, packet)
                    chunkCount++
                    kotlinx.coroutines.delay(40) // Throttle so we don't overrun MQTT / the link
                }
                Log.d("Nearby", "📤 ${packetType.name} sent encrypted in $chunkCount chunks: $fileName")
            } catch (e: Exception) {
                Log.e("Nearby", "❌ Failed to send encrypted ${packetType.name}: $fileName", e)
            }
        }
    }

    private fun queuePendingMessage(receiverId: String, packet: NetworkPacket) {
        val dao = pendingMessageDao ?: run {
            Log.w("Nearby", "PendingMessageDao not set, message will be lost")
            return
        }
        scope.launch {
            try {
                val json = PacketSerializer.serialize(packet)
                dao.insert(
                    com.firefly.befirefly.data.local.entity.PendingMessageEntity(
                        receiverId = receiverId,
                        packetJson = json
                    )
                )
                Log.d("Nearby", "Message queued for ${receiverId.take(10)}... (count: ${dao.getPendingCount()})")
            } catch (e: Exception) {
                Log.e("Nearby", "Failed to queue message", e)
            }
        }
    }

    private fun flushPendingQueue() {
        val dao = pendingMessageDao ?: return
        scope.launch {
            try {
                dao.deleteExpired()
                val pending = dao.getAll()
                if (pending.isEmpty()) return@launch
                Log.d("Nearby", "Flushing ${pending.size} pending messages...")
                
                for (msg in pending) {
                    try {
                        val packet = PacketSerializer.deserialize(msg.packetJson)
                        val endpointId = publicKeyToEndpointId[msg.receiverId]
                        
                        if (endpointId != null) {
                            // Peer is now connected via P2P! Packet is already E2E-encrypted.
                            sendRawPacket(endpointId, packet)
                            dao.deleteById(msg.id)
                            Log.d("Nearby", "Flushed queued message to ${msg.receiverId.take(10)}... via P2P")
                        } else if (amIOnline) {
                            // Send via cloud
                            cloudGateway.sendToCloud(packet)
                            dao.deleteById(msg.id)
                            Log.d("Nearby", "Flushed queued message to ${msg.receiverId.take(10)}... via Cloud")
                        } else {
                            // Still can't send, increment retry
                            dao.incrementRetryCount(msg.id)
                        }
                    } catch (e: Exception) {
                        Log.e("Nearby", "Error flushing message ${msg.id}", e)
                        dao.incrementRetryCount(msg.id)
                    }
                }
            } catch (e: Exception) {
                Log.e("Nearby", "Error flushing pending queue", e)
            }
        }
    }

    // Callback for connection lifecycle
    var onEndpointConnected: ((String, String) -> Unit)? = null

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Auto-accept (Insecure for now, but standard for mesh discoverability)
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d("Nearby", "Connected to $endpointId")
                    // Initiate Handshake
                    initiateHandshake(endpointId)
                    // New: Invoke callback
                    onEndpointConnected?.invoke(endpointId, "New Peer")
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.d("Nearby", "Connection rejected by $endpointId")
                }
                else -> {
                    Log.d("Nearby", "Connection failed with $endpointId")
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d("Nearby", "Disconnected from $endpointId")
            _connectedEndpoints.value = _connectedEndpoints.value - endpointId
            endpointSessions.remove(endpointId)
            
            // Remove from publicKey map
            val keysToRemove = publicKeyToEndpointId.entries.filter { it.value == endpointId }.map { it.key }
            keysToRemove.forEach { publicKeyToEndpointId.remove(it) }
        }
    }

    private val _packetFlow = kotlinx.coroutines.flow.MutableSharedFlow<NetworkPacket>(replay = 0, extraBufferCapacity = 64)
    val packetFlow: kotlinx.coroutines.flow.SharedFlow<NetworkPacket> = _packetFlow

    private val packetCache = android.util.LruCache<String, Boolean>(5000)

    // Maps Nearby payloadId to NetworkPacket metadata
    private val incomingFiles = mutableMapOf<Long, NetworkPacket>()

    // Callback for receiving payloads (messages)
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val json = String(payload.asBytes()!!, StandardCharsets.UTF_8)
                try {
                    val packet = PacketSerializer.deserialize(json)
                    
                    // 1. Deduplication: Check if we've seen this packet
                    if (packetCache.get(packet.id) == true) {
                        Log.v("Nearby", "Dropping duplicate packet: ${packet.id}")
                        return
                    }
                    packetCache.put(packet.id, true)
                    
                    Log.d("Nearby", "Received packet from $endpointId type: ${packet.type} TTL: ${packet.ttl}")
                    
                    // 2. TTL Check
                    if (packet.ttl <= 0) {
                        Log.w("Nearby", "Dropping packet ${packet.id} due to expired TTL")
                        return
                    }

                    when (packet.type) {
                        PacketType.HANDSHAKE_INIT -> {
                             val theirKey = packet.payload
                             if (theirKey != null) handleHandshake(endpointId, theirKey, false)
                        }
                        PacketType.HANDSHAKE_ACK -> {
                             val theirKey = packet.payload
                             if (theirKey != null) handleHandshake(endpointId, theirKey, true)
                        }
                        PacketType.TEXT, PacketType.IMAGE, PacketType.AUDIO, PacketType.FILE, PacketType.GROUP_MESSAGE, PacketType.GROUP_INVITE, PacketType.REACTION, PacketType.EDIT, PacketType.DELETE, PacketType.DISAPPEARING -> {
                            // End-to-end encrypted: the payload is sealed for the FINAL recipient.
                            // A relay must NEVER decrypt — it can only route the opaque blob.
                            // Media arrives as a series of encrypted chunks of this same type.
                            if (packet.receiverId == myUserId) {
                                // It's for me — hand it to the ViewModel, which holds the key to decrypt.
                                scope.launch { _packetFlow.emit(packet) }
                            } else {
                                // Not for me — forward it onward, untouched.
                                relayPacket(packet, excludeEndpointId = endpointId)
                            }
                        }
                        else -> {
                           // Relay logic or other packets
                           if (packet.receiverId != myUserId && packet.isRelay) {
                               if (amIOnline) cloudGateway.sendToCloud(packet)
                           } else {
                               scope.launch { _packetFlow.emit(packet) }
                           }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Nearby", "Failed to deserialize packet", e)
                }
            } else if (payload.type == Payload.Type.FILE) {
                // The actual file stream has started downloading to a temp file
                Log.d("Nearby", "📥 Incoming FILE stream started! PayloadId: ${payload.id}")
                
                // We MUST store the payload object to get its URI later when the transfer finishes
                val metadata = incomingFiles[payload.id]
                if (metadata != null) {
                    val uri = payload.asFile()?.asUri()
                    if (uri != null) {
                        incomingFiles[payload.id] = metadata.copy(payload = uri.toString())
                    }
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val payloadId = update.payloadId
            when (update.status) {
                PayloadTransferUpdate.Status.IN_PROGRESS -> {
                    // Could emit progress events here
                    val progress = if (update.totalBytes > 0) (update.bytesTransferred * 100 / update.totalBytes).toInt() else 0
                    if (progress % 10 == 0) Log.v("Nearby", "File $payloadId transfer: $progress%")
                }
                PayloadTransferUpdate.Status.SUCCESS -> {
                    Log.d("Nearby", "✅ Payload $payloadId transfer SUCCESS!")
                    val metadata = incomingFiles[payloadId]
                    if (metadata != null) {
                        // Emit the packet with the URI string we saved in `payload`
                        scope.launch {
                            _packetFlow.emit(metadata.copy(transferProgress = 100))
                        }
                        incomingFiles.remove(payloadId)
                    }
                }
                PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED -> {
                    Log.e("Nearby", "❌ Payload $payloadId transfer FAILED")
                    incomingFiles.remove(payloadId)
                }
            }
        }
    }
    
    // ... (existing code)


    
    // ...

    private fun initiateHandshake(endpointId: String, isAck: Boolean = false) {
        val myPublicKey = cryptoManager.getPublicKeyString()
        val myName = cryptoManager.getUsername() ?: "Firefly User"
        
        if (myPublicKey != null) {
            // New Protocol: Send JSON { key, name }
            val payloadObj = com.google.gson.JsonObject()
            payloadObj.addProperty("key", myPublicKey)
            payloadObj.addProperty("name", myName)
            val payloadStr = payloadObj.toString()

            val packet = NetworkPacket(
                senderId = myUserId ?: "unknown",
                receiverId = "handshake",
                type = if (isAck) PacketType.HANDSHAKE_ACK else PacketType.HANDSHAKE_INIT,
                payload = payloadStr
            )
            sendRawPacket(endpointId, packet)
        }
    }

    private fun handleHandshake(endpointId: String, otherPublicKey: String, isAck: Boolean) {
        val myKeyPair = cryptoManager.getKeyPair()
        if (myKeyPair != null) {
             try {
                 Log.d("Nearby", "Handling Handshake from $endpointId. Payload length: ${otherPublicKey.length}")
                 
                 // Protocol update: Handshake payload should be JSON { key, name }
                 var otherName = "Unknown Peer"
                 var finalKey = otherPublicKey
                 
                 try {
                     val json = com.google.gson.JsonParser.parseString(otherPublicKey).asJsonObject
                     if (json.has("key") && json.has("name")) {
                         finalKey = json.get("key").asString
                         otherName = json.get("name").asString
                         Log.d("Nearby", "Parsed Handshake JSON. Name: $otherName")
                     } else {
                         Log.w("Nearby", "Handshake JSON missing fields. Fallback to raw key.")
                     }
                 } catch (e: Exception) {
                     Log.w("Nearby", "Handshake payload is not JSON. Assuming raw key. Error: ${e.message}")
                 }

                 val secret = cryptoManager.deriveSharedSecret(myKeyPair.private, finalKey)
                 endpointSessions[endpointId] = secret
                 publicKeyToEndpointId[finalKey] = endpointId
                 Log.d("Nearby", "Secure Session Established with $endpointId ($otherName).")

                 // Update Discovered Users Flow
                 val currentMap = _discoveredUsers.value.toMutableMap()
                 currentMap[finalKey] = otherName
                 _discoveredUsers.value = currentMap
                 
                 Log.d("Nearby", "DiscoveredUsers updated. Count: ${_discoveredUsers.value.size}")
                 
                 onEndpointConnected?.invoke(endpointId, otherName)
                 
                 if (!isAck) {
                     // Respond with ACK
                     Log.d("Nearby", "Sending Handshake ACK to $endpointId")
                     initiateHandshake(endpointId, isAck = true) // Use helper
                 }

                 // Flush any pending messages for this newly connected peer
                 flushPendingQueue()
             } catch (e: Exception) {
                 Log.e("Nearby", "Handshake failed unexpectedly", e)
             }
        } else {
            Log.e("Nearby", "Cannot handle handshake: My KeyPair is null")
        }
    }

    // Sends a packet over a P2P link. Payloads are already end-to-end encrypted upstream
    // (in the ViewModel), so the transport only serializes and ships the bytes.
    private fun sendRawPacket(endpointId: String, packet: NetworkPacket) {
        try {
            val json = PacketSerializer.serialize(packet)
            val bytes = json.toByteArray(StandardCharsets.UTF_8)
            // Google Nearby rejects BYTES payloads larger than 32 KB. Guard so an oversized
            // packet logs instead of throwing and crashing the app.
            if (bytes.size > 32 * 1024) {
                Log.e("Nearby", "❌ Packet too large for Nearby BYTES (${bytes.size} bytes), dropping ${packet.id}")
                return
            }
            connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes))
        } catch (e: Exception) {
            Log.e("Nearby", "Failed to send raw packet ${packet.id}", e)
        }
    }


    // Callback for discovery
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d("Nearby", "Endpoint found: $endpointId (${info.endpointName})")
            
            // Try to extract username and publicKey from endpointName JSON
            try {
                val json = org.json.JSONObject(info.endpointName)
                val name = json.optString("n", info.endpointName)
                val key = json.optString("k", "")
                
                if (key.isNotBlank()) {
                    Log.d("Nearby", "Instant discovery via JSON! Found $name ($key)")
                    // Add to discovered users immediately!
                    val currentMap = _discoveredUsers.value.toMutableMap()
                    currentMap[key] = name
                    _discoveredUsers.value = currentMap
                }
            } catch (e: Exception) {
                // Not our JSON format, ignore
                Log.d("Nearby", "Endpoint name is not JSON: ${info.endpointName}")
            }
            
            // Automatically request connection for full handshake
            connectionsClient.requestConnection(
                myUserId ?: "unknown",
                endpointId,
                connectionLifecycleCallback
            ).addOnSuccessListener {
                Log.d("Nearby", "Connection requested to $endpointId")
            }.addOnFailureListener {
                Log.e("Nearby", "Failed to request connection", it)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d("Nearby", "Endpoint lost: $endpointId")
        }
    }

    fun startAdvertising(username: String, isWifi: Boolean) {
        // Check if Location Services (GPS) is enabled — required by Nearby Connections
        val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager
        val isGpsEnabled = locationManager?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true
        val isNetworkLocationEnabled = locationManager?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
        
        if (!isGpsEnabled && !isNetworkLocationEnabled) {
            Log.e("Nearby", "Location Services (GPS) is OFF. Nearby Connections requires GPS to be enabled.")
            _isAdvertising.value = false
            _lastError.value = "GPS/Location is OFF. Turn on Location in device settings."
            return
        }
        
        // Verify runtime permissions
        val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasCoarse && !hasFine) {
            Log.e("Nearby", "No location permission granted at runtime!")
            _isAdvertising.value = false
            _lastError.value = "Location permission not granted"
            return
        }
        
        // Stop previous session to ensure clean state
        connectionsClient.stopAdvertising()
        
        // Use P2P_CLUSTER for mesh
        val options = AdvertisingOptions.Builder().setStrategy(strategy).build()
        
        // Encode publicKey and username into endpointName for instant discovery!
        val encodedName = try {
            val json = org.json.JSONObject()
            json.put("n", username)
            json.put("k", myUserId ?: "")
            json.toString()
        } catch (e: Exception) {
            username // fallback
        }
        
        try {
            connectionsClient.startAdvertising(
                encodedName,
                context.packageName,
                connectionLifecycleCallback,
                options
            ).addOnSuccessListener {
                Log.d("Nearby", "Started advertising as $username (Success). amIOnline=$amIOnline")
                // Note: amIOnline is managed by NetworkMonitor collector — don't override here
                _isAdvertising.value = true
                _lastError.value = null
            }.addOnFailureListener {
                Log.e("Nearby", "Failed to start advertising: ${it.message}", it)
                _isAdvertising.value = false
                _lastError.value = "Adv Fail: ${it.message}"
            }
        } catch (e: SecurityException) {
            Log.e("Nearby", "SecurityException: Missing permissions for Advertising", e)
            _isAdvertising.value = false
            _lastError.value = "Adv Sec Error: ${e.message}"
        } catch (e: Exception) {
            Log.e("Nearby", "Exception in startAdvertising", e)
            _isAdvertising.value = false
            _lastError.value = "Adv Error: ${e.message}"
        }
    }

    fun startDiscovery(username: String, isWifi: Boolean) {
        // Check if Location Services (GPS) is enabled
        val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager
        val isGpsEnabled = locationManager?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true
        val isNetworkLocationEnabled = locationManager?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
        
        if (!isGpsEnabled && !isNetworkLocationEnabled) {
            Log.e("Nearby", "Location Services (GPS) is OFF. Discovery requires GPS.")
            _isDiscovering.value = false
            _lastError.value = "GPS/Location is OFF. Turn on Location in device settings."
            return
        }
        
        // Verify runtime permissions
        val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasCoarse && !hasFine) {
            Log.e("Nearby", "No location permission granted at runtime!")
            _isDiscovering.value = false
            _lastError.value = "Location permission not granted"
            return
        }
        
        // Stop previous discovery
        connectionsClient.stopDiscovery()
        
        val options = DiscoveryOptions.Builder().setStrategy(strategy).build()
        try {
            connectionsClient.startDiscovery(
                context.packageName,
                endpointDiscoveryCallback,
                options
            ).addOnSuccessListener {
                Log.d("Nearby", "Started discovery (Success)")
                _isDiscovering.value = true
                _lastError.value = null
            }.addOnFailureListener {
                Log.e("Nearby", "Failed to start discovery: ${it.message}", it)
                _isDiscovering.value = false
                _lastError.value = "Disc Fail: ${it.message}"
            }
        } catch (e: SecurityException) {
             Log.e("Nearby", "SecurityException: Missing permissions for Discovery", e)
             _isDiscovering.value = false
             _lastError.value = "Disc Sec Error: ${e.message}"
        } catch (e: Exception) {
            Log.e("Nearby", "Exception in startDiscovery", e)
            _isDiscovering.value = false
            _lastError.value = "Disc Error: ${e.message}"
        }
    }

    fun stopAll() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        _connectedEndpoints.value = emptyMap()
        endpointSessions.clear()
        _isAdvertising.value = false
        _isDiscovering.value = false
    }

    private fun relayPacket(packet: NetworkPacket, excludeEndpointId: String?) {
        val newTtl = packet.ttl - 1
        if (newTtl <= 0) {
            Log.w("Nearby", "Not relaying ${packet.id}: TTL expired")
            return
        }

        // Forward the SAME end-to-end encrypted packet. A relay never sees plaintext —
        // it only decrements the TTL and passes the sealed blob along.
        val relayed = packet.copy(ttl = newTtl, isRelay = true)
        Log.d("Nearby", "🔁 Relaying ${packet.id} (TTL=$newTtl) untouched to neighbors")

        val neighbors = endpointSessions.keys()
        while (neighbors.hasMoreElements()) {
            val endpointId = neighbors.nextElement()
            if (endpointId == excludeEndpointId) continue
            sendRawPacket(endpointId, relayed)
        }

        // If we have internet, also bridge the (still-encrypted) packet into the cloud
        // toward its destination.
        if (amIOnline) {
            cloudGateway.sendToCloud(relayed)
        }
    }
}
