package com.atlas.keyboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.atlas.keyboard.data.AtlasSettings
import com.atlas.keyboard.data.ClipboardRepository
import com.atlas.keyboard.data.SettingsRepository
import com.atlas.keyboard.keyboard.KeyType
import com.atlas.keyboard.keyboard.KeyboardLayouts
import com.atlas.keyboard.keyboard.KeyboardMode
import com.atlas.keyboard.keyboard.KeyboardView
import com.atlas.keyboard.keyboard.ShiftState
import com.atlas.keyboard.suggestion.SuggestionEngine
import com.atlas.keyboard.theme.KeyboardTheme
import com.atlas.keyboard.theme.KeyboardThemes
import com.atlas.keyboard.theme.customized
import com.atlas.keyboard.ui.ClipboardPanelView
import com.atlas.keyboard.ui.EmojiPanelView
import com.atlas.keyboard.ui.SuggestionStripView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Servicio de método de entrada (IME) de Atlas Keyboard.
 *
 * Gestiona el ciclo de vida del teclado, la comunicación con la app en foco a
 * través de [InputConnection] (texto en composición + confirmación), la barra
 * de sugerencias, la autocorrección, las mayúsculas automáticas, los paneles de
 * emojis y portapapeles, y la aplicación en caliente de las preferencias.
 */
