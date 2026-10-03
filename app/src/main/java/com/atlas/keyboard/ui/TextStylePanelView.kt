package com.atlas.keyboard.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.atlas.keyboard.R
import com.atlas.keyboard.textstyle.TextStyles
import com.atlas.keyboard.theme.KeyboardTheme
import com.atlas.keyboard.theme.KeyboardThemes
import com.atlas.keyboard.theme.withAlpha

/**
 * Panel de fuentes de texto: lista los estilos disponibles (Normal, Negrita,
 * Cursiva…) con una vista previa escrita en su propio estilo. El estilo activo
 * se muestra resaltado con una marca de verificación.
 */
class TextStylePanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    interface Listener {
        fun onStyleSelected(styleId: String)
        fun onBackToKeyboard()
    }

    var listener: Listener? = null

    private var theme: KeyboardTheme = KeyboardThemes.DARK
    private var activeStyleId: String = TextStyles.NORMAL.id
    private val density = resources.displayMetrics.density

    private class RowRefs(val row: View, val preview: TextView, val check: TextView)

    private val rows = LinkedHashMap<String, RowRefs>()
    private val backButton: TextView
    private val titleView: TextView

    init {
        orientation = VERTICAL

        val topBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        backButton = TextView(context).apply {
            text = context.getString(R.string.back_to_keyboard)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            val pad = (14 * density).toInt()
            setPadding(pad, 0, pad, 0)
            setOnClickListener { listener?.onBackToKeyboard() }
        }
        titleView = TextView(context).apply {
            text = context.getString(R.string.text_styles_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
        }
        topBar.addView(backButton, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        topBar.addView(titleView, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        // Espaciador para equilibrar el botón ABC.
        topBar.addView(View(context), LayoutParams(LayoutParams.WRAP_CONTENT, 1))
        addView(topBar, LayoutParams(LayoutParams.MATCH_PARENT, (48 * density).toInt()))

        val listContainer = LinearLayout(context).apply { orientation = VERTICAL }
        TextStyles.ALL.forEach { style -> listContainer.addView(buildStyleRow(style)) }
        val scroll = ScrollView(context).apply {
            addView(listContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        applyTheme(theme)
    }

    private fun buildStyleRow(style: TextStyles.Style): View {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val preview = TextView(context).apply {
            text = style.transform("Atlas Abc 123") + "  ·  " + style.displayName
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            val padH = (16 * density).toInt()
            setPadding(padH, 0, padH, 0)
            val out = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
            setBackgroundResource(out.resourceId)
        }
        row.addView(preview, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))

        val check = TextView(context).apply {
            text = "✓"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            visibility = GONE
            val pad = (16 * density).toInt()
            setPadding(pad, 0, pad, 0)
        }
        row.addView(check, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))

        row.setOnClickListener { listener?.onStyleSelected(style.id) }
        rows[style.id] = RowRefs(row, preview, check)

        val wrapper = LinearLayout(context).apply { orientation = VERTICAL }
        val height = (52 * density).toInt()
        wrapper.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, height))
        val divider = View(context).apply {
            setBackgroundColor(withAlpha(theme.specialKeyText, 0.2f))
        }
        wrapper.addView(
            divider,
            LayoutParams(LayoutParams.MATCH_PARENT, (1 * density).toInt().coerceAtLeast(1))
        )
        return wrapper
    }

    fun applyTheme(newTheme: KeyboardTheme) {
        theme = newTheme
        setBackgroundColor(theme.backgroundColor)
        backButton.setTextColor(theme.specialKeyText)
        titleView.setTextColor(theme.keyText)
        rows.values.forEach { refs ->
            refs.preview.setTextColor(theme.keyText)
            refs.check.setTextColor(theme.suggestionAccent)
        }
        refreshSelection()
    }

    /** Resalta la fila del estilo activo y marca el resto como inactivo. */
    fun setActiveStyle(styleId: String) {
        activeStyleId = styleId
        refreshSelection()
    }

    private fun refreshSelection() {
        rows.forEach { (id, refs) ->
            val selected = id == activeStyleId
            refs.row.setBackgroundColor(
                if (selected) withAlpha(theme.accentColor, 0.16f) else Color.TRANSPARENT
            )
            refs.check.visibility = if (selected) VISIBLE else GONE
        }
    }
}
