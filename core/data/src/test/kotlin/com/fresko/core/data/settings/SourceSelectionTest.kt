package com.fresko.core.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceSelectionTest {

    @Test
    fun `aucun reglage - tout est actif`() {
        assertEquals(emptySet<String>(), SourceSelection.disabled(null, null))
    }

    @Test
    fun `ancien format ecrit en v0_2 - NatoManga redevient active`() {
        // L'utilisateur avait touché un interrupteur en v0.2 : {asura, demonic} enregistré.
        assertEquals(emptySet<String>(), SourceSelection.disabled(setOf("asura", "demonic"), null))
    }

    @Test
    fun `ancien format - une source desactivee le reste`() {
        assertEquals(setOf("demonic"), SourceSelection.disabled(setOf("asura", "natomanga"), null))
    }

    @Test
    fun `nouveau format prioritaire`() {
        assertEquals(setOf("natomanga"), SourceSelection.disabled(setOf("asura"), setOf("natomanga")))
    }
}
