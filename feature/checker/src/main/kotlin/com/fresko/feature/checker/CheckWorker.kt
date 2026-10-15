package com.fresko.feature.checker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fresko.core.domain.UpdateChecker

/** Exécute un check : interroge les sources, notifie les nouveaux chapitres. */
class CheckWorker(
    context: Context,
    params: WorkerParameters,
    private val deps: CheckerDependencies,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val chained = inputData.getBoolean(KEY_CHAINED, false)
        try {
            val report = deps.checker.check()
            report.errors.forEach { (source, e) ->
                Log.w(TAG, "Check ${source.value} en échec : ${e.message}", e)
            }
            deps.notifier.notify(report.newChapters)
        } finally {
            // Worker interrompu (contraintes perdues…) : WorkManager le relancera
            // lui-même ; planifier un maillon ici doublerait la chaîne.
            if (chained && !isStopped) {
                val interval = deps.intervalMinutes()
                if (interval < CheckScheduler.PERIODIC_MIN_MINUTES) {
                    deps.scheduler.scheduleNextInChain(interval)
                }
            }
        }
        return Result.success()
    }

    companion object {
        const val KEY_CHAINED = "chained"
        private const val TAG = "FreskoCheck"
    }
}

/** Ce dont le worker a besoin, fourni par l'application (injection manuelle). */
class CheckerDependencies(
    val checker: UpdateChecker,
    val notifier: ChapterNotifier,
    val scheduler: CheckScheduler,
    val intervalMinutes: suspend () -> Int,
)
