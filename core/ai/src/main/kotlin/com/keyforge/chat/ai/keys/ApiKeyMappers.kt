package com.keyforge.chat.ai.keys

import com.keyforge.chat.data.db.ApiKeyEntity
import com.keyforge.chat.ai.ProviderType

/** Bridge the Room [ApiKeyEntity] (core:data) to the domain [ApiKey] (core:ai). */
fun ApiKeyEntity.toDomain(): ApiKey = ApiKey(
    id = id,
    label = label,
    provider = runCatching { ProviderType.valueOf(provider) }.getOrDefault(ProviderType.GROQ),
    encryptedValue = encryptedValue,
    enabled = enabled,
    orderIndex = orderIndex,
    health = KeyHealth(
        state = runCatching { KeyState.valueOf(healthState) }.getOrDefault(KeyState.HEALTHY),
        lastUsedAt = lastUsedAt,
        useCount = useCount,
        cooldownUntil = cooldownUntil,
        resetAt = resetAt,
        lastError = lastError,
    ),
)

fun ApiKey.toEntity(): ApiKeyEntity = ApiKeyEntity(
    id = id,
    label = label,
    provider = provider.name,
    encryptedValue = encryptedValue,
    enabled = enabled,
    orderIndex = orderIndex,
    healthState = health.state.name,
    lastUsedAt = health.lastUsedAt,
    useCount = health.useCount,
    cooldownUntil = health.cooldownUntil,
    resetAt = health.resetAt,
    lastError = health.lastError,
)
