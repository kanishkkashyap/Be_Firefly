package com.firefly.befirefly.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String, // The ID of the contact this message belongs to
    val senderId: String,
    val text: String,
    val timestamp: Long,
    val isSentByMe: Boolean,
    val type: String = "text", // "text" or "image"
    val mediaUri: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val fileSize: Long? = null,
    val transferProgress: Int = 100, // 0-100%
    val packetId: String? = null, // Maps to NetworkPacket.id for ACK correlation
    val status: String = "SENT", // SENT, DELIVERED, READ
    val reaction: String? = null, // Emoji reaction on this message (single reaction per message)
    val replyToPacketId: String? = null, // packetId of the message this one replies to
    val replyToText: String? = null, // Cached preview text of the replied-to message
    val replyToSentByMe: Boolean = false, // Whether the replied-to message was mine
    val isEdited: Boolean = false, // True if the text was edited after sending
    val isDeleted: Boolean = false, // True if deleted-for-everyone (shown as a tombstone)
    val expiresAt: Long? = null, // If set, the message auto-deletes once this timestamp passes
    val isStarred: Boolean = false, // Saved/bookmarked by the user
    val isPinned: Boolean = false // Pinned to the top of the conversation (one at a time)
)
