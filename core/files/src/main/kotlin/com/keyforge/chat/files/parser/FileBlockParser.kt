package com.keyforge.chat.files.parser

import com.keyforge.chat.files.GeneratedFile
import com.keyforge.chat.files.GeneratedZip
import java.util.Base64 as JvmBase64

/**
 * Streaming-tolerant parser for the `<<<FILE>>>` / `<<<ZIP>>>` fallback protocol
 * (section 5.2). Designed to be fed chunks as they arrive; it tracks open tags
 * and partial content so a file split across many SSE deltas still parses. The
 * model is told to never wrap content in markdown fences, so we don't strip
 * them — but we do tolerate a stray ``` fence at the very start of a file as a
 * defensive measure.
 */
class FileBlockParser {

    private enum class State { OUTSIDE, IN_FILE, IN_ZIP_FILE }
    private var state = State.OUTSIDE
    private val buffer = StringBuilder()
    private var currentPath: String? = null
    private var currentEncoding: String = "utf8"
    private val completed = mutableListOf<GeneratedFile>()
    private var openZipName: String? = null
    private val zipFiles = mutableListOf<GeneratedFile>()

    /** Feed a chunk of streamed text. Returns files that completed in this chunk. */
    fun feed(chunk: String): List<GeneratedFile> {
        buffer.append(chunk)
        val newlyCompleted = mutableListOf<GeneratedFile>()
        var progressed = true
        while (progressed) {
            progressed = consume(newlyCompleted)
        }
        return newlyCompleted
    }

    /** Call when the stream ends; flushes any trailing file. */
    fun finish(): List<GeneratedFile> {
        val out = mutableListOf<GeneratedFile>()
        // Be lenient: if we ended mid-file, emit it as-is (the truncation guard
        // will then catch unbalanced content and trigger self-repair).
        val path = currentPath
        if (state == State.IN_FILE && path != null) {
            val content = decode(buffer.toString().trimFences(), currentEncoding)
            val f = GeneratedFile(path, content, currentEncoding)
            completed.add(f); out.add(f)
        } else if (state == State.IN_ZIP_FILE && path != null && openZipName != null) {
            val content = decode(buffer.toString().trimFences(), currentEncoding)
            zipFiles.add(GeneratedFile(path, content, currentEncoding))
            val zip = GeneratedZip(openZipName!!, zipFiles.toList())
            // Represent the whole zip as a single synthetic file carrying its parts.
            val synthetic = GeneratedFile(
                path = openZipName!!,
                content = ByteArray(0),
                mimeType = "application/zip",
            ).let { it.copy(content = it.content) }
            // We return the individual files; the caller (SelfRepairLoop / pipeline)
            // knows to bundle them into a zip when zipFiles is non-empty.
            out.addAll(zip.files)
            completed.addAll(zip.files)
        }
        buffer.clear()
        currentPath = null
        state = State.OUTSIDE
        openZipName = null
        zipFiles.clear()
        return out
    }

    fun allCompleted(): List<GeneratedFile> = completed.toList()

