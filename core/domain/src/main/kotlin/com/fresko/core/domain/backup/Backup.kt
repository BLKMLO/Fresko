package com.fresko.core.domain.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Contenu d'une sauvegarde Fresko (fichier JSON lisible). Elle survit à une
 * désinstallation car elle est écrite hors de l'application (dossier choisi par
 * l'utilisateur, ou fichier exporté).
 *
 * Compatibilité : champs nouveaux = valeurs par défaut ; champs inconnus ignorés.
 */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    @SerialName("exported_at") val exportedAt: Long,
    val settings: BackupSettings,
    val works: List<BackupWork>,
) {
    companion object {
        const val FORMAT = "fresko-backup"
        const val VERSION = 1
    }
}

@Serializable
data class BackupSettings(
    @SerialName("interval_minutes") val intervalMinutes: Int,
    @SerialName("disabled_sources") val disabledSources: Set<String> = emptySet(),
)

@Serializable
data class BackupWork(
    val source: String,
    val key: String,
    val title: String,
    val url: String,
    val cover: String? = null,
    @SerialName("tracked_at") val trackedAt: Long? = null,
    val chapter: BackupChapter? = null,
    @SerialName("read_chapter_url") val readChapterUrl: String? = null,
    @SerialName("notify_early_access") val notifyEarlyAccess: Boolean = true,
)

@Serializable
data class BackupChapter(
    val number: Double? = null,
    val label: String,
    val url: String,
    @SerialName("published_at") val publishedAt: Long? = null,
    @SerialName("early_access") val earlyAccess: Boolean = false,
)

class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

object BackupCodec {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(backup: BackupFile): String = json.encodeToString(BackupFile.serializer(), backup)

    /** @throws InvalidBackupException si le fichier n'est pas une sauvegarde Fresko lisible. */
    fun decode(text: String): BackupFile {
        val backup = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: Exception) {
            throw InvalidBackupException("Ce fichier n'est pas une sauvegarde Fresko.", e)
        }
        if (backup.format != BackupFile.FORMAT) {
            throw InvalidBackupException("Ce fichier n'est pas une sauvegarde Fresko.")
        }
        if (backup.version > BackupFile.VERSION) {
            throw InvalidBackupException("Sauvegarde créée par une version plus récente de Fresko : mettez l'app à jour.")
        }
        return backup
    }
}
