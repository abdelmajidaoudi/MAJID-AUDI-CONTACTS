package com.example.data.local

import android.content.ContentProviderOperation
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.FileProvider
import com.example.domain.model.ContactDetail
import com.example.domain.vcard.VcfCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PhonebookSyncHelper {

    fun buildInsertContactIntent(contact: ContactDetail): Intent {
        return Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.Contacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.NAME, contact.displayName)
            contact.phones.getOrNull(0)?.let {
                putExtra(ContactsContract.Intents.Insert.PHONE, it.number)
            }
            contact.phones.getOrNull(1)?.let {
                putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE, it.number)
            }
            contact.emails.getOrNull(0)?.let {
                putExtra(ContactsContract.Intents.Insert.EMAIL, it.email)
            }
            contact.emails.getOrNull(1)?.let {
                putExtra(ContactsContract.Intents.Insert.SECONDARY_EMAIL, it.email)
            }
            if (contact.organization.isNotBlank()) {
                putExtra(ContactsContract.Intents.Insert.COMPANY, contact.organization)
            }
            if (contact.jobTitle.isNotBlank()) {
                putExtra(ContactsContract.Intents.Insert.JOB_TITLE, contact.jobTitle)
            }
            if (contact.postalAddress.isNotBlank()) {
                putExtra(ContactsContract.Intents.Insert.POSTAL, contact.postalAddress)
            }
            if (contact.notes.isNotBlank()) {
                putExtra(ContactsContract.Intents.Insert.NOTES, contact.notes)
            }
        }
    }

    suspend fun saveContactsToDevicePhonebook(
        context: Context,
        contacts: List<ContactDetail>,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): Int = withContext(Dispatchers.IO) {
        var savedCount = 0
        val total = contacts.size
        for (contact in contacts) {
            if (isCancelled()) break
            val ops = ArrayList<ContentProviderOperation>()
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build()
            )
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contact.displayName)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, contact.firstName)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, contact.lastName)
                    .build()
            )
            for (phone in contact.phones) {
                val phoneType = when (phone.typeLabel) {
                    "Mobile" -> ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                    "Travail" -> ContactsContract.CommonDataKinds.Phone.TYPE_WORK
                    "Domicile" -> ContactsContract.CommonDataKinds.Phone.TYPE_HOME
                    "Fax" -> ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK
                    else -> ContactsContract.CommonDataKinds.Phone.TYPE_OTHER
                }
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                        )
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone.number)
                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, phoneType)
                        .build()
                )
            }
            for (email in contact.emails) {
                val emailType = when (email.typeLabel) {
                    "Travail" -> ContactsContract.CommonDataKinds.Email.TYPE_WORK
                    "Personnel" -> ContactsContract.CommonDataKinds.Email.TYPE_HOME
                    else -> ContactsContract.CommonDataKinds.Email.TYPE_OTHER
                }
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE
                        )
                        .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email.email)
                        .withValue(ContactsContract.CommonDataKinds.Email.TYPE, emailType)
                        .build()
                )
            }
            if (contact.organization.isNotBlank() || contact.jobTitle.isNotBlank()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE
                        )
                        .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, contact.organization)
                        .withValue(ContactsContract.CommonDataKinds.Organization.TITLE, contact.jobTitle)
                        .build()
                )
            }
            if (contact.notes.isNotBlank()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE
                        )
                        .withValue(ContactsContract.CommonDataKinds.Note.NOTE, contact.notes)
                        .build()
                )
            }
            runCatching {
                context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                savedCount++
            }
            onProgress(savedCount, total)
        }
        savedCount
    }

    suspend fun createShareVcfUri(
        context: Context,
        contacts: Sequence<ContactDetail>,
        fileName: String,
        photoStorage: PhotoStorage
    ): Uri = withContext(Dispatchers.IO) {
        val sharedDir = File(context.cacheDir, "shared").apply { if (!exists()) mkdirs() }
        // Clean old temp files
        sharedDir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 3600_000 }?.forEach { it.delete() }
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
        val file = File(sharedDir, safeName)
        FileOutputStream(file).use { fos ->
            VcfCodec.writeVcfStream(
                contacts = contacts,
                outputStream = fos,
                photoLoader = { path -> photoStorage.readPhotoBytes(path) }
            )
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun buildShareVcfIntent(uri: Uri, targetPackage: String? = null): Intent {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/x-vcard"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (!targetPackage.isNullOrBlank()) {
                setPackage(targetPackage)
            }
        }
        return intent
    }
}
