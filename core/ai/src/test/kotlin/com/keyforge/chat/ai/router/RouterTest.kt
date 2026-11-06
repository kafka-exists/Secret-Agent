package com.keyforge.chat.ai.router

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.keyforge.chat.ai.AiProvider
import com.keyforge.chat.ai.ChatMessage
import com.keyforge.chat.ai.ChatTarget
import com.keyforge.chat.ai.ModelInfo
import com.keyforge.chat.ai.ProviderRequest
import com.keyforge.chat.ai.ProviderType
import com.keyforge.chat.ai.Role
import com.keyforge.chat.ai.StreamDelta
import com.keyforge.chat.ai.keys.ApiKey
import com.keyforge.chat.ai.keys.KeyPool
import com.keyforge.chat.network.ApiResult
import com.keyforge.chat.network.HttpException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * A fake provider whose behavior is scripted by a queue of "scripts". Each
 * script is a list of (emit, then maybe throw). This drives the Router through
 * every row of the routing table without touching the network.
 */
class FakeProvider(
    private val providerType: ProviderType,
    private val scripts: ArrayDeque<Script>,
) : AiProvider {
    override val type = providerType

    data class Script(val deltas: List<StreamDelta>, val error: Throwable? = null)

    override suspend fun listModels(apiKey: String): ApiResult<List<ModelInfo>> = ApiResult.Success(emptyList())
    override suspend fun testKey(apiKey: String): ApiResult<Unit> = ApiResult.Success(Unit)

    override fun streamChat(
        request: ProviderRequest,
        target: ChatTarget,
        apiKey: String,
    ): Flow<StreamDelta> = flow {
        val script = scripts.removeFirstOrNull() ?: return@flow
        script.deltas.forEach { emit(it) }
        script.error?.let { throw it }
    }
}

private fun key(id: String, provider: ProviderType) = ApiKey(
    id = id, label = "key-$id", provider = provider,
    encryptedValue = "enc-$id", orderIndex = 0,
)

private fun target(provider: ProviderType, keyId: String, model: String) =
    ChatTarget(provider, keyId, "key-$keyId", model)

class RouterTest {

    private fun router(
        provider: AiProvider,
        targets: List<ChatTarget>,
        keys: List<ApiKey>,
    ): Router = Router(
        providers = mapOf(provider.type to provider),
        keyPool = KeyPool().apply { setKeys(keys) },
        chainProvider = { targets },
        keyResolver = { id -> keys.firstOrNull { it.id == id }?.encryptedValue },
    )

    @Test
    fun `happy path streams text then finish`() = runTest {
        val provider = FakeProvider(ProviderType.GROQ, ArrayDeque(listOf(
            FakeProvider.Script(listOf(
                StreamDelta.Text("Hello "),
                StreamDelta.Text("world"),
                StreamDelta.Finish("stop"),
            )),
        )))
        val router = router(provider, listOf(target(ProviderType.GROQ, "k1", "llama")), listOf(key("k1", ProviderType.GROQ)))

        router.stream(ProviderRequest(listOf(ChatMessage(Role.USER, "hi")), model = "llama")).test {
            assertThat(awaitItem()).isEqualTo(StreamDelta.Text("Hello "))
            assertThat(awaitItem()).isEqualTo(StreamDelta.Text("world"))
            assertThat(awaitItem()).isEqualTo(StreamDelta.Finish("stop"))
            awaitComplete()
        }
    }

    @Test
    fun `429 on key 1 switches to key 2 mid-stream`() = runTest {
        val provider = FakeProvider(ProviderType.GROQ, ArrayDeque(listOf(
            // Key 1: emits partial then 429s
            FakeProvider.Script(listOf(StreamDelta.Text("Hel")), HttpException(429, "rate limit", mapOf("retry-after" to "1"))),
            // Key 2: succeeds, continues the text
            FakeProvider.Script(listOf(StreamDelta.Text("lo world"), StreamDelta.Finish("stop"))),
        )))
        val keys = listOf(key("k1", ProviderType.GROQ), key("k2", ProviderType.GROQ))
        val targets = listOf(target(ProviderType.GROQ, "k1", "llama"), target(ProviderType.GROQ, "k2", "llama"))
        val router = router(provider, targets, keys)

        router.stream(ProviderRequest(listOf(ChatMessage(Role.USER, "hi")), model = "llama")).test {
            assertThat(awaitItem()).isEqualTo(StreamDelta.Text("Hel"))
            val switch = awaitItem()
            assertThat(switch).isInstanceOf(StreamDelta.Switch::class.java)
            assertThat(awaitItem()).isEqualTo(StreamDelta.Text("lo world"))
            assertThat(awaitItem()).isEqualTo(StreamDelta.Finish("stop"))
            awaitComplete()
        }
    }

