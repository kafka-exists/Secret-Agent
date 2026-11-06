package com.keyforge.chat.ai.providers

import com.keyforge.chat.ai.AiProvider
import com.keyforge.chat.ai.ChatTarget
import com.keyforge.chat.ai.ModelInfo
import com.keyforge.chat.ai.ProviderRequest
import com.keyforge.chat.ai.ProviderType
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

/**
 * Groq and OpenRouter share the OpenAI-compatible wire format. Subclasses set
 * the base URL, the auth header builder, and the model-list endpoint/headers.
 */
abstract class OpenAiCompatibleProvider(
    protected val client: HttpClient,
    protected val type: ProviderType,
) : AiProvider {

    abstract val baseUrl: String
    abstract val modelsPath: String
    /** Extra headers appended to every request (referer, app title, etc.). */
    abstract fun extraHeaders(): Map<String, String>

    final override val type: ProviderType get() = type

    override suspend fun listModels(apiKey: String): ApiResult<List<ModelInfo>> = runCatching {
        val response = client.get("$baseUrl$modelsPath") {
            authHeader(apiKey).forEach { (k, v) -> header(k, v) }
            extraHeaders().forEach { (k, v) -> header(k, v) }
        }
        if (!response.status.isSuccess()) {
            return@runCatching ApiResult.Failure(ApiError.Http(response.status.value, response.bodyAsText()))
        }
        val body: OpenAiModelsResponse = Json { ignoreUnknownKeys = true }.decodeFromString(response.bodyAsText())
        ApiResult.Success(body.data.map {
            ModelInfo(
                id = it.id,
                label = it.name ?: it.id,
                provider = type,
                contextWindow = it.contextLength,
            )
        })
    }.getOrElse { ApiResult.Failure(ApiError.Network(it)) }

    override suspend fun testKey(apiKey: String): ApiResult<Unit> {
        val result = listModels(apiKey)
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Failure -> result
        }
    }

    override fun streamChat(
        request: ProviderRequest,
        target: ChatTarget,
        apiKey: String,
    ): Flow<StreamDelta> = flow {
        val body = OpenAiWire.buildBody(request, type)
        val headers = buildMap {
            putAll(authHeader(apiKey))
            putAll(extraHeaders())
        }
        client.streamSse("$baseUrl/chat/completions", headers, body).collect { event ->
            for (delta in OpenAiWire.parseChunk(event.data)) emit(delta)
        }
    }

    /** Provider-specific auth header (Authorization: Bearer for both, here for clarity). */
    protected open fun authHeader(apiKey: String): Map<String, String> =
        mapOf("Authorization" to "Bearer $apiKey")
}

class GroqProvider(
    client: HttpClient,
) : OpenAiCompatibleProvider(client, ProviderType.GROQ) {
    override val baseUrl = "https://api.groq.com/openai/v1"
    override val modelsPath = "/models"
    override fun extraHeaders(): Map<String, String> = emptyMap()
}

class OpenRouterProvider(
    client: HttpClient,
) : OpenAiCompatibleProvider(client, ProviderType.OPENROUTER) {
    override val baseUrl = "https://openrouter.ai/api/v1"
    override val modelsPath = "/models"
    // OpenRouter asks clients to identify themselves.
    override fun extraHeaders(): Map<String, String> = mapOf(
        "HTTP-Referer" to "https://keyforge.chat",
        "X-Title" to "KeyForge Chat",
    )
}
