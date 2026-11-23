package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ContactEntity::class,
        ContactFtsEntity::class,
        PhoneEntity::class,
        EmailEntity::class,
        GroupEntity::class,
        ContactGroupCrossRef::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val DEFAULT_GROUPS = listOf(
            "Famille" to "#1D4ED8",
            "Collègues" to "#7C3AED",
            "Amis" to "#0891B2",
            "Travail" to "#2563EB",
            "Université" to "#9333EA",
            "Mes ex" to "#EC4899",
            "Clients" to "#0D9488",
            "Autres" to "#64748B"
        )

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(appContext: Context): AppDatabase {
            lateinit var db: AppDatabase
            db = Room.databaseBuilder(
                appContext,
                AppDatabase::class.java,
                "majid_aoudi_contacts.db"
            )
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addCallback(object : Callback() {
                    override fun onCreate(dbSqlite: SupportSQLiteDatabase) {
                        super.onCreate(dbSqlite)
                        val now = System.currentTimeMillis()
                        DEFAULT_GROUPS.forEach { (name, color) ->
                            dbSqlite.execSQL(
                                "INSERT OR IGNORE INTO groups (name, colorHex, isSystemDefault, createdAt) VALUES (?, ?, 1, ?)",
                                arrayOf<Any>(name, color, now)
                            )
                        }
                    }

                    override fun onOpen(dbSqlite: SupportSQLiteDatabase) {
                        super.onOpen(dbSqlite)
                        // Ensure FTS table stays synced and default groups exist
                        CoroutineScope(Dispatchers.IO).launch {
                            val dao = db.contactDao()
                            if (dao.getAllGroups().isEmpty()) {
                                val now = System.currentTimeMillis()
                                DEFAULT_GROUPS.forEach { (name, color) ->
                                    dao.insertGroup(
                                        GroupEntity(
                                            name = name,
                                            colorHex = color,
                                            isSystemDefault = true,
                                            createdAt = now
                                        )
                                    )
                                }
                            }
                        }
                    }
                })
                .build()
            return db
        }
    }
}
