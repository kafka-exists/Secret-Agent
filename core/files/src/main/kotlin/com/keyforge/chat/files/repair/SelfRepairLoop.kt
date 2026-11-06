package com.keyforge.chat.files.repair

import com.keyforge.chat.ai.ChatMessage
import com.keyforge.chat.ai.ProviderRequest
import com.keyforge.chat.ai.Role
import com.keyforge.chat.ai.StreamDelta
import com.keyforge.chat.ai.router.Router
import com.keyforge.chat.files.GeneratedFile
import com.keyforge.chat.files.parser.FileBlockParser
import com.keyforge.chat.files.validate.ReferenceChecker
import com.keyforge.chat.files.validate.Validators
import kotlinx.coroutines.flow.toList

/**
 * The self-repair loop (section 5.4-E). For a bundle of files:
 *  1. Validate each file + the cross-file reference graph.
 *  2. If anything fails, send the failing file + the exact error list back to
 *     the model with "fix and re-emit the complete file", routed through the
 *     Router (so it can fail over to another key/target).
 *  3. Re-parse the response, re-validate, repeat up to [maxAttempts] times.
 *  4. If still failing, mark the file(s) NeedsAttention with the error list.
 *
 * The status callback lets the file card show Generating → Validating →
 * Fixing (attempt N) → Verified / Needs attention.
 */
class SelfRepairLoop(
    private val router: Router,
    private val maxAttempts: Int = 3,
) {

    sealed interface Outcome {
        data class Verified(val files: List<GeneratedFile>) : Outcome
        data class NeedsAttention(val files: List<GeneratedFile>, val errors: List<String>) : Outcome
    }

    suspend fun repair(
        initialFiles: List<GeneratedFile>,
        onStatus: (path: String, status: String) -> Unit = { _, _ -> },
    ): Outcome {
        var current = initialFiles
        var attempt = 0
        var lastErrors = emptyList<String>()

        while (attempt < maxAttempts) {
            attempt++
            // 1. Validate each file.
            val failures = mutableListOf<Pair<GeneratedFile, List<String>>>()
            for (f in current) {
                onStatus(f.path, if (attempt == 1) "Validating" else "Fixing (attempt $attempt)")
                val report = Validators.validate(f, current)
                if (!report.ok) failures.add(f to report.errors)
            }
            // 2. Validate cross-file references.
            val refReport = ReferenceChecker.check(current)
            if (!refReport.ok) {
                failures.add(
                    current.firstOrNull { it.name == "manifest.json" } ?: current.first()
                        to refReport.missing.map { "Missing referenced file: $it" },
                )
            }

            if (failures.isEmpty()) {
                current.forEach { onStatus(it.path, "Verified") }
                return Outcome.Verified(current)
            }

            lastErrors = failures.flatMap { it.second }
            if (attempt == maxAttempts) break

            // 3. Ask the model to fix the worst-offending file.
            val (worstFile, worstErrors) = failures.maxByOrNull { it.second.size }!!
            val fixed = requestFix(worstFile, worstErrors, current)
            if (fixed == null) {
                // Model produced no parseable file; keep current and let the next attempt try.
                continue
            }
            current = current.filterNot { it.path == fixed.path } + fixed
        }

        current.forEach { onStatus(it.path, "Needs attention") }
        return Outcome.NeedsAttention(current, lastErrors)
    }

    /** Send a fix request through the Router and parse the first file out of the reply. */
    private suspend fun requestFix(
        broken: GeneratedFile,
        errors: List<String>,
        bundle: List<GeneratedFile>,
    ): GeneratedFile? {
        val systemPrompt = """
            You are a file-repair agent. The user received a file that failed validation.
            Fix the file and re-emit the COMPLETE, corrected file using the file-block protocol.
            Output ONLY the file block, no commentary. Use this exact format:

            <<<FILE path="${broken.path}">>>
            ...complete corrected content...
            <<<END_FILE>>>

            Do not wrap content in markdown fences. Do not use placeholders.
        """.trimIndent()

        val userPrompt = buildString {
            appendLine("File: ${broken.path}")
            appendLine("Validation errors:")
            errors.forEach { appendLine("- $it") }
            appendLine()
            appendLine("Current (broken) content:")
            appendLine(String(broken.content, Charsets.UTF_8))
            appendLine()
            appendLine("Other files in the bundle (for reference):")
            bundle.filterNot { it.path == broken.path }.take(10).forEach {
                appendLine("--- ${it.path} ---")
                appendLine(String(it.content, Charsets.UTF_8).take(500))
            }
        }

        val request = ProviderRequest(
            messages = listOf(
                ChatMessage(Role.SYSTEM, systemPrompt),
                ChatMessage(Role.USER, userPrompt),
            ),
            model = "auto", // Router fills in the actual model from the chain
        )

        val parser = FileBlockParser()
        val deltas = router.stream(request).toList()
        for (d in deltas) {
            if (d is StreamDelta.Text) parser.feed(d.text)
        }
        return parser.finish().firstOrNull()
    }
}
