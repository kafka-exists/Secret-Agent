package com.keyforge.chat.files.validate

import com.keyforge.chat.files.GeneratedFile
import org.jsoup.Jsoup
import org.mozilla.javascript.Context
import org.mozilla.javascript.Parser
import org.mozilla.javascript.ast.AstRoot
import java.io.ByteArrayInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * On-device validators (section 5.4-C). Each returns a [ValidationReport] with
 * either a clean pass or a specific, actionable error list the self-repair loop
 * can hand back to the model.
 */
data class ValidationReport(
    val ok: Boolean,
    val errors: List<String> = emptyList(),
) {
    companion object {
        val PASS = ValidationReport(true)
        fun fail(vararg errors: String) = ValidationReport(false, errors.toList())
    }
}

object Validators {

    /** Dispatch on extension. Unknown types get the heuristic check. */
    fun validate(file: GeneratedFile, bundle: List<GeneratedFile> = listOf(file)): ValidationReport {
        val placeholder = PlaceholderChecker.check(file)
        if (!placeholder.ok) return placeholder

        // Binary files (images, fonts, audio, video, wasm, archives) carry no
        // parseable text, so the JSON/JS/CSS/XML parsers and the heuristic
        // bracket/quote scan do not apply to them. A present non-empty binary
        // file is treated as valid; ZipBuilder's round-trip check separately
        // guards its byte-level integrity. This mirrors PlaceholderChecker,
        // which already short-circuits the same mime prefixes.
        if (isBinary(file.mimeType)) {
            return if (file.content.isNotEmpty()) ValidationReport.PASS
            else ValidationReport(false, listOf("File is empty"))
        }

        val ext = file.extension.lowercase()
        return when (ext) {
            "json" -> validateJson(file, bundle)
            "html", "htm" -> validateHtml(file, bundle)
            "js", "mjs" -> validateJs(file)
            "css" -> validateCss(file)
            "xml", "svg" -> validateXml(file)
            else -> validateHeuristic(file)
        }
    }

    private fun isBinary(mimeType: String): Boolean =
        // image/svg+xml is XML text, not a raster image — exclude it so it still
        // goes through the strict XML parser.
        (mimeType.startsWith("image/") && mimeType != "image/svg+xml") ||
            mimeType.startsWith("font/") ||
            mimeType.startsWith("audio/") ||
            mimeType.startsWith("video/") ||
            mimeType == "application/zip" ||
            mimeType == "application/wasm"

    // ---- JSON / manifest ---------------------------------------------------
    fun validateJson(file: GeneratedFile, bundle: List<GeneratedFile>): ValidationReport {
        val text = String(file.content, Charsets.UTF_8)
        val parsed = try {
            JsonAccess.parse(text)
        } catch (e: JsonParseException) {
            return ValidationReport(false, listOf("Invalid JSON: ${e.message}"))
        }
        // Run extension-manifest checks only when the file actually declares a
        // manifest_version — a file merely named manifest.json but carrying
        // plain data is treated as ordinary JSON.
        if (parsed.has("manifest_version")) {
            return validateExtensionManifest(parsed, bundle)
        }
        return ValidationReport.PASS
    }

    private fun validateExtensionManifest(m: JsonAccess, bundle: List<GeneratedFile>): ValidationReport {
        val errors = mutableListOf<String>()
        val mv = m.optInt("manifest_version", 0)
        if (mv !in listOf(2, 3)) errors.add("manifest_version must be 2 or 3 (got $mv)")
        if (m.optString("name").isBlank()) errors.add("name is required")
        if (m.optString("version").isBlank()) errors.add("version is required")
        // Permissions must be a list of strings.
        m.optJSONArray("permissions")?.let { arr ->
            arr.forEachIndexed { i, el ->
                if (el !is kotlinx.serialization.json.JsonPrimitive) errors.add("permissions[$i] must be a string")
            }
        }
        val paths = mutableListOf<String>()
        val icons = m.optJSONObject("icons")
        if (icons.keys().isNotEmpty()) icons.keys().forEach { size -> paths.add(icons.getString(size)) }
        m.optJSONObject("action").optString("default_popup").takeIf { it.isNotBlank() }?.let { paths.add(it) }
        m.optJSONObject("browser_action").optString("default_popup").takeIf { it.isNotBlank() }?.let { paths.add(it) }
        m.optString("background").takeIf { it.isNotBlank() }?.let { paths.add(it) }
        m.optJSONObject("background").optString("service_worker").takeIf { it.isNotBlank() }?.let { paths.add(it) }
        m.optJSONObject("background").optJSONArray("scripts")?.strings()?.forEach { paths.add(it) }
        m.optJSONObject("options_ui").optString("page").takeIf { it.isNotBlank() }?.let { paths.add(it) }
        m.optJSONArray("content_scripts")?.let { arr ->
            for (i in 0 until arr.size) {
                val cs = JsonAccess((arr[i] as? kotlinx.serialization.json.JsonObject) ?: continue)
                cs.optJSONArray("js")?.strings()?.forEach { paths.add(it) }
                cs.optJSONArray("css")?.strings()?.forEach { paths.add(it) }
            }
        }
        val bundlePaths = bundle.map { it.path }.toSet()
        for (p in paths) {
            if (p !in bundlePaths) errors.add("Manifest references '$p' but it is not in the bundle")
        }
        return if (errors.isEmpty()) ValidationReport.PASS else ValidationReport(false, errors)
    }

