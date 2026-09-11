package com.ridenotify.app.wa_reader.data.conversations.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ObservedConversationEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ConversationDatabase : RoomDatabase() {
    abstract fun observedConversationDao(): ObservedConversationDao

    companion object {
        const val DATABASE_NAME = "observed_conversations.db"

        fun create(context: Context): ConversationDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                ConversationDatabase::class.java,
                DATABASE_NAME,
            ).build()
    }
}
