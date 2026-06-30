package com.firefly.befirefly.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String, // UUID
    val name: String,
    val ownerId: String, // Public Key of creator
    val createdTimestamp: Long
)
