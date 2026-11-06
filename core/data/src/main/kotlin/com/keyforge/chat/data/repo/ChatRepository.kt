package com.keyforge.chat.data.repo

import com.keyforge.chat.data.db.ConversationDao
import com.keyforge.chat.data.db.ConversationEntity
import com.keyforge.chat.data.db.MessageDao
import com.keyforge.chat.data.db.MessageEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val conversations: ConversationDao,
    private val messages: MessageDao,
) {
    fun observeConversations(): Flow<List<ConversationEntity>> = conversations.observeAll()

    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> =
        messages.observeForConversation(conversationId)

    suspend fun createConversation(title: String, preset: String): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        conversations.upsert(ConversationEntity(id, title.ifBlank { "New chat" }, now, now, preset))
        return id
    }

    suspend fun appendMessage(
        conversationId: String,
        role: String,
        content: String,
        model: String? = null,
        provider: String? = null,
        toolCallsJson: String? = null,
    ): String {
        val id = UUID.randomUUID().toString()
        messages.upsert(
            MessageEntity(
                id = id,
                conversationId = conversationId,
                role = role,
                content = content,
                createdAt = System.currentTimeMillis(),
                model = model,
                provider = provider,
                toolCallsJson = toolCallsJson,
            ),
        )
        conversations.rename(conversationId, titleFrom(content), System.currentTimeMillis())
        return id
    }

    suspend fun updateMessage(id: String, content: String) {
        val existing = messages.getById(id) ?: return
        messages.upsert(existing.copy(content = content))
    }

    private fun titleFrom(content: String): String =
        content.lineSequence().firstOrNull()?.take(48)?.ifBlank { "New chat" } ?: "New chat"
}
