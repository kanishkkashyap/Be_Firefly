package com.firefly.befirefly.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_messages")
data class PendingMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiverId: String,         // Public key of intended recipient
    val packetJson: String,         // Full serialized NetworkPacket JSON
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val maxRetries: Int = 50        // Give up after 50 attempts
)
