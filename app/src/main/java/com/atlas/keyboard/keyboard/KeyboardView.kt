package com.atlas.keyboard.keyboard

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import com.atlas.keyboard.theme.KeyboardTheme
import com.atlas.keyboard.theme.KeyboardThemes
import com.atlas.keyboard.theme.readableTextOn
import kotlin.math.floor
import kotlin.math.min

/**
 * Vista personalizada que dibuja y gestiona todo el teclado:
 * layout por pesos, animación de pulsación, vista previa del carácter,
 * popup de alternativas al mantener pulsado y repetición de la tecla borrar.
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onTextCommit(text: String)
        fun onDelete()
        fun onEnter()
        fun onShiftTapped()
        fun onShiftLongPressed()
        fun onModeToggle()
        fun onSymbolsPageToggle()
        fun onEmojiRequested()
        fun onClipboardRequested()
        /** Llamado en el instante de pulsar una tecla (para vibración/sonido). */
        fun onKeyFeedback(type: KeyType)
    }

    var listener: Listener? = null

    private val density = resources.displayMetrics.density

    // ------------------------------------------------------------------ //
    // Estado del modelo
    // ------------------------------------------------------------------ //
    private var keyboard: Keyboard = Keyboard(emptyList())
    private var theme: KeyboardTheme = KeyboardThemes.DARK

    private class KeyRect(val key: Key, val rect: RectF)

    private val keyRects = ArrayList<KeyRect>()
    private var layoutDirty = true

    // ------------------------------------------------------------------ //
    // Pintura
    // ------------------------------------------------------------------ //
    private val backgroundPaint = Paint()
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val popupPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val popupHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ------------------------------------------------------------------ //
    // Estado táctil
    // ------------------------------------------------------------------ //
    private var pressedIndex = -1
    private var touchActive = false
    private var longPressFired = false
    private val pressProgress = HashMap<Int, Float>()
    private val animators = HashMap<Int, ValueAnimator>()
    private val handler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var repeatRunnable: Runnable? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // Popup de alternativas
    private var popupActive = false
    private var popupCandidates: List<String> = emptyList()
    private var popupSelected = -1
    private val popupBounds = RectF()
    private var popupCellWidth = 0f
    private var popupCellCount = 0

    // ------------------------------------------------------------------ //
    // API pública
    // ------------------------------------------------------------------ //

    fun setKeyboard(keyboard: Keyboard) {
        this.keyboard = keyboard
        cancelOngoingTouch()
        layoutDirty = true
        requestLayout()
        invalidate()
    }

    fun setTheme(theme: KeyboardTheme) {
        this.theme = theme
        backgroundPaint.color = theme.backgroundColor
        popupHighlightPaint.color = theme.accentColor
        invalidate()
    }

    /** Cancela pulsaciones pendientes (p. ej. al ocultarse el teclado). */
    fun cancelOngoingTouch() {
        removeTouchCallbacks()
        touchActive = false
        pressedIndex = -1
        dismissPopup()
        if (pressProgress.isNotEmpty() || animators.isNotEmpty()) {
            // Copia antes de cancelar: los listeners de cancelación mutan el mapa.
            val running = animators.values.toList()
            animators.clear()
            pressProgress.clear()
            running.forEach { it.cancel() }
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelOngoingTouch()
        handler.removeCallbacksAndMessages(null)
    }

    // ------------------------------------------------------------------ //
    // Medida y layout
    // ------------------------------------------------------------------ //

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutDirty = true
    }

    private fun layoutKeys(w: Int, h: Int) {
        keyRects.clear()
        val rows = keyboard.rows
        if (rows.isEmpty() || w <= 0 || h <= 0) return
        val unit = w / 10f
        val rowHeight = h / rows.size.toFloat()
        rows.forEachIndexed { r, row ->
            val totalWeight = row.keys.sumOf { it.weight.toDouble() }.toFloat()
            if (totalWeight <= 0f) return@forEachIndexed
            val available = w - unit * (row.leadingInset + row.trailingInset)
            var x = unit * row.leadingInset
            val y = r * rowHeight
            for (key in row.keys) {
                val keyWidth = available * (key.weight / totalWeight)
                keyRects += KeyRect(key, RectF(x, y, x + keyWidth, y + rowHeight))
                x += keyWidth
            }
        }
    }

    // ------------------------------------------------------------------ //
    // Dibujo
    // ------------------------------------------------------------------ //

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (layoutDirty) {
            layoutKeys(width, height)
            layoutDirty = false
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val horizontalGap = 1.5f * density
        val topGap = 2.5f * density
        val bottomGap = 1.5f * density
        val radius = theme.cornerRadiusDp * density

        for (i in keyRects.indices) {
            val kr = keyRects[i]
            val left = kr.rect.left + horizontalGap
            val right = kr.rect.right - horizontalGap
            val top = kr.rect.top + topGap
            val bottom = kr.rect.bottom - bottomGap
            val key = kr.key

            // Fondo de la tecla (normales, especiales y resaltadas).
            val textColor: Int
            when {
                key.highlighted -> {
                    keyPaint.color = theme.accentColor
                    textColor = readableTextOn(theme.accentColor)
                }
                isSpecial(key.type) -> {
                    keyPaint.color = theme.specialKeyBackground
                    textColor = theme.specialKeyText
                }
                else -> {
                    keyPaint.color = theme.keyBackground
                    textColor = theme.keyText
                }
            }
            canvas.drawRoundRect(left, top, right, bottom, radius, radius, keyPaint)

            // Superposición animada del estado pulsado.
            val progress = pressProgress[i] ?: 0f
            if (progress > 0f) {
                overlayPaint.color = theme.pressedOverlay
                overlayPaint.alpha = (Color.alpha(theme.pressedOverlay) * progress).toInt()
                canvas.drawRoundRect(left, top, right, bottom, radius, radius, overlayPaint)
            }

            // Etiqueta: tamaño grande para glifos simples, pequeño para textos.
            val singleGlyph = key.label.codePointCount(0, key.label.length) <= 1
            textPaint.textSize = (if (singleGlyph) theme.keyTextSizeSp else theme.specialKeyTextSizeSp) * density
            textPaint.color = textColor
            val cx = (left + right) / 2f
            val cy = (top + bottom) / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(key.label, cx, cy, textPaint)
        }

        // Vista previa del carácter encima de la tecla pulsada.
        if (!popupActive && pressedIndex in keyRects.indices) {
            val kr = keyRects[pressedIndex]
            if (kr.key.type == KeyType.CHARACTER) drawPreviewBubble(canvas, kr, radius)
        }
        if (popupActive) drawPopup(canvas, radius)
    }

    private fun isSpecial(type: KeyType): Boolean = when (type) {
        KeyType.CHARACTER, KeyType.SPACE -> false
        else -> true
    }

    private fun drawPreviewBubble(canvas: Canvas, kr: KeyRect, radius: Float) {
        val bubbleWidth = kr.rect.width() * 1.15f
        val bubbleHeight = kr.rect.height() * 1.5f
        var left = kr.rect.centerX() - bubbleWidth / 2f
        left = left.coerceIn(2f * density, width - bubbleWidth - 2f * density)
        var top = kr.rect.top - bubbleHeight - 8f * density
        if (top < 0) top = kr.rect.bottom + 8f * density
        val rect = RectF(left, top, left + bubbleWidth, top + bubbleHeight)

        popupPaint.color = theme.popupBackground
        canvas.drawRoundRect(rect, radius * 1.5f, radius * 1.5f, popupPaint)

        textPaint.textSize = theme.keyTextSizeSp * density * 1.4f
        textPaint.color = theme.popupText
        val cy = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(kr.key.label, rect.centerX(), cy, textPaint)
    }

    private fun drawPopup(canvas: Canvas, radius: Float) {
        popupPaint.color = theme.popupBackground
        canvas.drawRoundRect(popupBounds, radius * 1.5f, radius * 1.5f, popupPaint)

        textPaint.textSize = theme.keyTextSizeSp * density * 1.15f
        textPaint.color = theme.popupText
        for (i in 0 until popupCellCount) {
            val cellLeft = popupBounds.left + i * popupCellWidth
            val cellRight = cellLeft + popupCellWidth
            if (i == popupSelected) {
                canvas.drawRoundRect(
                    cellLeft + 2f * density,
                    popupBounds.top + 2f * density,
                    cellRight - 2f * density,
                    popupBounds.bottom - 2f * density,
                    radius, radius, popupHighlightPaint
                )
                textPaint.color = readableTextOn(theme.accentColor)
            } else {
                textPaint.color = theme.popupText
            }
            val cy = popupBounds.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(popupCandidates[i], (cellLeft + cellRight) / 2f, cy, textPaint)
        }
    }

    // ------------------------------------------------------------------ //
    // Gestión táctil
    // ------------------------------------------------------------------ //

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                onPressStarted(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                onPointerMoved(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP -> {
                onPressEnded()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelOngoingTouch()
                return true
            }
            // Punteros adicionales: los ignoramos (teclado monopuntero).
            else -> return true
        }
    }

    private fun onPressStarted(x: Float, y: Float) {
        if (touchActive) return
        val index = findKeyIndex(x, y)
        if (index < 0) return

        touchActive = true
        longPressFired = false
        pressedIndex = index
        animateProgress(index, 1f)

        val key = keyRects[index].key
        listener?.onKeyFeedback(key.type)

        when (key.type) {
            KeyType.DELETE -> {
                listener?.onDelete()
                val repeat = object : Runnable {
                    override fun run() {
                        listener?.onDelete()
                        handler.postDelayed(this, 50L)
                    }
                }
                repeatRunnable = repeat
                handler.postDelayed(repeat, 400L)
            }
            KeyType.SHIFT -> {
                scheduleLongPress {
                    longPressFired = true
                    listener?.onShiftLongPressed()
                }
            }
            else -> {
                if (key.longPress.isNotEmpty()) {
                    scheduleLongPress {
                        longPressFired = true
                        showPopup(key.longPress, keyRects[index].rect)
                    }
                }
            }
        }
    }

    private fun onPointerMoved(x: Float, y: Float) {
        if (!touchActive) return
        if (popupActive) {
            updatePopupSelection(x)
            return
        }
        val index = pressedIndex
        if (index >= 0 && index < keyRects.size) {
            val rect = keyRects[index].rect
            val out = x < rect.left - touchSlop || x > rect.right + touchSlop ||
                    y < rect.top - touchSlop || y > rect.bottom + touchSlop
            if (out) {
                // El dedo se salió de la tecla: cancela la pulsación.
                removeTouchCallbacks()
                val released = pressedIndex
                pressedIndex = -1
                if (released >= 0) animateProgress(released, 0f)
            }
        }
    }

    private fun onPressEnded() {
        removeTouchCallbacks()
        val index = pressedIndex
        touchActive = false

        if (popupActive) {
            val candidate = popupCandidates.getOrNull(popupSelected)
            dismissPopup()
            candidate?.let { listener?.onTextCommit(it) }
            if (index >= 0) animateProgress(index, 0f)
            pressedIndex = -1
            return
        }

        pressedIndex = -1
        if (index < 0 || index >= keyRects.size) return
        if (!longPressFired) {
            dispatchKey(keyRects[index].key)
        }
        animateProgress(index, 0f)
    }

    private fun dispatchKey(key: Key) {
        when (key.type) {
            KeyType.CHARACTER -> listener?.onTextCommit(key.output)
            KeyType.SPACE -> listener?.onTextCommit(" ")
            KeyType.ENTER -> listener?.onEnter()
            KeyType.SHIFT -> listener?.onShiftTapped()
            KeyType.MODE_SYMBOLS -> listener?.onModeToggle()
            KeyType.SYMBOLS_PAGE -> listener?.onSymbolsPageToggle()
            KeyType.EMOJI -> listener?.onEmojiRequested()
            KeyType.CLIPBOARD -> listener?.onClipboardRequested()
            KeyType.DELETE -> Unit // ya gestionado en DOWN con repetición
        }
    }

    private fun findKeyIndex(x: Float, y: Float): Int {
        for (i in keyRects.indices) {
            if (keyRects[i].rect.contains(x, y)) return i
        }
        return -1
    }

    private fun scheduleLongPress(action: () -> Unit) {
        val runnable = Runnable { action() }
        longPressRunnable = runnable
        handler.postDelayed(runnable, 350L)
    }

    private fun removeTouchCallbacks() {
        longPressRunnable?.let { handler.removeCallbacks(it) }
        repeatRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable = null
        repeatRunnable = null
    }

    // ------------------------------------------------------------------ //
    // Popup de alternativas (pulsación prolongada)
    // ------------------------------------------------------------------ //

    private fun showPopup(candidates: List<String>, anchor: RectF) {
        if (candidates.isEmpty()) return
        popupCandidates = candidates
        popupSelected = 0
        popupCellCount = candidates.size
        val margin = 4f * density
        val popupHeight = 48f * density
        popupCellWidth = min(38f * density, (width - 2 * margin) / popupCellCount)
        val popupWidth = popupCellWidth * popupCellCount

        var left = anchor.centerX() - popupWidth / 2f
        left = left.coerceIn(margin, width - popupWidth - margin)
        var top = anchor.top - popupHeight - 6f * density
        if (top < 0) top = anchor.bottom + 6f * density

        popupBounds.set(left, top, left + popupWidth, top + popupHeight)
        popupActive = true
        invalidate()
    }

    private fun updatePopupSelection(x: Float) {
        val index = floor((x - popupBounds.left) / popupCellWidth).toInt()
            .coerceIn(0, popupCellCount - 1)
        if (index != popupSelected) {
            popupSelected = index
            invalidate()
        }
    }

    private fun dismissPopup() {
        if (popupActive) {
            popupActive = false
            popupCandidates = emptyList()
            popupSelected = -1
            popupCellCount = 0
            invalidate()
        }
    }

    // ------------------------------------------------------------------ //
    // Animación de pulsación
    // ------------------------------------------------------------------ //

    private fun animateProgress(index: Int, target: Float) {
        animators[index]?.cancel()
        val start = pressProgress[index] ?: 0f
        if (start == target) return
        val animator = ValueAnimator.ofFloat(start, target).apply {
            duration = if (target > start) 70L else 130L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                pressProgress[index] = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (target == 0f) pressProgress.remove(index)
                    animators.remove(index)
                }
                override fun onAnimationCancel(animation: Animator) {
                    if (target == 0f) pressProgress.remove(index)
                    animators.remove(index)
                }
            })
        }
        animators[index] = animator
        animator.start()
    }
}
