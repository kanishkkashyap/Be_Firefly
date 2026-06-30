package com.firefly.befirefly.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.firefly.befirefly.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForContact(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesForContactOnce(conversationId: String): List<MessageEntity>

    @Insert
    suspend fun insertMessage(message: MessageEntity)

    @Delete
    suspend fun deleteMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessageById(id: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteAllForConversation(conversationId: String)

    @Query("UPDATE messages SET status = :status WHERE packetId = :packetId")
    suspend fun updateMessageStatus(packetId: String, status: String)

    @Query("UPDATE messages SET reaction = :reaction WHERE packetId = :packetId")
    suspend fun updateReaction(packetId: String, reaction: String?)

    @Query("UPDATE messages SET text = :text, isEdited = 1 WHERE packetId = :packetId")
    suspend fun editMessageText(packetId: String, text: String)

    @Query("UPDATE messages SET isDeleted = 1, text = '', reaction = NULL, mediaUri = NULL, type = 'text' WHERE packetId = :packetId")
    suspend fun markDeletedForEveryone(packetId: String)

    @Query("SELECT * FROM messages WHERE text LIKE '%' || :query || '%' AND isDeleted = 0 ORDER BY timestamp DESC")
    suspend fun searchAllMessages(query: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND text LIKE '%' || :query || '%' AND isDeleted = 0 ORDER BY timestamp DESC")
    suspend fun searchMessagesInConversation(conversationId: String, query: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE packetId = :packetId LIMIT 1")
    suspend fun getMessageByPacketId(packetId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND isSentByMe = 0 AND status != 'READ' ORDER BY timestamp ASC")
    suspend fun getUnreadMessages(conversationId: String): List<MessageEntity>

    @Query("DELETE FROM messages WHERE expiresAt IS NOT NULL AND expiresAt < :now")
    suspend fun deleteExpired(now: Long)

    @Query("UPDATE messages SET isStarred = :starred WHERE id = :id")
    suspend fun updateStarred(id: Long, starred: Boolean)

    @Query("SELECT * FROM messages WHERE isStarred = 1 ORDER BY timestamp DESC")
    suspend fun getStarredMessages(): List<MessageEntity>

    @Query("UPDATE messages SET isPinned = 0 WHERE conversationId = :conversationId")
    suspend fun clearPinnedInConversation(conversationId: String)

    @Query("UPDATE messages SET isPinned = :pinned WHERE id = :id")
    suspend fun updatePinnedMessage(id: Long, pinned: Boolean)
}
