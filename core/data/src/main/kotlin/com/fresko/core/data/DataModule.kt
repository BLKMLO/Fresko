package com.fresko.core.data

import android.content.Context
import com.fresko.core.data.db.FreskoDatabase
import com.fresko.core.data.repository.WorkRepository

/** Point d'entrée du module : Room reste un détail interne de core:data. */
class DataModule(context: Context) {
    private val database by lazy { FreskoDatabase.create(context.applicationContext) }
    val works: WorkRepository by lazy { WorkRepository(database.workDao()) }
}
