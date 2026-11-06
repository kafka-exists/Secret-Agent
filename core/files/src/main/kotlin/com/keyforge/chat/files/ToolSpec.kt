package com.keyforge.chat.files

import com.keyforge.chat.ai.ToolSpec

/**
 * The concrete tools this app exposes to the model. The [ToolSpec] data shape
 * itself lives in core-ai so providers can serialize it; here we just enumerate
 * the actual file-generation tools.
 */
object FileTools {
    val createFile = ToolSpec(
        name = "create_file",
        description = "Create a file with the given path and content. Use encoding='base64' for binary files (PNG, JPG, ICO, fonts). Content is written verbatim — never wrap in markdown fences.",
        parametersJson = """{"type":"object","properties":{"path":{"type":"string","description":"Relative path, e.g. 'src/background.js' or 'manifest.json'"},"content":{"type":"string","description":"Full file contents"},"mime_type":{"type":"string","description":"Optional MIME type"},"encoding":{"type":"string","enum":["utf8","base64"],"default":"utf8"}},"required":["path","content"]}""",
    )
    val createZip = ToolSpec(
        name = "create_zip",
        description = "Create a ZIP bundle containing multiple files. Use for projects like browser/VS Code extensions.",
        parametersJson = """{"type":"object","properties":{"zip_name":{"type":"string"},"files":{"type":"array","items":{"type":"object","properties":{"path":{"type":"string"},"content":{"type":"string"},"encoding":{"type":"string","enum":["utf8","base64"],"default":"utf8"}},"required":["path","content"]}}},"required":["zip_name","files"]}""",
    )
    val updateFile = ToolSpec(
        name = "update_file",
        description = "Replace the full contents of an existing file.",
        parametersJson = """{"type":"object","properties":{"path":{"type":"string"},"content":{"type":"string"}},"required":["path","content"]}""",
    )

    val all: List<ToolSpec> = listOf(createFile, createZip, updateFile)
}
