package com.firefly.befirefly.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.firefly.befirefly.data.local.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity)

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getContactById(id: String): ContactEntity?
    
    @Query("UPDATE contacts SET name = :newName WHERE id = :id")
    suspend fun updateContactName(id: String, newName: String)
    
    @Query("UPDATE contacts SET isPinned = :isPinned WHERE id = :id")
    suspend fun updatePinStatus(id: String, isPinned: Boolean)

    @Query("UPDATE contacts SET isVerified = :verified WHERE id = :id")
    suspend fun updateVerified(id: String, verified: Boolean)
    
    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteContact(id: String)
}
