package com.fresko.feature.checker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

/**
 * Planifie le check en arrière-plan avec WorkManager (respecte Doze et
 * l'optimisation batterie d'Android, survit aux redémarrages).
 *
 * Android impose 15 min minimum pour un travail périodique
 * ([PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS]). En dessous, on enchaîne
 * des travaux ponctuels : chaque exécution planifie la suivante avec un délai.
 */
class CheckScheduler(context: Context) {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    /** @param replace true quand l'utilisateur change l'intervalle. */
    fun schedule(intervalMinutes: Int, replace: Boolean) {
        if (intervalMinutes >= PERIODIC_MIN_MINUTES) {
            workManager.cancelUniqueWork(CHAIN_WORK)
            val request = PeriodicWorkRequestBuilder<CheckWorker>(
                intervalMinutes.toLong(), TimeUnit.MINUTES,
            ).setConstraints(constraints).build()
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        } else {
            workManager.cancelUniqueWork(PERIODIC_WORK)
            workManager.enqueueUniqueWork(
                CHAIN_WORK,
                if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                chainRequest(intervalMinutes),
            )
        }
    }

    /** Appelé par le worker à la fin d'un maillon de la chaîne (< 15 min). */
    internal fun scheduleNextInChain(intervalMinutes: Int) {
        // APPEND_OR_REPLACE : le maillon suivant démarre après la fin de celui en cours
        // (REPLACE annulerait le worker qui est en train de s'exécuter).
        workManager.enqueueUniqueWork(
            CHAIN_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            chainRequest(intervalMinutes),
        )
    }

    /** Check immédiat demandé par l'utilisateur. */
    fun checkNow() {
        val request = OneTimeWorkRequestBuilder<CheckWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniqueWork(MANUAL_WORK, ExistingWorkPolicy.KEEP, request)
    }

    /** True tant qu'un check manuel est en attente ou en cours. */
    fun observeManualCheck(): Flow<Boolean> =
        workManager.getWorkInfosForUniqueWorkFlow(MANUAL_WORK).map { infos ->
            infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
        }

    private fun chainRequest(intervalMinutes: Int) = OneTimeWorkRequestBuilder<CheckWorker>()
        .setInitialDelay(intervalMinutes.toLong(), TimeUnit.MINUTES)
        .setConstraints(constraints)
        .setInputData(workDataOf(CheckWorker.KEY_CHAINED to true))
        .build()

    companion object {
        const val PERIODIC_MIN_MINUTES = 15
        private const val PERIODIC_WORK = "fresko_check_periodic"
        private const val CHAIN_WORK = "fresko_check_chain"
        private const val MANUAL_WORK = "fresko_check_manual"
    }
}
