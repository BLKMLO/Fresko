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

    /** Réseau requis : Wi-Fi (réseau non facturé à l'usage) si [wifiOnly], sinon n'importe lequel. */
    private fun constraints(wifiOnly: Boolean) = Constraints.Builder()
        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    /**
     * @param replace true quand l'utilisateur change un réglage (intervalle, Wi-Fi uniquement).
     * @param wifiOnly vérifications automatiques seulement sur un réseau non facturé (Wi-Fi).
     */
    fun schedule(intervalMinutes: Int, replace: Boolean, wifiOnly: Boolean = false) {
        val constraints = constraints(wifiOnly)
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
                chainRequest(intervalMinutes, constraints),
            )
        }
    }

    /** Appelé par le worker à la fin d'un maillon de la chaîne (< 15 min). */
    internal fun scheduleNextInChain(intervalMinutes: Int, wifiOnly: Boolean) {
        // APPEND_OR_REPLACE : le maillon suivant démarre après la fin de celui en cours
        // (REPLACE annulerait le worker qui est en train de s'exécuter).
        workManager.enqueueUniqueWork(
            CHAIN_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            chainRequest(intervalMinutes, constraints(wifiOnly)),
        )
    }

    /** Check immédiat demandé par l'utilisateur (tout réseau, même si « Wi-Fi uniquement »). */
    fun checkNow() {
        val request = OneTimeWorkRequestBuilder<CheckWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniqueWork(MANUAL_WORK, ExistingWorkPolicy.KEEP, request)
    }

    /** État du check manuel : en cours, en attente (pas de réseau), ou aucun. */
    fun observeManualCheck(): Flow<ManualCheck> =
        workManager.getWorkInfosForUniqueWorkFlow(MANUAL_WORK).map { infos ->
            when {
                infos.any { it.state == WorkInfo.State.RUNNING } -> ManualCheck.RUNNING
                infos.any { it.state == WorkInfo.State.ENQUEUED } -> ManualCheck.WAITING
                else -> ManualCheck.IDLE
            }
        }

    /** Annule un check manuel qui attend le réseau. */
    fun cancelManualCheck() {
        workManager.cancelUniqueWork(MANUAL_WORK)
    }

    private fun chainRequest(intervalMinutes: Int, constraints: Constraints) = OneTimeWorkRequestBuilder<CheckWorker>()
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

/** Check demandé par l'utilisateur. WAITING : en file, typiquement faute de réseau. */
enum class ManualCheck { IDLE, WAITING, RUNNING }
