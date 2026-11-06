package com.keyforge.chat.ai.providers

import com.keyforge.chat.ai.AiProvider
import com.keyforge.chat.ai.ChatTarget
import com.keyforge.chat.ai.ModelInfo
import com.keyforge.chat.ai.ProviderRequest
import com.keyforge.chat.ai.ProviderType
import com.keyforge.chat.ai.Role
import com.keyforge.chat.ai.StreamDelta
import com.keyforge.chat.network.ApiError
import com.keyforge.chat.network.ApiResult
import com.keyforge.chat.network.streamSse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Google Generative Language API. Key travels in the `x-goog-api-key` header
 * (never the URL). `streamGenerateContent` with `alt=sse` returns Server-Sent
 * Events, so the same SseClient is reused. Function-calling format is mapped to
 * the OpenAI-style ToolCall the Router expects.
 */
class GeminiProvider(
    private val client: HttpClient,
) : AiProvider {

    override val type = ProviderType.GEMINI
    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta"

    override suspend fun listModels(apiKey: String): ApiResult<List<ModelInfo>> = runCatching {
        val response = client.get("$baseUrl/models") {
            header("x-goog-api-key", apiKey)
        }
        if (!response.status.isSuccess()) {
            return@runCatching ApiResult.Failure(ApiError.Http(response.status.value, response.bodyAsText()))
        }
        val obj = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val models = obj["models"]?.jsonArray ?: emptyList()
        ApiResult.Success(models.map { m ->
            val mo = m.jsonObject
            val name = mo["name"]?.jsonPrimitive?.contentOrNull?.removePrefix("models/") ?: ""
            ModelInfo(
                id = name,
                label = mo["displayName"]?.jsonPrimitive?.contentOrNull ?: name,
                provider = ProviderType.GEMINI,
                contextWindow = mo["inputTokenLimit"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
            )
        }.filter { it.id.startsWith("gemini") })
    }.getOrElse { ApiResult.Failure(ApiError.Network(it)) }

    override suspend fun testKey(apiKey: String): ApiResult<Unit> {
        val r = listModels(apiKey)
        return when (r) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Failure -> r
        }
    }

    override fun streamChat(
        request: ProviderRequest,
        target: ChatTarget,
        apiKey: String,
    ): Flow<StreamDelta> = flow {
        val url = "$baseUrl/models/${target.model}:streamGenerateContent?alt=sse"
        val body = buildBody(request)
        client.streamSse(url, mapOf("x-goog-api-key" to apiKey), body).collect { event ->
            for (delta in parseChunk(event.data)) emit(delta)
        }
    }

    private fun buildBody(request: ProviderRequest): String {
        val system = request.messages.firstOrNull { it.role == Role.SYSTEM }?.content
        val contents = request.messages.filter { it.role != Role.SYSTEM }.map { m ->
            buildJsonObject {
                put("role", if (m.role == Role.ASSISTANT) "model" else "user")
                putJsonArray("parts") {
                    add(buildJsonObject { put("text", m.content) })
                }
            }
        }
        val root = buildJsonObject {
            if (system != null) {
                put("systemInstruction", buildJsonObject {
                    putJsonArray("parts") { add(buildJsonObject { put("text", system) }) }
                })
            }
            putJsonArray("contents") { contents.forEach { add(it) } }
            put("generationConfig", buildJsonObject {
                put("temperature", request.temperature)
                request.maxTokens?.let { put("maxOutputTokens", it) }
            })
        }
        return Json.encodeToString(JsonObject.serializer(), root)
    }

    private fun parseChunk(payload: String): List<StreamDelta> {
        if (payload.isBlank()) return emptyList()
        val element = runCatching { Json.parseToJsonElement(payload) }.getOrNull() ?: return emptyList()
        val obj = element.jsonObject
        val candidates = obj["candidates"]?.jsonArray ?: return emptyList()
        return candidates.mapNotNull { cEl ->
            val c = cEl.jsonObject
            val content = c["content"]?.jsonObject
            val parts = content?.get("parts")?.jsonArray
            val text = parts?.firstOrNull { it.jsonObject["text"] != null }
                ?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
            val finish = c["finishReason"]?.jsonPrimitive?.contentOrNull
            when {
                text != null -> StreamDelta.Text(text)
                finish != null -> StreamDelta.Finish(finish.lowercase())
                else -> null
            }
        }
    }
}
