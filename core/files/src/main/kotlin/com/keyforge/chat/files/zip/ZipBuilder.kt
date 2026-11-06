package com.keyforge.chat.files.zip

import com.keyforge.chat.files.GeneratedFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Builds a ZIP on-device from [GeneratedFile]s and then re-opens it to verify
 * every entry reads back byte-for-byte equal to its source (section 5.4-C).
 * Paths are sanitized: `../`, absolute paths, and drive prefixes are rejected,
 * because a malicious or muddled model could otherwise write outside the bundle.
 */
object ZipBuilder {

    data class Result(val bytes: ByteArray, val verified: Boolean, val errors: List<String>) {
        override fun equals(other: Any?): Boolean = other is Result && verified == other.verified
        override fun hashCode(): Int = verified.hashCode()
    }

    fun build(files: List<GeneratedFile>): Result {
        val errors = mutableListOf<String>()
        for (f in files) {
            val violation = sanitizePath(f.path)
            if (violation != null) errors.add("Unsafe path '${f.path}': $violation")
        }
        if (errors.isNotEmpty()) return Result(ByteArray(0), verified = false, errors)

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for (f in files) {
                val entry = ZipEntry(f.path)
                entry.size = f.content.size.toLong()
                val crc = CRC32().apply { update(f.content) }
                entry.crc = crc.value
                zos.putNextEntry(entry)
                zos.write(f.content)
                zos.closeEntry()
            }
        }
        val bytes = baos.toByteArray()

        // Round-trip integrity check: re-open and compare each entry byte-for-byte.
        val byPath = files.associateBy { it.path }
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val source = byPath[entry.name]
                if (source == null) {
                    errors.add("Unexpected entry in zip: ${entry.name}")
                } else {
                    val readBack = zis.readBytes()
                    if (!readBack.contentEquals(source.content)) {
                        errors.add("CRC mismatch for ${entry.name}: read-back differs from source")
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return Result(bytes, verified = errors.isEmpty(), errors)
    }

    /** Returns null if the path is safe, or a human-readable reason if not. */
    fun sanitizePath(path: String): String? {
        if (path.isBlank()) return "empty path"
        if (path.startsWith("/")) return "absolute path"
        if (path.contains(":") && path.length > 1 && path[1] == ':') return "drive prefix"
        if (path.contains("..")) return "parent traversal"
        if (path.contains("\\")) return "backslash not allowed"
        if (path.startsWith("~")) return "home prefix"
        return null
    }
}
