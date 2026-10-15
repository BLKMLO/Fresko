package com.fresko.app.ui.library

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: LibraryViewModel, onOpenSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val open: (String) -> Unit = { url ->
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fresko") },
                actions = {
                    IconButton(onClick = { viewModel.refreshCatalog() }, enabled = !state.refreshing) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Rafraîchir le catalogue")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Paramètres")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Rechercher une œuvre") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Effacer")
                        }
                    }
                },
                singleLine = true,
            )
            if (state.sources.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = state.sourceFilter == null,
                            onClick = { viewModel.onSourceFilter(null) },
                            label = { Text("Toutes") },
                        )
                    }
                    items(state.sources, key = { it.id.value }) { chip ->
                        FilterChip(
                            selected = state.sourceFilter == chip.id,
                            onClick = { viewModel.onSourceFilter(chip.id) },
                            label = { Text(chip.name) },
                        )
                    }
                }
            }
            state.message?.let { msg ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(msg, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    if (!state.refreshing) TextButton(onClick = viewModel::dismissMessage) { Text("OK") }
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                if (state.tracked.isNotEmpty()) {
                    item(key = "h_tracked") { SectionHeader("Suivies (${state.tracked.size})") }
                    items(state.tracked, key = { "t_" + it.entity.sourceId + it.entity.workKey }) {
                        WorkRow(it, onToggle = { viewModel.toggleTracked(it) }, onOpen = open)
                    }
                }
                item(key = "h_others") { SectionHeader("Catalogue (${state.others.size})") }
                items(state.others, key = { "o_" + it.entity.sourceId + it.entity.workKey }) {
                    WorkRow(it, onToggle = { viewModel.toggleTracked(it) }, onOpen = open)
                }
                if (state.others.isEmpty() && !state.refreshing) {
                    item(key = "empty") {
                        Text(
                            if (state.query.isBlank()) "Aucune œuvre. Tirez le catalogue avec ⟳."
                            else "Aucun résultat pour « ${state.query} ».",
                            Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Text(
            text,
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun WorkRow(item: WorkItem, onToggle: () -> Unit, onOpen: (String) -> Unit) {
    val e = item.entity
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(e.chapterUrl ?: e.url) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(width = 44.dp, height = 62.dp).clip(RoundedCornerShape(6.dp)),
        ) {
            if (e.coverUrl != null) {
                AsyncImage(
                    model = e.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxSize()) {}
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                e.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (e.tracked) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(item.sourceName, e.chapterLabel).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onToggle) {
            Icon(
                if (e.tracked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (e.tracked) "Ne plus suivre" else "Suivre",
                tint = if (e.tracked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(Modifier.padding(start = 72.dp))
}
