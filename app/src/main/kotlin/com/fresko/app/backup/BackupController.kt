package com.fresko.app.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.fresko.core.data.backup.BackupMapper
import com.fresko.core.data.repository.WorkRepository
import com.fresko.core.data.settings.SettingsRepository
import com.fresko.core.domain.backup.BackupCodec
import com.fresko.core.domain.backup.BackupFile
import com.fresko.core.domain.backup.BackupSettings
import com.fresko.core.model.SourceId
import com.fresko.feature.checker.CheckScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sauvegarde des œuvres suivies et des réglages dans un fichier **hors de l'application**
 * (dossier choisi par l'utilisateur via le sélecteur Android) : elle survit donc à une
 * désinstallation. Après réinstallation, rechoisir le même dossier suffit : le fichier
 * existant est retrouvé et restauré.
 */
class BackupController(
    context: Context,
    private val works: WorkRepository,
    private val settings: SettingsRepository,
    private val scheduler: () -> CheckScheduler,
    private val allSourceIds: List<SourceId>,
) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    /** Empreinte du dernier contenu écrit automatiquement (évite les écritures inutiles). */
    @Volatile private var lastWrittenFingerprint: String? = null

    suspend fun build(): BackupFile {
        val s = settings.current()
        return BackupFile(
            exportedAt = System.currentTimeMillis(),
            settings = BackupSettings(
                intervalMinutes = s.intervalMinutes,
                disabledSources = allSourceIds.filter { it !in s.enabledSources }.map { it.value }.toSet(),
                wifiOnly = s.wifiOnly,
            ),
            works = works.snapshot().map(BackupMapper::toBackup),
        )
    }

    /** Export manuel vers un fichier choisi par l'utilisateur. */
    suspend fun exportTo(uri: Uri): Int = withContext(Dispatchers.IO) {
        val backup = build()
        write(uri, BackupCodec.encode(backup))
        backup.works.size
    }

    /** Résultat d'une restauration : œuvres ajoutées, et œuvres déjà suivies (conservées). */
    data class RestoreResult(val added: Int, val alreadyTracked: Int)

    /** Restauration manuelle : ajoute les œuvres absentes et applique les réglages. */
    suspend fun importFrom(uri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        val text = resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("Fichier illisible")
        restore(BackupCodec.decode(text), applySettings = true)
    }

    private suspend fun restore(backup: BackupFile, applySettings: Boolean): RestoreResult {
        val added = works.restore(backup.works.map(BackupMapper::toEntity))
        if (applySettings) {
            settings.setDisabledSources(backup.settings.disabledSources)
            settings.setIntervalMinutes(backup.settings.intervalMinutes)
            settings.setWifiOnly(backup.settings.wifiOnly)
            val s = settings.current()
            scheduler().schedule(s.intervalMinutes, replace = true, wifiOnly = s.wifiOnly)
        }
        return RestoreResult(added, backup.works.size - added)
    }

    /**
     * Choix du dossier de sauvegarde automatique. Si une sauvegarde Fresko s'y trouve déjà, ses
     * œuvres absentes de l'app sont ajoutées **avant** que le fichier soit réécrit : sinon,
     * après une réinstallation, suivre une œuvre puis choisir le dossier écrasait l'ancienne
     * sauvegarde. Les réglages sont repris seulement si l'app ne suivait encore rien.
     * Un fichier illisible n'est jamais écrasé (erreur).
     * @return nombre d'œuvres ajoutées depuis la sauvegarde trouvée.
     */
    suspend fun setFolder(treeUri: Uri): Int = withContext(Dispatchers.IO) {
        resolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val existing = findChild(treeUri, treeDocId, FILE_NAME)
        var restored = 0
        if (existing != null) {
            val text = resolver.openInputStream(existing)?.use { it.readBytes().decodeToString() }
                ?: error("Sauvegarde existante illisible : elle n'a pas été modifiée.")
            val wasEmpty = works.snapshot().isEmpty()
            restored = restore(BackupCodec.decode(text), applySettings = wasEmpty).added
        }
        val file = existing ?: DocumentsContract.createDocument(
            resolver,
            DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId),
            "application/json",
            FILE_NAME,
        ) ?: error("Impossible de créer le fichier de sauvegarde dans ce dossier")
        settings.setBackupTarget(file.toString(), folderLabel(treeDocId))
        lastWrittenFingerprint = null
        writeAuto()
        restored
    }

    suspend fun clearFolder() {
        // Les seuls accès dossier conservés par l'app sont ceux de la sauvegarde
        resolver.persistedUriPermissions.forEach {
            runCatching {
                resolver.releasePersistableUriPermission(
                    it.uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        settings.setBackupTarget(null, null)
        _lastError.value = null
    }

    /** Sauvegarde automatique, déclenchée à chaque changement (œuvres, chapitres, réglages). */
    @OptIn(FlowPreview::class)
    fun startAuto(scope: CoroutineScope) {
        scope.launch {
            combine(works.observeTracked(), settings.settings) { w, s -> w to s }
                .debounce(3_000)
                .collect { writeAuto() }
        }
    }

    private suspend fun writeAuto() = withContext(Dispatchers.IO) {
        val target = settings.current().backupFileUri ?: return@withContext
        val backup = build()
        val fingerprint = BackupCodec.encode(backup.copy(exportedAt = 0))
        if (fingerprint == lastWrittenFingerprint) return@withContext
        try {
            write(Uri.parse(target), BackupCodec.encode(backup))
            lastWrittenFingerprint = fingerprint
            settings.setLastBackupAt(backup.exportedAt)
            _lastError.value = null
        } catch (e: Exception) {
            Log.w(TAG, "Sauvegarde automatique impossible", e)
            _lastError.value = "Échec de la sauvegarde automatique : rechoisissez le dossier."
        }
    }

    private fun write(uri: Uri, text: String) {
        // "wt" : remplace le contenu (sinon un fichier plus court garderait la fin de l'ancien)
        resolver.openOutputStream(uri, "wt")?.use { it.write(text.encodeToByteArray()) }
            ?: error("Écriture impossible")
    }

    private fun findChild(treeUri: Uri, parentDocId: String, name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == name) return DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(0))
            }
        }
        return null
    }

    /** « primary:Documents/Fresko » → « Documents/Fresko ». */
    private fun folderLabel(treeDocId: String): String =
        treeDocId.substringAfter(':', treeDocId).ifEmpty { "Stockage principal" }

    companion object {
        const val FILE_NAME = "fresko-sauvegarde.json"
        private const val TAG = "FreskoBackup"
    }
}
