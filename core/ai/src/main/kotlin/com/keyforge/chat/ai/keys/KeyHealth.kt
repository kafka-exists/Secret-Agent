package com.keyforge.chat.ai.keys

import kotlin.time.Duration

/** Persisted health state of a single key. The UI status chip reads this. */
data class KeyHealth(
    val state: KeyState = KeyState.HEALTHY,
    val lastUsedAt: Long = 0L,
    val useCount: Int = 0,
    /** Epoch millis when a cooling key becomes usable again. */
    val cooldownUntil: Long = 0L,
    /** Epoch millis when an exhausted key resets (Gemini: midnight Pacific). */
    val resetAt: Long = 0L,
    val lastError: String? = null,
) {
    fun isUsable(now: Long): Boolean = when (state) {
        KeyState.HEALTHY -> true
        KeyState.COOLING_DOWN -> now >= cooldownUntil
        KeyState.EXHAUSTED -> now >= resetAt
        KeyState.INVALID -> false
    }
}

enum class KeyState { HEALTHY, COOLING_DOWN, EXHAUSTED, INVALID }

/** Domain key object (the encrypted value never lives here — only its id). */
data class ApiKey(
    val id: String,
    val label: String,
    val provider: com.keyforge.chat.ai.ProviderType,
    /** Encrypted at rest in Room; the plaintext is only decrypted in-memory. */
    val encryptedValue: String,
    val enabled: Boolean = true,
    val orderIndex: Int = 0,
    val health: KeyHealth = KeyHealth(),
)

/** A key resolved for use, paired with its decrypted plaintext. */
data class ResolvedKey(
    val apiKey: ApiKey,
    val plaintext: String,
)

/** Mark a key after a classified error. Returns the new health; selection honors it. */
fun ApiKey.applyError(
    error: com.keyforge.chat.ai.router.ClassifiedError,
    now: Long,
): ApiKey = copy(health = health.applyError(error, now))

fun KeyHealth.applyError(
    error: com.keyforge.chat.ai.router.ClassifiedError,
    now: Long,
): KeyHealth = when (error) {
    is com.keyforge.chat.ai.router.ClassifiedError.RateLimited ->
        copy(state = KeyState.COOLING_DOWN, cooldownUntil = now + error.cooldown.inWholeMilliseconds, lastError = "Rate limited")
    is com.keyforge.chat.ai.router.ClassifiedError.QuotaExhausted ->
        copy(state = KeyState.EXHAUSTED, resetAt = error.resetAt, lastError = "Quota exhausted")
    com.keyforge.chat.ai.router.ClassifiedError.InvalidKey ->
        copy(state = KeyState.INVALID, lastError = "Invalid key")
    com.keyforge.chat.ai.router.ClassifiedError.Transient ->
        copy(lastError = "Transient error") // do not break the key for one blip
    com.keyforge.chat.ai.router.ClassifiedError.ModelUnavailable,
    com.keyforge.chat.ai.router.ClassifiedError.ContextTooLong,
    com.keyforge.chat.ai.router.ClassifiedError.Truncated ->
        this // model/chain problems, not the key's fault
    is com.keyforge.chat.ai.router.ClassifiedError.Unknown ->
        copy(lastError = error.message)
}

fun KeyHealth.markUsed(now: Long): KeyHealth =
    copy(lastUsedAt = now, useCount = useCount + 1)
