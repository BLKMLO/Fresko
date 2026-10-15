package com.fresko.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fresko.core.model.SourceId

/** Couverture avec repli élégant (dégradé + initiale) pendant le chargement ou sans image. */
@Composable
fun Cover(
    url: String?,
    title: String,
    sourceId: SourceId,
    width: Dp,
    height: Dp,
    corner: Dp = 14.dp,
) {
    val accent = SourceStyle.color(sourceId)
    Box(
        Modifier
            .size(width, height)
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.85f), accent.copy(alpha = 0.35f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White.copy(alpha = 0.9f),
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Pastille « ● Asura Scans » aux couleurs de la source. */
@Composable
fun SourceBadge(sourceId: SourceId, name: String, modifier: Modifier = Modifier) {
    val accent = SourceStyle.color(sourceId)
    Row(
        modifier
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
        Text(name.uppercase(), style = MaterialTheme.typography.labelSmall, color = accent, maxLines = 1, softWrap = false)
    }
}

/** Pastille « accès anticipé ». */
@Composable
fun EarlyAccessBadge(modifier: Modifier = Modifier, text: String = "Anticipé") {
    Row(
        modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(11.dp),
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Text(
            text, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.Bold,
            maxLines = 1, softWrap = false,
        )
    }
}

/** Bloc gris pulsé, pour les résultats en cours de chargement. */
@Composable
fun Placeholder(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "placeholder")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(
        modifier
            .alpha(alpha)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}
