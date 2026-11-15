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

private const val REQ_MOD_LOCAL_ARCHIVE = 204
private const val REQ_MOD_LOCAL_DIR = 205

// Status-card views live in mod_manager_header, added with addHeaderView. Lint only
// sees the activity layout, so those ids look missing.
@SuppressLint("MissingInflatedId")
class ModManagerActivity : ThemedActivity(), GameListObserver {
    companion object {
        const val EXTRA_GAME_PATH = "EXTRA_GAME_PATH"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var catalogJob: Job? = null
    private var progressDialog: Dialog? = null

    private lateinit var gameList: GameList
    private var currentTargetPath: String = ""
    private lateinit var currentTarget: Item
    private lateinit var adapter: ModReleaseAdapter

    private var releases: List<ModReleaseEntry> = emptyList()
    private var isLoadingCatalog: Boolean = false
    private var catalogLoadError: Boolean = false

    private var pendingPostResumeAction: (() -> Unit)? = null

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
        val r10Candidates = GameCatalog.matchingItems(gameList, GameCatalog.ID_RANCE10)
        if (r10Candidates.isEmpty()) {
            Toast.makeText(this, R.string.mod_no_game_installed, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        currentTargetPath = intent.getStringExtra(EXTRA_GAME_PATH) ?: r10Candidates[0].path.path
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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent?.getStringExtra(EXTRA_GAME_PATH)?.takeIf { it.isNotBlank() }?.let {
            currentTargetPath = it
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
            if (::gameList.isInitialized) {
                gameList.restoreInstallProgress()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        gameList = LauncherActivity.obtainGameList(this)
        if (GameCatalog.matchingItems(gameList, GameCatalog.ID_RANCE10).isEmpty()) {
            finish()
            return
        }
        rebindCurrentTarget()
        gameList.observer = this
        val onlineEnabled = AppSettings.isOnlineInstallEnabled(this)
        if (!onlineEnabled) {
            catalogJob?.cancel()
            catalogJob = null
            isLoadingCatalog = false
            catalogLoadError = false
            releases = emptyList()
        } else if (releases.isEmpty() && !isLoadingCatalog && !catalogLoadError) {
            loadCatalog()
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
        super.onPause()
    }

    override fun onDestroy() {
        dismissProgressDialog()
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

    private fun rance10Items(): List<Item> =
        GameCatalog.matchingItems(gameList, GameCatalog.ID_RANCE10)

    private fun rebindCurrentTarget() {
        val candidates = rance10Items()
        if (candidates.isEmpty()) return
        currentTarget = candidates.firstOrNull { it.path.path == currentTargetPath } ?: candidates[0]
        currentTargetPath = currentTarget.path.path
    }

    private fun Item.cleanGameTitle(): String {
        val idx = name.indexOf(" [MOD")
        return if (idx > 0) name.substring(0, idx) else name
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

        catalogJob = scope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    OnlineInstaller.fetchModCatalog()
                }
                releases = loaded
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
            !hasMod -> getString(R.string.mod_mgr_status_none)
            displayVer.isNotBlank() -> getString(R.string.mod_mgr_status_ver, displayVer)
            else -> getString(R.string.mod_mgr_status_installed)
        }
        val summaryView = findViewById<TextView>(R.id.mod_mgr_summary)
        summaryView.text = if (onlineEnabled && releases.isNotEmpty()) {
            "$statusPart · ${getString(R.string.mod_mgr_summary_format, releases[0].version)}"
        } else {
            statusPart
        }
        summaryView.visibility = View.VISIBLE

        val isInstalling = gameList.installState is InstallState.Installing
        val btnLocal = findViewById<Button>(R.id.btn_mod_mgr_local)
        findViewById<Button>(R.id.btn_mod_mgr_switch_game).isEnabled = !isInstalling
        btnLocal.isEnabled = !isInstalling
        findViewById<Button>(R.id.btn_mod_mgr_restore).isEnabled =
            gameList.canRestoreVanilla(currentTarget) && !isInstalling

        val btnUpdate = findViewById<Button>(R.id.btn_mod_mgr_update)
        val showUpdateBtn = onlineEnabled || isInstalling
        btnUpdate.visibility = if (showUpdateBtn) View.VISIBLE else View.GONE
        val targetStartMargin = if (showUpdateBtn) UiKit.dpInt(this, 4) else 0
        (btnLocal.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            if (lp.marginStart != targetStartMargin) {
                lp.marginStart = targetStartMargin
                btnLocal.layoutParams = lp
            }
        }

        when {
            isInstalling -> {
                btnUpdate.text = getString(R.string.mod_mgr_btn_downloading)
                btnUpdate.isEnabled = true
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
                else -> getString(R.string.mod_mgr_empty)
            }
            UiKit.bindEmptyState(emptyView, R.drawable.ic_extension, label)
        } else {
            emptyView.visibility = View.GONE
        }

        adapter.notifyDataSetChanged()
    }

    private fun showSwitchGameDialog() {
        val candidates = rance10Items()
        if (gameList.installState is InstallState.Installing || candidates.isEmpty()) return
        val names = Array(candidates.size) { idx ->
            val it = candidates[idx]
            val ver = if (gameList.hasInstalledMod(it)) {
                gameList.getInstalledModInfo(it)?.version?.takeIf { v -> v.isNotBlank() } ?: "MOD"
            } else {
                null
            }
            if (ver != null) "${it.path.name} · $ver" else it.path.name
        }
        val curIdx = candidates.indexOfFirst { it.path.path == currentTargetPath }.coerceAtLeast(0)
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
    }

    private fun showLocalModInstallDialog() {
        if (gameList.installState is InstallState.Installing) {
            gameList.restoreInstallProgress()
            renderInstallState(gameList.installState)
            return
        }
        val options = arrayOf(
            getString(R.string.mod_opt_zip),
            getString(R.string.mod_opt_dir)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.mod_local_dialog_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivityForResult(
                            Intent.createChooser(i, getString(R.string.mod_choose_zip)),
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
    }

    private fun onPrimaryUpdateClicked() {
        if (gameList.installState is InstallState.Installing) {
            gameList.restoreInstallProgress()
            renderInstallState(gameList.installState)
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
        if (gameList.installState is InstallState.Installing) return
        if (!gameList.canRestoreVanilla(currentTarget)) return
        AlertDialog.Builder(this)
            .setTitle(R.string.action_uninstall_mod)
            .setMessage(R.string.mod_mgr_restore_confirm)
            .setPositiveButton(R.string.ok) { _, _ ->
                ensureGameNotRunning(this) {
                    if (gameList.restoreMod(currentTarget)) {
                        rebindCurrentTarget()
                        updateUi()
                        Toast.makeText(this, R.string.mod_restored_toast, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showReleaseDetailDialog(index: Int) {
        if (gameList.installState is InstallState.Installing) {
            gameList.restoreInstallProgress()
            renderInstallState(gameList.installState)
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
                append(getString(R.string.mod_mgr_detail_type_full))
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

        builder.show()
    }

    private fun executeSteps(steps: List<ModReleaseEntry>) {
        if (!AppSettings.isOnlineInstallEnabled(this)) return
        if (steps.isEmpty() || gameList.installState is InstallState.Installing) return
        ensureGameNotRunning(this) {
            InstallForegroundService.requestNotificationPermissionIfNeeded(this)
            rebindCurrentTarget()
            gameList.installOnlineModPlan(currentTarget, steps)
            renderInstallState(gameList.installState)
        }
    }

    override fun onInstallProgress(path: String) {
        renderInstallState(gameList.installState)
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
                if (gameList.isProgressMinimized) {
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
                if (state.resumable) {
                    Toast.makeText(this, R.string.online_download_paused_toast, Toast.LENGTH_LONG).show()
                }
                gameList.consumeInstallResult()
            }
            is InstallState.Failed -> {
                dismissProgressDialog()
                rebindCurrentTarget()
                updateUi()
                gameList.consumeInstallResult()
                AlertDialog.Builder(this)
                    .setTitle(R.string.error)
                    .setMessage(state.msgId)
                    .setPositiveButton(R.string.ok, null)
                    .show()
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
        val cancelBtn = dlg.findViewById<Button>(R.id.btn_cancel_install)
        if (cancelBtn != null) {
            cancelBtn.visibility = if (cancellable) View.VISIBLE else View.GONE
            cancelBtn.setText(if (resumable) R.string.notif_action_pause else R.string.cancel)
        }
    }

    private fun dismissProgressDialog() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        progressDialog?.dismiss()
        progressDialog = null
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
