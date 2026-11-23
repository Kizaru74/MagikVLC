package com.magikvlc.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    val autoPip: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_AUTO_PIP] ?: false }

    suspend fun setAutoPip(value: Boolean) {
        runCatching {
            context.settingsDataStore.edit { prefs -> prefs[KEY_AUTO_PIP] = value }
        }
    }

    private companion object {
        val KEY_AUTO_PIP = booleanPreferencesKey("auto_pip")
    }
}