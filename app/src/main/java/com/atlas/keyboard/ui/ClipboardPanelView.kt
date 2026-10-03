package com.atlas.keyboard.ui

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.atlas.keyboard.R
import com.atlas.keyboard.theme.KeyboardTheme
import com.atlas.keyboard.theme.KeyboardThemes
import com.atlas.keyboard.theme.withAlpha

/**
 * Historial del portapapeles: lista los textos copiados recientemente,
 * permite insertarlos con un toque, eliminarlos individualmente o borrar todo.
 */
class ClipboardPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    interface Listener {
        fun onClipSelected(text: String)
        fun onDeleteClip(text: String)
        fun onClearAll()
        fun onBackToKeyboard()
    }

    var listener: Listener? = null

    private var theme: KeyboardTheme = KeyboardThemes.DARK
    private val density = resources.displayMetrics.density

    private val backButton: TextView
    private val titleView: TextView
    private val clearAllButton: TextView
    private val listContainer: LinearLayout
    private val scrollView: ScrollView
    private val emptyView: TextView

    init {
        orientation = VERTICAL

        val topBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        backButton = actionButton(context.getString(R.string.back_to_keyboard), bold = true) {
            listener?.onBackToKeyboard()
        }
        titleView = TextView(context).apply {
            text = context.getString(R.string.clipboard_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
        }
        clearAllButton = actionButton(context.getString(R.string.clipboard_clear_all), bold = false) {
            listener?.onClearAll()
        }
        topBar.addView(backButton, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        topBar.addView(titleView, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        topBar.addView(clearAllButton, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        addView(topBar, LayoutParams(LayoutParams.MATCH_PARENT, (48 * density).toInt()))

        listContainer = LinearLayout(context).apply { orientation = VERTICAL }
        scrollView = ScrollView(context).apply {
            addView(listContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        emptyView = TextView(context).apply {
            text = context.getString(R.string.clipboard_empty)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            visibility = GONE
        }
        addView(scrollView, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(emptyView, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        applyTheme(theme)
    }

    private fun actionButton(label: String, bold: Boolean, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            val pad = (12 * density).toInt()
            setPadding(pad, 0, pad, 0)
            setOnClickListener { onClick() }
        }
    }

    fun applyTheme(newTheme: KeyboardTheme) {
        theme = newTheme
        setBackgroundColor(theme.backgroundColor)
        backButton.setTextColor(theme.specialKeyText)
        titleView.setTextColor(theme.keyText)
        clearAllButton.setTextColor(theme.suggestionAccent)
        emptyView.setTextColor(theme.specialKeyText)
        scrollView.setBackgroundColor(theme.backgroundColor)
    }

    /** Reconstruye la lista visible. Se llama con el historial actual. */
    fun showItems(items: List<String>) {
        listContainer.removeAllViews()
        val hasItems = items.isNotEmpty()
        emptyView.visibility = if (hasItems) GONE else VISIBLE
        scrollView.visibility = if (hasItems) VISIBLE else GONE
        items.forEach { text -> listContainer.addView(buildItemRow(text)) }
    }

    private fun buildItemRow(text: String): View {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(theme.keyBackground)
        }

        val preview = TextView(context).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(theme.keyText)
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.END
            val padV = (12 * density).toInt()
            val padH = (14 * density).toInt()
            setPadding(padH, padV, padH, padV)
            val out = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
            setBackgroundResource(out.resourceId)
            setOnClickListener { listener?.onClipSelected(text) }
        }
        row.addView(preview, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

        val delete = TextView(context).apply {
            this.text = "✕"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(theme.specialKeyText)
            gravity = Gravity.CENTER
            val pad = (14 * density).toInt()
            setPadding(pad, 0, pad, 0)
            setOnClickListener { listener?.onDeleteClip(text) }
        }
        row.addView(delete, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))

        val wrapper = LinearLayout(context).apply {
            orientation = VERTICAL
        }
        wrapper.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val divider = View(context).apply {
            setBackgroundColor(withAlpha(theme.specialKeyText, 0.2f))
        }
        wrapper.addView(
            divider,
            LayoutParams(LayoutParams.MATCH_PARENT, (1 * density).toInt().coerceAtLeast(1))
        )
        return wrapper
    }
}
