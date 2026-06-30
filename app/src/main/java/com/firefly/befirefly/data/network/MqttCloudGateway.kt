package com.firefly.befirefly.data.network

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.nio.charset.StandardCharsets
import java.util.UUID

class MqttCloudGateway : CloudGateway {

    private var client: MqttAsyncClient? = null
    private val BROKER_URI = "tcp://broker.emqx.io:1883"
    private val TOPIC_PREFIX = "befirefly/users/"
    private val TAG = "MqttGateway"
    private val MAX_SAFE_PAYLOAD = 500 * 1024 // 500KB — warn if exceeding
    private val MAX_RETRIES = 3
    private var reconnectDelay = 5000L // Exponential backoff starting at 5s
    
    // Connection State
    private val _isConnected = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isConnected: kotlinx.coroutines.flow.StateFlow<Boolean> = _isConnected

    private val _lastError = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val lastError: kotlinx.coroutines.flow.StateFlow<String?> = _lastError

    // Simple callback-based message delivery (no more callbackFlow)
    private val _incomingPackets = MutableSharedFlow<NetworkPacket>(replay = 1, extraBufferCapacity = 64)
    
    // Track what topic we're subscribed to
    private var subscribedTopic: String? = null
    private val pendingSubscribeTopics = mutableSetOf<String>()

    @Volatile private var initialized = false

