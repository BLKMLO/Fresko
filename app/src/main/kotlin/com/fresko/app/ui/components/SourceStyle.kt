package com.fresko.app.ui.components

import androidx.compose.ui.graphics.Color
import com.fresko.core.model.SourceId

/** Couleur d'accent de chaque source (une source inconnue reçoit une couleur neutre). */
object SourceStyle {
    private val colors = mapOf(
        "asura" to Color(0xFF8B5CF6),
        "demonic" to Color(0xFFEF4462),
    )

    fun color(id: SourceId): Color = colors[id.value] ?: Color(0xFF64748B)
}
