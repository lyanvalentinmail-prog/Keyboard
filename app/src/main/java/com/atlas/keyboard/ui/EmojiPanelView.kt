package com.atlas.keyboard.ui

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.atlas.keyboard.R
import com.atlas.keyboard.theme.KeyboardTheme
import com.atlas.keyboard.theme.KeyboardThemes
import com.atlas.keyboard.theme.withAlpha

/**
 * Panel de emojis organizado por categorías con barra de pestañas
 * desplazable y cuadrícula. El botón "ABC" vuelve al teclado.
 */
class EmojiPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    interface Listener {
        fun onEmojiSelected(emoji: String)
        fun onBackToKeyboard()
    }

    var listener: Listener? = null

    private data class Category(val name: String, val icon: String, val emojis: List<String>)

    private val categories = listOf(
        Category("Caritas", "😀", listOf(
            "😀","😃","😄","😁","😆","😅","🤣","😂","🙂","🙃","😉","😊","😇","🥰","😍","🤩","😘","😗","😚","😙",
            "🥲","😋","😛","😜","🤪","😝","🤑","🤗","🤭","🤫","🤔","🫡","🤐","🤨","😐","😑","😶","😏","😒","🙄",
            "😬","🤥","😌","😔","😪","🤤","😴","😷","🤒","🤕","🤢","🤮","🥵","🥶","🥴","😵","🤯","🤠","🥳","🥸",
            "😎","🤓","🧐","😕","🫤","😟","🙁","☹️","😮","😯","😲","😳","🥺","🥹","😦","😧","😨","😰","😥","😢",
            "😭","😱","😖","😣","😞","😓","😩","😫","🥱","😤","😡","😠","🤬","😈","👿","💀","☠️","💩","🤡","👹",
            "👺","👻","👽","👾","🤖"
        )),
        Category("Gestos", "👋", listOf(
            "👋","🤚","🖐️","✋","🖖","🫱","🫲","🫳","🫴","👌","🤌","🤏","✌️","🤞","🫰","🤟","🤘","🤙","👈","👉",
            "👆","🖕","👇","☝️","🫵","👍","👎","✊","👊","🤛","🤜","👏","🙌","🫶","👐","🤲","🤝","🙏","✍️","💅",
            "🤳","💪","🦾","🦿","🦵","🦶","👂","🦻","👃","🧠","👀","👁️","👅","👄","🫦","👶","🧒","👦","👧","🧑",
            "👱","👨","🧔","👩","🧓","👴","👵","🙍","🙎","🙅","🙆","💁","🙋","🧏","🙇","🤦","🤷","💆","💇","🚶","🧍"
        )),
        Category("Animales", "🐻", listOf(
            "🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐨","🐯","🦁","🐮","🐷","🐸","🐵","🙈","🙉","🙊","🐒","🐔",
            "🐧","🐦","🐤","🦆","🦅","🦉","🦇","🐺","🐗","🐴","🦄","🐝","🪱","🐛","🦋","🐌","🐞","🐜","🪰","🪲",
            "🦟","🕷️","🐢","🐍","🦎","🦂","🐙","🦑","🦐","🦞","🦀","🐡","🐠","🐟","🐬","🐳","🐋","🦈","🐊","🐅",
            "🐆","🦓","🦍","🦧","🐘","🦛","🦏","🐪","🐫","🦒","🦘","🦬","🐃","🐂","🐄","🐎","🐖","🐏","🐑","🦙",
            "🐐","🦌","🐕","🐩","🦮","🐈","🐓","🦃","🦚","🦜","🦢","🦩","🕊️","🐇","🦝","🦨","🦦","🦥","🐿️","🦔",
            "🌵","🎄","🌲","🌳","🌴","🌱","🌿","☘️","🍀","🌾","🌺","🌻","🌹","🥀","🌷","🌼","🌸","💐","🍄","🌍"
        )),
        Category("Comida", "🍎", listOf(
            "🍏","🍎","🍐","🍊","🍋","🍌","🍉","🍇","🍓","🫐","🍈","🍒","🍑","🥭","🍍","🥥","🥝","🍅","🍆","🥑",
            "🥦","🥬","🥒","🌶️","🫑","🌽","🥕","🫒","🧄","🧅","🥔","🍠","🥐","🍞","🥖","🥨","🥯","🧀","🥚","🍳",
            "🧈","🥞","🧇","🥓","🥩","🍗","🍖","🌭","🍔","🍟","🍕","🫓","🥪","🥙","🧆","🌮","🌯","🫔","🥗","🥘",
            "🍝","🍜","🍲","🍛","🍣","🍱","🥟","🦪","🍤","🍙","🍚","🍘","🍥","🥠","🥮","🍢","🍡","🍧","🍨","🍦",
            "🥧","🧁","🍰","🎂","🍮","🍭","🍬","🍫","🍿","🧋","☕","🍵","🧃","🥤","🧉","🍺","🍻","🥂","🍷","🥃"
        )),
        Category("Deportes", "⚽", listOf(
            "⚽","🏀","🏈","⚾","🥎","🎾","🏐","🏉","🥏","🎱","🪀","🏓","🏸","🏒","🏑","🥍","🏏","🪃","🥅","⛳",
            "🪁","🏹","🎣","🤿","🥊","🥋","🎽","🛹","🛼","🛷","⛸️","🥌","🎿","⛷️","🏂","🪂","🏋️","🤼","🤸","⛹️",
            "🤺","🤾","🏌️","🏇","🧘","🏄","🏊","🤽","🚣","🧗","🚵","🚴","🏆","🥇","🥈","🥉","🏅","🎖️","🏵️","🎗️",
            "🎫","🎟️","🎪","🤹","🎭","🩰","🎨","🎬","🎤","🎧","🎼","🎹","🥁","🪘","🎷","🎺","🎸","🎻","🎲","♟️",
            "🎯","🎳","🎮","🎰","🧩","🪄","🀄","🎴","🃏","🎽"
        )),
        Category("Viajes", "✈️", listOf(
            "🚗","🚕","🚙","🚌","🚎","🏎️","🚓","🚑","🚒","🚐","🛻","🚚","🚛","🚜","🛴","🚲","🛵","🏍️","🛺","🚨",
            "🚔","🚍","🚘","🚖","🚡","🚠","🚟","🚃","🚋","🚞","🚝","🚄","🚅","🚈","🚂","🚆","🚇","🚊","🚉","✈️",
            "🛫","🛬","🛩️","💺","🛰️","🚀","🛸","🚁","🛶","⛵","🚤","🛥️","🛳️","⛴️","🚢","⚓","⛽","🚧","🚦","🚥",
            "🚏","🗺️","🗿","🗽","🗼","🏰","🏯","🏟️","🎡","🎢","🎠","⛲","⛱️","🏖️","🏝️","🏜️","🌋","⛰️","🏔️","🗻",
            "🏕️","⛺","🏠","🏡","🏘️","🏗️","🏭","🏢","🏬","🏣","🏤","🏥","🏦","🏨","🏪","🏫","🏩","💒","🏛️","⛪",
            "🕌","🛕","🕍","🕋","⛩️","🛤️","🛣️","🗾","🎑","🌆"
        )),
        Category("Objetos", "💡", listOf(
            "⌚","📱","📲","💻","⌨️","🖥️","🖨️","🖱️","🕹️","💽","💾","💿","📀","📼","📷","📸","📹","🎥","📽️","📞",
            "☎️","📟","📠","📺","📻","🎙️","🎚️","🎛️","⏱️","⏲️","⏰","🕰️","⌛","⏳","📡","🔋","🪫","🔌","💡","🔦",
            "🕯️","🪔","🧯","💸","💵","💴","💶","💷","🪙","💰","💳","💎","⚖️","🪜","🧰","🪛","🔧","🔨","⚒️","🛠️",
            "⛏️","🪚","🔩","⚙️","🧱","⛓️","🧲","🔫","💣","🧨","🪓","🔪","🗡️","⚔️","🛡️","⚰️","⚱️","🏺","🔮","📿",
            "🧿","💈","⚗️","🔭","🔬","🩹","🩺","💊","💉","🩸","🧬","🦠","🧫","🧪","🌡️","🧹","🧺","🧻","🚽","🚿",
            "🛁","🧼","🪥","🪒","🧽","🧴","🛎️","🔑","🗝️","🚪","🪑","🛋️","🛏️","🧸","🖼️","🪞","🪟","🛍️","🛒","🎁",
            "🎈","🎀","🎊","🎉","🏮","✉️","📩","📨","📧","💌","📥","📤","📦","🏷️","📪","📫","📬","📭","📮","📜",
            "📃","📄","📑","🧾","📊","📈","📉","🗒️","🗓️","📆","📅","🗑️","📇","🗄️","📋","📁","📂","🗞️","📰","📓",
            "📔","📒","📕","📗","📘","📙","📚","📖","🔖","🧷","🔗","📎","📐","📏","🧮","📌","📍","✂️","🖊️","✒️",
            "🖌️","🖍️","📝","✏️","🔍","🔎","🔏","🔐","🔒","🔓"
        )),
        Category("Símbolos", "🔣", listOf(
            "❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔","❣️","💕","💞","💓","💗","💖","💘","💝","💟","☮️",
            "✝️","☪️","🕉️","☸️","✡️","🔯","🕎","☯️","☦️","⛎","♈","♉","♊","♋","♌","♍","♎","♏","♐","♑","♒",
            "♓","🆔","⚛️","☢️","☣️","📴","📳","✴️","🆚","💮","🉐","🈴","🈵","🈲","🅰️","🅱️","🆎","🆑","🅾️","🆘",
            "❌","⭕","🛑","⛔","📛","🚫","💯","💢","♨️","🚷","🚯","🚳","🚱","🔞","📵","🚭","❗","❕","❓","❔",
            "‼️","⁉️","🔅","🔆","⚠️","🚸","🔱","⚜️","🔰","♻️","✅","💹","❇️","✳️","❎","🌐","💠","Ⓜ️","🌀","💤",
            "🏧","🚾","♿","🅿️","🛂","🛃","🛄","🛅","🚹","🚺","🚼","⚧️","🚻","🚮","🎦","📶","🔣","ℹ️","🔤","🔡",
            "🔠","🆖","🆗","🆙","🆒","🆕","🆓","🔢","🔟","⏏️","▶️","⏸️","⏹️","⏺️","⏭️","⏮️","⏩","⏪","⏫","⏬",
            "◀️","🔼","🔽","➡️","⬅️","⬆️","⬇️","↗️","↘️","↙️","↖️","↕️","↔️","↪️","↩️","⤴️","⤵️","🔀","🔁","🔂","🔄",
            "🎵","🎶","➕","➖","➗","✖️","🟰","♾️","💲","💱","™️","©️","®️","🔚","🔙","🔛","🔝","🔜","✔️","☑️","🔘",
            "🔴","🟠","🟡","🟢","🔵","🟣","⚫","⚪","🟤","🔺","🔻","🔸","🔹","🔶","🔷","🔳","🔲","🟥","🟧","🟨","🟩",
            "🟦","🟪","⬛","⬜","🟫","🔈","🔇","🔉","🔊","🔔","🔕","📣","📢","💬","💭","🗯️","♠️","♣️","♥️","♦️","🀄"
        ))
    )

    private var theme: KeyboardTheme = KeyboardThemes.DARK
    private val density = resources.displayMetrics.density
    private val tabViews = mutableListOf<TextView>()
    private var currentCategory = 0

    private val gridAdapter = object : BaseAdapter() {
        var emojis: List<String> = categories[0].emojis
        override fun getCount(): Int = emojis.size
        override fun getItem(position: Int): String = emojis[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val tv = (convertView as? TextView) ?: TextView(context).apply {
                // GridView asigna sus propios LayoutParams; damos altura mínima.
                minimumHeight = (48 * density).toInt()
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                val out = TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
                setBackgroundResource(out.resourceId)
            }
            tv.text = getItem(position)
            return tv
        }
    }

    private val topBar = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private val gridView = GridView(context).apply {
        numColumns = GridView.AUTO_FIT
        columnWidth = (48 * density).toInt()
        stretchMode = GridView.STRETCH_COLUMN_WIDTH
        adapter = gridAdapter
        onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            listener?.onEmojiSelected(gridAdapter.getItem(position))
        }
    }

    init {
        orientation = VERTICAL
        buildTopBar()
        addView(topBar, LayoutParams(LayoutParams.MATCH_PARENT, (48 * density).toInt()))
        addView(gridView, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        setCategory(0)
    }

    private fun buildTopBar() {
        topBar.removeAllViews()
        tabViews.clear()

        val back = TextView(context).apply {
            text = context.getString(R.string.back_to_keyboard)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            val pad = (14 * density).toInt()
            setPadding(pad, 0, pad, 0)
            setOnClickListener { listener?.onBackToKeyboard() }
        }
        topBar.addView(back, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))

        val divider = View(context)
        topBar.addView(divider, LayoutParams((1 * density).toInt().coerceAtLeast(1), LayoutParams.MATCH_PARENT))

        val tabsRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        categories.forEachIndexed { index, category ->
            val tab = TextView(context).apply {
                text = category.icon
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                gravity = Gravity.CENTER
                setOnClickListener { setCategory(index) }
            }
            val size = (48 * density).toInt()
            tabsRow.addView(tab, LayoutParams(size, LayoutParams.MATCH_PARENT))
            tabViews += tab
        }
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(tabsRow, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        }
        topBar.addView(scroll, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
    }

    fun setCategory(index: Int) {
        currentCategory = index.coerceIn(categories.indices)
        gridAdapter.emojis = categories[currentCategory].emojis
        gridAdapter.notifyDataSetChanged()
        gridView.setSelection(0)
        refreshTabColors()
    }

    fun applyTheme(newTheme: KeyboardTheme) {
        theme = newTheme
        setBackgroundColor(theme.backgroundColor)
        topBar.setBackgroundColor(theme.backgroundColor)
        (topBar.getChildAt(0) as? TextView)?.setTextColor(theme.specialKeyText)
        gridView.setBackgroundColor(theme.backgroundColor)
        refreshTabColors()
    }

    private fun refreshTabColors() {
        tabViews.forEachIndexed { index, tv ->
            if (index == currentCategory) {
                tv.setBackgroundColor(withAlpha(theme.accentColor, 0.2f))
            } else {
                tv.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        }
    }
}
