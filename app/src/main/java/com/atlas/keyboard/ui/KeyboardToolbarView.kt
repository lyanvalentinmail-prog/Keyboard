package com.atlas.keyboard.ui

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.content.res.AppCompatResources
import com.atlas.keyboard.R
import com.atlas.keyboard.theme.KeyboardTheme
import com.atlas.keyboard.theme.KeyboardThemes

/**
 * Barra superior del teclado con accesos directos: configuración, emojis,
 * portapapeles y fuentes de texto. El botón de fuentes se tiñe con el color de
 * acento cuando hay un estilo distinto de "Normal" activo.
 */
class KeyboardToolbarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    interface Listener {
        fun onSettings()
        fun onEmoji()
        fun onClipboard()
        fun onFonts()
    }

    var listener: Listener? = null

    private var theme: KeyboardTheme = KeyboardThemes.DARK
    private var fontStyleActive = false
    private val buttons = ArrayList<ImageButton>(4)
    private lateinit var fontButton: ImageButton

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL

        addButton(R.drawable.ic_toolbar_settings, R.string.toolbar_settings_desc) {
            listener?.onSettings()
        }
        addButton(R.drawable.ic_key_emoji, R.string.toolbar_emoji_desc) {
            listener?.onEmoji()
        }
        addButton(R.drawable.ic_key_clipboard, R.string.toolbar_clipboard_desc) {
            listener?.onClipboard()
        }
        fontButton = addButton(R.drawable.ic_toolbar_font, R.string.toolbar_font_desc) {
            listener?.onFonts()
        }
        refreshTints()
    }

    private fun addButton(iconRes: Int, descRes: Int, onClick: () -> Unit): ImageButton {
        val button = ImageButton(context).apply {
            setImageDrawable(AppCompatResources.getDrawable(context, iconRes))
            contentDescription = context.getString(descRes)
            scaleType = ImageView.ScaleType.CENTER
            val out = TypedValue()
            context.theme.resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, out, true
            )
            setBackgroundResource(out.resourceId)
            setOnClickListener { onClick() }
        }
        buttons += button
        addView(button, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        return button
    }

    fun applyTheme(newTheme: KeyboardTheme) {
        theme = newTheme
        setBackgroundColor(newTheme.backgroundColor)
        refreshTints()
    }

    fun setFontStyleActive(active: Boolean) {
        fontStyleActive = active
        if (buttons.size == 4) refreshTints()
    }

    private fun refreshTints() {
        if (!::fontButton.isInitialized) return
        buttons.forEach { it.setColorFilter(theme.specialKeyText) }
        if (fontStyleActive) fontButton.setColorFilter(theme.accentColor)
    }
}
