package com.fresko.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [WorkEntity::class], version = 5, exportSchema = true)
abstract class FreskoDatabase : RoomDatabase() {
    abstract fun workDao(): WorkDao

    companion object {
        fun create(context: Context): FreskoDatabase =
            Room.databaseBuilder(context, FreskoDatabase::class.java, "fresko.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()

        /** v0.2.0 : accès anticipé par œuvre ; fin du catalogue (on ne garde que les suivies). */
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE works ADD COLUMN notify_early_access INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE works ADD COLUMN early_pending INTEGER NOT NULL DEFAULT 0")
                db.execSQL("DELETE FROM works WHERE tracked = 0")
            }
        }

        /** v0.3.0 : statut « lu ». Les chapitres déjà connus sont considérés comme lus. */
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE works ADD COLUMN read_chapter_url TEXT")
                db.execSQL("UPDATE works SET read_chapter_url = chapter_url")
            }
        }

        /**
         * v0.7.0 : erreur de vérification par œuvre. NatoManga a changé de domaine
         * (www.natomanga.com → 301 → www.manganami.com, constaté le 2026-11-06) : les URL
         * enregistrées sont réécrites pour rester comparables à celles du site (statut « lu »).
         */
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE works ADD COLUMN check_error TEXT")
                db.execSQL("ALTER TABLE works ADD COLUMN check_error_at INTEGER")
                for (column in listOf("url", "chapter_url", "read_chapter_url")) {
                    db.execSQL(
                        "UPDATE works SET $column = REPLACE($column, '$OLD_NATO', '$NEW_NATO') " +
                            "WHERE source_id = 'natomanga' AND $column LIKE '$OLD_NATO%'"
                    )
                }
            }
        }

        /**
         * v0.7.0 : même œuvre sur plusieurs sources (groupes) ; numéro du chapitre lu, pour
         * comparer le statut « lu » entre sources (les URL diffèrent d'un site à l'autre).
         */
        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE works ADD COLUMN group_id TEXT")
                db.execSQL("ALTER TABLE works ADD COLUMN read_chapter_number REAL")
                db.execSQL(
                    "UPDATE works SET read_chapter_number = chapter_number " +
                        "WHERE read_chapter_url IS NOT NULL AND read_chapter_url = chapter_url"
                )
            }
        }

        private const val OLD_NATO = LegacyUrls.OLD_NATO
        private const val NEW_NATO = LegacyUrls.NEW_NATO
    }
}
