package com.fresko.app.ui.library

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.fresko.app.R
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fresko.app.ui.components.Cover
import com.fresko.app.ui.components.EarlyAccessBadge
import com.fresko.app.ui.components.Placeholder
import com.fresko.app.ui.components.SourceBadge
import com.fresko.app.ui.components.relativeTime
import com.fresko.app.ui.components.shortChapter
import com.fresko.app.ui.theme.FreskoColors
import com.fresko.core.model.WorkInfo
import com.fresko.core.model.WorkKey

@Composable
fun LibraryScreen(viewModel: LibraryViewModel, onOpenSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LibraryContent(
        state = state,
        actions = LibraryActions(
            onQueryChange = viewModel::onQueryChange,
            onClearQuery = viewModel::clearQuery,
            onRefresh = viewModel::checkNow,
            onTrack = viewModel::track,
            onUntrack = viewModel::untrack,
            onToggleEarly = viewModel::setNotifyEarlyAccess,
            onMarkRead = viewModel::markRead,
            onMarkUnread = viewModel::markUnread,
            onOpenSettings = onOpenSettings,
        ),
    )
}

/** Actions de l'écran (séparées du ViewModel pour garder l'écran sans état). */
class LibraryActions(
    val onQueryChange: (String) -> Unit = {},
    val onClearQuery: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onTrack: (WorkInfo) -> Unit = {},
    val onUntrack: (WorkKey) -> Unit = {},
    val onToggleEarly: (WorkKey, Boolean) -> Unit = { _, _ -> },
    val onMarkRead: (WorkKey) -> Unit = {},
    val onMarkUnread: (WorkKey) -> Unit = {},
    val onOpenSettings: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryContent(state: LibraryUiState, actions: LibraryActions, initialSelection: WorkKey? = null) {
    val context = LocalContext.current
    val open: (String) -> Unit = { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
    var selectedKey by remember { mutableStateOf(initialSelection) }
    val selected = state.tracked.firstOrNull { it.entity.key == selectedKey }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        PullToRefreshBox(
            isRefreshing = state.checking,
            onRefresh = actions.onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                item(key = "header") { Header(state, actions.onOpenSettings) }
                item(key = "search") {
                    SearchField(
                        query = state.query,
                        onChange = actions.onQueryChange,
                        onClear = actions.onClearQuery,
                    )
                }
                if (state.isSearching) {
                    searchResults(state, actions, onSelect = { selectedKey = it })
                } else if (state.tracked.isEmpty()) {
                    item(key = "empty") { EmptyState(state.enabledSources.map { it.name }) }
                } else {
                    val toRead = state.toRead
                    val upToDate = state.upToDate
                    if (toRead.isNotEmpty()) {
                        item(key = "title_toread") {
                            SectionTitle("À lire", toRead.size.toString(), Modifier.animateItem())
                        }
                        items(toRead, key = { "t:" + it.entity.sourceId + ":" + it.entity.workKey }) { item ->
                            TrackedCard(item, onClick = { selectedKey = item.entity.key }, modifier = Modifier.animateItem())
                        }
                    }
                    if (upToDate.isNotEmpty()) {
                        item(key = "title_uptodate") {
                            SectionTitle("À jour", upToDate.size.toString(), Modifier.animateItem())
                        }
                        items(upToDate, key = { "t:" + it.entity.sourceId + ":" + it.entity.workKey }) { item ->
                            TrackedCard(item, onClick = { selectedKey = item.entity.key }, modifier = Modifier.animateItem())
                        }
                    }
                }
                if (!state.isSearching && state.hiddenCount > 0) {
                    item(key = "hidden") {
                        Hint(
                            "${state.hiddenCount} œuvre${if (state.hiddenCount > 1) "s" else ""} masquée" +
                                "${if (state.hiddenCount > 1) "s" else ""} : source désactivée dans les réglages " +
                                "(rien n'est supprimé).",
                        )
                    }
                }
            }
        }
    }

    if (selected != null) {
        WorkSheet(
            item = selected,
            onDismiss = { selectedKey = null },
            onOpen = open,
            onToggleEarly = { actions.onToggleEarly(selected.entity.key, it) },
            onMarkRead = { actions.onMarkRead(selected.entity.key) },
            onMarkUnread = { actions.onMarkUnread(selected.entity.key) },
            onUntrack = {
                actions.onUntrack(selected.entity.key)
                selectedKey = null
            },
        )
    }
}

