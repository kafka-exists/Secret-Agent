package com.keyforge.chat.files.parser

import com.google.common.truth.Truth.assertThat
import com.keyforge.chat.files.GeneratedFile
import org.junit.Test

class FileBlockParserTest {

    @Test fun `single file emitted in one chunk parses`() {
        val parser = FileBlockParser()
        val input = """
            Here is your file:
            <<<FILE path="manifest.json">>>
            {"name":"test","version":"1.0"}
            <<<END_FILE>>>
            Done.
        """.trimIndent()
        val files = parser.feed(input) + parser.finish()
        assertThat(files.map { it.path }).containsExactly("manifest.json")
        assertThat(String(files[0].content).trim()).isEqualTo("""{"name":"test","version":"1.0"}""")
    }

    @Test fun `file split across many chunks still parses`() {
        val parser = FileBlockParser()
        val whole = "<<<FILE path=\"app.js\">>>\nfunction f() { return 42; }\n<<<END_FILE>>>"
        val collected = mutableListOf<GeneratedFile>()
        // Feed 7 chars at a time.
        whole.chunked(7).forEach { collected.addAll(parser.feed(it)) }
        collected.addAll(parser.finish())
        assertThat(collected.map { it.path }).containsExactly("app.js")
        assertThat(String(collected[0].content).trim()).isEqualTo("function f() { return 42; }")
    }

    @Test fun `multiple files in one stream parse in order`() {
        val parser = FileBlockParser()
        val input = """
            <<<FILE path="a.txt">>>alpha<<<END_FILE>>>
            <<<FILE path="b.txt">>>bravo<<<END_FILE>>>
        """.trimIndent()
        val files = parser.feed(input) + parser.finish()
        assertThat(files.map { it.path }).containsExactly("a.txt", "b.txt").inOrder()
    }

    @Test fun `zip with nested files parses`() {
        val parser = FileBlockParser()
        val input = """
            <<<ZIP name="ext.zip">>>
            <<<FILE path="manifest.json">>>{"name":"x"}<<<END_FILE>>>
            <<<FILE path="bg.js">>>console.log(1);<<<END_FILE>>>
            <<<END_ZIP>>>
        """.trimIndent()
        val files = parser.feed(input) + parser.finish()
        assertThat(files.map { it.path }).containsExactly("manifest.json", "bg.js")
    }

    @Test fun `base64 encoding decodes to binary`() {
        val parser = FileBlockParser()
        // "hello" base64 = "aGVsbG8="
        val input = """<<<FILE path="icon.png" encoding="base64">>>aGVsbG8=<<<END_FILE>>>"""
        val files = parser.feed(input) + parser.finish()
        assertThat(files[0].content).isEqualTo("hello".toByteArray())
    }

    @Test fun `stray markdown fences around content are tolerated`() {
        val parser = FileBlockParser()
        val input = "<<<FILE path=\"a.js\">>>\n```js\nconsole.log(1);\n```\n<<<END_FILE>>>"
        val files = parser.feed(input) + parser.finish()
        assertThat(String(files[0].content).trim()).isEqualTo("console.log(1);")
    }
}
