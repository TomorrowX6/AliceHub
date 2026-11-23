package io.github.kichikuou.xsystem4

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.ProgressBar
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

private const val REQ_MOD_LOCAL_ARCHIVE = 204
private const val REQ_MOD_LOCAL_DIR = 205

// Status-card views live in mod_manager_header, added with addHeaderView. Lint only
// sees the activity layout, so those ids look missing.
@SuppressLint("MissingInflatedId")
class ModManagerActivity : ThemedActivity(), GameListObserver {
    companion object {
        const val EXTRA_GAME_PATH = "EXTRA_GAME_PATH"
        const val EXTRA_GAME_ID = "EXTRA_GAME_ID"
        private const val STATE_TARGET_PATH = "STATE_MOD_MGR_TARGET_PATH"
        private const val STATE_TARGET_GAME_ID = "STATE_MOD_MGR_TARGET_GAME_ID"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var catalogJob: Job? = null
    private var progressDialog: Dialog? = null
    private var restoreProgressDialog: Dialog? = null
    private val trackedDialogs = mutableListOf<Dialog>()
    private var lastProgressRenderMs: Long = 0L

    private lateinit var gameList: GameList
    private var targetGameId: String = GameCatalog.ID_RANCE10
    private lateinit var descriptor: GameDescriptor
    private val isCnPatch: Boolean
        get() = descriptor.isCnPatch
    private var currentTargetPath: String = ""
    private lateinit var currentTarget: Item
    private lateinit var adapter: ModReleaseAdapter

    private var releases: List<ModReleaseEntry> = emptyList()
    private var isLoadingCatalog: Boolean = false
    private var catalogLoadError: Boolean = false
    private var loadedDownloadRoute: String? = null

    private var pendingPostResumeAction: (() -> Unit)? = null

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

    private fun isInstallingCurrentTarget(): Boolean {
        if (!::gameList.isInitialized) return false
        return gameList.isInstallingForGame(targetGameId) ||
            (::currentTarget.isInitialized && gameList.isInstallingInto(currentTarget))
    }

    private fun resolveGameContext(srcIntent: Intent?, savedInstanceState: Bundle? = null) {
        val pathExtra = savedInstanceState?.getString(STATE_TARGET_PATH)?.takeIf { it.isNotBlank() }
            ?: srcIntent?.getStringExtra(EXTRA_GAME_PATH)?.takeIf { it.isNotBlank() }
        if (pathExtra != null) {
            currentTargetPath = pathExtra
        }
        val idExtra = savedInstanceState?.getString(STATE_TARGET_GAME_ID)?.takeIf { it.isNotBlank() }
            ?: srcIntent?.getStringExtra(EXTRA_GAME_ID)?.takeIf { it.isNotBlank() }
        targetGameId = idExtra ?: if (currentTargetPath.isNotBlank()) {
            val f = File(currentTargetPath)
            GameCatalog.classifyDirectory(f, f.name)
        } else {
            GameCatalog.ID_RANCE10
        }
        descriptor = GameCatalog.descriptorFor(this, targetGameId, gameList)
        title = getString(if (isCnPatch) R.string.action_install_cn_patch else R.string.mod_mgr_activity_title)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mod_manager)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        // The status card and its buttons scroll with the list as its header. It has to be
        // added before the adapter is set; its views are then found by findViewById() as usual.
        val listView = findViewById<ListView>(R.id.mod_mgr_version_list)
        listView.addHeaderView(
            layoutInflater.inflate(R.layout.mod_manager_header, listView, false), null, false
        )

        gameList = LauncherActivity.obtainGameList(this)
        resolveGameContext(intent, savedInstanceState)
        val candidates = overlayGameItems()
        if (candidates.isEmpty()) {
            Toast.makeText(this, R.string.mod_no_game_installed, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (currentTargetPath.isBlank()) {
            currentTargetPath = candidates[0].path.path
        }
        rebindCurrentTarget()

        adapter = ModReleaseAdapter()
        listView.adapter = adapter

        findViewById<View>(R.id.btn_mod_mgr_switch_game).setOnClickListener {
            showSwitchGameDialog()
        }
        findViewById<View>(R.id.btn_mod_mgr_update).setOnClickListener {
            onPrimaryUpdateClicked()
        }
        findViewById<View>(R.id.btn_mod_mgr_local).setOnClickListener {
            showLocalModInstallDialog()
        }
        findViewById<View>(R.id.btn_mod_mgr_restore).setOnClickListener {
            confirmRestoreVanilla()
        }

        listView.setOnItemClickListener { _, _, pos, _ ->
            val index = pos - listView.headerViewsCount
            if (index in releases.indices) {
                showReleaseDetailDialog(index)
            }
        }

        handleRestoreDialogIntent(intent)
        if (AppSettings.isOnlineInstallEnabled(this)) {
            loadCatalog()
        } else {
            updateUi()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (currentTargetPath.isNotBlank()) {
            outState.putString(STATE_TARGET_PATH, currentTargetPath)
        }
        if (targetGameId.isNotBlank()) {
            outState.putString(STATE_TARGET_GAME_ID, targetGameId)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null && (intent.hasExtra(EXTRA_GAME_ID) || intent.hasExtra(EXTRA_GAME_PATH))) {
            setIntent(intent)
        }
        val prevGameId = targetGameId
        resolveGameContext(intent)
        if (targetGameId != prevGameId) {
            releases = emptyList()
            catalogLoadError = false
            if (AppSettings.isOnlineInstallEnabled(this)) {
                loadCatalog()
            }
        }
        if (::gameList.isInitialized && gameList.size > 0) {
            rebindCurrentTarget()
            handleRestoreDialogIntent(intent)
            updateUi()
            renderInstallState(gameList.installState)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == InstallForegroundService.REQ_POST_NOTIFICATIONS) {
            InstallForegroundService.flushLatestNotification(this)
        }
    }

    private fun handleRestoreDialogIntent(srcIntent: Intent?) {
        if (srcIntent?.getBooleanExtra(InstallForegroundService.EXTRA_RESTORE_INSTALL_DIALOG, false) == true) {
            srcIntent.removeExtra(InstallForegroundService.EXTRA_RESTORE_INSTALL_DIALOG)
            if (isInstallingCurrentTarget()) {
                gameList.restoreInstallProgress()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        gameList = LauncherActivity.obtainGameList(this)
        if (overlayGameItems().isEmpty()) {
            finish()
            return
        }
        rebindCurrentTarget()
        gameList.observer = this
        val onlineEnabled = AppSettings.isOnlineInstallEnabled(this)
        val currentRoute = AppSettings.getDownloadRoute(this)
        if (!onlineEnabled) {
            catalogJob?.cancel()
            catalogJob = null
            isLoadingCatalog = false
            catalogLoadError = false
            loadedDownloadRoute = null
            releases = emptyList()
        } else if ((loadedDownloadRoute != null && loadedDownloadRoute != currentRoute) ||
            (releases.isEmpty() && !isLoadingCatalog && !catalogLoadError)
        ) {
            loadCatalog()
        }
        if (gameList.isRestoringMod) {
            showRestoreProgressDialog()
        } else {
            dismissRestoreProgressDialog()
        }
        updateUi()
        renderInstallState(gameList.installState)

        val postAction = pendingPostResumeAction
        pendingPostResumeAction = null
        postAction?.invoke()
    }

    override fun onPause() {
        if (::gameList.isInitialized && gameList.observer === this) {
            gameList.observer = null
        }
        dismissProgressDialog()
        dismissRestoreProgressDialog()
        super.onPause()
    }

    override fun onDestroy() {
        dismissProgressDialog()
        dismissRestoreProgressDialog()
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_MOD_LOCAL_ARCHIVE -> {
                val uri = if (resultCode == RESULT_OK) data?.data else null
                if (uri != null) {
                    pendingPostResumeAction = {
                        ensureGameNotRunning(this) {
                            InstallForegroundService.requestNotificationPermissionIfNeeded(this)
                            rebindCurrentTarget()
                            gameList.installModArchive(currentTarget, uri)
                            updateUi()
                            renderInstallState(gameList.installState)
                        }
                    }
                }
            }
            REQ_MOD_LOCAL_DIR -> {
                val uri = if (resultCode == RESULT_OK) data?.data else null
                if (uri != null) {
                    pendingPostResumeAction = {
                        ensureGameNotRunning(this) {
                            InstallForegroundService.requestNotificationPermissionIfNeeded(this)
                            rebindCurrentTarget()
                            gameList.installModDirectory(currentTarget, uri)
                            updateUi()
                            renderInstallState(gameList.installState)
                        }
                    }
                }
            }
        }
    }

    private fun overlayGameItems(): List<Item> =
        GameCatalog.matchingItems(gameList, targetGameId)

    private fun rebindCurrentTarget() {
        val candidates = overlayGameItems()
        if (candidates.isEmpty()) return
        currentTarget = candidates.firstOrNull { it.path.path == currentTargetPath } ?: candidates[0]
        currentTargetPath = currentTarget.path.path
    }

    private fun loadCatalog() {
        if (!AppSettings.isOnlineInstallEnabled(this)) {
            catalogJob?.cancel()
            catalogJob = null
            isLoadingCatalog = false
            catalogLoadError = false
            releases = emptyList()
            updateUi()
            return
        }
        catalogJob?.cancel()
        isLoadingCatalog = true
        catalogLoadError = false
        updateUi()

        val route = AppSettings.getDownloadRoute(this)
        loadedDownloadRoute = route
        val cdnBaseUrl = AppSettings.applyDownloadRoute(
            descriptor.effectiveOverlayCdnUrl ?: OnlineInstaller.RANCE10_CDN_URL,
            route
        )
        val catalogRelPath = descriptor.overlayCatalogRelPath
        catalogJob = scope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    OnlineInstaller.fetchModCatalog(cdnBaseUrl, catalogRelPath)
                }
                releases = loaded
                syncLocalModVersionWithCatalog(loaded)
                isLoadingCatalog = false
                catalogLoadError = false
                updateUi()
            } catch (_: Throwable) {
                isLoadingCatalog = false
                catalogLoadError = true
                updateUi()
            }
        }
    }

    private fun syncLocalModVersionWithCatalog(catalog: List<ModReleaseEntry>) {
        if (catalog.isEmpty() || !::currentTarget.isInitialized) return
        val hasMod = gameList.hasInstalledMod(currentTarget)
        if (!hasMod) return
        val installed = gameList.getInstalledModInfo(currentTarget) ?: return
        val idx = OnlineInstaller.matchInstalledIndex(catalog, installed)
        if (idx !in catalog.indices) return
        val matched = catalog[idx]
        if (installed.version != matched.version || (installed.fileId <= 0 && matched.fileId > 0)) {
            val resolvedBaseVer = if (!matched.isPatch) {
                matched.version
            } else {
                matched.baseVersion ?: installed.baseVersion
            }
            val resolvedBaseFid = if (!matched.isPatch) {
                matched.fileId
            } else {
                matched.baseFileId ?: installed.baseFileId
            }
            InstalledModInfo.write(
                currentTarget.path,
                installed.copy(
                    version = matched.version,
                    baseVersion = resolvedBaseVer,
                    fileId = if (installed.fileId > 0) installed.fileId else matched.fileId,
                    baseFileId = if (installed.baseFileId > 0) installed.baseFileId else resolvedBaseFid,
                    kind = descriptor.overlayKind
                )
            )
            gameList.refreshItemMetadata(currentTarget)
            rebindCurrentTarget()
        }
    }

    private fun updateUi() {
        val onlineEnabled = AppSettings.isOnlineInstallEnabled(this)
        val hasMod = gameList.hasInstalledMod(currentTarget)
        val installed = gameList.getInstalledModInfo(currentTarget)
        val installedIdx = if (hasMod) OnlineInstaller.matchInstalledIndex(releases, installed) else -1
        val displayVer = when {
            installedIdx in releases.indices -> releases[installedIdx].version
            installed != null && installed.version.isNotBlank() -> installed.version
            else -> ""
        }

        findViewById<TextView>(R.id.mod_mgr_title).text =
            getString(R.string.save_manager_current_dir, currentTarget.path.name)

        val statusPart = when {
            !hasMod -> getString(if (isCnPatch) R.string.cn_patch_mgr_status_none else R.string.mod_mgr_status_none)
            displayVer.isNotBlank() -> getString(R.string.mod_mgr_status_ver, displayVer)
            else -> getString(if (isCnPatch) R.string.cn_patch_mgr_status_installed else R.string.mod_mgr_status_installed)
        }
        val summaryView = findViewById<TextView>(R.id.mod_mgr_summary)
        summaryView.text = if (onlineEnabled && releases.isNotEmpty()) {
            "$statusPart · ${getString(R.string.mod_mgr_summary_format, releases[0].version)}"
        } else {
            statusPart
        }
        summaryView.visibility = View.VISIBLE

        val isInstallingAny = gameList.installState is InstallState.Installing
        val isInstallingThis = isInstallingCurrentTarget()
        val isRestoring = gameList.isRestoringMod
        val isBusy = isInstallingAny || isRestoring
        val btnLocal = findViewById<Button>(R.id.btn_mod_mgr_local)
        findViewById<Button>(R.id.btn_mod_mgr_switch_game).isEnabled = !isBusy
        btnLocal.isEnabled = !isBusy
        val btnRestore = findViewById<Button>(R.id.btn_mod_mgr_restore)
        btnRestore.setText(if (isCnPatch) R.string.action_uninstall_cn_patch else R.string.action_uninstall_mod)
        btnRestore.isEnabled = gameList.canRestoreVanilla(currentTarget) && !isBusy

        val btnUpdate = findViewById<Button>(R.id.btn_mod_mgr_update)
        val showUpdateBtn = onlineEnabled || isInstallingThis
        btnUpdate.visibility = if (showUpdateBtn) View.VISIBLE else View.GONE
        val targetStartMargin = if (showUpdateBtn) UiKit.dpInt(this, 4) else 0
        (btnLocal.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            if (lp.marginStart != targetStartMargin) {
                lp.marginStart = targetStartMargin
                btnLocal.layoutParams = lp
            }
        }

        when {
            isInstallingThis -> {
                btnUpdate.text = getString(R.string.mod_mgr_btn_downloading)
                btnUpdate.isEnabled = true
            }
            isBusy -> {
                btnUpdate.text = getString(
                    if (!hasMod) R.string.mod_mgr_btn_install_latest else R.string.mod_mgr_btn_update_latest
                )
                btnUpdate.isEnabled = false
            }
            !onlineEnabled -> {
                btnUpdate.isEnabled = false
            }
            isLoadingCatalog && releases.isEmpty() -> {
                btnUpdate.text = getString(R.string.mod_mgr_btn_checking)
                btnUpdate.isEnabled = false
            }
            catalogLoadError && releases.isEmpty() -> {
                btnUpdate.text = getString(R.string.mod_mgr_btn_retry)
                btnUpdate.isEnabled = true
            }
            releases.isNotEmpty() -> {
                val plan = OnlineInstaller.computeUpdatePlan(releases, hasMod, installed)
                if (plan == null) {
                    btnUpdate.text = getString(R.string.mod_mgr_btn_up_to_date)
                    btnUpdate.isEnabled = false
                } else {
                    val baseLabel = getString(
                        if (!hasMod) R.string.mod_mgr_btn_install_latest else R.string.mod_mgr_btn_update_latest
                    )
                    val planBytes = plan.steps.sumOf { it.expectedSize.coerceAtLeast(0L) }
                    val sizeStr = OnlineInstaller.formatSize(planBytes)
                    btnUpdate.text = if (sizeStr.isNotEmpty()) "$baseLabel · $sizeStr" else baseLabel
                    btnUpdate.isEnabled = true
                }
            }
            else -> {
                btnUpdate.text = getString(R.string.mod_mgr_btn_install_latest)
                btnUpdate.isEnabled = false
            }
        }

        val emptyView = findViewById<TextView>(R.id.mod_mgr_empty_view)
        if (releases.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            val label = when {
                !onlineEnabled -> getString(R.string.mod_mgr_online_disabled)
                isLoadingCatalog -> getString(R.string.mod_mgr_loading)
                catalogLoadError -> getString(R.string.mod_mgr_load_failed)
                else -> getString(if (isCnPatch) R.string.cn_patch_mgr_empty else R.string.mod_mgr_empty)
            }
            UiKit.bindEmptyState(
                emptyView,
                if (isCnPatch) R.drawable.ic_language else R.drawable.ic_extension,
                label
            )
        } else {
            emptyView.visibility = View.GONE
        }

        adapter.notifyDataSetChanged()
    }

    private fun showOtherInstallBusyToast() {
        Toast.makeText(
            this,
            GamepadBridge.tr(
                this,
                "后台正在进行其他安装任务，请稍候",
                "別のインストールタスクが進行中です",
                "Another installation is currently in progress"
            ),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showSwitchGameDialog() {
        val candidates = overlayGameItems()
        if (gameList.installState is InstallState.Installing || gameList.isRestoringMod || candidates.isEmpty()) return
        val fallbackTag = if (isCnPatch) "CN" else "MOD"
        val names = Array(candidates.size) { idx ->
            val it = candidates[idx]
            val ver = if (gameList.hasInstalledMod(it)) {
                gameList.getInstalledModInfo(it)?.version?.takeIf { v -> v.isNotBlank() } ?: fallbackTag
            } else {
                null
            }
            if (ver != null) "${it.path.name} · $ver" else it.path.name
        }
        val curIdx = candidates.indexOfFirst { it.path.path == currentTargetPath }.coerceAtLeast(0)
        trackDialog(
            AlertDialog.Builder(this)
                .setTitle(R.string.mod_select_game_title)
                .setSingleChoiceItems(names, curIdx) { dlg, which ->
                    if (which in candidates.indices) {
                        currentTarget = candidates[which]
                        currentTargetPath = currentTarget.path.path
                        updateUi()
                    }
                    dlg.dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        )
    }

    private fun showLocalModInstallDialog() {
        if (gameList.isRestoringMod) return
        if (gameList.installState is InstallState.Installing) {
            if (isInstallingCurrentTarget()) {
                gameList.restoreInstallProgress()
                renderInstallState(gameList.installState)
            } else {
                showOtherInstallBusyToast()
            }
            return
        }
        val options = arrayOf(
            getString(R.string.mod_opt_zip),
            getString(R.string.mod_opt_dir)
        )
        trackDialog(
            AlertDialog.Builder(this)
                .setTitle(if (isCnPatch) R.string.cn_patch_local_dialog_title else R.string.mod_local_dialog_title)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            val chooseTitle = getString(
                                if (isCnPatch) R.string.cn_patch_choose_zip else R.string.mod_choose_zip
                            )
                            startActivityForResult(
                                Intent.createChooser(i, chooseTitle),
                                REQ_MOD_LOCAL_ARCHIVE
                            )
                        }
                        1 -> {
                            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }, REQ_MOD_LOCAL_DIR)
                        }
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        )
    }

    private fun onPrimaryUpdateClicked() {
        if (gameList.isRestoringMod) return
        if (gameList.installState is InstallState.Installing) {
            if (isInstallingCurrentTarget()) {
                gameList.restoreInstallProgress()
                renderInstallState(gameList.installState)
            } else {
                showOtherInstallBusyToast()
            }
            return
        }
        if (catalogLoadError && releases.isEmpty()) {
            loadCatalog()
            return
        }
        val hasMod = gameList.hasInstalledMod(currentTarget)
        val installed = gameList.getInstalledModInfo(currentTarget)
        val plan = OnlineInstaller.computeUpdatePlan(releases, hasMod, installed) ?: return
        executeSteps(plan.steps)
    }

    private fun confirmRestoreVanilla() {
        if (gameList.installState is InstallState.Installing || gameList.isRestoringMod) return
        if (!gameList.canRestoreVanilla(currentTarget)) return
        trackDialog(
            AlertDialog.Builder(this)
                .setTitle(if (isCnPatch) R.string.action_uninstall_cn_patch else R.string.action_uninstall_mod)
                .setMessage(if (isCnPatch) R.string.cn_patch_mgr_restore_confirm else R.string.mod_mgr_restore_confirm)
                .setPositiveButton(R.string.ok) { _, _ ->
                    ensureGameNotRunning(this) {
                        startRestoreVanillaAsync()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        )
    }

    private fun startRestoreVanillaAsync() {
        if (gameList.installState is InstallState.Installing || gameList.isRestoringMod) return
        val restoredToastRes = if (isCnPatch) R.string.cn_patch_restored_toast else R.string.mod_restored_toast
        val appCtx = AppSettings.wrapThemeContext(applicationContext)
        val started = gameList.launchRestoreMod(currentTarget) { ok ->
            if (!isFinishing && !isDestroyed) {
                dismissRestoreProgressDialog()
                rebindCurrentTarget()
                updateUi()
            }
            if (ok) {
                Toast.makeText(appCtx, restoredToastRes, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(appCtx, R.string.error, Toast.LENGTH_SHORT).show()
            }
        }
        if (started) {
            showRestoreProgressDialog()
            updateUi()
        }
    }

    private fun showRestoreProgressDialog() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (restoreProgressDialog == null) {
            restoreProgressDialog = Dialog(this).apply {
                setTitle(if (isCnPatch) R.string.action_uninstall_cn_patch else R.string.action_uninstall_mod)
                setCancelable(false)
                setContentView(R.layout.progress_dialog)
                findViewById<View>(R.id.action_bar)?.visibility = View.GONE
                findViewById<ProgressBar>(R.id.progress_bar)?.visibility = View.GONE
                findViewById<TextView>(R.id.text)?.text = GamepadBridge.tr(
                    this@ModManagerActivity,
                    "正在还原原版文件…",
                    "バニラファイルを復元中…",
                    "Restoring vanilla files…"
                )
                show()
            }
            scope.launch {
                while (gameList.isRestoringMod) {
                    kotlinx.coroutines.delay(150)
                }
                if (!isFinishing && !isDestroyed && restoreProgressDialog != null) {
                    dismissRestoreProgressDialog()
                    rebindCurrentTarget()
                    updateUi()
                }
            }
        }
    }

    private fun dismissRestoreProgressDialog() {
        restoreProgressDialog?.dismiss()
        restoreProgressDialog = null
        if (progressDialog == null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun showReleaseDetailDialog(index: Int) {
        if (gameList.isRestoringMod) return
        if (gameList.installState is InstallState.Installing) {
            if (isInstallingCurrentTarget()) {
                gameList.restoreInstallProgress()
                renderInstallState(gameList.installState)
            } else {
                showOtherInstallBusyToast()
            }
            return
        }
        if (index !in releases.indices) return
        val entry = releases[index]
        val hasMod = gameList.hasInstalledMod(currentTarget)
        val installed = gameList.getInstalledModInfo(currentTarget)
        val installedIdx = if (hasMod) OnlineInstaller.matchInstalledIndex(releases, installed) else -1
        val hasMatchingBase = OnlineInstaller.hasMatchingBaseForPatch(releases, index, hasMod, installed)
        val comboSteps = if (entry.isPatch) {
            OnlineInstaller.computeStepsForSelectedRelease(
                releases = releases,
                targetIdx = index,
                hasMod = hasMod,
                installed = installed,
                includeBaseForPatch = true
            )
        } else {
            emptyList()
        }
        val patchOnlySteps = if (entry.isPatch) {
            OnlineInstaller.computeStepsForSelectedRelease(
                releases = releases,
                targetIdx = index,
                hasMod = hasMod,
                installed = installed,
                includeBaseForPatch = false
            )
        } else {
            emptyList()
        }

        val msg = buildString {
            append(getString(R.string.mod_mgr_detail_file, entry.fileName))
            val sizeStr = OnlineInstaller.formatSize(entry.expectedSize)
            if (sizeStr.isNotEmpty()) {
                append("\n").append(getString(R.string.mod_mgr_detail_size, sizeStr))
            }
            if (entry.formattedDate.isNotEmpty()) {
                append("\n").append(getString(R.string.mod_mgr_detail_date, entry.formattedDate))
            }
            append("\n")
            if (entry.isPatch) {
                val base = entry.baseVersion ?: "-"
                append(getString(R.string.mod_mgr_detail_type_patch, base))
            } else {
                append(
                    getString(
                        if (isCnPatch) R.string.cn_patch_mgr_detail_type_full else R.string.mod_mgr_detail_type_full
                    )
                )
            }
            if (entry.isPatch && !hasMatchingBase && !entry.baseVersion.isNullOrBlank()) {
                append("\n\n")
                append(getString(R.string.mod_mgr_patch_combo_hint, entry.baseVersion))
            }
            if (entry.changelog.isNotBlank()) {
                append("\n\n")
                append(entry.changelog)
            }
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(entry.version)
            .setMessage(msg)
            .setNegativeButton(R.string.cancel, null)

        if (!entry.isPatch) {
            val posLabel = when {
                hasMod && installedIdx == index -> getString(R.string.mod_mgr_action_reinstall)
                hasMod -> getString(R.string.mod_mgr_action_switch)
                else -> getString(R.string.mod_mgr_action_install)
            }
            builder.setPositiveButton(posLabel) { _, _ ->
                executeSteps(listOf(entry))
            }
        } else {
            val patchSteps = patchOnlySteps.ifEmpty { listOf(entry) }
            if (hasMatchingBase) {
                val posLabel = if (installedIdx == index) {
                    getString(R.string.mod_mgr_action_reinstall_patch)
                } else {
                    getString(R.string.mod_mgr_action_apply_patch)
                }
                builder.setPositiveButton(posLabel) { _, _ ->
                    executeSteps(patchSteps)
                }
                if (comboSteps.size > 1) {
                    builder.setNeutralButton(R.string.mod_mgr_action_combo_reinstall) { _, _ ->
                        executeSteps(comboSteps)
                    }
                }
            } else if (comboSteps.size > 1) {
                builder.setPositiveButton(R.string.mod_mgr_action_combo_install) { _, _ ->
                    executeSteps(comboSteps)
                }
                builder.setNeutralButton(R.string.mod_mgr_action_patch_only) { _, _ ->
                    executeSteps(patchSteps)
                }
            } else {
                builder.setPositiveButton(R.string.mod_mgr_action_apply_patch) { _, _ ->
                    executeSteps(patchSteps)
                }
            }
        }

        trackDialog(builder.show())
    }

    private fun executeSteps(steps: List<ModReleaseEntry>) {
        if (!AppSettings.isOnlineInstallEnabled(this)) return
        if (steps.isEmpty() || gameList.installState is InstallState.Installing || gameList.isRestoringMod) return
        ensureGameNotRunning(this) {
            InstallForegroundService.requestNotificationPermissionIfNeeded(this)
            rebindCurrentTarget()
            gameList.installOnlineModPlan(currentTarget, steps)
            renderInstallState(gameList.installState)
        }
    }

    override fun onInstallProgress(path: String) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastProgressRenderMs < 80L) return
        lastProgressRenderMs = now
        val state = gameList.installState
        if (state is InstallState.Installing) {
            if (!isInstallingCurrentTarget() || gameList.isProgressMinimized) {
                dismissProgressDialog()
            } else {
                showProgressDialog(
                    progress = state.progress,
                    permille = state.permille,
                    cancellable = state.cancellable,
                    resumable = state.resumable
                )
            }
        } else {
            renderInstallState(state)
        }
    }

    override fun onInstallSuccess() {
        renderInstallState(gameList.installState)
    }

    override fun onInstallCancelled() {
        renderInstallState(gameList.installState)
    }

    override fun onInstallFailure(msgId: Int) {
        renderInstallState(gameList.installState)
    }

    private fun renderInstallState(state: InstallState) {
        when (state) {
            InstallState.Idle -> {
                dismissProgressDialog()
                updateUi()
            }
            is InstallState.Installing -> {
                updateUi()
                if (!isInstallingCurrentTarget() || gameList.isProgressMinimized) {
                    dismissProgressDialog()
                } else {
                    showProgressDialog(
                        progress = state.progress,
                        permille = state.permille,
                        cancellable = state.cancellable,
                        resumable = state.resumable
                    )
                }
            }
            InstallState.Succeeded -> {
                dismissProgressDialog()
                rebindCurrentTarget()
                updateUi()
                Toast.makeText(this, R.string.install_done_toast, Toast.LENGTH_SHORT).show()
                gameList.consumeInstallResult()
            }
            is InstallState.Cancelled -> {
                dismissProgressDialog()
                rebindCurrentTarget()
                updateUi()
                if (state.paused) {
                    Toast.makeText(this, R.string.online_download_paused_toast, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, R.string.install_cancelled_toast, Toast.LENGTH_SHORT).show()
                }
                gameList.consumeInstallResult()
            }
            is InstallState.Failed -> {
                dismissProgressDialog()
                rebindCurrentTarget()
                updateUi()
                gameList.consumeInstallResult()
                trackDialog(
                    AlertDialog.Builder(this)
                        .setTitle(R.string.error)
                        .setMessage(state.msgId)
                        .setPositiveButton(R.string.ok, null)
                        .show()
                )
            }
        }
    }

    private fun showProgressDialog(
        progress: String? = null,
        permille: Int? = null,
        cancellable: Boolean = false,
        resumable: Boolean = false
    ) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (progressDialog == null) {
            progressDialog = Dialog(this).apply {
                setTitle(R.string.install_dialog_title)
                setCancelable(false)
                setContentView(R.layout.progress_dialog)
                findViewById<Button>(R.id.btn_bg_install)?.setOnClickListener {
                    gameList.minimizeInstallProgress()
                    dismissProgressDialog()
                    updateUi()
                    InstallForegroundService.requestNotificationPermissionIfNeeded(
                        this@ModManagerActivity,
                        forceIfRationale = true
                    )
                    Toast.makeText(
                        this@ModManagerActivity,
                        R.string.install_background_toast,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                findViewById<Button>(R.id.btn_pause_install)?.setOnClickListener {
                    gameList.pauseInstall()
                }
                findViewById<Button>(R.id.btn_cancel_install)?.setOnClickListener {
                    gameList.cancelInstall()
                }
                show()
            }
        }
        val dlg = progressDialog ?: return
        progress?.let {
            dlg.findViewById<TextView>(R.id.text)?.text = getString(R.string.install_progress, it)
        }
        val bar = dlg.findViewById<ProgressBar>(R.id.progress_bar)
        if (bar != null) {
            if (permille != null) {
                bar.visibility = View.VISIBLE
                bar.progress = permille.coerceIn(0, 1000)
            } else {
                bar.visibility = View.GONE
            }
        }
        val pauseBtn = dlg.findViewById<Button>(R.id.btn_pause_install)
        if (pauseBtn != null) {
            pauseBtn.visibility = if (cancellable && resumable) View.VISIBLE else View.GONE
        }
        val cancelBtn = dlg.findViewById<Button>(R.id.btn_cancel_install)
        if (cancelBtn != null) {
            cancelBtn.visibility = if (cancellable) View.VISIBLE else View.GONE
            cancelBtn.setText(R.string.cancel)
        }
    }

    private fun dismissProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
        if (restoreProgressDialog == null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private inner class ModReleaseAdapter : BaseAdapter() {
        override fun getCount(): Int = releases.size
        override fun getItem(position: Int): ModReleaseEntry = releases[position]
        override fun getItemId(position: Int): Long = releases[position].fileId.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@ModManagerActivity)
                .inflate(R.layout.list_item_entry, parent, false)
            val entry = releases[position]
            val hasMod = gameList.hasInstalledMod(currentTarget)
            val installed = gameList.getInstalledModInfo(currentTarget)
            val installedIdx = if (hasMod) OnlineInstaller.matchInstalledIndex(releases, installed) else -1
            val isCurrent = hasMod && installedIdx == position
            val isCurrentBase = hasMod && !isCurrent && !entry.isPatch && installed != null && (
                (installed.baseFileId > 0 && installed.baseFileId == entry.fileId) ||
                    (!installed.baseVersion.isNullOrBlank() &&
                        OnlineInstaller.normalizeModVersionKey(installed.baseVersion) ==
                        OnlineInstaller.normalizeModVersionKey(entry.version))
                )

            val tags = mutableListOf<UiKit.Chip>()
            if (position == 0) {
                tags.add(UiKit.Chip(getString(R.string.mod_mgr_tag_latest), UiKit.Tone.PRIMARY))
            }
            if (isCurrent) {
                tags.add(UiKit.Chip(getString(R.string.mod_mgr_tag_installed), UiKit.Tone.SUCCESS))
            } else if (isCurrentBase) {
                tags.add(UiKit.Chip(getString(R.string.mod_mgr_tag_base), UiKit.Tone.SUCCESS))
            }
            if (entry.isPatch) {
                tags.add(UiKit.Chip(getString(R.string.mod_mgr_tag_patch), UiKit.Tone.NEUTRAL))
            }

            view.findViewById<TextView>(R.id.title).text = entry.version
            UiKit.bindChips(view, tags)

            val metaParts = mutableListOf<String>()
            val sizeStr = OnlineInstaller.formatSize(entry.expectedSize)
            if (sizeStr.isNotEmpty()) {
                metaParts.add(sizeStr)
            }
            if (entry.formattedDate.isNotEmpty()) {
                metaParts.add(entry.formattedDate)
            }
            if (entry.isPatch && !entry.baseVersion.isNullOrBlank()) {
                metaParts.add(getString(R.string.mod_mgr_based_on, entry.baseVersion))
            }
            metaParts.add(entry.fileName)

            view.findViewById<TextView>(R.id.meta).text = metaParts.joinToString(" · ")
            return view
        }
    }
}
