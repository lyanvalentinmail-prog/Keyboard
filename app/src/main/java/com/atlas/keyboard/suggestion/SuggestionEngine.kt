package com.atlas.keyboard.suggestion

import android.content.Context
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.min

/**
 * Motor de sugerencias y autocorrección 100 % local. Carga los diccionarios de
 * `res/raw` (ordenados por frecuencia), sugiere por prefijo y corrige por
 * distancia de edición. Jamás sale texto del dispositivo.
 */
class SuggestionEngine {

    /** Palabras y sus formas normalizadas (sin acentos) alineadas por índice. */
    private data class Dictionary(val words: List<String>, val normalized: List<String>)

    private val dictionaries = ConcurrentHashMap<String, Dictionary>()

    @Volatile
    private var language: String = "es"

    private val stripAccentsRegex = "\\p{Mn}+".toRegex()

    /** Carga el diccionario del idioma si todavía no está en memoria. */
    fun loadLanguage(context: Context, lang: String, rawResId: Int) {
        if (dictionaries.containsKey(lang)) return
        val words = context.resources.openRawResource(rawResId).bufferedReader().useLines { seq ->
            seq.map { it.trim().lowercase(Locale.ROOT) }
                .filter { it.isNotEmpty() && !it.startsWith("#") && !it.contains(' ') }
                .distinct()
                .toList()
        }
        dictionaries[lang] = Dictionary(words, words.map { normalize(it) })
    }

    fun setLanguage(lang: String) {
        language = lang
    }

    /**
     * Devuelve hasta [max] palabras que continúan el prefijo escrito, en orden
     * de frecuencia. Insensible a mayúsculas y acentos.
     */
    fun suggest(input: String, max: Int = 3): List<String> {
        if (input.isEmpty()) return emptyList()
        val dict = dictionaries[language] ?: return emptyList()
        val prefix = normalize(input.lowercase(Locale.ROOT))
        if (prefix.isEmpty()) return emptyList()
        val result = ArrayList<String>(max)
        for (i in dict.normalized.indices) {
            val norm = dict.normalized[i]
            if (norm.length > prefix.length && norm.startsWith(prefix)) {
                result += dict.words[i]
                if (result.size >= max) break
            }
        }
        return result
    }

    /**
     * Mejor corrección para [input] o `null` si la palabra ya es válida.
     * Reglas: longitud mínima 3; primero intenta corregir solo los acentos;
     * después distancia de edición 1 (o 2 para palabras de 6+ letras que
     * empiecen igual).
     */
    fun bestCorrection(input: String): String? {
        val word = input.trim()
        if (word.length < 3) return null
        val dict = dictionaries[language] ?: return null
        val lower = word.lowercase(Locale.ROOT)
        if (dict.words.contains(lower)) return null

        val norm = normalize(lower)
        // 1) Misma palabra pero bien acentuada: "cafe" -> "café"
        for (i in dict.normalized.indices) {
            if (dict.normalized[i] == norm) return dict.words[i]
        }
        // 2) Distancia de edición
        val maxDistance = if (norm.length >= 6) 2 else 1
        var bestWord: String? = null
        var bestDistance = maxDistance + 1
        for (i in dict.normalized.indices) {
            val candidate = dict.normalized[i]
            if (abs(candidate.length - norm.length) >= bestDistance) continue
            val distance = levenshtein(norm, candidate, bestDistance - 1)
            if (distance in 1 until bestDistance) {
                if (distance == 2 && candidate.firstOrNull() != norm.firstOrNull()) continue
                bestDistance = distance
                bestWord = dict.words[i]
                if (distance == 1) break
            }
        }
        return bestWord
    }

    /** true si la palabra (con acentos o en su forma normalizada) existe. */
    fun isKnownWord(input: String): Boolean {
        val dict = dictionaries[language] ?: return false
        val lower = input.lowercase(Locale.ROOT)
        if (dict.words.contains(lower)) return true
        val norm = normalize(lower)
        return dict.normalized.contains(norm)
    }

    private fun normalize(text: String): String {
        return stripAccentsRegex.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "")
    }

    /** Distancia de Levenshtein con salida temprana al superar [limit]. */
    private fun levenshtein(a: String, b: String, limit: Int): Int {
        if (abs(a.length - b.length) > limit) return limit + 1
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            var rowMin = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                rowMin = min(rowMin, current[j])
            }
            if (rowMin > limit) return limit + 1
            previous = current
        }
        return previous[b.length]
    }
}
