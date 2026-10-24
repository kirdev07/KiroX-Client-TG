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

enum class BubbleShape(val title: String, val radiusDp: Float) { Soft("Мягкие", 22f), Medium("Средние", 16f), Strict("Строгие", 6f) }

enum class MotionLevel(val title: String, val factor: Float) { Normal("Обычные", 1f), Reduced("Меньше", 0.5f), Off("Выкл.", 0f) }

/** Local KiroX preferences (never Telegram data). */
class KiroXSettingsRepository(context: Context) {
    private val store = context.applicationContext.kiroxSettings
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val paletteKey = stringPreferencesKey("palette")
    private val seasonalKey = androidx.datastore.preferences.core.booleanPreferencesKey("seasonal_effects")
    private val bubbleKey = stringPreferencesKey("bubble_shape")
    private val motionKey = stringPreferencesKey("motion")

    val palette: StateFlow<FusionPalette> = store.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs -> FusionPalette.entries.firstOrNull { it.name == prefs[paletteKey] } ?: FusionPalette.AmoledBlack }
        .stateIn(scope, SharingStarted.Eagerly, FusionPalette.AmoledBlack)

    /** Light seasonal particles in screen headers; on by default, like the old Fusion. */
    val seasonalEffects: StateFlow<Boolean> = store.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { it[seasonalKey] ?: true }
        .stateIn(scope, SharingStarted.Eagerly, true)

    fun setSeasonalEffects(enabled: Boolean) {
        scope.launch { store.edit { it[seasonalKey] = enabled } }
    }

    val bubbleShape: StateFlow<BubbleShape> = store.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs -> BubbleShape.entries.firstOrNull { it.name == prefs[bubbleKey] } ?: BubbleShape.Medium }
        .stateIn(scope, SharingStarted.Eagerly, BubbleShape.Medium)

    val motion: StateFlow<MotionLevel> = store.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs -> MotionLevel.entries.firstOrNull { it.name == prefs[motionKey] } ?: MotionLevel.Normal }
        .stateIn(scope, SharingStarted.Eagerly, MotionLevel.Normal)

    fun setBubbleShape(value: BubbleShape) { scope.launch { store.edit { it[bubbleKey] = value.name } } }
    fun setMotion(value: MotionLevel) { scope.launch { store.edit { it[motionKey] = value.name } } }

    fun setPalette(value: FusionPalette) {
        scope.launch { store.edit { it[paletteKey] = value.name } }
    }
}
