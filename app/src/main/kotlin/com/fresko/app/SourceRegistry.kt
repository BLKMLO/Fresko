package com.fresko.app

import com.fresko.core.model.SourceId
import com.fresko.core.network.HttpFetcher
import com.fresko.core.source.Source
import com.fresko.sources.asura.AsuraSource
import com.fresko.sources.demonic.DemonicSource
import com.fresko.sources.natomanga.NatoMangaSource

/**
 * Liste des sites supportés. Pour ajouter un site : créer le module
 * `sources/<nom>`, l'ajouter à settings.gradle.kts et app/build.gradle.kts,
 * puis l'enregistrer ici.
 */
class SourceRegistry(http: HttpFetcher) {
    val all: List<Source> = listOf(
        AsuraSource(http),
        DemonicSource(http),
        NatoMangaSource(http),
    )

    private val byId = all.associateBy { it.id }

    operator fun get(id: SourceId): Source? = byId[id]
}
