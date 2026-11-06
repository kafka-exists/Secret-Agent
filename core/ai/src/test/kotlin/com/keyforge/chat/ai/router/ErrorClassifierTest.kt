package com.keyforge.chat.ai.router

import com.google.common.truth.Truth.assertThat
import com.keyforge.chat.network.HttpException
import org.junit.Test

class ErrorClassifierTest {

    @Test
    fun `429 with Retry-After seconds header classifies as RateLimited`() {
        val ex = HttpException(429, "rate limit", mapOf("retry-after" to "30"))
        val result = ErrorClassifier.classifyHttpException(ex, now = 0L)
        assertThat(result).isInstanceOf(ClassifiedError.RateLimited::class.java)
        assertThat((result as ClassifiedError.RateLimited).cooldown.inWholeSeconds).isEqualTo(30L)
    }

    @Test
    fun `429 without header falls back to jittered backoff`() {
        val ex = HttpException(429, "rate limit", emptyMap())
        val result = ErrorClassifier.classifyHttpException(ex)
        assertThat(result).isInstanceOf(ClassifiedError.RateLimited::class.java)
        val secs = (result as ClassifiedError.RateLimited).cooldown.inWholeSeconds
        assertThat(secs).isAtLeast(1L)
        assertThat(secs).isAtMost(60L)
    }

    @Test
    fun `401 classifies as InvalidKey`() {
        val result = ErrorClassifier.classifyHttpException(HttpException(401, "unauthorized"))
        assertThat(result).isEqualTo(ClassifiedError.InvalidKey)
    }

    @Test
    fun `403 classifies as InvalidKey`() {
        val result = ErrorClassifier.classifyHttpException(HttpException(403, "forbidden"))
        assertThat(result).isEqualTo(ClassifiedError.InvalidKey)
    }

    @Test
    fun `404 with model in body classifies as ModelUnavailable`() {
        val result = ErrorClassifier.classifyHttpException(HttpException(404, "model not found"))
        assertThat(result).isEqualTo(ClassifiedError.ModelUnavailable)
    }

    @Test
    fun `402 classifies as QuotaExhausted with Gemini-Pacific reset`() {
        val result = ErrorClassifier.classifyHttpException(HttpException(402, "quota"), now = 0L)
        assertThat(result).isInstanceOf(ClassifiedError.QuotaExhausted::class.java)
        assertThat((result as ClassifiedError.QuotaExhausted).resetAt).isGreaterThan(0L)
    }

    @Test
    fun `400 with context message classifies as ContextTooLong`() {
        val result = ErrorClassifier.classifyHttpException(HttpException(400, "request exceeds maximum context length"))
        assertThat(result).isEqualTo(ClassifiedError.ContextTooLong)
    }

    @Test
    fun `500 classifies as Transient`() {
        assertThat(ErrorClassifier.classifyHttpException(HttpException(500, "oops")))
            .isEqualTo(ClassifiedError.Transient)
    }

    @Test
    fun `503 classifies as Transient`() {
        assertThat(ErrorClassifier.classifyHttpException(HttpException(503, "unavailable")))
            .isEqualTo(ClassifiedError.Transient)
    }

    @Test
    fun `socket timeout classifies as Transient`() {
        val result = ErrorClassifier.classifyThrowable(java.net.SocketTimeoutException("timeout"))
        assertThat(result).isEqualTo(ClassifiedError.Transient)
    }

    @Test
    fun `unknown host classifies as Transient`() {
        val result = ErrorClassifier.classifyThrowable(java.net.UnknownHostException("nope"))
        assertThat(result).isEqualTo(ClassifiedError.Transient)
    }

    @Test
    fun `unknown status surfaces as Unknown with message`() {
        val result = ErrorClassifier.classifyHttpException(HttpException(418, "teapot"))
        assertThat(result).isInstanceOf(ClassifiedError.Unknown::class.java)
        assertThat((result as ClassifiedError.Unknown).message).contains("418")
    }

    @Test
    fun `Retry-After HTTP-date is parsed into seconds`() {
        // A fixed future date string. Parsing depends on the runtime zone offset,
        // but the result must be a positive Duration capped at 1h.
        val header = "Wed, 21 Oct 2099 07:28:00 GMT"
        val result = ErrorClassifier.parseRetryAfter(header, now = 0L)
        assertThat(result.inWholeSeconds).isAtLeast(1L)
        assertThat(result.inWholeSeconds).isAtMost(3600L)
    }
}