class AtlasInputMethodService : InputMethodService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var settingsRepo: SettingsRepository
    private lateinit var clipboardRepo: ClipboardRepository
    private lateinit var audioManager: AudioManager
    private lateinit var clipboardManager: ClipboardManager

    private val suggestionEngine = SuggestionEngine()

    private var settings = AtlasSettings()
    private var theme: KeyboardTheme = KeyboardThemes.DARK

    // Vista raíz del IME y sus componentes.
    private var rootView: LinearLayout? = null
    private var strip: SuggestionStripView? = null
    private var container: FrameLayout? = null
    private var keyboardView: KeyboardView? = null
    private var emojiPanel: EmojiPanelView? = null
    private var clipboardPanel: ClipboardPanelView? = null

    // Estado de escritura.
    private var mode = KeyboardMode.LETTERS
    private var shiftState = ShiftState.OFF
    private var lastShiftTapAt = 0L
    private val currentWord = StringBuilder()
    private var composingActive = false
    private var textFeaturesAllowed = true   // false en contraseñas/campos no-texto
    private var autoCapsAllowed = true

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        captureClipboardIfAllowed()
    }

    // ------------------------------------------------------------------ //
    // Ciclo de vida
    // ------------------------------------------------------------------ //

    override fun onCreate() {
        super.onCreate()
        settingsRepo = SettingsRepository(this)
        clipboardRepo = ClipboardRepository(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)

        serviceScope.launch(Dispatchers.IO) {
            suggestionEngine.loadLanguage(this@AtlasInputMethodService, "es", R.raw.dictionary_es)
            suggestionEngine.loadLanguage(this@AtlasInputMethodService, "en", R.raw.dictionary_en)
        }

        serviceScope.launch {
            settingsRepo.settingsFlow.collect { newSettings ->
                settings = newSettings
                theme = KeyboardThemes.byId(newSettings.themeId).customized(
                    background = newSettings.customBackground,
                    keyColor = newSettings.customKeyColor,
                    textColor = newSettings.customTextColor,
                    radiusDp = newSettings.customRadiusDp,
                    textSizeSp = newSettings.customTextSizeSp
                )
                suggestionEngine.setLanguage(newSettings.language)
                applySettingsToViews()
            }
        }
    }

    override fun onDestroy() {
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val stripView = SuggestionStripView(this)
        val containerView = FrameLayout(this)
        val keyboard = KeyboardView(this)

        stripView.listener = suggestionStripListener
        keyboard.listener = keyboardListener

        containerView.addView(
            keyboard,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(
            stripView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(SUGGESTION_STRIP_HEIGHT_DP)
            )
        )
        root.addView(
            containerView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, containerHeightPx())
        )

        rootView = root
        strip = stripView
        container = containerView
        keyboardView = keyboard

        applySettingsToViews()
        return root
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        textFeaturesAllowed = computeTextFeaturesAllowed(info)
        autoCapsAllowed = computeAutoCapsAllowed(info)
        currentWord.setLength(0)
        composingActive = false
        showKeyboard(KeyboardMode.LETTERS)
        syncCurrentWordFromCursor()
        evaluateAutoCaps()
        updateSuggestions()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        keyboardView?.cancelOngoingTouch()
        currentWord.setLength(0)
        composingActive = false
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd
        )
        // Re-sincroniza la palabra bajo el cursor. Si el texto extraído del
        // campo sigue coincidiendo con la composición activa, no se toca nada.
        syncCurrentWordFromCursor()
        updateSuggestions()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applySettingsToViews()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    // ------------------------------------------------------------------ //
    // Construcción y aplicación de ajustes
    // ------------------------------------------------------------------ //

    /** Reaplica tema, altura, layout y visibilidad de la franja de sugerencias. */
    private fun applySettingsToViews() {
        val stripView = strip ?: return
        val containerView = container ?: return
        val keyboard = keyboardView ?: return

        stripView.applyTheme(theme)
        stripView.layoutParams = stripView.layoutParams?.apply {
            height = dpToPx(SUGGESTION_STRIP_HEIGHT_DP)
        }
        containerView.layoutParams = containerView.layoutParams?.apply {
            height = containerHeightPx()
        }

        keyboard.setTheme(theme)
        emojiPanel?.applyTheme(theme)
        clipboardPanel?.applyTheme(theme)
        rebuildKeyboard()
        updateStripVisibility()
        updateSuggestions()
        tintSystemBars()
        rootView?.requestLayout()
    }

    private fun rebuildKeyboard() {
        val keyboard = keyboardView ?: return
        val layout = when (mode) {
            KeyboardMode.SYMBOLS_1 -> KeyboardLayouts.symbols(1, settings.language)
            KeyboardMode.SYMBOLS_2 -> KeyboardLayouts.symbols(2, settings.language)
            else -> KeyboardLayouts.letters(settings.language, shiftState, settings.showNumberRow)
        }
        keyboard.setKeyboard(layout)
    }

    private fun showKeyboard(targetMode: KeyboardMode) {
        mode = when (targetMode) {
            KeyboardMode.SYMBOLS_1, KeyboardMode.SYMBOLS_2 -> targetMode
            else -> KeyboardMode.LETTERS
        }
        keyboardView?.visibility = View.VISIBLE
        emojiPanel?.visibility = View.GONE
        clipboardPanel?.visibility = View.GONE
        updateStripVisibility()
        rebuildKeyboard()
        updateSuggestions()
    }

    private fun showPanel(panelMode: KeyboardMode) {
        val containerView = container ?: return
        mode = panelMode
        when (panelMode) {
            KeyboardMode.EMOJI -> {
                val panel = emojiPanel ?: EmojiPanelView(this).also { panel ->
                    panel.listener = emojiPanelListener
                    panel.applyTheme(theme)
                    panel.visibility = View.GONE
                    containerView.addView(
                        panel,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    )
                    emojiPanel = panel
                }
                panel.visibility = View.VISIBLE
            }
            KeyboardMode.CLIPBOARD -> {
                val panel = clipboardPanel ?: ClipboardPanelView(this).also { panel ->
                    panel.listener = clipboardPanelListener
                    panel.applyTheme(theme)
                    panel.visibility = View.GONE
                    containerView.addView(
                        panel,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    )
                    clipboardPanel = panel
                }
                panel.visibility = View.VISIBLE
                panel.showItems(clipboardRepo.items())
            }
            else -> Unit
        }
        keyboardView?.visibility = View.GONE
        val other = if (panelMode == KeyboardMode.EMOJI) clipboardPanel else emojiPanel
        other?.visibility = View.GONE
        strip?.visibility = View.GONE
    }

    private fun updateStripVisibility() {
        val visible = settings.suggestionsEnabled && textFeaturesAllowed
        strip?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun containerHeightPx(): Int {
        val base = settings.keyboardHeightDp
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val effective = if (landscape) minOf(base, 210) else base
        return dpToPx(effective)
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    private fun tintSystemBars() {
        try {
            val window = window?.window ?: return
            @Suppress("DEPRECATION")
            window.navigationBarColor = theme.backgroundColor
            @Suppress("DEPRECATION")
            window.statusBarColor = theme.backgroundColor
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------------ //
    // EditorInfo: qué funciones están permitidas en el campo actual
    // ------------------------------------------------------------------ //

    private fun computeTextFeaturesAllowed(info: EditorInfo): Boolean {
        val variation = info.inputType and EditorInfo.TYPE_MASK_VARIATION
        val classType = info.inputType and EditorInfo.TYPE_MASK_CLASS
        if (info.inputType == EditorInfo.TYPE_NULL || classType != EditorInfo.TYPE_CLASS_TEXT) {
            return false
        }
        if (variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD
        ) return false
        return info.inputType and EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS == 0
    }

    private fun computeAutoCapsAllowed(info: EditorInfo): Boolean {
        if (!textFeaturesAllowed) return false
        val variation = info.inputType and EditorInfo.TYPE_MASK_VARIATION
        return variation != EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS &&
                variation != EditorInfo.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS &&
                variation != EditorInfo.TYPE_TEXT_VARIATION_URI &&
                info.inputType and EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE == 0
    }

    // ------------------------------------------------------------------ //
    // Escritura: palabra en composición, sugerencias y autocorrección
    // ------------------------------------------------------------------ //

    private fun syncCurrentWordFromCursor() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(48, 0)
        val extracted = extractTrailingWord(before)
        // La composición activa sigue siendo válida si el campo contiene la
        // misma palabra que creemos estar componiendo.
        val matchesComposing = composingActive && extracted == currentWord.toString()
        if (!matchesComposing) {
            currentWord.setLength(0)
            currentWord.append(extracted)
            composingActive = false
        }
    }

    private fun extractTrailingWord(before: CharSequence?): String {
        if (before.isNullOrEmpty()) return ""
        var i = before.length - 1
        while (i >= 0 && before[i].isLetter()) i--
        return before.subSequence(i + 1, before.length).toString()
    }

    private fun updateSuggestions() {
        val stripView = strip ?: return
        if (!settings.suggestionsEnabled || !textFeaturesAllowed ||
            (mode != KeyboardMode.LETTERS && mode != KeyboardMode.SYMBOLS_1 &&
                    mode != KeyboardMode.SYMBOLS_2)
        ) {
            stripView.updateSuggestions(emptyList())
            return
        }
        val word = currentWord.toString()
        if (word.isEmpty()) {
            stripView.updateSuggestions(emptyList())
            return
        }
        val correction = if (word.length >= 3) suggestionEngine.bestCorrection(word) else null
        val completions = suggestionEngine.suggest(word, 3)
            .filter { it != correction }
            .map { capitalizeLike(word, it) }
        val slots = buildList {
            add(word)
            if (correction != null) add(capitalizeLike(word, correction))
            addAll(completions)
        }.distinct().take(3)
        stripView.updateSuggestions(
            slots,
            highlight = if (correction != null && slots.size > 1) 1 else -1
        )
    }

    private fun capitalizeLike(pattern: String, candidate: String): String {
        return if (pattern.isNotEmpty() && pattern.first().isUpperCase()) {
            candidate.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        } else candidate
    }

    /**
     * Confirma la palabra en composición. Con autocorrección activa y una
     * corrección disponible, sustituye la palabra escrita por la corregida.
     */
    private fun commitCurrentWord(ic: InputConnection, applyAutoCorrect: Boolean) {
        if (currentWord.isEmpty()) return
        val typed = currentWord.toString()
        val correction = if (applyAutoCorrect && settings.autoCorrect && textFeaturesAllowed) {
            suggestionEngine.bestCorrection(typed)
        } else null
        val finalText = capitalizeLike(typed, correction ?: typed)

        when {
            composingActive -> ic.commitText(finalText, 1)
            finalText != typed -> {
                ic.deleteSurroundingText(typed.length, 0)
                ic.commitText(finalText, 1)
            }
        }
        currentWord.setLength(0)
        composingActive = false
    }

    private fun handleSpace(ic: InputConnection) {
        commitCurrentWord(ic, applyAutoCorrect = true)
        ic.commitText(" ", 1)
        evaluateAutoCaps()
        updateSuggestions()
    }

    private fun evaluateAutoCaps() {
        if (!settings.autoCaps || !autoCapsAllowed) return
        if (mode != KeyboardMode.LETTERS) return
        val before = currentInputConnection?.getTextBeforeCursor(4, 0) ?: ""
        var i = before.length - 1
        while (i >= 0 && before[i] == ' ') i--
        val shouldCaps = i < 0 || before[i] == '.' || before[i] == '!' ||
                before[i] == '?' || before[i] == '\n'
        val target = if (shouldCaps) ShiftState.ON else ShiftState.OFF
        if (target != shiftState) {
            shiftState = target
            rebuildKeyboard()
        }
    }

    // ------------------------------------------------------------------ //
    // Listener del teclado
    // ------------------------------------------------------------------ //

    private val keyboardListener = object : KeyboardView.Listener {

        override fun onTextCommit(text: String) {
            val ic = currentInputConnection ?: return
            when {
                text == " " -> handleSpace(ic)
                text.all { it.isLetter() } -> {
                    if (!textFeaturesAllowed) {
                        // Campos de contraseña o no texto: entrada directa.
                        ic.commitText(text, 1)
                    } else {
                        currentWord.append(text)
                        ic.setComposingText(currentWord.toString(), 1)
                        composingActive = true
                        updateSuggestions()
                    }
                    if (shiftState == ShiftState.ON) {
                        shiftState = ShiftState.OFF
                        rebuildKeyboard()
                    }
                }
                else -> {
                    // Símbolos, números y puntuación: cierra la palabra abierta.
                    commitCurrentWord(ic, applyAutoCorrect = text == "." || text == "!" || text == "?")
                    ic.commitText(text, 1)
                    if (text == "." || text == "!" || text == "?") evaluateAutoCaps()
                    updateSuggestions()
                }
            }
        }

        override fun onDelete() {
            val ic = currentInputConnection ?: return
            when {
                composingActive && currentWord.isNotEmpty() -> {
                    currentWord.setLength(currentWord.length - 1)
                    if (currentWord.isEmpty()) {
                        ic.commitText("", 1)
                        composingActive = false
                    } else {
                        ic.setComposingText(currentWord.toString(), 1)
                    }
                    updateSuggestions()
                }
                currentWord.isNotEmpty() -> {
                    // Palabra re-sincronizada desde el campo (sin composición activa).
                    ic.deleteSurroundingText(1, 0)
                    currentWord.setLength(currentWord.length - 1)
                    updateSuggestions()
                }
                else -> {
                    val selected = ic.getSelectedText(0)
                    if (selected.isNullOrEmpty()) {
                        ic.deleteSurroundingText(1, 0)
                    } else {
                        ic.commitText("", 1)
                    }
                    syncCurrentWordFromCursor()
                    updateSuggestions()
                }
            }
        }

        override fun onEnter() {
            val ic = currentInputConnection ?: return
            commitCurrentWord(ic, applyAutoCorrect = false)
            val info = currentInputEditorInfo
            val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
                ?: EditorInfo.IME_ACTION_NONE
            val noEnterAction = (info?.imeOptions ?: 0) and
                    EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
            if (action != EditorInfo.IME_ACTION_NONE && !noEnterAction) {
                ic.performEditorAction(action)
            } else {
                ic.commitText("\n", 1)
            }
            evaluateAutoCaps()
            updateSuggestions()
        }

        override fun onShiftTapped() {
            val now = SystemClock.uptimeMillis()
            shiftState = when (shiftState) {
                ShiftState.OFF -> ShiftState.ON
                ShiftState.ON -> if (now - lastShiftTapAt < 450L) ShiftState.LOCKED else ShiftState.OFF
                ShiftState.LOCKED -> ShiftState.OFF
            }
            lastShiftTapAt = now
            rebuildKeyboard()
        }

        override fun onShiftLongPressed() {
            shiftState = ShiftState.LOCKED
            rebuildKeyboard()
        }

        override fun onModeToggle() {
            showKeyboard(if (mode == KeyboardMode.LETTERS) KeyboardMode.SYMBOLS_1 else KeyboardMode.LETTERS)
        }

        override fun onSymbolsPageToggle() {
            showKeyboard(if (mode == KeyboardMode.SYMBOLS_1) KeyboardMode.SYMBOLS_2 else KeyboardMode.SYMBOLS_1)
        }

        override fun onEmojiRequested() = showPanel(KeyboardMode.EMOJI)

        override fun onClipboardRequested() = showPanel(KeyboardMode.CLIPBOARD)

        override fun onKeyFeedback(type: KeyType) {
            if (settings.soundEnabled) {
                val effect = when (type) {
                    KeyType.DELETE -> AudioManager.FX_KEYPRESS_DELETE
                    KeyType.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                    KeyType.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                    else -> AudioManager.FX_KEYPRESS_STANDARD
                }
                audioManager.playSoundEffect(effect)
            }
            if (settings.vibrationEnabled && settings.vibrationStrength > 0) {
                performHaptic()
            }
        }
    }

    // ------------------------------------------------------------------ //
    // Franja de sugerencias
    // ------------------------------------------------------------------ //

    private val suggestionStripListener = object : SuggestionStripView.Listener {
        override fun onSuggestionPicked(word: String) {
            val ic = currentInputConnection ?: return
            when {
                composingActive -> ic.commitText("$word ", 1)
                currentWord.isNotEmpty() -> {
                    ic.deleteSurroundingText(currentWord.length, 0)
                    ic.commitText("$word ", 1)
                }
                else -> ic.commitText("$word ", 1)
            }
            currentWord.setLength(0)
            composingActive = false
            evaluateAutoCaps()
            updateSuggestions()
        }
    }

    // ------------------------------------------------------------------ //
    // Paneles de emojis y portapapeles
    // ------------------------------------------------------------------ //

    private val emojiPanelListener = object : EmojiPanelView.Listener {
        override fun onEmojiSelected(emoji: String) {
            val ic = currentInputConnection ?: return
            ic.finishComposingText()
            currentWord.setLength(0)
            composingActive = false
            ic.commitText(emoji, 1)
            updateSuggestions()
        }

        override fun onBackToKeyboard() = showKeyboard(KeyboardMode.LETTERS)
    }

    private val clipboardPanelListener = object : ClipboardPanelView.Listener {
        override fun onClipSelected(text: String) {
            val ic = currentInputConnection ?: return
            ic.finishComposingText()
            currentWord.setLength(0)
            composingActive = false
            ic.commitText(text, 1)
            syncCurrentWordFromCursor()
            updateSuggestions()
        }

        override fun onDeleteClip(text: String) {
            clipboardRepo.remove(text)
            clipboardPanel?.showItems(clipboardRepo.items())
        }

        override fun onClearAll() {
            clipboardRepo.clear()
            clipboardPanel?.showItems(emptyList())
        }

        override fun onBackToKeyboard() = showKeyboard(KeyboardMode.LETTERS)
    }

    // ------------------------------------------------------------------ //
    // Portapapeles del sistema
    // ------------------------------------------------------------------ //

    private fun captureClipboardIfAllowed() {
        try {
            val clip = clipboardManager.primaryClip ?: return
            if (clip.itemCount == 0) return
            // Respeta contenido marcado como sensible (Android 13+, p. ej. contraseñas).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val extras = clip.description?.extras
                if (extras != null && extras.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false)) {
                    return
                }
            }
            val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
            clipboardRepo.add(text)
            clipboardPanel?.showItems(clipboardRepo.items())
        } catch (_: Exception) {
            // Nunca bloqueamos el teclado por una lectura del portapapeles.
        }
    }

    // ------------------------------------------------------------------ //
    // Vibración
    // ------------------------------------------------------------------ //

    private fun performHaptic() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val amplitude = (settings.vibrationStrength * 255 / 100).coerceIn(20, 255)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(18L, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(18L)
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val SUGGESTION_STRIP_HEIGHT_DP = 44
    }
}
