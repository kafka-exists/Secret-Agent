package com.keyforge.chat.ai

import com.keyforge.chat.network.ApiResult
import kotlinx.coroutines.flow.Flow

/**
 * One provider implementation. Providers translate a [ProviderRequest] into
 * their wire format, stream deltas back, and classify transport errors by
 * throwing [com.keyforge.chat.network.HttpException] for HTTP failures (so the
 * Router can fail over) and emitting [StreamDelta.Finish] on normal completion.
 */
interface AiProvider {
    val type: ProviderType

    /** Live model list, cached upstream. */
    suspend fun listModels(apiKey: String): ApiResult<List<ModelInfo>>

    /** Tiny real call to verify a key works. */
    suspend fun testKey(apiKey: String): ApiResult<Unit>

    /** Stream a chat completion. Throws on classifiable HTTP/network errors. */
    fun streamChat(
        request: ProviderRequest,
        target: ChatTarget,
        apiKey: String,
    ): Flow<StreamDelta>
}
