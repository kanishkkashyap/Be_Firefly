package com.firefly.befirefly.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.firefly.befirefly.data.local.dao.GroupDao
import com.firefly.befirefly.data.local.entity.GroupEntity
import com.firefly.befirefly.data.local.entity.GroupMemberEntity
import com.firefly.befirefly.data.local.dao.MessageDao
import com.firefly.befirefly.data.local.dao.ContactDao
import com.firefly.befirefly.data.local.dao.PendingMessageDao
import com.firefly.befirefly.data.local.entity.MessageEntity
import com.firefly.befirefly.data.local.entity.ContactEntity
import com.firefly.befirefly.data.local.entity.PendingMessageEntity

@Database(entities = [MessageEntity::class, ContactEntity::class, GroupEntity::class, GroupMemberEntity::class, PendingMessageEntity::class], version = 10, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun contactDao(): ContactDao
    abstract fun groupDao(): GroupDao
    abstract fun pendingMessageDao(): PendingMessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "befirefly_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
