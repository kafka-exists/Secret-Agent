package com.keyforge.chat.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val routingPreset: String,
)

@Entity(
    tableName = "messages",
    indices = [Index("conversationId")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,           // USER / ASSISTANT / SYSTEM / TOOL
    val content: String,
    val createdAt: Long,
    val model: String? = null,
    val provider: String? = null,
    val toolCallsJson: String? = null,
)

@Entity(
    tableName = "generated_files",
    indices = [Index("messageId")],
)
data class GeneratedFileEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val path: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val contentRef: String,     // URI to internal storage or base64 cache
    val isZip: Boolean,
    val status: String,         // GENERATING / VALIDATING / FIXING / VERIFIED / NEEDS_ATTENTION
    val validationErrors: String? = null,
    val createdAt: Long,
)

@Entity(tableName = "api_keys", indices = [Index("provider")])
data class ApiKeyEntity(
    @PrimaryKey val id: String,
    val label: String,
    val provider: String,
    val encryptedValue: String,   // KeystoreCrypto.encrypt(plaintext)
    val enabled: Boolean,
    val orderIndex: Int,
    // Health (persisted circuit breaker state)
    val healthState: String,      // HEALTHY / COOLING_DOWN / EXHAUSTED / INVALID
    val lastUsedAt: Long,
    val useCount: Int,
    val cooldownUntil: Long,
    val resetAt: Long,
    val lastError: String? = null,
)

@Entity(tableName = "models", indices = [Index("provider")])
data class ModelEntity(
    @PrimaryKey val id: String,   // "provider:modelId"
    val provider: String,
    val modelId: String,
    val label: String,
    val contextWindow: Int?,
    val fetchedAt: Long,
)
