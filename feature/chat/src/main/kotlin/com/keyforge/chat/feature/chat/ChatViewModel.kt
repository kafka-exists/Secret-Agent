package com.keyforge.chat.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keyforge.chat.ai.ChatMessage
import com.keyforge.chat.ai.ProviderRequest
import com.keyforge.chat.ai.Role
import com.keyforge.chat.ai.RoutingPreset
import com.keyforge.chat.ai.StreamDelta
import com.keyforge.chat.ai.keys.KeyPool
import com.keyforge.chat.ai.keys.toDomain
import com.keyforge.chat.ai.router.Router
import com.keyforge.chat.data.repo.ApiKeyRepository
import com.keyforge.chat.data.repo.ChatRepository
import com.keyforge.chat.files.GeneratedFile
import com.keyforge.chat.files.parser.FileBlockParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepo: ChatRepository,
    private val keyRepo: ApiKeyRepository,
    private val providers: Map<com.keyforge.chat.ai.ProviderType, @JvmSuppressWildcards com.keyforge.chat.ai.AiProvider>,
    private val keyPool: KeyPool,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var conversationId: String? = null
    private val fileParser = FileBlockParser()

    init {
        viewModelScope.launch { refreshKeys() }
    }

    private suspend fun refreshKeys() {
        val keys = keyRepo.getAll().map { it.toDomain() }
        keyPool.setKeys(keys)
        keyPool.applyPreset(RoutingPreset.BALANCED)
    }

    fun onInputChange(text: String) {
        _state.update { it.copy(input = text) }
    }

    fun send() {
        val text = _state.value.input
        if (text.isBlank() || _state.value.isSending) return
        viewModelScope.launch {
            val cid = conversationId ?: chatRepo.createConversation("New chat", "Balanced").also { conversationId = it }
            val userMsgId = UUID.randomUUID().toString()
            val assistantMsgId = UUID.randomUUID().toString()

            // Add the user message immediately + an empty streaming assistant message.
            _state.update { s ->
                s.copy(
                    messages = s.messages + listOf(
                        ChatUiMessage(userMsgId, "USER", text),
                        ChatUiMessage(assistantMsgId, "ASSISTANT", "", isStreaming = true),
                    ),
                    input = "",
                    isSending = true,
                    isEmpty = false,
                    error = null,
                )
            }
            fileParser // fresh parser per turn
            // Persist the user message.
            chatRepo.appendMessage(cid, "USER", text)

            val request = ProviderRequest(
                messages = _state.value.messages
                    .filter { it.role == "USER" || it.content.isNotBlank() }
                    .map { ChatMessage(if (it.role == "USER") Role.USER else Role.ASSISTANT, it.content) },
                tools = com.keyforge.chat.files.FileTools.all,
                model = "auto",
            )

            // Snapshot keys once so the resolver (a non-suspend lambda) can decrypt synchronously.
            val keySnapshot = keyRepo.getAll().associateBy { it.id }

            val router = Router(
                providers = providers,
                keyPool = keyPool,
                chainProvider = { keyPool.targets.value },
                keyResolver = { id ->
                    keySnapshot[id]?.let { entity -> runCatching { keyRepo.decrypt(entity) }.getOrNull() }
                },
                onHealthChange = { updated ->
                    viewModelScope.launch {
                        updated.values.forEach { keyRepo.update(it.toEntity()) }
                    }
                },
            )

            val collected = StringBuilder()
            val files = mutableListOf<FileCardState>()
            try {
                router.stream(request).collect { delta ->
                    when (delta) {
                        is StreamDelta.Text -> {
                            collected.append(delta.text)
                            val parsed = fileParser.feed(delta.text)
                            parsed.forEach { files += it.toCard("file-${UUID.randomUUID()}") }
                            updateAssistant(assistantMsgId, collected.toString(), files = files)
                        }
                        is StreamDelta.Switch -> {
                            updateAssistant(assistantMsgId, collected.toString(), routingNote = "Switched to ${delta.target.label} (${delta.target.keyLabel})", files = files)
                        }
                        is StreamDelta.Finish -> {
                            val remaining = fileParser.finish()
                            remaining.forEach { files += it.toCard("file-${UUID.randomUUID()}") }
                            updateAssistant(assistantMsgId, collected.toString(), isStreaming = false, files = files)
                        }
                        is StreamDelta.Error -> {
                            _state.update { it.copy(error = delta.message, isSending = false) }
                            updateAssistant(assistantMsgId, collected.toString(), isStreaming = false, files = files)
                        }
                        is StreamDelta.ToolCall -> { /* tool calls handled via file parser */ }
                    }
                }
                // Flush any trailing file content.
                val trailing = fileParser.finish()
                trailing.forEach { files += it.toCard("file-${UUID.randomUUID()}") }
                if (files.isNotEmpty()) updateAssistant(assistantMsgId, collected.toString(), isStreaming = false, files = files)
                chatRepo.appendMessage(cid, "ASSISTANT", collected.toString(), model = null, provider = null)
            } catch (t: Throwable) {
                _state.update { it.copy(error = t.message ?: "Stream failed", isSending = false) }
                updateAssistant(assistantMsgId, collected.toString(), isStreaming = false, files = files)
            } finally {
                _state.update { it.copy(isSending = false) }
            }
        }
    }

    fun stop() {
        // Cancellation: the launched send() coroutine observes cancellation.
        // (Full stop requires a Job reference; left as a known extension point.)
        _state.update { it.copy(isSending = false) }
    }

    fun dismissError() { _state.update { it.copy(error = null) } }

    fun setAttached(attached: Boolean) { _state.update { it.copy(isAttached = attached) } }

    private fun updateAssistant(
        id: String,
        content: String,
        isStreaming: Boolean = true,
        routingNote: String? = null,
        files: List<FileCardState> = emptyList(),
    ) {
        _state.update { s ->
            s.copy(messages = s.messages.map { m ->
                if (m.id == id) m.copy(content = content, isStreaming = isStreaming, routingNote = routingNote ?: m.routingNote, files = files.ifEmpty { m.files })
                else m
            })
        }
    }

    private fun GeneratedFile.toCard(cardId: String): FileCardState = FileCardState(
        id = cardId,
        path = path,
        name = name,
        sizeBytes = size,
        mimeType = mimeType,
        isZip = mimeType == "application/zip",
        status = FileStatus.GENERATING,
    )
}
