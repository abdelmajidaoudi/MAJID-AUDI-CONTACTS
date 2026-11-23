package com.example.domain.util

import android.net.Uri
import com.example.domain.model.ContactDetail

object WhatsAppHelper {

    /**
     * Converts local or international phone numbers into the digits-only format required by https://wa.me/<number>.
     * Examples (with defaultCountryCode = "212"):
     * - "06 12 34 56 78" -> "212612345678"
     * - "+212 6 12 34 56 78" -> "212612345678"
     * - "00212 612-345-678" -> "212612345678"
     * - "+33 6 12 34 56 78" -> "33612345678"
     */
    fun formatPhoneForWaMe(rawPhone: String, defaultCountryCode: String = "212"): String {
        val trimmed = rawPhone.trim()
        if (trimmed.isEmpty()) return ""
        val cleanCode = defaultCountryCode.filter { it.isDigit() }.ifEmpty { "212" }

        val hasLeadingPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return ""

        return when {
            hasLeadingPlus -> {
                // Handle "+21206..." -> "2126..."
                if (digits.startsWith("${cleanCode}0") && digits.length > cleanCode.length + 1) {
                    cleanCode + digits.substring(cleanCode.length + 1)
                } else {
                    digits
                }
            }
            digits.startsWith("00") && digits.length > 4 -> digits.substring(2)
            digits.startsWith("0") && digits.length >= 9 -> cleanCode + digits.substring(1)
            else -> digits
        }
    }

    fun buildWaMeUrl(rawPhone: String, defaultCountryCode: String = "212"): String {
        val formatted = formatPhoneForWaMe(rawPhone, defaultCountryCode)
        return if (formatted.isEmpty()) "https://wa.me/" else "https://wa.me/$formatted"
    }

    fun buildWaMeTextUrl(messageText: String): String {
        return "https://wa.me/?text=${Uri.encode(messageText)}"
    }

    fun formatContactAsShareText(contact: ContactDetail, defaultCountryCode: String = "212"): String {
        return buildString {
            appendLine("📇 ${contact.displayName}")
            if (contact.organization.isNotBlank()) {
                val role = listOf(contact.jobTitle, contact.organization).filter { it.isNotBlank() }.joinToString(" • ")
                appendLine("🏢 $role")
            }
            contact.phones.forEach { phone ->
                appendLine("📞 ${phone.number} (${phone.typeLabel})")
                val wa = buildWaMeUrl(phone.number, defaultCountryCode)
                appendLine("💬 WhatsApp : $wa")
            }
            contact.emails.forEach { email ->
                appendLine("✉️ ${email.email}")
            }
            if (contact.notes.isNotBlank()) {
                appendLine("📝 ${contact.notes}")
            }
        }.trim()
    }
}
