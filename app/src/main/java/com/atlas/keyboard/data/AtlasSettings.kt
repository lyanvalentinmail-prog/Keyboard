package com.atlas.keyboard.data

/**
 * Preferencias completas de Atlas Keyboard. Los campos `custom*` a `null`
 * significan "usar el valor del tema base seleccionado".
 */
data class AtlasSettings(
    val vibrationEnabled: Boolean = true,
    /** 0..100 */
    val vibrationStrength: Int = 45,
    val soundEnabled: Boolean = false,
    val autoCaps: Boolean = true,
    val autoCorrect: Boolean = true,
    val suggestionsEnabled: Boolean = true,
    val showNumberRow: Boolean = false,
    val themeId: String = "dark",
    val customBackground: Int? = null,
    val customKeyColor: Int? = null,
    val customTextColor: Int? = null,
    val customRadiusDp: Float? = null,
    val customTextSizeSp: Float? = null,
    val keyboardHeightDp: Int = 240,
    val language: String = "es"
)
