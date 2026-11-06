package com.keyforge.chat.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keyforge.chat.feature.chat.components.Composer
import com.keyforge.chat.feature.chat.components.MessageRow
import com.keyforge.chat.ui.components.EmptyState
import com.keyforge.chat.ui.components.ErrorState
import com.keyforge.chat.ui.theme.spacing

@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Auto-scroll to bottom while streaming, unless the user has scrolled up.
    val isAtBottom by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = listState.layoutInfo.totalItemsCount
            total == 0 || last >= total - 2
        }
    }
    LaunchedEffect(state.messages.lastOrNull()?.id, state.messages.lastOrNull()?.content) {
        if (isAtBottom && state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }
    LaunchedEffect(isAtBottom) { viewModel.setAttached(!isAtBottom) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("KeyForge", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    Text(
                        state.presetLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = MaterialTheme.spacing.md),
                    )
                },
            )
        },
        bottomBar = {
            Composer(
                text = state.input,
                onTextChange = viewModel::onInputChange,
                isSending = state.isSending,
                onSend = viewModel::send,
                onStop = viewModel::stop,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.error != null && state.messages.isEmpty() -> ErrorState(
                    message = state.error!!,
                    onRetry = { viewModel.dismissError(); viewModel.send() },
                )
                state.isEmpty -> EmptyState(
                    title = "Start a conversation",
                    message = "Bring your own keys for Gemini, OpenRouter, and Groq. KeyForge switches automatically when one hits a limit.",
                    icon = Icons.Rounded.ChatBubbleOutline,
                    actionLabel = null,
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(MaterialTheme.spacing.lg),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(MaterialTheme.spacing.sm),
                ) {
                    items(state.messages, key = { it.id }) { msg ->
                        MessageRow(message = msg)
                    }
                }
            }
            // Jump-to-latest when detached.
            if (state.isAttached && state.messages.isNotEmpty()) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = true,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(MaterialTheme.spacing.lg),
                ) {
                    IconButton(onClick = {
                        // Will scroll on next composition via LaunchedEffect.
                    }) {
                        Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = "Jump to latest")
                    }
                }
            }
        }
    }
}
