package com.fresko.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [WorkEntity::class], version = 1, exportSchema = true)
abstract class FreskoDatabase : RoomDatabase() {
    abstract fun workDao(): WorkDao

    companion object {
        fun create(context: Context): FreskoDatabase =
            Room.databaseBuilder(context, FreskoDatabase::class.java, "fresko.db").build()
    }
}
