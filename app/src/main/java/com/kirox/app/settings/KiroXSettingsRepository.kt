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

// A damaged settings file is replaced with defaults instead of failing every read and write.
private val Context.kiroxSettings by preferencesDataStore(
    "kirox_settings",
    corruptionHandler = androidx.datastore.core.handlers.ReplaceFileCorruptionHandler { androidx.datastore.preferences.core.emptyPreferences() }
)

enum class BubbleShape(val title: String, val radiusDp: Float) { Soft("Мягкие", 22f), Medium("Средние", 16f), Strict("Строгие", 6f) }

enum class ChatTextSize(val title: String, val scale: Float) { Small("Мелкий", 0.9f), Normal("Обычный", 1f), Large("Крупный", 1.12f), Huge("Огромный", 1.25f) }

/** [Theme] is the style's own artwork (Fusion UI v2) or the plain background in classic palettes. */
enum class ChatWallpaper(val title: String) { Theme("Тема"), None("Нет"), Pattern("Узор"), Gradient("Градиент"), Dots("Точки") }

/** [spacingDp] between messages, [paddingDp] vertical padding inside a bubble. */
enum class ChatDensity(val title: String, val spacingDp: Float, val paddingDp: Float) { Compact("Компактно", 2f, 6f), Normal("Обычно", 4f, 9f), Roomy("Свободно", 8f, 12f) }

/** Everything the chat screen needs from KiroX settings, in one value. */
data class ChatStyle(
    val bubbleRadiusDp: Float = BubbleShape.Medium.radiusDp,
    val textScale: Float = 1f,
    val wallpaper: ChatWallpaper = ChatWallpaper.Theme,
    val density: ChatDensity = ChatDensity.Normal
)

/** Decorative animation in headers, previews and on the splash screen. */
enum class EffectScene(val title: String, val subtitle: String) {
    Auto("Авто (по дате)", "Сама меняется к праздникам"),
    Main("Основная", "Мерцающие частицы kiroX"),
    Halloween("Хэллоуин", "Тыквы в костюмах, летучие мыши, паутина"),
    Winter("Зима", "Снежинки, сугробы с ёлками и иней"),
    NewYear("Новый год", "Гирлянда, ёлка с подарками и салют");

    /** The scene to draw: [Auto] turns into the one for today's date. */
    fun resolved(calendar: java.util.Calendar = java.util.Calendar.getInstance()): EffectScene =
        if (this == Auto) sceneForDate(calendar.get(java.util.Calendar.MONTH) + 1, calendar.get(java.util.Calendar.DAY_OF_MONTH)) else this
}

/**
 * Scene for a date ([month] 1–12): New Year 20 Dec – 7 Jan, Halloween 25–31 Oct,
 * winter for the rest of December–February, the main animation otherwise. Local date only.
 */
fun sceneForDate(month: Int, day: Int): EffectScene = when {
    (month == 12 && day >= 20) || (month == 1 && day <= 7) -> EffectScene.NewYear
    month == 10 && day >= 25 -> EffectScene.Halloween
    month == 12 || month == 1 || month == 2 -> EffectScene.Winter
    else -> EffectScene.Main
}

/** Detail of decorative effects. [Auto] picks [Light] on weak devices and in battery saver. */
enum class EffectsQuality(val title: String) { Auto("Авто"), Full("Высокое"), Light("Лёгкое") }

enum class MotionLevel(val title: String, val factor: Float) { Normal("Обычные", 1f), Reduced("Меньше", 0.5f), Off("Выкл.", 0f) }

