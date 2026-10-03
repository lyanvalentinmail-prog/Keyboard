package com.atlas.keyboard.theme

import android.graphics.Color

/**
 * Paleta y métricas con las que se dibuja el teclado.
 * Todos los colores son ARGB; radios en dp; tamaños de texto en sp.
 */
data class KeyboardTheme(
    val id: String,
    val backgroundColor: Int,
    val keyBackground: Int,
    val keyText: Int,
    val specialKeyBackground: Int,
    val specialKeyText: Int,
    val accentColor: Int,
    val suggestionText: Int,
    val suggestionAccent: Int,
    val popupBackground: Int,
    val popupText: Int,
    val cornerRadiusDp: Float,
    val keyTextSizeSp: Float,
    val specialKeyTextSizeSp: Float,
    /** Color con alfa propio usado como superposición al pulsar una tecla. */
    val pressedOverlay: Int
)

/** Temas incluidos de serie. */
object KeyboardThemes {

    val DARK = KeyboardTheme(
        id = "dark",
        backgroundColor = Color.parseColor("#121417"),
        keyBackground = Color.parseColor("#26292E"),
        keyText = Color.parseColor("#E8EAED"),
        specialKeyBackground = Color.parseColor("#1B1E22"),
        specialKeyText = Color.parseColor("#9AA0A6"),
        accentColor = Color.parseColor("#8AB4F8"),
        suggestionText = Color.parseColor("#E8EAED"),
        suggestionAccent = Color.parseColor("#8AB4F8"),
        popupBackground = Color.parseColor("#2F3338"),
        popupText = Color.parseColor("#E8EAED"),
        cornerRadiusDp = 8f,
        keyTextSizeSp = 19f,
        specialKeyTextSizeSp = 14f,
        pressedOverlay = Color.parseColor("#408AB4F8")
    )

    val LIGHT = KeyboardTheme(
        id = "light",
        backgroundColor = Color.parseColor("#E8EAEE"),
        keyBackground = Color.parseColor("#FFFFFF"),
        keyText = Color.parseColor("#202124"),
        specialKeyBackground = Color.parseColor("#D5D9E0"),
        specialKeyText = Color.parseColor("#4A4F55"),
        accentColor = Color.parseColor("#1A73E8"),
        suggestionText = Color.parseColor("#202124"),
        suggestionAccent = Color.parseColor("#1A73E8"),
        popupBackground = Color.parseColor("#FFFFFF"),
        popupText = Color.parseColor("#202124"),
        cornerRadiusDp = 8f,
        keyTextSizeSp = 19f,
        specialKeyTextSizeSp = 14f,
        pressedOverlay = Color.parseColor("#351A73E8")
    )

    val AMOLED = KeyboardTheme(
        id = "amoled",
        backgroundColor = Color.parseColor("#000000"),
        keyBackground = Color.parseColor("#141414"),
        keyText = Color.parseColor("#E6E6E6"),
        specialKeyBackground = Color.parseColor("#0B0B0B"),
        specialKeyText = Color.parseColor("#9E9E9E"),
        accentColor = Color.parseColor("#64FFDA"),
        suggestionText = Color.parseColor("#E6E6E6"),
        suggestionAccent = Color.parseColor("#64FFDA"),
        popupBackground = Color.parseColor("#1D1D1D"),
        popupText = Color.parseColor("#E6E6E6"),
        cornerRadiusDp = 8f,
        keyTextSizeSp = 19f,
        specialKeyTextSizeSp = 14f,
        pressedOverlay = Color.parseColor("#4064FFDA")
    )

    val MINIMAL = KeyboardTheme(
        id = "minimal",
        backgroundColor = Color.parseColor("#1A1D21"),
        keyBackground = Color.parseColor("#22262B"),
        keyText = Color.parseColor("#DDE3EA"),
        specialKeyBackground = Color.parseColor("#1A1D21"),
        specialKeyText = Color.parseColor("#8A919C"),
        accentColor = Color.parseColor("#C2C7CF"),
        suggestionText = Color.parseColor("#DDE3EA"),
        suggestionAccent = Color.parseColor("#C2C7CF"),
        popupBackground = Color.parseColor("#2A2E34"),
        popupText = Color.parseColor("#DDE3EA"),
        cornerRadiusDp = 4f,
        keyTextSizeSp = 18f,
        specialKeyTextSizeSp = 13f,
        pressedOverlay = Color.parseColor("#33FFFFFF")
    )

    val ALL: List<KeyboardTheme> = listOf(DARK, LIGHT, AMOLED, MINIMAL)

    fun byId(id: String): KeyboardTheme = ALL.firstOrNull { it.id == id } ?: DARK
}

/**
 * Aplica las personalizaciones del usuario sobre un tema base. Los valores
 * `null` mantienen el color del tema original; los colores dependientes
 * (teclas especiales, popup…) se derivan automáticamente.
 */
fun KeyboardTheme.customized(
    background: Int? = null,
    keyColor: Int? = null,
    textColor: Int? = null,
    radiusDp: Float? = null,
    textSizeSp: Float? = null
): KeyboardTheme {
    val newBg = background ?: backgroundColor
    val newKey = keyColor ?: keyBackground
    val newText = textColor ?: keyText
    val newRadius = radiusDp ?: cornerRadiusDp
    val newSize = textSizeSp ?: keyTextSizeSp
    return copy(
        backgroundColor = newBg,
        keyBackground = newKey,
        keyText = newText,
        specialKeyBackground = mix(newBg, newKey, 0.65f),
        specialKeyText = withAlpha(newText, 0.72f),
        popupBackground = mix(newKey, Color.WHITE, 0.08f),
        popupText = newText,
        suggestionText = newText,
        cornerRadiusDp = newRadius,
        keyTextSizeSp = newSize,
        specialKeyTextSizeSp = newSize * 0.75f
    )
}

/** Mezcla [a] con [b]; ratio 0f devuelve [a], 1f devuelve [b]. */
internal fun mix(a: Int, b: Int, ratio: Float): Int {
    val r = ratio.coerceIn(0f, 1f)
    val inv = 1f - r
    return Color.argb(
        (Color.alpha(a) * inv + Color.alpha(b) * r).toInt(),
        (Color.red(a) * inv + Color.red(b) * r).toInt(),
        (Color.green(a) * inv + Color.green(b) * r).toInt(),
        (Color.blue(a) * inv + Color.blue(b) * r).toInt()
    )
}

internal fun withAlpha(color: Int, alphaFactor: Float): Int {
    val alpha = (Color.alpha(color) * alphaFactor).toInt().coerceIn(0, 255)
    return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}

/** Luz percibida del color (0..1) para elegir texto legible encima. */
internal fun luminance(color: Int): Float {
    return (0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)) / 255f
}

/** Texto oscuro o claro según contraste con el fondo dado. */
fun readableTextOn(background: Int): Int =
    if (luminance(background) > 0.6f) Color.parseColor("#202124") else Color.WHITE
