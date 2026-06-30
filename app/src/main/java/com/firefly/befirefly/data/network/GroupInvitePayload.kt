package com.firefly.befirefly.data.network

data class GroupInvitePayload(
    val groupId: String,
    val groupName: String,
    val memberIds: List<String> // Public Keys
)
