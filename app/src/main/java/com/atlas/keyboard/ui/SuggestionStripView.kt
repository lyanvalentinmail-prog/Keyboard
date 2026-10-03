package com.atlas.keyboard.ui

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.atlas.keyboard.theme.KeyboardTheme
import com.atlas.keyboard.theme.KeyboardThemes
import com.atlas.keyboard.theme.withAlpha

/**
 * Franja superior del teclado con hasta 3 sugerencias seleccionables.
 * La sugerencia marcada como corrección se muestra resaltada en negrita.
 */
class SuggestionStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    interface Listener {
        fun onSuggestionPicked(word: String)
    }

    var listener: Listener? = null

    private val slots = Array(3) { TextView(context) }
    private val dividers = Array(2) { View(context) }
    private var texts: List<String> = emptyList()
    private var theme: KeyboardTheme = KeyboardThemes.DARK
    private var highlightIndex = -1
    private var textSizeSp = 16f

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val density = resources.displayMetrics.density

        slots.forEachIndexed { index, tv ->
            tv.layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            tv.gravity = Gravity.CENTER
            tv.setSingleLine()
            tv.ellipsize = android.text.TextUtils.TruncateAt.END
            tv.isClickable = true
            tv.isFocusable = true
            tv.setOnClickListener {
                val word = texts.getOrNull(index).orEmpty()
                if (word.isNotEmpty()) listener?.onSuggestionPicked(word)
            }
            addView(tv)
            if (index < 2) {
                val divider = dividers[index]
                val margin = (10 * density).toInt()
                val lp = LayoutParams((1 * density).toInt().coerceAtLeast(1), 0)
                lp.height = LayoutParams.MATCH_PARENT
                lp.topMargin = margin
                lp.bottomMargin = margin
                divider.layoutParams = lp
                addView(divider)
            }
        }
        applyTheme(theme)
    }

    /** [list] contiene hasta 3 textos; [highlight] marca el índice resaltado o -1. */
    fun updateSuggestions(list: List<String>, highlight: Int = -1, textSize: Float = 16f) {
        texts = list.take(3)
        highlightIndex = highlight
        textSizeSp = textSize
        refresh()
    }

    fun applyTheme(newTheme: KeyboardTheme) {
        theme = newTheme
        setBackgroundColor(newTheme.backgroundColor)
        dividers.forEach { it.setBackgroundColor(withAlpha(newTheme.suggestionText, 0.25f)) }
        refresh()
    }

    private fun refresh() {
        slots.forEachIndexed { index, tv ->
            val text = texts.getOrNull(index).orEmpty()
            tv.text = text
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            if (index == highlightIndex && text.isNotEmpty()) {
                tv.setTextColor(theme.suggestionAccent)
                tv.setTypeface(null, Typeface.BOLD)
            } else {
                tv.setTextColor(theme.suggestionText)
                tv.setTypeface(null, Typeface.NORMAL)
            }
        }
        dividers.forEachIndexed { index, divider ->
            val visible = texts.getOrNull(index).orEmpty().isNotEmpty() &&
                    texts.getOrNull(index + 1).orEmpty().isNotEmpty()
            divider.visibility = if (visible) VISIBLE else GONE
        }
    }
}
