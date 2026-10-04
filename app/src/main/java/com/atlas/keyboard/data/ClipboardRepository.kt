package com.atlas.keyboard.data

import android.content.Context
import org.json.JSONArray

/**
 * Historial local del portapapeles: los últimos textos copiados por el usuario
 * en cualquier app, guardados únicamente en SharedPreferences del dispositivo.
 */
class ClipboardRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "atlas_clipboard"
        private const val KEY_ITEMS = "items"
        private const val MAX_ITEMS = 25
        private const val MAX_ITEM_LENGTH = 500
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Elementos del historial, del más reciente al más antiguo. */
    @Synchronized
    fun items(): List<String> {
        val json = prefs.getString(KEY_ITEMS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(json)
            List(array.length()) { array.getString(it) }
        }.getOrDefault(emptyList())
    }

    /** Añade un nuevo texto al historial (deduplicado y al principio). */
    @Synchronized
    fun add(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || clean.length > MAX_ITEM_LENGTH) return
        val current = items().toMutableList()
        current.remove(clean)
        current.add(0, clean)
        while (current.size > MAX_ITEMS) current.removeAt(current.size - 1)
        save(current)
    }

    @Synchronized
    fun remove(text: String) {
        val current = items().toMutableList()
        if (current.remove(text)) save(current)
    }

    @Synchronized
    fun clear() = save(emptyList())

    private fun save(list: List<String>) {
        val array = JSONArray()
        list.forEach { array.put(it) }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }
}