    @Test
    fun `401 marks key Invalid and skips to next`() = runTest {
        val provider = FakeProvider(ProviderType.GROQ, ArrayDeque(listOf(
            FakeProvider.Script(emptyList(), HttpException(401, "bad key")),
            FakeProvider.Script(listOf(StreamDelta.Text("ok"), StreamDelta.Finish("stop"))),
        )))
        val keys = listOf(key("k1", ProviderType.GROQ), key("k2", ProviderType.GROQ))
        val targets = listOf(target(ProviderType.GROQ, "k1", "m"), target(ProviderType.GROQ, "k2", "m"))
        val router = router(provider, targets, keys)

        router.stream(ProviderRequest(listOf(ChatMessage(Role.USER, "hi")), model = "m")).test {
            val switch = awaitItem()
            assertThat(switch).isInstanceOf(StreamDelta.Switch::class.java)
            assertThat(awaitItem()).isEqualTo(StreamDelta.Text("ok"))
            assertThat(awaitItem()).isEqualTo(StreamDelta.Finish("stop"))
            awaitComplete()
        }
    }

    @Test
    fun `transient 5xx retries same target once then switches`() = runTest {
        val provider = FakeProvider(ProviderType.GROQ, ArrayDeque(listOf(
            FakeProvider.Script(emptyList(), HttpException(503, "down")),  // first attempt
            FakeProvider.Script(emptyList(), HttpException(503, "down")),  // retry (still failing)
            FakeProvider.Script(listOf(StreamDelta.Text("recovered"), StreamDelta.Finish("stop"))), // next target
        )))
        val keys = listOf(key("k1", ProviderType.GROQ), key("k2", ProviderType.GROQ))
        val targets = listOf(target(ProviderType.GROQ, "k1", "m"), target(ProviderType.GROQ, "k2", "m"))
        val router = router(provider, targets, keys)

        router.stream(ProviderRequest(listOf(ChatMessage(Role.USER, "hi")), model = "m")).test {
            val switch = awaitItem()
            assertThat(switch).isInstanceOf(StreamDelta.Switch::class.java)
            assertThat(awaitItem()).isEqualTo(StreamDelta.Text("recovered"))
            assertThat(awaitItem()).isEqualTo(StreamDelta.Finish("stop"))
            awaitComplete()
        }
    }

    @Test
    fun `truncation finish_reason length triggers continuation and stitch`() = runTest {
        val provider = FakeProvider(ProviderType.GROQ, ArrayDeque(listOf(
            FakeProvider.Script(listOf(StreamDelta.Text("partial..."), StreamDelta.Finish("length"))),
            FakeProvider.Script(listOf(StreamDelta.Text(" complete"), StreamDelta.Finish("stop"))),
        )))
        val keys = listOf(key("k1", ProviderType.GROQ), key("k2", ProviderType.GROQ))
        val targets = listOf(target(ProviderType.GROQ, "k1", "m"), target(ProviderType.GROQ, "k2", "m"))
        val router = router(provider, targets, keys)

        router.stream(ProviderRequest(listOf(ChatMessage(Role.USER, "hi")), model = "m")).test {
            assertThat(awaitItem()).isEqualTo(StreamDelta.Text("partial..."))
            val switch = awaitItem()
            assertThat(switch).isInstanceOf(StreamDelta.Switch::class.java)
            assertThat(awaitItem()).isEqualTo(StreamDelta.Text(" complete"))
            assertThat(awaitItem()).isEqualTo(StreamDelta.Finish("stop"))
            awaitComplete()
        }
    }

    @Test
    fun `model not found switches to next model in chain`() = runTest {
        val provider = FakeProvider(ProviderType.GROQ, ArrayDeque(listOf(
            FakeProvider.Script(emptyList(), HttpException(404, "model not found")),
            FakeProvider.Script(listOf(StreamDelta.Text("ok"), StreamDelta.Finish("stop"))),
        )))
        val keys = listOf(key("k1", ProviderType.GROQ))
        val targets = listOf(
            target(ProviderType.GROQ, "k1", "missing-model"),
            target(ProviderType.GROQ, "k1", "fallback-model"),
        )
        val router = router(provider, targets, keys)

        router.stream(ProviderRequest(listOf(ChatMessage(Role.USER, "hi")), model = "missing-model")).test {
            val switch = awaitItem()
            assertThat(switch).isInstanceOf(StreamDelta.Switch::class.java)
            assertThat(awaitItem()).isEqualTo(StreamDelta.Text("ok"))
            assertThat(awaitItem()).isEqualTo(StreamDelta.Finish("stop"))
            awaitComplete()
        }
    }

    @Test
    fun `all targets exhausted emits Error`() = runTest {
        val provider = FakeProvider(ProviderType.GROQ, ArrayDeque(listOf(
            FakeProvider.Script(emptyList(), HttpException(401, "bad")),
            FakeProvider.Script(emptyList(), HttpException(401, "bad")),
        )))
        val keys = listOf(key("k1", ProviderType.GROQ), key("k2", ProviderType.GROQ))
        val targets = listOf(target(ProviderType.GROQ, "k1", "m"), target(ProviderType.GROQ, "k2", "m"))
        val router = router(provider, targets, keys)

        router.stream(ProviderRequest(listOf(ChatMessage(Role.USER, "hi")), model = "m")).test {
            // two switches then error
            assertThat(awaitItem()).isInstanceOf(StreamDelta.Switch::class.java)
            assertThat(awaitItem()).isInstanceOf(StreamDelta.Switch::class.java)
            val err = awaitItem()
            assertThat(err).isInstanceOf(StreamDelta.Error::class.java)
            awaitComplete()
        }
    }
}
