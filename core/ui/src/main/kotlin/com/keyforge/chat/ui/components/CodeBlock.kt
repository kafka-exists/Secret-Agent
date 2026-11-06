package com.keyforge.chat.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.keyforge.chat.ui.theme.CodeColors
import com.keyforge.chat.ui.theme.CodeFontFamily
import kotlinx.coroutines.delay

/**
 * Code block with a header bar (language label + copy button with check-state
 * feedback), horizontal scroll, monospace, and a lightweight theme-aware
 * tokenizer. Code spans are scrollable so long lines never break the layout.
 */
@Composable
fun CodeBlock(
    code: String,
    language: String = "",
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    val container = MaterialTheme.colorScheme.surfaceVariant
    val onContainer = MaterialTheme.colorScheme.onSurfaceVariant

    androidx.compose.material3.Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = container,
        tonalElevation = com.keyforge.chat.ui.theme.Elevation.Level1,
    ) {
        androidx.compose.foundation.layout.Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = language.ifBlank { "code" },
                    style = MaterialTheme.typography.labelMedium,
                    color = onContainer,
                    modifier = Modifier.weight(1f),
                )
                AnimatedContent(targetState = copied, label = "copyIcon") { isCopied ->
                    Icon(
                        imageVector = if (isCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = if (isCopied) "Copied" else "Copy code",
                        modifier = Modifier.size(18.dp),
                        tint = onContainer,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = remember(code, language) { highlight(code) },
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = CodeFontFamily),
                    color = onContainer,
                    softWrap = false,
                )
            }
        }
    }
}

private fun highlight(code: String): AnnotatedString = buildAnnotatedString {
    // Match comments, strings (single/double/triple-quoted), numbers, and a broad
    // set of language keywords. Built as an escaped string rather than a raw
    // string so the embedded triple-quote sequence stays unambiguous.
    val tokenPattern = Regex(
        "(?s)(//.*?\$|/\\*.*?\\*/|#.*?\$)|(\"\"\"[^\"]*\"\"\")|('(?:\\\\.|[^'\\\\])*')|(\\b\\d+(?:\\.\\d+)?\\b)|(\\b(?:fun|val|var|class|object|interface|return|if|else|for|while|import|package|public|private|internal|const|override|suspend|companion|when|in|is|as|null|true|false|def|print|len|range|self|from|export|let|function|new|extends|default|async|await|try|catch|throw)\\b)",
        setOf(RegexOption.MULTILINE),
    )
    var last = 0
    for (m in tokenPattern.findAll(code)) {
        if (m.range.first > last) append(code.substring(last, m.range.first))
        val style = when {
            m.groups[1] != null -> SpanStyle(color = CodeColors.comment)       // comment
            m.groups[2] != null || m.groups[3] != null -> SpanStyle(color = CodeColors.string) // string
            m.groups[4] != null -> SpanStyle(color = CodeColors.number)        // number
            m.groups[5] != null -> SpanStyle(color = CodeColors.keyword)       // keyword
            else -> SpanStyle()
        }
        withStyle(style) { append(m.value) }
        last = m.range.last + 1
    }
    if (last < code.length) append(code.substring(last))
}
