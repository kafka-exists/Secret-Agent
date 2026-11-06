package com.keyforge.chat.ai

import kotlinx.serialization.Serializable

/**
 * A tool the model can call. Lives in core-ai (the lower-level module) so the
 * providers can reference it without depending on core-files; the concrete tool
 * definitions ([com.keyforge.chat.files.FileTools]) stay in core-files.
 */
@Serializable
data class ToolSpec(
    val name: String,
    val description: String,
    /** JSON Schema describing arguments, as a JSON string. */
    val parametersJson: String,
)
