package com.fresko.app.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Sources", style = MaterialTheme.typography.titleMedium)
            state.sources.forEach { s ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            s.lastSuccess?.let { "Dernier check réussi : " + format.format(Date(it)) }
                                ?: "Pas encore vérifiée",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = s.enabled, onCheckedChange = { viewModel.setSourceEnabled(s.id, it) })
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Fréquence de vérification", style = MaterialTheme.typography.titleMedium)
            SettingsViewModel.INTERVALS.forEach { minutes ->
                Row(
                    Modifier.fillMaxWidth()
                        .selectable(selected = state.intervalMinutes == minutes) { viewModel.setInterval(minutes) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = state.intervalMinutes == minutes, onClick = null)
                    Text(formatInterval(minutes), Modifier.padding(start = 8.dp))
                }
            }
            Text(
                "Android regroupe les tâches de fond pour économiser la batterie (mode Doze) : " +
                    "l'intervalle est un minimum, un check peut être retardé quand le téléphone " +
                    "est inactif. Les checks ne s'exécutent qu'avec du réseau et une batterie non faible.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Button(onClick = viewModel::checkNow, modifier = Modifier.fillMaxWidth()) {
                Text("Vérifier maintenant")
            }
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Réglages d'optimisation de la batterie") }
        }
    }
}

private fun formatInterval(minutes: Int): String = when {
    minutes < 60 -> "$minutes minutes"
    minutes == 60 -> "1 heure"
    else -> "${minutes / 60} heures"
}
