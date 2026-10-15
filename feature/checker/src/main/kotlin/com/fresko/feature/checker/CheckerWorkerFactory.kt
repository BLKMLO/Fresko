package com.fresko.feature.checker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters

/** Fabrique WorkManager qui injecte les dépendances dans [CheckWorker]. */
class CheckerWorkerFactory(
    private val deps: () -> CheckerDependencies,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        CheckWorker::class.java.name -> CheckWorker(appContext, workerParameters, deps())
        else -> null
    }
}
