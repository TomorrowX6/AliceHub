package io.github.kichikuou.xsystem4

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val HUB_INSTALL_ARCHIVE_REQ = 21
private const val HUB_INSTALL_DIR_REQ = 22

class GameHubActivity : ThemedActivity(), GameListObserver {
    companion object {
        const val EXTRA_GAME_ID = "EXTRA_GAME_ID"
        const val EXTRA_GAME_PATH = "EXTRA_GAME_PATH"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var refreshedSizeCdnUrl: String? = null

    private lateinit var gameList: GameList
    private var gameId: String = GameCatalog.ID_RANCE10
    private lateinit var descriptor: GameDescriptor
    private var matchingItems: List<Item> = emptyList()
    private var selectedPath: String? = null
    private var selectedItem: Item? = null

    private var progressDialog: Dialog? = null
    private var uninstallDialog: AlertDialog? = null
    private var pendingPostResumeAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_hub)
        actionBar?.setDisplayHomeAsUpEnabled(true)
        // Keeps the row ripples inside the cards' rounded corners.
        findViewById<View>(R.id.hub_install_card).clipToOutline = true
        findViewById<View>(R.id.hub_tools_card).clipToOutline = true

        gameId = intent.getStringExtra(EXTRA_GAME_ID)?.takeIf { it.isNotBlank() } ?: GameCatalog.ID_RANCE10
        selectedPath = intent.getStringExtra(EXTRA_GAME_PATH)?.takeIf { it.isNotBlank() }

        gameList = LauncherActivity.obtainGameList(this, refresh = false)
        rebindGameState()

        findViewById<View>(R.id.btn_hub_switch_instance).setOnClickListener { showSwitchInstanceDialog() }
        findViewById<View>(R.id.btn_hub_play).setOnClickListener { onPrimaryPlayClicked() }
        findViewById<View>(R.id.btn_hub_online_install).setOnClickListener { openOnlineInstall() }
        findViewById<View>(R.id.btn_hub_local_install).setOnClickListener { openLocalInstall() }
        findViewById<View>(R.id.btn_hub_mod_manager).setOnClickListener { openModManager() }
        findViewById<View>(R.id.btn_hub_save_manager).setOnClickListener { openSaveManager() }
        findViewById<View>(R.id.btn_hub_game_modifier).setOnClickListener { openGameModifier() }
        findViewById<View>(R.id.btn_hub_gamepad_settings).setOnClickListener { openGamepadSettings() }

        handleRestoreDialogIntent(intent)
        refreshUi()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent?.getStringExtra(EXTRA_GAME_ID)?.takeIf { it.isNotBlank() }?.let {
            gameId = it
        }
        intent?.getStringExtra(EXTRA_GAME_PATH)?.takeIf { it.isNotBlank() }?.let {
            selectedPath = it
        }
        if (::gameList.isInitialized) {
            rebindGameState()
            handleRestoreDialogIntent(intent)
            refreshUi()
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
        gameList = LauncherActivity.obtainGameList(this, refresh = false)
        gameList.observer = this
        rebindGameState()
        refreshOnlineSizeIfNeeded()
        refreshUi()
        renderInstallState(gameList.installState)
        matchingItems.firstOrNull { gameList.isUninstalling(it) }?.let { watchUninstall(it) }

        val postAction = pendingPostResumeAction
        pendingPostResumeAction = null
        postAction?.invoke()
        ErrorReportKit.checkAndShowPendingCrash(this)
    }

    override fun onPause() {
        if (::gameList.isInitialized && gameList.observer === this) {
            gameList.observer = null
        }
        dismissProgressDialog()
        super.onPause()
    }

