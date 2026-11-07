package com.fresko.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fresko.app.ui.components.Cover
import com.fresko.app.ui.components.EarlyAccessBadge
import com.fresko.app.ui.components.SourceBadge
import com.fresko.app.ui.components.relativeTime
import com.fresko.app.ui.components.shortChapter
import com.fresko.core.model.WorkKey
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Fiche d'une œuvre suivie : dernier chapitre, sources (une ou plusieurs), réglage accès
 * anticipé par source, actions. Lu / non lu et « Ne plus suivre » portent sur toute l'œuvre.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkSheet(
    item: TrackedItem,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    onToggleEarly: (WorkKey, Boolean) -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onRemoveSource: (WorkKey) -> Unit,
    onAddSource: () -> Unit,
    onUntrack: () -> Unit,
) {
    val e = item.entity
    // Suppression demandée, en attente de confirmation.
    var confirm by remember { mutableStateOf<PendingRemoval?>(null) }
    confirm?.let { pending ->
        ConfirmRemovalDialog(
            pending = pending,
            title = e.title,
            onConfirm = {
                confirm = null
                when (pending) {
                    is PendingRemoval.All -> onUntrack()
                    is PendingRemoval.OneSource -> onRemoveSource(pending.member.entity.key)
                }
            },
            onDismiss = { confirm = null },
        )
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Cover(item.cover.entity.coverUrl, e.title, item.cover.source.id, width = 96.dp, height = 136.dp, corner = 18.dp)
                Column(Modifier.padding(start = 18.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (m in listOf(item.lead) + (item.members - item.lead)) SourceBadge(m.source.id, m.source.name)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(e.title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    item.members.mapNotNull { it.entity.trackedAt }.minOrNull()?.let {
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
                    if (e.chapterPublishedAt != null || e.chapterEarlyAccess || item.isGroup) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val details = listOfNotNull(
                                if (item.isGroup) "sur ${item.source.name}" else null,
                                e.chapterPublishedAt?.let { relativeTime(it) },
                            ).joinToString(" · ")
                            if (details.isNotEmpty()) {
                                Text(
                                    details,
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

            for (failed in item.inError) {
                val f = failed.entity
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            "DERNIÈRE VÉRIFICATION EN ÉCHEC" + if (item.isGroup) " · ${failed.source.name.uppercase()}" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(f.checkError.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            (f.checkErrorAt?.let { "Échec " + relativeTime(it) + ". " } ?: "") +
                                "Nouvel essai à chaque check ; le chapitre affiché peut ne pas être le dernier.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            SourcesCard(item, onOpen, onToggleEarly, { confirm = PendingRemoval.OneSource(it) }, onAddSource)

            Button(
                onClick = {
                    onOpen(e.chapterUrl ?: e.url)
                    if (item.isUnread) onMarkRead()
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
                if (item.members.any { it.entity.chapterUrl != null }) {
                    FilledTonalButton(
                        onClick = if (item.isUnread) onMarkRead else onMarkUnread,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (item.isUnread) "Marquer lu" else "Marquer non lu", maxLines = 1)
                    }
                }
                OutlinedButton(onClick = { onOpen(e.url) }, modifier = Modifier.weight(1f)) {
                    Text("Page de l'œuvre", maxLines = 1)
                }
            }
            TextButton(
                onClick = { confirm = PendingRemoval.All(item.members.size) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (item.isGroup) "Ne plus suivre (toutes les sources)" else "Ne plus suivre")
            }
        }
    }
}

/** Sources de l'œuvre : dernier chapitre de chacune, accès anticipé, retrait ; ajout d'une source. */
@Composable
private fun SourcesCard(
    item: TrackedItem,
    onOpen: (String) -> Unit,
    onToggleEarly: (WorkKey, Boolean) -> Unit,
    onRemoveSource: (Member) -> Unit,
    onAddSource: () -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("SOURCES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.isGroup) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Une seule alerte par numéro de chapitre : celle de la première source qui le publie.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            for ((index, m) in item.members.withIndex()) {
                val me = m.entity
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SourceBadge(m.source.id, m.source.name)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            (if (me.chapterUrl != null) shortChapter(me.chapterNumber, me.chapterLabel) else "Pas encore vérifié") +
                                (me.chapterPublishedAt?.let { " · " + relativeTime(it) } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (item.isGroup && me.title != item.entity.title) {
                            Text(
                                me.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    TextButton(onClick = { onOpen(me.chapterUrl ?: me.url) }) { Text("Ouvrir") }
                    if (item.isGroup) {
                        IconButton(onClick = { onRemoveSource(m) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Retirer ${m.source.name}")
                        }
                    }
                }
                if (m.source.hasEarlyAccess) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("Alertes accès anticipé", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (me.notifyEarlyAccess) {
                                    "Alerte dès la sortie, même en accès anticipé (premium)."
                                } else {
                                    "Alerte uniquement quand le chapitre devient gratuit."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = me.notifyEarlyAccess, onCheckedChange = { onToggleEarly(me.key, it) })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onAddSource, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Ajouter une source")
            }
        }
    }
}

/** Suppression à confirmer : toute l'œuvre, ou une seule de ses sources. */
private sealed interface PendingRemoval {
    data class All(val sourceCount: Int) : PendingRemoval
    data class OneSource(val member: Member) : PendingRemoval
}

@Composable
private fun ConfirmRemovalDialog(
    pending: PendingRemoval,
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val (heading, text, action) = when (pending) {
        is PendingRemoval.All -> Triple(
            "Ne plus suivre « $title » ?",
            (if (pending.sourceCount > 1) "L'œuvre ne sera plus suivie sur aucune de ses ${pending.sourceCount} sources. " else "") +
                "Vous ne recevrez plus d'alerte pour ses nouveaux chapitres. Pour la suivre à nouveau, il faudra la rechercher.",
            "Ne plus suivre",
        )
        is PendingRemoval.OneSource -> Triple(
            "Retirer ${pending.member.source.name} ?",
            "« $title » ne sera plus vérifiée sur ${pending.member.source.name}. Les autres sources restent suivies.",
            "Retirer",
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
        title = { Text(heading) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(action) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
