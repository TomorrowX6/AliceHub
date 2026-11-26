package io.github.kichikuou.xsystem4

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal const val SAVE_IMPORT_TMP_PREFIX = ".save_import_tmp"
private const val SAVE_BACKUP_ROOT = "save_backups"
private const val SAVE_BACKUP_KEEP = 5
private const val REQ_IMPORT_DIR = 102
private const val REQ_IMPORT_FILES = 103
private const val REQ_EXPORT_ZIP = 104
private const val REQ_EXPORT_DIR = 105
private const val REQ_EXPORT_SINGLE_FILE = 106

data class SaveTarget(
    val displayName: String,
    val dirName: String,
    val saveDir: File,
    val externalGameDir: File?
)

data class SaveFileEntry(
    val file: File,
    val relativePath: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val tag: String
)

private class SaveFileAdapter(
    private val activity: Activity,
    private var entries: List<SaveFileEntry>
) : BaseAdapter() {

    var accentColor: Int? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun update(newEntries: List<SaveFileEntry>) {
        entries = newEntries
        notifyDataSetChanged()
    }

    override fun getCount(): Int = entries.size
    override fun getItem(position: Int): SaveFileEntry = entries[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(activity)
            .inflate(R.layout.list_item_entry, parent, false)
        val entry = entries[position]

        view.findViewById<TextView>(R.id.title).text = entry.relativePath
        val tagToneValue = tagTone(entry.file.name)
        UiKit.bindChips(
            view,
            if (entry.tag.isNotEmpty()) {
                listOf(UiKit.Chip(entry.tag, tagToneValue))
            } else {
                emptyList()
            }
        )
        val customAccent = accentColor
        if (entry.tag.isNotEmpty() && tagToneValue == UiKit.Tone.PRIMARY && customAccent != null) {
            view.findViewById<TextView>(R.id.chip1)?.let { UiKit.styleAccentChip(it, customAccent) }
        }
        val dateStr = dateFormat.format(Date(entry.lastModified))
        view.findViewById<TextView>(R.id.meta).text = "${formatFileSize(entry.sizeBytes)} · $dateStr"
        return view
    }

    /** Game-wide saves stand out from the per-slot ones; crash logs get a warning tint. */
    private fun tagTone(fileName: String): UiKit.Tone {
        val lower = ArchiveExtractor.repairSjisAsGbkMojibake(fileName).lowercase(Locale.ROOT)
        return when {
            lower in GLOBAL_SAVE_FILES ||
                GameCatalog.isHarukaSystemSaveFileName(fileName) ||
                lower.endsWith(".msgskip") -> UiKit.Tone.PRIMARY
            lower.endsWith(".log") -> UiKit.Tone.WARNING
            else -> UiKit.Tone.NEUTRAL
        }
    }

    private companion object {
        val GLOBAL_SAVE_FILES = setOf(
            "globalsave.asd", "globalsavedata.asd", "collection.asd", "msgskip.asd",
            "afcgmode.asd", "afcommon.asd", "afconfig.asd", "afbgmmode.asd",
            "ixseal_afcgmode.asd", "ixseal_afcommon.asd", "ixseal_afconfig.asd",
            "ixseal_afbgmmode.asd", "ixseal_afinfo.asd", "ixseal_afmemmode.asd",
            "ixseal_extra.sav",
            "妻しぼりsys.asd", "嵢偟傏傝sys.asd", "tsumashiborisys.asd",
            "config.asd", "progress.asd", "joypad.asd",
            "keyfile.dat", "msgskipflag.sav",
            "已读文本跳过.asd", "既読スキップ.asd",
            "共有.asd", "ｃｇモード.asd", "回想モード.asd", "音楽モード.asd", "3dsetting.asd",
            "システム共通データ.asd",
            "超昂閃忍ハルカsf.asd", "超昂閃忍ハルカsys.asd", "超昂閃忍ハルカfn.asd",
            "超昂闪忍遥sf.asd", "超昂闪忍遥sys.asd", "超昂闪忍遥fn.asd",
            "harukasf.asd", "harukasys.asd", "harukafn.asd",
            "gameenquete.asd", "commonsystemdata.sav", "volume.sav"
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024L -> "${bytes} B"
        bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
    }
}

internal fun isGameProcessRunning(context: Context): Boolean =
    GameCatalog.isGameProcessRunning(context)

internal fun ensureGameNotRunning(activity: Activity, onProceed: () -> Unit) {
    if (!GameCatalog.isGameProcessRunning(activity)) {
        onProceed()
        return
    }
    val lang = AppSettings.resolveLocale(activity).language
    val title = when {
        lang.startsWith("zh") -> "游戏正在运行"
        lang.startsWith("ja") -> "ゲームが実行中です"
        else -> "Game Is Running"
    }
    val message = when {
        lang.startsWith("zh") -> "请先结束后台游戏再执行此操作。"
        lang.startsWith("ja") -> "バックグラウンドのゲームを終了して続行しますか？"
        else -> "Close the background game and continue?"
    }
    val confirmLabel = when {
        lang.startsWith("zh") -> "结束并继续"
        lang.startsWith("ja") -> "終了して続行"
        else -> "Close & Continue"
    }
    AlertDialog.Builder(activity)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(confirmLabel) { _, _ ->
            GameCatalog.killRunningGameProcess(activity)
            GameCatalog.waitForGameProcessExit(
                activity = activity,
                onExited = { onProceed() },
                onTimeout = { GameCatalog.showGameStopFailedToast(activity) }
            )
        }
        .setNegativeButton(R.string.cancel, null)
        .show()
}

// Status-card views live in save_manager_header, added with addHeaderView. Lint only
// sees the activity layout, so those ids look missing.
@SuppressLint("MissingInflatedId")
class SaveManagerActivity : ThemedActivity() {
    companion object {
        const val EXTRA_DIR_NAME = "EXTRA_DIR_NAME"
        const val EXTRA_GAME_ID = "EXTRA_GAME_ID"

        private const val STATE_TARGET_DIR = "save_mgr_target_dir"
        private const val STATE_EXPORT_AS_PC = "save_mgr_export_as_pc"
        private const val STATE_SINGLE_EXPORT_FILE = "save_mgr_single_export_file"
        private const val STATE_SINGLE_EXPORT_AS_PC = "save_mgr_single_export_as_pc"

        // Import/export/restore jobs outlive the activity instance: rotating or switching
        // dark mode must not orphan a half-done copy (or let a second one start alongside
        // it). Results go to whichever instance is current when the job ends via WeakReference.
        private val opScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var opJob: Job? = null
        private var opProgressMessage: String? = null
        private var liveHostRef: java.lang.ref.WeakReference<SaveManagerActivity>? = null

        private fun launchSaveOp(block: suspend CoroutineScope.() -> Unit): Job =
            opScope.launch(block = block)

        private fun onHost(action: (SaveManagerActivity) -> Unit) {
            val host = liveHostRef?.get()
            if (host != null && !host.isFinishing && !host.isDestroyed) {
                action(host)
            }
        }

        private fun hostMessage(appCtx: Context, title: String, message: String) {
            val host = liveHostRef?.get()
            if (host != null && !host.isFinishing && !host.isDestroyed) {
                host.showMessageDialog(title, message)
            } else {
                Toast.makeText(appCtx, "$title: $message", Toast.LENGTH_LONG).show()
            }
        }

        private fun gameRunningAbortMessage(context: Context): String =
            GamepadBridge.tr(
                context,
                "游戏正在运行，已取消存档写入以防损坏",
                "ゲームが実行中のため、セーブデータの書き込みを中止しました",
                "Game is running; save modification aborted to prevent corruption"
            )

        private fun ensureProcessStoppedOnIo(context: Context) {
            if (GameCatalog.isGameProcessRunning(context)) {
                throw IOException(gameRunningAbortMessage(context))
            }
        }

        private fun hasExistingSavesInDir(dir: File): Boolean =
            dir.walkTopDown().any { it.isFile && !it.name.startsWith(".") }

        private fun saveBackupDirFor(filesDir: File, dirName: String): File =
            File(File(filesDir, SAVE_BACKUP_ROOT), File(dirName).name)

        private fun listSaveBackupsFor(filesDir: File, dirName: String): List<File> =
            saveBackupDirFor(filesDir, dirName).listFiles()
                ?.filter { it.isFile && it.name.endsWith(".zip") }
                ?.sortedByDescending { it.name }
                .orEmpty()

        /**
         * Zips every save in [saveDir] into the backup folder before an import, clear, or restore
         * overwrites them, keeping the newest [SAVE_BACKUP_KEEP]. Returns null if there was
         * nothing to back up. Runs on an IO thread without capturing an Activity reference.
         */
        private fun backupSavesInDir(filesDir: File, saveDir: File, dirName: String): File? {
            val files = saveDir.walkTopDown().filter { it.isFile && !it.name.startsWith(".") }.toList()
            if (files.isEmpty()) return null
            val dir = saveBackupDirFor(filesDir, dirName)
            dir.mkdirs()
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            val out = File(dir, "SaveData_$stamp.zip")
            val tmp = File(dir, ".${out.name}.tmp")
            try {
                ZipOutputStream(tmp.outputStream().buffered()).use { zos ->
                    for (f in files) {
                        zos.putNextEntry(ZipEntry("SaveData/" + f.relativeTo(saveDir).invariantSeparatorsPath))
                        f.inputStream().buffered().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
                if (!tmp.isFile || tmp.length() <= 0L) {
                    throw IOException("Backup archive is empty")
                }
                if (!tmp.renameTo(out)) {
                    throw IOException("Cannot write save backup")
                }
            } catch (e: Exception) {
                tmp.delete()
                throw e
            }
            listSaveBackupsFor(filesDir, dirName).drop(SAVE_BACKUP_KEEP).forEach { it.delete() }
            return out
        }

        private fun restoreSavesFromBackupSafely(
            appCtx: Context,
            backup: File,
            saveDir: File,
            dirName: String
        ): Int {
            ensureProcessStoppedOnIo(appCtx)
            return SaveBackupRestore.restore(backup, saveDir, beforeSwap = {
                ensureProcessStoppedOnIo(appCtx)
                backupSavesInDir(appCtx.filesDir, saveDir, dirName)
            })
        }

        private fun formatArchiveImportErrorForContext(context: Context, e: Throwable): String {
            val raw = e.message ?: ""
            val lang = AppSettings.resolveLocale(context).language
            if (raw.startsWith("ENCRYPTED:")) {
                return when {
                    lang.startsWith("zh") -> "不支持加密压缩包，请解压后导入"
                    lang.startsWith("ja") -> "パスワード付きアーカイブには非対応です"
                    else -> "Password-protected archives are not supported"
                }
            }
            if (raw.startsWith("ENOSPC:")) {
                return context.getString(R.string.import_no_space)
            }
            return e.localizedMessage ?: context.getString(R.string.error)
        }

        private data class DocNode(val documentId: String, val name: String, val isDirectory: Boolean)

        private fun listDocumentChildrenFor(
            context: Context,
            treeUri: Uri,
            parentDocId: String
        ): List<DocNode> {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            val result = mutableListOf<DocNode>()
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIdx) ?: continue
                    val name = cursor.getString(nameIdx) ?: continue
                    val mime = cursor.getString(mimeIdx)
                    val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    result.add(DocNode(id, name, isDir))
                }
            }
            return result
        }

        private fun isInsideStatic(root: File, file: File): Boolean {
            val r = root.canonicalPath
            val f = file.canonicalPath
            return f.startsWith(r + File.separator)
        }

        private fun extractSaveArchiveToTargetStatic(
            context: Context,
            uri: Uri,
            targetDir: File,
            paths: SaveImportPaths,
            writes: SaveImportPaths.ImportWrites
        ): Int {
            val tempDir = File(context.filesDir, "$SAVE_IMPORT_TMP_PREFIX${System.nanoTime()}")
            tempDir.mkdirs()
            try {
                ArchiveExtractor.extractUriToDirectory(
                    context = context,
                    uri = uri,
                    destDir = tempDir,
                    tempScratchParent = context.cacheDir
                )
                ensureProcessStoppedOnIo(context)
                return paths.importExtractedArchive(tempDir, targetDir, writes)
            } finally {
                tempDir.deleteRecursively()
            }
        }

        private fun copyDocumentTreeToInternalStatic(
            context: Context,
            treeUri: Uri,
            parentDocId: String,
            destDir: File,
            paths: SaveImportPaths,
            writes: SaveImportPaths.ImportWrites,
            rootDir: File = destDir
        ): Int {
            destDir.mkdirs()
            var count = 0
            for (child in listDocumentChildrenFor(context, treeUri, parentDocId)) {
                if (child.name.startsWith(".")) continue
                val cleanName = paths.nameForImport(child.name, destDir == rootDir && !child.isDirectory) ?: continue
                if (child.isDirectory) {
                    val subDir = File(destDir, cleanName)
                    if (!isInsideStatic(rootDir, subDir)) continue
                    count += copyDocumentTreeToInternalStatic(context, treeUri, child.documentId, subDir, paths, writes, rootDir)
                } else {
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, child.documentId)
                    val outFile = File(destDir, cleanName)
                    if (!isInsideStatic(rootDir, outFile)) continue
                    val tmpFile = File(destDir, ".imp_${System.nanoTime()}.tmp")
                    try {
                        context.contentResolver.openInputStream(docUri)?.use { input ->
                            tmpFile.outputStream().buffered().use { output ->
                                input.copyTo(output)
                            }
                            writes.write(tmpFile, outFile)
                            count++
                        }
                    } finally {
                        tmpFile.delete()
                    }
                }
            }
            return count
        }

        private fun queryDisplayNameStatic(context: Context, uri: Uri): String? {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) {
                    return cursor.getString(idx)
                }
            }
            return uri.lastPathSegment
        }

        private fun copyExportFileStatic(
            context: Context,
            srcFile: File,
            outStream: java.io.OutputStream,
            asPc: Boolean
        ): Boolean {
            if (asPc) {
                return GameModifierBridge.copySaveFileForPc(srcFile, outStream, context.cacheDir)
            }
            srcFile.inputStream().buffered().use { input ->
                input.copyTo(outStream)
            }
            return false
        }

        private fun getOrCreateSubdirStatic(
            context: Context,
            treeUri: Uri,
            parentDocId: String,
            dirName: String
        ): String {
            val existing = listDocumentChildrenFor(context, treeUri, parentDocId)
                .firstOrNull { it.isDirectory && it.name == dirName }
            if (existing != null) return existing.documentId

            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
            val createdUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentUri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                dirName
            ) ?: throw IOException("Failed to create directory: $dirName")
            return DocumentsContract.getDocumentId(createdUri)
        }

        private fun formatExportDoneMessageForContext(
            context: Context,
            exported: Int,
            converted: Int,
            asPc: Boolean,
            defaultMsgResId: Int
        ): String {
            return if (asPc && converted > 0) {
                context.getString(R.string.save_export_pc_converted_done_msg, exported, converted)
            } else {
                context.getString(defaultMsgResId, exported)
            }
        }

        private fun startRestoreBackupOp(
            appCtx: Context,
            backup: File,
            targetDir: File,
            targetDirName: String
        ): Job = launchSaveOp {
            try {
                val count = withContext(Dispatchers.IO) {
                    restoreSavesFromBackupSafely(appCtx, backup, targetDir, targetDirName)
                }
                onHost {
                    it.dismissProgress()
                    it.refreshFileList()
                }
                hostMessage(
                    appCtx,
                    appCtx.getString(R.string.save_import_done_title),
                    appCtx.getString(R.string.save_restore_backup_done_msg, count)
                )
            } catch (e: Exception) {
                Log.e("SaveManager", "Failed to restore save backup", e)
                onHost {
                    it.dismissProgress()
                    it.refreshFileList()
                }
                hostMessage(
                    appCtx,
                    appCtx.getString(R.string.save_import_failed_title),
                    formatArchiveImportErrorForContext(appCtx, e)
                )
            }
        }

        @JvmStatic
        fun saveImportPathsForTarget(appCtx: Context, target: SaveTarget): SaveImportPaths {
            val gameDir = target.externalGameDir
            val isDsrTarget = target.dirName.endsWith(Item.DAISENGOKU_SAVE_DIR_SUFFIX)
            if (isDsrTarget) {
                val dsrIni = gameDir?.let { dir ->
                    System4BootEntry.bootConfigurations(dir)
                        .firstOrNull { System4BootEntry.iniUsesDaiSengokuSaveFolder(it.ini) == true }
                        ?.ini
                }
                return SaveImportPaths(gameDir, dsrIni, if (dsrIni == null) "SaveDataDSR" else null)
            }
            val launchTarget = gameDir?.let { System4BootEntry.resolveLaunchTarget(appCtx, it) }
            val targetUsesDsr = gameDir != null && launchTarget != null &&
                System4BootEntry.usesDaiSengokuSaveFolder(gameDir, launchTarget)
            return if (targetUsesDsr) {
                SaveImportPaths(gameDir, null, "")
            } else {
                SaveImportPaths(gameDir, launchTarget)
            }
        }

        private fun startImportDirectoryOp(
            appCtx: Context,
            treeUri: Uri,
            target: SaveTarget
        ): Job = launchSaveOp {
            try {
                val count = withContext(Dispatchers.IO) {
                    ensureProcessStoppedOnIo(appCtx)
                    val targetDir = target.saveDir
                    targetDir.mkdirs()
                    backupSavesInDir(appCtx.filesDir, targetDir, target.dirName)
                    val paths = saveImportPathsForTarget(appCtx, target)
                    val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
                    val source = paths.sourceRoot(
                        DocNode(rootDocId, "", true),
                        { listDocumentChildrenFor(appCtx, treeUri, it.documentId) },
                        DocNode::name,
                        DocNode::isDirectory
                    )
                    ensureProcessStoppedOnIo(appCtx)
                    copyDocumentTreeToInternalStatic(
                        appCtx, treeUri, source.documentId, targetDir, paths, SaveImportPaths.ImportWrites()
                    )
                }
                onHost {
                    it.dismissProgress()
                    it.refreshFileList()
                }
                hostMessage(
                    appCtx,
                    appCtx.getString(R.string.save_import_done_title),
                    appCtx.getString(R.string.save_import_dir_done_msg, count)
                )
            } catch (e: Exception) {
                Log.e("SaveManager", "Failed to import saves from directory", e)
                onHost {
                    it.dismissProgress()
                    it.refreshFileList()
                }
                hostMessage(
                    appCtx,
                    appCtx.getString(R.string.save_import_failed_title),
                    formatArchiveImportErrorForContext(appCtx, e)
                )
            }
        }

        private fun startImportFilesOp(
            appCtx: Context,
            uris: List<Uri>,
            target: SaveTarget
        ): Job = launchSaveOp {
            val writes = SaveImportPaths.ImportWrites()
            try {
                val count = withContext(Dispatchers.IO) {
                    ensureProcessStoppedOnIo(appCtx)
                    val targetDir = target.saveDir
                    targetDir.mkdirs()
                    backupSavesInDir(appCtx.filesDir, targetDir, target.dirName)
                    val paths = saveImportPathsForTarget(appCtx, target)
                    val rootCanonical = targetDir.canonicalFile
                    var imported = 0
                    for (uri in uris) {
                        ensureProcessStoppedOnIo(appCtx)
                        val fileName = queryDisplayNameStatic(appCtx, uri)
                            ?: "save_${System.currentTimeMillis()}.asd"
                        val safeName = paths.nameForImport(fileName, atRoot = true) ?: continue
                        if (ArchiveExtractor.isSupportedArchiveName(safeName)) {
                            imported += extractSaveArchiveToTargetStatic(appCtx, uri, targetDir, paths, writes)
                        } else {
                            val dst = File(rootCanonical, safeName).canonicalFile
                            if (!dst.path.startsWith(rootCanonical.path + File.separator)) continue
                            val tmpDst = File(rootCanonical, ".imp_${System.nanoTime()}.tmp")
                            try {
                                appCtx.contentResolver.openInputStream(uri)?.use { input ->
                                    tmpDst.outputStream().buffered().use { output ->
                                        input.copyTo(output)
                                    }
                                    writes.write(tmpDst, dst)
                                    imported++
                                }
                            } finally {
                                tmpDst.delete()
                            }
                        }
                    }
                    imported + writes.finishFallbacks()
                }
                onHost {
                    it.dismissProgress()
                    it.refreshFileList()
                }
                hostMessage(
                    appCtx,
                    appCtx.getString(R.string.save_import_done_title),
                    appCtx.getString(R.string.save_import_files_done_msg, count)
                )
            } catch (e: Exception) {
                Log.e("SaveManager", "Failed to import save files", e)
                onHost {
                    it.dismissProgress()
                    it.refreshFileList()
                }
                hostMessage(
                    appCtx,
                    appCtx.getString(R.string.save_import_failed_title),
                    formatArchiveImportErrorForContext(appCtx, e)
                )
            } finally {
                withContext(Dispatchers.IO + NonCancellable) { writes.close() }
            }
        }

        private fun startExportZipOp(
            appCtx: Context,
            uri: Uri,
            entriesToExport: List<SaveFileEntry>,
            asPc: Boolean
        ): Job = launchSaveOp {
            try {
                val (count, converted) = withContext(Dispatchers.IO) {
                    val outStream = appCtx.contentResolver.openOutputStream(uri)
                        ?: throw IOException("Failed to create target ZIP file")
                    var exported = 0
                    var convCount = 0
                    ZipOutputStream(outStream.buffered()).use { zos ->
                        for (entry in entriesToExport) {
                            if (!entry.file.exists()) continue
                            val zipEntry = ZipEntry("SaveData/${entry.relativePath}")
                            zipEntry.time = entry.lastModified
                            zos.putNextEntry(zipEntry)
                            if (copyExportFileStatic(appCtx, entry.file, zos, asPc)) {
                                convCount++
                            }
                            zos.closeEntry()
                            exported++
                        }
                    }
                    exported to convCount
                }
                onHost { it.dismissProgress() }
                hostMessage(
                    appCtx,
                    appCtx.getString(R.string.save_export_done_title),
                    formatExportDoneMessageForContext(
                        appCtx,
                        count,
                        converted,
                        asPc,
                        R.string.save_export_zip_done_msg
                    )
                )
            } catch (e: Exception) {
                Log.e("SaveManager", "Failed to export saves to ZIP", e)
                onHost { it.dismissProgress() }
                hostMessage(
                    appCtx,
                    appCtx.getString(R.string.save_export_failed_title),
                    e.localizedMessage ?: appCtx.getString(R.string.error)
                )
            }
        }

        private fun startExportDirectoryOp(
            appCtx: Context,
            treeUri: Uri,
            entriesToExport: List<SaveFileEntry>,
            asPc: Boolean
        ): Job = launchSaveOp {
            try {
                val (count, converted) = withContext(Dispatchers.IO) {
                    val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
                    var exported = 0
                    var convCount = 0
                    for (entry in entriesToExport) {
                        if (!entry.file.exists()) continue
                        val parts = entry.relativePath.split('/').filter { it.isNotEmpty() }
                        if (parts.isEmpty()) continue
                        var parentDocId = rootDocId
                        for (i in 0 until parts.size - 1) {
                            parentDocId = getOrCreateSubdirStatic(appCtx, treeUri, parentDocId, parts[i])
                        }
                        val fileName = parts.last()
                        val existing = listDocumentChildrenFor(appCtx, treeUri, parentDocId)
                            .firstOrNull { !it.isDirectory && it.name == fileName }
                        if (existing != null) {
                            val existingUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, existing.documentId)
                            try {
                                DocumentsContract.deleteDocument(appCtx.contentResolver, existingUri)
                            } catch (_: Exception) {
                            }
                        }
                        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
                        val newDocUri = DocumentsContract.createDocument(
                            appCtx.contentResolver,
                            parentUri,
                            "application/octet-stream",
                            fileName
                        ) ?: throw IOException("Failed to create file: $fileName")
                        appCtx.contentResolver.openOutputStream(newDocUri)?.use { out ->
                            if (copyExportFileStatic(appCtx, entry.file, out, asPc)) {
                                convCount++
                            }
                        } ?: throw IOException("Failed to write file: $fileName")
                        exported++
                    }
                    exported to convCount
                }
                onHost { it.dismissProgress() }
                hostMessage(
                    appCtx,
                    appCtx.getString(R.string.save_export_done_title),
                    formatExportDoneMessageForContext(
                        appCtx,
                        count,
                        converted,
                        asPc,
                        R.string.save_export_dir_done_msg
                    )
                )
            } catch (e: Exception) {
                Log.e("SaveManager", "Failed to export saves to directory", e)
                onHost { it.dismissProgress() }
                hostMessage(
                    appCtx,
                    appCtx.getString(R.string.save_export_failed_title),
                    e.localizedMessage ?: appCtx.getString(R.string.error)
                )
            }
        }

        private fun startExportSingleFileOp(
            appCtx: Context,
            srcFile: File,
            destUri: Uri,
            asPc: Boolean
        ): Job = launchSaveOp {
            try {
                withContext(Dispatchers.IO) {
                    appCtx.contentResolver.openOutputStream(destUri)?.use { out ->
                        copyExportFileStatic(appCtx, srcFile, out, asPc)
                    } ?: throw IOException("Failed to write target file")
                }
                onHost { it.dismissProgress() }
                Toast.makeText(
                    appCtx,
                    appCtx.getString(R.string.save_file_exported_toast, srcFile.name),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                onHost { it.dismissProgress() }
                hostMessage(
                    appCtx,
                    appCtx.getString(R.string.save_export_failed_title),
                    e.localizedMessage ?: appCtx.getString(R.string.error)
                )
            }
        }

        private fun startClearSavesOp(
            appCtx: Context,
            targetDir: File,
            targetDirName: String
        ): Job = launchSaveOp {
            try {
                withContext(Dispatchers.IO) {
                    ensureProcessStoppedOnIo(appCtx)
                    if (hasExistingSavesInDir(targetDir)) {
                        val backup = backupSavesInDir(appCtx.filesDir, targetDir, targetDirName)
                        if (backup == null || !backup.isFile || backup.length() <= 0L) {
                            throw IOException(
                                GamepadBridge.tr(
                                    appCtx,
                                    "清空前自动备份失败，已中止删除",
                                    "消去前の自動バックアップに失敗したため中止しました",
                                    "Automatic backup failed; aborted clearing saves"
                                )
                            )
                        }
                    }
                    ensureProcessStoppedOnIo(appCtx)
                    targetDir.listFiles()
                        ?.filter { !it.name.startsWith(".") }
                        ?.forEach { child ->
                            if (!child.deleteRecursively() && child.exists()) {
                                throw IOException("Failed to delete save file: ${child.name}")
                            }
                        }
                }
                onHost {
                    it.dismissProgress()
                    it.refreshFileList()
                }
                Toast.makeText(appCtx, R.string.save_cleared_toast, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("SaveManager", "Failed to clear saves", e)
                onHost {
                    it.dismissProgress()
                    it.refreshFileList()
                }
                hostMessage(
                    appCtx,
                    appCtx.getString(R.string.error),
                    formatArchiveImportErrorForContext(appCtx, e)
                )
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activeJob: Job?
        get() = opJob
        set(value) { opJob = value }
    private var progressDialog: Dialog? = null
    private val trackedDialogs = mutableListOf<Dialog>()

    private var scopedGameId: String? = null
    private var targets: MutableList<SaveTarget> = mutableListOf()
    private lateinit var currentTarget: SaveTarget
    private lateinit var adapter: SaveFileAdapter
    private var currentEntries: List<SaveFileEntry> = emptyList()
    private var pendingSingleExportFile: File? = null
    private var pendingSingleExportAsPc: Boolean = false
    private var pendingExportAsPc: Boolean = false

    private fun <T : Dialog> trackDialog(dlg: T): T {
        trackedDialogs.removeAll { !it.isShowing }
        trackedDialogs.add(dlg)
        return dlg
    }

    private fun dismissTrackedDialogs() {
        for (dlg in trackedDialogs.toList()) {
            if (dlg.isShowing) {
                try {
                    dlg.dismiss()
                } catch (_: Throwable) {
                }
            }
        }
        trackedDialogs.clear()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_save_manager)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        scopedGameId = intent.getStringExtra(EXTRA_GAME_ID)?.takeIf { it.isNotBlank() }
        scopedGameId?.let { gid ->
            val desc = GameCatalog.descriptorFor(this, gid)
            title = "${desc.title} · ${getString(R.string.action_save_manager)}"
        }

        adapter = SaveFileAdapter(this, emptyList())
        val listView = findViewById<ListView>(R.id.save_file_list)
        // The info card and its buttons scroll with the list as its header. It has to be
        // added before the adapter is set; its views are then found by findViewById() as usual.
        listView.addHeaderView(
            layoutInflater.inflate(R.layout.save_manager_header, listView, false), null, false
        )
        listView.adapter = adapter

        liveHostRef = java.lang.ref.WeakReference(this)
        if (savedInstanceState != null) {
            pendingExportAsPc = savedInstanceState.getBoolean(STATE_EXPORT_AS_PC, false)
            pendingSingleExportFile = savedInstanceState.getString(STATE_SINGLE_EXPORT_FILE)?.let { File(it) }
            pendingSingleExportAsPc = savedInstanceState.getBoolean(STATE_SINGLE_EXPORT_AS_PC, false)
        }
        val savedTargetDir = savedInstanceState?.getString(STATE_TARGET_DIR)
        discoverSaveTargets(
            preferredDirName = savedTargetDir ?: intent.getStringExtra(EXTRA_DIR_NAME),
            resolveInstallAlias = savedTargetDir == null
        )
        if (opJob?.isActive == true) {
            showProgress(opProgressMessage ?: getString(R.string.action_save_manager))
        } else {
            // Leftovers of an import interrupted by a process death.
            filesDir.listFiles()?.filter { it.name.startsWith(SAVE_IMPORT_TMP_PREFIX) }
                ?.forEach { it.deleteRecursively() }
        }

        findViewById<View>(R.id.btn_switch_game).setOnClickListener { showSwitchTargetDialog() }
        findViewById<View>(R.id.btn_import_saves).setOnClickListener {
            ensureGameNotRunning(this) { showImportDialog() }
        }
        findViewById<View>(R.id.btn_export_saves).setOnClickListener { showExportDialog() }
        findViewById<View>(R.id.btn_clear_saves).setOnClickListener {
            ensureGameNotRunning(this) { showClearSavesDialog() }
        }

        listView.setOnItemClickListener { _, _, pos, _ ->
            val index = pos - listView.headerViewsCount
            if (index in 0 until adapter.count) {
                showSingleFileManageDialog(adapter.getItem(index))
            }
        }

        refreshFileList()
    }

    override fun onResume() {
        super.onResume()
        liveHostRef = java.lang.ref.WeakReference(this)
        if (::currentTarget.isInitialized) {
            refreshFileList()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::currentTarget.isInitialized) outState.putString(STATE_TARGET_DIR, currentTarget.dirName)
        outState.putBoolean(STATE_EXPORT_AS_PC, pendingExportAsPc)
        pendingSingleExportFile?.let { outState.putString(STATE_SINGLE_EXPORT_FILE, it.absolutePath) }
        outState.putBoolean(STATE_SINGLE_EXPORT_AS_PC, pendingSingleExportAsPc)
    }

    override fun onDestroy() {
        if (liveHostRef?.get() === this) {
            liveHostRef = null
        }
        dismissProgress()
        dismissTrackedDialogs()
        scope.cancel()
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun discoverSaveTargets(
        preferredDirName: String? = null,
        resolveInstallAlias: Boolean = false
    ) {
        val savesRoot = File(filesDir, "saves")
        savesRoot.mkdirs()

        val found = LinkedHashMap<String, SaveTarget>()
        var resolvedPreferredDirName = preferredDirName
        val gameList = GameList(this)
        try {
            for (i in 0 until gameList.size) {
                val item = gameList[i]
                val saveDir = item.savedir ?: Item.resolveSaveDir(this, item.path)
                val saveKey = saveDir.name
                saveDir.mkdirs()
                if (resolveInstallAlias && preferredDirName != null && item.path.name == preferredDirName) {
                    resolvedPreferredDirName = saveKey
                }
                found[saveKey] = SaveTarget(
                    displayName = saveKey,
                    dirName = saveKey,
                    saveDir = saveDir,
                    externalGameDir = item.path
                )
            }

            savesRoot.listFiles()?.filter { it.isDirectory && !it.name.startsWith('.') }?.forEach { dir ->
                if (!found.containsKey(dir.name)) {
                    val baseGameName = dir.name.removeSuffix(Item.DAISENGOKU_SAVE_DIR_SUFFIX)
                    val extDir = getExternalFilesDir(null)?.let { root ->
                        File(root, dir.name).takeIf { it.exists() }
                            ?: File(root, baseGameName).takeIf { it.exists() }
                    }
                    found[dir.name] = SaveTarget(
                        displayName = dir.name,
                        dirName = dir.name,
                        saveDir = dir,
                        externalGameDir = extDir
                    )
                }
            }

            val gid = scopedGameId
            if (!gid.isNullOrBlank()) {
                val iter = found.entries.iterator()
                while (iter.hasNext()) {
                    val entry = iter.next().value
                    val classified = GameCatalog.classifyDirectory(
                        gameDir = entry.externalGameDir,
                        dirName = entry.dirName,
                        saveDir = entry.saveDir,
                        displayName = entry.displayName
                    )
                    if (classified != gid && entry.dirName != resolvedPreferredDirName && entry.dirName != preferredDirName) {
                        iter.remove()
                    }
                }
                if (found.isEmpty()) {
                    val desc = GameCatalog.descriptorFor(this, gid, gameList)
                    val defaultDir = File(savesRoot, desc.defaultDirName)
                    defaultDir.mkdirs()
                    val extDir = getExternalFilesDir(null)?.let { File(it, desc.defaultDirName) }?.takeIf { it.exists() }
                    found[desc.defaultDirName] = SaveTarget(
                        displayName = desc.defaultDirName,
                        dirName = desc.defaultDirName,
                        saveDir = defaultDir,
                        externalGameDir = extDir
                    )
                }
            } else if (found.isEmpty()) {
                val defaultDir = File(savesRoot, "Rance10")
                defaultDir.mkdirs()
                val extDir = getExternalFilesDir(null)?.let { File(it, "Rance10") }
                found["Rance10"] = SaveTarget(
                    displayName = "Rance10",
                    dirName = "Rance10",
                    saveDir = defaultDir,
                    externalGameDir = extDir
                )
            }
        } finally {
            gameList.close()
        }

        targets = found.values.toMutableList()
        currentTarget = targets.firstOrNull { it.dirName == resolvedPreferredDirName }
            ?: targets.firstOrNull { it.dirName == preferredDirName }
            ?: targets.first()
    }

    private fun refreshFileList() {
        val saveDir = currentTarget.saveDir
        saveDir.mkdirs()
        saveImportPathsForTarget(this, currentTarget).migrateExistingSaves(saveDir)

        val list = mutableListOf<SaveFileEntry>()
        collectSaveFiles(saveDir, saveDir, list)
        list.sortWith(compareBy<SaveFileEntry> {
            when {
                it.file.name.equals("GlobalSave.asd", true) ||
                    it.file.name.equals("GlobalSaveData.asd", true) -> 0
                it.file.name.equals("Collection.asd", true) -> 1
                it.file.name.endsWith(".asd", true) || it.file.name.endsWith(".sav", true) -> 2
                it.file.name.endsWith(".log", true) -> 4
                else -> 3
            }
        }.thenByDescending { it.lastModified }.thenBy { it.relativePath })

        currentEntries = list
        adapter.update(list)

        val totalBytes = list.sumOf { it.sizeBytes }
        findViewById<TextView>(R.id.save_target_title).text =
            getString(R.string.save_manager_current_dir, currentTarget.dirName)
        findViewById<TextView>(R.id.save_target_summary).text =
            getString(R.string.save_manager_dir_summary, list.size, formatFileSize(totalBytes))
        applyThemeAccent()

        val emptyView = findViewById<TextView>(R.id.save_empty_view)
        if (list.isEmpty()) {
            UiKit.bindEmptyState(emptyView, R.drawable.ic_folder_open, getString(R.string.save_manager_empty))
            emptyView.visibility = View.VISIBLE
        } else {
            emptyView.visibility = View.GONE
        }
    }

    private fun applyThemeAccent() {
        val accent = UiKit.resolveGameAccentColor(
            this,
            scopedGameId,
            currentTarget.externalGameDir,
            currentTarget.dirName,
            currentTarget.saveDir
        )
        adapter.accentColor = accent
        findViewById<View>(R.id.save_header_card)?.background =
            UiKit.accentCard(this, accent, R.dimen.ui_radius_card, clickable = false)
        listOf(R.id.btn_import_saves, R.id.btn_export_saves, R.id.btn_switch_game).forEach { id ->
            findViewById<Button>(id)?.let { UiKit.styleTonalAccentButton(it, accent) }
        }
        findViewById<Button>(R.id.btn_clear_saves)?.let { UiKit.styleWhiteDangerButton(it) }
    }

    private fun collectSaveFiles(root: File, current: File, out: MutableList<SaveFileEntry>) {
        val children = current.listFiles() ?: return
        for (child in children) {
            if (child.name.startsWith(".")) continue
            if (child.isDirectory) {
                collectSaveFiles(root, child, out)
            } else if (child.isFile) {
                val rel = child.relativeTo(root).path.replace('\\', '/')
                out.add(
                    SaveFileEntry(
                        file = child,
                        relativePath = rel,
                        sizeBytes = child.length(),
                        lastModified = child.lastModified(),
                        tag = classifySaveFile(child.name)
                    )
                )
            }
        }
    }

    private fun classifySaveFile(name: String): String {
        val lower = ArchiveExtractor.repairSjisAsGbkMojibake(name).lowercase(Locale.ROOT)
        return when {
            lower == "globalsave.asd" || lower == "globalsavedata.asd" ||
                lower == "afcommon.asd" || lower == "progress.asd" ||
                lower == "妻しぼりsys.asd" || lower == "tsumashiborisys.asd" ||
                lower == "共有.asd" -> getString(R.string.save_tag_global)
            lower == "collection.asd" -> getString(R.string.save_tag_collection)
            lower == "msgskip.asd" || lower == "已读文本跳过.asd" ||
                lower == "既読スキップ.asd" || lower == "msgskipflag.sav" ||
                lower.endsWith(".msgskip") ->
                getString(R.string.save_tag_msgskip)
            lower == "afcgmode.asd" || lower == "afbgmmode.asd" ||
                lower == "ｃｇモード.asd" || lower == "回想モード.asd" || lower == "音楽モード.asd" -> getString(R.string.save_tag_afcg)
            lower == "gameenquete.asd" -> getString(R.string.save_tag_enquete)
            lower == "commonsystemdata.sav" || GameCatalog.isHarukaSystemSaveFileName(name) || lower == "afconfig.asd" ||
                lower == "config.asd" || lower == "joypad.asd" ||
                lower == "keyfile.dat" ||
                lower == "3dsetting.asd" || lower == "volume.sav" ->
                getString(R.string.save_tag_common)
            lower.endsWith(".asd") || lower.endsWith(".sav") || lower.endsWith(".rsm") ||
                lower.endsWith(".vsf") -> getString(R.string.save_tag_progress)
            lower.endsWith(".qnt") || lower.endsWith(".png") -> getString(R.string.save_tag_thumbnail)
            lower.endsWith(".log") -> getString(R.string.save_tag_log)
            else -> getString(R.string.save_tag_other)
        }
    }

    private fun showSwitchTargetDialog() {
        discoverSaveTargets(currentTarget.dirName)
        val labels = targets.map { target ->
            val count = target.saveDir.walkTopDown()
                .filter { it.isFile && !it.name.startsWith(".") && !it.name.startsWith("crash") }
                .count()
            getString(R.string.save_dir_item_count, target.displayName, count)
        }.toMutableList()
        labels.add(getString(R.string.save_dir_new_option))

        trackDialog(
            AlertDialog.Builder(this)
                .setTitle(R.string.save_dir_select_title)
                .setItems(labels.toTypedArray()) { _, which ->
                    if (which == targets.size) {
                        showCreateTargetDialog()
                    } else {
                        currentTarget = targets[which]
                        refreshFileList()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        )
    }

    private fun showCreateTargetDialog() {
        val defaultPrefix = scopedGameId?.let { GameCatalog.descriptorFor(this, it).defaultDirName } ?: "Rance10"
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = getString(R.string.save_dir_create_hint)
            setText(defaultPrefix)
        }
        trackDialog(
            AlertDialog.Builder(this)
                .setTitle(R.string.save_dir_create_title)
                .setView(UiKit.dialogContent(this, input))
                .setPositiveButton(R.string.ok) { _, _ ->
                    val rawName = input.text.toString().trim()
                    val safeName = rawName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    if (safeName.isEmpty() || safeName.startsWith(".")) {
                        Toast.makeText(this, R.string.save_dir_invalid_name, Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    val newDir = File(File(filesDir, "saves"), safeName)
                    newDir.mkdirs()
                    discoverSaveTargets(safeName)
                    refreshFileList()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        )
    }

    private fun showImportDialog() {
        if (activeJob?.isActive == true) return
        val latestBackup = listSaveBackupsFor(filesDir, currentTarget.dirName).firstOrNull()
        val options = mutableListOf(
            getString(R.string.save_import_opt_files),
            getString(R.string.save_import_opt_dir)
        )
        if (latestBackup != null) {
            options.add(getString(R.string.save_import_opt_restore_backup, formatBackupTime(latestBackup)))
        }
        trackDialog(
            AlertDialog.Builder(this)
                .setTitle(R.string.save_import_title)
                .setItems(options.toTypedArray()) { _, which ->
                    when (which) {
                        0 -> confirmImportOverwrite { chooseImportFiles() }
                        1 -> confirmImportOverwrite { chooseImportDirectory() }
                        2 -> latestBackup?.let { confirmRestoreBackup(it) }
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        )
    }

    /** Importing overwrites same-named saves; say so (and that a backup is made) first. */
    private fun confirmImportOverwrite(proceed: () -> Unit) {
        if (!hasExistingSavesInDir(currentTarget.saveDir)) {
            proceed()
            return
        }
        trackDialog(
            AlertDialog.Builder(this)
                .setTitle(R.string.save_import_title)
                .setMessage(R.string.save_import_overwrite_msg)
                .setPositiveButton(R.string.ok) { _, _ -> proceed() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        )
    }

    private fun formatBackupTime(backup: File): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(backup.lastModified()))

    private fun confirmRestoreBackup(backup: File) {
        trackDialog(
            AlertDialog.Builder(this)
                .setTitle(R.string.save_import_title)
                .setMessage(getString(R.string.save_restore_backup_confirm_msg, formatBackupTime(backup)))
                .setPositiveButton(R.string.ok) { _, _ ->
                    ensureGameNotRunning(this) { runRestoreBackup(backup) }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        )
    }

    private fun runRestoreBackup(backup: File) {
        if (activeJob?.isActive == true) return
        val target = currentTarget
        val appCtx = AppSettings.wrapThemeContext(applicationContext)
        showProgress(getString(R.string.save_progress_restore_backup))
        activeJob = startRestoreBackupOp(appCtx, backup, target.saveDir, target.dirName)
    }

    private fun entriesForExport(asPc: Boolean): List<SaveFileEntry> {
        if (!asPc) return currentEntries.toList()
        return currentEntries.filter { entry ->
            val lower = entry.file.name.lowercase(Locale.ROOT)
            !lower.endsWith(".bak") && !lower.endsWith(".log")
        }
    }

    private fun showExportDialog() {
        if (activeJob?.isActive == true) return
        if (currentEntries.isEmpty()) {
            Toast.makeText(this, R.string.save_export_empty_toast, Toast.LENGTH_SHORT).show()
            return
        }
        val options = arrayOf(
            getString(R.string.save_export_opt_pc_zip),
            getString(R.string.save_export_opt_pc_dir),
            getString(R.string.save_export_opt_zip),
            getString(R.string.save_export_opt_dir)
        )
        trackDialog(
            AlertDialog.Builder(this)
                .setTitle(R.string.save_export_title)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> chooseExportZip(asPc = true)
                        1 -> chooseExportDirectory(asPc = true)
                        2 -> chooseExportZip(asPc = false)
                        3 -> chooseExportDirectory(asPc = false)
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        )
    }

    private fun chooseImportDirectory() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, REQ_IMPORT_DIR)
    }

    private fun chooseImportFiles() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(Intent.createChooser(intent, getString(R.string.save_chooser_import_files)), REQ_IMPORT_FILES)
    }

    private fun chooseExportZip(asPc: Boolean) {
        if (entriesForExport(asPc).isEmpty()) {
            Toast.makeText(this, R.string.save_export_empty_toast, Toast.LENGTH_SHORT).show()
            return
        }
        pendingExportAsPc = asPc
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val suffix = if (asPc) "PC_SaveData" else "SaveData"
        val defaultName = "${currentTarget.dirName}_${suffix}_${timestamp}.zip"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, defaultName)
        }
        startActivityForResult(intent, REQ_EXPORT_ZIP)
    }

    private fun chooseExportDirectory(asPc: Boolean) {
        if (entriesForExport(asPc).isEmpty()) {
            Toast.makeText(this, R.string.save_export_empty_toast, Toast.LENGTH_SHORT).show()
            return
        }
        pendingExportAsPc = asPc
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQ_EXPORT_DIR)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return

        when (requestCode) {
            REQ_IMPORT_DIR -> {
                val uri = data.data ?: return
                if (GameCatalog.isGameProcessRunning(this)) {
                    ensureGameNotRunning(this) { runImportDirectory(uri) }
                } else {
                    runImportDirectory(uri)
                }
            }
            REQ_IMPORT_FILES -> {
                val uris = mutableListOf<Uri>()
                val clipData = data.clipData
                if (clipData != null) {
                    for (i in 0 until clipData.itemCount) {
                        clipData.getItemAt(i)?.uri?.let { uris.add(it) }
                    }
                } else {
                    data.data?.let { uris.add(it) }
                }
                if (uris.isNotEmpty()) {
                    if (GameCatalog.isGameProcessRunning(this)) {
                        ensureGameNotRunning(this) { runImportFiles(uris) }
                    } else {
                        runImportFiles(uris)
                    }
                }
            }
            REQ_EXPORT_ZIP -> {
                val uri = data.data ?: return
                val asPc = pendingExportAsPc
                pendingExportAsPc = false
                runExportZip(uri, asPc)
            }
            REQ_EXPORT_DIR -> {
                val uri = data.data ?: return
                val asPc = pendingExportAsPc
                pendingExportAsPc = false
                runExportDirectory(uri, asPc)
            }
            REQ_EXPORT_SINGLE_FILE -> {
                val uri = data.data ?: return
                val srcFile = pendingSingleExportFile ?: return
                val asPc = pendingSingleExportAsPc
                pendingSingleExportFile = null
                pendingSingleExportAsPc = false
                runExportSingleFile(srcFile, uri, asPc)
            }
        }
    }

    private fun runImportDirectory(treeUri: Uri) {
        if (activeJob?.isActive == true) return
        val appCtx = AppSettings.wrapThemeContext(applicationContext)
        showProgress(getString(R.string.save_progress_import_dir))
        activeJob = startImportDirectoryOp(appCtx, treeUri, currentTarget)
    }

    private fun runImportFiles(uris: List<Uri>) {
        if (activeJob?.isActive == true) return
        val appCtx = AppSettings.wrapThemeContext(applicationContext)
        showProgress(getString(R.string.save_progress_import_files))
        activeJob = startImportFilesOp(appCtx, uris, currentTarget)
    }

    private fun runExportZip(uri: Uri, asPc: Boolean) {
        if (activeJob?.isActive == true) return
        val entriesToExport = entriesForExport(asPc)
        val appCtx = AppSettings.wrapThemeContext(applicationContext)
        showProgress(getString(R.string.save_progress_export_zip))
        activeJob = startExportZipOp(appCtx, uri, entriesToExport, asPc)
    }

    private fun runExportDirectory(treeUri: Uri, asPc: Boolean) {
        if (activeJob?.isActive == true) return
        val entriesToExport = entriesForExport(asPc)
        val appCtx = AppSettings.wrapThemeContext(applicationContext)
        showProgress(getString(R.string.save_progress_export_dir))
        activeJob = startExportDirectoryOp(appCtx, treeUri, entriesToExport, asPc)
    }

    private fun showSingleFileManageDialog(entry: SaveFileEntry) {
        val isLogFile = entry.file.name.endsWith(".log", ignoreCase = true)
        if (isLogFile) {
            val logOptions = arrayOf(
                getString(R.string.error_report_view),
                getString(R.string.error_report_share),
                getString(R.string.save_file_opt_export),
                getString(R.string.save_file_opt_delete)
            )
            trackDialog(
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.save_file_dialog_title, entry.relativePath, formatFileSize(entry.sizeBytes)))
                    .setItems(logOptions) { _, which ->
                        when (which) {
                            0 -> ErrorReportKit.showFromFile(this, entry.file)
                            1 -> ErrorReportKit.shareFromFile(this, entry.file)
                            2 -> startSingleFileExport(entry.file, mimeType = "text/plain", asPc = false)
                            3 -> ensureGameNotRunning(this) { showDeleteSingleFileDialog(entry) }
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            )
            return
        }

        val items = mutableListOf<Pair<String, () -> Unit>>()
        items.add(getString(R.string.save_file_opt_export) to {
            startSingleFileExport(entry.file, mimeType = "application/octet-stream", asPc = false)
        })
        if (GameModifierBridge.isX4ssSaveFile(entry.file)) {
            items.add(getString(R.string.save_file_opt_export_pc) to {
                startSingleFileExport(entry.file, mimeType = "application/octet-stream", asPc = true)
            })
        }
        items.add(getString(R.string.save_file_opt_backup) to {
            ensureGameNotRunning(this) { showDuplicateFileDialog(entry) }
        })
        items.add(getString(R.string.save_file_opt_rename) to {
            ensureGameNotRunning(this) { showRenameFileDialog(entry) }
        })
        items.add(getString(R.string.save_file_opt_delete) to {
            ensureGameNotRunning(this) { showDeleteSingleFileDialog(entry) }
        })

        val options = items.map { it.first }.toTypedArray()
        trackDialog(
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.save_file_dialog_title, entry.relativePath, formatFileSize(entry.sizeBytes)))
                .setItems(options) { _, which ->
                    items[which].second()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        )
    }

    private fun startSingleFileExport(file: File, mimeType: String, asPc: Boolean) {
        pendingSingleExportFile = file
        pendingSingleExportAsPc = asPc
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, file.name)
        }
        startActivityForResult(intent, REQ_EXPORT_SINGLE_FILE)
    }

    private fun runExportSingleFile(srcFile: File, destUri: Uri, asPc: Boolean) {
        if (activeJob?.isActive == true) return
        val appCtx = AppSettings.wrapThemeContext(applicationContext)
        showProgress(getString(R.string.save_progress_export_file))
        activeJob = startExportSingleFileOp(appCtx, srcFile, destUri, asPc)
    }

    private fun isValidUserSaveFileName(name: String): Boolean {
        if (name.isEmpty() || name.startsWith(".") || name == "." || name == "..") return false
        if (name.contains('/') || name.contains('\\') || name.contains('\u0000')) return false
        return true
    }

    private fun performDuplicateFileCopy(entry: SaveFileEntry, dst: File, newName: String) {
        ensureGameNotRunning(this) {
            try {
                val parent = dst.parentFile ?: throw IOException("Invalid target directory")
                val tmp = File(parent, ".dup_${System.nanoTime()}.tmp")
                try {
                    entry.file.copyTo(tmp, overwrite = true)
                    if (!tmp.renameTo(dst)) {
                        tmp.copyTo(dst, overwrite = true)
                        tmp.delete()
                    }
                } finally {
                    tmp.delete()
                }
                refreshFileList()
                Toast.makeText(
                    this,
                    getString(R.string.save_backup_created_toast, newName),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                showMessageDialog(
                    getString(R.string.error),
                    e.localizedMessage ?: getString(R.string.error)
                )
            }
        }
    }

    private fun showDuplicateFileDialog(entry: SaveFileEntry) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText("${entry.file.name}.bak")
        }
        trackDialog(
            AlertDialog.Builder(this)
                .setTitle(R.string.save_backup_title)
                .setView(UiKit.dialogContent(this, input))
                .setPositiveButton(R.string.ok) { _, _ ->
                    val newName = input.text.toString().trim()
                    if (!isValidUserSaveFileName(newName)) {
                        Toast.makeText(this, R.string.save_invalid_filename, Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    val parent = entry.file.parentFile
                    if (parent == null) {
                        Toast.makeText(this, R.string.save_invalid_filename, Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    val dst = File(parent, newName)
                    val sameFile = try {
                        dst.canonicalPath == entry.file.canonicalPath
                    } catch (_: IOException) {
                        dst.absolutePath == entry.file.absolutePath
                    }
                    if (sameFile) {
                        Toast.makeText(this, R.string.save_invalid_filename, Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    if (dst.exists()) {
                        val confirmMsg = GamepadBridge.tr(
                            this,
                            "文件“$newName”已存在，是否覆盖？",
                            "「$newName」は既に存在します。上書きしますか？",
                            "\"$newName\" already exists. Overwrite?"
                        )
                        trackDialog(
                            AlertDialog.Builder(this)
                                .setTitle(R.string.save_backup_title)
                                .setMessage(confirmMsg)
                                .setPositiveButton(R.string.ok) { _, _ ->
                                    performDuplicateFileCopy(entry, dst, newName)
                                }
                                .setNegativeButton(R.string.cancel, null)
                                .show()
                        )
                    } else {
                        performDuplicateFileCopy(entry, dst, newName)
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        )
    }

    private fun showRenameFileDialog(entry: SaveFileEntry) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(entry.file.name)
        }
        trackDialog(
            AlertDialog.Builder(this)
                .setTitle(R.string.save_rename_title)
                .setView(UiKit.dialogContent(this, input))
                .setPositiveButton(R.string.ok) { _, _ ->
                    val newName = input.text.toString().trim()
                    if (newName == entry.file.name) {
                        return@setPositiveButton
                    }
                    if (!isValidUserSaveFileName(newName)) {
                        Toast.makeText(this, R.string.save_invalid_filename, Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    ensureGameNotRunning(this) {
                        val dst = File(entry.file.parentFile, newName)
                        if (dst.exists()) {
                            Toast.makeText(this, R.string.save_rename_exists_toast, Toast.LENGTH_SHORT).show()
                            return@ensureGameNotRunning
                        }
                        if (entry.file.renameTo(dst)) {
                            refreshFileList()
                            Toast.makeText(
                                this,
                                getString(R.string.save_renamed_toast, newName),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(this, R.string.save_rename_failed_toast, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        )
    }

    private fun showDeleteSingleFileDialog(entry: SaveFileEntry) {
        trackDialog(
            AlertDialog.Builder(this)
                .setTitle(R.string.save_delete_title)
                .setMessage(getString(R.string.save_delete_confirm_msg, entry.relativePath))
                .setPositiveButton(R.string.ok) { _, _ ->
                    ensureGameNotRunning(this) {
                        if (entry.file.delete()) {
                            refreshFileList()
                            Toast.makeText(
                                this,
                                getString(R.string.save_deleted_toast, entry.relativePath),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(this, R.string.save_delete_failed_toast, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        )
    }

    private fun showClearSavesDialog() {
        if (activeJob?.isActive == true) return
        if (currentEntries.isEmpty()) {
            Toast.makeText(this, R.string.save_clear_already_empty, Toast.LENGTH_SHORT).show()
            return
        }
        confirmDeleteAllSaves()
    }

    private fun confirmDeleteAllSaves() {
        trackDialog(
            AlertDialog.Builder(this)
                .setTitle(R.string.save_clear_confirm_title)
                .setMessage(getString(R.string.save_clear_confirm_msg, currentEntries.size))
                .setPositiveButton(R.string.ok) { _, _ ->
                    ensureGameNotRunning(this) {
                        runClearAllSaves()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        )
    }

    private fun runClearAllSaves() {
        if (activeJob?.isActive == true) return
        val target = currentTarget
        val appCtx = AppSettings.wrapThemeContext(applicationContext)
        showProgress(
            GamepadBridge.tr(
                this,
                "正在备份并清空存档…",
                "セーブデータをバックアップして消去中…",
                "Backing up and clearing saves…"
            )
        )
        activeJob = startClearSavesOp(appCtx, target.saveDir, target.dirName)
    }

    private fun showProgress(message: String) {
        opProgressMessage = message
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (progressDialog == null) {
            progressDialog = Dialog(this).apply {
                setTitle(R.string.action_save_manager)
                setCancelable(false)
                setContentView(R.layout.progress_dialog)
                // Save operations are short and cannot be moved to the background or cancelled.
                findViewById<View>(R.id.action_bar)?.visibility = View.GONE
                show()
            }
        }
        progressDialog?.findViewById<TextView>(R.id.text)?.text = message
    }

    private fun dismissProgress() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        progressDialog?.dismiss()
        progressDialog = null
    }

    private fun showMessageDialog(title: String, message: String) {
        trackDialog(
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .show()
        )
    }
}
