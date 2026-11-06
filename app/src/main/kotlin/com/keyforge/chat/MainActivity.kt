package com.keyforge.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keyforge.chat.feature.settings.SettingsViewModel
import com.keyforge.chat.nav.KeyForgeNavGraph
import com.keyforge.chat.ui.theme.KeyForgeTheme
import com.keyforge.chat.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.keyforge.chat.data.datastore.SettingsStore

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsStore: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install before any content so there's no flash of white (section 6.6).
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by settingsStore.themeMode.collectAsState(initial = "System")
            val dynamicColor by settingsStore.dynamicColor.collectAsState(initial = true)
            KeyForgeTheme(
                themeMode = runCatching { ThemeMode.valueOf(themeMode) }.getOrDefault(ThemeMode.System),
                dynamicColor = dynamicColor,
            ) {
                KeyForgeNavGraph()
            }
        }
    }
}
