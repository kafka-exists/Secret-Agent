package com.keyforge.chat.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.keyforge.chat.data.crypto.KeystoreCrypto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("keyforge_settings")

/** User preferences. Sensitive values (none here yet) would go through KeystoreCrypto. */
class SettingsStore(private val context: Context) {

    object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val ROUTING_PRESET = stringPreferencesKey("routing_preset")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
    }

    val themeMode: Flow<String> = context.settingsDataStore.data.map { it[Keys.THEME_MODE] ?: "System" }
    val dynamicColor: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.DYNAMIC_COLOR] ?: true }
    val biometricLock: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.BIOMETRIC_LOCK] ?: false }
    val routingPreset: Flow<String> = context.settingsDataStore.data.map { it[Keys.ROUTING_PRESET] ?: "Balanced" }
    val onboarded: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.ONBOARDED] ?: false }
    val reduceMotion: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.REDUCE_MOTION] ?: false }

    suspend fun setThemeMode(mode: String) = context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode }
    suspend fun setDynamicColor(v: Boolean) = context.settingsDataStore.edit { it[Keys.DYNAMIC_COLOR] = v }
    suspend fun setBiometricLock(v: Boolean) = context.settingsDataStore.edit { it[Keys.BIOMETRIC_LOCK] = v }
    suspend fun setRoutingPreset(p: String) = context.settingsDataStore.edit { it[Keys.ROUTING_PRESET] = p }
    suspend fun setOnboarded(v: Boolean) = context.settingsDataStore.edit { it[Keys.ONBOARDED] = v }
    suspend fun setReduceMotion(v: Boolean) = context.settingsDataStore.edit { it[Keys.REDUCE_MOTION] = v }
}
