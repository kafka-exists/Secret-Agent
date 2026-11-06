package com.keyforge.chat.ai.providers

import com.keyforge.chat.ai.ModelInfo
import com.keyforge.chat.ai.ProviderRequest
import com.keyforge.chat.ai.ProviderType
import com.keyforge.chat.ai.Role
import com.keyforge.chat.ai.StreamDelta
import com.keyforge.chat.ai.ToolCall
import com.keyforge.chat.ai.ToolSpec
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Shared OpenAI-compatible request/response mapping. Groq and OpenRouter both
 * speak this protocol; only base URL + headers differ. Kept here so the two
 * providers stay in lockstep and the wire format is audited in one place.
 */
internal object OpenAiWire {

    fun buildBody(request: ProviderRequest, provider: ProviderType): String {
        val json = Json { encodeDefaults = false; explicitNulls = false }
        val root = buildJsonObject {
            put("model", request.model)
            put("stream", true)
            put("temperature", request.temperature)
            request.maxTokens?.let { put("max_tokens", it) }
            putJsonArray("messages") {
                for (m in request.messages) {
                    add(buildJsonObject {
                        put("role", m.role.wire())
                        put("content", m.content)
                        if (m.toolCalls.isNotEmpty()) {
                            putJsonArray("tool_calls") {
                                m.toolCalls.forEach { tc ->
                                    add(buildJsonObject {
                                        put("id", tc.id)
                                        put("type", "function")
                                        putJsonObject("function") {
                                            put("name", tc.name)
                                            put("arguments", tc.arguments)
                                        }
                                    })
                                }
                            }
                        }
                        m.toolCallId?.let { put("tool_call_id", it) }
                        m.name?.let { put("name", it) }
                    })
                }
            }
            if (request.tools.isNotEmpty()) {
                putJsonArray("tools") {
                    request.tools.forEach { add(it.toOpenAi()) }
                }
            }
            if (provider == ProviderType.GROQ) {
                // Groq benefits from parallel tool calls being explicit.
                put("parallel_tool_calls", true)
            }
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    /** Parse a single SSE `data:` JSON chunk into deltas. */
    fun parseChunk(payload: String): List<StreamDelta> {
        if (payload.isBlank() || payload == "[DONE]") return emptyList()
        val element = runCatching { Json.parseToJsonElement(payload) }.getOrNull() ?: return emptyList()
        val obj = element.jsonObject
        val choices = obj["choices"]?.jsonArray ?: return emptyList()
        return choices.mapNotNull { choiceEl ->
            val choice = choiceEl.jsonObject
            val delta = choice["delta"]?.jsonObject ?: return@mapNotNull null
            val text = delta["content"]?.jsonPrimitive?.contentOrNull
            val toolCalls = delta["tool_calls"]?.jsonArray?.map { tcEl ->
                val tc = tcEl.jsonObject
                val fn = tc["function"]?.jsonObject
                ToolCall(
                    id = tc["id"]?.jsonPrimitive?.contentOrNull ?: ("call_${tc["index"]?.jsonPrimitive?.contentOrNull ?: 0}"),
                    name = fn?.get("name")?.jsonPrimitive?.contentOrNull ?: "",
                    arguments = fn?.get("arguments")?.jsonPrimitive?.contentOrNull ?: "",
                )
            }
            val finish = choice["finish_reason"]?.jsonPrimitive?.contentOrNull
            when {
                text != null -> StreamDelta.Text(text)
                toolCalls != null && toolCalls.isNotEmpty() -> StreamDelta.ToolCall(toolCalls.first())
                finish != null -> StreamDelta.Finish(finish)
                else -> null
            }
        }
    }

    private fun Role.wire(): String = when (this) {
        Role.SYSTEM -> "system"
        Role.USER -> "user"
        Role.ASSISTANT -> "assistant"
        Role.TOOL -> "tool"
    }

    private fun ToolSpec.toOpenAi(): JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", name)
            put("description", description)
            // Parameters are already a JSON schema string; parse back to object.
            val params = runCatching { Json.parseToJsonElement(parametersJson) }.getOrNull()
                ?: JsonPrimitive("object")
            put("parameters", params)
        }
    }
}

@Serializable
internal data class OpenAiModelsResponse(
    val data: List<OpenAiModel> = emptyList(),
)

@Serializable
internal data class OpenAiModel(
    val id: String,
    @SerialName("name") val name: String? = null,
    @SerialName("context_length") val contextLength: Int? = null,
)
