package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Query("SELECT COUNT(*) FROM contacts")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM contacts")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM contacts WHERE phoneCount = 0")
    fun observeNoPhoneCount(): Flow<Int>

    @Query(
        """
        SELECT g.id, g.name, g.colorHex, g.isSystemDefault,
               COUNT(ref.contactId) AS contactCount
        FROM groups g
        LEFT JOIN contact_group_cross_ref ref ON g.id = ref.groupId
        GROUP BY g.id
        ORDER BY g.isSystemDefault DESC, g.name COLLATE NOCASE ASC
        """
    )
    fun observeGroupsWithCounts(): Flow<List<GroupWithCountRow>>

    @Query("SELECT * FROM groups ORDER BY isSystemDefault DESC, name COLLATE NOCASE ASC")
    suspend fun getAllGroups(): List<GroupEntity>

    @Query("SELECT * FROM groups WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getGroupByName(name: String): GroupEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGroup(group: GroupEntity): Long

    @Update
    suspend fun updateGroup(group: GroupEntity)

    @Query("DELETE FROM groups WHERE id = :groupId")
    suspend fun deleteGroupById(groupId: Long)

    @RawQuery
    suspend fun queryContactsPage(query: SupportSQLiteQuery): List<ContactSummaryRow>

    @RawQuery
    suspend fun countFilteredContacts(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
    suspend fun getContactById(id: Long): ContactEntity?

    @Query("SELECT * FROM contacts WHERE id IN (:ids)")
    suspend fun getContactsByIds(ids: List<Long>): List<ContactEntity>

    @Query("SELECT * FROM contacts ORDER BY sortKey ASC LIMIT :limit OFFSET :offset")
    suspend fun getContactsBatch(limit: Int, offset: Int): List<ContactEntity>

    @Query(
        """
        SELECT c.* FROM contacts c
        INNER JOIN contact_group_cross_ref ref ON c.id = ref.contactId
        WHERE ref.groupId = :groupId
        ORDER BY c.sortKey ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getContactsBatchByGroup(groupId: Long, limit: Int, offset: Int): List<ContactEntity>

    @Query("SELECT * FROM phones WHERE contactId = :contactId ORDER BY sortOrder ASC")
    suspend fun getPhonesForContact(contactId: Long): List<PhoneEntity>

    @Query("SELECT * FROM phones WHERE contactId IN (:contactIds) ORDER BY contactId, sortOrder ASC")
    suspend fun getPhonesForContacts(contactIds: List<Long>): List<PhoneEntity>

    @Query("SELECT * FROM emails WHERE contactId = :contactId ORDER BY sortOrder ASC")
    suspend fun getEmailsForContact(contactId: Long): List<EmailEntity>

    @Query("SELECT * FROM emails WHERE contactId IN (:contactIds) ORDER BY contactId, sortOrder ASC")
    suspend fun getEmailsForContacts(contactIds: List<Long>): List<EmailEntity>

    @Query(
        """
        SELECT g.* FROM groups g
        INNER JOIN contact_group_cross_ref ref ON g.id = ref.groupId
        WHERE ref.contactId = :contactId
        ORDER BY g.name COLLATE NOCASE ASC
        """
    )
    suspend fun getGroupsForContact(contactId: Long): List<GroupEntity>

    @Query("SELECT contactId, groupId FROM contact_group_cross_ref WHERE contactId IN (:contactIds)")
    suspend fun getGroupRefsForContacts(contactIds: List<Long>): List<ContactGroupCrossRef>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<ContactEntity>): List<Long>

    @Update
    suspend fun updateContact(contact: ContactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhones(phones: List<PhoneEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmails(emails: List<EmailEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGroupRefs(refs: List<ContactGroupCrossRef>)

    @Query("DELETE FROM phones WHERE contactId = :contactId")
    suspend fun deletePhonesForContact(contactId: Long)

    @Query("DELETE FROM emails WHERE contactId = :contactId")
    suspend fun deleteEmailsForContact(contactId: Long)

    @Query("DELETE FROM contact_group_cross_ref WHERE contactId = :contactId")
    suspend fun deleteGroupRefsForContact(contactId: Long)

    @Query("DELETE FROM contacts WHERE id IN (:ids)")
    suspend fun deleteContactsByIds(ids: List<Long>)

    @Query("DELETE FROM contacts")
    suspend fun deleteAllContacts()

    @Query("SELECT DISTINCT contactId FROM phones WHERE tailKey IN (:tailKeys)")
    suspend fun findContactIdsByPhoneTails(tailKeys: List<String>): List<Long>

    @Query("SELECT DISTINCT contactId FROM emails WHERE emailLower IN (:emailsLower)")
    suspend fun findContactIdsByEmails(emailsLower: List<String>): List<Long>

    @Query("SELECT id FROM contacts WHERE sortKey IN (:sortKeys)")
    suspend fun findContactIdsBySortKeys(sortKeys: List<String>): List<Long>

    // Fast SQL Duplicate Cluster Keys
    @Query("SELECT tailKey FROM phones WHERE tailKey != '' GROUP BY tailKey HAVING COUNT(DISTINCT contactId) > 1 LIMIT :limit")
    suspend fun findDuplicatePhoneTailKeys(limit: Int = 80): List<String>

    @Query("SELECT emailLower FROM emails WHERE emailLower != '' GROUP BY emailLower HAVING COUNT(DISTINCT contactId) > 1 LIMIT :limit")
    suspend fun findDuplicateEmailKeys(limit: Int = 80): List<String>

    @Query("SELECT sortKey FROM contacts WHERE sortKey != '' AND sortKey != 'zzz' GROUP BY sortKey HAVING COUNT(*) > 1 LIMIT :limit")
    suspend fun findDuplicateSortKeys(limit: Int = 80): List<String>

    @Query("SELECT DISTINCT contactId FROM phones WHERE tailKey = :tailKey")
    suspend fun getContactIdsByPhoneTail(tailKey: String): List<Long>

    @Query("SELECT DISTINCT contactId FROM emails WHERE emailLower = :emailLower")
    suspend fun getContactIdsByEmailLower(emailLower: String): List<Long>

    @Query("SELECT id FROM contacts WHERE sortKey = :sortKey")
    suspend fun getContactIdsBySortKey(sortKey: String): List<Long>

    @Query("SELECT contactId FROM contact_group_cross_ref WHERE groupId = :groupId")
    suspend fun getContactIdsForGroup(groupId: Long): List<Long>

    @Query("UPDATE contacts SET groupNamesCache = :groupNamesCache, searchBlob = :searchBlob, updatedAt = :updatedAt WHERE id = :contactId")
    suspend fun updateContactGroupCache(contactId: Long, groupNamesCache: String, searchBlob: String, updatedAt: Long)

    @Transaction
    suspend fun upsertFullContact(
        contact: ContactEntity,
        phones: List<PhoneEntity>,
        emails: List<EmailEntity>,
        groupIds: List<Long>
    ): Long {
        val contactId = if (contact.id == 0L) {
            insertContact(contact)
        } else {
            updateContact(contact)
            deletePhonesForContact(contact.id)
            deleteEmailsForContact(contact.id)
            deleteGroupRefsForContact(contact.id)
            contact.id
        }
        if (phones.isNotEmpty()) {
            insertPhones(phones.mapIndexed { idx, p -> p.copy(id = 0L, contactId = contactId, sortOrder = idx) })
        }
        if (emails.isNotEmpty()) {
            insertEmails(emails.mapIndexed { idx, e -> e.copy(id = 0L, contactId = contactId, sortOrder = idx) })
        }
        if (groupIds.isNotEmpty()) {
            insertGroupRefs(groupIds.distinct().map { ContactGroupCrossRef(contactId = contactId, groupId = it) })
        }
        return contactId
    }
}
