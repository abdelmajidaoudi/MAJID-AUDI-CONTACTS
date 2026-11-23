package com.example.domain.util

object PhoneNormalizer {
    /**
     * Produces a canonical representation suitable for equality checks and indexed lookup.
     * Examples:
     *  - "06 12 34 56 78" -> "0612345678"
     *  - "00212 612-345-678" -> "+212612345678"
     *  - "+212 (0)6 12 34 56 78" -> "+212612345678"
     */
    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val sb = StringBuilder(trimmed.length)
        var hasPlus = false
        for (i in trimmed.indices) {
            val c = trimmed[i]
            if (c == '+' && sb.isEmpty() && !hasPlus) {
                hasPlus = true
                sb.append('+')
            } else if (c.isDigit()) {
                sb.append(c)
            }
        }
        var result = sb.toString()
        if (result.startsWith("00") && result.length > 4) {
            result = "+" + result.substring(2)
        }
        // Handle "+CC06..." pattern like "+2120612345678" or "+330612345678"
        if (result.startsWith("+2120") && result.length >= 14) {
            result = "+212" + result.substring(5)
        } else if (result.startsWith("+330") && result.length >= 13) {
            result = "+33" + result.substring(4)
        }
        return result
    }

    /**
     * Returns the last 9 digits when available so local formats ("0612345678")
     * and international formats ("+212612345678" / "+33612345678") map to the same cluster key.
     */
    fun tailKey(raw: String): String {
        val digitsOnly = buildString {
            for (c in raw) if (c.isDigit()) append(c)
        }
        if (digitsOnly.isEmpty()) return ""
        return if (digitsOnly.length >= 9) {
            digitsOnly.takeLast(9)
        } else {
            digitsOnly
        }
    }
}
