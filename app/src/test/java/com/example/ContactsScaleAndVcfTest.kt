package com.example

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.ContactEntity
import com.example.data.local.PhoneEntity
import com.example.domain.model.ContactDetail
import com.example.domain.model.EmailEntry
import com.example.domain.model.GroupItem
import com.example.domain.model.PhoneEntry
import com.example.domain.util.AlphaHelper
import com.example.domain.util.PhoneNormalizer
import com.example.domain.vcard.VcfCodec
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.system.measureTimeMillis

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ContactsScaleAndVcfTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `parse vCard 2_1 quoted-printable Arabic and French plus vCard 3_0 and 4_0`() = runBlocking {
        val vcfSample = """
            BEGIN:VCARD
            VERSION:2.1
            N;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:=C3=89lodie;=41=6D=61=7A=69=67=68;;;
            FN;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:=C3=89lodie =D9=85=D8=AC=D9=8A=D8=AF
            TEL;CELL:+212 6 12 34 56 78
            TEL;WORK:05 22 98 76 54
            EMAIL;INTERNET:elodie@exemple.ma
            CATEGORIES:Famille,Clients
            NOTE;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:Note sur=0Adeux lignes
            END:VCARD
            BEGIN:VCARD
            VERSION:4.0
            FN:ⵜⴰⵎⴰⵣⵉⵖⵜ Amazigh Contact
            N:Amazigh;ⵜⴰⵎⴰⵣⵉⵖⵜ;;;
            TEL;TYPE=cell:0612345678
            ORG:Atlas Tech
            TITLE:Directeur
            X-CUSTOM-TAG:VIP-2026
            END:VCARD
        """.trimIndent()

        val parsed = VcfCodec.parseVcfAll(ByteArrayInputStream(vcfSample.toByteArray(StandardCharsets.UTF_8)))
        assertEquals(2, parsed.size)

        val first = parsed[0]
        assertTrue(first.displayName.contains("مجيد"))
        assertEquals(2, first.phones.size)
        assertEquals(listOf("Famille", "Clients"), first.groups)
        assertTrue(first.notes.contains("deux lignes"))

        val second = parsed[1]
        assertTrue(second.displayName.contains("ⵜⴰⵎⴰⵣⵉⵖⵜ"))
        assertEquals("Atlas Tech", second.organization)
        assertEquals("VIP-2026", second.customFields["X-CUSTOM-TAG"])
    }

    @Test
    fun `phone normalization detects local and international equivalents`() {
        val a = PhoneNormalizer.tailKey("06 12-34.56.78")
        val b = PhoneNormalizer.tailKey("+212 612 345 678")
        val c = PhoneNormalizer.tailKey("00212 (0)612345678")
        assertEquals(a, b)
        assertEquals(b, c)
    }

    @Test
    fun `alpha helper normalizes French accents and Arabic letters`() {
        assertEquals("E", AlphaHelper.computeFirstLetter("Élodie"))
        assertEquals("C", AlphaHelper.computeFirstLetter("Çedric"))
        assertEquals("ا", AlphaHelper.computeFirstLetter("أحمد"))
        assertEquals("ا", AlphaHelper.computeFirstLetter("إبراهيم"))
        assertEquals("#", AlphaHelper.computeFirstLetter("0612345678"))
    }

    @Test
    fun `round-trip VCF, CSV and JSON export preserves fields`() = runBlocking {
        val detail = ContactDetail(
            id = 1L,
            uid = "uid-1",
            firstName = "Majid",
            lastName = "Aoudi",
            displayName = "Majid Aoudi",
            phones = listOf(PhoneEntry("+212612345678", "Mobile")),
            emails = listOf(EmailEntry("majid@exemple.ma", "Travail")),
            organization = "Majid Tech",
            notes = "Contact clé",
            groups = listOf(GroupItem(1L, "Travail", "#1D4ED8", true))
        )

        val vcfOut = ByteArrayOutputStream()
        VcfCodec.writeVcfStream(sequenceOf(detail), vcfOut)
        val reParsedVcf = VcfCodec.parseVcfAll(ByteArrayInputStream(vcfOut.toByteArray()))
        assertEquals(1, reParsedVcf.size)
        assertEquals("Majid Aoudi", reParsedVcf[0].displayName)
        assertEquals("Travail", reParsedVcf[0].groups.first())

        val csvOut = ByteArrayOutputStream()
        VcfCodec.writeCsvStream(sequenceOf(detail), csvOut)
        val csvText = csvOut.toString(StandardCharsets.UTF_8.name())
        assertTrue(csvText.startsWith("\uFEFF"))
        assertTrue(csvText.contains("Majid Aoudi"))

        val jsonOut = ByteArrayOutputStream()
        VcfCodec.writeJsonStream(sequenceOf(detail), jsonOut)
        val restored = VcfCodec.parseJsonBackup(ByteArrayInputStream(jsonOut.toByteArray()))
        assertEquals(1, restored.size)
        assertEquals("Majid Tech", restored[0].organization)
    }

    @Test
    fun `scale test inserting, searching and paginating 100000 contacts in SQLite Room`() = runBlocking {
        val dao = db.contactDao()
        val totalToInsert = 100_000
        val batchSize = 2_500
        val now = System.currentTimeMillis()

        val insertMs = measureTimeMillis {
            for (start in 0 until totalToInsert step batchSize) {
                db.withTransaction {
                    val contactBatch = ArrayList<ContactEntity>(batchSize)
                    val phoneRawBatch = ArrayList<String>(batchSize)
                    for (i in start until (start + batchSize)) {
                        val name = if (i == 77_777) "Majid Aoudi Spécial" else "Contact Test #$i"
                        val phone = String.format(Locale.US, "+2126%08d", i)
                        phoneRawBatch.add(phone)
                        contactBatch.add(
                            ContactEntity(
                                uid = "uid-$i",
                                firstName = "Contact",
                                lastName = "#$i",
                                displayName = name,
                                sortKey = AlphaHelper.computeSortKey(name),
                                firstLetter = AlphaHelper.computeFirstLetter(name),
                                primaryPhone = phone,
                                primaryPhoneNormalized = phone,
                                primaryPhoneTail = PhoneNormalizer.tailKey(phone),
                                primaryEmail = "user$i@test.ma",
                                primaryEmailLower = "user$i@test.ma",
                                organization = if (i % 10 == 0) "Atlas Corp" else "",
                                jobTitle = "",
                                birthday = "",
                                postalAddress = "",
                                notes = "",
                                photoPath = null,
                                groupNamesCache = "Clients",
                                phoneCount = 1,
                                emailCount = 1,
                                searchBlob = "${name.lowercase()} $phone ${PhoneNormalizer.tailKey(phone)} user$i@test.ma clients",
                                customFieldsJson = "",
                                avatarColorSeed = i,
                                createdAt = now,
                                updatedAt = now
                            )
                        )
                    }
                    val ids = dao.insertContacts(contactBatch)
                    val phoneEntities = ids.mapIndexed { idx, rowId ->
                        val ph = phoneRawBatch[idx]
                        PhoneEntity(
                            contactId = rowId,
                            number = ph,
                            normalizedNumber = ph,
                            tailKey = PhoneNormalizer.tailKey(ph),
                            typeLabel = "Mobile",
                            sortOrder = 0
                        )
                    }
                    dao.insertPhones(phoneEntities)
                }
            }
        }

        assertEquals(totalToInsert, dao.getTotalCount())

        // Paginated query in the middle of 100,000 contacts
        val pageQuery = SimpleSQLiteQuery(
            """
            SELECT id, displayName, firstName, lastName, firstLetter,
                   primaryPhone, primaryEmail, organization, photoPath,
                   groupNamesCache, phoneCount, emailCount, avatarColorSeed
            FROM contacts
            ORDER BY sortKey ASC, id ASC
            LIMIT 60 OFFSET 50000
            """.trimIndent()
        )
        val pageRows: List<com.example.data.local.ContactSummaryRow>
        val pageMs = measureTimeMillis {
            pageRows = dao.queryContactsPage(pageQuery)
        }
        assertEquals(60, pageRows.size)

        // Instant FTS4 search among 100,000 contacts
        val ftsQuery = SimpleSQLiteQuery(
            """
            SELECT c.id, c.displayName, c.firstName, c.lastName, c.firstLetter,
                   c.primaryPhone, c.primaryEmail, c.organization, c.photoPath,
                   c.groupNamesCache, c.phoneCount, c.emailCount, c.avatarColorSeed
            FROM contacts c
            INNER JOIN contacts_fts fts ON fts.rowid = c.id
            WHERE contacts_fts MATCH ?
            LIMIT 20
            """.trimIndent(),
            arrayOf("majid* aoudi*")
        )
        val searchHits: List<com.example.data.local.ContactSummaryRow>
        val searchMs = measureTimeMillis {
            searchHits = dao.queryContactsPage(ftsQuery)
        }
        assertEquals(1, searchHits.size)
        assertEquals("Majid Aoudi Spécial", searchHits[0].displayName)
        assertTrue("Expected fast batch insert (< 60s), took ${insertMs}ms", insertMs < 60_000)
        assertTrue("Expected fast FTS4 search (< 500ms), took ${searchMs}ms", searchMs < 500)
        assertTrue("Expected fast pagination (< 500ms), took ${pageMs}ms", pageMs < 500)
    }
}