    /**
     * Creates + connects the MQTT client using a STABLE per-user client id. Combined with a
     * persistent session (cleanSession = false), the broker keeps the subscription alive and
     * QUEUES QoS-1 messages while we're offline, delivering them on reconnect (store-and-forward).
     */
    @Synchronized
    private fun initClientFor(userId: String) {
        if (initialized) return
        initialized = true
        try {
            val clientId = "bf-" + Integer.toHexString(userId.hashCode())
            Log.d(TAG, "Initializing Paho MQTT. Broker=$BROKER_URI clientId=$clientId")

            client = MqttAsyncClient(BROKER_URI, clientId, MemoryPersistence())
            
            client?.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    Log.d(TAG, "✅ MQTT Connected! reconnect=$reconnect server=$serverURI")
                    _isConnected.value = true
                    _lastError.value = null
                    reconnectDelay = 5000L // Reset backoff on successful connect
                    
                    // Auto-subscribe to all pending topics on connect/reconnect
                    synchronized(pendingSubscribeTopics) {
                        pendingSubscribeTopics.forEach { topic ->
                            Log.d(TAG, "🔄 Auto-subscribing to pending topic: $topic")
                            doSubscribe(topic)
                        }
                        // Keep them in pending so they auto-resubscribe on future reconnects too
                    }
                }

                override fun connectionLost(cause: Throwable?) {
                    Log.w(TAG, "❌ MQTT connection lost: ${cause?.message}")
                    _isConnected.value = false
                    _lastError.value = "MQTT Lost: ${cause?.message}"
                    subscribedTopic = null
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    // Handle ALL incoming messages here (simpler than per-subscribe callbacks)
                    if (message == null) return
                    try {
                        val json = String(message.payload, StandardCharsets.UTF_8)
                        val packet = PacketSerializer.deserialize(json)
                        Log.d(TAG, "☁️ MESSAGE ARRIVED on topic=$topic type=${packet.type} from=${packet.senderId.take(10)}... to=${packet.receiverId.take(10)}...")
                        
                        val sent = _incomingPackets.tryEmit(packet)
                        if (!sent) {
                            Log.e(TAG, "⚠️ Failed to emit packet to flow (buffer full?)")
                        } else {
                            Log.d(TAG, "✅ Packet emitted to flow successfully")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse MQTT message", e)
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    Log.d(TAG, "📬 Delivery complete")
                }
            })
            
            connect()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MQTT Client", e)
            _lastError.value = "MQTT Init Fail: ${e.message}"
        }
    }

    private fun connect() {
        try {
            val options = MqttConnectOptions().apply {
                isCleanSession = false // Persistent session → broker queues messages while offline
                keepAliveInterval = 60
                isAutomaticReconnect = true
                connectionTimeout = 15
                maxInflight = 1000 // Huge buffer to support massive file chunk streaming
            }
            
            Log.d(TAG, "🔌 Connecting to MQTT broker...")
            client?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "✅ MQTT connect onSuccess!")
                    // connectComplete callback will be called by Paho
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "❌ MQTT connect FAILED: ${exception?.message}", exception)
                    _isConnected.value = false
                    _lastError.value = "MQTT Conn Fail: ${exception?.message}"
                    
                    // Retry with exponential backoff (5s → 10s → 20s → max 60s)
                    val delay = reconnectDelay
                    reconnectDelay = (reconnectDelay * 2).coerceAtMost(60000)
                    Log.d(TAG, "🔄 Retrying MQTT connection in ${delay/1000}s...")
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        Log.d(TAG, "🔄 Retrying MQTT connection...")
                        connect()
                    }, delay)
                }
            })
        } catch (e: MqttException) {
            Log.e(TAG, "MQTT connect exception: ${e.message}", e)
            _isConnected.value = false
            _lastError.value = "MQTT Ex: ${e.message}"
        }
    }

    private fun doSubscribe(topic: String) {
        val mqttClient = client ?: return
        if (!mqttClient.isConnected) {
            Log.w(TAG, "Cannot subscribe yet — not connected. Will retry on reconnect.")
            return
        }
        
        try {
            Log.d(TAG, "📥 Subscribing to: $topic ...")
            mqttClient.subscribe(topic, 1, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    subscribedTopic = topic
                    Log.d(TAG, "✅ SUBSCRIBED to $topic — ready to receive messages!")
                    if (_lastError.value?.contains("Sub") == true) {
                        _lastError.value = null
                    }
                }
                
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "❌ SUBSCRIBE FAILED for $topic: ${exception?.message}", exception)
                    _lastError.value = "Sub Fail: ${exception?.message}"
                    
                    // Retry subscribe after 3 seconds
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        Log.d(TAG, "🔄 Retrying subscribe to $topic")
                        doSubscribe(topic)
                    }, 3000)
                }
            })
        } catch (e: MqttException) {
            Log.e(TAG, "Subscribe exception: ${e.message}", e)
            _lastError.value = "Sub Ex: ${e.message}"
        }
    }

    private fun sanitizeTopicId(id: String): String {
        return id.replace("+", "-")
                 .replace("/", "_")
                 .replace("=", "")
    }

    // Called by NearbyConnectionsManager to start listening
    fun subscribeForUser(userId: String) {
        // Lazily create + connect the client with a stable id tied to this identity.
        initClientFor(userId)

        val safeUserId = sanitizeTopicId(userId)
        val topic = "$TOPIC_PREFIX$safeUserId"
        
        synchronized(pendingSubscribeTopics) {
            pendingSubscribeTopics.add(topic)
        }
        
        Log.d(TAG, "📋 subscribeForUser: userId=${userId.take(10)}... topic=$topic")
        
        val mqttClient = client
        if (mqttClient != null && mqttClient.isConnected) {
            doSubscribe(topic)
        } else {
            Log.d(TAG, "⏳ MQTT not connected yet. Will subscribe when connected (via connectComplete callback)")
        }
    }

    // Get incoming messages as a Flow (collected by NearbyConnectionsManager)
    fun incomingMessages(): Flow<NetworkPacket> = _incomingPackets

    override fun sendToCloud(packet: NetworkPacket) {
        sendToCloudWithRetry(packet, attempt = 1)
    }

    private fun sendToCloudWithRetry(packet: NetworkPacket, attempt: Int) {
        val mqttClient = client ?: run {
            Log.e(TAG, "❌ Cannot send — client is null!")
            _lastError.value = "Send fail: client null"
            return
        }
        
        if (!mqttClient.isConnected) {
            Log.e(TAG, "❌ Cannot send — not connected! Attempting reconnect...")
            _lastError.value = "Send fail: not connected"
            connect()
            return
        }
        
        val safeReceiverId = sanitizeTopicId(packet.receiverId)
        val topic = "$TOPIC_PREFIX$safeReceiverId"
        val payloadStr = PacketSerializer.serialize(packet)
        val payloadBytes = payloadStr.toByteArray(StandardCharsets.UTF_8)
        
        // Payload size warning
        if (payloadBytes.size > MAX_SAFE_PAYLOAD) {
            Log.w(TAG, "⚠️ Large MQTT payload: ${payloadBytes.size / 1024}KB (max safe: ${MAX_SAFE_PAYLOAD / 1024}KB) type=${packet.type}")
        }
        
        Log.d(TAG, "📤 Publishing to $topic (${payloadBytes.size / 1024}KB) type=${packet.type} attempt=$attempt/${MAX_RETRIES}")
        
        try {
            val message = MqttMessage(payloadBytes).apply {
                qos = 1
                isRetained = false
            }
            
            mqttClient.publish(topic, message, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "✅ Published successfully to $topic (${payloadBytes.size / 1024}KB)")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "❌ Failed to publish to $topic (attempt $attempt): ${exception?.message}", exception)
                    
                    if (attempt < MAX_RETRIES) {
                        val retryDelay = (attempt * 1000).toLong() // 1s, 2s, 3s
                        Log.d(TAG, "🔄 Retrying publish in ${retryDelay}ms (attempt ${attempt + 1}/$MAX_RETRIES)")
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            sendToCloudWithRetry(packet, attempt + 1)
                        }, retryDelay)
                    } else {
                        Log.e(TAG, "❌ PERMANENTLY FAILED to publish after $MAX_RETRIES attempts")
                        _lastError.value = "Pub Fail after $MAX_RETRIES retries: ${exception?.message}"
                    }
                }
            })
        } catch (e: MqttException) {
            Log.e(TAG, "Publish exception: ${e.message}", e)
            if (attempt < MAX_RETRIES) {
                val retryDelay = (attempt * 1000).toLong()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    sendToCloudWithRetry(packet, attempt + 1)
                }, retryDelay)
            } else {
                _lastError.value = "Pub Ex after retries: ${e.message}"
            }
        }
    }

    // Legacy interface method — now just returns the flow
    override fun listenForMessages(userId: String): Flow<NetworkPacket> {
        subscribeForUser(userId)
        return _incomingPackets
    }
}
