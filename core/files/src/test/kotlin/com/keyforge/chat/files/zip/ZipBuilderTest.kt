package com.keyforge.chat.files.zip

import com.google.common.truth.Truth.assertThat
import com.keyforge.chat.files.GeneratedFile
import org.junit.Test
import java.util.zip.ZipInputStream

class ZipBuilderTest {

    @Test fun `zip round-trip preserves every entry byte-for-byte`() {
        val files = listOf(
            GeneratedFile("manifest.json", """{"name":"x"}""".toByteArray()),
            GeneratedFile("src/background.js", "console.log('hi');".toByteArray()),
            GeneratedFile("icons/icon16.png", ByteArray(64) { it.toByte() }),
        )
        val result = ZipBuilder.build(files)
        assertThat(result.verified).isTrue()
        assertThat(result.errors).isEmpty()

        // Re-open and confirm contents.
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(result.bytes.inputStream()).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                entries[e.name] = zis.readBytes()
                zis.closeEntry()
                e = zis.nextEntry
            }
        }
        assertThat(entries.keys).containsExactly("manifest.json", "src/background.js", "icons/icon16.png")
        assertThat(entries["manifest.json"]).isEqualTo(files[0].content)
        assertThat(entries["src/background.js"]).isEqualTo(files[1].content)
        assertThat(entries["icons/icon16.png"]).isEqualTo(files[2].content)
    }

    @Test fun `path traversal is rejected`() {
        val files = listOf(GeneratedFile("../escape.txt", "evil".toByteArray()))
        val result = ZipBuilder.build(files)
        assertThat(result.verified).isFalse()
        assertThat(result.errors.joinToString()).contains("traversal")
    }

    @Test fun `absolute path is rejected`() {
        assertThat(ZipBuilder.sanitizePath("/etc/passwd")).isNotNull()
    }

    @Test fun `drive prefix is rejected`() {
        assertThat(ZipBuilder.sanitizePath("C:/Users/x")).isNotNull()
    }

    @Test fun `backslash is rejected`() {
        assertThat(ZipBuilder.sanitizePath("dir\\file.txt")).isNotNull()
    }

    @Test fun `clean relative path is accepted`() {
        assertThat(ZipBuilder.sanitizePath("src/sub/file.js")).isNull()
    }

    @Test fun `corrupted content would fail round-trip`() {
        val files = listOf(GeneratedFile("a.txt", "hello".toByteArray()))
        val result = ZipBuilder.build(files)
        // Tamper: flip a byte and re-verify the build's own check still passed first.
        assertThat(result.verified).isTrue()
        val tampered = result.bytes.copyOf()
        if (tampered.size > 20) tampered[20] = (tampered[20].toInt() xor 0xFF).toByte()
        // Re-opening a tampered zip should throw or mismatch when read.
        var mismatch = false
        try {
            ZipInputStream(tampered.inputStream()).use { zis ->
                var e = zis.nextEntry
                if (e != null) {
                    val readBack = zis.readBytes()
                    if (!readBack.contentEquals(files[0].content)) mismatch = true
                }
            }
        } catch (e: Exception) {
            mismatch = true
        }
        assertThat(mismatch).isTrue()
    }
}
