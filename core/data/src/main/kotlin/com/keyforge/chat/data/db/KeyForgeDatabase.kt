package com.keyforge.chat.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        GeneratedFileEntity::class,
        ApiKeyEntity::class,
        ModelEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class KeyForgeDatabase : RoomDatabase() {
    abstract fun conversations(): ConversationDao
    abstract fun messages(): MessageDao
    abstract fun generatedFiles(): GeneratedFileDao
    abstract fun apiKeys(): ApiKeyDao
    abstract fun models(): ModelDao

    companion object {
        const val NAME = "keyforge.db"
    }
}
