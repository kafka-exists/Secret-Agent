package com.keyforge.chat.ai

import kotlinx.serialization.Serializable

/** Supported bring-your-own-key providers. */
enum class ProviderType { GEMINI, OPENROUTER, GROQ }

/** Conversation role. */
enum class Role { SYSTEM, USER, ASSISTANT, TOOL }

@Serializable
data class ChatMessage(
    val role: Role,
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
    val name: String? = null,
)

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    /** Raw JSON arguments string, as returned by the model. */
    val arguments: String,
)

/** A single failover target: which provider, which key, which model. */
data class ChatTarget(
    val provider: ProviderType,
    val keyId: String,
    val keyLabel: String,
    val model: String,
) {
    val label: String get() = "${provider.name.lowercase().replaceFirstChar { it.uppercase() }} · $model"
}

/** Routing presets the user can pick. Each defines a provider+model preference order. */
enum class RoutingPreset(val preference: List<Pair<ProviderType, String>>) {
    FASTEST(
        listOf(
            ProviderType.GROQ to "llama-3.3-70b-versatile",
            ProviderType.GROQ to "llama-3.1-8b-instant",
            ProviderType.OPENROUTER to "google/gemini-flash-1.5",
            ProviderType.GEMINI to "gemini-1.5-flash",
        ),
    ),
    SMARTEST(
        listOf(
            ProviderType.OPENROUTER to "anthropic/claude-3.5-sonnet",
            ProviderType.OPENROUTER to "openai/gpt-4o",
            ProviderType.GEMINI to "gemini-1.5-pro",
            ProviderType.GROQ to "llama-3.3-70b-versatile",
        ),
    ),
    CHEAPEST(
        listOf(
            ProviderType.GROQ to "llama-3.1-8b-instant",
            ProviderType.GEMINI to "gemini-1.5-flash",
            ProviderType.OPENROUTER to "google/gemini-flash-1.5",
        ),
    ),
    BALANCED(
        listOf(
            ProviderType.GEMINI to "gemini-1.5-flash",
            ProviderType.GROQ to "llama-3.3-70b-versatile",
            ProviderType.OPENROUTER to "anthropic/claude-3.5-sonnet",
            ProviderType.GEMINI to "gemini-1.5-pro",
        ),
    ),
}

/** A model advertised by a provider. */
data class ModelInfo(
    val id: String,
    val label: String,
    val provider: ProviderType,
    val contextWindow: Int? = null,
)

/** What the Router asks a provider to do. */
data class ProviderRequest(
    val messages: List<ChatMessage>,
    val tools: List<com.keyforge.chat.files.ToolSpec> = emptyList(),
    val model: String,
    val temperature: Double = 0.7,
    val maxTokens: Int? = null,
) {
    /**
     * Build a request that asks the next target to continue from [partial] text
     * emitted before a failover/truncation, without repeating it.
     */
    fun continuedFrom(partial: String): ProviderRequest {
        if (partial.isBlank()) return this
        val continuation = ChatMessage(
            role = Role.USER,
            content = "Continue your previous response exactly from where it ended. " +
                "Do not repeat what you already wrote. Resume mid-sentence if needed.",
        )
        return copy(messages = messages + ChatMessage(Role.ASSISTANT, partial) + continuation)
    }

    /** Drop middle history, keep system messages and the most recent turns. */
    fun trimmed(keepRecent: Int = 4): ProviderRequest {
        if (messages.size <= keepRecent + 1) return this
        val system = messages.filter { it.role == Role.SYSTEM }
        val rest = messages.filter { it.role != Role.SYSTEM }
        return copy(messages = system + rest.takeLast(keepRecent))
    }
}

/** Incremental events emitted while streaming a reply. */
sealed interface StreamDelta {
    data class Text(val text: String) : StreamDelta
    data class ToolCall(val call: ToolCall) : StreamDelta
    data class Finish(val reason: String) : StreamDelta
    /** The Router switched targets mid-stream; surface as a subtle chip. */
    data class Switch(val target: ChatTarget, val reason: String) : StreamDelta
    data class Error(val message: String) : StreamDelta
}
