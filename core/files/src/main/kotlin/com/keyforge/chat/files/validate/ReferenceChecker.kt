package com.keyforge.chat.files.validate

import com.keyforge.chat.files.GeneratedFile
import org.jsoup.Jsoup

/**
 * Cross-file reference checker (section 5.4-D). Builds a dependency graph per
 * bundle: manifest → scripts/icons/popup, HTML → assets, and reports any
 * referenced-but-missing or declared-but-unused file. Combined with the per-file
 * validators, this guarantees a bundle is internally consistent.
 */
object ReferenceChecker {

    data class Edge(val from: String, val to: String, val kind: String)

    data class Report(
        val ok: Boolean,
        val missing: List<String>,
        val unused: List<String>,
        val edges: List<Edge>,
    )

    fun check(bundle: List<GeneratedFile>): Report {
        val paths = bundle.map { it.path }.toSet()
        val edges = mutableListOf<Edge>()
        val referenced = mutableSetOf<String>()

        for (file in bundle) {
            when (file.extension.lowercase()) {
                "json" -> {
                    val obj = runCatching { JsonAccess.parse(String(file.content, Charsets.UTF_8)) }.getOrNull()
                    if (obj != null && obj.has("manifest_version")) {
                        collectManifestRefs(obj, edges, file.path, referenced)
                    }
                }
                "html", "htm" -> {
                    val doc = runCatching { Jsoup.parse(String(file.content, Charsets.UTF_8)) }.getOrNull()
                    doc?.select("[href], [src]")?.forEach { el ->
                        val raw = el.attr("href").ifBlank { el.attr("src") }
                        if (raw.isNotBlank() && !raw.startsWith("http") && !raw.startsWith("//") && !raw.startsWith("data:")) {
                            val ref = resolveRelative(file.path, raw)
                            edges.add(Edge(file.path, ref, "html-asset"))
                            referenced.add(ref)
                        }
                    }
                }
                "js", "mjs" -> {
                    val text = String(file.content, Charsets.UTF_8)
                    // Cheap static import scan: import ... from './x' or require('./x')
                    val importPattern = Regex("""(?:import|require)\s*\(?['"]([^'"]+)['"]""")
                    for (m in importPattern.findAll(text)) {
                        val ref = m.groupValues[1]
                        if (!ref.startsWith("http") && !ref.startsWith("node:")) {
                            val resolved = resolveRelative(file.path, ref)
                            edges.add(Edge(file.path, resolved, "import"))
                            referenced.add(resolved)
                            // Also try with common extensions.
                            listOf(".js", ".mjs", "/index.js").forEach { suffix ->
                                if (!resolved.endsWith(suffix)) referenced.add(resolved + suffix)
                            }
                        }
                    }
                }
            }
        }

        val missing = referenced.filter { it !in paths }
        val unused = paths.filter { it !in referenced && it !in setOf("manifest.json", "README.md") }
            .filterNot { path -> bundle.first { it.path == path }.mimeType.startsWith("image/") }
        return Report(ok = missing.isEmpty(), missing = missing, unused = unused, edges = edges)
    }

    private fun collectManifestRefs(
        m: JsonAccess,
        edges: MutableList<Edge>,
        from: String,
        referenced: MutableSet<String>,
    ) {
        val icons = m.optJSONObject("icons")
        if (icons.keys().isNotEmpty()) icons.keys().forEach { size ->
            val p = icons.getString(size)
            edges.add(Edge(from, p, "icon")); referenced.add(p)
        }
        listOf("action", "browser_action").forEach { key ->
            m.optJSONObject(key).optString("default_popup").takeIf { it.isNotBlank() }?.let {
                edges.add(Edge(from, it, "popup")); referenced.add(it)
            }
        }
        m.optString("background").takeIf { it.isNotBlank() }?.let {
            edges.add(Edge(from, it, "background")); referenced.add(it)
        }
        m.optJSONObject("background").optString("service_worker").takeIf { it.isNotBlank() }?.let {
            edges.add(Edge(from, it, "service-worker")); referenced.add(it)
        }
        m.optJSONObject("background").optJSONArray("scripts")?.strings()?.forEach { p ->
            edges.add(Edge(from, p, "background-script")); referenced.add(p)
        }
        m.optJSONObject("options_ui").optString("page").takeIf { it.isNotBlank() }?.let {
            edges.add(Edge(from, it, "options")); referenced.add(it)
        }
        m.optJSONArray("content_scripts")?.let { arr ->
            for (i in 0 until arr.size) {
                val cs = JsonAccess((arr[i] as? kotlinx.serialization.json.JsonObject) ?: continue)
                cs.optJSONArray("js")?.strings()?.forEach { p -> edges.add(Edge(from, p, "content-script")); referenced.add(p) }
                cs.optJSONArray("css")?.strings()?.forEach { p -> edges.add(Edge(from, p, "content-css")); referenced.add(p) }
            }
        }
    }

    private fun resolveRelative(fromPath: String, ref: String): String {
        if (ref.startsWith("/")) return ref.removePrefix("/")
        val dir = fromPath.substringBeforeLast('/', "")
        return if (dir.isEmpty()) ref else "$dir/$ref"
    }
}
