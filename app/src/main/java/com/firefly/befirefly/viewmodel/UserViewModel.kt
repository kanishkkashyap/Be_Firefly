package com.firefly.befirefly.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.map
import com.firefly.befirefly.data.CryptoWallet
import com.firefly.befirefly.data.crypto.CryptoManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import com.firefly.befirefly.data.network.NetworkPacket
import com.firefly.befirefly.data.network.PacketType
import com.firefly.befirefly.data.network.GroupInvitePayload
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.KeyPair
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import com.firefly.befirefly.data.local.entity.ContactEntity
import com.firefly.befirefly.utils.FileChunker

class UserViewModel(application: Application) : AndroidViewModel(application) {

    // Service Binding
    private var meshService: com.firefly.befirefly.data.service.MeshService? = null

    var nearbyManager: com.firefly.befirefly.data.network.NearbyConnectionsManager? by mutableStateOf(null)
        private set

    private val cryptoManager = CryptoManager(application)
    // NetworkMonitor: reuse from NearbyConnectionsManager if available, else create one for initial state
    private val networkMonitor = com.firefly.befirefly.data.network.NetworkMonitor(application)

    // Database & Repository
    private val database = com.firefly.befirefly.data.local.AppDatabase.getDatabase(application)
    private val repository = com.firefly.befirefly.data.repository.ChatRepository(
        database.messageDao(),
        database.contactDao(),
        database.groupDao(),
        database.statusDao()
    )

    // In-memory state
    var wallet by mutableStateOf<CryptoWallet?>(null)
        private set

    var username by mutableStateOf("")
        private set

    var currentConversationId by mutableStateOf<String?>(null)
        private set

    var isWifiConnected by mutableStateOf(false)
        private set

