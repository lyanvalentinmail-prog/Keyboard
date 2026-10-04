package com.atlas.keyboard.suggestion

import android.content.Context
import com.atlas.keyboard.textstyle.TextStyles
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

    /** Palabras aprendidas del usuario (palabra → usos), priorizadas al sugerir. */
    @Volatile
    private var userWords: Map<String, Int> = emptyMap()

    @Volatile
    private var userWordsNormalized: Map<String, String> = emptyMap()

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

    /** Carga en memoria el vocabulario aprendido (al arrancar el teclado). */
    fun setUserWords(words: Map<String, Int>) {
        userWords = words
        userWordsNormalized = words.keys.associateWith { normalize(it) }
    }

    /** true si el vocabulario aprendido contiene entradas. */
    fun hasUserWords(): Boolean = userWords.isNotEmpty()

    /**
     * Registra un uso de [word] (en minúsculas, ya validada como "solo letras").
     * Devuelve el mapa actualizado listo para persistir.
     */
    fun learnWord(word: String): Map<String, Int> {
        val updated = userWords.toMutableMap()
        updated[word] = (updated[word] ?: 0) + 1
        setUserWords(updated)
        return updated
    }

    /** Vacía el vocabulario aprendido. */
    fun clearUserWords() = setUserWords(emptyMap())

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
        // 1) Palabras aprendidas del usuario, de más a menos usadas.
        if (userWords.isNotEmpty()) {
            userWordsNormalized.entries
                .filter { (original, norm) -> norm.length > prefix.length &&
                        norm.startsWith(prefix) && original != prefix }
                .sortedByDescending { userWords[it.key] ?: 0 }
                .forEach {
                    result += it.key
                    if (result.size >= max) return result
                }
        }
        // 2) Diccionario base por frecuencia.
        for (i in dict.normalized.indices) {
            val norm = dict.normalized[i]
            if (norm.length > prefix.length && norm.startsWith(prefix)) {
                if (dict.words[i] !in result) {
                    result += dict.words[i]
                    if (result.size >= max) break
                }
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
        if (lower in userWords) return null // aprendida del usuario: no la toca

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

    /**
     * Escritura por gestos: dado el rastro de letras que el dedo atravesó (en
     * orden, sin duplicados consecutivos), devuelve la mejor palabra del
     * diccionario compatible: misma primera y última letra y cuyas letras
     * aparezcan en orden dentro del rastro. Gana la palabra que explica una
     * mayor fracción del rastro; a igual cobertura, la más frecuente (orden
     * del diccionario). `null` si no hay candidata razonable.
     */
    fun bestGlideMatch(traversed: String): String? {
        if (traversed.length < 2) return null
        val dict = dictionaries[language] ?: return null
        val trace = normalize(traversed.lowercase(Locale.ROOT))
        if (trace.length < 2) return null
        val first = trace.first()
        val last = trace.last()
        var best: String? = null
        var bestCoverage = -1f
        for (i in dict.normalized.indices) {
            val word = dict.normalized[i]
            if (word.length < 2 || word.length > trace.length) continue
            if (word.first() != first || word.last() != last) continue
            // La palabra debe aparecer como subsecuencia ordenada del rastro.
            var ti = 0
            var matched = true
            for (c in word) {
                val found = trace.indexOf(c, ti)
                if (found < 0) { matched = false; break }
                ti = found + 1
            }
            if (!matched) continue
            val coverage = word.length.toFloat() / trace.length
            if (coverage > bestCoverage) {
                bestCoverage = coverage
                best = dict.words[i]
                if (coverage >= 0.99f) break
            }
        }
        return best
    }

    /** true si la palabra (con acentos o en su forma normalizada) existe. */
    fun isKnownWord(input: String): Boolean {
        val dict = dictionaries[language] ?: return false
        val lower = input.lowercase(Locale.ROOT)
        if (dict.words.contains(lower)) return true
        val norm = normalize(lower)
        if (userWordsNormalized.values.contains(norm)) return true
        return dict.normalized.contains(norm)
    }

    /**
     * Normaliza para comparar: convierte caracteres estilizados (fuente activa)
     * a su ASCII equivalente y elimina marcas diacríticas.
     */
    private fun normalize(text: String): String {
        val base = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val mapped = TextStyles.asciiOf(cp)
            if (mapped != null) base.append(mapped) else base.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        val decomposed = Normalizer.normalize(base.toString(), Normalizer.Form.NFD)
        return stripAccentsRegex.replace(decomposed, "")
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
