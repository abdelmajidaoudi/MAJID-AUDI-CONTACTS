package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aa_contacts_prefs")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_COUNTRY_CODE = stringPreferencesKey("default_country_code")
        val DRIVE_ACCOUNT_EMAIL = stringPreferencesKey("drive_account_email")
        val LAST_DRIVE_SYNC_TS = longPreferencesKey("last_drive_sync_ts")
        val LAST_DRIVE_SYNC_INFO = stringPreferencesKey("last_drive_sync_info")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.SYSTEM)
    }

    val defaultCountryCode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_COUNTRY_CODE] ?: "212"
    }

    val driveAccountEmail: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.DRIVE_ACCOUNT_EMAIL]?.takeIf { it.isNotBlank() }
    }

    val lastDriveSyncTimestamp: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_DRIVE_SYNC_TS] ?: 0L
    }

    val lastDriveSyncInfo: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_DRIVE_SYNC_INFO] ?: ""
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs -> prefs[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDefaultCountryCode(code: String) {
        val clean = code.filter { it.isDigit() }.ifEmpty { "212" }
        context.dataStore.edit { prefs -> prefs[Keys.DEFAULT_COUNTRY_CODE] = clean }
    }

    suspend fun setDriveAccountEmail(email: String?) {
        context.dataStore.edit { prefs ->
            if (email.isNullOrBlank()) prefs.remove(Keys.DRIVE_ACCOUNT_EMAIL)
            else prefs[Keys.DRIVE_ACCOUNT_EMAIL] = email
        }
    }

    suspend fun recordDriveSync(timestamp: Long, summary: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_DRIVE_SYNC_TS] = timestamp
            prefs[Keys.LAST_DRIVE_SYNC_INFO] = summary
        }
    }
}
