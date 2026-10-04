package com.atlas.keyboard.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewOutlineProvider
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.atlas.keyboard.R
import com.atlas.keyboard.data.AtlasSettings
import com.atlas.keyboard.data.ClipboardRepository
import com.atlas.keyboard.data.SettingsRepository
import com.atlas.keyboard.theme.KeyboardThemes
import com.atlas.keyboard.theme.customized
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Activity de configuración de Atlas Keyboard: activación del IME, opciones de
 * escritura, apariencia (tema + personalización), idioma, portapapeles y
 * privacidad. Cada cambio se persiste al instante con DataStore.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var repo: SettingsRepository
    private lateinit var clipboardRepo: ClipboardRepository
    private var initializing = true
    private var current = AtlasSettings()
    /** Última terna de colores personalizados aplicada, para no recrear los swatches en cada emisión. */
    private var lastColors: Triple<Int?, Int?, Int?>? = null

    private val density by lazy { resources.displayMetrics.density }

    companion object {
        private val SWATCHES = intArrayOf(
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#E8EAED"),
            Color.parseColor("#121417"),
            Color.parseColor("#000000"),
            Color.parseColor("#1A73E8"),
            Color.parseColor("#0B8043"),
            Color.parseColor("#F9AB00"),
            Color.parseColor("#D81B60"),
            Color.parseColor("#7B1FA2"),
            Color.parseColor("#546E7A")
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        repo = SettingsRepository(this)
        clipboardRepo = ClipboardRepository(this)

        findViewById<Button>(R.id.btn_enable_keyboard).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<Button>(R.id.btn_set_default).setOnClickListener {
            val imm = getSystemService(InputMethodManager::class.java)
            imm?.showInputMethodPicker()
        }
        findViewById<Button>(R.id.btn_clear_clipboard).setOnClickListener {
            clipboardRepo.clear()
            Toast.makeText(this, R.string.clipboard_cleared, Toast.LENGTH_SHORT).show()
        }

        // La vista previa se dibuja con esquinas redondeadas sobre el layout.
        findViewById<ThemePreviewView>(R.id.keyboard_preview).apply {
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 12f * density)
                }
            }
        }

        lifecycleScope.launch {
            current = repo.settingsFlow.first()
            bindInitialState(current)
            initializing = false
            wireListeners()
        }

        // Vista previa en vivo: cualquier cambio de tema/color/radio/tamaño se
        // refleja al instante en la miniatura del teclado.
        lifecycleScope.launch {
            repo.settingsFlow.collect { s ->
                current = s
                val theme = KeyboardThemes.byId(s.themeId).customized(
                    background = s.customBackground,
                    keyColor = s.customKeyColor,
                    textColor = s.customTextColor,
                    radiusDp = s.customRadiusDp,
                    textSizeSp = s.customTextSizeSp
                )
                findViewById<ThemePreviewView>(R.id.keyboard_preview).setTheme(theme)
                val colors = Triple(s.customBackground, s.customKeyColor, s.customTextColor)
                if (colors != lastColors) {
                    lastColors = colors
                    if (!initializing) rebuildSwatches(s)
                }
            }
        }
    }

    // ------------------------------------------------------------------ //
    // Estado inicial de la interfaz
    // ------------------------------------------------------------------ //

    private fun bindInitialState(s: AtlasSettings) {
        findViewById<SwitchCompat>(R.id.switch_vibration).isChecked = s.vibrationEnabled
        findViewById<SwitchCompat>(R.id.switch_sound).isChecked = s.soundEnabled
        findViewById<SwitchCompat>(R.id.switch_auto_caps).isChecked = s.autoCaps
        findViewById<SwitchCompat>(R.id.switch_auto_correct).isChecked = s.autoCorrect
        findViewById<SwitchCompat>(R.id.switch_suggestions).isChecked = s.suggestionsEnabled
        findViewById<SwitchCompat>(R.id.switch_glide_typing).isChecked = s.glideTyping
        findViewById<SwitchCompat>(R.id.switch_number_row).isChecked = s.showNumberRow

        findViewById<SeekBar>(R.id.seek_vibration).apply {
            max = 100
            progress = s.vibrationStrength
        }

        val heightSeek = findViewById<SeekBar>(R.id.seek_height)
        heightSeek.max = SettingsRepository.MAX_HEIGHT_DP - SettingsRepository.MIN_HEIGHT_DP
        heightSeek.progress = s.keyboardHeightDp - SettingsRepository.MIN_HEIGHT_DP
        updateHeightLabel(s.keyboardHeightDp)

        val baseTheme = KeyboardThemes.byId(s.themeId)
        findViewById<RadioGroup>(R.id.radio_group_theme).check(
            when (s.themeId) {
                "light" -> R.id.radio_theme_light
                "amoled" -> R.id.radio_theme_amoled
                "minimal" -> R.id.radio_theme_minimal
                else -> R.id.radio_theme_dark
            }
        )
        findViewById<RadioGroup>(R.id.radio_group_language).check(
            if (s.language == "en") R.id.radio_lang_en else R.id.radio_lang_es
        )

        val radiusSeek = findViewById<SeekBar>(R.id.seek_radius)
        radiusSeek.max = (SettingsRepository.MAX_RADIUS_DP - SettingsRepository.MIN_RADIUS_DP).toInt()
        val radius = s.customRadiusDp ?: baseTheme.cornerRadiusDp
        radiusSeek.progress = (radius - SettingsRepository.MIN_RADIUS_DP).toInt()
            .coerceIn(0, radiusSeek.max)
        updateRadiusLabel(radius)

        val textSizeSeek = findViewById<SeekBar>(R.id.seek_text_size)
        textSizeSeek.max = (SettingsRepository.MAX_TEXT_SIZE_SP - SettingsRepository.MIN_TEXT_SIZE_SP).toInt()
        val textSize = s.customTextSizeSp ?: baseTheme.keyTextSizeSp
        textSizeSeek.progress = (textSize - SettingsRepository.MIN_TEXT_SIZE_SP).toInt()
            .coerceIn(0, textSizeSeek.max)
        updateTextSizeLabel(textSize)

        rebuildSwatches(s)
    }

    private fun rebuildSwatches(s: AtlasSettings) {
        lastColors = Triple(s.customBackground, s.customKeyColor, s.customTextColor)
        buildSwatchRow(R.id.row_bg_colors, s.customBackground) { color ->
            lifecycleScope.launch { repo.setCustomBackground(color) }
            current = current.copy(customBackground = color)
            rebuildSwatches(current)
        }
        buildSwatchRow(R.id.row_key_colors, s.customKeyColor) { color ->
            lifecycleScope.launch { repo.setCustomKeyColor(color) }
            current = current.copy(customKeyColor = color)
            rebuildSwatches(current)
        }
        buildSwatchRow(R.id.row_text_colors, s.customTextColor) { color ->
            lifecycleScope.launch { repo.setCustomTextColor(color) }
            current = current.copy(customTextColor = color)
            rebuildSwatches(current)
        }
    }

    // ------------------------------------------------------------------ //
    // Listeners
    // ------------------------------------------------------------------ //

    private fun wireListeners() {
        findViewById<SwitchCompat>(R.id.switch_vibration).setOnCheckedChangeListener { _, checked ->
            persist { repo.setVibrationEnabled(checked) }
        }
        findViewById<SwitchCompat>(R.id.switch_sound).setOnCheckedChangeListener { _, checked ->
            persist { repo.setSoundEnabled(checked) }
        }
        findViewById<SwitchCompat>(R.id.switch_auto_caps).setOnCheckedChangeListener { _, checked ->
            persist { repo.setAutoCaps(checked) }
        }
        findViewById<SwitchCompat>(R.id.switch_auto_correct).setOnCheckedChangeListener { _, checked ->
            persist { repo.setAutoCorrect(checked) }
        }
        findViewById<SwitchCompat>(R.id.switch_suggestions).setOnCheckedChangeListener { _, checked ->
            persist { repo.setSuggestionsEnabled(checked) }
        }
        findViewById<SwitchCompat>(R.id.switch_glide_typing).setOnCheckedChangeListener { _, checked ->
            persist { repo.setGlideTyping(checked) }
        }
        findViewById<SwitchCompat>(R.id.switch_number_row).setOnCheckedChangeListener { _, checked ->
            persist { repo.setShowNumberRow(checked) }
        }

        findViewById<SeekBar>(R.id.seek_vibration).setOnSeekBarChangeListener(
            seekListener { progress ->
                persist { repo.setVibrationStrength(progress) }
            }
        )

        findViewById<SeekBar>(R.id.seek_height).setOnSeekBarChangeListener(
            seekListener { progress ->
                val dp = SettingsRepository.MIN_HEIGHT_DP + progress
                updateHeightLabel(dp)
                persist { repo.setKeyboardHeightDp(dp) }
            }
        )

        findViewById<SeekBar>(R.id.seek_radius).setOnSeekBarChangeListener(
            seekListener { progress ->
                val value = SettingsRepository.MIN_RADIUS_DP + progress
                updateRadiusLabel(value)
                persist { repo.setCustomRadiusDp(value) }
            }
        )

        findViewById<SeekBar>(R.id.seek_text_size).setOnSeekBarChangeListener(
            seekListener { progress ->
                val value = SettingsRepository.MIN_TEXT_SIZE_SP + progress
                updateTextSizeLabel(value)
                persist { repo.setCustomTextSizeSp(value) }
            }
        )

        findViewById<RadioGroup>(R.id.radio_group_theme)
            .setOnCheckedChangeListener { _, checkedId ->
                val id = when (checkedId) {
                    R.id.radio_theme_light -> "light"
                    R.id.radio_theme_amoled -> "amoled"
                    R.id.radio_theme_minimal -> "minimal"
                    else -> "dark"
                }
                persist { repo.setThemeId(id) }
            }

        findViewById<RadioGroup>(R.id.radio_group_language)
            .setOnCheckedChangeListener { _, checkedId ->
                val lang = if (checkedId == R.id.radio_lang_en) "en" else "es"
                persist { repo.setLanguage(lang) }
            }

        findViewById<Button>(R.id.btn_reset_custom).setOnClickListener {
            lifecycleScope.launch {
                repo.resetCustomization()
                current = repo.settingsFlow.first()
                val baseTheme = KeyboardThemes.byId(current.themeId)
                findViewById<SeekBar>(R.id.seek_radius).progress =
                    (baseTheme.cornerRadiusDp - SettingsRepository.MIN_RADIUS_DP).toInt()
                findViewById<SeekBar>(R.id.seek_text_size).progress =
                    (baseTheme.keyTextSizeSp - SettingsRepository.MIN_TEXT_SIZE_SP).toInt()
                updateRadiusLabel(baseTheme.cornerRadiusDp)
                updateTextSizeLabel(baseTheme.keyTextSizeSp)
                rebuildSwatches(current)
            }
        }
    }

    private fun seekListener(onChange: (Int) -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && !initializing) onChange(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
    }

    private fun persist(block: suspend () -> Unit) {
        if (initializing) return
        lifecycleScope.launch { block() }
    }

    // ------------------------------------------------------------------ //
    // Swatches de color
    // ------------------------------------------------------------------ //

    private fun buildSwatchRow(rowId: Int, selectedColor: Int?, onPick: (Int) -> Unit) {
        val row = findViewById<LinearLayout>(rowId)
        row.removeAllViews()
        val size = (36 * density).toInt()
        val margin = (8 * density).toInt()
        SWATCHES.forEach { color ->
            val swatch = View(this).apply {
                background = circleDrawable(color, selected = selectedColor == color)
                setOnClickListener { onPick(color) }
            }
            val lp = LinearLayout.LayoutParams(size, size)
            lp.marginEnd = margin
            row.addView(swatch, lp)
        }
    }

    private fun circleDrawable(color: Int, selected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            if (selected) {
                setStroke((3 * density).toInt(), Color.parseColor("#1A73E8"))
            } else {
                setStroke((1 * density).toInt(), Color.parseColor("#77808A"))
            }
        }
    }

    // ------------------------------------------------------------------ //
    // Etiquetas de los sliders
    // ------------------------------------------------------------------ //

    private fun updateHeightLabel(dp: Int) {
        findViewById<TextView>(R.id.text_height_value).text = getString(R.string.value_dp, dp)
    }

    private fun updateRadiusLabel(dp: Float) {
        findViewById<TextView>(R.id.text_radius_value).text =
            getString(R.string.value_dp, dp.toInt())
    }

    private fun updateTextSizeLabel(sp: Float) {
        findViewById<TextView>(R.id.text_textsize_value).text =
            getString(R.string.value_sp, sp.toInt())
    }
}