    override fun onDestroy() {
        if (::gameList.isInitialized && gameList.observer === this) {
            gameList.observer = null
        }
        dismissProgressDialog()
        dismissUninstallDialog()
        scope.cancel()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.game_hub_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val cur = selectedItem
        val deleteMenu = menu.findItem(R.id.menu_delete)
        deleteMenu?.isVisible = (cur != null)
        deleteMenu?.subMenu?.clearHeader()
        menu.findItem(R.id.uninstall_game)?.isVisible = (cur != null)
        val uninstallModItem = menu.findItem(R.id.uninstall_mod)
        uninstallModItem?.isVisible =
            (cur != null && descriptor.supportsLocalMod && gameList.hasInstalledMod(cur))
        uninstallModItem?.setTitle(
            if (descriptor.isCnPatch) R.string.action_uninstall_cn_patch else R.string.action_uninstall_mod
        )
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                navigateBackToSelect()
                true
            }
            R.id.uninstall_game -> {
                selectedItem?.let { confirmUninstallGame(it) }
                true
            }
            R.id.uninstall_mod -> {
                selectedItem?.let { confirmUninstallMod(it) }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            HUB_INSTALL_DIR_REQ -> {
                pendingPostResumeAction = {
                    InstallForegroundService.requestNotificationPermissionIfNeeded(this)
                    gameList.installDirectory(
                        uri = uri,
                        expectedAinName = descriptor.expectedAinName,
                        targetGameId = descriptor.id
                    )
                    refreshUi()
                    renderInstallState(gameList.installState)
                }
            }
            HUB_INSTALL_ARCHIVE_REQ -> {
                pendingPostResumeAction = {
                    InstallForegroundService.requestNotificationPermissionIfNeeded(this)
                    gameList.installArchive(
                        uri = uri,
                        expectedAinName = descriptor.expectedAinName,
                        targetGameId = descriptor.id
                    )
                    refreshUi()
                    renderInstallState(gameList.installState)
                }
            }
        }
    }

    private fun navigateBackToSelect() {
        if (isTaskRoot) {
            startActivity(Intent(this, LauncherActivity::class.java))
        }
        finish()
    }

    private fun rebindGameState() {
        descriptor = GameCatalog.descriptorFor(this, gameId, gameList)
        matchingItems = GameCatalog.matchingItems(gameList, descriptor.id)
        val matchedByPath = selectedPath?.let { p -> matchingItems.firstOrNull { it.path.path == p } }
        selectedItem = matchedByPath ?: matchingItems.firstOrNull()
        selectedPath = selectedItem?.path?.path
    }

    private fun refreshUi() {
        title = descriptor.title
        invalidateOptionsMenu()

        findViewById<View>(R.id.hub_hero_card).background = UiKit.accentCard(
            this, descriptor.accentColor, R.dimen.ui_radius_card, clickable = false
        )

        val curItem = selectedItem
        UiKit.bindGameIcon(
            frame = findViewById(R.id.hub_icon_frame),
            iconView = findViewById(R.id.hub_icon),
            desc = descriptor,
            item = curItem,
            radiusDp = 20f
        )

        findViewById<TextView>(R.id.hub_title).text =
            if (descriptor.id == GameCatalog.ID_RANCE10 ||
                descriptor.id == GameCatalog.ID_DOHNA_DOHNA ||
                descriptor.id == GameCatalog.ID_EVENICLE ||
                descriptor.id == GameCatalog.ID_SENGOKU_RANCE ||
                descriptor.id == GameCatalog.ID_RANCE03 ||
                descriptor.id == GameCatalog.ID_RANCE08 ||
                descriptor.id == GameCatalog.ID_RANCE09 ||
                descriptor.id == GameCatalog.ID_ESCALAYER
            ) {
                descriptor.title
            } else {
                curItem?.let { GameCatalog.cleanItemTitle(it.name) } ?: descriptor.title
            }

        val runningRoot = GameCatalog.getRunningGameRoot(this)
        val isRunning = curItem != null && runningRoot == curItem.path.path
        val isInstallingHere = gameList.isInstallingForGame(descriptor.id)

        val statusView = findViewById<TextView>(R.id.hub_status)
        val statusText: String
        val statusTone: UiKit.Tone
        when {
            isInstallingHere -> {
                statusText = getString(R.string.game_status_installing)
                statusTone = UiKit.Tone.INFO
            }
            isRunning && curItem != null -> {
                statusText = getString(R.string.game_status_running)
                statusTone = UiKit.Tone.SUCCESS
            }
            curItem != null && curItem.error != null -> {
                statusText = getString(R.string.game_status_error)
                statusTone = UiKit.Tone.DANGER
            }
            curItem != null && gameList.hasInstalledMod(curItem) -> {
                val modInfo = gameList.getInstalledModInfo(curItem)
                val isCnPatch = descriptor.isCnPatch || modInfo?.kind == InstalledModInfo.KIND_CN_PATCH
                val ver = modInfo?.version?.takeIf { it.isNotBlank() } ?: if (isCnPatch) "V1.00" else "MOD"
                statusText = getString(
                    if (isCnPatch) R.string.game_status_installed_cn_patch else R.string.game_status_installed_mod,
                    ver
                )
                statusTone = UiKit.Tone.SUCCESS
            }
            curItem != null -> {
                statusText = getString(R.string.game_status_installed)
                statusTone = UiKit.Tone.SUCCESS
            }
            else -> {
                statusText = getString(R.string.game_status_not_installed)
                statusTone = UiKit.Tone.NEUTRAL
            }
        }
        statusView.text = statusText
        UiKit.styleChip(statusView, statusTone, dot = true)

        // With several copies of the game installed, the directory in use gets its own row
        // (next to the button that switches it) instead of being squeezed into the status chip.
        val hasInstanceChoice = matchingItems.size > 1
        findViewById<View>(R.id.hub_instance_row).visibility =
            if (hasInstanceChoice) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.hub_instance_dir).text =
            if (hasInstanceChoice) curItem?.path?.name.orEmpty() else ""

        val playBtn = findViewById<Button>(R.id.btn_hub_play)
        playBtn.setTextColor(Color.WHITE)
        if (curItem != null) {
            playBtn.text = UiKit.iconLabel(
                this, R.drawable.ic_play_arrow, Color.WHITE,
                getString(if (isRunning) R.string.hub_btn_resume_game else R.string.hub_btn_start_game)
            )
            playBtn.background = UiKit.accentButtonBackground(this, descriptor.accentColor, 18f)
        } else {
            playBtn.text = UiKit.iconLabel(
                this, R.drawable.ic_download, Color.WHITE,
                getString(R.string.hub_btn_install_first)
            )
            playBtn.background = UiKit.accentButtonBackground(
                this, UiKit.withAlpha(descriptor.accentColor, 215), 18f
            )
        }

        val onlineBtn = findViewById<Button>(R.id.btn_hub_online_install)
        val localBtn = findViewById<Button>(R.id.btn_hub_local_install)
        val modMgrBtn = findViewById<Button>(R.id.btn_hub_mod_manager)

        val onlineEnabled = descriptor.supportsOnlineBaseInstall && AppSettings.isOnlineInstallEnabled(this)
        onlineBtn.visibility = if (onlineEnabled) View.VISIBLE else View.GONE
        onlineBtn.text = onlineInstallLabel()
        val hasOverlaySupport = descriptor.supportsOnlineMod || descriptor.supportsLocalMod
        modMgrBtn.visibility = if (hasOverlaySupport) View.VISIBLE else View.GONE
        modMgrBtn.setText(if (descriptor.isCnPatch) R.string.action_install_cn_patch else R.string.mod_mgr_activity_title)

        val rowAccent = UiKit.accentOnSurface(this, descriptor.accentColor)
        UiKit.styleActionRow(onlineBtn, R.drawable.ic_cloud_download, rowAccent)
        UiKit.styleActionRow(localBtn, R.drawable.ic_folder_open, rowAccent)
        UiKit.styleActionRow(
            modMgrBtn,
            if (descriptor.isCnPatch) R.drawable.ic_language else R.drawable.ic_extension,
            rowAccent
        )
        UiKit.styleActionRow(findViewById(R.id.btn_hub_save_manager), R.drawable.ic_save, rowAccent)
        UiKit.styleActionRow(findViewById(R.id.btn_hub_game_modifier), R.drawable.ic_tune, rowAccent)
        UiKit.styleActionRow(findViewById(R.id.btn_hub_gamepad_settings), R.drawable.ic_gamepad, rowAccent)
    }

    private fun onlineBaseSizeText(): String {
        val cdnUrl = descriptor.onlineBaseCdnUrl ?: return ""
        val bytes = AppSettings.getCachedBaseGameSize(this, cdnUrl)
        return OnlineInstaller.formatSize(bytes)
    }

    private fun onlineInstallLabel(): String {
        val sizeStr = onlineBaseSizeText()
        return if (sizeStr.isNotEmpty()) {
            getString(R.string.action_online_install_with_size, sizeStr)
        } else {
            getString(R.string.action_online_install)
        }
    }

    private fun refreshOnlineSizeIfNeeded() {
        if (!descriptor.supportsOnlineBaseInstall || !AppSettings.isOnlineInstallEnabled(this)) return
        val cdnUrl = descriptor.onlineBaseCdnUrl ?: return
        if (refreshedSizeCdnUrl == cdnUrl) return
        refreshedSizeCdnUrl = cdnUrl
        scope.launch {
            val fetched = try {
                withContext(Dispatchers.IO) {
                    OnlineInstaller.fetchManifestTotalBytes(cdnUrl)
                }
            } catch (_: Throwable) {
                -1L
            }
            if (fetched > 0L) {
                val prev = AppSettings.getCachedBaseGameSize(this@GameHubActivity, cdnUrl)
                AppSettings.setCachedBaseGameSize(this@GameHubActivity, cdnUrl, fetched)
                if (prev != fetched) {
                    refreshUi()
                }
            }
        }
    }

    private fun showSwitchInstanceDialog() {
        if (matchingItems.size <= 1) return
        val labels = Array(matchingItems.size) { idx ->
            val it = matchingItems[idx]
            val ver = if (gameList.hasInstalledMod(it)) {
                gameList.getInstalledModInfo(it)?.version?.takeIf { v -> v.isNotBlank() } ?: "MOD"
            } else {
                null
            }
            if (ver != null) "${it.path.name} · $ver" else it.path.name
        }
        val curIdx = matchingItems.indexOfFirst { it.path.path == selectedItem?.path?.path }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.hub_select_instance_title)
            .setSingleChoiceItems(labels, curIdx) { dlg, which ->
                if (which in matchingItems.indices) {
                    selectedItem = matchingItems[which]
                    selectedPath = selectedItem?.path?.path
                    refreshUi()
                }
                dlg.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun onPrimaryPlayClicked() {
        if (gameList.installState is InstallState.Installing) {
            gameList.restoreInstallProgress()
            renderInstallState(gameList.installState)
            return
        }
        val curItem = selectedItem
        if (curItem != null) {
            GameCatalog.launchGameItem(this, curItem)
            return
        }
        if (descriptor.supportsOnlineBaseInstall && AppSettings.isOnlineInstallEnabled(this)) {
            val opts = arrayOf(
                onlineInstallLabel(),
                getString(R.string.action_local_install)
            )
            AlertDialog.Builder(this)
                .setTitle(R.string.hub_btn_install_first)
                .setItems(opts) { _, which ->
                    when (which) {
                        0 -> confirmOnlineBaseGameInstall()
                        1 -> openLocalInstallBaseDialog()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            openLocalInstallBaseDialog()
        }
    }

    private fun openOnlineInstall() {
        if (gameList.installState is InstallState.Installing) {
            gameList.restoreInstallProgress()
            renderInstallState(gameList.installState)
            return
        }
        confirmOnlineBaseGameInstall()
    }

    private fun confirmOnlineBaseGameInstall() {
        if (gameList.installState is InstallState.Installing) {
            gameList.restoreInstallProgress()
            renderInstallState(gameList.installState)
            return
        }
        if (!AppSettings.isOnlineInstallEnabled(this)) return
        val cdnUrl = descriptor.onlineBaseCdnUrl ?: return
        val sizeStr = onlineBaseSizeText()
        val msg = if (sizeStr.isNotEmpty()) {
            getString(R.string.online_base_confirm_msg_size, descriptor.title, sizeStr)
        } else {
            getString(R.string.online_base_confirm_msg, descriptor.title)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.online_base_confirm_title)
            .setMessage(msg)
            .setPositiveButton(R.string.ok) { _, _ ->
                InstallForegroundService.requestNotificationPermissionIfNeeded(this)
                gameList.installOnlineBaseGame(cdnBaseUrl = cdnUrl, targetGameId = descriptor.id)
                refreshUi()
                renderInstallState(gameList.installState)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openLocalInstall() {
        if (gameList.installState is InstallState.Installing) {
            gameList.restoreInstallProgress()
            renderInstallState(gameList.installState)
            return
        }
        openLocalInstallBaseDialog()
    }

    private fun openLocalInstallBaseDialog() {
        if (gameList.installState is InstallState.Installing) return
        GameCatalog.showLocalGameImportDialog(
            activity = this,
            titleRes = R.string.game_local_dialog_title,
            archiveReqCode = HUB_INSTALL_ARCHIVE_REQ,
            dirReqCode = HUB_INSTALL_DIR_REQ
        )
    }

    private fun openModManager() {
        val target = selectedItem
        if (target == null) {
            AlertDialog.Builder(this)
                .setTitle(if (descriptor.isCnPatch) R.string.action_install_cn_patch else R.string.mod_mgr_activity_title)
                .setMessage(R.string.mod_no_game_installed)
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }
        val intent = Intent(this, ModManagerActivity::class.java)
            .putExtra(ModManagerActivity.EXTRA_GAME_PATH, target.path.path)
            .putExtra(ModManagerActivity.EXTRA_GAME_ID, descriptor.id)
        startActivity(intent)
    }

    private fun openSaveManager() {
        val targetDirName = selectedItem?.path?.name ?: descriptor.defaultDirName
        val intent = Intent(this, SaveManagerActivity::class.java)
            .putExtra(SaveManagerActivity.EXTRA_DIR_NAME, targetDirName)
            .putExtra(SaveManagerActivity.EXTRA_GAME_ID, descriptor.id)
        startActivity(intent)
    }

    private fun openGameModifier() {
        val targetDirName = selectedItem?.path?.name ?: descriptor.defaultDirName
        val intent = Intent(this, GameModifierActivity::class.java)
            .putExtra(GameModifierActivity.EXTRA_DIR_NAME, targetDirName)
            .putExtra(GameModifierActivity.EXTRA_GAME_ID, descriptor.id)
        startActivity(intent)
    }

    private fun openGamepadSettings() {
        val targetDirName = selectedItem?.path?.name ?: descriptor.defaultDirName
        val displayTitle = findViewById<TextView>(R.id.hub_title).text?.toString()?.takeIf { it.isNotBlank() }
            ?: descriptor.title
        val intent = Intent(this, GamepadSettingsActivity::class.java)
            .putExtra(GamepadSettingsActivity.EXTRA_PROFILE_KEY, targetDirName)
            .putExtra(GamepadSettingsActivity.EXTRA_GAME_ID, descriptor.id)
            .putExtra(GamepadSettingsActivity.EXTRA_GAME_TITLE, displayTitle)
        startActivity(intent)
    }

    private fun confirmUninstallGame(item: Item) {
        if (gameList.isUninstalling(item)) {
            watchUninstall(item)
            return
        }
        if (gameList.isInstallingInto(item) || gameList.isInstallingForGame(descriptor.id)) {
            gameList.restoreInstallProgress()
            renderInstallState(gameList.installState)
            return
        }
        ensureGameNotRunning(this) {
            AlertDialog.Builder(this)
                .setTitle(R.string.action_delete_base_game)
                .setMessage(getString(R.string.delete_base_game_message, GameCatalog.cleanItemTitle(item.name)))
                .setPositiveButton(R.string.ok) { _, _ ->
                    // Deleting several GB of files takes a while: do it off the main thread
                    // behind a modal progress dialog (which also blocks a second tap).
                    gameList.uninstallAsync(item) { _ -> }
                    watchUninstall(item)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun confirmUninstallMod(item: Item) {
        if (gameList.installState is InstallState.Installing) {
            gameList.restoreInstallProgress()
            renderInstallState(gameList.installState)
            return
        }
        val titleRes = if (descriptor.isCnPatch) R.string.action_uninstall_cn_patch else R.string.action_uninstall_mod
        if (!gameList.canRestoreVanilla(item)) {
            AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setMessage(
                    if (descriptor.isCnPatch) R.string.uninstall_cn_patch_no_backup_msg
                    else R.string.uninstall_mod_no_backup_msg
                )
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setMessage(
                if (descriptor.isCnPatch) R.string.cn_patch_mgr_restore_confirm
                else R.string.mod_mgr_restore_confirm
            )
            .setPositiveButton(R.string.ok) { _, _ ->
                ensureGameNotRunning(this) {
                    if (gameList.restoreMod(item)) {
                        rebindGameState()
                        refreshUi()
                        Toast.makeText(
                            this,
                            if (descriptor.isCnPatch) R.string.cn_patch_restored_toast else R.string.mod_restored_toast,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onInstallProgress(path: String) {
        renderInstallState(gameList.installState)
    }

    override fun onInstallSuccess() {
        rebindGameState()
        refreshUi()
        renderInstallState(gameList.installState)
    }

    override fun onInstallCancelled() {
        rebindGameState()
        refreshUi()
        renderInstallState(gameList.installState)
    }

    override fun onInstallFailure(msgId: Int) {
        rebindGameState()
        refreshUi()
        renderInstallState(gameList.installState)
    }

    private fun renderInstallState(state: InstallState) {
        when (state) {
            InstallState.Idle -> dismissProgressDialog()
            is InstallState.Installing -> {
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
                rebindGameState()
                refreshUi()
                Toast.makeText(this, R.string.install_done_toast, Toast.LENGTH_SHORT).show()
                gameList.consumeInstallResult()
            }
            is InstallState.Cancelled -> {
                dismissProgressDialog()
                rebindGameState()
                refreshUi()
                if (state.paused) {
                    Toast.makeText(this, R.string.online_download_paused_toast, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, R.string.install_cancelled_toast, Toast.LENGTH_SHORT).show()
                }
                gameList.consumeInstallResult()
            }
            is InstallState.Failed -> {
                dismissProgressDialog()
                rebindGameState()
                refreshUi()
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
                    refreshUi()
                    InstallForegroundService.requestNotificationPermissionIfNeeded(
                        this@GameHubActivity,
                        forceIfRationale = true
                    )
                    Toast.makeText(
                        this@GameHubActivity,
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

    /**
     * Shows the progress dialog until [item]'s deletion (running in GameList's scope) ends.
     * Polling keeps this working when the activity is recreated mid-deletion.
     */
    private fun watchUninstall(item: Item) {
        if (!gameList.isUninstalling(item) || uninstallDialog?.isShowing == true) return
        showUninstallProgress()
        scope.launch {
            while (gameList.isUninstalling(item)) delay(200)
            dismissUninstallDialog()
            if (item.path.exists()) {
                Toast.makeText(this@GameHubActivity, R.string.delete_base_game_failed, Toast.LENGTH_LONG).show()
            }
            rebindGameState()
            if (descriptor.id.startsWith(GameCatalog.PREFIX_CUSTOM) && matchingItems.isEmpty()) {
                navigateBackToSelect()
            } else {
                refreshUi()
            }
        }
    }

    private fun showUninstallProgress() {
        if (uninstallDialog?.isShowing == true) return
        val pad = UiKit.dpInt(this, 24)
        val row = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(ProgressBar(this@GameHubActivity).apply { isIndeterminate = true })
            addView(TextView(this@GameHubActivity).apply {
                setText(R.string.deleting_base_game)
                setTextColor(getColor(R.color.ui_text_primary))
                textSize = 15f
                setPadding(pad, 0, 0, 0)
            })
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        uninstallDialog = AlertDialog.Builder(this)
            .setView(row)
            .setCancelable(false)
            .show()
    }

    private fun dismissUninstallDialog() {
        uninstallDialog?.dismiss()
        uninstallDialog = null
        if (progressDialog == null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun dismissProgressDialog() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        progressDialog?.dismiss()
        progressDialog = null
    }
}
