package com.atlas.keyboard.data

import android.content.Context

/**
 * Vocabulario aprendido localmente: palabras que el usuario escribe con
 * frecuencia y el teclado prioriza en las sugerencias. Todo queda en el
 * almacenamiento privado de la app (SharedPreferences); nada sale del
 * dispositivo. Formato: "palabra:usos palabra:usos …".
 */
class UserWordStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Mapa palabra → número de usos observados. */
    @Synchronized
    fun load(): Map<String, Int> {
        val raw = prefs.getString(KEY, null) ?: return emptyMap()
        val result = LinkedHashMap<String, Int>()
        raw.split(' ').forEach { entry ->
            val sep = entry.lastIndexOf(':')
            if (sep > 0) {
                val word = entry.substring(0, sep)
                val count = entry.substring(sep + 1).toIntOrNull() ?: return@forEach
                if (word.isNotEmpty() && count > 0) result[word] = count
            }
        }
        return result
    }

    /** Persiste el mapa, podando las menos usadas si supera [MAX_WORDS]. */
    @Synchronized
    fun save(words: Map<String, Int>) {
        val trimmed = if (words.size <= MAX_WORDS) words
        else words.entries.sortedByDescending { it.value }.take(MAX_WORDS)
            .associate { it.toPair() }
        val raw = trimmed.entries.joinToString(" ") { "${it.key}:${it.value}" }
        prefs.edit().putString(KEY, raw).apply()
    }

    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val PREFS = "atlas_user_words"
        const val KEY = "words"
        const val MAX_WORDS = 500
    }
}
