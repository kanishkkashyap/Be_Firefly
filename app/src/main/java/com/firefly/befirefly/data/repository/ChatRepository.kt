package com.firefly.befirefly.data.repository

import com.firefly.befirefly.data.local.dao.ContactDao
import com.firefly.befirefly.data.local.dao.MessageDao
import com.firefly.befirefly.data.local.entity.ContactEntity
import com.firefly.befirefly.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

import com.firefly.befirefly.data.local.dao.GroupDao
import com.firefly.befirefly.data.local.entity.GroupEntity
import com.firefly.befirefly.data.local.entity.GroupMemberEntity

class ChatRepository(
    private val messageDao: MessageDao,
    private val contactDao: ContactDao,
    private val groupDao: GroupDao,
    private val statusDao: com.firefly.befirefly.data.local.dao.StatusDao? = null
) {
    fun getMessages(conversationId: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesForContact(conversationId)
    }

    suspend fun getMessagesOnce(conversationId: String): List<MessageEntity> {
        return messageDao.getMessagesForContactOnce(conversationId)
    }

    suspend fun sendMessage(conversationId: String, text: String, packetId: String? = null,
                            replyToPacketId: String? = null, replyToText: String? = null, replyToSentByMe: Boolean = false,
                            expiresAt: Long? = null) {
        val message = MessageEntity(
            conversationId = conversationId,
            senderId = "me",
            text = text,
            timestamp = System.currentTimeMillis(),
            isSentByMe = true,
            type = "text",
            packetId = packetId,
            status = "SENT",
            replyToPacketId = replyToPacketId,
            replyToText = replyToText,
            replyToSentByMe = replyToSentByMe,
            expiresAt = expiresAt
        )
        messageDao.insertMessage(message)
    }

    suspend fun sendImage(conversationId: String, uri: String) {
        val message = MessageEntity(
            conversationId = conversationId,
            senderId = "me",
            text = "", // No text for image message
            timestamp = System.currentTimeMillis(),
            isSentByMe = true,
            type = "image",
            mediaUri = uri
        )
        messageDao.insertMessage(message)
    }

    suspend fun insertMessage(message: MessageEntity) {
        messageDao.insertMessage(message)
    }

    suspend fun receiveMessage(conversationId: String, text: String, senderId: String = conversationId, packetId: String? = null,
                               replyToPacketId: String? = null, replyToText: String? = null, replyToSentByMe: Boolean = false,
                               expiresAt: Long? = null) {
        val message = MessageEntity(
            conversationId = conversationId,
            senderId = senderId,
            text = text,
            timestamp = System.currentTimeMillis(),
            isSentByMe = false,
            packetId = packetId,
            status = "DELIVERED",
            replyToPacketId = replyToPacketId,
            replyToText = replyToText,
            replyToSentByMe = replyToSentByMe,
            expiresAt = expiresAt
        )
        messageDao.insertMessage(message)
    }

    suspend fun receiveMedia(
        conversationId: String, 
        localPath: String, 
        type: String, 
        fileName: String? = null,
        mimeType: String? = null,
        fileSize: Long? = null,
        transferProgress: Int = 100,
        senderId: String, 
        packetId: String,
        isSentByMe: Boolean = false,
        expiresAt: Long? = null
    ) {
        val message = MessageEntity(
            conversationId = conversationId,
            senderId = senderId,
            text = if (isSentByMe) "" else "Media received",
            timestamp = System.currentTimeMillis(),
            isSentByMe = isSentByMe,
            type = type,
            mediaUri = localPath,
            fileName = fileName,
            mimeType = mimeType,
            fileSize = fileSize,
            transferProgress = transferProgress,
            packetId = packetId,
            status = if (isSentByMe) "SENT" else "DELIVERED",
            expiresAt = expiresAt
        )
        messageDao.insertMessage(message)
    }

    suspend fun deleteExpiredMessages() {
        messageDao.deleteExpired(System.currentTimeMillis())
    }

    suspend fun setStarred(id: Long, starred: Boolean) {
        messageDao.updateStarred(id, starred)
    }

    suspend fun getStarredMessages(): List<MessageEntity> {
        return messageDao.getStarredMessages()
    }

    /** Pin a message (only one per conversation) — clears any existing pin first. */
    suspend fun pinMessage(conversationId: String, id: Long) {
        messageDao.clearPinnedInConversation(conversationId)
        messageDao.updatePinnedMessage(id, true)
    }

    suspend fun unpinMessage(conversationId: String) {
        messageDao.clearPinnedInConversation(conversationId)
    }

    suspend fun updateMessageStatus(packetId: String, status: String) {
        messageDao.updateMessageStatus(packetId, status)
    }

    suspend fun setReaction(packetId: String, reaction: String?) {
        messageDao.updateReaction(packetId, reaction)
    }

    suspend fun editMessage(packetId: String, newText: String) {
        messageDao.editMessageText(packetId, newText)
    }

    suspend fun deleteForEveryone(packetId: String) {
        messageDao.markDeletedForEveryone(packetId)
    }

    suspend fun getMessageByPacketId(packetId: String): MessageEntity? {
        return messageDao.getMessageByPacketId(packetId)
    }

    suspend fun searchMessages(query: String): List<MessageEntity> {
        if (query.isBlank()) return emptyList()
        return messageDao.searchAllMessages(query)
    }

    suspend fun searchMessagesInConversation(conversationId: String, query: String): List<MessageEntity> {
        if (query.isBlank()) return emptyList()
        return messageDao.searchMessagesInConversation(conversationId, query)
    }

    suspend fun getUnreadMessages(conversationId: String): List<MessageEntity> {
        return messageDao.getUnreadMessages(conversationId)
    }

    // --- Message Deletion ---
    suspend fun deleteMessage(id: Long) {
        messageDao.deleteMessageById(id)
    }

    suspend fun clearChat(conversationId: String) {
        messageDao.deleteAllForConversation(conversationId)
    }
    
    fun getAllContacts(): Flow<List<ContactEntity>> {
        return contactDao.getAllContacts()
    }
    
    suspend fun getContact(id: String): ContactEntity? {
        return contactDao.getContactById(id)
    }
    
    suspend fun addContact(id: String, name: String) {
        val contact = ContactEntity(
            id = id,
            name = name,
            publicKey = id, // ID is the Public Key
            lastSeen = System.currentTimeMillis(),
            isOnline = true
        )
        contactDao.insertContact(contact)
    }

    suspend fun updateContactName(id: String, newName: String) {
        contactDao.updateContactName(id, newName)
    }

    suspend fun deleteContact(id: String) {
        contactDao.deleteContact(id)
    }

    // --- Chat Pinning ---
    suspend fun togglePinContact(id: String) {
        val contact = contactDao.getContactById(id)
        if (contact != null) {
            contactDao.updatePinStatus(id, !contact.isPinned)
        }
    }

    suspend fun setPinStatus(id: String, isPinned: Boolean) {
        contactDao.updatePinStatus(id, isPinned)
    }

    suspend fun setVerified(id: String, verified: Boolean) {
        contactDao.updateVerified(id, verified)
    }

    suspend fun setBlocked(id: String, blocked: Boolean) {
        contactDao.updateBlocked(id, blocked)
    }

    // Group Methods
    suspend fun createGroup(groupId: String, name: String, ownerId: String, members: List<String>) {
        val group = GroupEntity(
            id = groupId,
            name = name,
            ownerId = ownerId,
            createdTimestamp = System.currentTimeMillis()
        )
        groupDao.insertGroup(group)

        val memberEntities = members.map { userId ->
            GroupMemberEntity(groupId = groupId, userId = userId, isAdmin = (userId == ownerId))
        }
        groupDao.insertMembers(memberEntities)
    }

    /** Replace a group's name + full member set (used when a group update arrives over the network). */
    suspend fun syncGroup(groupId: String, name: String, ownerId: String, members: List<String>) {
        groupDao.insertGroup(GroupEntity(id = groupId, name = name, ownerId = ownerId, createdTimestamp = System.currentTimeMillis()))
        groupDao.getGroupMembers(groupId).forEach { groupDao.deleteMember(groupId, it.userId) }
        groupDao.insertMembers(members.map { GroupMemberEntity(groupId = groupId, userId = it, isAdmin = (it == ownerId)) })
    }

    fun getAllGroups(): Flow<List<GroupEntity>> {
        return groupDao.getAllGroups()
    }
    
    suspend fun getGroupMembers(groupId: String): List<GroupMemberEntity> {
        return groupDao.getGroupMembers(groupId)
    }

    // --- Status / Stories ---
    fun getStatuses(): Flow<List<com.firefly.befirefly.data.local.entity.StatusEntity>> =
        statusDao?.getStatuses() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun insertStatus(status: com.firefly.befirefly.data.local.entity.StatusEntity) {
        statusDao?.insertStatus(status)
    }

    suspend fun deleteExpiredStatuses() {
        statusDao?.deleteExpired(System.currentTimeMillis())
    }

    suspend fun getGroup(groupId: String): GroupEntity? = groupDao.getGroupById(groupId)

    suspend fun addGroupMember(groupId: String, userId: String, isAdmin: Boolean = false) {
        groupDao.insertMember(GroupMemberEntity(groupId = groupId, userId = userId, isAdmin = isAdmin))
    }

    suspend fun removeGroupMember(groupId: String, userId: String) {
        groupDao.deleteMember(groupId, userId)
    }

    suspend fun renameGroup(groupId: String, name: String) {
        groupDao.updateGroupName(groupId, name)
    }

    /** Leave/delete a group locally (members cascade) and clear its messages. */
    suspend fun leaveGroup(groupId: String) {
        groupDao.deleteGroup(groupId)
        messageDao.deleteAllForConversation(groupId)
    }
}
