package com.keyforge.chat.feature.keys

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keyforge.chat.ai.ProviderType
import com.keyforge.chat.feature.keys.components.KeyCard
import com.keyforge.chat.ui.components.EmptyState
import com.keyforge.chat.ui.components.LoadingSkeleton
import com.keyforge.chat.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeysScreen(
    modifier: Modifier = Modifier,
    viewModel: KeysViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var addProvider by remember { mutableStateOf(ProviderType.GEMINI) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("API keys") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.showAddDialog(true) },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Add key") },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> LoadingSkeleton(rows = 3, modifier = Modifier.padding(MaterialTheme.spacing.lg))
                state.rows.isEmpty() -> EmptyState(
                    title = "No keys yet",
                    message = "Add a key for Gemini, OpenRouter, or Groq to start chatting. Keys are encrypted on this device.",
                    icon = Icons.Rounded.Key,
                    actionLabel = "Add a key",
                    onAction = { viewModel.showAddDialog(true) },
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(MaterialTheme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
                ) {
                    items(state.rows, key = { it.id }) { row ->
                        KeyCard(
                            row = row,
                            onToggle = { viewModel.setEnabled(row.id, it) },
                            onTest = { viewModel.test(row.id) },
                            onDelete = { viewModel.delete(row.id) },
                        )
                    }
                }
            }
        }
    }

    if (state.showAddDialog) {
        AddKeyDialog(
            onDismiss = { viewModel.showAddDialog(false) },
            onConfirm = { provider, label, key ->
                viewModel.addKey(provider, label, key)
            },
        )
    }
}

@Composable
private fun AddKeyDialog(
    onDismiss: () -> Unit,
    onConfirm: (ProviderType, String, String) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf(ProviderType.GEMINI) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add API key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                Text("Provider", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                    ProviderType.values().forEach { p ->
                        FilterChip(
                            selected = provider == p,
                            onClick = { provider = p },
                            label = { Text(p.name) },
                        )
                    }
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("API key") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = label.isNotBlank() && key.isNotBlank(),
                onClick = { onConfirm(provider, label, key) },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
    androidx.compose.material3.FilterChip(selected = selected, onClick = onClick, label = label)
}
