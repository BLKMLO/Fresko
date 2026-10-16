package com.fresko.app.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.fresko.app.ui.library.LibraryActions
import com.fresko.app.ui.library.LibraryContent
import com.fresko.app.ui.library.LibraryUiState
import com.fresko.app.ui.library.SearchResultItem
import com.fresko.app.ui.library.SourceSearch
import com.fresko.app.ui.library.SourceUi
import com.fresko.app.ui.library.TrackedItem
import com.fresko.app.ui.settings.SettingsContent
import com.fresko.app.ui.settings.SettingsUiState
import com.fresko.app.ui.settings.SourceSetting
import com.fresko.app.ui.theme.FreskoTheme
import com.fresko.core.data.db.WorkEntity
import com.fresko.core.model.ChapterInfo
import com.fresko.core.model.SourceId
import com.fresko.core.model.WorkInfo
import com.fresko.core.model.WorkKey
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Génère des captures des écrans avec des données d'exemple (titres réels relevés
 * le 2026-10-15). Les couvertures ne sont pas chargées (pas de réseau) : on voit le repli.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val now = System.currentTimeMillis()
    private val asura = SourceUi(SourceId("asura"), "Asura Scans", hasEarlyAccess = true)
    private val demonic = SourceUi(SourceId("demonic"), "Demonic Scans", hasEarlyAccess = false)
    private val nato = SourceUi(SourceId("natomanga"), "NatoManga", hasEarlyAccess = false)

    private fun work(src: SourceUi, key: String, title: String, ch: Double, agoMin: Long, early: Boolean = false, notifyEarly: Boolean = true, read: Boolean = false) =
        TrackedItem(
            WorkEntity(
                sourceId = src.id.value, workKey = key, title = title, url = "https://x/$key", coverUrl = null,
                tracked = true, trackedAt = now - 20 * 24 * 3600_000L,
                chapterNumber = ch, chapterLabel = "Chapitre ${ch.toInt()}", chapterUrl = "https://x/$key/$ch",
                chapterPublishedAt = now - agoMin * 60_000, chapterEarlyAccess = early, notifyEarlyAccess = notifyEarly,
                readChapterUrl = if (read) "https://x/$key/$ch" else null,
            ),
            src,
        )

    private val tracked = listOf(
        work(asura, "solo", "Solo Max-Level Newbie", 281.0, 38, early = true),
        work(nato, "taming", "The Taming", 39.0, 70),
        work(demonic, "demon", "The Demon King Overrun by Heroes", 52.0, 95, read = true),
        work(asura, "sword", "Sword of the Undying", 30.0, 300, notifyEarly = false, read = true),
        work(demonic, "max", "Max Level Player", 158.0, 26 * 60, read = true),
        work(asura, "raising", "Raising Villains the Right Way", 56.0, 4 * 24 * 60, read = true),
    )

    private fun shot(name: String, dark: Boolean, multipleRoots: Boolean = false, content: @androidx.compose.runtime.Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers(if (dark) "+night" else "+notnight")
        compose.activity.recreate()
        compose.setContent { FreskoTheme { content() } }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        // Toutes les fenêtres (activité + éventuelle feuille modale), dessinées l'une sur l'autre.
        val views = windowRootViews().ifEmpty { listOf(compose.activity.window.decorView) }
        val base = views.first()
        val bitmap = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val target = if (multipleRoots) views else views.take(1)
        for (v in target) {
            val loc = IntArray(2).also { v.getLocationOnScreen(it) }
            canvas.save()
            canvas.translate(loc[0].toFloat(), loc[1].toFloat())
            v.draw(canvas)
            canvas.restore()
        }
        val dir = File(System.getProperty("fresko.screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "$name-${if (dark) "dark" else "light"}.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun windowRootViews(): List<View> = runCatching {
        val wmg = Class.forName("android.view.WindowManagerGlobal")
        val instance = wmg.getMethod("getInstance").invoke(null)
        val field = wmg.getDeclaredField("mViews").apply { isAccessible = true }
        (field.get(instance) as List<View>).filter { it.isAttachedToWindow && it.width > 0 }
    }.getOrDefault(emptyList())

    @Test fun libraryDark() = shot("1-library", dark = true) {
        LibraryContent(LibraryUiState(tracked = tracked, enabledSources = listOf(asura, demonic, nato), lastCheck = now - 4 * 60_000), LibraryActions())
    }

    @Test fun libraryLight() = shot("1-library", dark = false) {
        LibraryContent(LibraryUiState(tracked = tracked, enabledSources = listOf(asura, demonic, nato), lastCheck = now - 4 * 60_000), LibraryActions())
    }

    @Test fun emptyDark() = shot("2-empty", dark = true) {
        LibraryContent(LibraryUiState(enabledSources = listOf(asura, demonic, nato)), LibraryActions())
    }

    private fun info(src: SourceUi, key: String, title: String, ch: Double?, agoMin: Long = 60, early: Boolean = false) = WorkInfo(
        WorkKey(src.id, key), title, "https://x/$key", null,
        ch?.let { ChapterInfo(it, "Chapitre ${it.toInt()}", "https://x", now - agoMin * 60_000, early) },
    )

    private val searchState = LibraryUiState(
        tracked = tracked,
        query = "solo",
        matchingTracked = tracked.take(1),
        enabledSources = listOf(asura, demonic, nato),
        search = listOf(
            SourceSearch(
                asura, loading = false, failed = false,
                results = listOf(
                    SearchResultItem(info(asura, "solo", "Solo Max-Level Newbie", 281.0, 38, early = true), tracked = true),
                    SearchResultItem(info(asura, "solosword", "Solo Swordmaster", 74.0, 3 * 60), tracked = false),
                    SearchResultItem(info(asura, "emperor", "Emperor of Solo Play", 19.0, 2 * 24 * 60), tracked = false),
                ),
            ),
            SourceSearch(demonic, loading = true, failed = false, results = emptyList()),
        ),
    )

    @Test fun searchDark() = shot("3-search", dark = true) { LibraryContent(searchState, LibraryActions()) }

    @Test fun searchLight() = shot("3-search", dark = false) { LibraryContent(searchState, LibraryActions()) }

    @Test fun sheetDark() = shot("4-sheet", dark = true, multipleRoots = true) {
        LibraryContent(
            LibraryUiState(tracked = tracked, enabledSources = listOf(asura, demonic, nato), lastCheck = now),
            LibraryActions(),
            initialSelection = tracked[0].entity.key,
        )
    }

    private val settingsState = SettingsUiState(
        sources = listOf(
            SourceSetting(asura.id, asura.name, true, now - 4 * 60_000, 3),
            SourceSetting(demonic.id, demonic.name, false, now - 9 * 60_000, 2),
            SourceSetting(nato.id, nato.name, true, now - 4 * 60_000, 1),
        ),
        intervalMinutes = 10,
    )

    @Test fun settingsDark() = shot("5-settings", dark = true) { SettingsContent(settingsState) }

    @Test fun settingsLight() = shot("5-settings", dark = false) { SettingsContent(settingsState) }
}
