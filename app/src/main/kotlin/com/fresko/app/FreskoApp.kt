package com.fresko.app

import android.app.Application
import androidx.work.Configuration
import com.fresko.feature.checker.CheckerWorkerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FreskoApp : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.ensureChannel()
        // Sauvegarde automatique (si un dossier a été choisi) à chaque changement de données
        container.backup.startAuto(appScope)
        // S'assure qu'un check est planifié (KEEP : ne réinitialise pas le planning existant).
        appScope.launch {
            container.scheduler.schedule(container.settings.current().intervalMinutes, replace = false)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(CheckerWorkerFactory { container.checkerDependencies() })
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
