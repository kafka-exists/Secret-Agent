package com.keyforge.chat.ai.router

import com.keyforge.chat.network.ApiError
import com.keyforge.chat.network.HttpException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Outcome of classifying a failure against the spec's routing table (section
 * 4.2). Each result maps to the action the Router takes. This is the single
 * source of truth for "what does this error mean for the key and the chain?"
 */
sealed interface ClassifiedError {
    /** HTTP 429 / rate limit. Cool the key down per Retry-After/backoff, switch keys same model. */
    data class RateLimited(val cooldown: Duration) : ClassifiedError
    /** Daily quota exhausted. Mark key Exhausted until reset, switch keys. */
    data class QuotaExhausted(val resetAt: Long) : ClassifiedError
    /** 401/403/invalid key. Mark Invalid, skip permanently until edited. */
    data object InvalidKey : ClassifiedError
    /** 5xx / timeout / network. Retry once, then next target. */
    data object Transient : ClassifiedError
    /** Model not found or feature unsupported. Next model in chain. */
    data object ModelUnavailable : ClassifiedError
    /** Context too long. Switch to a larger-context model or trim history. */
    data object ContextTooLong : ClassifiedError
    /** Output truncated (finish_reason=length or unbalanced braces). Continue + stitch. */
    data object Truncated : ClassifiedError
    /** Something we can't classify — surfaces as an error to the user. */
    data class Unknown(val message: String) : ClassifiedError
}

/**
 * Pure error classification. No I/O, no clock dependency for the deterministic
 * rows — `now` is injected so tests are stable. Implements every row of the
 * section 4.2 table.
 */
object ErrorClassifier {

    fun classifyHttpException(ex: HttpException, now: Long = System.currentTimeMillis()): ClassifiedError {
        val body = ex.body.lowercase()
        val retryAfter = parseRetryAfter(ex.headers["retry-after"], now)
        return when (ex.status) {
            429 -> ClassifiedError.RateLimited(retryAfter)
            401, 403 -> ClassifiedError.InvalidKey
            404 -> if (body.contains("model") || body.contains("not found")) ClassifiedError.ModelUnavailable
                   else ClassifiedError.Unknown("HTTP 404")
            400 -> when {
                body.contains("context") || body.contains("too long") || body.contains("maximum context") ->
                    ClassifiedError.ContextTooLong
                body.contains("quota") || body.contains("exhausted") || body.contains("limit") ->
                    ClassifiedError.RateLimited(retryAfter)
                body.contains("rate") -> ClassifiedError.RateLimited(retryAfter)
                else -> ClassifiedError.Unknown("HTTP 400: ${ex.body.take(160)}")
            }
            402 -> ClassifiedError.QuotaExhausted(parseGeminiReset(now))
            in 500..599 -> ClassifiedError.Transient
            else -> ClassifiedError.Unknown("HTTP ${ex.status}")
        }
    }

    fun classifyApiError(error: ApiError, now: Long = System.currentTimeMillis()): ClassifiedError =
        when (error) {
            is ApiError.Http -> classifyHttpException(
                HttpException(error.status, error.body),
                now,
            )
            is ApiError.Network -> ClassifiedError.Transient
            is ApiError.Parse -> ClassifiedError.Unknown("Parse error: ${error.cause.message}")
        }

    fun classifyThrowable(t: Throwable, now: Long = System.currentTimeMillis()): ClassifiedError =
        when (t) {
            is HttpException -> classifyHttpException(t, now)
            is java.net.SocketTimeoutException,
            is java.net.UnknownHostException,
            is java.io.IOException -> ClassifiedError.Transient
            else -> ClassifiedError.Unknown(t.message ?: t::class.simpleName ?: "unknown")
        }

    /** Retry-After seconds, or exponential backoff with jitter as a fallback. */
    internal fun parseRetryAfter(header: String?, now: Long): Duration {
        if (header == null) return backoffWithJitter(attempt = 1)
        header.toIntOrNull()?.let { return it.coerceIn(1, 3600).seconds }
        // HTTP-date form
        runCatching {
            val rfc = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
            val parsed = java.time.ZonedDateTime.parse(header, rfc).toInstant().toEpochMilli()
            val seconds = ((parsed - now) / 1000).coerceAtLeast(1L)
            return seconds.coerceAtMost(3600L).seconds
        }
        return backoffWithJitter(attempt = 1)
    }

    /** Exponential backoff with full jitter: 1–2s, 2–4s, 4–8s… capped at 60s. */
    internal fun backoffWithJitter(attempt: Int): Duration {
        val base = (1L shl (attempt - 1).coerceAtMost(6)) // 1,2,4,8,16,32,64
        val cap = base.coerceAtMost(60L)
        val jitter = (1L..cap).random()
        return jitter.seconds
    }

    /** Gemini daily quota resets at midnight Pacific. */
    internal fun parseGeminiReset(now: Long): Long {
        val zone = java.time.ZoneId.of("America/Los_Angeles")
        val tomorrow = java.time.LocalDate.now(zone).plusDays(1).atStartOfDay(zone)
        return tomorrow.toInstant().toEpochMilli()
    }
}
