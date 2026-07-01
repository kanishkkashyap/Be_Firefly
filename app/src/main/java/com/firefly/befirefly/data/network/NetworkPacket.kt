package com.firefly.befirefly.data.network

import com.google.gson.Gson

data class NetworkPacket(
    val id: String = java.util.UUID.randomUUID().toString(),
    val senderId: String,
    val receiverId: String, // Destination ID
    val type: PacketType,
    val payload: String? = null, // Plaintext payload (text or Base64 media)
    val encryptedPayload: String? = null, // Encrypted payload
    val encryptionType: String = "NONE", // "AES", "RSA", "NONE"
    val timestamp: Long = System.currentTimeMillis(),
    val isRelay: Boolean = false, // True if this packet is meant to be forwarded
    val ttl: Int = 7, // Time To Live: hops remaining
    // Media metadata
    val fileName: String? = null, // Original filename (e.g., "photo.jpg")
    val mimeType: String? = null, // MIME type (e.g., "image/jpeg")
    val fileSize: Long = 0, // Original file size in bytes
    val nearbyPayloadId: Long? = null, // Links this metadata packet to a Payload.Type.FILE
    val transferProgress: Int = 100, // 0-100%
    val replyToPacketId: String? = null // If this message replies to another, that message's packetId
)

enum class PacketType {
    TEXT,
    IMAGE,
    AUDIO,
    FILE, // Documents, videos, etc.
    ACK,
    DELIVERY_ACK,
    READ_ACK,
    ROUTING_INFO,
    HANDSHAKE_INIT,
    HANDSHAKE_ACK,
    GROUP_MESSAGE,
    GROUP_INVITE,
    PROFILE_UPDATE,
    REACTION,   // Add/remove an emoji reaction on a message
    EDIT,       // Edit the text of a previously sent message
    DELETE,     // Delete a message for everyone
    TYPING,     // Typing indicator (ephemeral, not persisted)
    DISAPPEARING, // Set/clear the disappearing-message timer for a conversation
    STATUS,      // Ephemeral status/story broadcast to contacts
    STATUS_MEDIA, // Photo/video story, sent as encrypted chunks (reuses media pipeline)
    CALL_OFFER,  // WebRTC SDP offer (start a call) — internet only
    CALL_ANSWER, // WebRTC SDP answer (accept a call)
    CALL_ICE,    // WebRTC ICE candidate exchange
    CALL_END,    // Hang up / decline / cancel a call
    CALL_BUSY    // Callee is already in another call
}

object PacketSerializer {
    private val gson = Gson()

    fun serialize(packet: NetworkPacket): String {
        return gson.toJson(packet)
    }

    fun deserialize(json: String): NetworkPacket {
        return gson.fromJson(json, NetworkPacket::class.java)
    }
}
