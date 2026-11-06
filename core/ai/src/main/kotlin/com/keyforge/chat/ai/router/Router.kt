package com.keyforge.chat.ai.router

import com.keyforge.chat.ai.AiProvider
import com.keyforge.chat.ai.ChatTarget
import com.keyforge.chat.ai.ProviderRequest
import com.keyforge.chat.ai.ProviderType
import com.keyforge.chat.ai.StreamDelta
import com.keyforge.chat.ai.keys.KeyPool
import com.keyforge.chat.network.HttpException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach

/**
 * The failover Router. Given a [ProviderRequest], it streams deltas from the
 * first usable target; on a classifiable error it switches to the next target
 * and continues into the SAME logical response, stitching any partial text
 * already emitted. Every switch emits a [StreamDelta.Switch] so the UI can show
 * the subtle "Switched to Groq · llama… (key 2)" chip.
 *
 * Routing policy (section 4.2 table):
 *  - RateLimited / QuotaExhausted: cool/exhaust the key, same model, next key.
 *  - InvalidKey: skip key permanently, next key same model.
 *  - Transient (5xx/timeout/network): retry once, then next target.
 *  - ModelUnavailable: next model in chain (different provider allowed).
 *  - ContextTooLong: trim history, same target; if still failing, larger model.
 *  - Truncated: continue-from-partial on next target, stitch.
 *  - Unknown: surface as StreamDelta.Error and stop.
 */
class Router(
    private val providers: Map<ProviderType, AiProvider>,
    private val keyPool: KeyPool,
    private val chainProvider: () -> List<ChatTarget>,
    private val keyResolver: (keyId: String) -> String?,
    private val onHealthChange: (Map<String, com.keyforge.chat.ai.keys.ApiKey>) -> Unit = {},
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    /** Maximum switches per single stream() call; guards against pathological loops. */
    private val maxSwitches = 8

    /** Stream a request with full failover. Emits text deltas continuously. */
    open fun stream(request: ProviderRequest): Flow<StreamDelta> = flow {
        var current = request
        var partial = StringBuilder()
        var attempts = 0
        var transientRetried = false

        val targets = chainProvider()
        val queue = ArrayDeque(targets)

        while (queue.isNotEmpty() && attempts < maxSwitches) {
            val target = queue.removeFirst()
            val apiKey = keyResolver(target.keyId) ?: continue
            val provider = providers[target.provider] ?: continue
            keyPool.markUsed(target.keyId)
            onHealthChange(keyPool.snapshot().associateBy { it.id })

            var finishReason: String? = null
            try {
                provider.streamChat(current, target, apiKey).collect { delta ->
                    when (delta) {
                        is StreamDelta.Text -> {
                            partial.append(delta.text)
                            emit(delta)
                        }
                        is StreamDelta.Finish -> finishReason = delta.reason
                        is StreamDelta.ToolCall -> emit(delta)
                        is StreamDelta.Switch, is StreamDelta.Error -> emit(delta)
                    }
                }
            } catch (t: Throwable) {
                val classified = ErrorClassifier.classifyThrowable(t, now())
                when (classified) {
                    is ClassifiedError.RateLimited -> {
                        keyPool.applyError(target.keyId, classified)
                        emit(StreamDelta.Switch(target, "Rate limited, switching key"))
                        continue
                    }
                    is ClassifiedError.QuotaExhausted -> {
                        keyPool.applyError(target.keyId, classified)
                        emit(StreamDelta.Switch(target, "Quota exhausted, switching key"))
                        continue
                    }
                    ClassifiedError.InvalidKey -> {
                        keyPool.applyError(target.keyId, ClassifiedError.InvalidKey)
                        emit(StreamDelta.Switch(target, "Invalid key, switching"))
                        continue
                    }
                    ClassifiedError.Transient -> {
                        if (!transientRetried) {
                            transientRetried = true
                            queue.addFirst(target) // retry the same target once
                            continue
                        }
                        emit(StreamDelta.Switch(target, "Server error, switching target"))
                        continue
                    }
                    ClassifiedError.ModelUnavailable -> {
                        emit(StreamDelta.Switch(target, "Model unavailable, next model"))
                        continue
                    }
                    ClassifiedError.ContextTooLong -> {
                        current = current.trimmed()
                        queue.addFirst(target)
                        continue
                    }
                    ClassifiedError.Truncated -> {
                        current = current.continuedFrom(partial.toString())
                        emit(StreamDelta.Switch(target, "Continuing after truncation"))
                        continue
                    }
                    is ClassifiedError.Unknown -> {
                        emit(StreamDelta.Error(classified.message))
                        return@flow
                    }
                }
                onHealthChange(keyPool.snapshot().associateBy { it.id })
                continue
            }

            // Normal finish. If truncated (length), continue on the next target and stitch.
            if (finishReason == "length" || finishReason == "max_tokens") {
                if (queue.isNotEmpty()) {
                    current = current.continuedFrom(partial.toString())
                    emit(StreamDelta.Switch(target, "Output truncated, continuing"))
                    attempts++
                    continue
                }
                emit(StreamDelta.Finish(finishReason!!))
            }
            onHealthChange(keyPool.snapshot().associateBy { it.id })
            return@flow
        }

        if (attempts >= maxSwitches) {
            emit(StreamDelta.Error("Exhausted all failover targets"))
        }
    }.onEach {
        // Persist health after each terminal-ish event so state survives restarts.
        if (it is StreamDelta.Error || it is StreamDelta.Finish) {
            onHealthChange(keyPool.snapshot().associateBy { id -> id.id })
        }
    }
}
