package com.keyforge.chat.files.repair

import com.google.common.truth.Truth.assertThat
import com.keyforge.chat.ai.ProviderRequest
import com.keyforge.chat.ai.StreamDelta
import com.keyforge.chat.ai.keys.KeyPool
import com.keyforge.chat.ai.router.Router
import com.keyforge.chat.files.GeneratedFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Drives the SelfRepairLoop through the spec's required scenario: a fake Router
 * that returns broken output first, then fixed output. Verifies the loop
 * detects, re-requests, and lands on Verified.
 */
class ScriptedRouter(
    private val scripts: ArrayDeque<List<StreamDelta>>,
) : Router(
    providers = emptyMap(),
    keyPool = KeyPool(),
    chainProvider = { emptyList() },
    keyResolver = { null },
) {
    override fun stream(request: ProviderRequest): Flow<StreamDelta> = flow {
        scripts.removeFirstOrNull()?.forEach { emit(it) }
    }
}

class SelfRepairLoopTest {

    @Test fun `broken JSON is detected and auto-repaired`() = runTest {
        val brokenJson = """{"name": }""" // invalid
        val fixedJson = """{"name":"test","version":"1.0"}"""
        val brokenFile = GeneratedFile("manifest.json", brokenJson.toByteArray())

        val router = ScriptedRouter(ArrayDeque(listOf(
            listOf(StreamDelta.Text("<<<FILE path=\"manifest.json\">>>\n$fixedJson\n<<<END_FILE>>>")),
        )))
        val loop = SelfRepairLoop(router, maxAttempts = 3)

        val statuses = mutableListOf<Pair<String, String>>()
        val outcome = loop.repair(listOf(brokenFile)) { p, s -> statuses += p to s }

        assertThat(outcome).isInstanceOf(SelfRepairLoop.Outcome.Verified::class.java)
        val verified = (outcome as SelfRepairLoop.Outcome.Verified).files.first()
        assertThat(String(verified.content).trim()).isEqualTo(fixedJson)
        assertThat(statuses).contains("manifest.json" to "Fixing (attempt 2)")
        assertThat(statuses).contains("manifest.json" to "Verified")
    }

    @Test fun `file that never gets fixed becomes NeedsAttention`() = runTest {
        val alwaysBroken = GeneratedFile("bad.js", "function f( {".toByteArray())
        val router = ScriptedRouter(ArrayDeque(List(3) {
            listOf(StreamDelta.Text("<<<FILE path=\"bad.js\">>>\nfunction f( {\n<<<END_FILE>>>"))
        }))
        val loop = SelfRepairLoop(router, maxAttempts = 3)
        val outcome = loop.repair(listOf(alwaysBroken))
        assertThat(outcome).isInstanceOf(SelfRepairLoop.Outcome.NeedsAttention::class.java)
    }

    @Test fun `valid file passes on first attempt`() = runTest {
        val good = GeneratedFile("ok.js", "console.log('hi');".toByteArray())
        val router = ScriptedRouter(ArrayDeque())
        val loop = SelfRepairLoop(router, maxAttempts = 3)
        val outcome = loop.repair(listOf(good))
        assertThat(outcome).isInstanceOf(SelfRepairLoop.Outcome.Verified::class.java)
    }
}