private fun LazyListScope.searchResults(
    state: LibraryUiState,
    actions: LibraryActions,
    onSelect: (WorkKey) -> Unit,
) {
    if (state.matchingTracked.isNotEmpty()) {
        item(key = "title_matching") { SectionTitle("Dans vos lectures", state.matchingTracked.size.toString()) }
        items(state.matchingTracked, key = { "m:" + it.entity.sourceId + ":" + it.entity.workKey }) { item ->
            TrackedCard(item, onClick = { onSelect(item.entity.key) }, modifier = Modifier.animateItem())
        }
    }
    for (section in state.search) {
        val sid = section.source.id.value
        item(key = "src:$sid") {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SourceBadge(section.source.id, section.source.name)
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(section.loading, enter = fadeIn(), exit = fadeOut()) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                if (!section.loading && !section.failed) {
                    Text(
                        "${section.results.size} résultat" + if (section.results.size > 1) "s" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        when {
            section.loading && section.results.isEmpty() -> items(3, key = { "ph:$sid:$it" }) { PlaceholderRow() }
            section.failed -> item(key = "fail:$sid") { Hint("Recherche indisponible pour le moment.") }
            section.results.isEmpty() -> item(key = "none:$sid") { Hint("Aucun résultat.") }
            else -> items(section.results, key = { "r:$sid:" + it.info.key.key }) { result ->
                SearchResultRow(
                    result,
                    onTrack = { actions.onTrack(result.info) },
                    onOpenTracked = { onSelect(result.info.key) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun Header(state: LibraryUiState, onOpenSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Fresko",
                style = MaterialTheme.typography.displaySmall.merge(TextStyle(brush = FreskoColors.Brand)),
            )
            val n = state.tracked.size
            val unread = state.toRead.size
            val main = when {
                n == 0 -> "Vos alertes de nouveaux chapitres"
                unread == 0 -> "Tout est lu"
                else -> "$unread à lire sur $n œuvre${if (n > 1) "s" else ""}"
            }
            val subtitle = if (n > 0 && state.lastCheck != null) "$main · vérifié ${relativeTime(state.lastCheck)}" else main
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalIconButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Réglages")
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, onClear: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    TextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        placeholder = { Text("Rechercher une œuvre…") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) { Icon(Icons.Filled.Close, contentDescription = "Effacer") }
            }
        },
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun SectionTitle(title: String, count: String?, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                count,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun TrackedCard(item: TrackedItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val e = item.entity
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(e.coverUrl, e.title, item.source.id, width = 66.dp, height = 94.dp)
            Column(Modifier.weight(1f).padding(start = 14.dp, end = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SourceBadge(item.source.id, item.source.name)
                    if (e.isUnread) {
                        Spacer(Modifier.width(6.dp))
                        NewBadge()
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    e.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        shortChapter(e.chapterNumber, e.chapterLabel),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    e.chapterPublishedAt?.let {
                        Text(
                            "  ·  " + relativeTime(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (e.chapterEarlyAccess) {
                        Spacer(Modifier.width(8.dp))
                        EarlyAccessBadge()
                    }
                }
                if (item.source.hasEarlyAccess && !e.notifyEarlyAccess) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Accès anticipé ignoré",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun NewBadge() {
    Text(
        "NOUVEAU",
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = Modifier
            .clip(CircleShape)
            .background(FreskoColors.Brand)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun SearchResultRow(
    result: SearchResultItem,
    onTrack: () -> Unit,
    onOpenTracked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val info = result.info
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(info.coverUrl, info.title, info.key.sourceId, width = 50.dp, height = 72.dp, corner = 12.dp)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(info.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            info.latestChapter?.let { ch ->
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        shortChapter(ch.number, ch.label) + (ch.publishedAtMillis?.let { "  ·  " + relativeTime(it) } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (ch.isEarlyAccess) {
                        Spacer(Modifier.width(6.dp))
                        EarlyAccessBadge()
                    }
                }
            }
        }
        if (result.tracked) {
            FilledTonalButton(onClick = onOpenTracked, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Suivie")
            }
        } else {
            Button(onClick = onTrack, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Suivre")
            }
        }
    }
}

@Composable
private fun PlaceholderRow() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Placeholder(Modifier.size(50.dp, 72.dp))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Placeholder(Modifier.fillMaxWidth(0.8f).height(14.dp))
            Spacer(Modifier.height(8.dp))
            Placeholder(Modifier.fillMaxWidth(0.4f).height(10.dp))
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun EmptyState(sourceNames: List<String>) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.fresko_mascot),
            contentDescription = null,
            modifier = Modifier.size(180.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("Ne ratez plus un chapitre", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        val where = when (sourceNames.size) {
            0 -> "vos sources"
            1 -> sourceNames[0]
            else -> sourceNames.dropLast(1).joinToString(", ") + " et " + sourceNames.last()
        }
        Text(
            "Recherchez une œuvre sur $where, puis touchez « Suivre ». " +
                "Fresko vous prévient à chaque nouvelle sortie.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (sourceNames.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Aucune source activée : ouvrez les réglages.",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
