package com.keyforge.chat.files.validate

import com.google.common.truth.Truth.assertThat
import com.keyforge.chat.files.GeneratedFile
import org.junit.Test

class ValidatorsTest {

    @Test fun `valid JSON passes`() {
        val f = file("manifest.json", """{"name":"x","version":"1.0"}""")
        assertThat(Validators.validate(f)).isEqualTo(ValidationReport.PASS)
    }

    @Test fun `invalid JSON reports parse error`() {
        val f = file("bad.json", """{"name": }""")
        val r = Validators.validate(f)
        assertThat(r.ok).isFalse()
        assertThat(r.errors.joinToString()).contains("Invalid JSON")
    }

    @Test fun `valid manifest v3 passes when all refs present`() {
        val manifest = file("manifest.json", """
            {"manifest_version":3,"name":"Test","version":"1.0",
             "background":{"service_worker":"background.js"},
             "action":{"default_popup":"popup.html"},
             "icons":{"16":"icon16.png","128":"icon128.png"}}
        """.trimIndent())
        val bundle = listOf(
            manifest,
            file("background.js", "console.log('hi');"),
            file("popup.html", "<html></html>"),
            file("icon16.png", ByteArray(10)),
            file("icon128.png", ByteArray(10)),
        )
        assertThat(Validators.validate(manifest, bundle)).isEqualTo(ValidationReport.PASS)
    }

    @Test fun `manifest missing referenced file fails`() {
        val manifest = file("manifest.json", """
            {"manifest_version":3,"name":"Test","version":"1.0",
             "background":{"service_worker":"background.js"}}
        """.trimIndent())
        val r = Validators.validate(manifest, listOf(manifest))
        assertThat(r.ok).isFalse()
        assertThat(r.errors.joinToString()).contains("background.js")
    }

    @Test fun `manifest without version fails`() {
        val manifest = file("manifest.json", """{"manifest_version":3,"name":"x"}""")
        val r = Validators.validate(manifest, listOf(manifest))
        assertThat(r.ok).isFalse()
        assertThat(r.errors.joinToString()).contains("version")
    }

    @Test fun `valid JS passes Rhino parse`() {
        val f = file("ok.js", "function add(a, b) { return a + b; }\nconsole.log(add(1, 2));")
        assertThat(Validators.validate(f)).isEqualTo(ValidationReport.PASS)
    }

    @Test fun `JS with syntax error is rejected with line info`() {
        val f = file("bad.js", "function add(a, b {\n  return a + b;\n}\n")
        val r = Validators.validate(f)
        assertThat(r.ok).isFalse()
        assertThat(r.errors.joinToString()).contains("JS syntax error")
    }

    @Test fun `balanced CSS passes`() {
        assertThat(Validators.validate(file("s.css", "body { color: red; }"))).isEqualTo(ValidationReport.PASS)
    }

    @Test fun `unbalanced CSS braces fail`() {
        val r = Validators.validate(file("s.css", "body { color: red;"))
        assertThat(r.ok).isFalse()
        assertThat(r.errors.joinToString()).contains("Unbalanced braces")
    }

    @Test fun `valid SVG passes XML parse`() {
        val svg = file("i.svg", """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16"><rect width="16" height="16"/></svg>""")
        assertThat(Validators.validate(svg)).isEqualTo(ValidationReport.PASS)
    }

    @Test fun `malformed SVG fails`() {
        val svg = file("i.svg", """<svg><rect width="16"></svg>""")
        assertThat(Validators.validate(svg).ok).isFalse()
    }

    @Test fun `HTML referencing missing asset fails`() {
        val html = file("index.html", """<html><script src="app.js"></script></html>""")
        val r = Validators.validate(html, listOf(html))
        assertThat(r.ok).isFalse()
        assertThat(r.errors.joinToString()).contains("app.js")
    }

    @Test fun `placeholder TODO is rejected`() {
        val r = Validators.validate(file("s.js", "function f() { /* TODO */ }"))
        assertThat(r.ok).isFalse()
        assertThat(r.errors.joinToString()).contains("placeholder")
    }

    @Test fun `placeholder lorem ipsum is rejected`() {
        val r = Validators.validate(file("p.txt", "lorem ipsum dolor"))
        assertThat(r.ok).isFalse()
    }

    @Test fun `empty file fails heuristic`() {
        val r = Validators.validate(file("x.py", ""))
        assertThat(r.ok).isFalse()
        assertThat(r.errors.joinToString()).contains("empty")
    }

    @Test fun `balanced kotlin passes heuristic`() {
        val r = Validators.validate(file("x.kt", "fun main() { println(\"hi\") }"))
        assertThat(r).isEqualTo(ValidationReport.PASS)
    }

    @Test fun `unbalanced braces in kotlin fail`() {
        val r = Validators.validate(file("x.kt", "fun main() { println(\"hi\")"))
        assertThat(r.ok).isFalse()
    }

    private fun file(path: String, content: String) = GeneratedFile(path, content.toByteArray(Charsets.UTF_8))
    private fun file(path: String, bytes: ByteArray) = GeneratedFile(path, bytes)
}
