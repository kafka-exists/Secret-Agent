package com.keyforge.chat.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(c: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE conversations SET title = :title, updatedAt = :ts WHERE id = :id")
    suspend fun rename(id: String, title: String, ts: Long)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :cid ORDER BY createdAt ASC")
    fun observeForConversation(cid: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(m: MessageEntity)

    @Query("DELETE FROM messages WHERE conversationId = :cid")
    suspend fun deleteForConversation(cid: String)

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: String): MessageEntity?
}

@Dao
interface GeneratedFileDao {
    @Query("SELECT * FROM generated_files WHERE messageId = :mid ORDER BY createdAt ASC")
    fun observeForMessage(mid: String): Flow<List<GeneratedFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(f: GeneratedFileEntity)

    @Query("SELECT * FROM generated_files WHERE id = :id")
    suspend fun getById(id: String): GeneratedFileEntity?

    @Delete
    suspend fun delete(f: GeneratedFileEntity)
}

@Dao
interface ApiKeyDao {
    @Query("SELECT * FROM api_keys ORDER BY provider, orderIndex")
    fun observeAll(): Flow<List<ApiKeyEntity>>

    @Query("SELECT * FROM api_keys ORDER BY provider, orderIndex")
    suspend fun getAll(): List<ApiKeyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(k: ApiKeyEntity)

    @Update
    suspend fun update(k: ApiKeyEntity)

    @Query("DELETE FROM api_keys WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE api_keys SET orderIndex = :order WHERE id = :id")
    suspend fun reorder(id: String, order: Int)
}

@Dao
interface ModelDao {
    @Query("SELECT * FROM models WHERE provider = :provider")
    suspend fun forProvider(provider: String): List<ModelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(models: List<ModelEntity>)

    @Query("DELETE FROM models WHERE provider = :provider")
    suspend fun deleteForProvider(provider: String)
}
