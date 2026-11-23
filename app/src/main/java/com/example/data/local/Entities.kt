package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "contacts",
    indices = [
        Index(value = ["uid"]),
        Index(value = ["sortKey"]),
        Index(value = ["firstLetter", "sortKey"]),
        Index(value = ["primaryPhoneNormalized"]),
        Index(value = ["primaryPhoneTail"]),
        Index(value = ["primaryEmailLower"]),
        Index(value = ["updatedAt"]),
        Index(value = ["phoneCount"])
    ]
)
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uid: String,
    val firstName: String,
    val lastName: String,
    val displayName: String,
    val sortKey: String,
    val firstLetter: String,
    val primaryPhone: String,
    val primaryPhoneNormalized: String,
    val primaryPhoneTail: String,
    val primaryEmail: String,
    val primaryEmailLower: String,
    val organization: String,
    val jobTitle: String,
    val birthday: String,
    val postalAddress: String,
    val notes: String,
    val photoPath: String?,
    val groupNamesCache: String,
    val phoneCount: Int,
    val emailCount: Int,
    val searchBlob: String,
    val customFieldsJson: String,
    val avatarColorSeed: Int,
    val createdAt: Long,
    val updatedAt: Long
)

@Fts4(contentEntity = ContactEntity::class)
@Entity(tableName = "contacts_fts")
data class ContactFtsEntity(
    @ColumnInfo(name = "searchBlob") val searchBlob: String
)

@Entity(
    tableName = "phones",
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["contactId"]),
        Index(value = ["normalizedNumber"]),
        Index(value = ["tailKey"])
    ]
)
data class PhoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val contactId: Long,
    val number: String,
    val normalizedNumber: String,
    val tailKey: String,
    val typeLabel: String,
    val sortOrder: Int
)

@Entity(
    tableName = "emails",
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["contactId"]),
        Index(value = ["emailLower"])
    ]
)
data class EmailEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val contactId: Long,
    val email: String,
    val emailLower: String,
    val typeLabel: String,
    val sortOrder: Int
)

@Entity(
    tableName = "groups",
    indices = [Index(value = ["name"], unique = true)]
)
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val colorHex: String,
    val isSystemDefault: Boolean,
    val createdAt: Long
)

@Entity(
    tableName = "contact_group_cross_ref",
    primaryKeys = ["contactId", "groupId"],
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["contactId"]),
        Index(value = ["groupId"])
    ]
)
data class ContactGroupCrossRef(
    val contactId: Long,
    val groupId: Long
)

data class ContactSummaryRow(
    val id: Long,
    val displayName: String,
    val firstName: String,
    val lastName: String,
    val firstLetter: String,
    val primaryPhone: String,
    val primaryEmail: String,
    val organization: String,
    val photoPath: String?,
    val groupNamesCache: String,
    val phoneCount: Int,
    val emailCount: Int,
    val avatarColorSeed: Int
)

data class GroupWithCountRow(
    val id: Long,
    val name: String,
    val colorHex: String,
    val isSystemDefault: Boolean,
    val contactCount: Int
)
