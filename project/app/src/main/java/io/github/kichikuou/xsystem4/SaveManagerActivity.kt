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
        val lower = fileName.lowercase(Locale.ROOT)
        return when {
            lower in GLOBAL_SAVE_FILES -> UiKit.Tone.PRIMARY
            lower.endsWith(".log") -> UiKit.Tone.WARNING
            else -> UiKit.Tone.NEUTRAL
        }
    }

    private companion object {
        val GLOBAL_SAVE_FILES = setOf(
            "globalsave.asd", "globalsavedata.asd", "collection.asd", "msgskip.asd",
            "afcgmode.asd", "afcommon.asd", "afconfig.asd", "afbgmmode.asd",
            "共有.asd", "ｃｇモード.asd", "音楽モード.asd", "3dsetting.asd",
            "gameenquete.asd", "commonsystemdata.sav"
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

internal fun isGameProcessRunning(context: Context): Boolean {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val targetProcName = "${context.packageName}:game"
    return try {
        am?.runningAppProcesses?.any { it.processName == targetProcName } == true
    } catch (_: Throwable) {
        false
    }
}

internal fun ensureGameNotRunning(activity: Activity, onProceed: () -> Unit) {
    val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val targetProcName = "${activity.packageName}:game"
    val gameProc = try {
        am?.runningAppProcesses?.firstOrNull { it.processName == targetProcName }
    } catch (_: Throwable) {
        null
    }
    if (gameProc == null) {
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
            try {
                Process.killProcess(gameProc.pid)
            } catch (_: Throwable) {
            }
            for (attempt in 0 until 20) {
                if (!isGameProcessRunning(activity)) break
                try {
                    Thread.sleep(25)
                } catch (_: InterruptedException) {
                    break
                }
            }
            onProceed()
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
        // it). Results go to whichever instance is current when the job ends.
        private val opScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var opJob: Job? = null
        private var opProgressMessage: String? = null
        private var liveHost: SaveManagerActivity? = null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activeJob: Job?
        get() = opJob
        set(value) { opJob = value }
    private var progressDialog: Dialog? = null

    private var scopedGameId: String? = null
    private var targets: MutableList<SaveTarget> = mutableListOf()
    private lateinit var currentTarget: SaveTarget
    private lateinit var adapter: SaveFileAdapter
    private var currentEntries: List<SaveFileEntry> = emptyList()
    private var pendingSingleExportFile: File? = null
    private var pendingSingleExportAsPc: Boolean = false
    private var pendingExportAsPc: Boolean = false

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

        liveHost = this
        if (savedInstanceState != null) {
            pendingExportAsPc = savedInstanceState.getBoolean(STATE_EXPORT_AS_PC, false)
            pendingSingleExportFile = savedInstanceState.getString(STATE_SINGLE_EXPORT_FILE)?.let { File(it) }
            pendingSingleExportAsPc = savedInstanceState.getBoolean(STATE_SINGLE_EXPORT_AS_PC, false)
        }
        discoverSaveTargets(
            savedInstanceState?.getString(STATE_TARGET_DIR) ?: intent.getStringExtra(EXTRA_DIR_NAME)
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

    private fun launchSaveOp(block: suspend CoroutineScope.() -> Unit): Job = opScope.launch(block = block)

    /** Runs [action] on the live SaveManager instance; skipped if none is showing. */
    private fun onHost(action: (SaveManagerActivity) -> Unit) {
        val host = liveHost
        if (host != null && !host.isDestroyed) action(host)
    }

    /** Shows an operation result on the live instance, or as a toast if the screen is gone. */
    private fun hostMessage(title: String, message: String) {
        val host = liveHost
        if (host != null && !host.isFinishing && !host.isDestroyed) {
            host.showMessageDialog(title, message)
        } else {
            Toast.makeText(applicationContext, "$title: $message", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        if (liveHost === this) liveHost = null
        dismissProgress()
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

    private fun discoverSaveTargets(preferredDirName: String? = null) {
        val savesRoot = File(filesDir, "saves")
        savesRoot.mkdirs()

        val found = LinkedHashMap<String, SaveTarget>()
        val gameList = GameList(this)
        for (i in 0 until gameList.size) {
            val item = gameList[i]
            val dirName = item.path.name
            val saveDir = item.savedir ?: File(savesRoot, dirName)
            saveDir.mkdirs()
            found[dirName] = SaveTarget(
                displayName = dirName,
                dirName = dirName,
                saveDir = saveDir,
                externalGameDir = item.path
            )
        }

        savesRoot.listFiles()?.filter { it.isDirectory && !it.name.startsWith('.') }?.forEach { dir ->
            if (!found.containsKey(dir.name)) {
                val extDir = getExternalFilesDir(null)?.let { File(it, dir.name) }?.takeIf { it.exists() }
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
                if (classified != gid && entry.dirName != preferredDirName) {
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

        targets = found.values.toMutableList()
        currentTarget = targets.firstOrNull { it.dirName == preferredDirName } ?: targets.first()
    }

    private fun refreshFileList() {
        val saveDir = currentTarget.saveDir
        saveDir.mkdirs()
        migrateMojibakeSaveTree(saveDir)

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

    /** Renames legacy Shift-JIS-as-GBK mojibake save filenames/dirs in [dir] without clobbering existing targets. */
    private fun migrateMojibakeSaveTree(dir: File) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.name.startsWith(".")) continue
            if (child.isDirectory) {
                migrateMojibakeSaveTree(child)
            }
            val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(child.name)
            if (repaired != child.name && !repaired.contains('/') && !repaired.contains('\\')) {
                val target = File(dir, repaired)
                if (!target.exists()) {
                    child.renameTo(target)
                }
            }
        }
    }

    private fun classifySaveFile(name: String): String {
        val lower = ArchiveExtractor.repairSjisAsGbkMojibake(name).lowercase(Locale.ROOT)
        return when {
            lower == "globalsave.asd" || lower == "globalsavedata.asd" ||
                lower == "afcommon.asd" || lower == "共有.asd" -> getString(R.string.save_tag_global)
            lower == "collection.asd" -> getString(R.string.save_tag_collection)
            lower == "msgskip.asd" -> getString(R.string.save_tag_msgskip)
            lower == "afcgmode.asd" || lower == "afbgmmode.asd" ||
                lower == "ｃｇモード.asd" || lower == "音楽モード.asd" -> getString(R.string.save_tag_afcg)
            lower == "gameenquete.asd" -> getString(R.string.save_tag_enquete)
            lower == "commonsystemdata.sav" || lower == "afconfig.asd" || lower == "3dsetting.asd" ->
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
    }

    private fun showCreateTargetDialog() {
        val defaultPrefix = scopedGameId?.let { GameCatalog.descriptorFor(this, it).defaultDirName } ?: "Rance10"
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = getString(R.string.save_dir_create_hint)
            setText(defaultPrefix)
        }
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
    }

    private fun showImportDialog() {
        if (activeJob?.isActive == true) return
        val latestBackup = listSaveBackups(currentTarget.dirName).firstOrNull()
        val options = mutableListOf(
            getString(R.string.save_import_opt_files),
            getString(R.string.save_import_opt_dir)
        )
        if (latestBackup != null) {
            options.add(getString(R.string.save_import_opt_restore_backup, formatBackupTime(latestBackup)))
        }
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
    }

    private fun hasExistingSaves(dir: File): Boolean =
        dir.walkTopDown().any { it.isFile && !it.name.startsWith(".") }

    /** Importing overwrites same-named saves; say so (and that a backup is made) first. */
    private fun confirmImportOverwrite(proceed: () -> Unit) {
        if (!hasExistingSaves(currentTarget.saveDir)) {
            proceed()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.save_import_title)
            .setMessage(R.string.save_import_overwrite_msg)
            .setPositiveButton(R.string.ok) { _, _ -> proceed() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun saveBackupDir(dirName: String): File =
        File(File(filesDir, SAVE_BACKUP_ROOT), File(dirName).name)

    /** Backups for [dirName], newest first. */
    private fun listSaveBackups(dirName: String): List<File> =
        saveBackupDir(dirName).listFiles()
            ?.filter { it.isFile && it.name.endsWith(".zip") }
            ?.sortedByDescending { it.name }
            .orEmpty()

    private fun formatBackupTime(backup: File): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(backup.lastModified()))

    /**
     * Zips every save in [saveDir] into the backup folder before an import or restore
     * overwrites them, keeping the newest [SAVE_BACKUP_KEEP]. Returns null if there was
     * nothing to back up. Runs on an IO thread.
     */
    private fun backupSaves(saveDir: File, dirName: String): File? {
        val files = saveDir.walkTopDown().filter { it.isFile && !it.name.startsWith(".") }.toList()
        if (files.isEmpty()) return null
        val dir = saveBackupDir(dirName)
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
            if (!tmp.renameTo(out)) throw IOException("Cannot write save backup")
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
        listSaveBackups(dirName).drop(SAVE_BACKUP_KEEP).forEach { it.delete() }
        return out
    }

    private fun confirmRestoreBackup(backup: File) {
        AlertDialog.Builder(this)
            .setTitle(R.string.save_import_title)
            .setMessage(getString(R.string.save_restore_backup_confirm_msg, formatBackupTime(backup)))
            .setPositiveButton(R.string.ok) { _, _ ->
                ensureGameNotRunning(this) { runRestoreBackup(backup) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun runRestoreBackup(backup: File) {
        val target = currentTarget
        showProgress(getString(R.string.save_progress_restore_backup))
        activeJob = launchSaveOp {
            try {
                val count = withContext(Dispatchers.IO) {
                    // Back up the current saves first so the restore itself can be undone.
                    backupSaves(target.saveDir, target.dirName)
                    restoreSavesFromBackup(backup, target.saveDir)
                }
                onHost { it.dismissProgress() }
                onHost { it.refreshFileList() }
                hostMessage(
                    getString(R.string.save_import_done_title),
                    getString(R.string.save_restore_backup_done_msg, count)
                )
            } catch (e: Exception) {
                Log.e("SaveManager", "Failed to restore save backup", e)
                onHost { it.dismissProgress() }
                onHost { it.refreshFileList() }
                hostMessage(
                    getString(R.string.save_import_failed_title),
                    e.localizedMessage ?: getString(R.string.error)
                )
            }
        }
    }

    /** Replaces the saves in [saveDir] with the contents of one of our backup zips. */
    private fun restoreSavesFromBackup(backup: File, saveDir: File): Int {
        val root = saveDir.canonicalFile
        saveDir.listFiles()?.filter { !it.name.startsWith(".") }?.forEach { it.deleteRecursively() }
        var count = 0
        java.util.zip.ZipInputStream(backup.inputStream().buffered()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (entry.isDirectory) continue
                val rel = entry.name.removePrefix("SaveData/")
                val dst = File(root, rel).canonicalFile
                if (!dst.path.startsWith(root.path + File.separator)) continue
                dst.parentFile?.mkdirs()
                dst.outputStream().buffered().use { zis.copyTo(it) }
                count++
            }
        }
        return count
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
                runImportDirectory(uri)
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
                    runImportFiles(uris)
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

    private fun extractSaveArchiveToTarget(uri: Uri, targetDir: File): Int {
        // Unique per import, so a second import can never wipe the folder of one still running.
        val tempDir = File(filesDir, "$SAVE_IMPORT_TMP_PREFIX${System.nanoTime()}")
        tempDir.mkdirs()
        try {
            ArchiveExtractor.extractUriToDirectory(
                context = this,
                uri = uri,
                destDir = tempDir,
                tempScratchParent = cacheDir
            )
            val normalizedRoot = findNormalizedSaveRoot(tempDir)
            var copied = 0
            normalizedRoot.walkTopDown().forEach { src ->
                if (src.isFile && !src.name.startsWith(".")) {
                    val relPath = ArchiveExtractor.repairSjisAsGbkMojibake(src.relativeTo(normalizedRoot).path)
                    val dst = File(targetDir, relPath)
                    dst.parentFile?.mkdirs()
                    src.copyTo(dst, overwrite = true)
                    copied++
                }
            }
            return copied
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun formatArchiveImportError(e: Exception): String {
        val raw = e.message ?: ""
        val lang = AppSettings.resolveLocale(this).language
        if (raw.startsWith("ENCRYPTED:")) {
            return when {
                lang.startsWith("zh") -> "不支持加密压缩包，请解压后导入"
                lang.startsWith("ja") -> "パスワード付きアーカイブには非対応です"
                else -> "Password-protected archives are not supported"
            }
        }
        if (raw.startsWith("ENOSPC:")) {
            return getString(R.string.import_no_space)
        }
        return e.localizedMessage ?: getString(R.string.error)
    }

    private fun findNormalizedSaveRoot(tempDir: File): File {
        val saveDataDir = tempDir.walkTopDown().maxDepth(3).firstOrNull {
            it.isDirectory && it.name.equals("SaveData", ignoreCase = true)
        }
        if (saveDataDir != null) {
            return saveDataDir
        }
        var cur = tempDir
        while (true) {
            val children = cur.listFiles()?.filter { !it.name.startsWith(".") } ?: break
            val files = children.filter { it.isFile }
            val dirs = children.filter { it.isDirectory }
            if (files.isEmpty() && dirs.size == 1) {
                cur = dirs[0]
            } else {
                break
            }
        }
        return cur
    }

    private fun runImportDirectory(treeUri: Uri) {
        val targetDir = currentTarget.saveDir
        val targetDirName = currentTarget.dirName
        showProgress(getString(R.string.save_progress_import_dir))
        activeJob = launchSaveOp {
            try {
                val count = withContext(Dispatchers.IO) {
                    backupSaves(targetDir, targetDirName)
                    val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
                    val children = listDocumentChildren(treeUri, rootDocId)
                    val saveDataSubdir = children.firstOrNull {
                        it.isDirectory && it.name.equals("SaveData", ignoreCase = true)
                    }
                    val hasDirectSaveFiles = children.any {
                        !it.isDirectory && (it.name.endsWith(".asd", true) || it.name.endsWith(".sav", true) || it.name.endsWith(".rsm", true))
                    }
                    val sourceDocId = if (saveDataSubdir != null && !hasDirectSaveFiles) {
                        saveDataSubdir.documentId
                    } else {
                        rootDocId
                    }
                    copyDocumentTreeToInternal(treeUri, sourceDocId, targetDir)
                }
                onHost { it.dismissProgress() }
                onHost { it.refreshFileList() }
                hostMessage(
                    getString(R.string.save_import_done_title),
                    getString(R.string.save_import_dir_done_msg, count)
                )
            } catch (e: Exception) {
                Log.e("SaveManager", "Failed to import saves from directory", e)
                onHost { it.dismissProgress() }
                hostMessage(
                    getString(R.string.save_import_failed_title),
                    e.localizedMessage ?: getString(R.string.error)
                )
            }
        }
    }

    private data class DocNode(val documentId: String, val name: String, val isDirectory: Boolean)

    private fun listDocumentChildren(treeUri: Uri, parentDocId: String): List<DocNode> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val result = mutableListOf<DocNode>()
        contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
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

    /**
     * Turns a provider-supplied display name into a single safe path component, or null.
     * DISPLAY_NAME comes from an arbitrary DocumentsProvider and may contain separators
     * or be "..", which would let a copy escape the save folder.
     */
    private fun safeChildName(raw: String): String? {
        val name = ArchiveExtractor.repairSjisAsGbkMojibake(raw)
            .substringAfterLast('/').substringAfterLast('\\').trim()
        if (name.isEmpty() || name == "." || name == ".." || name.contains('\u0000')) return null
        return name
    }

    private fun isInside(root: File, file: File): Boolean {
        val r = root.canonicalPath
        val f = file.canonicalPath
        return f.startsWith(r + File.separator)
    }

    private fun copyDocumentTreeToInternal(
        treeUri: Uri,
        parentDocId: String,
        destDir: File,
        rootDir: File = destDir
    ): Int {
        destDir.mkdirs()
        var count = 0
        for (child in listDocumentChildren(treeUri, parentDocId)) {
            if (child.name.startsWith(".")) continue
            val cleanName = safeChildName(child.name) ?: continue
            if (child.isDirectory) {
                val subDir = File(destDir, cleanName)
                if (!isInside(rootDir, subDir)) continue
                count += copyDocumentTreeToInternal(treeUri, child.documentId, subDir, rootDir)
            } else {
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, child.documentId)
                val outFile = File(destDir, cleanName)
                if (!isInside(rootDir, outFile)) continue
                contentResolver.openInputStream(docUri)?.use { input ->
                    outFile.outputStream().buffered().use { output ->
                        input.copyTo(output)
                    }
                    count++
                }
            }
        }
        return count
    }

    private fun runImportFiles(uris: List<Uri>) {
        val targetDir = currentTarget.saveDir
        val targetDirName = currentTarget.dirName
        showProgress(getString(R.string.save_progress_import_files))
        activeJob = launchSaveOp {
            try {
                val count = withContext(Dispatchers.IO) {
                    targetDir.mkdirs()
                    backupSaves(targetDir, targetDirName)
                    var imported = 0
                    for (uri in uris) {
                        val fileName = queryDisplayName(uri) ?: "save_${System.currentTimeMillis()}.asd"
                        val safeName = safeChildName(fileName) ?: continue
                        if (ArchiveExtractor.isSupportedArchiveName(safeName)) {
                            imported += extractSaveArchiveToTarget(uri, targetDir)
                        } else {
                            val dst = File(targetDir, safeName)
                            contentResolver.openInputStream(uri)?.use { input ->
                                dst.outputStream().buffered().use { output ->
                                    input.copyTo(output)
                                }
                                imported++
                            }
                        }
                    }
                    imported
                }
                onHost { it.dismissProgress() }
                onHost { it.refreshFileList() }
                hostMessage(
                    getString(R.string.save_import_done_title),
                    getString(R.string.save_import_files_done_msg, count)
                )
            } catch (e: Exception) {
                Log.e("SaveManager", "Failed to import save files", e)
                onHost { it.dismissProgress() }
                hostMessage(
                    getString(R.string.save_import_failed_title),
                    e.localizedMessage ?: getString(R.string.error)
                )
            }
        }
    }

    private fun copyExportFile(srcFile: File, outStream: java.io.OutputStream, asPc: Boolean): Boolean {
        if (asPc) {
            return GameModifierBridge.copySaveFileForPc(srcFile, outStream, cacheDir)
        }
        srcFile.inputStream().buffered().use { input ->
            input.copyTo(outStream)
        }
        return false
    }

    private fun formatExportDoneMessage(exported: Int, converted: Int, asPc: Boolean, defaultMsgResId: Int): String {
        return if (asPc && converted > 0) {
            getString(R.string.save_export_pc_converted_done_msg, exported, converted)
        } else {
            getString(defaultMsgResId, exported)
        }
    }

    private fun runExportZip(uri: Uri, asPc: Boolean) {
        val entriesToExport = entriesForExport(asPc)
        showProgress(getString(R.string.save_progress_export_zip))
        activeJob = launchSaveOp {
            try {
                val (count, converted) = withContext(Dispatchers.IO) {
                    val outStream = contentResolver.openOutputStream(uri)
                        ?: throw IOException("Failed to create target ZIP file")
                    var exported = 0
                    var convCount = 0
                    ZipOutputStream(outStream.buffered()).use { zos ->
                        for (entry in entriesToExport) {
                            if (!entry.file.exists()) continue
                            val zipEntry = ZipEntry("SaveData/${entry.relativePath}")
                            zipEntry.time = entry.lastModified
                            zos.putNextEntry(zipEntry)
                            if (copyExportFile(entry.file, zos, asPc)) {
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
                    getString(R.string.save_export_done_title),
                    formatExportDoneMessage(count, converted, asPc, R.string.save_export_zip_done_msg)
                )
            } catch (e: Exception) {
                Log.e("SaveManager", "Failed to export saves to ZIP", e)
                onHost { it.dismissProgress() }
                hostMessage(
                    getString(R.string.save_export_failed_title),
                    e.localizedMessage ?: getString(R.string.error)
                )
            }
        }
    }

    private fun runExportDirectory(treeUri: Uri, asPc: Boolean) {
        val entriesToExport = entriesForExport(asPc)
        showProgress(getString(R.string.save_progress_export_dir))
        activeJob = launchSaveOp {
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
                            parentDocId = getOrCreateSubdir(treeUri, parentDocId, parts[i])
                        }
                        val fileName = parts.last()
                        val existing = listDocumentChildren(treeUri, parentDocId)
                            .firstOrNull { !it.isDirectory && it.name == fileName }
                        if (existing != null) {
                            val existingUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, existing.documentId)
                            try {
                                DocumentsContract.deleteDocument(contentResolver, existingUri)
                            } catch (_: Exception) {
                            }
                        }
                        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
                        val newDocUri = DocumentsContract.createDocument(
                            contentResolver,
                            parentUri,
                            "application/octet-stream",
                            fileName
                        ) ?: throw IOException("Failed to create file: $fileName")
                        contentResolver.openOutputStream(newDocUri)?.use { out ->
                            if (copyExportFile(entry.file, out, asPc)) {
                                convCount++
                            }
                        } ?: throw IOException("Failed to write file: $fileName")
                        exported++
                    }
                    exported to convCount
                }
                onHost { it.dismissProgress() }
                hostMessage(
                    getString(R.string.save_export_done_title),
                    formatExportDoneMessage(count, converted, asPc, R.string.save_export_dir_done_msg)
                )
            } catch (e: Exception) {
                Log.e("SaveManager", "Failed to export saves to directory", e)
                onHost { it.dismissProgress() }
                hostMessage(
                    getString(R.string.save_export_failed_title),
                    e.localizedMessage ?: getString(R.string.error)
                )
            }
        }
    }

    private fun getOrCreateSubdir(treeUri: Uri, parentDocId: String, dirName: String): String {
        val existing = listDocumentChildren(treeUri, parentDocId)
            .firstOrNull { it.isDirectory && it.name == dirName }
        if (existing != null) return existing.documentId

        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
        val createdUri = DocumentsContract.createDocument(
            contentResolver,
            parentUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            dirName
        ) ?: throw IOException("Failed to create directory: $dirName")
        return DocumentsContract.getDocumentId(createdUri)
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
        items.add(getString(R.string.save_file_opt_backup) to { showDuplicateFileDialog(entry) })
        items.add(getString(R.string.save_file_opt_rename) to {
            ensureGameNotRunning(this) { showRenameFileDialog(entry) }
        })
        items.add(getString(R.string.save_file_opt_delete) to {
            ensureGameNotRunning(this) { showDeleteSingleFileDialog(entry) }
        })

        val options = items.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.save_file_dialog_title, entry.relativePath, formatFileSize(entry.sizeBytes)))
            .setItems(options) { _, which ->
                items[which].second()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
        showProgress(getString(R.string.save_progress_export_file))
        activeJob = launchSaveOp {
            try {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(destUri)?.use { out ->
                        copyExportFile(srcFile, out, asPc)
                    } ?: throw IOException("Failed to write target file")
                }
                onHost { it.dismissProgress() }
                Toast.makeText(
                    applicationContext,
                    getString(R.string.save_file_exported_toast, srcFile.name),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                onHost { it.dismissProgress() }
                hostMessage(
                    getString(R.string.save_export_failed_title),
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
        AlertDialog.Builder(this)
            .setTitle(R.string.save_backup_title)
            .setView(UiKit.dialogContent(this, input))
            .setPositiveButton(R.string.ok) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty() || newName.contains('/') || newName.contains('\\')) {
                    Toast.makeText(this, R.string.save_invalid_filename, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                try {
                    val dst = File(entry.file.parentFile, newName)
                    entry.file.copyTo(dst, overwrite = true)
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
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showRenameFileDialog(entry: SaveFileEntry) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(entry.file.name)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.save_rename_title)
            .setView(UiKit.dialogContent(this, input))
            .setPositiveButton(R.string.ok) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty() || newName == entry.file.name || newName.contains('/') || newName.contains('\\')) {
                    return@setPositiveButton
                }
                val dst = File(entry.file.parentFile, newName)
                if (dst.exists()) {
                    Toast.makeText(this, R.string.save_rename_exists_toast, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
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
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDeleteSingleFileDialog(entry: SaveFileEntry) {
        AlertDialog.Builder(this)
            .setTitle(R.string.save_delete_title)
            .setMessage(getString(R.string.save_delete_confirm_msg, entry.relativePath))
            .setPositiveButton(R.string.ok) { _, _ ->
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
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showClearSavesDialog() {
        if (currentEntries.isEmpty()) {
            Toast.makeText(this, R.string.save_clear_already_empty, Toast.LENGTH_SHORT).show()
            return
        }
        confirmDeleteAllSaves()
    }

    private fun confirmDeleteAllSaves() {
        AlertDialog.Builder(this)
            .setTitle(R.string.save_clear_confirm_title)
            .setMessage(getString(R.string.save_clear_confirm_msg, currentEntries.size))
            .setPositiveButton(R.string.ok) { _, _ ->
                currentTarget.saveDir.listFiles()?.forEach { child ->
                    child.deleteRecursively()
                }
                refreshFileList()
                Toast.makeText(this, R.string.save_cleared_toast, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment
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
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }
}
