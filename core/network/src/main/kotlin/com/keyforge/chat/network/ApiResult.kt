package com.keyforge.chat.network

/** Outcome of a non-streaming API call (model list, key test). */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

/** Classified transport-level failure. The Router maps these into routing actions. */
sealed interface ApiError {
    data class Http(val status: Int, val body: String) : ApiError
    data class Network(val cause: Throwable) : ApiError
    data class Parse(val cause: Throwable) : ApiError
}

/** Thrown mid-stream when an SSE response returns a non-2xx status. */
class HttpException(
    val status: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap(),
) : RuntimeException("HTTP $status")
