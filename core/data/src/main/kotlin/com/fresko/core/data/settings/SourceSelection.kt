package com.fresko.core.data.settings

/** Calcul des sources désactivées, avec reprise de l'ancien format (logique pure, testée). */
object SourceSelection {

    /** Sources qui existaient quand l'ancien format « activées » a été introduit (v0.1.0). */
    val LEGACY_SOURCES = setOf("asura", "demonic")

    /**
     * @param legacyEnabled ancienne liste des sources activées (null si jamais écrite)
     * @param disabled nouvelle liste des sources désactivées (null si jamais écrite)
     *
     * Reprise : seules les sources qui existaient avec l'ancien format peuvent être
     * considérées comme désactivées ; les sources plus récentes (NatoManga…) sont activées.
     */
    fun disabled(legacyEnabled: Set<String>?, disabled: Set<String>?): Set<String> = when {
        disabled != null -> disabled
        legacyEnabled != null -> LEGACY_SOURCES - legacyEnabled
        else -> emptySet()
    }
}
