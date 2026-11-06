package com.keyforge.chat.network

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** A single dispatched SSE `data:` payload (already concatenation-joined). */
data class SseEvent(val data: String)

/**
 * Opens a streaming POST and emits one [SseEvent] per blank-line-delimited
 * event. `data:` lines are joined with newlines; a `data: [DONE]` sentinel ends
 * the stream. Used by all three providers so the parsing contract is shared.
 *
 * Non-2xx responses raise [HttpException] with the body so the Router can
 * classify it (rate limit, quota, auth, server error).
 */
fun HttpClient.streamSse(
    urlString: String,
    headers: Map<String, String>,
    body: String,
): Flow<SseEvent> = flow {
    val response = post(urlString) {
        headers.forEach { (k, v) -> header(k, v) }
        header(HttpHeaders.Accept, "text/event-stream")
        contentType(ContentType.Application.Json)
        setBody(body)
    }
    if (!response.status.isSuccess()) {
        val headerMap = response.headers.entries().associate { it.key.lowercase() to it.value.joinToString(",") }
        throw HttpException(response.status.value, response.bodyAsText(), headerMap)
    }
    val channel = response.bodyAsChannel()
    val buffer = StringBuilder()
    while (!channel.isClosedForRead) {
        val line = channel.readUTF8Line() ?: break
        when {
            line.startsWith("data:") -> {
                val payload = line.removePrefix("data:").trim()
                if (payload == "[DONE]") return@flow
                if (buffer.isNotEmpty()) buffer.append('\n')
                buffer.append(payload)
            }
            line.isBlank() -> {
                if (buffer.isNotEmpty()) {
                    emit(SseEvent(buffer.toString()))
                    buffer.clear()
                }
            }
            // event/id/retry fields and `:` comments are ignored
        }
    }
    if (buffer.isNotEmpty()) emit(SseEvent(buffer.toString()))
}
