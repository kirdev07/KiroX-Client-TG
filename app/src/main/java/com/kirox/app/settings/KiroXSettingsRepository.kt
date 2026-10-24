package com.kirox.app.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kirox.app.ui.theme.FusionPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.kiroxSettings by preferencesDataStore("kirox_settings")

/** Local KiroX preferences (never Telegram data). */
class KiroXSettingsRepository(context: Context) {
    private val store = context.applicationContext.kiroxSettings
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val paletteKey = stringPreferencesKey("palette")

    val palette: StateFlow<FusionPalette> = store.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs -> FusionPalette.entries.firstOrNull { it.name == prefs[paletteKey] } ?: FusionPalette.AmoledBlack }
        .stateIn(scope, SharingStarted.Eagerly, FusionPalette.AmoledBlack)

    fun setPalette(value: FusionPalette) {
        scope.launch { store.edit { it[paletteKey] = value.name } }
    }
}