    var profilePictureUri by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            networkMonitor.isWifiConnected.collect { connected ->
                isWifiConnected = connected
            }
        }
        // Periodically purge expired (disappearing) messages.
        viewModelScope.launch {
            try { com.firefly.befirefly.utils.FileReassembler.cleanupStale(getApplication()) } catch (_: Exception) {}
            while (true) {
                try { repository.deleteExpiredMessages() } catch (_: Exception) {}
                try { repository.deleteExpiredStatuses() } catch (_: Exception) {}
                kotlinx.coroutines.delay(15000)
            }
        }
    }

    // --- End-to-end encryption layer ---
    // ECDH is symmetric: the secret derived from (myPrivateKey + otherPublicKey) is identical
    // to the one the other party derives from (theirPrivateKey + myPublicKey). So we can seal a
    // message for the FINAL recipient with no handshake — and every relay only ever sees ciphertext.
    private val e2eSecretCache = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()

    private fun e2eSecretFor(otherPublicKey: String): ByteArray? {
        e2eSecretCache[otherPublicKey]?.let { return it }
        val priv = wallet?.privateKey ?: return null
        return try {
            val secret = cryptoManager.deriveSharedSecret(priv, otherPublicKey)
            e2eSecretCache[otherPublicKey] = secret
            secret
        } catch (e: Exception) {
            android.util.Log.e("UserViewModel", "Failed to derive E2E secret for ${otherPublicKey.take(10)}...", e)
            null
        }
    }

    /** Seal plaintext for the given recipient. Returns null if the secret can't be derived. */
    private fun encryptFor(recipientPublicKey: String, plaintext: String): String? {
        val secret = e2eSecretFor(recipientPublicKey) ?: return null
        return com.firefly.befirefly.utils.SecurityManager.encrypt(plaintext, secret)
    }

    /** Open a sealed blob that was encrypted by the given sender. Returns null on failure. */
    private fun decryptFrom(senderPublicKey: String, blob: String): String? {
        val secret = e2eSecretFor(senderPublicKey) ?: return null
        return com.firefly.befirefly.utils.SecurityManager.decrypt(blob, secret)
    }

    // --- Tier 1 feature state ---
    private val notificationHelper by lazy { com.firefly.befirefly.utils.NotificationHelper(getApplication()) }
    private val gson = com.google.gson.Gson()

    // Whether the app is currently in the foreground (set from MainActivity lifecycle).
    var isAppForeground: Boolean = true

    /** Post a notification unless the user is actively looking at this exact chat. */
    private suspend fun maybeNotify(senderId: String, preview: String) {
        if (isAppForeground && currentConversationId == senderId) return
        if (isMuted(senderId)) return
        val name = repository.getContact(senderId)?.name ?: "New message"
        val shown = if (notifPrefs.getBoolean("hide_previews", false)) "New message" else preview
        kotlinx.coroutines.withContext(Dispatchers.Main) {
            notificationHelper.showMessageNotification(name, shown, senderId)
        }
    }

    // Reply: the message the user is currently replying to (null when not replying)
    var replyingTo by mutableStateOf<com.firefly.befirefly.ui.screens.Message?>(null)
        private set

    // Typing indicator: which contact (by id) is currently typing to us
    var typingFrom by mutableStateOf<String?>(null)
        private set
    private var typingClearJob: kotlinx.coroutines.Job? = null
    private var lastTypingSentAt = 0L

    // Search
    var searchResults by mutableStateOf<List<com.firefly.befirefly.ui.screens.Message>>(emptyList())
        private set

    fun setReplyTo(message: com.firefly.befirefly.ui.screens.Message?) {
        replyingTo = message
    }

    /** Add or toggle off an emoji reaction on a message, and tell the other party. */
    fun reactToMessage(message: com.firefly.befirefly.ui.screens.Message, emoji: String) {
        val targetPacketId = message.packetId ?: return
        val convId = currentConversationId ?: return
        val newReaction = if (message.reaction == emoji) null else emoji
        viewModelScope.launch {
            repository.setReaction(targetPacketId, newReaction)
            val json = gson.toJson(mapOf("target" to targetPacketId, "emoji" to (newReaction ?: "")))
            sendControlPacket(convId, com.firefly.befirefly.data.network.PacketType.REACTION, json)
        }
    }

    /** Edit the text of one of my own messages, and propagate the edit. */
    fun editMessage(message: com.firefly.befirefly.ui.screens.Message, newText: String) {
        val targetPacketId = message.packetId ?: return
        val convId = currentConversationId ?: return
        if (!message.isSentByMe || newText.isBlank()) return
        viewModelScope.launch {
            repository.editMessage(targetPacketId, newText)
            val json = gson.toJson(mapOf("target" to targetPacketId, "text" to newText))
            sendControlPacket(convId, com.firefly.befirefly.data.network.PacketType.EDIT, json)
        }
    }

    /** Delete a message for everyone (replaces it with a tombstone on both sides). */
    fun deleteForEveryone(message: com.firefly.befirefly.ui.screens.Message) {
        val targetPacketId = message.packetId ?: return
        val convId = currentConversationId ?: return
        viewModelScope.launch {
            repository.deleteForEveryone(targetPacketId)
            val json = gson.toJson(mapOf("target" to targetPacketId))
            sendControlPacket(convId, com.firefly.befirefly.data.network.PacketType.DELETE, json)
        }
    }

    /** Forward a message (text or media) to another contact. */
    fun forwardMessage(message: com.firefly.befirefly.ui.screens.Message, targetContactId: String) {
        val myId = wallet?.publicKey ?: return
        viewModelScope.launch {
            when (message.type) {
                "text" -> {
                    val packet = NetworkPacket(
                        senderId = myId,
                        receiverId = targetContactId,
                        type = PacketType.TEXT,
                        encryptedPayload = encryptFor(targetContactId, message.text),
                        encryptionType = "E2E-AES-GCM"
                    )
                    repository.sendMessage(targetContactId, message.text, packetId = packet.id, expiresAt = expiryFor(targetContactId))
                    if (packet.encryptedPayload != null) nearbyManager?.sendPayload(targetContactId, packet)
                }
                "image", "audio", "file" -> {
                    val mediaUri = message.mediaUri ?: return@launch
                    val uri = if (mediaUri.startsWith("content://") || mediaUri.startsWith("file://"))
                        android.net.Uri.parse(mediaUri)
                    else
                        android.net.Uri.fromFile(java.io.File(mediaUri))
                    val app = getApplication<android.app.Application>()
                    val meta = try { FileChunker.getFileMetadata(app, uri) } catch (e: Exception) { return@launch }
                    val packetId = java.util.UUID.randomUUID().toString()
                    val pType = when (message.type) {
                        "image" -> PacketType.IMAGE
                        "audio" -> PacketType.AUDIO
                        else -> PacketType.FILE
                    }
                    repository.receiveMedia(targetContactId, mediaUri, message.type, meta.fileName, meta.mimeType, meta.fileSize, 0, myId, packetId, isSentByMe = true, expiresAt = expiryFor(targetContactId))
                    nearbyManager?.sendFilePayload(targetContactId, uri, meta.fileName, meta.mimeType, meta.fileSize, packetId, pType)
                }
            }
        }
    }

    /** Notify the current contact that we're typing (throttled to once every 3s). */
    fun notifyTyping() {
        val convId = currentConversationId ?: return
        val myId = wallet?.publicKey ?: return
        val now = System.currentTimeMillis()
        if (now - lastTypingSentAt < 3000) return
        lastTypingSentAt = now
        nearbyManager?.sendPayload(convId, NetworkPacket(
            senderId = myId, receiverId = convId, type = PacketType.TYPING, payload = "1"
        ))
    }

    /** Search the current conversation for messages containing [query]. */
    fun search(query: String) {
        val convId = currentConversationId
        viewModelScope.launch {
            val results = if (convId != null)
                repository.searchMessagesInConversation(convId, query)
            else
                repository.searchMessages(query)
            searchResults = results.map { entity ->
                com.firefly.befirefly.ui.screens.Message(
                    id = entity.id, text = entity.text, isSentByMe = entity.isSentByMe,
                    status = entity.status, timestamp = entity.timestamp, type = entity.type,
                    packetId = entity.packetId
                )
            }
        }
    }

    fun clearSearch() { searchResults = emptyList() }

    private fun sendControlPacket(receiverId: String, type: com.firefly.befirefly.data.network.PacketType, jsonPayload: String) {
        val myId = wallet?.publicKey ?: return
        val sealed = encryptFor(receiverId, jsonPayload) ?: return
        nearbyManager?.sendPayload(receiverId, NetworkPacket(
            senderId = myId, receiverId = receiverId, type = type,
            encryptedPayload = sealed, encryptionType = "E2E-AES-GCM"
        ))
    }

    private fun onRemoteTyping(senderId: String) {
        typingFrom = senderId
        typingClearJob?.cancel()
        typingClearJob = viewModelScope.launch {
            kotlinx.coroutines.delay(5000)
            if (typingFrom == senderId) typingFrom = null
        }
    }

    // --- Disappearing messages ---
    private val disappearingPrefs by lazy {
        getApplication<android.app.Application>().getSharedPreferences("disappearing", android.content.Context.MODE_PRIVATE)
    }

    // Seconds for the currently open conversation (0 = off). Drives the UI.
    var currentDisappearingSeconds by mutableStateOf(0L)
        private set

    private fun disappearingFor(conversationId: String): Long = disappearingPrefs.getLong(conversationId, 0L)

    /** Compute an expiry timestamp for a new message in this conversation, or null if off. */
    private fun expiryFor(conversationId: String): Long? {
        val secs = disappearingFor(conversationId)
        return if (secs > 0) System.currentTimeMillis() + secs * 1000 else null
    }

    /** Set/clear the disappearing-message timer for a conversation and sync it to the peer. */
    fun setDisappearingTimer(conversationId: String, seconds: Long) {
        disappearingPrefs.edit().putLong(conversationId, seconds).apply()
        if (conversationId == currentConversationId) currentDisappearingSeconds = seconds
        viewModelScope.launch {
            val members = repository.getGroupMembers(conversationId)
            if (members.isNotEmpty()) {
                // Group: each member applies the timer to THIS group conversation.
                val myId = wallet?.publicKey
                val json = gson.toJson(mapOf("seconds" to seconds, "groupId" to conversationId))
                members.forEach { m ->
                    if (m.userId != myId) sendControlPacket(m.userId, com.firefly.befirefly.data.network.PacketType.DISAPPEARING, json)
                }
            } else {
                // 1:1
                sendControlPacket(conversationId, com.firefly.befirefly.data.network.PacketType.DISAPPEARING,
                    gson.toJson(mapOf("seconds" to seconds)))
            }
        }
    }

    // --- Contact verification (safety number / QR) ---
    var currentContactVerified by mutableStateOf(false)
        private set
    var currentSafetyNumber by mutableStateOf("")
        private set

    /** Mark a contact as verified (or not) after a safety-number / QR check. */
    fun verifyContact(contactId: String, verified: Boolean) {
        viewModelScope.launch {
            repository.setVerified(contactId, verified)
            if (contactId == currentConversationId) currentContactVerified = verified
        }
    }

    // --- Per-chat mute ---
    private val mutePrefs by lazy {
        getApplication<android.app.Application>().getSharedPreferences("muted_chats", android.content.Context.MODE_PRIVATE)
    }
    var currentChatMuted by mutableStateOf(false)
        private set

    fun isMuted(conversationId: String): Boolean = mutePrefs.getBoolean(conversationId, false)

    fun toggleMute(conversationId: String) {
        val newVal = !isMuted(conversationId)
        mutePrefs.edit().putBoolean(conversationId, newVal).apply()
        if (conversationId == currentConversationId) currentChatMuted = newVal
    }

    // --- Block & notification privacy ---
    private val notifPrefs by lazy {
        getApplication<android.app.Application>().getSharedPreferences("notif_prefs", android.content.Context.MODE_PRIVATE)
    }
    var currentContactBlocked by mutableStateOf(false)
        private set

    fun toggleBlock(contactId: String) {
        viewModelScope.launch {
            val current = repository.getContact(contactId)?.isBlocked ?: false
            repository.setBlocked(contactId, !current)
            if (contactId == currentConversationId) currentContactBlocked = !current
        }
    }

    // --- Drafts ---
    private val draftPrefs by lazy {
        getApplication<android.app.Application>().getSharedPreferences("drafts", android.content.Context.MODE_PRIVATE)
    }
    var currentDraft by mutableStateOf("")
        private set

    private fun getDraft(conversationId: String): String = draftPrefs.getString(conversationId, "") ?: ""

    /** Save (or clear) the unsent draft for the open conversation. */
    fun saveDraft(text: String) {
        val id = currentConversationId ?: return
        if (text.isBlank()) draftPrefs.edit().remove(id).apply()
        else draftPrefs.edit().putString(id, text).apply()
    }

    // --- Star & pin messages ---
    fun toggleStar(message: com.firefly.befirefly.ui.screens.Message) {
        viewModelScope.launch { repository.setStarred(message.id, !message.isStarred) }
    }

    // --- Groups ---
    var isCurrentChatGroup by mutableStateOf(false)
        private set
    var currentGroupName by mutableStateOf("")
        private set
    var currentGroupIsOwner by mutableStateOf(false)
        private set
    var currentGroupMembers by mutableStateOf<List<com.firefly.befirefly.ui.screens.GroupMemberUi>>(emptyList())
        private set

    private suspend fun loadGroupInfo(groupId: String) {
        val group = repository.getGroup(groupId)
        if (group == null) {
            isCurrentChatGroup = false
            currentGroupMembers = emptyList()
            return
        }
        isCurrentChatGroup = true
        currentGroupName = group.name
        currentGroupIsOwner = group.ownerId == wallet?.publicKey
        val myId = wallet?.publicKey
        currentGroupMembers = repository.getGroupMembers(groupId).map { m ->
            val name = if (m.userId == myId) "You" else repository.getContact(m.userId)?.name ?: "User-${m.userId.takeLast(6)}"
            com.firefly.befirefly.ui.screens.GroupMemberUi(m.userId, name, m.isAdmin)
        }
    }

    private suspend fun propagateGroupInvite(groupId: String) {
        val group = repository.getGroup(groupId) ?: return
        val myId = wallet?.publicKey ?: return
        val members = repository.getGroupMembers(groupId)
        val invite = com.firefly.befirefly.data.network.GroupInvitePayload(groupId, group.name, members.map { it.userId })
        val json = gson.toJson(invite)
        members.forEach { m ->
            if (m.userId != myId) sendControlPacket(m.userId, com.firefly.befirefly.data.network.PacketType.GROUP_INVITE, json)
        }
    }

    fun renameGroup(groupId: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.renameGroup(groupId, newName)
            if (groupId == currentConversationId) currentGroupName = newName
            propagateGroupInvite(groupId)
        }
    }

    fun addGroupMember(groupId: String, userId: String) {
        viewModelScope.launch {
            repository.addGroupMember(groupId, userId)
            if (groupId == currentConversationId) loadGroupInfo(groupId)
            propagateGroupInvite(groupId)
        }
    }

    fun removeGroupMember(groupId: String, userId: String) {
        viewModelScope.launch {
            repository.removeGroupMember(groupId, userId)
            if (groupId == currentConversationId) loadGroupInfo(groupId)
            propagateGroupInvite(groupId)
        }
    }

    fun leaveGroup(groupId: String) {
        viewModelScope.launch {
            repository.leaveGroup(groupId)
            if (groupId == currentConversationId) {
                isCurrentChatGroup = false
                currentGroupMembers = emptyList()
            }
        }
    }

    fun togglePinMessage(message: com.firefly.befirefly.ui.screens.Message) {
        val convId = currentConversationId ?: return
        viewModelScope.launch {
            if (message.isPinned) repository.unpinMessage(convId)
            else repository.pinMessage(convId, message.id)
        }
    }

    // --- Status / Stories ---
    val statuses: kotlinx.coroutines.flow.Flow<List<com.firefly.befirefly.ui.screens.StatusUi>> =
        repository.getStatuses().map { list ->
            list.map { com.firefly.befirefly.ui.screens.StatusUi(it.authorName, it.text, it.timestamp, it.isMine, it.mediaPath, it.mediaType) }
        }

    /** Post a status and broadcast it (encrypted) to all contacts. Expires in 24h. */
    fun postStatus(text: String) {
        if (text.isBlank()) return
        val myId = wallet?.publicKey ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            repository.insertStatus(
                com.firefly.befirefly.data.local.entity.StatusEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    authorId = myId,
                    authorName = username.ifBlank { "Me" },
                    text = text,
                    timestamp = now,
                    expiresAt = now + 24L * 3600_000L,
                    isMine = true
                )
            )
            val json = gson.toJson(mapOf("text" to text, "name" to username))
            repository.getAllContacts().firstOrNull()?.forEach { c ->
                sendControlPacket(c.id, com.firefly.befirefly.data.network.PacketType.STATUS, json)
            }
        }
    }

    /**
     * Post a photo/video story. The media is copied locally for our own viewing and broadcast to
     * every contact via the existing encrypted, chunked media pipeline (STATUS_MEDIA packets).
     * Note: this sends the media once per contact — fine for photos (compressed), heavier for video.
     */
    fun postStatusMedia(uri: android.net.Uri, isVideo: Boolean) {
        val myId = wallet?.publicKey ?: return
        val app = getApplication<android.app.Application>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val mediaType = if (isVideo) "video" else "image"
                val ext = if (isVideo) "mp4" else "jpg"
                val dir = java.io.File(app.filesDir, "status_media").apply { mkdirs() }
                val localFile = java.io.File(dir, "status_${System.currentTimeMillis()}.$ext")
                app.contentResolver.openInputStream(uri)?.use { inp ->
                    java.io.FileOutputStream(localFile).use { out -> inp.copyTo(out) }
                }

                val now = System.currentTimeMillis()
                repository.insertStatus(
                    com.firefly.befirefly.data.local.entity.StatusEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        authorId = myId,
                        authorName = username.ifBlank { "Me" },
                        text = "",
                        timestamp = now,
                        expiresAt = now + 24L * 3600_000L,
                        isMine = true,
                        mediaPath = localFile.absolutePath,
                        mediaType = mediaType
                    )
                )

                val manager = nearbyManager ?: return@launch
                val contacts = repository.getAllContacts().firstOrNull() ?: emptyList()
                for (c in contacts) {
                    val packetId = java.util.UUID.randomUUID().toString()
                    if (!isVideo) {
                        val compressed = com.firefly.befirefly.utils.MediaUtils.compressImage(app, uri)
                        if (compressed != null) {
                            val tmp = java.io.File(app.cacheDir, "status_${packetId}.jpg")
                            tmp.writeBytes(compressed)
                            manager.sendFilePayload(c.id, android.net.Uri.fromFile(tmp), "status.jpg", "image/jpeg", tmp.length(), packetId, packetType = PacketType.STATUS_MEDIA)
                        } else {
                            manager.sendFilePayload(c.id, android.net.Uri.fromFile(localFile), "status.jpg", "image/jpeg", localFile.length(), packetId, packetType = PacketType.STATUS_MEDIA)
                        }
                    } else {
                        manager.sendFilePayload(c.id, android.net.Uri.fromFile(localFile), "status.mp4", "video/mp4", localFile.length(), packetId, packetType = PacketType.STATUS_MEDIA)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("UserViewModel", "Failed to post media status", e)
            }
        }
    }

    // ======================= VOICE / VIDEO CALLS (WebRTC, internet only) =======================
    // Signaling rides the existing E2E-encrypted control-packet path over the cloud (MQTT).
    // Media flows peer-to-peer via ICE. See WebRtcManager for NAT/TURN notes.

    var callState by mutableStateOf(com.firefly.befirefly.data.call.CallUiState())
        private set

    // Exposed so the call UI can build renderers (eglBase) and attach the local preview track.
    var webRtcManager: com.firefly.befirefly.data.call.WebRtcManager? by mutableStateOf(null)
        private set

    var remoteVideoTrack: org.webrtc.VideoTrack? by mutableStateOf(null)
        private set

    private val audioManager by lazy {
        getApplication<android.app.Application>().getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    }
    private var savedAudioMode = android.media.AudioManager.MODE_NORMAL
    private var pendingIncomingOfferSdp: String? = null
    private var callTimerJob: kotlinx.coroutines.Job? = null
    private val callRinger by lazy { com.firefly.befirefly.utils.CallRinger(getApplication()) }

    private fun mainLaunch(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.Main) { block() }
    }

    private fun newWebRtcManager(peerId: String): com.firefly.befirefly.data.call.WebRtcManager {
        val mgr = com.firefly.befirefly.data.call.WebRtcManager(getApplication())
        mgr.setListener(object : com.firefly.befirefly.data.call.WebRtcManager.Listener {
            override fun onLocalDescription(sdp: org.webrtc.SessionDescription) {
                val type = if (sdp.type == org.webrtc.SessionDescription.Type.OFFER)
                    PacketType.CALL_OFFER else PacketType.CALL_ANSWER
                val json = gson.toJson(mapOf(
                    "sdp" to sdp.description,
                    "video" to callState.isVideo,
                    "name" to username
                ))
                sendControlPacket(peerId, type, json)
            }
            override fun onLocalIceCandidate(candidate: org.webrtc.IceCandidate) {
                val json = gson.toJson(mapOf(
                    "sdpMid" to candidate.sdpMid,
                    "sdpMLineIndex" to candidate.sdpMLineIndex,
                    "candidate" to candidate.sdp
                ))
                sendControlPacket(peerId, PacketType.CALL_ICE, json)
            }
            override fun onConnectionStateChanged(state: org.webrtc.PeerConnection.PeerConnectionState) {
                mainLaunch {
                    when (state) {
                        org.webrtc.PeerConnection.PeerConnectionState.CONNECTED -> {
                            if (callState.phase != com.firefly.befirefly.data.call.CallPhase.CONNECTED) {
                                callState = callState.copy(
                                    phase = com.firefly.befirefly.data.call.CallPhase.CONNECTED,
                                    startedAtMs = System.currentTimeMillis()
                                )
                            }
                        }
                        org.webrtc.PeerConnection.PeerConnectionState.DISCONNECTED,
                        org.webrtc.PeerConnection.PeerConnectionState.FAILED,
                        org.webrtc.PeerConnection.PeerConnectionState.CLOSED -> {
                            if (callState.phase != com.firefly.befirefly.data.call.CallPhase.IDLE) endCall(notifyPeer = false)
                        }
                        else -> {}
                    }
                }
            }
            override fun onRemoteVideoTrack(track: org.webrtc.VideoTrack) {
                mainLaunch { remoteVideoTrack = track }
            }
        })
        return mgr
    }

    private fun configureAudioForCall(video: Boolean) {
        try {
            savedAudioMode = audioManager.mode
            audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
            val speaker = video // video calls default to speaker, voice calls to earpiece
            audioManager.isSpeakerphoneOn = speaker
            callState = callState.copy(isSpeakerOn = speaker)
        } catch (_: Exception) {}
    }

    private fun restoreAudio() {
        try {
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = savedAudioMode
        } catch (_: Exception) {}
    }

    /** Start an outgoing call. Requires internet (cloud relay) to reach the peer. */
    fun startCall(peerId: String, video: Boolean) {
        if (callState.phase != com.firefly.befirefly.data.call.CallPhase.IDLE) return
        viewModelScope.launch {
            val name = repository.getContact(peerId)?.name ?: "User-${peerId.takeLast(6)}"
            mainLaunch {
                callState = com.firefly.befirefly.data.call.CallUiState(
                    phase = com.firefly.befirefly.data.call.CallPhase.OUTGOING,
                    peerId = peerId, peerName = name, isVideo = video, isVideoEnabled = video
                )
                remoteVideoTrack = null
                configureAudioForCall(video)
                val mgr = newWebRtcManager(peerId)
                webRtcManager = mgr
                mgr.startCall(video)
            }
        }
    }

    /** Accept the incoming call we're currently ringing on. */
    fun acceptCall() {
        val offer = pendingIncomingOfferSdp ?: return
        val peerId = callState.peerId
        callRinger.stop()
        mainLaunch {
            callState = callState.copy(phase = com.firefly.befirefly.data.call.CallPhase.CONNECTING)
            configureAudioForCall(callState.isVideo)
            val mgr = newWebRtcManager(peerId)
            webRtcManager = mgr
            mgr.onRemoteOffer(
                org.webrtc.SessionDescription(org.webrtc.SessionDescription.Type.OFFER, offer),
                callState.isVideo
            )
            pendingIncomingOfferSdp = null
        }
    }

    /** Decline an incoming call, or cancel/hang up an active one. */
    fun declineCall() = endCall(notifyPeer = true)

    fun endCall(notifyPeer: Boolean = true) {
        val peerId = callState.peerId
        callRinger.stop()
        if (notifyPeer && peerId.isNotBlank() && callState.phase != com.firefly.befirefly.data.call.CallPhase.IDLE) {
            sendControlPacket(peerId, PacketType.CALL_END, "{}")
        }
        callTimerJob?.cancel(); callTimerJob = null
        webRtcManager?.endCall()
        webRtcManager = null
        remoteVideoTrack = null
        pendingIncomingOfferSdp = null
        restoreAudio()
        callState = com.firefly.befirefly.data.call.CallUiState() // back to IDLE
    }

    fun toggleMute() {
        val muted = !callState.isMuted
        webRtcManager?.setMicEnabled(!muted)
        callState = callState.copy(isMuted = muted)
    }

    fun toggleCallVideo() {
        val enabled = !callState.isVideoEnabled
        webRtcManager?.setVideoEnabled(enabled)
        callState = callState.copy(isVideoEnabled = enabled)
    }

    fun switchCamera() { webRtcManager?.switchCamera() }

    // --- Encrypted backup / restore (downloadable ZIP, password-protected) ---
    fun exportBackup(
        out: java.io.OutputStream,
        password: String,
        onResult: (com.firefly.befirefly.utils.BackupManager.BackupResult) -> Unit
    ) {
        viewModelScope.launch {
            val result = kotlinx.coroutines.withContext(Dispatchers.IO) {
                val r = com.firefly.befirefly.utils.BackupManager.export(database, username, password, out)
                try { out.close() } catch (_: Exception) {}
                r
            }
            onResult(result)
        }
    }

    fun importBackup(
        input: java.io.InputStream,
        password: String,
        onResult: (com.firefly.befirefly.utils.BackupManager.BackupResult) -> Unit
    ) {
        viewModelScope.launch {
            val result = kotlinx.coroutines.withContext(Dispatchers.IO) {
                val r = com.firefly.befirefly.utils.BackupManager.import(database, password, input)
                try { input.close() } catch (_: Exception) {}
                r
            }
            onResult(result)
        }
    }


    fun toggleSpeaker() {
        val on = !callState.isSpeakerOn
        try { audioManager.isSpeakerphoneOn = on } catch (_: Exception) {}
        callState = callState.copy(isSpeakerOn = on)
    }

    private fun handleCallPacket(packet: NetworkPacket, payload: String?) {
        when (packet.type) {
            PacketType.CALL_OFFER -> {
                // Reject if already busy.
                if (callState.phase != com.firefly.befirefly.data.call.CallPhase.IDLE) {
                    sendControlPacket(packet.senderId, PacketType.CALL_BUSY, "{}")
                    return
                }
                if (payload == null) return
                try {
                    val obj = JsonParser.parseString(payload).asJsonObject
                    val sdp = obj.get("sdp").asString
                    val video = obj.has("video") && obj.get("video").asBoolean
                    val name = if (obj.has("name")) obj.get("name").asString else "User-${packet.senderId.takeLast(6)}"
                    pendingIncomingOfferSdp = sdp
                    mainLaunch {
                        callState = com.firefly.befirefly.data.call.CallUiState(
                            phase = com.firefly.befirefly.data.call.CallPhase.INCOMING,
                            peerId = packet.senderId, peerName = name,
                            isVideo = video, isVideoEnabled = video
                        )
                        remoteVideoTrack = null
                        callRinger.start()
                    }
                } catch (e: Exception) { android.util.Log.e("UserViewModel", "Bad CALL_OFFER", e) }
            }
            PacketType.CALL_ANSWER -> {
                if (payload == null) return
                try {
                    val obj = JsonParser.parseString(payload).asJsonObject
                    val sdp = obj.get("sdp").asString
                    mainLaunch {
                        callState = callState.copy(phase = com.firefly.befirefly.data.call.CallPhase.CONNECTING)
                        webRtcManager?.onRemoteAnswer(
                            org.webrtc.SessionDescription(org.webrtc.SessionDescription.Type.ANSWER, sdp)
                        )
                    }
                } catch (e: Exception) { android.util.Log.e("UserViewModel", "Bad CALL_ANSWER", e) }
            }
            PacketType.CALL_ICE -> {
                if (payload == null) return
                try {
                    val obj = JsonParser.parseString(payload).asJsonObject
                    val candidate = org.webrtc.IceCandidate(
                        obj.get("sdpMid").asString,
                        obj.get("sdpMLineIndex").asInt,
                        obj.get("candidate").asString
                    )
                    webRtcManager?.onRemoteIceCandidate(candidate)
                } catch (e: Exception) { android.util.Log.e("UserViewModel", "Bad CALL_ICE", e) }
            }
            PacketType.CALL_END, PacketType.CALL_BUSY -> {
                mainLaunch { if (callState.phase != com.firefly.befirefly.data.call.CallPhase.IDLE) endCall(notifyPeer = false) }
            }
            else -> {}
        }
    }

    // Guard against duplicate packet collectors
    private var packetCollectorJob: kotlinx.coroutines.Job? = null
    private val processedPacketIds = java.util.Collections.synchronizedSet(
        java.util.LinkedHashSet<String>()
    )

    fun setService(service: com.firefly.befirefly.data.service.MeshService) {
        this.meshService = service
        this.nearbyManager = service.nearbyManager

        packetCollectorJob?.cancel()
        packetCollectorJob = viewModelScope.launch {
            service.nearbyManager.packetFlow.collect { packet ->
                if (processedPacketIds.contains(packet.id)) {
                    android.util.Log.v("UserViewModel", "Dropping duplicate packet: ${packet.id.take(8)}...")
                    return@collect
                }
                processedPacketIds.add(packet.id)
                if (processedPacketIds.size > 2000) {
                    val iterator = processedPacketIds.iterator()
                    if (iterator.hasNext()) { iterator.next(); iterator.remove() }
                }

                handleIncomingPacket(packet)
            }
        }

        if (wallet != null && username.isNotEmpty()) {
            startOfflineMode()
        }
    }

    // Messages for the current active conversation
    var messages = mutableStateListOf<com.firefly.befirefly.ui.screens.Message>()
        private set

    private var messageJob: kotlinx.coroutines.Job? = null

    fun selectContact(contactId: String) {
        android.util.Log.d("UserViewModel", "💬 selectContact: id=${contactId.take(10)}... (was: ${currentConversationId?.take(10)})")
        currentConversationId = contactId
        currentDisappearingSeconds = disappearingFor(contactId)
        currentSafetyNumber = wallet?.publicKey?.let {
            com.firefly.befirefly.utils.SecurityUtils.computeSafetyNumber(it, contactId)
        } ?: ""
        currentChatMuted = isMuted(contactId)
        currentDraft = getDraft(contactId)
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            currentContactVerified = repository.getContact(contactId)?.isVerified ?: false
            currentContactBlocked = repository.getContact(contactId)?.isBlocked ?: false
            loadGroupInfo(contactId)
            repository.deleteExpiredMessages()
            sendReadReceipts(contactId)

            repository.getMessages(contactId).collect { entities ->
                messages.clear()
                messages.addAll(entities.map { entity ->
                    val media = entity.mediaUri
                    var audio: String? = null
                    var image: String? = null

                    if (media != null) {
                        when (entity.type) {
                            "audio" -> audio = media
                            "image" -> image = media
                            "file" -> { /* file type handled via type field */ }
                            else -> {
                                if (media.endsWith(".m4a") || media.endsWith(".mp3") || media.endsWith(".ogg")) {
                                    audio = media
                                } else {
                                    image = media
                                }
                            }
                        }
                    }

                    com.firefly.befirefly.ui.screens.Message(
                        id = entity.id,
                        text = entity.text,
                        isSentByMe = entity.isSentByMe,
                        imageUri = image,
                        audioPath = audio,
                        status = entity.status,
                        timestamp = entity.timestamp,
                        type = entity.type,
                        fileName = entity.fileName ?: if (entity.type == "file") (entity.text.ifBlank { entity.mediaUri?.substringAfterLast("/") }) else null,
                        mediaUri = entity.mediaUri,
                        mimeType = entity.mimeType,
                        fileSize = entity.fileSize,
                        transferProgress = entity.transferProgress,
                        packetId = entity.packetId,
                        reaction = entity.reaction,
                        replyToPacketId = entity.replyToPacketId,
                        replyToText = entity.replyToText,
                        replyToSentByMe = entity.replyToSentByMe,
                        isEdited = entity.isEdited,
                        isDeleted = entity.isDeleted,
                        isStarred = entity.isStarred,
                        isPinned = entity.isPinned
                    )
                })
            }
        }
    }

    private suspend fun sendReadReceipts(contactId: String) {
        val myId = wallet?.publicKey ?: return
        val unreadMessages = repository.getUnreadMessages(contactId)
        unreadMessages.forEach { msg ->
            if (msg.packetId != null) {
                repository.updateMessageStatus(msg.packetId, "READ")
                nearbyManager?.sendPayload(
                    msg.senderId,
                    com.firefly.befirefly.data.network.NetworkPacket(
                        senderId = myId,
                        receiverId = msg.senderId,
                        type = com.firefly.befirefly.data.network.PacketType.READ_ACK,
                        payload = msg.packetId
                    )
                )
            }
        }
    }

    // Unified Chat List
    val chatPreviews: kotlinx.coroutines.flow.Flow<List<com.firefly.befirefly.domain.model.ChatPreview>> = kotlinx.coroutines.flow.combine(
        repository.getAllContacts(),
        repository.getAllGroups()
    ) { contacts, groups ->
        val list = mutableListOf<com.firefly.befirefly.domain.model.ChatPreview>()

        list.addAll(contacts.map { contact ->
            com.firefly.befirefly.domain.model.ChatPreview(
                id = contact.id,
                name = contact.name,
                lastMessage = if (contact.isOnline) "Online" else "Offline",
                timestamp = contact.lastSeen,
                isGroup = false,
                avatarUri = null,
                isPinned = contact.isPinned
            )
        })

        list.addAll(groups.map { group ->
            com.firefly.befirefly.domain.model.ChatPreview(
                id = group.id,
                name = group.name,
                lastMessage = "Group Chat",
                timestamp = group.createdTimestamp,
                isGroup = true,
                avatarUri = null
            )
        })

        list.sortedWith(compareByDescending<com.firefly.befirefly.domain.model.ChatPreview> { it.isPinned }.thenByDescending { it.timestamp })
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val isAdvertising: kotlinx.coroutines.flow.Flow<Boolean> = snapshotFlow { nearbyManager }
        .flatMapLatest { it?.isAdvertising ?: kotlinx.coroutines.flow.flowOf(false) }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val isDiscovering: kotlinx.coroutines.flow.Flow<Boolean> = snapshotFlow { nearbyManager }
        .flatMapLatest { it?.isDiscovering ?: kotlinx.coroutines.flow.flowOf(false) }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val isCloudConnected: kotlinx.coroutines.flow.Flow<Boolean> = snapshotFlow { nearbyManager }
        .flatMapLatest { it?.isCloudConnected ?: kotlinx.coroutines.flow.flowOf(false) }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val connectedPeersCount: kotlinx.coroutines.flow.Flow<Int> = snapshotFlow { nearbyManager }
        .flatMapLatest { it?.connectedEndpoints ?: kotlinx.coroutines.flow.flowOf(emptyMap()) }
        .map { it.size }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val lastNetworkError: kotlinx.coroutines.flow.Flow<String?> = snapshotFlow { nearbyManager }
        .flatMapLatest { it?.lastError ?: kotlinx.coroutines.flow.flowOf(null) }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val discoveredUsers: kotlinx.coroutines.flow.Flow<List<com.firefly.befirefly.domain.model.ChatPreview>> =
        snapshotFlow { nearbyManager }
            .flatMapLatest { manager ->
                manager?.discoveredUsers ?: kotlinx.coroutines.flow.flowOf(emptyMap())
            }
            .combine(repository.getAllContacts()) { discovered: Map<String, String>, contacts: List<ContactEntity> ->
                val contactIds = contacts.map { it.id }.toSet()
                discovered.entries.filter { entry ->
                    val key = entry.key
                    key != wallet?.publicKey && !contactIds.contains(key)
                }.map { entry ->
                    val key = entry.key
                    val name = entry.value
                    com.firefly.befirefly.domain.model.ChatPreview(
                        id = key,
                        name = name,
                        lastMessage = "Tap to add",
                        timestamp = System.currentTimeMillis(),
                        isGroup = false,
                        avatarUri = null
                    )
                }
            }

    val allContacts: kotlinx.coroutines.flow.Flow<List<com.firefly.befirefly.domain.model.ChatPreview>> = repository.getAllContacts().map { entities ->
        entities.map { contact ->
            com.firefly.befirefly.domain.model.ChatPreview(
                id = contact.id,
                name = contact.name,
                lastMessage = if (contact.isOnline) "Online" else "Offline",
                timestamp = contact.lastSeen,
                isGroup = false,
                avatarUri = null
            )
        }
    }

    fun loadSavedCredentials() {
        android.util.Log.d("UserViewModel", "Loading saved credentials...")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val keyPair = cryptoManager.getKeyPair()
                val savedUsername = cryptoManager.getUsername()

                android.util.Log.d("UserViewModel", "Loaded credentials. Username: $savedUsername, Keys found: ${keyPair != null}")

                if (keyPair != null && !savedUsername.isNullOrEmpty()) {
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        try {
                            username = savedUsername
                            wallet = CryptoWallet(
                                publicKey = cryptoManager.getPublicKeyString() ?: "",
                                privateKey = keyPair.private
                            )
                            profilePictureUri = cryptoManager.getProfilePicture()
                            if (meshService != null) {
                                startOfflineMode()
                            }
                            Unit
                        } catch (e: Exception) {
                            android.util.Log.e("UserViewModel", "Error restoring wallet state", e)
                            Unit
                        }
                    }
                }
                Unit
            } catch (e: Exception) {
                android.util.Log.e("UserViewModel", "Error loading credentials", e)
                Unit
                Unit
                Unit
            }
        }
    }

    fun handleLoginSuccess(privateKey: String, newUsername: String) {
        try {
            username = newUsername
            startOfflineMode()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun createIdentity(name: String, privateKey: String, publicKey: String) {
        android.util.Log.d("UserViewModel", "createIdentity called for $name")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var finalPublicKey = publicKey
                if (publicKey.isBlank()) {
                    android.util.Log.d("UserViewModel", "Public Key missing. Deriving from Private Key...")
                    val derived = com.firefly.befirefly.utils.SecurityUtils.derivePublicKeyFromPrivate(privateKey)
                    if (derived != null) {
                        finalPublicKey = derived
                        android.util.Log.d("UserViewModel", "Public Key derived successfully.")
                    } else {
                        android.util.Log.e("UserViewModel", "Failed to derive Public Key.")
                    }
                }

                android.util.Log.d("UserViewModel", "Saving keys to storage...")
                cryptoManager.saveKeys(privateKey, finalPublicKey)
                cryptoManager.saveUsername(name)
                android.util.Log.d("UserViewModel", "Keys saved.")

                val keyPair = cryptoManager.getKeyPair()

                if (keyPair != null) {
                    android.util.Log.d("UserViewModel", "KeyPair re-loaded successfully. Updating state.")
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        username = name
                        wallet = CryptoWallet(
                            publicKey = finalPublicKey,
                            privateKey = keyPair.private
                        )
                        startOfflineMode()
                    }
                } else {
                    android.util.Log.e("UserViewModel", "Failed to load keys after saving")
                }
            } catch (e: Exception) {
                android.util.Log.e("UserViewModel", "Error in createIdentity", e)
            }
        }
    }

    fun startOfflineMode() {
        val myId = wallet?.publicKey
        val manager = nearbyManager ?: return

        android.util.Log.d("UserViewModel", "Starting Offline Mode... Permissions should be granted.")

        if (myId != null) {
            manager.setMyIdentity(myId)
        }
        manager.startAdvertising(username, isWifiConnected)
        manager.startDiscovery(username, isWifiConnected)
    }

    private fun handleIncomingPacket(packet: com.firefly.befirefly.data.network.NetworkPacket) {
        android.util.Log.d("UserViewModel", "handleIncomingPacket: type=${packet.type} from=${packet.senderId.take(10)}... to=${packet.receiverId.take(10)}...")
        if (packet.receiverId == wallet?.publicKey) {
            android.util.Log.d("UserViewModel", "Packet IS for me. Processing ${packet.type}...")
            viewModelScope.launch {
                // Drop everything from blocked contacts.
                if (repository.getContact(packet.senderId)?.isBlocked == true) return@launch
                // E2E: if the packet carries a sealed payload, open it with the shared secret
                // derived from the SENDER's public key. Control packets (ACKs) stay plaintext.
                val payload = if (packet.encryptedPayload != null) {
                    val decrypted = decryptFrom(packet.senderId, packet.encryptedPayload)
                    if (decrypted == null) {
                        android.util.Log.e("UserViewModel", "❌ Failed to decrypt E2E payload from ${packet.senderId.take(10)}...")
                    }
                    decrypted
                } else {
                    packet.payload
                }

                when (packet.type) {
                    com.firefly.befirefly.data.network.PacketType.TEXT -> {
                        if (payload != null) {
                            android.util.Log.d("UserViewModel", "Received TEXT: \"${payload.take(50)}\" from ${packet.senderId.take(10)}...")

                            val existingContact = repository.getContact(packet.senderId)
                            if (existingContact == null) {
                                android.util.Log.d("UserViewModel", "Unknown sender — auto-creating contact for ${packet.senderId.take(10)}...")
                                repository.addContact(packet.senderId, "User-${packet.senderId.takeLast(6)}")
                            }

                            // Reconstruct the reply quote from our own copy of the original message
                            // (we only receive the original's packetId, never its plaintext on the wire).
                            var replyText: String? = null
                            var replyMine = false
                            if (packet.replyToPacketId != null) {
                                val original = repository.getMessageByPacketId(packet.replyToPacketId)
                                replyText = original?.text
                                replyMine = original?.isSentByMe ?: false
                            }

                            repository.receiveMessage(
                                packet.senderId, payload, packetId = packet.id,
                                replyToPacketId = packet.replyToPacketId,
                                replyToText = replyText, replyToSentByMe = replyMine,
                                expiresAt = expiryFor(packet.senderId)
                            )

                            // Notify only when the chat isn't currently open / app is backgrounded.
                            maybeNotify(packet.senderId, payload.take(120))

                            val myId = wallet?.publicKey ?: return@launch
                            nearbyManager?.sendPayload(
                                packet.senderId,
                                com.firefly.befirefly.data.network.NetworkPacket(
                                    senderId = myId,
                                    receiverId = packet.senderId,
                                    type = com.firefly.befirefly.data.network.PacketType.DELIVERY_ACK,
                                    payload = packet.id
                                )
                            )
                            if (currentConversationId == packet.senderId) {
                                repository.updateMessageStatus(packet.id, "READ")
                                nearbyManager?.sendPayload(
                                    packet.senderId,
                                    com.firefly.befirefly.data.network.NetworkPacket(
                                        senderId = myId,
                                        receiverId = packet.senderId,
                                        type = com.firefly.befirefly.data.network.PacketType.READ_ACK,
                                        payload = packet.id
                                    )
                                )
                            }
                        }
                    }
                    PacketType.FILE -> {
                        if (payload != null) {
                            val isChunked = payload.contains("|") && payload.contains(":")
                            if (isChunked) {
                                val baseId = packet.id.substringBeforeLast("-")
                                val app = getApplication<android.app.Application>()
                                val assembled = com.firefly.befirefly.utils.FileReassembler.addChunk(app, baseId, payload)
                                if (assembled != null) {
                                    val existingContact = repository.getContact(packet.senderId)
                                    if (existingContact == null) {
                                        repository.addContact(packet.senderId, "User-${packet.senderId.takeLast(6)}")
                                    }
                                    repository.receiveMedia(
                                        conversationId = packet.senderId,
                                        localPath = assembled.path,
                                        type = "file",
                                        fileName = assembled.fileName,
                                        mimeType = assembled.mimeType,
                                        fileSize = assembled.fileSize,
                                        transferProgress = 100,
                                        senderId = packet.senderId,
                                        packetId = baseId,
                                        expiresAt = expiryFor(packet.senderId)
                                    )
                                    val myId = wallet?.publicKey ?: return@launch
                                    nearbyManager?.sendPayload(
                                        packet.senderId,
                                        NetworkPacket(
                                            senderId = myId,
                                            receiverId = packet.senderId,
                                            type = PacketType.DELIVERY_ACK,
                                            payload = baseId
                                        )
                                    )
                                    maybeNotify(packet.senderId, "📎 ${assembled.fileName}")
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        android.widget.Toast.makeText(app, "📁 Received file: ${assembled.fileName}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                val localUriString = payload
                                val app = getApplication<android.app.Application>()
                                val fileName = packet.fileName ?: "file_${System.currentTimeMillis()}"
                                val mimeType = packet.mimeType
                                val fileSize = packet.fileSize
                                android.util.Log.d("UserViewModel", "📁 Received FILE stream: $fileName ($fileSize bytes)")
                                val existingContact = repository.getContact(packet.senderId)
                                if (existingContact == null) {
                                    repository.addContact(packet.senderId, "User-${packet.senderId.takeLast(6)}")
                                }
                                try {
                                    val localUri = android.net.Uri.parse(localUriString)
                                    val contentResolver = app.contentResolver
                                    val destDir = java.io.File(app.filesDir, "received_files")
                                    if (!destDir.exists()) destDir.mkdirs()
                                    val destFile = java.io.File(destDir, fileName)
                                    contentResolver.openInputStream(localUri)?.use { input ->
                                        java.io.FileOutputStream(destFile).use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    try {
                                        val tempFile = java.io.File(localUri.path!!)
                                        if (tempFile.exists()) tempFile.delete()
                                    } catch (e: Exception) {}
                                    repository.receiveMedia(
                                        conversationId = packet.senderId,
                                        localPath = destFile.absolutePath,
                                        type = "file",
                                        fileName = fileName,
                                        mimeType = mimeType,
                                        fileSize = fileSize,
                                        transferProgress = 100,
                                        senderId = packet.senderId,
                                        packetId = packet.id
                                    )
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        android.widget.Toast.makeText(app, "📁 Received File: $fileName", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("UserViewModel", "Failed to process received FILE stream", e)
                                }
                                val myId = wallet?.publicKey ?: return@launch
                                nearbyManager?.sendPayload(
                                    packet.senderId,
                                    NetworkPacket(
                                        senderId = myId,
                                        receiverId = packet.senderId,
                                        type = PacketType.DELIVERY_ACK,
                                        payload = packet.id
                                    )
                                )
                            }
                        }
                    }
                    PacketType.IMAGE, PacketType.AUDIO -> {
                        if (payload != null) {
                            val typeStr = when (packet.type) {
                                PacketType.IMAGE -> "image"
                                PacketType.AUDIO -> "audio"
                                else -> "file"
                            }

                            val isChunked = payload.contains("|") && payload.contains(":")
                            if (isChunked) {
                                val baseId = packet.id.substringBeforeLast("-")
                                val app = getApplication<android.app.Application>()
                                val assembled = com.firefly.befirefly.utils.FileReassembler.addChunk(app, baseId, payload)
                                if (assembled != null) {
                                    val existingContact = repository.getContact(packet.senderId)
                                    if (existingContact == null) {
                                        repository.addContact(packet.senderId, "User-${packet.senderId.takeLast(6)}")
                                    }
                                    repository.receiveMedia(
                                        conversationId = packet.senderId,
                                        localPath = assembled.path,
                                        type = typeStr,
                                        fileName = assembled.fileName,
                                        mimeType = assembled.mimeType,
                                        fileSize = assembled.fileSize,
                                        transferProgress = 100,
                                        senderId = packet.senderId,
                                        packetId = baseId,
                                        expiresAt = expiryFor(packet.senderId)
                                    )
                                    val myId = wallet?.publicKey ?: return@launch
                                    nearbyManager?.sendPayload(packet.senderId, NetworkPacket(
                                        senderId = myId, receiverId = packet.senderId,
                                        type = PacketType.DELIVERY_ACK, payload = baseId
                                    ))
                                    maybeNotify(packet.senderId, if (typeStr == "audio") "🎤 Voice message" else "📷 Photo")
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        android.widget.Toast.makeText(app, "📁 Received: ${assembled.fileName}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                android.util.Log.d("UserViewModel", "Received $typeStr: ${packet.fileName} from ${packet.senderId.take(10)}...")
                                val app = getApplication<android.app.Application>()
                                val existingContact = repository.getContact(packet.senderId)
                                if (existingContact == null) {
                                    repository.addContact(packet.senderId, "User-${packet.senderId.takeLast(6)}")
                                }
                                try {
                                    val bytes = com.firefly.befirefly.utils.MediaUtils.decodeFromBase64(payload)
                                    val fileName = packet.fileName ?: "received_${typeStr}_${System.currentTimeMillis()}"
                                    val mimeType = packet.mimeType
                                    val localPath = com.firefly.befirefly.utils.MediaUtils.saveMediaToStorage(app, bytes, fileName, mimeType)
                                    if (localPath != null) {
                                        repository.receiveMedia(
                                            conversationId = packet.senderId,
                                            localPath = localPath,
                                            type = typeStr,
                                            fileName = fileName,
                                            senderId = packet.senderId,
                                            packetId = packet.id
                                        )
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("UserViewModel", "Failed to decode/save media", e)
                                }
                                val myId = wallet?.publicKey ?: return@launch
                                nearbyManager?.sendPayload(packet.senderId, NetworkPacket(
                                    senderId = myId, receiverId = packet.senderId,
                                    type = PacketType.DELIVERY_ACK, payload = packet.id
                                ))
                            }
                        }
                    }
                    com.firefly.befirefly.data.network.PacketType.DELIVERY_ACK -> {
                        if (payload != null) {
                            repository.updateMessageStatus(payload, "DELIVERED")
                        }
                    }
                    com.firefly.befirefly.data.network.PacketType.READ_ACK -> {
                        if (payload != null) {
                            repository.updateMessageStatus(payload, "READ")
                        }
                    }
                    com.firefly.befirefly.data.network.PacketType.REACTION -> {
                        if (payload != null) {
                            try {
                                val obj = com.google.gson.JsonParser.parseString(payload).asJsonObject
                                val target = obj.get("target").asString
                                val emoji = obj.get("emoji").asString
                                repository.setReaction(target, emoji.ifBlank { null })
                            } catch (e: Exception) {
                                android.util.Log.e("UserViewModel", "Failed to parse REACTION", e)
                            }
                        }
                    }
                    com.firefly.befirefly.data.network.PacketType.EDIT -> {
                        if (payload != null) {
                            try {
                                val obj = com.google.gson.JsonParser.parseString(payload).asJsonObject
                                val target = obj.get("target").asString
                                val newText = obj.get("text").asString
                                repository.editMessage(target, newText)
                            } catch (e: Exception) {
                                android.util.Log.e("UserViewModel", "Failed to parse EDIT", e)
                            }
                        }
                    }
                    com.firefly.befirefly.data.network.PacketType.DELETE -> {
                        if (payload != null) {
                            try {
                                val obj = com.google.gson.JsonParser.parseString(payload).asJsonObject
                                val target = obj.get("target").asString
                                repository.deleteForEveryone(target)
                            } catch (e: Exception) {
                                android.util.Log.e("UserViewModel", "Failed to parse DELETE", e)
                            }
                        }
                    }
                    com.firefly.befirefly.data.network.PacketType.TYPING -> {
                        // Ephemeral, plaintext "1" — show the typing indicator for this sender.
                        onRemoteTyping(packet.senderId)
                    }
                    com.firefly.befirefly.data.network.PacketType.STATUS -> {
                        if (payload != null) {
                            try {
                                val obj = com.google.gson.JsonParser.parseString(payload).asJsonObject
                                val text = obj.get("text").asString
                                val name = if (obj.has("name")) obj.get("name").asString else (repository.getContact(packet.senderId)?.name ?: "User-${packet.senderId.takeLast(6)}")
                                val now = System.currentTimeMillis()
                                repository.insertStatus(
                                    com.firefly.befirefly.data.local.entity.StatusEntity(
                                        id = packet.id,
                                        authorId = packet.senderId,
                                        authorName = name,
                                        text = text,
                                        timestamp = now,
                                        expiresAt = now + 24L * 3600_000L,
                                        isMine = false
                                    )
                                )
                            } catch (e: Exception) {
                                android.util.Log.e("UserViewModel", "Failed to parse STATUS", e)
                            }
                        }
                    }
                    com.firefly.befirefly.data.network.PacketType.STATUS_MEDIA -> {
                        if (payload != null) {
                            val isChunked = payload.contains("|") && payload.contains(":")
                            if (isChunked) {
                                val baseId = packet.id.substringBeforeLast("-")
                                val app = getApplication<android.app.Application>()
                                val assembled = com.firefly.befirefly.utils.FileReassembler.addChunk(app, baseId, payload)
                                if (assembled != null) {
                                    val name = repository.getContact(packet.senderId)?.name ?: "User-${packet.senderId.takeLast(6)}"
                                    val mediaType = if (assembled.mimeType.startsWith("video")) "video" else "image"
                                    val now = System.currentTimeMillis()
                                    repository.insertStatus(
                                        com.firefly.befirefly.data.local.entity.StatusEntity(
                                            id = baseId,
                                            authorId = packet.senderId,
                                            authorName = name,
                                            text = "",
                                            timestamp = now,
                                            expiresAt = now + 24L * 3600_000L,
                                            isMine = false,
                                            mediaPath = assembled.path,
                                            mediaType = mediaType
                                        )
                                    )
                                }
                            }
                        }
                    }
                    com.firefly.befirefly.data.network.PacketType.DISAPPEARING -> {
                        if (payload != null) {
                            try {
                                val obj = com.google.gson.JsonParser.parseString(payload).asJsonObject
                                val seconds = obj.get("seconds").asLong
                                // Groups carry a groupId; 1:1 chats key off the sender.
                                val target = if (obj.has("groupId")) obj.get("groupId").asString else packet.senderId
                                disappearingPrefs.edit().putLong(target, seconds).apply()
                                if (target == currentConversationId) currentDisappearingSeconds = seconds
                            } catch (e: Exception) {
                                android.util.Log.e("UserViewModel", "Failed to parse DISAPPEARING", e)
                            }
                        }
                    }
                    com.firefly.befirefly.data.network.PacketType.GROUP_INVITE -> {
                        if (payload != null) {
                            val invite = com.google.gson.Gson().fromJson(payload, com.firefly.befirefly.data.network.GroupInvitePayload::class.java)
                            repository.syncGroup(invite.groupId, invite.groupName, packet.senderId, invite.memberIds)
                            if (invite.groupId == currentConversationId) loadGroupInfo(invite.groupId)
                        }
                    }
                    com.firefly.befirefly.data.network.PacketType.GROUP_MESSAGE -> {
                        if (payload != null) {
                            try {
                                val data = com.google.gson.JsonParser.parseString(payload).asJsonObject
                                val groupId = data.get("groupId").asString
                                val text = data.get("text").asString

                                repository.receiveMessage(conversationId = groupId, text = text, senderId = packet.senderId, packetId = packet.id, expiresAt = expiryFor(groupId))
                            } catch (e: Exception) {
                                android.util.Log.e("UserViewModel", "Failed to parse group message", e)
                            }
                        }
                    }
                    com.firefly.befirefly.data.network.PacketType.CALL_OFFER,
                    com.firefly.befirefly.data.network.PacketType.CALL_ANSWER,
                    com.firefly.befirefly.data.network.PacketType.CALL_ICE,
                    com.firefly.befirefly.data.network.PacketType.CALL_END,
                    com.firefly.befirefly.data.network.PacketType.CALL_BUSY -> {
                        handleCallPacket(packet, payload)
                    }
                    else -> {}
                }
            }
        }
    }

    fun sendGroupMessage(groupId: String, text: String) {
        viewModelScope.launch {
            repository.sendMessage(groupId, text, expiresAt = expiryFor(groupId))

            val members = repository.getGroupMembers(groupId)
            val myId = wallet?.publicKey

            val jsonPayload = com.google.gson.JsonObject().apply {
                addProperty("groupId", groupId)
                addProperty("text", text)
            }.toString()

            members.forEach { member ->
                if (member.userId != myId) {
                    val sealed = encryptFor(member.userId, jsonPayload)
                    if (sealed != null) {
                        nearbyManager?.sendPayload(
                            member.userId,
                            com.firefly.befirefly.data.network.NetworkPacket(
                                senderId = myId ?: "unknown",
                                receiverId = member.userId,
                                type = com.firefly.befirefly.data.network.PacketType.GROUP_MESSAGE,
                                encryptedPayload = sealed,
                                encryptionType = "E2E-AES-GCM"
                            )
                        )
                    } else {
                        android.util.Log.e("UserViewModel", "Skipping group send to ${member.userId.take(10)}... — encryption failed")
                    }
                }
            }
        }
    }

    fun sendMessage(text: String) {
        val conversationId = currentConversationId ?: run {
            android.util.Log.e("UserViewModel", "sendMessage FAILED: currentConversationId is null!")
            return
        }

        android.util.Log.d("UserViewModel", "📤 sendMessage called. text=\"${text.take(30)}\" conversationId=${conversationId.take(10)}...")

        viewModelScope.launch {
            val contact = repository.getContact(conversationId)

            if (contact != null) {
                android.util.Log.d("UserViewModel", "📤 Found contact: name=${contact.name} id=${contact.id.take(10)}...")
                val myId = wallet?.publicKey ?: "unknown"
                android.util.Log.d("UserViewModel", "📤 My ID: ${myId.take(10)}... Receiver ID: ${contact.id.take(10)}...")

                val reply = replyingTo
                val packet = com.firefly.befirefly.data.network.NetworkPacket(
                    senderId = myId,
                    receiverId = contact.id,
                    type = com.firefly.befirefly.data.network.PacketType.TEXT,
                    encryptedPayload = encryptFor(contact.id, text),
                    encryptionType = "E2E-AES-GCM",
                    replyToPacketId = reply?.packetId
                )
                android.util.Log.d("UserViewModel", "📤 Created packet: id=${packet.id} type=${packet.type}")

                repository.sendMessage(
                    conversationId, text, packetId = packet.id,
                    replyToPacketId = reply?.packetId,
                    replyToText = reply?.text,
                    replyToSentByMe = reply?.isSentByMe ?: false,
                    expiresAt = expiryFor(conversationId)
                )
                replyingTo = null
                android.util.Log.d("UserViewModel", "📤 Saved to DB. Now sending via network...")

                val manager = nearbyManager
                if (manager == null) {
                    android.util.Log.e("UserViewModel", "📤 FAILED: nearbyManager is NULL!")
                } else if (packet.encryptedPayload == null) {
                    android.util.Log.e("UserViewModel", "📤 FAILED: could not encrypt message — refusing to send plaintext over the network")
                } else {
                    manager.sendPayload(contact.id, packet)
                    android.util.Log.d("UserViewModel", "📤 sendPayload called successfully")
                }
            } else {
                android.util.Log.d("UserViewModel", "No contact found for $conversationId, trying group...")
                sendGroupMessage(conversationId, text)
            }
        }
    }

    fun sendImage(uri: android.net.Uri) {
        val conversationId = currentConversationId ?: return
        val app = getApplication<android.app.Application>()

        // Try to persist URI permission (some pickers grant temporary access)
        try {
            app.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) { /* Not all URIs support persistable permissions — that's OK */ }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val manager = nearbyManager
                if (manager == null) {
                    android.util.Log.e("UserViewModel", "❌ sendImage: nearbyManager is null — network not ready")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(app, "Network not ready. Try again in a moment.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val metadata = FileChunker.getFileMetadata(app, uri)
                val contact = repository.getContact(conversationId) ?: return@launch
                val myId = wallet?.publicKey ?: return@launch

                android.util.Log.d("UserViewModel", "📸 sendImage: ${metadata.fileName} (${metadata.mimeType}, ${metadata.fileSize} bytes)")

                val packetId = java.util.UUID.randomUUID().toString()
                // Display the original locally (full quality for the sender)
                repository.receiveMedia(
                    conversationId, uri.toString(), "image",
                    metadata.fileName, metadata.mimeType, metadata.fileSize,
                    0, myId, packetId, isSentByMe = true, expiresAt = expiryFor(conversationId)
                )

                // Compress before sending so we transmit a small JPEG instead of a multi-MB original.
                val compressed = com.firefly.befirefly.utils.MediaUtils.compressImage(app, uri)
                if (compressed != null) {
                    val tmp = java.io.File(app.cacheDir, "send_img_${packetId}.jpg")
                    tmp.writeBytes(compressed)
                    manager.sendFilePayload(contact.id, android.net.Uri.fromFile(tmp), "image.jpg", "image/jpeg", tmp.length(), packetId, packetType = PacketType.IMAGE)
                } else {
                    // Fallback: send the original if compression failed
                    manager.sendFilePayload(contact.id, uri, metadata.fileName, metadata.mimeType, metadata.fileSize, packetId, packetType = PacketType.IMAGE)
                }
                android.util.Log.d("UserViewModel", "📸 Image send initiated: ${metadata.fileName}")
            } catch (e: Exception) {
                android.util.Log.e("UserViewModel", "❌ Failed to send image", e)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(app, "Failed to send image: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun sendAudio(file: java.io.File) {
        val conversationId = currentConversationId ?: return
        val app = getApplication<android.app.Application>()
        val uri = android.net.Uri.fromFile(file)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val manager = nearbyManager
                if (manager == null) {
                    android.util.Log.e("UserViewModel", "❌ sendAudio: nearbyManager is null")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(app, "Network not ready. Try again in a moment.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val metadata = FileChunker.getFileMetadata(app, uri)
                val contact = repository.getContact(conversationId) ?: return@launch
                val myId = wallet?.publicKey ?: return@launch

                android.util.Log.d("UserViewModel", "🎤 sendAudio: ${metadata.fileName} (${metadata.fileSize} bytes)")

                val packetId = java.util.UUID.randomUUID().toString()
                repository.receiveMedia(
                    conversationId, file.absolutePath, "audio",
                    metadata.fileName, metadata.mimeType, metadata.fileSize,
                    0, myId, packetId, isSentByMe = true, expiresAt = expiryFor(conversationId)
                )

                manager.sendFilePayload(contact.id, uri, metadata.fileName, metadata.mimeType, metadata.fileSize, packetId, packetType = PacketType.AUDIO)
                android.util.Log.d("UserViewModel", "🎤 Audio send initiated: ${metadata.fileName}")
            } catch (e: Exception) {
                android.util.Log.e("UserViewModel", "❌ Failed to send audio", e)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(app, "Failed to send audio: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun sendFile(uri: android.net.Uri) {
        val conversationId = currentConversationId ?: return
        val app = getApplication<android.app.Application>()

        // Try to persist URI permission
        try {
            app.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) { /* Not all URIs support persistable permissions */ }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val manager = nearbyManager
                if (manager == null) {
                    android.util.Log.e("UserViewModel", "❌ sendFile: nearbyManager is null")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(app, "Network not ready. Try again in a moment.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val metadata = FileChunker.getFileMetadata(app, uri)
                val contact = repository.getContact(conversationId) ?: return@launch
                val myId = wallet?.publicKey ?: return@launch

                android.util.Log.d("UserViewModel", "📎 sendFile: ${metadata.fileName} (${metadata.mimeType}, ${metadata.fileSize} bytes)")

                val packetId = java.util.UUID.randomUUID().toString()
                repository.receiveMedia(
                    conversationId, uri.toString(), "file",
                    metadata.fileName, metadata.mimeType, metadata.fileSize,
                    0, myId, packetId, isSentByMe = true, expiresAt = expiryFor(conversationId)
                )

                manager.sendFilePayload(contact.id, uri, metadata.fileName, metadata.mimeType, metadata.fileSize, packetId)
                android.util.Log.d("UserViewModel", "📎 File send initiated: ${metadata.fileName}")
            } catch (e: Exception) {
                android.util.Log.e("UserViewModel", "❌ Failed to send file", e)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(app, "Failed to send file: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun logout() {
        wallet = null
        username = ""
        profilePictureUri = null
        e2eSecretCache.clear()
        nearbyManager?.stopAll()
    }

    fun resetIdentity() {
        viewModelScope.launch(Dispatchers.IO) {
            cryptoManager.clear()

            val app = getApplication<android.app.Application>()
            try {
                val prefsDir = java.io.File(app.applicationInfo.dataDir, "shared_prefs")
                prefsDir.listFiles()?.forEach { it.delete() }
                android.util.Log.d("Nuke", "Cleared shared_prefs directory")

                val dbDir = java.io.File(app.applicationInfo.dataDir, "databases")
                dbDir.listFiles()?.forEach { it.delete() }
                android.util.Log.d("Nuke", "Cleared databases directory")

                app.cacheDir.deleteRecursively()
                android.util.Log.d("Nuke", "Cleared cache")
            } catch (e: Exception) {
                android.util.Log.e("Nuke", "Error during nuclear cleanup", e)
            }

            kotlinx.coroutines.withContext(Dispatchers.Main) {
                android.util.Log.d("Nuke", "Restarting app...")
                val intent = app.packageManager.getLaunchIntentForPackage(app.packageName)
                intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                app.startActivity(intent)
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    fun updateProfilePicture(uri: String) {
        try {
            val contentUri = android.net.Uri.parse(uri)
            val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            getApplication<android.app.Application>().contentResolver.takePersistableUriPermission(contentUri, takeFlags)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        profilePictureUri = uri
        viewModelScope.launch(Dispatchers.IO) {
            cryptoManager.saveProfilePicture(uri)
        }
    }

    fun createGroup(name: String, memberIds: List<String>) {
        val groupId = java.util.UUID.randomUUID().toString()
        val ownerId = wallet?.publicKey ?: return

        viewModelScope.launch {
            repository.createGroup(groupId, name, ownerId, memberIds + ownerId)

            val invitePayload = com.firefly.befirefly.data.network.GroupInvitePayload(groupId, name, memberIds + ownerId)
            val json = com.google.gson.Gson().toJson(invitePayload)

            memberIds.forEach { memberId ->
                val sealed = encryptFor(memberId, json)
                if (sealed != null) {
                    nearbyManager?.sendPayload(
                        memberId,
                        com.firefly.befirefly.data.network.NetworkPacket(
                            senderId = ownerId,
                            receiverId = memberId,
                            type = com.firefly.befirefly.data.network.PacketType.GROUP_INVITE,
                            encryptedPayload = sealed,
                            encryptionType = "E2E-AES-GCM"
                        )
                    )
                } else {
                    android.util.Log.e("UserViewModel", "Skipping group invite to ${memberId.take(10)}... — encryption failed")
                }
            }
        }
    }

    fun addContact(name: String, publicKey: String) {
        android.util.Log.d("UserViewModel", "➕ addContact called: name=$name key=${publicKey.take(10)}...")
        viewModelScope.launch {
            repository.addContact(publicKey, name)
            android.util.Log.d("UserViewModel", "➕ Contact saved to DB: name=$name id=${publicKey.take(10)}...")
        }
    }

    fun testCloudRelay() {
        val app = getApplication<android.app.Application>()
        val myId = wallet?.publicKey ?: run {
            android.util.Log.e("UserViewModel", "🧪 TEST FAILED: No wallet/publicKey!")
            android.widget.Toast.makeText(app, "❌ Test failed: No identity!", android.widget.Toast.LENGTH_LONG).show()
            return
        }

        val manager = nearbyManager ?: run {
            android.util.Log.e("UserViewModel", "🧪 TEST FAILED: nearbyManager is null!")
            android.widget.Toast.makeText(app, "❌ Test failed: No mesh service!", android.widget.Toast.LENGTH_LONG).show()
            return
        }

        android.util.Log.d("UserViewModel", "🧪 CLOUD TEST: myId=${myId.take(15)}...")
        android.widget.Toast.makeText(app, "🧪 Sending cloud test message...", android.widget.Toast.LENGTH_SHORT).show()

        val testPacket = com.firefly.befirefly.data.network.NetworkPacket(
            senderId = myId,
            receiverId = myId,
            type = com.firefly.befirefly.data.network.PacketType.TEXT,
            payload = "🧪 CLOUD TEST at ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}"
        )

        manager.sendPayload(myId, testPacket)
        android.util.Log.d("UserViewModel", "🧪 Test packet dispatched!")
    }

    fun updateContactName(contactId: String, newName: String) {
        viewModelScope.launch {
            repository.updateContactName(contactId, newName)
            android.util.Log.d("UserViewModel", "✏️ Contact renamed: ${contactId.take(10)}... → $newName")
        }
    }

    fun deleteContact(contactId: String) {
        viewModelScope.launch {
            repository.deleteContact(contactId)
            android.util.Log.d("UserViewModel", "🗑️ Contact deleted: ${contactId.take(10)}...")
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
            android.util.Log.d("UserViewModel", "🗑️ Message deleted: id=$messageId")
        }
    }

    fun clearChat() {
        val convId = currentConversationId ?: return
        viewModelScope.launch {
            repository.clearChat(convId)
            android.util.Log.d("UserViewModel", "🗑️ Chat cleared: ${convId.take(10)}...")
        }
    }

    fun togglePinChat(chatId: String) {
        viewModelScope.launch {
            repository.togglePinContact(chatId)
            android.util.Log.d("UserViewModel", "📌 Pin toggled: ${chatId.take(10)}...")
        }
    }

    fun exportChat(onReady: (android.net.Uri) -> Unit) {
        val convId = currentConversationId ?: return
        val app = getApplication<android.app.Application>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val msgs = repository.getMessagesOnce(convId)
                val contact = repository.getContact(convId)
                val contactName = contact?.name ?: "Unknown"
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())

                val sb = StringBuilder()
                sb.appendLine("Chat Export: $contactName")
                sb.appendLine("Exported: ${dateFormat.format(java.util.Date())}")
                sb.appendLine("Messages: ${msgs.size}")
                sb.appendLine("═".repeat(40))
                sb.appendLine()

                for (msg in msgs) {
                    val sender = if (msg.isSentByMe) "Me" else contactName
                    val time = dateFormat.format(java.util.Date(msg.timestamp))
                    when (msg.type) {
                        "text" -> sb.appendLine("[$time] $sender: ${msg.text}")
                        "image" -> sb.appendLine("[$time] $sender: 📷 [Image]")
                        "audio" -> sb.appendLine("[$time] $sender: 🎤 [Voice Note]")
                        "file" -> sb.appendLine("[$time] $sender: 📎 [File: ${msg.fileName ?: "Unknown"}]")
                        else -> sb.appendLine("[$time] $sender: ${msg.text}")
                    }
                }

                val fileName = "chat_${contactName.replace(" ", "_")}_${System.currentTimeMillis()}.txt"
                val file = java.io.File(app.cacheDir, fileName)
                file.writeText(sb.toString())

                val uri = androidx.core.content.FileProvider.getUriForFile(
                    app,
                    "${app.packageName}.fileprovider",
                    file
                )

                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onReady(uri)
                }

                android.util.Log.d("UserViewModel", "📤 Chat exported: $fileName (${msgs.size} messages)")
            } catch (e: Exception) {
                android.util.Log.e("UserViewModel", "Export failed", e)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(app, "Export failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    var currentWallpaperGradient by mutableStateOf<Int?>(null)
        private set
    var currentWallpaperImage by mutableStateOf<String?>(null)
        private set

    fun loadWallpaper(conversationId: String) {
        val app = getApplication<android.app.Application>()
        val prefs = app.getSharedPreferences("wallpapers", android.content.Context.MODE_PRIVATE)
        val type = prefs.getString("wp_type_$conversationId", null)
        when (type) {
            "gradient" -> {
                currentWallpaperGradient = prefs.getInt("wp_value_$conversationId", 0)
                currentWallpaperImage = null
            }
            "image" -> {
                currentWallpaperImage = prefs.getString("wp_value_str_$conversationId", null)
                currentWallpaperGradient = null
            }
            else -> {
                currentWallpaperGradient = null
                currentWallpaperImage = null
            }
        }
    }

    fun setWallpaperGradient(conversationId: String, index: Int) {
        val app = getApplication<android.app.Application>()
        app.getSharedPreferences("wallpapers", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("wp_type_$conversationId", "gradient")
            .putInt("wp_value_$conversationId", index)
            .remove("wp_value_str_$conversationId")
            .apply()
        currentWallpaperGradient = index
        currentWallpaperImage = null
    }

    fun setWallpaperImage(conversationId: String, uri: String) {
        val app = getApplication<android.app.Application>()
        app.getSharedPreferences("wallpapers", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("wp_type_$conversationId", "image")
            .putString("wp_value_str_$conversationId", uri)
            .apply()
        currentWallpaperImage = uri
        currentWallpaperGradient = null
    }

    fun resetWallpaper(conversationId: String) {
        val app = getApplication<android.app.Application>()
        app.getSharedPreferences("wallpapers", android.content.Context.MODE_PRIVATE)
            .edit()
            .remove("wp_type_$conversationId")
            .remove("wp_value_$conversationId")
            .remove("wp_value_str_$conversationId")
            .apply()
        currentWallpaperGradient = null
        currentWallpaperImage = null
    }
}