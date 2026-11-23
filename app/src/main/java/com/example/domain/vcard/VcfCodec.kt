package com.example.domain.vcard

import android.util.Base64
import com.example.domain.model.ContactDetail
import com.example.domain.model.EmailEntry
import com.example.domain.model.ParsedContact
import com.example.domain.model.PhoneEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Streaming, zero-OOM vCard 2.1 / 3.0 / 4.0 parser & exporter, plus UTF-8 BOM CSV and JSON backup codecs.
 */
object VcfCodec {

    private const val BATCH_SIZE = 500

    suspend fun parseVcfStream(
        inputStream: InputStream,
        onProgress: (parsedCount: Int) -> Unit = {},
        isCancelled: () -> Boolean = { false },
        onBatch: suspend (List<ParsedContact>) -> Unit
    ): Int {
        val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8), 64 * 1024)
        val batch = ArrayList<ParsedContact>(BATCH_SIZE)
        var totalParsed = 0

        var inCard = false
        val logicalLines = ArrayList<String>(24)
        var currentLineBuffer: StringBuilder? = null
        var inQuotedPrintableContinuation = false

        fun flushLogicalLine() {
            currentLineBuffer?.let {
                logicalLines.add(it.toString())
                currentLineBuffer = null
            }
            inQuotedPrintableContinuation = false
        }

        while (true) {
            if (isCancelled()) break
            val rawLine = reader.readLine() ?: break

            // Handle quoted-printable soft break ("=" at end of line) from vCard 2.1
            if (inQuotedPrintableContinuation && currentLineBuffer != null) {
                val trimmedEnd = rawLine.trimEnd()
                if (trimmedEnd.endsWith("=")) {
                    currentLineBuffer!!.append(trimmedEnd.dropLast(1))
                    inQuotedPrintableContinuation = true
                } else {
                    currentLineBuffer!!.append(rawLine)
                    inQuotedPrintableContinuation = false
                }
                continue
            }

            // RFC 2425 / 6350 folded line starts with space or tab
            if ((rawLine.startsWith(" ") || rawLine.startsWith("\t")) && currentLineBuffer != null) {
                currentLineBuffer!!.append(rawLine.substring(1))
                continue
            }

            flushLogicalLine()

            val upper = rawLine.trim().uppercase()
            if (upper == "BEGIN:VCARD") {
                inCard = true
                logicalLines.clear()
                continue
            }
            if (upper == "END:VCARD") {
                if (inCard) {
                    val parsed = parseCardLines(logicalLines)
                    if (parsed != null) {
                        batch.add(parsed)
                        totalParsed++
                        if (batch.size >= BATCH_SIZE) {
                            onBatch(batch.toList())
                            batch.clear()
                            onProgress(totalParsed)
                        }
                    }
                }
                inCard = false
                logicalLines.clear()
                continue
            }

            if (inCard && rawLine.isNotEmpty()) {
                val isQpHeader = rawLine.uppercase().contains("QUOTED-PRINTABLE")
                if (isQpHeader && rawLine.trimEnd().endsWith("=")) {
                    currentLineBuffer = StringBuilder(rawLine.trimEnd().dropLast(1))
                    inQuotedPrintableContinuation = true
                } else {
                    currentLineBuffer = StringBuilder(rawLine)
                    inQuotedPrintableContinuation = false
                }
            }
        }
        flushLogicalLine()

        if (batch.isNotEmpty() && !isCancelled()) {
            onBatch(batch.toList())
            batch.clear()
            onProgress(totalParsed)
        }
        return totalParsed
    }

    suspend fun parseVcfAll(
        inputStream: InputStream,
        maxLimit: Int = Int.MAX_VALUE
    ): List<ParsedContact> {
        val result = ArrayList<ParsedContact>()
        parseVcfStream(
            inputStream = inputStream,
            onBatch = { chunk ->
                for (item in chunk) {
                    if (result.size < maxLimit) result.add(item)
                }
            }
        )
        return result
    }

    private fun parseCardLines(lines: List<String>): ParsedContact? {
        if (lines.isEmpty()) return null

        var uid = ""
        var fn = ""
        var firstName = ""
        var lastName = ""
        var org = ""
        var title = ""
        var birthday = ""
        var address = ""
        var notes = ""
        var photoBytes: ByteArray? = null
        val phones = ArrayList<PhoneEntry>()
        val emails = ArrayList<EmailEntry>()
        val groups = LinkedHashSet<String>()
        val customFields = LinkedHashMap<String, String>()

        for (line in lines) {
            val colonIdx = findUnquotedColon(line)
            if (colonIdx <= 0) continue

            val headerPart = line.substring(0, colonIdx)
            val rawValue = line.substring(colonIdx + 1)

            // Strip group prefix like "item1.TEL" -> "TEL"
            val headerTokens = headerPart.split(';')
            val propNameFull = headerTokens[0].trim().uppercase()
            val propName = propNameFull.substringAfterLast('.')
            val params = headerTokens.drop(1).map { it.trim() }

            val isQp = params.any { it.equals("ENCODING=QUOTED-PRINTABLE", true) || it.equals("QUOTED-PRINTABLE", true) }
            val isBase64 = params.any {
                it.equals("ENCODING=BASE64", true) ||
                    it.equals("ENCODING=B", true) ||
                    it.equals("BASE64", true) ||
                    it.equals("B", true)
            }

            fun decodedValue(): String {
                val v = if (isQp) decodeQuotedPrintable(rawValue) else rawValue
                return unescapeVCardText(v).trim()
            }

            when (propName) {
                "VERSION" -> Unit
                "UID" -> uid = decodedValue()
                "FN" -> fn = decodedValue()
                "N" -> {
                    val v = if (isQp) decodeQuotedPrintable(rawValue) else rawValue
                    val parts = splitEscaped(v, ';')
                    lastName = unescapeVCardText(parts.getOrElse(0) { "" }).trim()
                    firstName = unescapeVCardText(parts.getOrElse(1) { "" }).trim()
                    val middle = unescapeVCardText(parts.getOrElse(2) { "" }).trim()
                    if (middle.isNotEmpty() && firstName.isNotEmpty()) {
                        firstName = "$firstName $middle"
                    }
                }
                "TEL" -> {
                    val num = decodedValue()
                    if (num.isNotEmpty()) {
                        phones.add(PhoneEntry(number = num, typeLabel = resolvePhoneType(params)))
                    }
                }
                "EMAIL" -> {
                    val mail = decodedValue()
                    if (mail.isNotEmpty()) {
                        emails.add(EmailEntry(email = mail, typeLabel = resolveEmailType(params)))
                    }
                }
                "ORG" -> {
                    val v = if (isQp) decodeQuotedPrintable(rawValue) else rawValue
                    org = splitEscaped(v, ';')
                        .map { unescapeVCardText(it).trim() }
                        .filter { it.isNotEmpty() }
                        .joinToString(" - ")
                }
                "TITLE", "ROLE" -> {
                    if (title.isEmpty()) title = decodedValue()
                }
                "BDAY" -> birthday = decodedValue()
                "ADR" -> {
                    val v = if (isQp) decodeQuotedPrintable(rawValue) else rawValue
                    val formatted = splitEscaped(v, ';')
                        .map { unescapeVCardText(it).trim() }
                        .filter { it.isNotEmpty() }
                        .joinToString(", ")
                    if (formatted.isNotEmpty()) {
                        address = if (address.isEmpty()) formatted else "$address\n$formatted"
                    }
                }
                "NOTE" -> {
                    val n = decodedValue()
                    if (n.isNotEmpty()) notes = if (notes.isEmpty()) n else "$notes\n$n"
                }
                "CATEGORIES" -> {
                    val v = if (isQp) decodeQuotedPrintable(rawValue) else rawValue
                    splitEscaped(v, ',').forEach { rawCat ->
                        val cat = unescapeVCardText(rawCat).trim()
                        if (cat.isNotEmpty()) groups.add(cat)
                    }
                }
                "PHOTO" -> {
                    if (photoBytes == null) {
                        photoBytes = decodePhoto(rawValue, isBase64)
                    }
                }
                else -> {
                    if (propName.startsWith("X-") && rawValue.isNotBlank()) {
                        customFields[propName] = decodedValue()
                    }
                }
            }
        }

        if (fn.isEmpty() && firstName.isEmpty() && lastName.isEmpty() && phones.isEmpty() && emails.isEmpty() && org.isEmpty()) {
            return null
        }

        if (fn.isEmpty()) {
            fn = listOf(firstName, lastName).filter { it.isNotEmpty() }.joinToString(" ")
        } else if (firstName.isEmpty() && lastName.isEmpty()) {
            val parts = fn.trim().split(Regex("\\s+"), limit = 2)
            firstName = parts.getOrElse(0) { "" }
            lastName = parts.getOrElse(1) { "" }
        }

        return ParsedContact(
            uid = uid.ifEmpty { UUID.randomUUID().toString() },
            firstName = firstName,
            lastName = lastName,
            displayName = fn,
            phones = phones.distinctBy { it.number },
            emails = emails.distinctBy { it.email.lowercase() },
            organization = org,
            jobTitle = title,
            birthday = birthday,
            postalAddress = address,
            notes = notes,
            groups = groups.toList(),
            photoBytes = photoBytes,
            customFields = customFields
        )
    }

    private fun decodePhoto(rawValue: String, isBase64: Boolean): ByteArray? {
        return try {
            val clean = rawValue.trim()
            val payload = when {
                clean.startsWith("data:", ignoreCase = true) && clean.contains("base64,", ignoreCase = true) ->
                    clean.substringAfter("base64,", "")
                isBase64 || clean.length > 64 -> clean
                else -> return null
            }.replace(Regex("\\s+"), "")
            if (payload.isEmpty()) null else Base64.decode(payload, Base64.DEFAULT)
        } catch (_: Throwable) {
            null
        }
    }

    private fun resolvePhoneType(params: List<String>): String {
        val joined = params.joinToString(";").uppercase()
        return when {
            joined.contains("CELL") || joined.contains("MOBILE") -> "Mobile"
            joined.contains("WORK") -> "Travail"
            joined.contains("HOME") -> "Domicile"
            joined.contains("FAX") -> "Fax"
            else -> "Autre"
        }
    }

    private fun resolveEmailType(params: List<String>): String {
        val joined = params.joinToString(";").uppercase()
        return when {
            joined.contains("WORK") -> "Travail"
            joined.contains("HOME") || joined.contains("INTERNET") -> "Personnel"
            else -> "Autre"
        }
    }

    private fun findUnquotedColon(line: String): Int {
        var inQuotes = false
        for (i in line.indices) {
            val c = line[i]
            if (c == '"') inQuotes = !inQuotes
            else if (c == ':' && !inQuotes) return i
        }
        return -1
    }

    private fun splitEscaped(input: String, delimiter: Char): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var escaped = false
        for (c in input) {
            if (escaped) {
                cur.append('\\').append(c)
                escaped = false
            } else if (c == '\\') {
                escaped = true
            } else if (c == delimiter) {
                out.add(cur.toString())
                cur.clear()
            } else {
                cur.append(c)
            }
        }
        if (escaped) cur.append('\\')
        out.add(cur.toString())
        return out
    }

    private fun unescapeVCardText(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                val next = text[i + 1]
                when (next) {
                    'n', 'N' -> sb.append('\n')
                    '\\', ',', ';', ':' -> sb.append(next)
                    else -> sb.append(next)
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun escapeVCardText(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")
            .replace(";", "\\;")
            .replace(",", "\\,")
    }

    fun decodeQuotedPrintable(input: String): String {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '=' && i + 2 < input.length) {
                val hex = input.substring(i + 1, i + 3)
                val parsed = hex.toIntOrNull(16)
                if (parsed != null) {
                    out.write(parsed)
                    i += 3
                    continue
                }
            }
            val bytes = c.toString().toByteArray(StandardCharsets.UTF_8)
            out.write(bytes)
            i++
        }
        return out.toString(StandardCharsets.UTF_8.name())
    }

    fun writeVcfStream(
        contacts: Sequence<ContactDetail>,
        outputStream: OutputStream,
        photoLoader: (String) -> ByteArray? = { null },
        onProgress: (Int) -> Unit = {},
        isCancelled: () -> Boolean = { false }
    ): Int {
        val writer = BufferedWriter(OutputStreamWriter(outputStream, StandardCharsets.UTF_8), 64 * 1024)
        var written = 0
        for (c in contacts) {
            if (isCancelled()) break
            writer.write("BEGIN:VCARD\r\n")
            writer.write("VERSION:3.0\r\n")
            val uid = c.uid.ifBlank { UUID.randomUUID().toString() }
            writer.write("UID:$uid\r\n")
            val fn = c.displayName.ifBlank {
                listOf(c.firstName, c.lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Contact" }
            }
            writer.write("FN:${escapeVCardText(fn)}\r\n")
            writer.write("N:${escapeVCardText(c.lastName)};${escapeVCardText(c.firstName)};;;\r\n")

            for (phone in c.phones) {
                val typeToken = when (phone.typeLabel) {
                    "Mobile" -> "CELL"
                    "Travail" -> "WORK"
                    "Domicile" -> "HOME"
                    "Fax" -> "FAX"
                    else -> "VOICE"
                }
                writer.write("TEL;TYPE=$typeToken:${phone.number}\r\n")
            }
            for (email in c.emails) {
                val typeToken = when (email.typeLabel) {
                    "Travail" -> "WORK"
                    "Personnel" -> "HOME"
                    else -> "INTERNET"
                }
                writer.write("EMAIL;TYPE=INTERNET,$typeToken:${email.email}\r\n")
            }
            if (c.organization.isNotBlank()) {
                writer.write("ORG:${escapeVCardText(c.organization)}\r\n")
            }
            if (c.jobTitle.isNotBlank()) {
                writer.write("TITLE:${escapeVCardText(c.jobTitle)}\r\n")
            }
            if (c.birthday.isNotBlank()) {
                writer.write("BDAY:${escapeVCardText(c.birthday)}\r\n")
            }
            if (c.postalAddress.isNotBlank()) {
                writer.write("ADR;TYPE=HOME:;;${escapeVCardText(c.postalAddress)};;;;\r\n")
            }
            if (c.notes.isNotBlank()) {
                writer.write("NOTE:${escapeVCardText(c.notes)}\r\n")
            }
            if (c.groups.isNotEmpty()) {
                val cats = c.groups.joinToString(",") { escapeVCardText(it.name) }
                writer.write("CATEGORIES:$cats\r\n")
            }
            c.photoPath?.let { path ->
                val bytes = photoLoader(path)
                if (bytes != null && bytes.isNotEmpty()) {
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    writer.write("PHOTO;ENCODING=b;TYPE=JPEG:$b64\r\n")
                }
            }
            for ((k, v) in c.customFields) {
                if (k.startsWith("X-", ignoreCase = true) && v.isNotBlank()) {
                    writer.write("$k:${escapeVCardText(v)}\r\n")
                }
            }
            writer.write("END:VCARD\r\n")
            written++
            if (written % 250 == 0) {
                onProgress(written)
            }
        }
        writer.flush()
        onProgress(written)
        return written
    }

    fun writeCsvStream(
        contacts: Sequence<ContactDetail>,
        outputStream: OutputStream,
        onProgress: (Int) -> Unit = {},
        isCancelled: () -> Boolean = { false }
    ): Int {
        val writer = BufferedWriter(OutputStreamWriter(outputStream, StandardCharsets.UTF_8), 64 * 1024)
        // UTF-8 BOM for seamless Excel compatibility with Arabic, French, and Amazigh text
        writer.write("\uFEFF")
        writer.write("Nom complet;Prénom;Nom;Téléphones;E-mails;Entreprise;Fonction;Groupes;Anniversaire;Adresse;Notes\r\n")
        var written = 0
        for (c in contacts) {
            if (isCancelled()) break
            val row = listOf(
                c.displayName,
                c.firstName,
                c.lastName,
                c.phones.joinToString(" | ") { "${it.number} (${it.typeLabel})" },
                c.emails.joinToString(" | ") { "${it.email} (${it.typeLabel})" },
                c.organization,
                c.jobTitle,
                c.groups.joinToString(", ") { it.name },
                c.birthday,
                c.postalAddress,
                c.notes
            ).joinToString(";") { csvEscape(it) }
            writer.write(row)
            writer.write("\r\n")
            written++
            if (written % 250 == 0) onProgress(written)
        }
        writer.flush()
        onProgress(written)
        return written
    }

    private fun csvEscape(value: String): String {
        val needsQuotes = value.contains(';') || value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        if (!needsQuotes) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    fun writeJsonStream(
        contacts: Sequence<ContactDetail>,
        outputStream: OutputStream,
        photoLoader: (String) -> ByteArray? = { null },
        onProgress: (Int) -> Unit = {},
        isCancelled: () -> Boolean = { false }
    ): Int {
        val writer = BufferedWriter(OutputStreamWriter(outputStream, StandardCharsets.UTF_8), 64 * 1024)
        writer.write("{\n  \"app\": \"AA contacts\",\n  \"version\": 1,\n  \"exportedAt\": ${System.currentTimeMillis()},\n  \"contacts\": [\n")
        var written = 0
        for (c in contacts) {
            if (isCancelled()) break
            if (written > 0) writer.write(",\n")
            val obj = JSONObject().apply {
                put("uid", c.uid)
                put("firstName", c.firstName)
                put("lastName", c.lastName)
                put("displayName", c.displayName)
                put("organization", c.organization)
                put("jobTitle", c.jobTitle)
                put("birthday", c.birthday)
                put("postalAddress", c.postalAddress)
                put("notes", c.notes)
                put("phones", JSONArray().apply {
                    c.phones.forEach { p ->
                        put(JSONObject().apply {
                            put("number", p.number)
                            put("typeLabel", p.typeLabel)
                        })
                    }
                })
                put("emails", JSONArray().apply {
                    c.emails.forEach { e ->
                        put(JSONObject().apply {
                            put("email", e.email)
                            put("typeLabel", e.typeLabel)
                        })
                    }
                })
                put("groups", JSONArray().apply {
                    c.groups.forEach { put(it.name) }
                })
                if (c.customFields.isNotEmpty()) {
                    put("customFields", JSONObject(c.customFields))
                }
                c.photoPath?.let { path ->
                    val bytes = photoLoader(path)
                    if (bytes != null && bytes.isNotEmpty()) {
                        put("photoBase64", Base64.encodeToString(bytes, Base64.NO_WRAP))
                    }
                }
            }
            writer.write("    ")
            writer.write(obj.toString())
            written++
            if (written % 200 == 0) onProgress(written)
        }
        writer.write("\n  ]\n}\n")
        writer.flush()
        onProgress(written)
        return written
    }

    fun parseJsonBackup(inputStream: InputStream): List<ParsedContact> {
        val text = inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        val root = JSONObject(text)
        val arr = root.optJSONArray("contacts") ?: return emptyList()
        val out = ArrayList<ParsedContact>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val phonesArr = obj.optJSONArray("phones")
            val phones = ArrayList<PhoneEntry>()
            if (phonesArr != null) {
                for (j in 0 until phonesArr.length()) {
                    val po = phonesArr.optJSONObject(j) ?: continue
                    val num = po.optString("number")
                    if (num.isNotBlank()) phones.add(PhoneEntry(num, po.optString("typeLabel", "Mobile")))
                }
            }
            val emailsArr = obj.optJSONArray("emails")
            val emails = ArrayList<EmailEntry>()
            if (emailsArr != null) {
                for (j in 0 until emailsArr.length()) {
                    val eo = emailsArr.optJSONObject(j) ?: continue
                    val mail = eo.optString("email")
                    if (mail.isNotBlank()) emails.add(EmailEntry(mail, eo.optString("typeLabel", "Personnel")))
                }
            }
            val groupsArr = obj.optJSONArray("groups")
            val groups = ArrayList<String>()
            if (groupsArr != null) {
                for (j in 0 until groupsArr.length()) {
                    val g = groupsArr.optString(j)
                    if (g.isNotBlank()) groups.add(g)
                }
            }
            val customObj = obj.optJSONObject("customFields")
            val customMap = LinkedHashMap<String, String>()
            if (customObj != null) {
                val keys = customObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    customMap[k] = customObj.optString(k)
                }
            }
            val b64 = obj.optString("photoBase64", "")
            val photoBytes = if (b64.isNotBlank()) {
                try { Base64.decode(b64, Base64.DEFAULT) } catch (_: Throwable) { null }
            } else null

            out.add(
                ParsedContact(
                    uid = obj.optString("uid").ifBlank { UUID.randomUUID().toString() },
                    firstName = obj.optString("firstName"),
                    lastName = obj.optString("lastName"),
                    displayName = obj.optString("displayName"),
                    phones = phones,
                    emails = emails,
                    organization = obj.optString("organization"),
                    jobTitle = obj.optString("jobTitle"),
                    birthday = obj.optString("birthday"),
                    postalAddress = obj.optString("postalAddress"),
                    notes = obj.optString("notes"),
                    groups = groups,
                    photoBytes = photoBytes,
                    customFields = customMap
                )
            )
        }
        return out
    }
}