    private fun consume(out: MutableList<GeneratedFile>): Boolean {
        val text = buffer.toString()
        when (state) {
            State.OUTSIDE -> {
                val fileStart = text.indexOf("<<<FILE ")
                val zipStart = text.indexOf("<<<ZIP ")
                val next = nearest(fileStart, zipStart)
                if (next < 0) return false
                if (next == fileStart && fileStart >= 0) {
                    val headerEnd = text.indexOf(">>>", fileStart)
                    if (headerEnd < 0) return false
                    val header = text.substring(fileStart, headerEnd)
                    currentPath = extractAttr(header, "path")
                    currentEncoding = extractAttr(header, "encoding") ?: "utf8"
                    buffer.setLength(0)
                    buffer.append(text.substring(headerEnd + 3))
                    state = State.IN_FILE
                    return true
                } else {
                    // ZIP start
                    val headerEnd = text.indexOf(">>>", zipStart)
                    if (headerEnd < 0) return false
                    val header = text.substring(zipStart, headerEnd)
                    openZipName = extractAttr(header, "name")
                    buffer.setLength(0)
                    buffer.append(text.substring(headerEnd + 3))
                    state = State.IN_ZIP_FILE
                    return true
                }
            }
            State.IN_FILE, State.IN_ZIP_FILE -> {
                // Between two files inside a zip (currentPath == null), the next
                // <<<FILE ...>>> header opens the next nested file.
                if (currentPath == null && state == State.IN_ZIP_FILE) {
                    val fileStart = text.indexOf("<<<FILE ")
                    if (fileStart >= 0) {
                        val headerEnd = text.indexOf(">>>", fileStart)
                        if (headerEnd < 0) return false
                        val header = text.substring(fileStart, headerEnd)
                        currentPath = extractAttr(header, "path")
                        currentEncoding = extractAttr(header, "encoding") ?: "utf8"
                        buffer.setLength(0)
                        buffer.append(text.substring(headerEnd + 3))
                        return true
                    }
                }
                val endIdx = text.indexOf("<<<END_FILE>>>")
                val zipEndIdx = if (state == State.IN_ZIP_FILE) text.indexOf("<<<END_ZIP>>>") else -1
                if (endIdx >= 0 && (zipEndIdx < 0 || endIdx < zipEndIdx)) {
                    val content = text.substring(0, endIdx).trimFences()
                    val path = currentPath ?: return false
                    val file = GeneratedFile(path, decode(content, currentEncoding), currentEncoding)
                    completed.add(file)
                    out.add(file)
                    if (state == State.IN_ZIP_FILE) zipFiles.add(file)
                    buffer.setLength(0)
                    buffer.append(text.substring(endIdx + "<<<END_FILE>>>".length))
                    state = if (zipEndIdx in 0..endIdx) State.OUTSIDE else {
                        if (openZipName != null) State.IN_ZIP_FILE else State.OUTSIDE
                    }
                    currentPath = null
                    return true
                }
                if (zipEndIdx >= 0 && state == State.IN_ZIP_FILE) {
                    // Close the zip
                    buffer.setLength(0)
                    buffer.append(text.substring(zipEndIdx + "<<<END_ZIP>>>".length))
                    state = State.OUTSIDE
                    openZipName = null
                    zipFiles.clear()
                    return true
                }
                return false
            }
        }
    }

    private fun nearest(a: Int, b: Int): Int = when {
        a < 0 -> b
        b < 0 -> a
        else -> minOf(a, b)
    }

    private fun extractAttr(header: String, attr: String): String? {
        val key = "$attr=\""
        val start = header.indexOf(key)
        if (start < 0) return null
        val valueStart = start + key.length
        val end = header.indexOf('"', valueStart)
        if (end < 0) return null
        return header.substring(valueStart, end)
    }

    private fun decode(content: String, encoding: String): ByteArray = when (encoding) {
        "base64" -> runCatching { JvmBase64.getDecoder().decode(content) }.getOrElse { content.toByteArray(Charsets.UTF_8) }
        else -> content.toByteArray(Charsets.UTF_8)
    }

    private fun String.trimFences(): String {
        var s = this.trim()
        // Strip a single leading ```lang fence + trailing ``` if present.
        if (s.startsWith("```")) {
            val firstLineEnd = s.indexOf('\n')
            if (firstLineEnd > 0) {
                // ```lang\n... -> drop the fence line; bare ``` with no newline -> drop just the fence.
                s = s.substring(firstLineEnd + 1)
            } else {
                s = s.removePrefix("```")
            }
        }
        val trailingFence = s.lastIndexOf("```")
        if (trailingFence >= 0 && s.substring(trailingFence).trim() == "```") {
            s = s.substring(0, trailingFence)
        }
        return s
    }
}
