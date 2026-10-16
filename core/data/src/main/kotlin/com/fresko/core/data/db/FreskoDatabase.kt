package com.fresko.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [WorkEntity::class], version = 3, exportSchema = true)
abstract class FreskoDatabase : RoomDatabase() {
    abstract fun workDao(): WorkDao

    companion object {
        fun create(context: Context): FreskoDatabase =
            Room.databaseBuilder(context, FreskoDatabase::class.java, "fresko.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
    }
}
