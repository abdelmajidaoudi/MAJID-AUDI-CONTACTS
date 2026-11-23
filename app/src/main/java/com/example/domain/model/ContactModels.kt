package com.example.domain.model

enum class ThemeMode(val label: String) {
    SYSTEM("Système"),
    LIGHT("Clair"),
    DARK("Sombre")
}

enum class SortOrder(val label: String) {
    ALPHA_ASC("A → Z (Alphabétique)"),
    ALPHA_DESC("Z → A (Inversé)"),
    RECENT("Modifiés récemment")
}

enum class ContactFilter(val label: String) {
    ALL("Tous"),
    NO_PHONE("Sans numéro"),
    DUPLICATES("Doublons"),
    WITH_PHOTO("Avec photo"),
    WITH_EMAIL("Avec e-mail")
}

enum class ExportFormat(val label: String, val extension: String, val mimeType: String) {
    VCF_SINGLE("VCF unique (.vcf)", "vcf", "text/x-vcard"),
    VCF_BY_GROUP("VCF séparés par groupe", "vcf", "text/x-vcard"),
    CSV("CSV compatible Excel (.csv)", "csv", "text/csv"),
    JSON("Sauvegarde JSON (.json)", "json", "application/json")
}

enum class MergeStrategy(val label: String, val description: String) {
    SMART_MERGE(
        "Fusionner intelligemment",
        "Combine les numéros, e-mails, groupes et notes sans perdre d'information."
    ),
    KEEP_BOTH(
        "Conserver les deux",
        "Importe tous les contacts même si des doublons sont détectés."
    ),
    SKIP_DUPLICATE(
        "Ignorer les doublons",
        "Conserve le contact existant et ignore les entrées identiques."
    )
}

data class PhoneEntry(
    val number: String,
    val typeLabel: String = "Mobile"
)

data class EmailEntry(
    val email: String,
    val typeLabel: String = "Personnel"
)

data class ParsedContact(
    val uid: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val displayName: String = "",
    val phones: List<PhoneEntry> = emptyList(),
    val emails: List<EmailEntry> = emptyList(),
    val organization: String = "",
    val jobTitle: String = "",
    val birthday: String = "",
    val postalAddress: String = "",
    val notes: String = "",
    val groups: List<String> = emptyList(),
    val photoBytes: ByteArray? = null,
    val customFields: Map<String, String> = emptyMap()
) {
    fun resolvedDisplayName(): String {
        if (displayName.isNotBlank()) return displayName.trim()
        val combined = listOf(firstName.trim(), lastName.trim()).filter { it.isNotEmpty() }.joinToString(" ")
        if (combined.isNotEmpty()) return combined
        if (organization.isNotBlank()) return organization.trim()
        if (phones.isNotEmpty()) return phones.first().number
        if (emails.isNotEmpty()) return emails.first().email
        return "Contact sans nom"
    }
}

data class ContactSummary(
    val id: Long,
    val displayName: String,
    val firstName: String,
    val lastName: String,
    val firstLetter: String,
    val primaryPhone: String,
    val primaryEmail: String,
    val organization: String,
    val photoPath: String?,
    val groupNames: String,
    val phoneCount: Int,
    val emailCount: Int,
    val avatarColorSeed: Int
)

data class ContactDetail(
    val id: Long = 0L,
    val uid: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val displayName: String = "",
    val phones: List<PhoneEntry> = emptyList(),
    val emails: List<EmailEntry> = emptyList(),
    val organization: String = "",
    val jobTitle: String = "",
    val birthday: String = "",
    val postalAddress: String = "",
    val notes: String = "",
    val photoPath: String? = null,
    val groups: List<GroupItem> = emptyList(),
    val customFields: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class GroupItem(
    val id: Long,
    val name: String,
    val colorHex: String,
    val isSystemDefault: Boolean,
    val contactCount: Int = 0
)

enum class DuplicateReason(val label: String) {
    PHONE("Même numéro de téléphone"),
    EMAIL("Même adresse e-mail"),
    NAME("Même nom complet")
}

data class DuplicateCluster(
    val clusterKey: String,
    val reason: DuplicateReason,
    val matchValue: String,
    val contacts: List<ContactDetail>
)

data class MergeSourceFile(
    val id: String,
    val fileName: String,
    val uriString: String,
    val contacts: List<ParsedContact>,
    val selectedIndices: Set<Int>
) {
    val selectedCount: Int get() = selectedIndices.size
}

data class MergePreviewSummary(
    val totalBefore: Int,
    val duplicatesDetected: Int,
    val estimatedAfter: Int,
    val duplicatePairs: List<Pair<ParsedContact, ParsedContact>>
)

data class OperationProgress(
    val title: String,
    val current: Int,
    val total: Int,
    val indeterminate: Boolean = false,
    val detail: String = ""
) {
    val fraction: Float
        get() = if (total > 0) (current.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
}

data class BackupRecord(
    val fileName: String,
    val filePath: String,
    val timestamp: Long,
    val sizeBytes: Long,
    val isAutomatic: Boolean
)