/** Local KiroX preferences (never Telegram data). */
class KiroXSettingsRepository(context: Context) {
    private val store = context.applicationContext.kiroxSettings
    // A failed write (full disk) loses that one setting; it must not crash the app.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + kotlinx.coroutines.CoroutineExceptionHandler { _, _ -> })
    private val paletteKey = stringPreferencesKey("palette")
    private val seasonalKey = androidx.datastore.preferences.core.booleanPreferencesKey("seasonal_effects")
    private val bubbleKey = stringPreferencesKey("bubble_shape")
    private val motionKey = stringPreferencesKey("motion")
    private val qualityKey = stringPreferencesKey("effects_quality")
    private val sceneKey = stringPreferencesKey("effect_scene")
    private val textSizeKey = stringPreferencesKey("chat_text_size")
    private val wallpaperKey = stringPreferencesKey("chat_wallpaper")
    private val densityKey = stringPreferencesKey("chat_density")

    val palette: StateFlow<FusionPalette> = store.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        // A saved palette (old or new) is kept; only a fresh install starts with Fusion Expressive.
        .map { prefs -> FusionPalette.entries.firstOrNull { it.name == prefs[paletteKey] } ?: FusionPalette.Expressive }
        .stateIn(scope, SharingStarted.Eagerly, FusionPalette.Expressive)

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

    val effectsQuality: StateFlow<EffectsQuality> = store.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs -> EffectsQuality.entries.firstOrNull { it.name == prefs[qualityKey] } ?: EffectsQuality.Auto }
        .stateIn(scope, SharingStarted.Eagerly, EffectsQuality.Auto)

    /** Unset → Halloween during the Halloween season, the main animation otherwise. */
    val effectScene: StateFlow<EffectScene> = store.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs -> EffectScene.entries.firstOrNull { it.name == prefs[sceneKey] } ?: EffectScene.Auto }
        .stateIn(scope, SharingStarted.Eagerly, EffectScene.Auto)

    val chatTextSize: StateFlow<ChatTextSize> = enumFlow(textSizeKey, ChatTextSize.Normal)
    val chatWallpaper: StateFlow<ChatWallpaper> = enumFlow(wallpaperKey, ChatWallpaper.Theme)
    val chatDensity: StateFlow<ChatDensity> = enumFlow(densityKey, ChatDensity.Normal)

    /** Back to defaults for every KiroX look-and-feel setting; Telegram data is not touched. */
    fun resetAll() { scope.launch { store.edit { it.clear() } } }

    fun setChatTextSize(value: ChatTextSize) { scope.launch { store.edit { it[textSizeKey] = value.name } } }
    fun setChatWallpaper(value: ChatWallpaper) { scope.launch { store.edit { it[wallpaperKey] = value.name } } }
    fun setChatDensity(value: ChatDensity) { scope.launch { store.edit { it[densityKey] = value.name } } }

    private inline fun <reified E : Enum<E>> enumFlow(key: androidx.datastore.preferences.core.Preferences.Key<String>, default: E): StateFlow<E> = store.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs -> enumValues<E>().firstOrNull { it.name == prefs[key] } ?: default }
        .stateIn(scope, SharingStarted.Eagerly, default)

    fun setEffectScene(value: EffectScene) { scope.launch { store.edit { it[sceneKey] = value.name } } }


    fun setEffectsQuality(value: EffectsQuality) { scope.launch { store.edit { it[qualityKey] = value.name } } }

    fun setBubbleShape(value: BubbleShape) { scope.launch { store.edit { it[bubbleKey] = value.name } } }
    fun setMotion(value: MotionLevel) { scope.launch { store.edit { it[motionKey] = value.name } } }

    private val glassBlurKey = androidx.datastore.preferences.core.booleanPreferencesKey("glass_blur")
    /** Blur of the Glass backdrop glows (Android 12+). */
    val glassBlur: StateFlow<Boolean> = store.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { it[glassBlurKey] ?: true }
        .stateIn(scope, SharingStarted.Eagerly, true)
    fun setGlassBlur(value: Boolean) { scope.launch { store.edit { it[glassBlurKey] = value } } }

    fun setPalette(value: FusionPalette) {
        scope.launch { store.edit { it[paletteKey] = value.name } }
    }
}
