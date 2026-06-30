package com.firefly.befirefly.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.firefly.befirefly.data.local.entity.PendingMessageEntity

@Dao
interface PendingMessageDao {
    @Insert
    suspend fun insert(message: PendingMessageEntity)

    @Query("SELECT * FROM pending_messages WHERE receiverId = :receiverId ORDER BY createdAt ASC")
    suspend fun getForReceiver(receiverId: String): List<PendingMessageEntity>

    @Query("SELECT * FROM pending_messages ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingMessageEntity>

    @Query("DELETE FROM pending_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE pending_messages SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetryCount(id: Long)

    @Query("DELETE FROM pending_messages WHERE retryCount >= maxRetries")
    suspend fun deleteExpired()

    @Query("SELECT COUNT(*) FROM pending_messages")
    suspend fun getPendingCount(): Int
}
