package com.fresko.app.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fresko.app.BuildConfig
import com.fresko.app.ui.components.SourceStyle
import com.fresko.app.ui.components.relativeTime
import com.fresko.app.ui.theme.FreskoColors
import com.fresko.core.model.SourceId
import java.util.Date

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::chooseBackupFolder)
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::exportTo)
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importFrom)
    }
    SettingsContent(
        state = state,
        onBack = onBack,
        onToggleSource = { id, enabled -> viewModel.setSourceEnabled(id, enabled) },
        onInterval = { viewModel.setInterval(it) },
        onCheckNow = viewModel::checkNow,
        backupActions = BackupActions(
            onChooseFolder = { pickFolder.launch(null) },
            onStopAuto = viewModel::stopAutoBackup,
            onExport = { export.launch(exportFileName()) },
            onImport = { import.launch(BACKUP_MIME_TYPES) },
        ),
    )
}

/** Actions de la carte « Sauvegarde » (lanceurs du sélecteur de fichiers Android). */
class BackupActions(
    val onChooseFolder: () -> Unit = {},
    val onStopAuto: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onImport: () -> Unit = {},
)

/** Types acceptés à l'import : certains gestionnaires de fichiers étiquettent mal le JSON. */
val BACKUP_MIME_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")

private fun exportFileName(): String =
    "fresko-sauvegarde-" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.FRANCE).format(Date()) + ".json"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsContent(
    state: SettingsUiState,
    onBack: () -> Unit = {},
    onToggleSource: (SourceId, Boolean) -> Unit = { _, _ -> },
    onInterval: (Int) -> Unit = {},
    onCheckNow: () -> Unit = {},
    backupActions: BackupActions = BackupActions(),
) {
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Réglages", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsCard(
                "Sources",
                "Une source désactivée n'est plus vérifiée ni affichée ; ses œuvres suivies sont conservées.",
            ) {
                state.sources.forEachIndexed { index, s ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val accent = SourceStyle.color(s.id)
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(s.name.take(1), style = MaterialTheme.typography.titleMedium, color = accent)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(s.name, style = MaterialTheme.typography.titleMedium)
                            val works = "${s.trackedCount} œuvre${if (s.trackedCount > 1) "s" else ""}"
                            Text(
                                when {
                                    !s.enabled -> "Désactivée · $works conservée${if (s.trackedCount > 1) "s" else ""}"
                                    s.lastSuccess != null -> "$works · vérifiée ${relativeTime(s.lastSuccess)}"
                                    else -> "$works · pas encore vérifiée"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = s.enabled, onCheckedChange = { onToggleSource(s.id, it) })
                    }
                }
            }

            SettingsCard("Fréquence", "Intervalle minimal entre deux vérifications") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SettingsViewModel.INTERVALS.forEach { minutes ->
                        FilterChip(
                            selected = state.intervalMinutes == minutes,
                            onClick = { onInterval(minutes) },
                            label = { Text(formatInterval(minutes)) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(16.dp).padding(top = 2.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Pour économiser la batterie, Android peut retarder les vérifications quand " +
                            "le téléphone est inactif. Elles n'ont lieu qu'avec du réseau et une batterie non faible.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SettingsCard("Vérification", null) {
                Button(onClick = onCheckNow, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Vérifier maintenant")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Optimisation de la batterie") }
            }

            BackupCard(state.backup, backupActions)

            Column(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Fresko",
                    style = MaterialTheme.typography.titleLarge.merge(TextStyle(brush = FreskoColors.Brand)),
                )
                Text(
                    "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun BackupCard(backup: BackupUi, actions: BackupActions) {
    SettingsCard(
        "Sauvegarde",
        "Un fichier hors de l'application : vos œuvres et réglages survivent à une désinstallation.",
    ) {
        Text("Sauvegarde automatique", style = MaterialTheme.typography.titleMedium)
        Text(
            when {
                backup.folder == null ->
                    "Désactivée. Choisissez un dossier (par ex. créez « Documents/Fresko ») : " +
                        "le fichier y sera mis à jour à chaque changement."
                backup.lastBackupAt != null ->
                    "Dossier « ${backup.folder} » · dernière sauvegarde ${relativeTime(backup.lastBackupAt)}"
                else -> "Dossier « ${backup.folder} »"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        backup.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = actions.onChooseFolder, enabled = !backup.busy, modifier = Modifier.weight(1f)) {
                Text(if (backup.folder == null) "Choisir le dossier" else "Changer", maxLines = 1)
            }
            if (backup.folder != null) {
                OutlinedButton(onClick = actions.onStopAuto, enabled = !backup.busy, modifier = Modifier.weight(1f)) {
                    Text("Arrêter", maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = actions.onExport, enabled = !backup.busy, modifier = Modifier.weight(1f)) {
                Text("Exporter", maxLines = 1)
            }
            OutlinedButton(onClick = actions.onImport, enabled = !backup.busy, modifier = Modifier.weight(1f)) {
                Text("Restaurer", maxLines = 1)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Après une réinstallation : rechoisissez le même dossier (la sauvegarde est retrouvée " +
                "et restaurée automatiquement), ou touchez « Restaurer » et ouvrez le fichier.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        backup.message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SettingsCard(title: String, subtitle: String?, content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

private fun formatInterval(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    else -> "${minutes / 60} h"
}
