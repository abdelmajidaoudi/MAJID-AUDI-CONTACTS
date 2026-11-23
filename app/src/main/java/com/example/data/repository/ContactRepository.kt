package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.data.local.AppDatabase
import com.example.data.local.ContactDao
import com.example.data.local.ContactEntity
import com.example.data.local.ContactGroupCrossRef
import com.example.data.local.EmailEntity
import com.example.data.local.GroupEntity
import com.example.data.local.PhoneEntity
import com.example.data.local.PhotoStorage
import com.example.domain.model.BackupRecord
import com.example.domain.model.ContactDetail
import com.example.domain.model.ContactFilter
import com.example.domain.model.ContactSummary
import com.example.domain.model.DuplicateCluster
import com.example.domain.model.DuplicateReason
import com.example.domain.model.EmailEntry
import com.example.domain.model.ExportFormat
import com.example.domain.model.GroupItem
import com.example.domain.model.MergePreviewSummary
import com.example.domain.model.MergeSourceFile
import com.example.domain.model.MergeStrategy
import com.example.domain.model.ParsedContact
import com.example.domain.model.PhoneEntry
import com.example.domain.model.SortOrder
import com.example.domain.util.AlphaHelper
import com.example.domain.util.PhoneNormalizer
import com.example.domain.vcard.VcfCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.absoluteValue

class ContactRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val photoStorage: PhotoStorage
) {
    private val dao: ContactDao = db.contactDao()

    private val backupsDir: File by lazy {
        File(context.filesDir, "backups").apply { if (!exists()) mkdirs() }
    }

    val totalCount: Flow<Int> = dao.observeTotalCount()
    val noPhoneCount: Flow<Int> = dao.observeNoPhoneCount()

    val groupsWithCounts: Flow<List<GroupItem>> = dao.observeGroupsWithCounts().map { rows ->
        rows.map {
            GroupItem(
                id = it.id,
                name = it.name,
                colorHex = it.colorHex,
                isSystemDefault = it.isSystemDefault,
                contactCount = it.contactCount
            )
        }
    }

    suspend fun getTotalCountNow(): Int = withContext(Dispatchers.IO) { dao.getTotalCount() }

    suspend fun queryContactsPage(
        searchQuery: String,
        filter: ContactFilter,
        groupId: Long?,
        firstLetter: String?,
        sortOrder: SortOrder,
        limit: Int,
        offset: Int
    ): Pair<List<ContactSummary>, Int> = withContext(Dispatchers.IO) {
        val (whereClause, args) = buildWhereClause(searchQuery, filter, groupId, firstLetter)
        val orderBy = when (sortOrder) {
            SortOrder.ALPHA_ASC -> "c.sortKey ASC, c.id ASC"
            SortOrder.ALPHA_DESC -> "c.sortKey DESC, c.id DESC"
            SortOrder.RECENT -> "c.updatedAt DESC, c.id DESC"
        }

        val countSql = "SELECT COUNT(*) FROM contacts c $whereClause"
        val total = dao.countFilteredContacts(SimpleSQLiteQuery(countSql, args.toTypedArray()))

        val dataSql = """
            SELECT c.id, c.displayName, c.firstName, c.lastName, c.firstLetter,
                   c.primaryPhone, c.primaryEmail, c.organization, c.photoPath,
                   c.groupNamesCache, c.phoneCount, c.emailCount, c.avatarColorSeed
            FROM contacts c
            $whereClause
            ORDER BY $orderBy
            LIMIT ? OFFSET ?
        """.trimIndent()
        val dataArgs = args + listOf(limit, offset)
        val rows = dao.queryContactsPage(SimpleSQLiteQuery(dataSql, dataArgs.toTypedArray()))

        val items = rows.map { r ->
            ContactSummary(
                id = r.id,
                displayName = r.displayName,
                firstName = r.firstName,
                lastName = r.lastName,
                firstLetter = r.firstLetter,
                primaryPhone = r.primaryPhone,
                primaryEmail = r.primaryEmail,
                organization = r.organization,
                photoPath = r.photoPath,
                groupNames = r.groupNamesCache,
                phoneCount = r.phoneCount,
                emailCount = r.emailCount,
                avatarColorSeed = r.avatarColorSeed
            )
        }
        items to total
    }

    private fun buildWhereClause(
        searchQuery: String,
        filter: ContactFilter,
        groupId: Long?,
        firstLetter: String?
    ): Pair<String, List<Any>> {
        val conditions = ArrayList<String>()
        val args = ArrayList<Any>()
        val joins = StringBuilder()

        if (groupId != null) {
            joins.append(" INNER JOIN contact_group_cross_ref gr ON gr.contactId = c.id ")
            conditions.add("gr.groupId = ?")
            args.add(groupId)
        }

        if (!firstLetter.isNullOrBlank()) {
            conditions.add("c.firstLetter = ?")
            args.add(firstLetter)
        }

        when (filter) {
            ContactFilter.ALL -> Unit
            ContactFilter.NO_PHONE -> conditions.add("c.phoneCount = 0")
            ContactFilter.WITH_PHOTO -> conditions.add("c.photoPath IS NOT NULL AND c.photoPath != ''")
            ContactFilter.WITH_EMAIL -> conditions.add("c.emailCount > 0")
            ContactFilter.DUPLICATES -> {
                conditions.add(
                    """
                    (
                      EXISTS (
                        SELECT 1 FROM phones p1
                        WHERE p1.contactId = c.id AND p1.tailKey != ''
                        AND EXISTS (
                          SELECT 1 FROM phones p2
                          WHERE p2.tailKey = p1.tailKey AND p2.contactId != c.id
                        )
                      )
                      OR (c.sortKey != '' AND c.sortKey != 'zzz' AND (SELECT COUNT(*) FROM contacts c2 WHERE c2.sortKey = c.sortKey) > 1)
                    )
                    """.trimIndent()
                )
            }
        }

        val trimmed = searchQuery.trim()
        if (trimmed.isNotEmpty()) {
            val digits = trimmed.filter { it.isDigit() }
            val ftsTokens = buildFtsTokens(trimmed)
            if (digits.length >= 3 && digits.length == trimmed.replace("+", "").replace(" ", "").replace("-", "").length) {
                // Pure phone search: indexed normalized match
                conditions.add(
                    "EXISTS (SELECT 1 FROM phones p WHERE p.contactId = c.id AND (p.normalizedNumber LIKE ? OR p.tailKey LIKE ?))"
                )
                args.add("%$digits%")
                args.add("%$digits%")
            } else if (ftsTokens.isNotEmpty()) {
                joins.append(" INNER JOIN contacts_fts fts ON fts.rowid = c.id ")
                conditions.add("contacts_fts MATCH ?")
                args.add(ftsTokens)
            } else {
                val pattern = "%${trimmed.lowercase()}%"
                conditions.add("c.searchBlob LIKE ?")
                args.add(pattern)
            }
        }

        val wherePart = if (conditions.isEmpty()) "" else " WHERE " + conditions.joinToString(" AND ")
        return joins.toString() + wherePart to args
    }

    private fun buildFtsTokens(raw: String): String {
        val tokens = raw.split(Regex("[\\s@.,;:+()\\-/]+"))
            .map { token ->
                token.filter { Character.isLetterOrDigit(it) }
            }
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return ""
        return tokens.joinToString(" ") { "$it*" }
    }

    suspend fun getContactDetail(contactId: Long): ContactDetail? = withContext(Dispatchers.IO) {
        val entity = dao.getContactById(contactId) ?: return@withContext null
        val phones = dao.getPhonesForContact(contactId).map { PhoneEntry(it.number, it.typeLabel) }
        val emails = dao.getEmailsForContact(contactId).map { EmailEntry(it.email, it.typeLabel) }
        val groups = dao.getGroupsForContact(contactId).map {
            GroupItem(it.id, it.name, it.colorHex, it.isSystemDefault)
        }
        ContactDetail(
            id = entity.id,
            uid = entity.uid,
            firstName = entity.firstName,
            lastName = entity.lastName,
            displayName = entity.displayName,
            phones = phones,
            emails = emails,
            organization = entity.organization,
            jobTitle = entity.jobTitle,
            birthday = entity.birthday,
            postalAddress = entity.postalAddress,
            notes = entity.notes,
            photoPath = entity.photoPath,
            groups = groups,
            customFields = decodeCustomFields(entity.customFieldsJson),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    suspend fun saveContact(
        detail: ContactDetail,
        newPhotoUri: Uri? = null,
        removePhoto: Boolean = false
    ): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = if (detail.id > 0L) dao.getContactById(detail.id) else null

        var finalPhotoPath = detail.photoPath
        if (removePhoto) {
            photoStorage.deletePhoto(existing?.photoPath)
            finalPhotoPath = null
        } else if (newPhotoUri != null) {
            val saved = photoStorage.savePhotoFromUri(newPhotoUri)
            if (saved != null) {
                if (existing?.photoPath != null && existing.photoPath != saved) {
                    photoStorage.deletePhoto(existing.photoPath)
                }
                finalPhotoPath = saved
            }
        }

        val resolvedName = detail.displayName.trim().ifEmpty {
            listOf(detail.firstName.trim(), detail.lastName.trim()).filter { it.isNotEmpty() }.joinToString(" ")
                .ifEmpty { detail.organization.trim() }
                .ifEmpty { detail.phones.firstOrNull()?.number ?: "" }
                .ifEmpty { detail.emails.firstOrNull()?.email ?: "" }
                .ifEmpty { "Contact sans nom" }
        }

        val cleanPhones = detail.phones.filter { it.number.isNotBlank() }
        val cleanEmails = detail.emails.filter { it.email.isNotBlank() }
        val groupIds = detail.groups.map { it.id }.distinct()
        val groupNames = detail.groups.joinToString(", ") { it.name }

        val firstPhone = cleanPhones.firstOrNull()?.number ?: ""
        val firstEmail = cleanEmails.firstOrNull()?.email ?: ""

        val entity = ContactEntity(
            id = detail.id,
            uid = detail.uid.ifBlank { existing?.uid ?: UUID.randomUUID().toString() },
            firstName = detail.firstName.trim(),
            lastName = detail.lastName.trim(),
            displayName = resolvedName,
            sortKey = AlphaHelper.computeSortKey(resolvedName),
            firstLetter = AlphaHelper.computeFirstLetter(resolvedName),
            primaryPhone = firstPhone,
            primaryPhoneNormalized = PhoneNormalizer.normalize(firstPhone),
            primaryPhoneTail = PhoneNormalizer.tailKey(firstPhone),
            primaryEmail = firstEmail,
            primaryEmailLower = firstEmail.trim().lowercase(),
            organization = detail.organization.trim(),
            jobTitle = detail.jobTitle.trim(),
            birthday = detail.birthday.trim(),
            postalAddress = detail.postalAddress.trim(),
            notes = detail.notes.trim(),
            photoPath = finalPhotoPath,
            groupNamesCache = groupNames,
            phoneCount = cleanPhones.size,
            emailCount = cleanEmails.size,
            searchBlob = buildSearchBlob(
                displayName = resolvedName,
                firstName = detail.firstName,
                lastName = detail.lastName,
                phones = cleanPhones,
                emails = cleanEmails,
                organization = detail.organization,
                jobTitle = detail.jobTitle,
                notes = detail.notes,
                groupNames = groupNames
            ),
            customFieldsJson = encodeCustomFields(detail.customFields),
            avatarColorSeed = existing?.avatarColorSeed ?: resolvedName.hashCode().absoluteValue,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )

        val phoneEntities = cleanPhones.mapIndexed { idx, p ->
            PhoneEntity(
                contactId = 0L,
                number = p.number.trim(),
                normalizedNumber = PhoneNormalizer.normalize(p.number),
                tailKey = PhoneNormalizer.tailKey(p.number),
                typeLabel = p.typeLabel,
                sortOrder = idx
            )
        }
        val emailEntities = cleanEmails.mapIndexed { idx, e ->
            EmailEntity(
                contactId = 0L,
                email = e.email.trim(),
                emailLower = e.email.trim().lowercase(),
                typeLabel = e.typeLabel,
                sortOrder = idx
            )
        }

        dao.upsertFullContact(entity, phoneEntities, emailEntities, groupIds)
    }

    suspend fun duplicateContact(contactId: Long): Long? = withContext(Dispatchers.IO) {
        val original = getContactDetail(contactId) ?: return@withContext null
        val copiedPhotoPath = original.photoPath?.let { path ->
            photoStorage.readPhotoBytes(path)?.let { photoStorage.savePhotoBytes(it) }
        }
        val copy = original.copy(
            id = 0L,
            uid = UUID.randomUUID().toString(),
            displayName = "${original.displayName} (copie)",
            photoPath = copiedPhotoPath
        )
        saveContact(copy)
    }

    suspend fun deleteContacts(contactIds: List<Long>, createSafetyBackup: Boolean = true) = withContext(Dispatchers.IO) {
        if (contactIds.isEmpty()) return@withContext
        if (createSafetyBackup && contactIds.size >= 3) {
            createLocalBackup(isAutomatic = true)
        }
        contactIds.chunked(500).forEach { chunk ->
            val entities = dao.getContactsByIds(chunk)
            entities.forEach { photoStorage.deletePhoto(it.photoPath) }
            dao.deleteContactsByIds(chunk)
        }
    }

    suspend fun batchAssignGroup(contactIds: List<Long>, groupId: Long) = withContext(Dispatchers.IO) {
        if (contactIds.isEmpty()) return@withContext
        val allGroups = dao.getAllGroups().associateBy { it.id }
        if (!allGroups.containsKey(groupId)) return@withContext
        val now = System.currentTimeMillis()
        db.withTransaction {
            contactIds.chunked(500).forEach { chunk ->
                dao.insertGroupRefs(chunk.map { ContactGroupCrossRef(it, groupId) })
                val entities = dao.getContactsByIds(chunk)
                val refs = dao.getGroupRefsForContacts(chunk).groupBy { it.contactId }
                val phonesMap = dao.getPhonesForContacts(chunk).groupBy { it.contactId }
                val emailsMap = dao.getEmailsForContacts(chunk).groupBy { it.contactId }
                for (c in entities) {
                    val gNames = refs[c.id].orEmpty().mapNotNull { allGroups[it.groupId]?.name }.joinToString(", ")
                    val phones = phonesMap[c.id].orEmpty().map { PhoneEntry(it.number, it.typeLabel) }
                    val emails = emailsMap[c.id].orEmpty().map { EmailEntry(it.email, it.typeLabel) }
                    val newBlob = buildSearchBlob(
                        c.displayName, c.firstName, c.lastName, phones, emails,
                        c.organization, c.jobTitle, c.notes, gNames
                    )
                    dao.updateContactGroupCache(c.id, gNames, newBlob, now)
                }
            }
        }
    }

    suspend fun batchUpdateOrganizationAndNote(
        contactIds: List<Long>,
        organization: String?,
        noteToAppend: String?
    ) = withContext(Dispatchers.IO) {
        if (contactIds.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        db.withTransaction {
            contactIds.chunked(300).forEach { chunk ->
                val entities = dao.getContactsByIds(chunk)
                val phonesMap = dao.getPhonesForContacts(chunk).groupBy { it.contactId }
                val emailsMap = dao.getEmailsForContacts(chunk).groupBy { it.contactId }
                for (c in entities) {
                    val newOrg = organization?.trim()?.takeIf { it.isNotEmpty() } ?: c.organization
                    val newNotes = if (!noteToAppend.isNullOrBlank()) {
                        if (c.notes.isBlank()) noteToAppend.trim() else "${c.notes}\n${noteToAppend.trim()}"
                    } else c.notes
                    val phones = phonesMap[c.id].orEmpty().map { PhoneEntry(it.number, it.typeLabel) }
                    val emails = emailsMap[c.id].orEmpty().map { EmailEntry(it.email, it.typeLabel) }
                    val updated = c.copy(
                        organization = newOrg,
                        notes = newNotes,
                        searchBlob = buildSearchBlob(
                            c.displayName, c.firstName, c.lastName, phones, emails,
                            newOrg, c.jobTitle, newNotes, c.groupNamesCache
                        ),
                        updatedAt = now
                    )
                    dao.updateContact(updated)
                }
            }
        }
    }

    suspend fun createGroup(name: String, colorHex: String): Long = withContext(Dispatchers.IO) {
        val clean = name.trim()
        if (clean.isEmpty()) return@withContext -1L
        val existing = dao.getGroupByName(clean)
        if (existing != null) return@withContext existing.id
        dao.insertGroup(
            GroupEntity(
                name = clean,
                colorHex = colorHex,
                isSystemDefault = false,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun renameGroup(groupId: Long, newName: String, newColorHex: String) = withContext(Dispatchers.IO) {
        val all = dao.getAllGroups()
        val target = all.find { it.id == groupId } ?: return@withContext
        val clean = newName.trim()
        if (clean.isEmpty()) return@withContext
        db.withTransaction {
            dao.updateGroup(target.copy(name = clean, colorHex = newColorHex))
            val affectedIds = dao.getContactIdsForGroup(groupId)
            val updatedGroupMap = dao.getAllGroups().associateBy { it.id }
            val now = System.currentTimeMillis()
            affectedIds.chunked(400).forEach { chunk ->
                val entities = dao.getContactsByIds(chunk)
                val refs = dao.getGroupRefsForContacts(chunk).groupBy { it.contactId }
                val phonesMap = dao.getPhonesForContacts(chunk).groupBy { it.contactId }
                val emailsMap = dao.getEmailsForContacts(chunk).groupBy { it.contactId }
                for (c in entities) {
                    val gNames = refs[c.id].orEmpty().mapNotNull { updatedGroupMap[it.groupId]?.name }.joinToString(", ")
                    val phones = phonesMap[c.id].orEmpty().map { PhoneEntry(it.number, it.typeLabel) }
                    val emails = emailsMap[c.id].orEmpty().map { EmailEntry(it.email, it.typeLabel) }
                    dao.updateContactGroupCache(
                        c.id,
                        gNames,
                        buildSearchBlob(
                            c.displayName, c.firstName, c.lastName, phones, emails,
                            c.organization, c.jobTitle, c.notes, gNames
                        ),
                        now
                    )
                }
            }
        }
    }

    suspend fun deleteGroup(groupId: Long) = withContext(Dispatchers.IO) {
        val affectedIds = dao.getContactIdsForGroup(groupId)
        db.withTransaction {
            dao.deleteGroupById(groupId)
            val remainingGroups = dao.getAllGroups().associateBy { it.id }
            val now = System.currentTimeMillis()
            affectedIds.chunked(400).forEach { chunk ->
                val entities = dao.getContactsByIds(chunk)
                val refs = dao.getGroupRefsForContacts(chunk).groupBy { it.contactId }
                val phonesMap = dao.getPhonesForContacts(chunk).groupBy { it.contactId }
                val emailsMap = dao.getEmailsForContacts(chunk).groupBy { it.contactId }
                for (c in entities) {
                    val gNames = refs[c.id].orEmpty().mapNotNull { remainingGroups[it.groupId]?.name }.joinToString(", ")
                    val phones = phonesMap[c.id].orEmpty().map { PhoneEntry(it.number, it.typeLabel) }
                    val emails = emailsMap[c.id].orEmpty().map { EmailEntry(it.email, it.typeLabel) }
                    dao.updateContactGroupCache(
                        c.id,
                        gNames,
                        buildSearchBlob(
                            c.displayName, c.firstName, c.lastName, phones, emails,
                            c.organization, c.jobTitle, c.notes, gNames
                        ),
                        now
                    )
                }
            }
        }
    }

    /**
     * Streaming VCF import supporting thousands or hundreds of thousands of contacts cleanly.
     */
    suspend fun importVcfStreams(
        uris: List<Uri>,
        onProgress: (imported: Int, fileIndex: Int, totalFiles: Int) -> Unit,
        isCancelled: () -> Boolean
    ): Int = withContext(Dispatchers.IO) {
        var totalImported = 0
        val groupCache = ensureGroupCache()

        uris.forEachIndexed { idx, uri ->
            if (isCancelled()) return@forEachIndexed
            context.contentResolver.openInputStream(uri)?.use { stream ->
                VcfCodec.parseVcfStream(
                    inputStream = stream,
                    isCancelled = isCancelled,
                    onBatch = { batch ->
                        if (isCancelled()) return@parseVcfStream
                        insertParsedBatch(batch, groupCache)
                        totalImported += batch.size
                        onProgress(totalImported, idx + 1, uris.size)
                    }
                )
            }
        }
        totalImported
    }

    suspend fun parseVcfForMergePreview(uri: Uri, fileName: String): MergeSourceFile = withContext(Dispatchers.IO) {
        val contacts = context.contentResolver.openInputStream(uri)?.use { stream ->
            VcfCodec.parseVcfAll(stream)
        } ?: emptyList()
        MergeSourceFile(
            id = UUID.randomUUID().toString(),
            fileName = fileName,
            uriString = uri.toString(),
            contacts = contacts,
            selectedIndices = contacts.indices.toSet()
        )
    }

    fun analyzeMergeCandidates(sources: List<MergeSourceFile>): MergePreviewSummary {
        val selected = sources.flatMap { src ->
            src.selectedIndices.sorted().mapNotNull { idx -> src.contacts.getOrNull(idx) }
        }
        val phoneMap = HashMap<String, ParsedContact>()
        val emailMap = HashMap<String, ParsedContact>()
        val nameMap = HashMap<String, ParsedContact>()
        val duplicatePairs = ArrayList<Pair<ParsedContact, ParsedContact>>()

        for (c in selected) {
            var matched: ParsedContact? = null
            for (p in c.phones) {
                val key = PhoneNormalizer.tailKey(p.number)
                if (key.isNotEmpty() && phoneMap.containsKey(key)) {
                    matched = phoneMap[key]
                    break
                }
            }
            if (matched == null) {
                for (e in c.emails) {
                    val key = e.email.trim().lowercase()
                    if (key.isNotEmpty() && emailMap.containsKey(key)) {
                        matched = emailMap[key]
                        break
                    }
                }
            }
            if (matched == null) {
                val nKey = AlphaHelper.computeSortKey(c.resolvedDisplayName())
                if (nKey.isNotEmpty() && nKey != "zzz" && nameMap.containsKey(nKey)) {
                    matched = nameMap[nKey]
                }
            }

            if (matched != null) {
                duplicatePairs.add(matched to c)
            } else {
                c.phones.forEach { p ->
                    val k = PhoneNormalizer.tailKey(p.number)
                    if (k.isNotEmpty()) phoneMap.putIfAbsent(k, c)
                }
                c.emails.forEach { e ->
                    val k = e.email.trim().lowercase()
                    if (k.isNotEmpty()) emailMap.putIfAbsent(k, c)
                }
                val nKey = AlphaHelper.computeSortKey(c.resolvedDisplayName())
                if (nKey.isNotEmpty() && nKey != "zzz") nameMap.putIfAbsent(nKey, c)
            }
        }

        val uniqueAfter = selected.size - duplicatePairs.size
        return MergePreviewSummary(
            totalBefore = selected.size,
            duplicatesDetected = duplicatePairs.size,
            estimatedAfter = uniqueAfter,
            duplicatePairs = duplicatePairs
        )
    }

    suspend fun mergeAndPrepareContacts(
        sources: List<MergeSourceFile>,
        strategy: MergeStrategy
    ): List<ParsedContact> = withContext(Dispatchers.Default) {
        val selected = sources.flatMap { src ->
            src.selectedIndices.sorted().mapNotNull { idx -> src.contacts.getOrNull(idx) }
        }
        if (strategy == MergeStrategy.KEEP_BOTH) return@withContext selected

        val result = ArrayList<ParsedContact>(selected.size)
        val phoneToIdx = HashMap<String, Int>()
        val emailToIdx = HashMap<String, Int>()
        val nameToIdx = HashMap<String, Int>()

        for (c in selected) {
            var matchIdx: Int? = null
            for (p in c.phones) {
                val k = PhoneNormalizer.tailKey(p.number)
                if (k.isNotEmpty() && phoneToIdx.containsKey(k)) {
                    matchIdx = phoneToIdx[k]
                    break
                }
            }
            if (matchIdx == null) {
                for (e in c.emails) {
                    val k = e.email.trim().lowercase()
                    if (k.isNotEmpty() && emailToIdx.containsKey(k)) {
                        matchIdx = emailToIdx[k]
                        break
                    }
                }
            }
            if (matchIdx == null) {
                val nKey = AlphaHelper.computeSortKey(c.resolvedDisplayName())
                if (nKey.isNotEmpty() && nKey != "zzz" && nameToIdx.containsKey(nKey)) {
                    matchIdx = nameToIdx[nKey]
                }
            }

            if (matchIdx == null) {
                val newIdx = result.size
                result.add(c)
                c.phones.forEach { p ->
                    val k = PhoneNormalizer.tailKey(p.number)
                    if (k.isNotEmpty()) phoneToIdx.putIfAbsent(k, newIdx)
                }
                c.emails.forEach { e ->
                    val k = e.email.trim().lowercase()
                    if (k.isNotEmpty()) emailToIdx.putIfAbsent(k, newIdx)
                }
                val nKey = AlphaHelper.computeSortKey(c.resolvedDisplayName())
                if (nKey.isNotEmpty() && nKey != "zzz") nameToIdx.putIfAbsent(nKey, newIdx)
            } else if (strategy == MergeStrategy.SMART_MERGE) {
                val existing = result[matchIdx]
                val merged = mergeTwoParsedContacts(existing, c)
                result[matchIdx] = merged
                merged.phones.forEach { p ->
                    val k = PhoneNormalizer.tailKey(p.number)
                    if (k.isNotEmpty()) phoneToIdx.putIfAbsent(k, matchIdx)
                }
                merged.emails.forEach { e ->
                    val k = e.email.trim().lowercase()
                    if (k.isNotEmpty()) emailToIdx.putIfAbsent(k, matchIdx)
                }
            }
            // If SKIP_DUPLICATE: do nothing
        }
        result
    }

    suspend fun importParsedContactsToDb(
        contacts: List<ParsedContact>,
        onProgress: (Int) -> Unit = {},
        isCancelled: () -> Boolean = { false }
    ): Int = withContext(Dispatchers.IO) {
        val groupCache = ensureGroupCache()
        var count = 0
        contacts.chunked(500).forEach { chunk ->
            if (isCancelled()) return@withContext count
            insertParsedBatch(chunk, groupCache)
            count += chunk.size
            onProgress(count)
        }
        count
    }

    suspend fun writeParsedContactsToVcfUri(
        contacts: List<ParsedContact>,
        outputUri: Uri,
        onProgress: (Int) -> Unit = {},
        isCancelled: () -> Boolean = { false }
    ): Int = withContext(Dispatchers.IO) {
        val tempDetails = contacts.asSequence().map { p ->
            ContactDetail(
                uid = p.uid,
                firstName = p.firstName,
                lastName = p.lastName,
                displayName = p.resolvedDisplayName(),
                phones = p.phones,
                emails = p.emails,
                organization = p.organization,
                jobTitle = p.jobTitle,
                birthday = p.birthday,
                postalAddress = p.postalAddress,
                notes = p.notes,
                groups = p.groups.map { GroupItem(0L, it, "#1D4ED8", false) },
                customFields = p.customFields
            )
        }
        context.contentResolver.openOutputStream(outputUri)?.use { os ->
            VcfCodec.writeVcfStream(
                contacts = tempDetails,
                outputStream = os,
                onProgress = onProgress,
                isCancelled = isCancelled
            )
        } ?: 0
    }

    suspend fun scanDuplicateClusters(limitPerCategory: Int = 60): List<DuplicateCluster> =
        withContext(Dispatchers.IO) {
            val clusters = ArrayList<DuplicateCluster>()
            val seenIdSets = HashSet<Set<Long>>()

            // 1. Phone tail duplicates
            val phoneKeys = dao.findDuplicatePhoneTailKeys(limitPerCategory)
            for (tail in phoneKeys) {
                val ids = dao.getContactIdsByPhoneTail(tail).distinct()
                if (ids.size > 1 && seenIdSets.add(ids.toSet())) {
                    val details = ids.mapNotNull { getContactDetail(it) }
                    if (details.size > 1) {
                        clusters.add(
                            DuplicateCluster(
                                clusterKey = "phone:$tail",
                                reason = DuplicateReason.PHONE,
                                matchValue = details.firstOrNull()?.phones?.firstOrNull()?.number ?: tail,
                                contacts = details
                            )
                        )
                    }
                }
            }

            // 2. Email duplicates
            val emailKeys = dao.findDuplicateEmailKeys(limitPerCategory)
            for (mail in emailKeys) {
                val ids = dao.getContactIdsByEmailLower(mail).distinct()
                if (ids.size > 1 && seenIdSets.add(ids.toSet())) {
                    val details = ids.mapNotNull { getContactDetail(it) }
                    if (details.size > 1) {
                        clusters.add(
                            DuplicateCluster(
                                clusterKey = "email:$mail",
                                reason = DuplicateReason.EMAIL,
                                matchValue = mail,
                                contacts = details
                            )
                        )
                    }
                }
            }

            // 3. Display name duplicates
            val nameKeys = dao.findDuplicateSortKeys(limitPerCategory)
            for (sk in nameKeys) {
                val ids = dao.getContactIdsBySortKey(sk).distinct()
                if (ids.size > 1 && seenIdSets.add(ids.toSet())) {
                    val details = ids.mapNotNull { getContactDetail(it) }
                    if (details.size > 1) {
                        clusters.add(
                            DuplicateCluster(
                                clusterKey = "name:$sk",
                                reason = DuplicateReason.NAME,
                                matchValue = details.first().displayName,
                                contacts = details
                            )
                        )
                    }
                }
            }

            clusters
        }

    suspend fun mergeDuplicateCluster(cluster: DuplicateCluster): Long = withContext(Dispatchers.IO) {
        if (cluster.contacts.size < 2) return@withContext cluster.contacts.firstOrNull()?.id ?: 0L
        createLocalBackup(isAutomatic = true)

        val primary = cluster.contacts.first()
        val allPhones = cluster.contacts.flatMap { it.phones }
            .distinctBy { PhoneNormalizer.normalize(it.number).ifEmpty { it.number } }
        val allEmails = cluster.contacts.flatMap { it.emails }
            .distinctBy { it.email.trim().lowercase() }
        val allGroups = cluster.contacts.flatMap { it.groups }.distinctBy { it.id }
        val mergedNotes = cluster.contacts.map { it.notes.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n---\n")
        val mergedCustom = LinkedHashMap<String, String>()
        cluster.contacts.forEach { mergedCustom.putAll(it.customFields) }

        val merged = primary.copy(
            firstName = cluster.contacts.firstNotNullOfOrNull { it.firstName.takeIf { s -> s.isNotBlank() } } ?: "",
            lastName = cluster.contacts.firstNotNullOfOrNull { it.lastName.takeIf { s -> s.isNotBlank() } } ?: "",
            displayName = cluster.contacts.firstNotNullOfOrNull { it.displayName.takeIf { s -> s.isNotBlank() } } ?: primary.displayName,
            phones = allPhones,
            emails = allEmails,
            organization = cluster.contacts.firstNotNullOfOrNull { it.organization.takeIf { s -> s.isNotBlank() } } ?: "",
            jobTitle = cluster.contacts.firstNotNullOfOrNull { it.jobTitle.takeIf { s -> s.isNotBlank() } } ?: "",
            birthday = cluster.contacts.firstNotNullOfOrNull { it.birthday.takeIf { s -> s.isNotBlank() } } ?: "",
            postalAddress = cluster.contacts.firstNotNullOfOrNull { it.postalAddress.takeIf { s -> s.isNotBlank() } } ?: "",
            notes = mergedNotes,
            photoPath = cluster.contacts.firstNotNullOfOrNull { it.photoPath },
            groups = allGroups,
            customFields = mergedCustom
        )

        val savedId = saveContact(merged)
        val toDelete = cluster.contacts.drop(1).map { it.id }
        deleteContacts(toDelete, createSafetyBackup = false)
        savedId
    }

    suspend fun exportContactsToUri(
        outputUri: Uri,
        format: ExportFormat,
        selectedIds: List<Long>? = null,
        groupId: Long? = null,
        onProgress: (Int, Int) -> Unit,
        isCancelled: () -> Boolean
    ): Int = withContext(Dispatchers.IO) {
        val total = when {
            selectedIds != null -> selectedIds.size
            groupId != null -> dao.getContactIdsForGroup(groupId).size
            else -> dao.getTotalCount()
        }
        val seq = streamContactDetails(selectedIds = selectedIds, groupId = groupId)
        context.contentResolver.openOutputStream(outputUri)?.use { os ->
            when (format) {
                ExportFormat.VCF_SINGLE, ExportFormat.VCF_BY_GROUP -> {
                    VcfCodec.writeVcfStream(
                        contacts = seq,
                        outputStream = os,
                        photoLoader = { path -> photoStorage.readPhotoBytes(path) },
                        onProgress = { n -> onProgress(n, total) },
                        isCancelled = isCancelled
                    )
                }
                ExportFormat.CSV -> {
                    VcfCodec.writeCsvStream(
                        contacts = seq,
                        outputStream = os,
                        onProgress = { n -> onProgress(n, total) },
                        isCancelled = isCancelled
                    )
                }
                ExportFormat.JSON -> {
                    VcfCodec.writeJsonStream(
                        contacts = seq,
                        outputStream = os,
                        photoLoader = { path -> photoStorage.readPhotoBytes(path) },
                        onProgress = { n -> onProgress(n, total) },
                        isCancelled = isCancelled
                    )
                }
            }
        } ?: 0
    }

    suspend fun exportVcfByGroupToFolder(
        treeUri: Uri,
        onProgress: (Int, Int) -> Unit,
        isCancelled: () -> Boolean
    ): Int = withContext(Dispatchers.IO) {
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
        val groups = dao.getAllGroups()
        var totalWritten = 0
        val totalContacts = dao.getTotalCount()

        for (group in groups) {
            if (isCancelled()) break
            val ids = dao.getContactIdsForGroup(group.id)
            if (ids.isEmpty()) continue
            val safeName = group.name.replace(Regex("[^a-zA-Z0-9_\\-\\u0600-\\u06FF ]"), "_")
            val fileName = "${safeName}.vcf"
            val docUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentDocUri,
                "text/x-vcard",
                fileName
            ) ?: continue
            context.contentResolver.openOutputStream(docUri)?.use { os ->
                val count = VcfCodec.writeVcfStream(
                    contacts = streamContactDetails(selectedIds = null, groupId = group.id),
                    outputStream = os,
                    photoLoader = { path -> photoStorage.readPhotoBytes(path) },
                    isCancelled = isCancelled
                )
                totalWritten += count
                onProgress(totalWritten.coerceAtMost(totalContacts.coerceAtLeast(1)), totalContacts.coerceAtLeast(1))
            }
        }
        totalWritten
    }

    private fun streamContactDetails(
        selectedIds: List<Long>? = null,
        groupId: Long? = null
    ): Sequence<ContactDetail> = sequence {
        val allGroupsMap = kotlinx.coroutines.runBlocking { dao.getAllGroups().associateBy { it.id } }
        val chunkSize = 400
        if (selectedIds != null) {
            for (chunk in selectedIds.chunked(chunkSize)) {
                val details = kotlinx.coroutines.runBlocking { loadDetailsChunk(chunk, allGroupsMap) }
                for (d in details) yield(d)
            }
        } else {
            var offset = 0
            while (true) {
                val entities = kotlinx.coroutines.runBlocking {
                    if (groupId != null) dao.getContactsBatchByGroup(groupId, chunkSize, offset)
                    else dao.getContactsBatch(chunkSize, offset)
                }
                if (entities.isEmpty()) break
                val details = kotlinx.coroutines.runBlocking { hydrateEntities(entities, allGroupsMap) }
                for (d in details) yield(d)
                offset += entities.size
                if (entities.size < chunkSize) break
            }
        }
    }

    private suspend fun loadDetailsChunk(
        ids: List<Long>,
        groupMap: Map<Long, GroupEntity>
    ): List<ContactDetail> {
        val entities = dao.getContactsByIds(ids)
        return hydrateEntities(entities, groupMap)
    }

    private suspend fun hydrateEntities(
        entities: List<ContactEntity>,
        groupMap: Map<Long, GroupEntity>
    ): List<ContactDetail> {
        if (entities.isEmpty()) return emptyList()
        val ids = entities.map { it.id }
        val phonesByContact = dao.getPhonesForContacts(ids).groupBy { it.contactId }
        val emailsByContact = dao.getEmailsForContacts(ids).groupBy { it.contactId }
        val refsByContact = dao.getGroupRefsForContacts(ids).groupBy { it.contactId }

        return entities.map { e ->
            ContactDetail(
                id = e.id,
                uid = e.uid,
                firstName = e.firstName,
                lastName = e.lastName,
                displayName = e.displayName,
                phones = phonesByContact[e.id].orEmpty().map { PhoneEntry(it.number, it.typeLabel) },
                emails = emailsByContact[e.id].orEmpty().map { EmailEntry(it.email, it.typeLabel) },
                organization = e.organization,
                jobTitle = e.jobTitle,
                birthday = e.birthday,
                postalAddress = e.postalAddress,
                notes = e.notes,
                photoPath = e.photoPath,
                groups = refsByContact[e.id].orEmpty().mapNotNull { ref ->
                    groupMap[ref.groupId]?.let { g ->
                        GroupItem(g.id, g.name, g.colorHex, g.isSystemDefault)
                    }
                },
                customFields = decodeCustomFields(e.customFieldsJson),
                createdAt = e.createdAt,
                updatedAt = e.updatedAt
            )
        }
    }

    // Backups & Restore
    suspend fun createLocalBackup(isAutomatic: Boolean = false): BackupRecord? = withContext(Dispatchers.IO) {
        try {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(Date())
            val prefix = if (isAutomatic) "auto_backup_" else "majid_backup_"
            val file = File(backupsDir, "$prefix$stamp.json.gz")
            GZIPOutputStream(FileOutputStream(file)).use { gz ->
                VcfCodec.writeJsonStream(
                    contacts = streamContactDetails(),
                    outputStream = gz,
                    photoLoader = { path -> photoStorage.readPhotoBytes(path) }
                )
            }
            // Keep max 5 automatic backups
            if (isAutomatic) {
                val autos = backupsDir.listFiles { f -> f.name.startsWith("auto_backup_") }
                    ?.sortedByDescending { it.lastModified() } ?: emptyList()
                autos.drop(5).forEach { it.delete() }
            }
            BackupRecord(
                fileName = file.name,
                filePath = file.absolutePath,
                timestamp = file.lastModified(),
                sizeBytes = file.length(),
                isAutomatic = isAutomatic
            )
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun listLocalBackups(): List<BackupRecord> = withContext(Dispatchers.IO) {
        backupsDir.listFiles()
            ?.filter { it.isFile && (it.name.endsWith(".json.gz") || it.name.endsWith(".json")) }
            ?.sortedByDescending { it.lastModified() }
            ?.map { f ->
                BackupRecord(
                    fileName = f.name,
                    filePath = f.absolutePath,
                    timestamp = f.lastModified(),
                    sizeBytes = f.length(),
                    isAutomatic = f.name.startsWith("auto_backup_")
                )
            } ?: emptyList()
    }

    suspend fun restoreFromBackupFile(filePath: String, replaceExisting: Boolean): Int =
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            if (!file.exists()) return@withContext 0
            FileInputStream(file).use { fis ->
                val stream: InputStream = if (file.name.endsWith(".gz")) GZIPInputStream(fis) else fis
                restoreFromJsonStream(stream, replaceExisting)
            }
        }

    suspend fun restoreFromBackupUri(uri: Uri, replaceExisting: Boolean): Int =
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                restoreFromJsonStream(stream, replaceExisting)
            } ?: 0
        }

    private suspend fun restoreFromJsonStream(stream: InputStream, replaceExisting: Boolean): Int {
        val parsed = VcfCodec.parseJsonBackup(stream)
        if (replaceExisting) {
            photoStorage.clearAllPhotos()
            dao.deleteAllContacts()
        }
        return importParsedContactsToDb(parsed)
    }

    /**
     * Generates synthetic benchmark contacts in fast batched transactions to verify 100,000+ contact performance.
     */
    suspend fun generateSyntheticContacts(
        count: Int,
        onProgress: (Int, Int) -> Unit,
        isCancelled: () -> Boolean
    ): Int = withContext(Dispatchers.IO) {
        val firstNames = listOf(
            "Majid", "Youssef", "Fatima", "Salma", "Karim", "Nadia", "Amine", "Kenza",
            "Mehdi", "Leila", "Omar", "Imane", "Rachid", "Sonia", "Tarik", "Amina",
            "محمد", "فاطمة", "يوسف", "سلمى", "كريم", "نادية", "أمين", "كنزة"
        )
        val lastNames = listOf(
            "Aoudi", "Benali", "El Amrani", "Tazi", "Berrada", "Chraibi", "Idrissi", "Bennani",
            "Alaoui", "Fassi", "Mansouri", "Ziani", "Kettani", "Naciri", "Ouazzani",
            "العوادي", "بنعلي", "العمراني", "الإدريسي", "المنصوري"
        )
        val orgs = listOf("Majid Tech", "Atlas Solutions", "Maghreb Digital", "Casablanca Finance", "Rabat Consulting", "")
        val groupCache = ensureGroupCache()
        val groupNamesList = groupCache.keys.toList()

        val batchSize = 1000
        var generated = 0
        while (generated < count && !isCancelled()) {
            val toCreate = minOf(batchSize, count - generated)
            val batch = ArrayList<ParsedContact>(toCreate)
            for (i in 0 until toCreate) {
                val idx = generated + i + 1
                val fn = firstNames[idx % firstNames.size]
                val ln = lastNames[(idx / firstNames.size) % lastNames.size]
                val phone = String.format(Locale.US, "+212 6%02d %02d %02d %02d", (idx % 90) + 10, (idx / 100) % 100, (idx / 1000) % 100, idx % 100)
                val email = "${fn.lowercase().filter { it in 'a'..'z' }.ifEmpty { "contact" }}.$idx@exemple.ma"
                val group = if (groupNamesList.isNotEmpty()) listOf(groupNamesList[idx % groupNamesList.size]) else emptyList()
                batch.add(
                    ParsedContact(
                        uid = UUID.randomUUID().toString(),
                        firstName = fn,
                        lastName = ln,
                        displayName = "$fn $ln #$idx",
                        phones = listOf(PhoneEntry(phone, "Mobile")),
                        emails = listOf(EmailEntry(email, "Travail")),
                        organization = orgs[idx % orgs.size],
                        groups = group
                    )
                )
            }
            insertParsedBatch(batch, groupCache)
            generated += toCreate
            onProgress(generated, count)
        }
        generated
    }

    suspend fun clearAllContacts() = withContext(Dispatchers.IO) {
        createLocalBackup(isAutomatic = true)
        photoStorage.clearAllPhotos()
        dao.deleteAllContacts()
    }

    private suspend fun ensureGroupCache(): MutableMap<String, GroupEntity> {
        val map = LinkedHashMap<String, GroupEntity>()
        dao.getAllGroups().forEach { map[it.name.lowercase()] = it }
        return map
    }

    private suspend fun insertParsedBatch(
        batch: List<ParsedContact>,
        groupCache: MutableMap<String, GroupEntity>
    ) {
        val now = System.currentTimeMillis()
        val groupPalette = listOf("#1D4ED8", "#7C3AED", "#0891B2", "#2563EB", "#9333EA", "#0D9488")

        db.withTransaction {
            // Ensure any new group names from CATEGORIES exist
            for (p in batch) {
                for (gName in p.groups) {
                    val key = gName.trim().lowercase()
                    if (key.isNotEmpty() && !groupCache.containsKey(key)) {
                        val color = groupPalette[groupCache.size % groupPalette.size]
                        val newId = dao.insertGroup(
                            GroupEntity(
                                name = gName.trim(),
                                colorHex = color,
                                isSystemDefault = false,
                                createdAt = now
                            )
                        )
                        val resolved = if (newId > 0L) {
                            GroupEntity(newId, gName.trim(), color, false, now)
                        } else {
                            dao.getGroupByName(gName.trim())
                        }
                        if (resolved != null) groupCache[key] = resolved
                    }
                }
            }

            val entities = ArrayList<ContactEntity>(batch.size)
            val batchGroups = ArrayList<List<GroupEntity>>(batch.size)

            for (p in batch) {
                val resolvedName = p.resolvedDisplayName()
                val photoPath = p.photoBytes?.let { photoStorage.savePhotoBytes(it) }
                val firstPhone = p.phones.firstOrNull()?.number ?: ""
                val firstEmail = p.emails.firstOrNull()?.email ?: ""
                val matchedGroups = p.groups.mapNotNull { groupCache[it.trim().lowercase()] }.distinctBy { it.id }
                val groupNamesStr = matchedGroups.joinToString(", ") { it.name }
                batchGroups.add(matchedGroups)

                entities.add(
                    ContactEntity(
                        uid = p.uid.ifBlank { UUID.randomUUID().toString() },
                        firstName = p.firstName.trim(),
                        lastName = p.lastName.trim(),
                        displayName = resolvedName,
                        sortKey = AlphaHelper.computeSortKey(resolvedName),
                        firstLetter = AlphaHelper.computeFirstLetter(resolvedName),
                        primaryPhone = firstPhone,
                        primaryPhoneNormalized = PhoneNormalizer.normalize(firstPhone),
                        primaryPhoneTail = PhoneNormalizer.tailKey(firstPhone),
                        primaryEmail = firstEmail,
                        primaryEmailLower = firstEmail.trim().lowercase(),
                        organization = p.organization.trim(),
                        jobTitle = p.jobTitle.trim(),
                        birthday = p.birthday.trim(),
                        postalAddress = p.postalAddress.trim(),
                        notes = p.notes.trim(),
                        photoPath = photoPath,
                        groupNamesCache = groupNamesStr,
                        phoneCount = p.phones.size,
                        emailCount = p.emails.size,
                        searchBlob = buildSearchBlob(
                            resolvedName, p.firstName, p.lastName, p.phones, p.emails,
                            p.organization, p.jobTitle, p.notes, groupNamesStr
                        ),
                        customFieldsJson = encodeCustomFields(p.customFields),
                        avatarColorSeed = resolvedName.hashCode().absoluteValue,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }

            val insertedIds = dao.insertContacts(entities)
            val allPhones = ArrayList<PhoneEntity>(batch.size)
            val allEmails = ArrayList<EmailEntity>(batch.size)
            val allGroupRefs = ArrayList<ContactGroupCrossRef>(batch.size)

            for (idx in batch.indices) {
                val newId = insertedIds[idx]
                val p = batch[idx]
                p.phones.forEachIndexed { pIdx, ph ->
                    allPhones.add(
                        PhoneEntity(
                            contactId = newId,
                            number = ph.number.trim(),
                            normalizedNumber = PhoneNormalizer.normalize(ph.number),
                            tailKey = PhoneNormalizer.tailKey(ph.number),
                            typeLabel = ph.typeLabel,
                            sortOrder = pIdx
                        )
                    )
                }
                p.emails.forEachIndexed { eIdx, em ->
                    allEmails.add(
                        EmailEntity(
                            contactId = newId,
                            email = em.email.trim(),
                            emailLower = em.email.trim().lowercase(),
                            typeLabel = em.typeLabel,
                            sortOrder = eIdx
                        )
                    )
                }
                batchGroups[idx].forEach { grp ->
                    allGroupRefs.add(ContactGroupCrossRef(newId, grp.id))
                }
            }

            if (allPhones.isNotEmpty()) dao.insertPhones(allPhones)
            if (allEmails.isNotEmpty()) dao.insertEmails(allEmails)
            if (allGroupRefs.isNotEmpty()) dao.insertGroupRefs(allGroupRefs)
        }
    }

    private fun mergeTwoParsedContacts(a: ParsedContact, b: ParsedContact): ParsedContact {
        val phones = (a.phones + b.phones).distinctBy {
            PhoneNormalizer.tailKey(it.number).ifEmpty { it.number }
        }
        val emails = (a.emails + b.emails).distinctBy { it.email.trim().lowercase() }
        val groups = (a.groups + b.groups).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val notes = listOf(a.notes.trim(), b.notes.trim()).filter { it.isNotEmpty() }.distinct().joinToString("\n")
        val custom = LinkedHashMap<String, String>().apply {
            putAll(a.customFields)
            putAll(b.customFields)
        }
        return a.copy(
            firstName = a.firstName.ifBlank { b.firstName },
            lastName = a.lastName.ifBlank { b.lastName },
            displayName = a.resolvedDisplayName(),
            phones = phones,
            emails = emails,
            organization = a.organization.ifBlank { b.organization },
            jobTitle = a.jobTitle.ifBlank { b.jobTitle },
            birthday = a.birthday.ifBlank { b.birthday },
            postalAddress = a.postalAddress.ifBlank { b.postalAddress },
            notes = notes,
            groups = groups,
            photoBytes = a.photoBytes ?: b.photoBytes,
            customFields = custom
        )
    }

    private fun buildSearchBlob(
        displayName: String,
        firstName: String,
        lastName: String,
        phones: List<PhoneEntry>,
        emails: List<EmailEntry>,
        organization: String,
        jobTitle: String,
        notes: String,
        groupNames: String
    ): String {
        val phoneTokens = phones.flatMap {
            listOf(it.number, PhoneNormalizer.normalize(it.number), PhoneNormalizer.tailKey(it.number))
        }
        val emailTokens = emails.map { it.email }
        return listOf(
            displayName,
            firstName,
            lastName,
            AlphaHelper.computeSortKey(displayName),
            phoneTokens.joinToString(" "),
            emailTokens.joinToString(" "),
            organization,
            jobTitle,
            groupNames,
            notes
        ).filter { it.isNotBlank() }.joinToString(" ").lowercase()
    }

    private fun encodeCustomFields(fields: Map<String, String>): String {
        if (fields.isEmpty()) return ""
        return JSONObject(fields).toString()
    }

    private fun decodeCustomFields(json: String): Map<String, String> {
        if (json.isBlank()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            val map = LinkedHashMap<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.optString(k)
            }
            map
        } catch (_: Throwable) {
            emptyMap()
        }
    }
}
