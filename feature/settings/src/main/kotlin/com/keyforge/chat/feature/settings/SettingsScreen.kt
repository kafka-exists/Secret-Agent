package com.keyforge.chat.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keyforge.chat.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier.fillMaxWidth(),
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(MaterialTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xl),
        ) {
            Section(title = "Appearance", description = "Choose how KeyForge looks. Dynamic color follows your wallpaper on Android 12 and up.") {
                val themes = listOf("System", "Light", "Dark", "Amoled")
                themes.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = state.themeMode == t, onClick = { viewModel.setThemeMode(t) })
                        Text(t, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                ToggleRow(
                    title = "Dynamic color",
                    subtitle = "Material You palette from your wallpaper",
                    checked = state.dynamicColor,
                    onCheck = viewModel::setDynamicColor,
                )
                ToggleRow(
                    title = "Reduce motion",
                    subtitle = "Use instant transitions instead of animations",
                    checked = state.reduceMotion,
                    onCheck = viewModel::setReduceMotion,
                )
            }
            Section(title = "Routing", description = "Pick the order KeyForge tries providers and models. You can also reorder keys on the Keys screen.") {
                RoutingPreset.values().forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = state.routingPreset == p.name, onClick = { viewModel.setRoutingPreset(p.name) })
                        Column {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge)
                            Text(preferenceSummary(p), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Section(title = "Security", description = "Keys are encrypted on this device and never sent anywhere except the provider you choose.") {
                ToggleRow(
                    title = "Biometric lock for keys",
                    subtitle = "Require fingerprint or face unlock to view keys",
                    checked = state.biometricLock,
                    onCheck = viewModel::setBiometricLock,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, description: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheck: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheck)
    }
}

private fun preferenceSummary(p: RoutingPreset): String = p.preference.joinToString(", ") { (prov, model) ->
    "${prov.name.lowercase().replaceFirstChar { it.uppercase() }} $model"
}
