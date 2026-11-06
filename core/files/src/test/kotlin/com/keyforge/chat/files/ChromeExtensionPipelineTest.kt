package com.keyforge.chat.files

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.keyforge.chat.files.validate.ReferenceChecker
import com.keyforge.chat.files.validate.Validators
import com.keyforge.chat.files.zip.ZipBuilder
import org.junit.Test
import java.util.zip.ZipInputStream

/**
 * End-to-end pipeline test for a generated Chrome extension (section 5.4-G).
 * Builds a manifest v3 extension with background, popup, content script, and
 * icons, then asserts every validator passes, the reference graph is complete,
 * and the resulting ZIP round-trips byte-for-byte.
 */
class ChromeExtensionPipelineTest {

    private val manifestJson = """
        {
          "manifest_version": 3,
          "name": "Page Tint",
          "version": "1.0.0",
          "description": "Tints the current page a calm teal.",
          "permissions": ["activeTab", "scripting"],
          "background": { "service_worker": "background.js" },
          "action": { "default_popup": "popup/popup.html", "default_icon": { "16": "icons/icon16.png", "128": "icons/icon128.png" } },
          "content_scripts": [
            { "matches": ["<all_urls>"], "js": ["content/tint.js"], "css": ["content/tint.css"] }
          ],
          "icons": { "16": "icons/icon16.png", "128": "icons/icon128.png" }
        }
    """.trimIndent()

    private val backgroundJs = """
        chrome.runtime.onInstalled.addListener(() => {
          console.log('Page Tint installed');
        });
    """.trimIndent()

    private val popupHtml = """
        <!DOCTYPE html>
        <html>
          <head><meta charset="utf-8"><link rel="stylesheet" href="popup.css"></head>
          <body>
            <button id="tint">Tint page</button>
            <script src="popup.js"></script>
          </body>
        </html>
    """.trimIndent()

    private val popupCss = "body { width: 180px; padding: 8px; font-family: sans-serif; }"
    private val popupJs = "document.getElementById('tint').addEventListener('click', () => { chrome.tabs.query({active:true}, () => {}); });"
    private val tintJs = "(function(){ document.body.style.background = '#80D5C922'; })();"
    private val tintCss = "body { transition: background 0.2s; }"

    private fun bundle(): List<GeneratedFile> = listOf(
        GeneratedFile("manifest.json", manifestJson.toByteArray()),
        GeneratedFile("background.js", backgroundJs.toByteArray()),
        GeneratedFile("popup/popup.html", popupHtml.toByteArray()),
        GeneratedFile("popup/popup.css", popupCss.toByteArray()),
        GeneratedFile("popup/popup.js", popupJs.toByteArray()),
        GeneratedFile("content/tint.js", tintJs.toByteArray()),
        GeneratedFile("content/tint.css", tintCss.toByteArray()),
        GeneratedFile("icons/icon16.png", ByteArray(64) { it.toByte() }),
        GeneratedFile("icons/icon128.png", ByteArray(128) { it.toByte() }),
    )

    @Test fun `every file in the extension passes its validator`() {
        val bundle = bundle()
        for (f in bundle) {
            val report = Validators.validate(f, bundle)
            assertWithMessage("$f failed: ${report.errors}").that(report.ok).isTrue()
        }
    }

    @Test fun `cross-file reference graph is complete`() {
        val bundle = bundle()
        val report = ReferenceChecker.check(bundle)
        assertThat(report.missing).isEmpty()
    }

    @Test fun `zip round-trips every entry intact`() {
        val bundle = bundle()
        val result = ZipBuilder.build(bundle)
        assertThat(result.verified).isTrue()

        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(result.bytes.inputStream()).use { zis ->
            var e = zis.nextEntry
            while (e != null) { entries[e.name] = zis.readBytes(); zis.closeEntry(); e = zis.nextEntry }
        }
        assertThat(entries.keys).containsExactlyElementsIn(bundle.map { it.path })
        for (f in bundle) {
            assertThat(entries[f.path]).isEqualTo(f.content)
        }
    }

    @Test fun `extension with missing icon fails reference check`() {
        val broken = bundle().filterNot { it.path == "icons/icon128.png" }
        val report = ReferenceChecker.check(broken)
        assertThat(report.missing).contains("icons/icon128.png")
    }

    @Test fun `extension with missing service_worker fails manifest validation`() {
        val broken = bundle().filterNot { it.path == "background.js" }
        val manifest = broken.first { it.path == "manifest.json" }
        val r = Validators.validate(manifest, broken)
        assertThat(r.ok).isFalse()
        assertThat(r.errors.joinToString()).contains("background.js")
    }
}
