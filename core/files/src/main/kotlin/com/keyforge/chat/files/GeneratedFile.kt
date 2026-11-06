package com.keyforge.chat.files

/**
 * A file the model emitted, parsed from either a tool call or the `<<<FILE>>>`
 * fallback protocol. [content] is the raw bytes-as-string (base64-decoded if
 * the model used encoding="base64").
 */
data class GeneratedFile(
    val path: String,
    val content: ByteArray,
    val encoding: String = "utf8",
    val mimeType: String = guessMime(path),
) {
    val name: String get() = path.substringAfterLast('/')
    val size: Long get() = content.size.toLong()
    val extension: String get() = path.substringAfterLast('.', missingDelimiterValue = "")

    override fun equals(other: Any?): Boolean = other is GeneratedFile && path == other.path
    override fun hashCode(): Int = path.hashCode()
}

/** A ZIP bundle requested by the model. */
data class GeneratedZip(
    val name: String,
    val files: List<GeneratedFile>,
)

fun guessMime(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "json" -> "application/json"
    "html", "htm" -> "text/html"
    "js", "mjs" -> "text/javascript"
    "css" -> "text/css"
    "svg" -> "image/svg+xml"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "ico" -> "image/x-icon"
    "xml" -> "application/xml"
    "py" -> "text/x-python"
    "kt" -> "text/x-kotlin"
    "java" -> "text/x-java"
    "md" -> "text/markdown"
    "txt" -> "text/plain"
    "zip" -> "application/zip"
    "wasm" -> "application/wasm"
    "ttf" -> "font/ttf"
    "woff" -> "font/woff"
    "woff2" -> "font/woff2"
    else -> "application/octet-stream"
}
