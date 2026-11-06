package com.keyforge.chat.feature.chat

import com.keyforge.chat.ai.StreamDelta
import com.keyforge.chat.files.GeneratedFile

/** UI-facing message model. */
data class ChatUiMessage(
    val id: String,
    val role: String,           // "USER" / "ASSISTANT"
    val content: String,
    val isStreaming: Boolean = false,
    val providerLabel: String? = null,
    val routingNote: String? = null,
    val files: List<FileCardState> = emptyList(),
)

/** State of a single generated file card (section 5.3 / 5.4-E). */
data class FileCardState(
    val id: String,
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val isZip: Boolean,
    val status: FileStatus,
    val errors: List<String> = emptyList(),
)

enum class FileStatus { GENERATING, VALIDATING, FIXING, VERIFIED, NEEDS_ATTENTION }

/** Aggregated UI state for the chat screen. */
data class ChatUiState(
    val messages: List<ChatUiMessage> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    val isAttached: Boolean = false,        // user scrolled away from bottom
    val presetLabel: String = "Balanced",
    val error: String? = null,
    val isEmpty: Boolean = true,
) {
    val canSend: Boolean get() = input.isNotBlank() && !isSending
}
