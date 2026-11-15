package io.github.kichikuou.xsystem4

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

private const val HUB_INSTALL_ARCHIVE_REQ = 21
private const val HUB_INSTALL_DIR_REQ = 22
private const val HUB_MOD_ARCHIVE_REQ = 23
private const val HUB_MOD_DIR_REQ = 24
private const val HUB_MOD_AUTH_RETRY_REQ = 25

class GameHubActivity : Activity(), GameListObserver {
    companion object {
        const val EXTRA_GAME_ID = "EXTRA_GAME_ID"
        const val EXTRA_GAME_PATH = "EXTRA_GAME_PATH"
    }

    private lateinit var gameList: GameList
    private var gameId: String = GameCatalog.ID_RANCE10
    private lateinit var descriptor: GameDescriptor
    private var matchingItems: List<Item> = emptyList()
    private var selectedPath: String? = null
    private var selectedItem: Item? = null

    private var progressDialog: Dialog? = null
    private var pendingModTarget: Item? = null
    private var pendingHubRetry: PendingModAuthRetry? = null
    private var pendingPostResumeAction: (() -> Unit)? = null

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_hub)
        actionBar?.setDisplayHomeAsUpEnabled(true)

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
        findViewById<View>(R.id.btn_hub_uninstall).setOnClickListener {
            selectedItem?.let { confirmUninstallGame(it) }
        }
        findViewById<View>(R.id.btn_hub_back_select).setOnClickListener { navigateBackToSelect() }

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
        refreshUi()
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
        if (::gameList.isInitialized && gameList.observer === this) {
            gameList.observer = null
        }
        dismissProgressDialog()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.game_hub_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.uninstall_game)?.isVisible = (selectedItem != null)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                navigateBackToSelect()
                true
            }
            R.id.refresh -> {
                gameList = LauncherActivity.obtainGameList(this, refresh = true)
                gameList.observer = this
                rebindGameState()
                refreshUi()
                renderInstallState(gameList.installState)
                true
            }
            R.id.uninstall_game -> {
                selectedItem?.let { confirmUninstallGame(it) }
                true
            }
            R.id.open_github -> {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GameCatalog.GITHUB_URL)))
                true
            }
            R.id.licenses -> {
                startActivity(Intent(this, LicensesMenuActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == HUB_MOD_AUTH_RETRY_REQ) {
            val retry = pendingHubRetry
            pendingHubRetry = null
            if (resultCode == RESULT_OK && retry != null) {
                val freshCookie = data?.getStringExtra(ModPortalActivity.EXTRA_COOKIE)
                    ?: CookieManager.getInstance().getCookie(OnlineInstaller.MOD_PORTAL_BASE_URL)
                    ?: ""
                pendingPostResumeAction = {
                    InstallForegroundService.requestNotificationPermissionIfNeeded(this)
                    if (retry.steps != null) {
                        gameList.installOnlineModPlan(retry.targetItem, retry.steps, freshCookie, fromModManager = false)
                    } else if (retry.manualReq != null) {
                        gameList.installOnlineMod(
                            retry.targetItem,
                            retry.manualReq.copy(cookie = freshCookie),
                            retry.manualReleaseMeta,
                            fromModManager = false
                        )
                    }
                    refreshUi()
                    renderInstallState(gameList.installState)
                }
            }
            return
        }

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
            HUB_MOD_DIR_REQ -> {
                val target = pendingModTarget ?: selectedItem ?: return
                pendingPostResumeAction = {
                    ensureGameNotRunning(this) {
                        InstallForegroundService.requestNotificationPermissionIfNeeded(this)
                        gameList.installModDirectory(target, uri)
                        refreshUi()
                        renderInstallState(gameList.installState)
                    }
                }
            }
            HUB_MOD_ARCHIVE_REQ -> {
                val target = pendingModTarget ?: selectedItem ?: return
                pendingPostResumeAction = {
                    ensureGameNotRunning(this) {
                        InstallForegroundService.requestNotificationPermissionIfNeeded(this)
                        gameList.installModArchive(target, uri)
                        refreshUi()
                        renderInstallState(gameList.installState)
                    }
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

        val heroCard = findViewById<View>(R.id.hub_hero_card)
        heroCard.background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(0x18888888)
            setStroke(dp(1), descriptor.accentBorderColor)
        }

        val sectionBg1 = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(0x12888888)
            setStroke(dp(1), 0x26FFFFFF)
        }
        val sectionBg2 = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(0x12888888)
            setStroke(dp(1), 0x26FFFFFF)
        }
        findViewById<View>(R.id.hub_install_card).background = sectionBg1
        findViewById<View>(R.id.hub_tools_card).background = sectionBg2

        val iconFrame = findViewById<FrameLayout>(R.id.hub_icon_frame)
        iconFrame.background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(descriptor.iconFrameColor)
            setStroke(dp(1), 0x33FFFFFF)
        }

        val iconView = findViewById<ImageView>(R.id.hub_icon)
        val curItem = selectedItem
        if (descriptor.id == GameCatalog.ID_RANCE10 || descriptor.id == GameCatalog.ID_DOHNA_DOHNA) {
            iconView.setImageResource(descriptor.iconRes)
        } else {
            val bmp = curItem?.getIconBitmap(dp(64))
            if (bmp != null) {
                iconView.setImageBitmap(bmp)
            } else {
                iconView.setImageResource(descriptor.iconRes)
            }
        }

        findViewById<TextView>(R.id.hub_title).text = curItem?.name ?: descriptor.title
        findViewById<TextView>(R.id.hub_subtitle).text = descriptor.subtitle

        val runningRoot = GameCatalog.getRunningGameRoot(this)
        val isRunning = curItem != null && runningRoot == curItem.path.path
        val isInstallingHere = gameList.isInstallingForGame(descriptor.id)

        val statusView = findViewById<TextView>(R.id.hub_status)
        val statusText: String
        val statusBg: Int
        val statusColor: Int
        when {
            isInstallingHere -> {
                statusText = getString(R.string.game_status_installing)
                statusBg = 0x332196F3
                statusColor = 0xFF64B5F6.toInt()
            }
            isRunning && curItem != null -> {
                statusText = getString(
                    R.string.hub_status_dir_installed,
                    getString(R.string.game_status_running),
                    curItem.path.name
                )
                statusBg = 0x334CAF50
                statusColor = 0xFF81C784.toInt()
            }
            curItem != null && curItem.error != null -> {
                statusText = getString(R.string.game_status_error)
                statusBg = 0x33F44336
                statusColor = 0xFFE57373.toInt()
            }
            curItem != null -> {
                statusText = getString(
                    R.string.hub_status_dir_installed,
                    getString(R.string.game_status_installed),
                    curItem.path.name
                )
                statusBg = 0x334CAF50
                statusColor = 0xFF66BB6A.toInt()
            }
            else -> {
                statusText = getString(R.string.hub_status_not_installed_hint)
                statusBg = 0x24888888
                statusColor = Color.GRAY
            }
        }
        statusView.text = statusText
        statusView.setTextColor(statusColor)
        statusView.background = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(statusBg)
        }

        findViewById<View>(R.id.btn_hub_switch_instance).visibility =
            if (matchingItems.size > 1) View.VISIBLE else View.GONE

        val playBtn = findViewById<Button>(R.id.btn_hub_play)
        if (curItem != null) {
            playBtn.text = getString(if (isRunning) R.string.hub_btn_resume_game else R.string.hub_btn_start_game)
            playBtn.setTextColor(Color.WHITE)
            playBtn.background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(descriptor.accentColor)
            }
        } else {
            playBtn.text = getString(R.string.hub_btn_install_first)
            playBtn.setTextColor(Color.WHITE)
            playBtn.background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(descriptor.accentColor)
                alpha = 215
            }
        }

        val onlineBtn = findViewById<Button>(R.id.btn_hub_online_install)
        val localBtn = findViewById<Button>(R.id.btn_hub_local_install)
        val modMgrBtn = findViewById<Button>(R.id.btn_hub_mod_manager)
        val lpLocal = localBtn.layoutParams as? LinearLayout.LayoutParams

        if (descriptor.supportsOnlineBaseInstall) {
            onlineBtn.visibility = View.VISIBLE
            lpLocal?.marginStart = dp(4)
            localBtn.layoutParams = lpLocal
        } else {
            onlineBtn.visibility = View.GONE
            lpLocal?.marginStart = 0
            localBtn.layoutParams = lpLocal
        }

        modMgrBtn.visibility = if (descriptor.supportsOnlineMod) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.hub_tools_hint).text = descriptor.toolsHint
        findViewById<View>(R.id.btn_hub_uninstall).visibility =
            if (curItem != null) View.VISIBLE else View.GONE
    }

    private fun showSwitchInstanceDialog() {
        if (matchingItems.size <= 1) return
        val labels = Array(matchingItems.size) { idx ->
            val it = matchingItems[idx]
            "${it.name} · ${it.path.name}"
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
        if (descriptor.supportsOnlineBaseInstall) {
            val opts = arrayOf(
                getString(R.string.action_online_install),
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
        if (!descriptor.supportsOnlineMod) {
            confirmOnlineBaseGameInstall()
            return
        }
        val options = arrayOf(
            getString(R.string.online_sub_game),
            getString(R.string.online_sub_mod)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.action_online_install)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> confirmOnlineBaseGameInstall()
                    1 -> openModManager()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmOnlineBaseGameInstall() {
        if (gameList.installState is InstallState.Installing) {
            gameList.restoreInstallProgress()
            renderInstallState(gameList.installState)
            return
        }
        val lines = arrayOf(
            getString(R.string.online_line_edgeone),
            getString(R.string.online_line_cloudflare)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.online_select_line_title)
            .setItems(lines) { _, which ->
                val chosen = if (which == 0) BaseGameCdnLine.EDGEONE else BaseGameCdnLine.CLOUDFLARE
                InstallForegroundService.requestNotificationPermissionIfNeeded(this)
                gameList.installOnlineBaseGame(chosen, targetGameId = descriptor.id)
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
        val curItem = selectedItem
        if (curItem == null || !descriptor.supportsLocalMod) {
            openLocalInstallBaseDialog()
            return
        }
        val modSubLabel = if (descriptor.isRance10) {
            getString(R.string.local_sub_mod)
        } else {
            getString(R.string.hub_local_patch_sub)
        }
        val options = arrayOf(
            getString(R.string.local_sub_game),
            modSubLabel
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.action_local_install)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openLocalInstallBaseDialog()
                    1 -> showLocalModInstallDialog(curItem)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openLocalInstallBaseDialog() {
        if (gameList.installState is InstallState.Installing) return
        val options = arrayOf(
            getString(R.string.game_opt_zip),
            getString(R.string.game_opt_dir)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.game_local_dialog_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivityForResult(
                            Intent.createChooser(i, getString(R.string.choose_a_file)),
                            HUB_INSTALL_ARCHIVE_REQ
                        )
                    }
                    1 -> {
                        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, HUB_INSTALL_DIR_REQ)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun Item.cleanGameTitle(): String {
        val idx = name.indexOf(" [MOD")
        return if (idx > 0) name.substring(0, idx) else name
    }

    private fun showLocalModInstallDialog(target: Item) {
        val options = mutableListOf(
            getString(R.string.mod_opt_zip),
            getString(R.string.mod_opt_dir)
        )
        if (gameList.hasInstalledMod(target)) {
            options.add(getString(R.string.mod_opt_restore_vanilla))
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.mod_local_dialog_title, target.cleanGameTitle()))
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> {
                        pendingModTarget = target
                        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivityForResult(
                            Intent.createChooser(i, getString(R.string.mod_choose_zip)),
                            HUB_MOD_ARCHIVE_REQ
                        )
                    }
                    1 -> {
                        pendingModTarget = target
                        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, HUB_MOD_DIR_REQ)
                    }
                    2 -> {
                        ensureGameNotRunning(this) {
                            if (gameList.restoreMod(target)) {
                                rebindGameState()
                                refreshUi()
                                Toast.makeText(this, R.string.mod_restored_toast, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openModManager() {
        val target = selectedItem
        if (target == null) {
            AlertDialog.Builder(this)
                .setTitle(R.string.mod_mgr_activity_title)
                .setMessage(R.string.mod_no_game_installed)
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }
        val intent = Intent(this, ModManagerActivity::class.java)
            .putExtra(ModManagerActivity.EXTRA_GAME_PATH, target.path.path)
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

    private fun confirmUninstallGame(item: Item) {
        if (gameList.isInstallingInto(item)) {
            gameList.restoreInstallProgress()
            renderInstallState(gameList.installState)
            return
        }
        ensureGameNotRunning(this) {
            AlertDialog.Builder(this)
                .setTitle(R.string.uninstall_dialog_title)
                .setMessage(getString(R.string.uninstall_dialog_message, item.name))
                .setPositiveButton(R.string.ok) { _, _ ->
                    gameList.uninstall(item)
                    rebindGameState()
                    if (descriptor.id.startsWith(GameCatalog.PREFIX_CUSTOM) && matchingItems.isEmpty()) {
                        navigateBackToSelect()
                    } else {
                        refreshUi()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
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
                if (state.resumable) {
                    Toast.makeText(this, R.string.online_download_paused_toast, Toast.LENGTH_LONG).show()
                }
                gameList.consumeInstallResult()
            }
            is InstallState.Failed -> {
                dismissProgressDialog()
                rebindGameState()
                refreshUi()
                val retry = if (state.msgId == R.string.online_mod_auth_expired) {
                    gameList.pendingModAuthRetry
                } else {
                    null
                }
                gameList.clearPendingModAuthRetry()
                gameList.consumeInstallResult()
                if (retry != null) {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.online_mod_auth_dialog_title)
                        .setMessage(R.string.online_mod_auth_expired_retry_msg)
                        .setPositiveButton(R.string.online_mod_portal_btn_auth) { _, _ ->
                            pendingHubRetry = retry
                            val intent = Intent(this, ModPortalActivity::class.java)
                                .putExtra(ModPortalActivity.EXTRA_AUTH_ONLY, true)
                                .putExtra(ModPortalActivity.EXTRA_TARGET_LABEL, retry.label)
                            startActivityForResult(intent, HUB_MOD_AUTH_RETRY_REQ)
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.error)
                        .setMessage(state.msgId)
                        .setPositiveButton(R.string.ok, null)
                        .show()
                }
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
}
