package com.keyforge.chat.ai.keys

import com.keyforge.chat.ai.ChatTarget
import com.keyforge.chat.ai.ProviderType
import com.keyforge.chat.ai.RoutingPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory key pool + circuit breaker + selection policy. The Router holds one
 * of these. Health is the source of truth for selection; persistence (Room) is
 * kept in sync by the Router through [snapshot]/[restore].
 *
 * Selection policy: least-recently-used among enabled, currently-usable keys
 * for the requested provider — this spreads load across keys instead of
 * hammering the first one. Keys past their cooldown automatically become usable
 * again, so the circuit breaker is self-healing.
 */
class KeyPool(
    private val now: () -> Long = ::System.currentTimeMillis,
) {
    private val _keys = MutableStateFlow<List<ApiKey>>(emptyList())
    val keys: StateFlow<List<ApiKey>> = _keys.asStateFlow()

    private val _targets = MutableStateFlow<List<ChatTarget>>(emptyList())
    val targets: StateFlow<List<ChatTarget>> = _targets.asStateFlow()

    fun setKeys(keys: List<ApiKey>) { _keys.value = keys.sortedBy { it.orderIndex } }

    fun snapshot(): List<ApiKey> = _keys.value

    fun restore(keys: List<ApiKey>) { setKeys(keys) }

    /** Build the failover chain from a preset + the currently-known keys. */
    fun applyPreset(preset: RoutingPreset) {
        val chain = mutableListOf<ChatTarget>()
        for ((provider, model) in preset.preference) {
            keysFor(provider).forEach { key ->
                chain.add(ChatTarget(provider, key.id, key.label, model))
            }
        }
        _targets.value = chain
    }

    fun setTargets(targets: List<ChatTarget>) { _targets.value = targets }

    /** Pick the next usable key for [provider], LRU order, skipping cooled-down ones. */
    fun selectKey(provider: ProviderType): ApiKey? =
        keysFor(provider)
            .filter { it.enabled && it.health.isUsable(now()) }
            .minByOrNull { it.health.lastUsedAt }

    private fun keysFor(provider: ProviderType): List<ApiKey> =
        _keys.value.filter { it.provider == provider }

    /** Mark a key after a classified error and persist the new state. */
    fun applyError(keyId: String, error: com.keyforge.chat.ai.router.ClassifiedError) {
        _keys.update { list ->
            list.map { if (it.id == keyId) it.applyError(error, now()) else it }
        }
    }

    fun markUsed(keyId: String) {
        _keys.update { list ->
            list.map { if (it.id == keyId) it.copy(health = it.health.markUsed(now())) else it }
        }
    }

    /** Remaining usable keys for a provider (for the "switch to next key" path). */
    fun usableKeysFor(provider: ProviderType): List<ApiKey> =
        keysFor(provider).filter { it.enabled && it.health.isUsable(now()) }
}
