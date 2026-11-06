package com.keyforge.chat.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Single shared HTTP client. Logging is permanently off so API keys are never
 * written to logcat — keys travel only in headers, never in URLs.
 */
fun createHttpClient(
    json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
        explicitNulls = false
        coerceInputValues = true
    },
): HttpClient = HttpClient(OkHttp) {
    engine {
        config { retryOnConnectionFailure(true) }
    }
    install(ContentNegotiation) { json(json) }
    // Deliberately no body logging: keys must never appear in logs.
    install(Logging) { level = LogLevel.NONE }
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        requestTimeoutMillis = 60_000
        socketTimeoutMillis = 120_000
    }
}
