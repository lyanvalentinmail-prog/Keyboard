package com.atlas.keyboard.keyboard

/** Tipos funcionales de tecla que soporta el teclado. */
enum class KeyType {
    /** Tecla imprimible (letra o símbolo). El servicio decide si compone palabra. */
    CHARACTER,
    SHIFT,
    DELETE,
    ENTER,
    SPACE,
    /** ?123 / ABC: alterna entre letras y símbolos. */
    MODE_SYMBOLS,
    /** Alterna entre la página 1 y 2 de símbolos. */
    SYMBOLS_PAGE,
    EMOJI,
    CLIPBOARD
}

/** Estados de mayúsculas del teclado. */
enum class ShiftState { OFF, ON, LOCKED }

/** Panel mostrado actualmente en el área del teclado. */
enum class KeyboardMode { LETTERS, SYMBOLS_1, SYMBOLS_2, EMOJI, CLIPBOARD, TEXT_STYLES }

/**
 * Definición inmutable de una tecla.
 *
 * @param label       Texto dibujado en la tecla.
 * @param output      Texto que se inserta cuando el tipo es [KeyType.CHARACTER].
 * @param weight      Peso relativo dentro de la fila (una tecla normal pesa 1f).
 * @param longPress   Alternativas mostradas al mantener pulsada la tecla.
 * @param highlighted La tecla se dibuja resaltada (p. ej. shift activo).
 * @param repeatable  Si al mantenerla se repite la acción (borrado).
 * @param icon        VectorDrawable opcional (0 = dibujar solo la etiqueta).
 */
data class Key(
    val label: String,
    val output: String = label,
    val type: KeyType = KeyType.CHARACTER,
    val weight: Float = 1f,
    val longPress: List<String> = emptyList(),
    val highlighted: Boolean = false,
    val repeatable: Boolean = false,
    val icon: Int = 0
)

/**
 * Fila de teclas. [leadingInset]/[trailingInset] son sangrías laterales medidas
 * en unidades de tecla estándar (la fila base siempre equivale a 10 unidades).
 */
data class KeyboardRow(
    val keys: List<Key>,
    val leadingInset: Float = 0f,
    val trailingInset: Float = 0f
)

/** Un teclado completo: lista de filas de arriba abajo. */
data class Keyboard(val rows: List<KeyboardRow>)
