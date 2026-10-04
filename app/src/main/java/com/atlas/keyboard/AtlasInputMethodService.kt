package com.atlas.keyboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.view.inputmethod.InputMethodManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.atlas.keyboard.data.AtlasSettings
import com.atlas.keyboard.data.ClipboardRepository
import com.atlas.keyboard.data.SettingsRepository
import com.atlas.keyboard.data.UserWordStore
import com.atlas.keyboard.keyboard.KeyType
import com.atlas.keyboard.keyboard.KeyboardLayouts
import com.atlas.keyboard.keyboard.KeyboardMode
import com.atlas.keyboard.keyboard.KeyboardView
import com.atlas.keyboard.keyboard.ShiftState
import com.atlas.keyboard.suggestion.SuggestionEngine
import com.atlas.keyboard.textstyle.TextStyles
import com.atlas.keyboard.theme.KeyboardTheme
import com.atlas.keyboard.theme.KeyboardThemes
import com.atlas.keyboard.theme.customized
import com.atlas.keyboard.ui.ClipboardPanelView
import com.atlas.keyboard.ui.EmojiPanelView
import com.atlas.keyboard.ui.KeyboardToolbarView
import com.atlas.keyboard.ui.SettingsActivity
import com.atlas.keyboard.ui.SuggestionStripView
import com.atlas.keyboard.ui.TextStylePanelView
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
    private val userWordStore by lazy { UserWordStore(this) }

    private var settings = AtlasSettings()
    private var theme: KeyboardTheme = KeyboardThemes.DARK

    // Vista raíz del IME y sus componentes.
    private var rootView: LinearLayout? = null
    private var toolbar: KeyboardToolbarView? = null
    private var strip: SuggestionStripView? = null
    private var container: FrameLayout? = null
    private var keyboardView: KeyboardView? = null
    private var emojiPanel: EmojiPanelView? = null
    private var clipboardPanel: ClipboardPanelView? = null
    private var textStylePanel: TextStylePanelView? = null
    private var activeTextStyle: TextStyles.Style = TextStyles.NORMAL

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
            suggestionEngine.setUserWords(userWordStore.load())
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
                activeTextStyle = TextStyles.byId(newSettings.textStyleId)
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
        val toolbarView = KeyboardToolbarView(this)
        val stripView = SuggestionStripView(this)
        val containerView = FrameLayout(this)
        val keyboard = KeyboardView(this)

        toolbarView.listener = toolbarListener
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
            toolbarView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(TOOLBAR_HEIGHT_DP)
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
        toolbar = toolbarView
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

        toolbar?.applyTheme(theme)
        toolbar?.setFontStyleActive(activeTextStyle != TextStyles.NORMAL)
        stripView.applyTheme(theme)
        stripView.layoutParams = stripView.layoutParams?.apply {
            height = dpToPx(SUGGESTION_STRIP_HEIGHT_DP)
        }
        containerView.layoutParams = containerView.layoutParams?.apply {
            height = containerHeightPx()
        }

        keyboard.setTheme(theme)
        keyboard.glideEnabled = settings.glideTyping
        emojiPanel?.applyTheme(theme)
        clipboardPanel?.applyTheme(theme)
        textStylePanel?.applyTheme(theme)
        textStylePanel?.setActiveStyle(activeTextStyle.id)
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
        textStylePanel?.visibility = View.GONE
        updateStripVisibility()
        rebuildKeyboard()
        updateSuggestions()
    }

    private fun showPanel(panelMode: KeyboardMode) {
        val containerView = container ?: return
        mode = panelMode
        val panel: View = when (panelMode) {
            KeyboardMode.EMOJI -> emojiPanel ?: EmojiPanelView(this).also { newPanel ->
                newPanel.listener = emojiPanelListener
                newPanel.applyTheme(theme)
                newPanel.visibility = View.GONE
                containerView.addView(
                    newPanel,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                emojiPanel = newPanel
            }
            KeyboardMode.CLIPBOARD -> clipboardPanel ?: ClipboardPanelView(this).also { newPanel ->
                newPanel.listener = clipboardPanelListener
                newPanel.applyTheme(theme)
                newPanel.visibility = View.GONE
                containerView.addView(
                    newPanel,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                clipboardPanel = newPanel
            }
            KeyboardMode.TEXT_STYLES -> textStylePanel
                ?: TextStylePanelView(this).also { newPanel ->
                    newPanel.listener = textStylePanelListener
                    newPanel.applyTheme(theme)
                    newPanel.visibility = View.GONE
                    containerView.addView(
                        newPanel,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    )
                    textStylePanel = newPanel
                }
            else -> return
        }
        panel.visibility = View.VISIBLE
        when (panel) {
            clipboardPanel -> (panel as? ClipboardPanelView)?.showItems(clipboardRepo.items())
            textStylePanel -> (panel as? TextStylePanelView)?.setActiveStyle(activeTextStyle.id)
            emojiPanel -> (panel as? EmojiPanelView)?.refreshRecents()
        }
        keyboardView?.visibility = View.GONE
        listOfNotNull<View>(emojiPanel, clipboardPanel, textStylePanel)
            .filter { it !== panel }
            .forEach { it.visibility = View.GONE }
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
        var i = before.length
        while (i > 0) {
            val cp = Character.codePointBefore(before, i)
            if (!Character.isLetter(cp)) break
            i -= Character.charCount(cp)
        }
        return before.subSequence(i, before.length).toString()
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
        // Las sugerencias se muestran con la capitalización y la fuente activa.
        fun display(plain: String): String =
            activeTextStyle.transform(capitalizeLike(word, plain))
        val completions = suggestionEngine.suggest(word, 3)
            .filter { it != correction }
            .map(::display)
        val slots = buildList {
            add(word)
            if (correction != null) add(display(correction))
            addAll(completions)
        }.distinct().take(3)
        stripView.updateSuggestions(
            slots,
            highlight = if (correction != null && slots.size > 1) 1 else -1
        )
    }

    private fun capitalizeLike(pattern: String, candidate: String): String {
        if (pattern.isEmpty()) return candidate
        // También detecta mayúsculas estilizadas (puntos de código > BMP).
        return if (Character.isUpperCase(pattern.codePointAt(0))) {
            candidate.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        } else candidate
    }

    /** true si el texto está formado únicamente por letras (incl. estilizadas). */
    private fun isAllLetters(text: String): Boolean {
        if (text.isEmpty()) return false
        var i = 0
        while (i < text.length) {
            if (!Character.isLetter(text.codePointAt(i))) return false
            i += Character.charCount(text.codePointAt(i))
        }
        return true
    }

    /** Unidades UTF-16 del último punto de código antes del cursor. */
    private fun unitsOfLastCodePoint(ic: InputConnection): Int {
        val before = ic.getTextBeforeCursor(4, 0) ?: return 1
        if (before.isEmpty()) return 1
        return Character.charCount(Character.codePointBefore(before, before.length))
    }

    private fun StringBuilder.dropLastCodePoint() {
        if (isEmpty()) return
        val len = length
        if (len >= 2 && Character.isSurrogatePair(this[len - 2], this[len - 1])) {
            setLength(len - 2)
        } else {
            setLength(len - 1)
        }
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
        val finalText = activeTextStyle.transform(capitalizeLike(typed, correction ?: typed))

        when {
            composingActive -> ic.commitText(finalText, 1)
            finalText != typed -> {
                ic.deleteSurroundingText(typed.length, 0)
                ic.commitText(finalText, 1)
            }
        }
        currentWord.setLength(0)
        composingActive = false
        learnTypedWord(typed)
    }

    /**
     * Aprende localmente la palabra confirmada (≥3 letras, solo en campos con
     * funciones de texto). El guardado en disco se difiere para no escribir en
     * cada tecla: se programa 3 s después del último aprendizaje.
     */
    private fun learnTypedWord(word: String) {
        if (!textFeaturesAllowed || !settings.suggestionsEnabled) return
        if (word.length < 3 || !isAllLetters(word)) return
        val plain = TextStyles.plain(word).lowercase()
        serviceScope.launch(Dispatchers.IO) {
            val updated = suggestionEngine.learnWord(plain)
            saveJob?.cancel()
            saveJob = serviceScope.launch(Dispatchers.IO) {
                kotlinx.coroutines.delay(3000L)
                userWordStore.save(updated)
            }
        }
    }

    private var saveJob: kotlinx.coroutines.Job? = null

    /** Marca de tiempo del último espacio simple, para el atajo doble espacio → ". ". */
    private var lastSpaceCommitAt: Long = 0L

    private fun handleSpace(ic: InputConnection) {
        commitCurrentWord(ic, applyAutoCorrect = true)
        val now = SystemClock.uptimeMillis()
        val before = ic.getTextBeforeCursor(3, 0)?.toString() ?: ""
        val doubleSpace = settings.autoCorrect && textFeaturesAllowed &&
                now - lastSpaceCommitAt <= DOUBLE_SPACE_WINDOW_MS &&
                before.length >= 2 &&
                before[before.length - 1] == ' ' &&
                before[before.length - 2].isLetter()
        if (doubleSpace) {
            // Doble espacio rápido tras una palabra: sustituye el espacio por punto y espacio.
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            lastSpaceCommitAt = 0L
        } else {
            ic.commitText(" ", 1)
            lastSpaceCommitAt = now
        }
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
    // Barra superior: ajustes, emojis, portapapeles y fuentes
    // ------------------------------------------------------------------ //

    private val toolbarListener = object : KeyboardToolbarView.Listener {
        override fun onSettings() {
            val intent = Intent(this@AtlasInputMethodService, SettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { startActivity(intent) }
        }

        override fun onEmoji() = showPanel(KeyboardMode.EMOJI)

        override fun onClipboard() = showPanel(KeyboardMode.CLIPBOARD)

        override fun onFonts() = showPanel(KeyboardMode.TEXT_STYLES)
    }

    private val textStylePanelListener = object : TextStylePanelView.Listener {
        override fun onStyleSelected(styleId: String) {
            serviceScope.launch { settingsRepo.setTextStyleId(styleId) }
            // Aplicación inmediata; el flujo de ajustes confirmará el estado.
            activeTextStyle = TextStyles.byId(styleId)
            toolbar?.setFontStyleActive(activeTextStyle != TextStyles.NORMAL)
            textStylePanel?.setActiveStyle(styleId)
        }

        override fun onBackToKeyboard() = showKeyboard(KeyboardMode.LETTERS)
    }

    // ------------------------------------------------------------------ //
    // Listener del teclado
    // ------------------------------------------------------------------ //

    private val keyboardListener = object : KeyboardView.Listener {

        override fun onTextCommit(text: String) {
            val ic = currentInputConnection ?: return
            when {
                text == " " -> handleSpace(ic)
                isAllLetters(text) -> {
                    // Con una fuente activa, las letras se insertan estilizadas.
                    val styled = activeTextStyle.transform(text)
                    if (!textFeaturesAllowed) {
                        // Campos de contraseña o no texto: entrada directa.
                        ic.commitText(styled, 1)
                    } else {
                        currentWord.append(styled)
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
                    currentWord.dropLastCodePoint()
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
                    ic.deleteSurroundingText(unitsOfLastCodePoint(ic), 0)
                    currentWord.dropLastCodePoint()
                    updateSuggestions()
                }
                else -> {
                    val selected = ic.getSelectedText(0)
                    if (selected.isNullOrEmpty()) {
                        ic.deleteSurroundingText(unitsOfLastCodePoint(ic), 0)
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

        override fun onGlideFinished(letters: List<String>) {
            val ic = currentInputConnection ?: return
            val trace = letters.joinToString("")
            if (trace.isEmpty()) return
            if (!textFeaturesAllowed || trace.length < 2) {
                // Campos seguros o trazo mínimo: inserta literal.
                trace.forEach { onTextCommit(it.toString()) }
                return
            }
            commitCurrentWord(ic, applyAutoCorrect = false)
            val match = suggestionEngine.bestGlideMatch(trace) ?: trace
            val styled = activeTextStyle.transform(capitalizeLike(trace, match))
            ic.commitText("$styled ", 1)
            if (shiftState == ShiftState.ON) {
                shiftState = ShiftState.OFF
                rebuildKeyboard()
            }
            evaluateAutoCaps()
            updateSuggestions()
        }

        override fun onSpaceLongPressed() {
            // Pulsación larga en el espacio: selector de método de entrada del sistema.
            switchInputMethodPicker()
        }

        private fun switchInputMethodPicker() {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showInputMethodPicker()
        }

        override fun onCursorMove(steps: Int) {
            if (steps == 0) return
            val code = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
            repeat(kotlin.math.abs(steps)) { sendDownUpKeyEvents(code) }
            // El cursor cambió de sitio: la palabra en composición deja de ser válida.
            currentWord.setLength(0)
            composingActive = false
            updateSuggestions()
        }

        override fun onDeleteWord() {
            val ic = currentInputConnection ?: return
            if (composingActive && currentWord.isNotEmpty()) {
                // Con composición activa, el gesto borra la palabra entera.
                currentWord.setLength(0)
                ic.commitText("", 1)
                composingActive = false
                updateSuggestions()
                return
            }
            val before = ic.getTextBeforeCursor(48, 0)?.toString().orEmpty()
            if (before.isEmpty()) return
            var i = before.length
            while (i > 0 && before[i - 1].isWhitespace()) i--
            while (i > 0 && !before[i - 1].isWhitespace()) i--
            val toDelete = before.length - i
            if (toDelete > 0) {
                // No corta pares subrogados (emojis) por la mitad.
                val safeDelete = if (i < before.length &&
                    Character.isLowSurrogate(before[i]) && i > 0
                ) toDelete + 1 else toDelete
                ic.deleteSurroundingText(safeDelete, 0)
            }
            syncCurrentWordFromCursor()
            updateSuggestions()
        }

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
        private const val TOOLBAR_HEIGHT_DP = 40
        /** Ventana (ms) entre dos espacios para convertirlos en punto y espacio. */
        private const val DOUBLE_SPACE_WINDOW_MS = 600L
        private const val SUGGESTION_STRIP_HEIGHT_DP = 44
    }
}
