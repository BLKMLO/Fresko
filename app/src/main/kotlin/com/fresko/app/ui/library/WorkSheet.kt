package com.fresko.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fresko.app.ui.components.Cover
import com.fresko.app.ui.components.EarlyAccessBadge
import com.fresko.app.ui.components.SourceBadge
import com.fresko.app.ui.components.relativeTime
import com.fresko.app.ui.components.shortChapter
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Fiche d'une œuvre suivie : dernier chapitre, réglage accès anticipé, actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkSheet(
    item: TrackedItem,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    onToggleEarly: (Boolean) -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onUntrack: () -> Unit,
) {
    val e = item.entity
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Cover(e.coverUrl, e.title, item.source.id, width = 96.dp, height = 136.dp, corner = 18.dp)
                Column(Modifier.padding(start = 18.dp)) {
                    SourceBadge(item.source.id, item.source.name)
                    Spacer(Modifier.height(8.dp))
                    Text(e.title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    e.trackedAt?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Suivie depuis le " + DateFormat.getDateInstance(DateFormat.LONG, Locale.FRANCE).format(Date(it)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        "DERNIER CHAPITRE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(e.chapterLabel ?: "Pas encore vérifié", style = MaterialTheme.typography.titleMedium)
                    if (e.chapterPublishedAt != null || e.chapterEarlyAccess) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            e.chapterPublishedAt?.let {
                                Text(
                                    relativeTime(it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            if (e.chapterEarlyAccess) EarlyAccessBadge(text = "Accès anticipé")
                        }
                    }
                }
            }

            if (item.source.hasEarlyAccess) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("Alertes accès anticipé", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (e.notifyEarlyAccess) {
                                    "Alerte dès la sortie, même en accès anticipé (premium)."
                                } else {
                                    "Alerte uniquement quand le chapitre devient gratuit."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = e.notifyEarlyAccess, onCheckedChange = onToggleEarly)
                    }
                }
            }

            Button(
                onClick = {
                    onOpen(e.chapterUrl ?: e.url)
                    if (e.isUnread) onMarkRead()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (e.chapterUrl != null) "Lire le " + shortChapter(e.chapterNumber, e.chapterLabel).lowercase()
                    else "Ouvrir l'œuvre"
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (e.chapterUrl != null) {
                    FilledTonalButton(
                        onClick = if (e.isUnread) onMarkRead else onMarkUnread,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (e.isUnread) "Marquer lu" else "Marquer non lu", maxLines = 1)
                    }
                }
                OutlinedButton(onClick = { onOpen(e.url) }, modifier = Modifier.weight(1f)) {
                    Text("Page de l'œuvre", maxLines = 1)
                }
            }
            TextButton(
                onClick = onUntrack,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Ne plus suivre")
            }
        }
    }
}