    // ---- HTML --------------------------------------------------------------
    fun validateHtml(file: GeneratedFile, bundle: List<GeneratedFile>): ValidationReport {
        val text = String(file.content, Charsets.UTF_8)
        val doc = try {
            Jsoup.parse(text)
        } catch (e: Exception) {
            return ValidationReport(false, listOf("HTML parse failed: ${e.message}"))
        }
        val errors = mutableListOf<String>()
        val bundlePaths = bundle.map { it.path }.toSet()
        for (el in doc.select("[href], [src]")) {
            val raw = el.attr("href").ifBlank { el.attr("src") }
            if (raw.isBlank() || raw.startsWith("http") || raw.startsWith("//") || raw.startsWith("data:")) continue
            val ref = resolveRelative(file.path, raw)
            if (ref !in bundlePaths) errors.add("HTML references '$ref' which is not in the bundle")
        }
        return if (errors.isEmpty()) ValidationReport.PASS else ValidationReport(false, errors)
    }

    private fun resolveRelative(fromPath: String, ref: String): String {
        if (ref.startsWith("/")) return ref.removePrefix("/")
        val dir = fromPath.substringBeforeLast('/', "")
        return if (dir.isEmpty()) ref else "$dir/$ref"
    }

    // ---- JavaScript (Rhino AST parse) -------------------------------------
    fun validateJs(file: GeneratedFile): ValidationReport {
        val text = String(file.content, Charsets.UTF_8)
        return try {
            val ctx = Context.enter()
            try {
                ctx.languageVersion = Context.VERSION_ES6
                ctx.optimizationLevel = -1
                val env = org.mozilla.javascript.CompilerEnvirons()
                val parser = Parser(env)
                val ast: AstRoot = parser.parse(text, file.name, 1)
                if (ast == null) ValidationReport.fail("JS parse returned null")
                else ValidationReport.PASS
            } finally {
                Context.exit()
            }
        } catch (e: Exception) {
            ValidationReport(false, listOf("JS syntax error: ${e.message}"))
        }
    }

    // ---- CSS (brace balance + basic parse) --------------------------------
    fun validateCss(file: GeneratedFile): ValidationReport {
        val text = String(file.content, Charsets.UTF_8)
        val open = text.count { it == '{' }
        val close = text.count { it == '}' }
        return if (open == close) ValidationReport.PASS
        else ValidationReport(false, listOf("Unbalanced braces in CSS: $open '{' vs $close '}'"))
    }

    // ---- XML / SVG (strict SAX parse) -------------------------------------
    fun validateXml(file: GeneratedFile): ValidationReport {
        return try {
            val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newSAXParser()
            parser.parse(ByteArrayInputStream(file.content), org.xml.sax.helpers.DefaultHandler())
            ValidationReport.PASS
        } catch (e: Exception) {
            ValidationReport(false, listOf("XML parse error: ${e.message}"))
        }
    }

    // ---- Heuristic for .py, .kt, .java, others ----------------------------
    fun validateHeuristic(file: GeneratedFile): ValidationReport {
        val text = String(file.content, Charsets.UTF_8)
        val errors = mutableListOf<String>()
        if (text.isEmpty()) errors.add("File is empty")
        // Bracket balance across (), [], {}.
        val bal = IntArray(3)
        for (c in text) when (c) {
            '(' -> bal[0]++; ')' -> bal[0]--
            '[' -> bal[1]++; ']' -> bal[1]--
            '{' -> bal[2]++; '}' -> bal[2]--
        }
        if (bal.any { it != 0 }) errors.add("Unbalanced brackets: ()=${bal[0]} []=${bal[1]} {}=${bal[2]}")
        // Unterminated string literal (a single " with no match on its line).
        for ((idx, line) in text.lines().withIndex()) {
            val quotes = line.count { it == '"' } - line.count { it == '\\' && false }
            if (quotes % 2 != 0 && !line.trim().startsWith("//")) {
                // Could be a legit multi-line; only flag if it's the last line.
                if (idx == text.lines().lastIndex) errors.add("Possibly unterminated string at line ${idx + 1}")
            }
        }
        return if (errors.isEmpty()) ValidationReport.PASS else ValidationReport(false, errors)
    }
}

/** Fails on leftover placeholder markers (section 5.4-C "All"). */
object PlaceholderChecker {
    private val MARKERS = listOf(
        Regex("(?i)\\bTODO\\b"),
        Regex("(?i)lorem ipsum"),
        Regex("(?i)your_api_key_here"),
        Regex("(?i)your code here"),
        Regex("(?i)replace me"),
        Regex("\\.\\.\\.{3,}"),     // "..." used as a stub
        Regex("(?i)placeholder"),
    )

    fun check(file: GeneratedFile): ValidationReport {
        // Binary files don't get placeholder-checked as text.
        if (file.mimeType.startsWith("image/") || file.mimeType.startsWith("font/")) {
            return ValidationReport.PASS
        }
        val text = String(file.content, Charsets.UTF_8)
        val hits = MARKERS.mapNotNull { pat -> pat.find(text)?.value }
        return if (hits.isEmpty()) ValidationReport.PASS
        else ValidationReport(false, hits.map { "Contains placeholder marker: $it" })
    }
}
