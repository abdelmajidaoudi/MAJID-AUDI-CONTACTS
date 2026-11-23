package com.example.domain.util

import java.text.Normalizer
import java.util.Locale

object AlphaHelper {

    val LATIN_ALPHABET = ('A'..'Z').map { it.toString() }
    val ARABIC_ALPHABET = listOf(
        "ا", "ب", "ت", "ث", "ج", "ح", "خ", "د", "ذ", "ر",
        "ز", "س", "ش", "ص", "ض", "ط", "ظ", "ع", "غ", "ف",
        "ق", "ك", "ل", "م", "ن", "ه", "و", "ي"
    )

    fun computeFirstLetter(displayName: String): String {
        val trimmed = displayName.trim()
        if (trimmed.isEmpty()) return "#"
        val firstChar = trimmed[0]

        // Arabic range
        if (firstChar in '\u0600'..'\u06FF' || firstChar in '\u0750'..'\u077F') {
            return normalizeArabicChar(firstChar)
        }

        // Amazigh / Tifinagh range
        if (firstChar in '\u2D30'..'\u2D7F') {
            return firstChar.toString()
        }

        // Strip Latin diacritics (é -> E, ç -> C, à -> A)
        val decomposed = Normalizer.normalize(firstChar.toString(), Normalizer.Form.NFD)
        val baseChar = decomposed.firstOrNull { Character.isLetterOrDigit(it) } ?: firstChar
        val upper = baseChar.uppercaseChar()
        return if (upper in 'A'..'Z') upper.toString() else "#"
    }

    fun computeSortKey(displayName: String): String {
        val trimmed = displayName.trim()
        if (trimmed.isEmpty()) return "zzz"
        val normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase(Locale.FRENCH)
        return buildString(normalized.length) {
            for (c in normalized) {
                when (c) {
                    'أ', 'إ', 'آ', 'ٱ' -> append('ا')
                    'ى' -> append('ي')
                    'ة' -> append('ه')
                    else -> append(c)
                }
            }
        }
    }

    private fun normalizeArabicChar(c: Char): String = when (c) {
        'أ', 'إ', 'آ', 'ٱ', 'ا' -> "ا"
        'ى', 'ئ', 'ي' -> "ي"
        'ؤ', 'و' -> "و"
        'ة' -> "ت"
        in 'ا'..'ي' -> c.toString()
        else -> "#"
    }
}
