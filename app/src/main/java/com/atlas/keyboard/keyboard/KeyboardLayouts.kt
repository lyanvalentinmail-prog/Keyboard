package com.atlas.keyboard.keyboard

/**
 * Construye las distribuciones QWERTY de letras (es/en) y las dos páginas de
 * símbolos del teclado. Las distribuciones son datos puros; el renderizado y la
 * interacción viven en [KeyboardView] y [com.atlas.keyboard.AtlasInputMethodService].
 */
object KeyboardLayouts {

    /** Acentos y variantes disponibles al mantener pulsada una vocal/letra. */
    private val ACCENTS: Map<Char, List<String>> = mapOf(
        'a' to listOf("á", "à", "â", "ä", "ã"),
        'e' to listOf("é", "è", "ê", "ë"),
        'i' to listOf("í", "ì", "î", "ï"),
        'o' to listOf("ó", "ò", "ô", "ö", "õ"),
        'u' to listOf("ú", "ü", "ù", "û"),
        'n' to listOf("ñ"),
        'c' to listOf("ç"),
        's' to listOf("ß"),
        'y' to listOf("ÿ")
    )

    /** Distribución principal de letras para el idioma dado ("es" | "en"). */
    fun letters(language: String, shift: ShiftState, showNumberRow: Boolean): Keyboard {
        val rows = mutableListOf<KeyboardRow>()
        if (showNumberRow) rows += numberRow()
        if (language == "en") {
            rows += letterRow("qwertyuiop", shift, withDigits = true)
            rows += KeyboardRow(letterRow("asdfghjkl", shift).keys, leadingInset = 0.5f)
            rows += letterRowWithShift("zxcvbnm", shift)
            rows += bottomRow(inSymbols = false, spaceLabel = "SPACE")
        } else {
            rows += letterRow("qwertyuiop", shift, withDigits = true)
            rows += letterRow("asdfghjklñ", shift)
            rows += letterRowWithShift("zxcvbnm", shift)
            rows += bottomRow(inSymbols = false, spaceLabel = "ESPACIO")
        }
        return Keyboard(rows)
    }

    /** Páginas de números y símbolos. */
    fun symbols(page: Int, language: String): Keyboard {
        val spaceLabel = if (language == "en") "SPACE" else "ESPACIO"
        val rows = mutableListOf<KeyboardRow>()
        if (page == 1) {
            rows += symbolRow(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"))
            rows += symbolRow(listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/"))
            rows += KeyboardRow(
                listOf(
                    Key(label = "=<", type = KeyType.SYMBOLS_PAGE, weight = 1.5f),
                    symbolKey("!"), symbolKey("*"), symbolKey("\""), symbolKey("'"),
                    symbolKey(":"), symbolKey(";"), symbolKey("?"),
                    Key(label = "⌫", type = KeyType.DELETE, weight = 1.5f, repeatable = true)
                )
            )
        } else {
            rows += symbolRow(listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "°"))
            rows += symbolRow(listOf("€", "£", "¥", "¢", "©", "®", "™", "[", "]", "§"))
            rows += KeyboardRow(
                listOf(
                    Key(label = "123", type = KeyType.SYMBOLS_PAGE, weight = 1.5f),
                    symbolKey("{"), symbolKey("}"), symbolKey("<"), symbolKey(">"),
                    symbolKey("^"), symbolKey("¡"), symbolKey("¿"),
                    Key(label = "⌫", type = KeyType.DELETE, weight = 1.5f, repeatable = true)
                )
            )
        }
        rows += bottomRow(inSymbols = true, spaceLabel = spaceLabel)
        return Keyboard(rows)
    }

    // ------------------------------------------------------------------ //

    private fun numberRow(): KeyboardRow = KeyboardRow(
        (1..9).map { i -> Key(label = i.toString()) } + Key(label = "0")
    )

    private fun letterKey(c: Char, shift: ShiftState, alternates: List<String>): Key {
        val base = c.toString()
        val shifted = shift != ShiftState.OFF
        val longPress = if (shifted) {
            alternates.map { alt ->
                if (alt.length == 1 && alt[0].isLetter()) alt.uppercase() else alt
            }
        } else alternates
        return Key(
            label = if (shifted) base.uppercase() else base,
            output = if (shifted) base.uppercase() else base,
            longPress = longPress
        )
    }

    private fun letterRow(chars: String, shift: ShiftState, withDigits: Boolean = false): KeyboardRow {
        val keys = chars.mapIndexed { index, c ->
            val alternates = buildList {
                if (withDigits) add(((index + 1) % 10).toString())
                addAll(ACCENTS[c] ?: emptyList())
            }
            letterKey(c, shift, alternates)
        }
        return KeyboardRow(keys)
    }

    private fun letterRowWithShift(chars: String, shift: ShiftState): KeyboardRow {
        val keys = mutableListOf<Key>()
        keys += Key(
            label = if (shift == ShiftState.LOCKED) "⇪" else "⇧",
            type = KeyType.SHIFT,
            weight = 1.5f,
            highlighted = shift != ShiftState.OFF
        )
        keys += chars.map { letterKey(it, shift, ACCENTS[it] ?: emptyList()) }
        keys += Key(label = "⌫", type = KeyType.DELETE, weight = 1.5f, repeatable = true)
        return KeyboardRow(keys)
    }

    private fun symbolKey(label: String): Key = Key(label = label)

    private fun symbolRow(labels: List<String>): KeyboardRow = KeyboardRow(labels.map(::symbolKey))

    private fun bottomRow(inSymbols: Boolean, spaceLabel: String): KeyboardRow = KeyboardRow(
        listOf(
            Key(
                label = if (inSymbols) "ABC" else "?123",
                type = KeyType.MODE_SYMBOLS,
                weight = 1.4f
            ),
            Key(label = "😀", type = KeyType.EMOJI, weight = 1f),
            Key(label = "📋", type = KeyType.CLIPBOARD, weight = 1f),
            Key(label = ",", weight = 1f, longPress = listOf(";", ":", "!", "¡")),
            Key(label = spaceLabel, output = " ", type = KeyType.SPACE, weight = 4.2f),
            Key(label = ".", weight = 1f, longPress = listOf(",", ";", ":", "…", "?", "¿", "-")),
            Key(label = "↵", type = KeyType.ENTER, weight = 1.5f)
        )
    )
}
