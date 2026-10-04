package com.atlas.keyboard.textstyle

/**
 * Estilos tipográficos basados en caracteres Unicode (bloque Mathematical
 * Alphanumeric Symbols y similares). Al estar activo un estilo, las letras y
 * dígitos ASCII que escribes se transforman al estilo elegido.
 *
 * Los caracteres estilizados siguen siendo Unicode válido: funcionan en
 * WhatsApp, Telegram, Instagram, etc. sin fuentes externas ni red.
 */
object TextStyles {

    class Style internal constructor(
        val id: String,
        val displayName: String,
        internal val forward: Map<Char, Int>   // ASCII -> punto de código Unicode
    ) {

        /** Transforma letras y dígitos ASCII del texto al estilo actual. */
        fun transform(text: String): String {
            if (forward.isEmpty()) return text
            val out = StringBuilder(text.length)
            var i = 0
            while (i < text.length) {
                val cp = text.codePointAt(i)
                val mapped = if (cp in 0..0x7F) forward[cp.toChar()] else null
                if (mapped != null) out.appendCodePoint(mapped) else out.appendCodePoint(cp)
                i += Character.charCount(cp)
            }
            return out.toString()
        }
    }

    val NORMAL = Style("normal", "Normal", emptyMap())

    val SANS_BOLD = Style("sans_bold", "Negrita", rangeMap(0x1D5D4, 0x1D5EE, 0x1D7EC))

    val SANS_ITALIC = Style("sans_italic", "Cursiva", rangeMap(0x1D608, 0x1D622))

    val SANS_BOLD_ITALIC = Style(
        "sans_bold_italic", "Negrita cursiva", rangeMap(0x1D63C, 0x1D656)
    )

    val MONOSPACE = Style("monospace", "Monoespaciada", rangeMap(0x1D670, 0x1D68A, 0x1D7F6))

    val SCRIPT = Style("script", "Manuscrita", rangeMap(0x1D4D0, 0x1D4EA))

    val FRAKTUR = Style(
        "fraktur", "Gótica",
        rangeMap(
            0x1D504, 0x1D51E,
            exceptions = mapOf(
                'C' to 0x212D, 'H' to 0x210C, 'I' to 0x2111, 'R' to 0x211C, 'Z' to 0x2128
            )
        )
    )

    val DOUBLE_STRUCK = Style(
        "double_struck", "Doble trazo",
        rangeMap(
            0x1D538, 0x1D552, 0x1D7D8,
            exceptions = mapOf(
                'C' to 0x2102, 'H' to 0x210D, 'N' to 0x2115, 'P' to 0x2119,
                'Q' to 0x211A, 'R' to 0x211D, 'Z' to 0x2124
            )
        )
    )

    val CIRCLED: Style = Style(
        "circled", "Circulares",
        buildMap {
            for (c in 'A'..'Z') put(c, 0x24B6 + (c - 'A'))
            for (c in 'a'..'z') put(c, 0x24D0 + (c - 'a'))
            for (c in '1'..'9') put(c, 0x2460 + (c - '1'))
            put('0', 0x24EA)
        }
    )

    val SQUARED: Style = Style(
        "squared", "Cuadrados",
        buildMap {
            for (c in 'A'..'Z') {
                put(c, 0x1F170 + (c - 'A'))
                // Solo existen mayúsculas cuadradas: minúsculas -> su versión.
                put(c.lowercaseChar(), 0x1F170 + (c - 'A'))
            }
        }
    )

    val ALL: List<Style> = listOf(
        NORMAL, SANS_BOLD, SANS_ITALIC, SANS_BOLD_ITALIC, MONOSPACE,
        SCRIPT, FRAKTUR, DOUBLE_STRUCK, CIRCLED, SQUARED
    )

    fun byId(id: String): Style = ALL.firstOrNull { it.id == id } ?: NORMAL

    /**
     * Mapa inverso global: punto de código estilizado -> carácter ASCII. Lo usa
     * el motor de sugerencias para reconocer palabras escritas con estilos.
     */
    private val reverse: Map<Int, Char> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HashMap<Int, Char>().apply {
            ALL.forEach { style ->
                style.forward.forEach { (ascii, codePoint) ->
                    if (!containsKey(codePoint)) put(codePoint, ascii)
                }
            }
        }
    }

    /** ASCII equivalente de un punto de código estilizado, o `null`. */
    fun asciiOf(codePoint: Int): Char? = reverse[codePoint]

    /** Quita el estilo: convierte cada carácter estilizado a su ASCII base. */
    fun plain(text: String): String {
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val mapped = asciiOf(cp)
            if (mapped != null) out.append(mapped) else out.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return out.toString()
    }

    private fun rangeMap(
        upper: Int = 0,
        lower: Int = 0,
        digits: Int = 0,
        exceptions: Map<Char, Int> = emptyMap()
    ): Map<Char, Int> {
        val map = HashMap<Char, Int>()
        if (upper != 0) for (c in 'A'..'Z') map[c] = upper + (c - 'A')
        if (lower != 0) for (c in 'a'..'z') map[c] = lower + (c - 'a')
        if (digits != 0) for (c in '0'..'9') map[c] = digits + (c - '0')
        map.putAll(exceptions)
        return map
    }
}
