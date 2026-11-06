package com.keyforge.chat.data.repo

import com.keyforge.chat.data.crypto.KeystoreCrypto
import com.keyforge.chat.data.db.ApiKeyDao
import com.keyforge.chat.data.db.ApiKeyEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistence layer for API keys. Operates on [ApiKeyEntity] (a storage type that
 * lives in core:data) so this module has no upstream dependency on core:ai. The
 * feature layer maps entities to the domain [com.keyforge.chat.ai.keys.ApiKey].
 */
@Singleton
class ApiKeyRepository @Inject constructor(
    private val dao: ApiKeyDao,
    private val crypto: KeystoreCrypto,
) {
    fun observeAll(): Flow<List<ApiKeyEntity>> = dao.observeAll()
    suspend fun getAll(): List<ApiKeyEntity> = dao.getAll()

    /** Add a key. Plaintext is encrypted immediately; the caller discards it. */
    suspend fun add(provider: String, label: String, plaintext: String): ApiKeyEntity {
        val entity = ApiKeyEntity(
            id = UUID.randomUUID().toString(),
            label = label,
            provider = provider,
            encryptedValue = crypto.encrypt(plaintext),
            enabled = true,
            orderIndex = (dao.getAll().maxOfOrNull { it.orderIndex } ?: -1) + 1,
            healthState = "HEALTHY",
            lastUsedAt = 0L,
            useCount = 0,
            cooldownUntil = 0L,
            resetAt = 0L,
        )
        dao.upsert(entity)
        return entity
    }

    suspend fun update(entity: ApiKeyEntity) = dao.upsert(entity)
    suspend fun setEnabled(id: String, enabled: Boolean) {
        val existing = dao.getAll().firstOrNull { it.id == id } ?: return
        dao.upsert(existing.copy(enabled = enabled))
    }
    suspend fun delete(id: String) = dao.delete(id)
    suspend fun reorder(orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id -> dao.reorder(id, index) }
    }

    /** Decrypt a key for use. Only call at the moment of dispatch. */
    fun decrypt(entity: ApiKeyEntity): String = crypto.decrypt(entity.encryptedValue)
    fun maskPlaintext(plaintext: String): String = crypto.mask(plaintext)
}
