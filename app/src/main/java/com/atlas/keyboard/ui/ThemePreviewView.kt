package com.atlas.keyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import com.atlas.keyboard.R
import com.atlas.keyboard.theme.KeyboardTheme
import com.atlas.keyboard.theme.KeyboardThemes
import com.atlas.keyboard.theme.mix

/**
 * Vista previa estática del teclado usada en la pantalla de Ajustes. Dibuja
 * una miniatura fiel (fondo, tira de sugerencias, tres filas de teclas con las
 * esquinas redondeadas configuradas) que se re-renderiza en cuanto cambia el
 * tema o cualquier opción de apariencia.
 */
class ThemePreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var theme: KeyboardTheme = KeyboardThemes.DARK

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val tmpRect = RectF()
    private val tmpBounds = Rect()

    /** Actualiza el tema mostrado y redibuja la miniatura. */
    fun setTheme(newTheme: KeyboardTheme) {
        theme = newTheme
        invalidate()
    }

    private fun dp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics
    )

    private fun sp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Fondo del teclado.
        keyPaint.color = theme.backgroundColor
        keyPaint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w, h, keyPaint)

        // Tira de sugerencias (18% superior).
        val stripH = h * 0.18f
        keyPaint.color = mix(theme.backgroundColor, theme.keyBackground, 0.25f)
        canvas.drawRect(0f, 0f, w, stripH, keyPaint)
        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = sp(13f)
        val words = listOf("escribir", "Escribe", "escrito")
        val xs = floatArrayOf(w / 6f, w / 2f, 5f * w / 6f)
        val stripBaseline = stripH / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        words.forEachIndexed { index, word ->
            textPaint.color = if (index == 1) theme.accentColor else theme.suggestionText
            textPaint.isFakeBoldText = index == 1
            canvas.drawText(word, xs[index], stripBaseline, textPaint)
        }
        textPaint.isFakeBoldText = false

        // Filas de teclas simuladas bajo la tira.
        val gap = dp(2f)
        val pad = dp(4f)
        var top = stripH + gap
        val rowsArea = h - top - pad
        val rowH = (rowsArea - gap * 2f) / 3f
        val radius = dp(theme.cornerRadiusDp).coerceAtMost(rowH / 2f)
        textPaint.textSize = theme.keyTextSizeSp.let { sp(it * 0.62f) }

        // Fila 1: 10 letras; Fila 2: 9 letras con sangría de media tecla.
        val row1 = "qwertyuiop"
        val row2 = "asdfghjkl"
        val unitRow1 = (w - pad * 2f + gap) / 10f
        val unitRow2 = (w - pad * 2f + gap) / 10f
        drawKeyRow(canvas, row1.map { it.toString() }, top, rowH, pad, unitRow1, gap, radius, 0f)
        drawKeyRow(canvas, row2.map { it.toString() }, top + rowH + gap, rowH, pad, unitRow2, gap, radius, unitRow2 * 0.5f)

        // Fila 3: ?123 (1.5) + espacio (5) + enter (1.5) en unidades de w/8 con hueco de 2 unidades.
        val row3Top = top + (rowH + gap) * 2f
        val unitRow3 = (w - pad * 2f + gap) / 10f
        val x123 = pad
        val w123 = unitRow3 * 1.5f - gap
        drawKey(canvas, x123, row3Top, w123, rowH, radius, true, "?123")
        val xSpace = x123 + w123 + gap + unitRow3 * 1f
        val wSpace = unitRow3 * 5f - gap
        drawKey(canvas, xSpace, row3Top, wSpace, rowH, radius, false, "")
        val xEnter = xSpace + wSpace + gap + unitRow3 * 1f
        val wEnter = pad + w - gap - xEnter
        drawKey(canvas, xEnter, row3Top, wEnter, rowH, radius, true, "")

        // Icono de enter dentro de la última tecla.
        AppCompatResources.getDrawable(context, R.drawable.ic_key_enter)?.let { icon ->
            val iconSize = (rowH * 0.5f).toInt()
            val cx = (xEnter + wEnter / 2f).toInt()
            val cy = (row3Top + rowH / 2f).toInt()
            icon.setBounds(cx - iconSize / 2, cy - iconSize / 2, cx + iconSize / 2, cy + iconSize / 2)
            icon.setTint(theme.specialKeyText)
            icon.draw(canvas)
        }

        // Barra del espacio (línea centrada).
        keyPaint.color = theme.keyText
        keyPaint.alpha = 110
        val spaceBarW = wSpace * 0.45f
        tmpRect.set(
            xSpace + wSpace / 2f - spaceBarW / 2f,
            row3Top + rowH / 2f - dp(1.5f),
            xSpace + wSpace / 2f + spaceBarW / 2f,
            row3Top + rowH / 2f + dp(1.5f)
        )
        canvas.drawRoundRect(tmpRect, dp(2f), dp(2f), keyPaint)
        keyPaint.alpha = 255
    }

    private fun drawKeyRow(
        canvas: Canvas,
        labels: List<String>,
        top: Float,
        rowH: Float,
        pad: Float,
        unit: Float,
        gap: Float,
        radius: Float,
        offset: Float
    ) {
        labels.forEachIndexed { index, label ->
            val left = pad + offset + index * unit
            drawKey(canvas, left, top, unit - gap, rowH, radius, false, label)
        }
    }

    private fun drawKey(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        radius: Float,
        special: Boolean,
        label: String
    ) {
        if (width <= 0f || height <= 0f) return
        tmpRect.set(left, top, left + width, top + height)
        keyPaint.color = if (special) theme.specialKeyBackground else theme.keyBackground
        keyPaint.style = Paint.Style.FILL
        keyPaint.alpha = 255
        canvas.drawRoundRect(tmpRect, radius, radius, keyPaint)
        if (label.isNotEmpty()) {
            textPaint.color = if (special) theme.specialKeyText else theme.keyText
            val baseline = top + height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            // Ajusta el texto si no entra en la tecla.
            var size = textPaint.textSize
            textPaint.getTextBounds(label, 0, label.length, tmpBounds)
            if (tmpBounds.width() > width - dp(4f) && size > sp(8f)) {
                textPaint.textSize = sp(9f)
            }
            canvas.drawText(label, left + width / 2f, baseline, textPaint)
            textPaint.textSize = size
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredH = dp(170f).toInt()
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val hSize = MeasureSpec.getSize(heightMeasureSpec)
        val height = when (hMode) {
            MeasureSpec.EXACTLY -> hSize
            MeasureSpec.AT_MOST -> minOf(desiredH, hSize)
            else -> desiredH
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }
}
