package com.firefly.befirefly.domain.model

data class ChatPreview(
    val id: String, // Contact ID or Group ID
    val name: String,
    val lastMessage: String,
    val timestamp: Long,
    val isGroup: Boolean,
    val avatarUri: String? = null,
    val isPinned: Boolean = false
)
