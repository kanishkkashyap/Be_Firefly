package com.firefly.befirefly.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val id: String, // Endpoint ID or Public Key
    val name: String,
    val publicKey: String,
    val lastSeen: Long,
    val isOnline: Boolean = false,
    val isPinned: Boolean = false,
    val isVerified: Boolean = false, // Safety number / QR verified (no MITM)
    val isBlocked: Boolean = false // Blocked — their messages are dropped
)
