package com.atlas.keyboard.data

import android.content.Context

/**
 * Historial local de emojis usados recientemente. Se guarda en
 * SharedPreferences (solo en el dispositivo), sin red de por medio: los emojis
 * se concatenan separados por espacio, que nunca forma parte de un emoji.
 */
class RecentEmojiStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Emojis más recientes primero (máximo [MAX]). */
    @Synchronized
    fun get(): List<String> =
        prefs.getString(KEY, null)
            ?.split(' ')
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    /** Coloca [emoji] al frente del historial, sin duplicados. */
    @Synchronized
    fun push(emoji: String) {
        val list = get().toMutableList()
        list.remove(emoji)
        list.add(0, emoji)
        while (list.size > MAX) list.removeAt(list.size - 1)
        prefs.edit().putString(KEY, list.joinToString(" ")).apply()
    }

    private companion object {
        const val PREFS = "atlas_recent_emojis"
        const val KEY = "recent"
        const val MAX = 30
    }
}
