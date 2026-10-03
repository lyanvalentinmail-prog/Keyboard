package com.atlas.keyboard.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.atlasDataStore by preferencesDataStore(name = "atlas_settings")

/**
 * Acceso tipado a las preferencias de Atlas Keyboard almacenadas con DataStore.
 * Los colores usan el centinela [NO_COLOR] y las métricas [NO_FLOAT] cuando no
 * hay personalización activa.
 */
class SettingsRepository(private val context: Context) {

    companion object {
        const val NO_COLOR = Int.MIN_VALUE
        const val NO_FLOAT = -1f

        private val KEY_VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        private val KEY_VIBRATION_STRENGTH = intPreferencesKey("vibration_strength")
        private val KEY_SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        private val KEY_AUTO_CAPS = booleanPreferencesKey("auto_caps")
        private val KEY_AUTO_CORRECT = booleanPreferencesKey("auto_correct")
        private val KEY_SUGGESTIONS = booleanPreferencesKey("suggestions_enabled")
        private val KEY_NUMBER_ROW = booleanPreferencesKey("show_number_row")
        private val KEY_THEME_ID = stringPreferencesKey("theme_id")
        private val KEY_CUSTOM_BG = intPreferencesKey("custom_bg")
        private val KEY_CUSTOM_KEY = intPreferencesKey("custom_key")
        private val KEY_CUSTOM_TEXT = intPreferencesKey("custom_text")
        private val KEY_CUSTOM_RADIUS = floatPreferencesKey("custom_radius")
        private val KEY_CUSTOM_TEXT_SIZE = floatPreferencesKey("custom_text_size")
        private val KEY_KEYBOARD_HEIGHT = intPreferencesKey("keyboard_height_dp")
        private val KEY_LANGUAGE = stringPreferencesKey("language")
        private val KEY_TEXT_STYLE = stringPreferencesKey("text_style_id")

        const val MIN_HEIGHT_DP = 200
        const val MAX_HEIGHT_DP = 320
        const val MIN_RADIUS_DP = 2f
        const val MAX_RADIUS_DP = 20f
        const val MIN_TEXT_SIZE_SP = 14f
        const val MAX_TEXT_SIZE_SP = 26f
    }

    val settingsFlow: Flow<AtlasSettings> = context.atlasDataStore.data.map { p ->
        AtlasSettings(
            vibrationEnabled = p[KEY_VIBRATION_ENABLED] ?: true,
            vibrationStrength = (p[KEY_VIBRATION_STRENGTH] ?: 45).coerceIn(0, 100),
            soundEnabled = p[KEY_SOUND_ENABLED] ?: false,
            autoCaps = p[KEY_AUTO_CAPS] ?: true,
            autoCorrect = p[KEY_AUTO_CORRECT] ?: true,
            suggestionsEnabled = p[KEY_SUGGESTIONS] ?: true,
            showNumberRow = p[KEY_NUMBER_ROW] ?: false,
            themeId = p[KEY_THEME_ID] ?: "dark",
            customBackground = (p[KEY_CUSTOM_BG] ?: NO_COLOR).toNullableColor(),
            customKeyColor = (p[KEY_CUSTOM_KEY] ?: NO_COLOR).toNullableColor(),
            customTextColor = (p[KEY_CUSTOM_TEXT] ?: NO_COLOR).toNullableColor(),
            customRadiusDp = (p[KEY_CUSTOM_RADIUS] ?: NO_FLOAT).toNullableFloat(),
            customTextSizeSp = (p[KEY_CUSTOM_TEXT_SIZE] ?: NO_FLOAT).toNullableFloat(),
            keyboardHeightDp = (p[KEY_KEYBOARD_HEIGHT] ?: 240).coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP),
            language = p[KEY_LANGUAGE] ?: "es",
            textStyleId = p[KEY_TEXT_STYLE] ?: "normal"
        )
    }

    private fun Int.toNullableColor(): Int? = if (this == NO_COLOR) null else this
    private fun Float.toNullableFloat(): Float? = if (this == NO_FLOAT) null else this

    suspend fun setVibrationEnabled(value: Boolean) {
        context.atlasDataStore.edit { it[KEY_VIBRATION_ENABLED] = value }
    }

    suspend fun setVibrationStrength(value: Int) {
        context.atlasDataStore.edit { it[KEY_VIBRATION_STRENGTH] = value.coerceIn(0, 100) }
    }

    suspend fun setSoundEnabled(value: Boolean) {
        context.atlasDataStore.edit { it[KEY_SOUND_ENABLED] = value }
    }

    suspend fun setAutoCaps(value: Boolean) {
        context.atlasDataStore.edit { it[KEY_AUTO_CAPS] = value }
    }

    suspend fun setAutoCorrect(value: Boolean) {
        context.atlasDataStore.edit { it[KEY_AUTO_CORRECT] = value }
    }

    suspend fun setSuggestionsEnabled(value: Boolean) {
        context.atlasDataStore.edit { it[KEY_SUGGESTIONS] = value }
    }

    suspend fun setShowNumberRow(value: Boolean) {
        context.atlasDataStore.edit { it[KEY_NUMBER_ROW] = value }
    }

    suspend fun setThemeId(value: String) {
        context.atlasDataStore.edit { it[KEY_THEME_ID] = value }
    }

    suspend fun setCustomBackground(color: Int?) {
        context.atlasDataStore.edit { it[KEY_CUSTOM_BG] = color ?: NO_COLOR }
    }

    suspend fun setCustomKeyColor(color: Int?) {
        context.atlasDataStore.edit { it[KEY_CUSTOM_KEY] = color ?: NO_COLOR }
    }

    suspend fun setCustomTextColor(color: Int?) {
        context.atlasDataStore.edit { it[KEY_CUSTOM_TEXT] = color ?: NO_COLOR }
    }

    suspend fun setCustomRadiusDp(value: Float?) {
        context.atlasDataStore.edit { it[KEY_CUSTOM_RADIUS] = value ?: NO_FLOAT }
    }

    suspend fun setCustomTextSizeSp(value: Float?) {
        context.atlasDataStore.edit { it[KEY_CUSTOM_TEXT_SIZE] = value ?: NO_FLOAT }
    }

    suspend fun setKeyboardHeightDp(value: Int) {
        context.atlasDataStore.edit {
            it[KEY_KEYBOARD_HEIGHT] = value.coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)
        }
    }

    suspend fun setLanguage(value: String) {
        context.atlasDataStore.edit { it[KEY_LANGUAGE] = value }
    }

    suspend fun setTextStyleId(value: String) {
        context.atlasDataStore.edit { it[KEY_TEXT_STYLE] = value }
    }

    /** Elimina todas las personalizaciones del tema (colores, radio, tamaño). */
    suspend fun resetCustomization() {
        context.atlasDataStore.edit {
            it[KEY_CUSTOM_BG] = NO_COLOR
            it[KEY_CUSTOM_KEY] = NO_COLOR
            it[KEY_CUSTOM_TEXT] = NO_COLOR
            it[KEY_CUSTOM_RADIUS] = NO_FLOAT
            it[KEY_CUSTOM_TEXT_SIZE] = NO_FLOAT
        }
    }
}
