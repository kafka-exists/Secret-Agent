package com.keyforge.chat.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.keyforge.chat.feature.chat.ChatScreen
import com.keyforge.chat.feature.keys.KeysScreen
import com.keyforge.chat.feature.settings.SettingsScreen

object Destinations {
    const val CHAT = "chat"
    const val KEYS = "keys"
    const val SETTINGS = "settings"
}

@Composable
fun KeyForgeNavGraph() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: Destinations.CHAT

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = current == Destinations.CHAT,
                    onClick = { navController.navigate(Destinations.CHAT) { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
                    icon = { Icon(Icons.Rounded.Chat, contentDescription = null) },
                    label = { Text("Chat") },
                )
                NavigationBarItem(
                    selected = current == Destinations.KEYS,
                    onClick = { navController.navigate(Destinations.KEYS) { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
                    icon = { Icon(Icons.Rounded.Key, contentDescription = null) },
                    label = { Text("Keys") },
                )
                NavigationBarItem(
                    selected = current == Destinations.SETTINGS,
                    onClick = { navController.navigate(Destinations.SETTINGS) { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
                    icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destinations.CHAT,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Destinations.CHAT) { ChatScreen(modifier = Modifier.fillMaxSize()) }
            composable(Destinations.KEYS) { KeysScreen(modifier = Modifier.fillMaxSize()) }
            composable(Destinations.SETTINGS) { SettingsScreen(modifier = Modifier.fillMaxSize()) }
        }
    }
}
