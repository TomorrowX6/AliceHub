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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
        private const val STATE_HUB_GAME_ID = "STATE_HUB_GAME_ID"
        private const val STATE_HUB_SELECTED_PATH = "STATE_HUB_SELECTED_PATH"
        private const val PREFS_NAME = "alicehub_settings"
        private const val KEY_SELECTED_INSTANCE_PREFIX = "hub_selected_instance_path_"

        fun shouldShowInstanceRow(descriptor: GameDescriptor, matchingCount: Int): Boolean {
            return matchingCount > 1 || (descriptor.isRance10 && matchingCount >= 1)
        }

        fun persistSelectedInstancePath(context: android.content.Context, targetGameId: String, path: String?) {
            val editor = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            val key = KEY_SELECTED_INSTANCE_PREFIX + targetGameId
            if (path.isNullOrBlank()) {
                editor.remove(key)
            } else {
                editor.putString(key, path)
            }
            editor.apply()
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var refreshedSizeCdnUrl: String? = null
    private var sizeRefreshJob: Job? = null
    private var sizeRefreshUrl: String? = null

    private lateinit var gameList: GameList
    private var gameId: String = GameCatalog.ID_RANCE10
    private lateinit var descriptor: GameDescriptor
    private var matchingItems: List<Item> = emptyList()
    private var selectedPath: String? = null
    private var selectedItem: Item? = null

    private var progressDialog: Dialog? = null
    private var uncensoredProgressDialog: Dialog? = null
    private var restoreModDialog: Dialog? = null
    private var uninstallDialog: AlertDialog? = null
    private val trackedDialogs = mutableListOf<Dialog>()
    private var lastProgressRenderMs: Long = 0L
    private var lastUncensoredRenderMs: Long = 0L
    private var pendingPostResumeAction: (() -> Unit)? = null
    private var skipInitialResumeRefresh: Boolean = false
    private var pendingCreatedInstanceName: String? = null
    private val uncensoredListener: (UncensoredInstallState) -> Unit = { st ->
        renderUncensoredInstallState(st)
    }

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

    private fun isInstallingCurrentHubGame(): Boolean {
        if (!::gameList.isInitialized || !::descriptor.isInitialized) return false
        val cur = selectedItem
        return gameList.isInstallingForGame(descriptor.id) ||
            (cur != null && gameList.isInstallingInto(cur))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.clearLegacyDownloadRoutePref(this)
        setContentView(R.layout.activity_game_hub)
        actionBar?.setDisplayHomeAsUpEnabled(true)
        // Keeps the row ripples inside the cards' rounded corners.
        findViewById<View>(R.id.hub_install_card).clipToOutline = true
        findViewById<View>(R.id.hub_tools_card).clipToOutline = true

        gameId = savedInstanceState?.getString(STATE_HUB_GAME_ID)?.takeIf { it.isNotBlank() }
            ?: intent.getStringExtra(EXTRA_GAME_ID)?.takeIf { it.isNotBlank() }
            ?: GameCatalog.ID_RANCE10
        selectedPath = savedInstanceState?.getString(STATE_HUB_SELECTED_PATH)?.takeIf { it.isNotBlank() }
            ?: intent.getStringExtra(EXTRA_GAME_PATH)?.takeIf { it.isNotBlank() }

        gameList = LauncherActivity.obtainGameList(this, refresh = false)
        rebindGameState()

        findViewById<View>(R.id.btn_hub_switch_instance).setOnClickListener { showSwitchInstanceDialog() }
        findViewById<View>(R.id.btn_hub_switch_boot_entry).setOnClickListener { showBootEntryDialog() }
        findViewById<View>(R.id.btn_hub_play).setOnClickListener { onPrimaryPlayClicked() }
        findViewById<View>(R.id.btn_hub_online_install).setOnClickListener { openOnlineInstall() }
        findViewById<View>(R.id.btn_hub_local_install).setOnClickListener { openLocalInstall() }
        findViewById<View>(R.id.btn_hub_mod_manager).setOnClickListener { openModManager() }
        findViewById<View>(R.id.btn_hub_uncensored_patch).setOnClickListener { openUncensoredPatchDialog() }
        findViewById<View>(R.id.btn_hub_save_manager).setOnClickListener { openSaveManager() }
        findViewById<View>(R.id.btn_hub_game_modifier).setOnClickListener { openGameModifier() }
        findViewById<View>(R.id.btn_hub_gamepad_settings).setOnClickListener { openGamepadSettings() }

        handleRestoreDialogIntent(intent)
        refreshUi()
        skipInitialResumeRefresh = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_HUB_GAME_ID, gameId)
        selectedPath?.takeIf { it.isNotBlank() }?.let {
            outState.putString(STATE_HUB_SELECTED_PATH, it)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null && (
                intent.hasExtra(EXTRA_GAME_ID) ||
                    intent.hasExtra(EXTRA_GAME_PATH) ||
                    intent.hasExtra(InstallForegroundService.EXTRA_RESTORE_INSTALL_DIALOG)
                )
        ) {
            setIntent(intent)
        }
        val prevGameId = gameId
        intent?.getStringExtra(EXTRA_GAME_ID)?.takeIf { it.isNotBlank() }?.let {
            gameId = it
        }
        val incomingPath = intent?.getStringExtra(EXTRA_GAME_PATH)?.takeIf { it.isNotBlank() }
        if (incomingPath != null) {
            selectedPath = incomingPath
        } else if (gameId != prevGameId) {
            selectedPath = null
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
            if (isInstallingCurrentHubGame()) {
                gameList.restoreInstallProgress()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val latestList = LauncherActivity.obtainGameList(this, refresh = false)
        val listChanged = latestList !== gameList
        gameList = latestList
        gameList.observer = this
        val skipRefresh = skipInitialResumeRefresh && !listChanged
        skipInitialResumeRefresh = false
        if (!skipRefresh) {
            rebindGameState()
        }
        refreshOnlineSizeIfNeeded()
        if (gameList.isRestoringMod) {
            showRestoreModProgress()
        } else {
            dismissRestoreModProgress()
        }
        if (!skipRefresh) {
            refreshUi()
        }
        renderInstallState(gameList.installState)
        UncensoredPatchManager.addListener(uncensoredListener)
        matchingItems.firstOrNull { gameList.isUninstalling(it) }?.let { watchUninstall(it) }

        val postAction = pendingPostResumeAction
        pendingPostResumeAction = null
        postAction?.invoke()
        ErrorReportKit.checkAndShowPendingCrash(this)
    }

    override fun onPause() {
        UncensoredPatchManager.removeListener(uncensoredListener)
        if (::gameList.isInitialized && gameList.observer === this) {
            gameList.observer = null
        }
        dismissProgressDialog()
        dismissUncensoredProgressDialog()
        dismissRestoreModProgress()
        super.onPause()
    }

    override fun onDestroy() {
        UncensoredPatchManager.removeListener(uncensoredListener)
        if (::gameList.isInitialized && gameList.observer === this) {
            gameList.observer = null
        }
        dismissProgressDialog()
        dismissUncensoredProgressDialog()
        dismissRestoreModProgress()
        dismissUninstallDialog()
        dismissTrackedDialogs()
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

    private fun loadPersistedInstancePath(targetGameId: String): String? {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_SELECTED_INSTANCE_PREFIX + targetGameId, null)
            ?.takeIf { it.isNotBlank() }
    }

    private fun persistSelectedInstancePath(targetGameId: String, path: String?) {
        Companion.persistSelectedInstancePath(this, targetGameId, path)
    }

    private fun rebindGameState() {
        descriptor = GameCatalog.descriptorFor(this, gameId, gameList)
        matchingItems = GameCatalog.matchingItems(gameList, descriptor.id)
        val effectivePath = selectedPath?.takeIf { it.isNotBlank() }
            ?: loadPersistedInstancePath(descriptor.id)
        val matchedByPath = effectivePath?.let { p ->
            val resolved = AppStorage.resolveGamePath(this, p)
            matchingItems.firstOrNull { it.path.path == resolved }
        }
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
            if (GameCatalog.isBuiltInGameId(descriptor.id)) {
                descriptor.title
            } else {
                curItem?.let { GameCatalog.cleanItemTitle(it.name) } ?: descriptor.title
            }

        val runningRoot = GameCatalog.getRunningGameRoot(this)
        val runningTarget = GameCatalog.getRunningLaunchTarget(this)
        val selectedTarget = curItem?.let { System4BootEntry.resolveLaunchTarget(this, it.path).path }
        val isRunning = curItem != null &&
            runningRoot == curItem.path.path &&
            (GameCatalog.isSystem3xGameDir(curItem.path) || runningTarget == null || runningTarget == selectedTarget)
        val isInstallingHere = isInstallingCurrentHubGame()

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
            curItem != null -> {
                statusText = getString(R.string.game_status_installed)
                statusTone = UiKit.Tone.SUCCESS
            }
            else -> {
                statusText = getString(R.string.game_status_not_installed)
                statusTone = UiKit.Tone.NEUTRAL
            }
        }
        val uncensoredInstalled = curItem != null && UncensoredPatchManager.detectInstalled(curItem.path)
        statusView.text = statusText
        UiKit.styleChip(statusView, statusTone, dot = true)

        val translationView = findViewById<TextView>(R.id.hub_translation_badge)
        val modInfo = if (curItem != null) gameList.getInstalledModInfo(curItem) else null
        val hasChinese = GameCatalog.hasChineseTranslation(descriptor, curItem, modInfo)
        translationView.text = getString(
            if (hasChinese) R.string.game_translation_cn else R.string.game_translation_raw
        )
        UiKit.styleChip(
            translationView,
            if (hasChinese) UiKit.Tone.PRIMARY else UiKit.Tone.WARNING,
            dot = false
        )

        // Show the directory switch row when multiple copies exist, and keep it permanently
        // visible for installed Rance 10 copies so players can switch or create directories
        // for vanilla and multi-version MOD setups.
        val showInstanceRow = shouldShowInstanceRow(descriptor, matchingItems.size)
        findViewById<View>(R.id.hub_instance_row).visibility =
            if (showInstanceRow) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.hub_instance_dir).text =
            if (showInstanceRow) curItem?.path?.name.orEmpty() else ""

        val bootEntries = if (curItem != null && curItem.error == null && !GameCatalog.isSystem3xGameDir(curItem.path)) {
            System4BootEntry.listEntries(curItem.path)
        } else {
            emptyList()
        }
        val bootEntryRow = findViewById<View>(R.id.hub_boot_entry_row)
        val bootEntryValue = findViewById<TextView>(R.id.hub_boot_entry_value)
        if (curItem != null && bootEntries.isNotEmpty()) {
            val selected = System4BootEntry.getSelectedEntry(this, curItem.path)
            val effective = selected.takeIf { it.isNotEmpty() && it in bootEntries }
            bootEntryRow.visibility = View.VISIBLE
            bootEntryValue.text = effective ?: getString(R.string.hub_boot_entry_default)
        } else {
            bootEntryRow.visibility = View.GONE
            bootEntryValue.text = ""
        }

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
        val uncBtn = findViewById<Button>(R.id.btn_hub_uncensored_patch)

        val onlineEnabled = descriptor.supportsOnlineBaseInstall && AppSettings.isOnlineInstallEnabled(this)
        onlineBtn.visibility = if (onlineEnabled) View.VISIBLE else View.GONE
        onlineBtn.text = onlineInstallLabel()
        val hasOverlaySupport = descriptor.supportsOnlineMod || descriptor.supportsLocalMod
        modMgrBtn.visibility = if (hasOverlaySupport) View.VISIBLE else View.GONE
        modMgrBtn.setText(if (descriptor.isCnPatch) R.string.action_install_cn_patch else R.string.mod_mgr_activity_title)
        val showUncensored = descriptor.supportsUncensoredPatch && (uncensoredInstalled || onlineEnabled)
        uncBtn.visibility = if (showUncensored) View.VISIBLE else View.GONE
        uncBtn.setText(if (uncensoredInstalled) R.string.uncensored_patch_row_installed else R.string.uncensored_patch_title)

        val rowAccent = UiKit.accentOnSurface(this, descriptor.accentColor)
        UiKit.styleActionRow(onlineBtn, R.drawable.ic_cloud_download, rowAccent)
        UiKit.styleActionRow(localBtn, R.drawable.ic_folder_open, rowAccent)
        UiKit.styleActionRow(
            modMgrBtn,
            if (descriptor.isCnPatch) R.drawable.ic_language else R.drawable.ic_extension,
            rowAccent
        )
        UiKit.styleActionRow(uncBtn, R.drawable.ic_extension, rowAccent)
        UiKit.styleActionRow(findViewById(R.id.btn_hub_save_manager), R.drawable.ic_save, rowAccent)
        UiKit.styleActionRow(findViewById(R.id.btn_hub_game_modifier), R.drawable.ic_tune, rowAccent)
        UiKit.styleActionRow(findViewById(R.id.btn_hub_gamepad_settings), R.drawable.ic_gamepad, rowAccent)
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
        val fetchUrl = AppSettings.canonicalCdnUrl(cdnUrl)
        if (refreshedSizeCdnUrl == fetchUrl) return
        if (sizeRefreshJob?.isActive == true && sizeRefreshUrl == fetchUrl) return
        sizeRefreshJob?.cancel()
        sizeRefreshUrl = fetchUrl
        sizeRefreshJob = scope.launch {
            val fetched = try {
                withContext(Dispatchers.IO) {
                    OnlineInstaller.fetchManifestTotalBytes(fetchUrl)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Throwable) {
                -1L
            }
            if (isFinishing || isDestroyed) return@launch
            if (fetched > 0L) {
                refreshedSizeCdnUrl = fetchUrl
                val prev = AppSettings.getCachedBaseGameSize(this@GameHubActivity, cdnUrl)
                AppSettings.setCachedBaseGameSize(this@GameHubActivity, cdnUrl, fetched)
                if (prev != fetched) {
                    refreshUi()
                }
            }
        }
    }

    private fun formatInstanceLabel(it: Item): String {
        val fallbackTag = if (descriptor.isCnPatch) "CN" else "MOD"
        val ver = if (gameList.hasInstalledMod(it)) {
            val info = gameList.getInstalledModInfo(it)
            val v = info?.version?.takeIf { s -> s.isNotBlank() } ?: fallbackTag
            val g = info?.group?.takeIf { s -> s.isNotBlank() }
            if (!descriptor.isCnPatch && g != null && !g.equals(v, ignoreCase = true)) "$g · $v" else v
        } else if (descriptor.isRance10) {
            getString(R.string.hub_instance_vanilla_tag)
        } else {
            null
        }
        return if (ver != null) "${it.path.name} · $ver" else it.path.name
    }

    private fun showSwitchInstanceDialog() {
        if (matchingItems.isEmpty() || (!descriptor.isRance10 && matchingItems.size <= 1)) return
        val labels = Array(matchingItems.size) { idx -> formatInstanceLabel(matchingItems[idx]) }
        val curIdx = matchingItems.indexOfFirst { it.path.path == selectedItem?.path?.path }.coerceAtLeast(0)
        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.hub_select_instance_title)
            .setSingleChoiceItems(labels, curIdx) { dlg, which ->
                if (which in matchingItems.indices) {
                    selectedItem = matchingItems[which]
                    selectedPath = selectedItem?.path?.path
                    persistSelectedInstancePath(descriptor.id, selectedPath)
                    refreshUi()
                }
                dlg.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
        if (descriptor.isRance10) {
            builder.setPositiveButton(R.string.hub_create_instance) { _, _ ->
                showCreateRance10InstanceDialog()
            }
        }
        trackDialog(builder.show())
    }

    private fun showBootEntryDialog() {
        val item = selectedItem ?: return
        if (item.error != null || GameCatalog.isSystem3xGameDir(item.path)) return
        val entries = System4BootEntry.listEntries(item.path)
        if (entries.isEmpty()) return
        val labels = Array(entries.size + 1) { idx ->
            if (idx == 0) getString(R.string.hub_boot_entry_default) else entries[idx - 1]
        }
        val selected = System4BootEntry.getSelectedEntry(this, item.path)
        val curIdx = entries.indexOf(selected).let { if (it >= 0) it + 1 else 0 }
        trackDialog(
            AlertDialog.Builder(this)
                .setTitle(R.string.hub_boot_entry)
                .setSingleChoiceItems(labels, curIdx) { dlg, which ->
                    val chosen = if (which > 0 && which <= entries.size) entries[which - 1] else ""
                    System4BootEntry.setSelectedEntry(this, item.path, chosen)
                    refreshUi()
                    dlg.dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        )
    }

    private fun showCreateRance10InstanceDialog() {
        if (!descriptor.isRance10 || matchingItems.isEmpty()) return
        if (gameList.isRestoringMod || UncensoredPatchManager.isAnyDownloading()) {
            showOtherInstallBusyToast()
            return
        }
        if (gameList.installState is InstallState.Installing) {
            if (isInstallingCurrentHubGame()) {
                gameList.restoreInstallProgress()
                renderInstallState(gameList.installState)
            } else {
                showOtherInstallBusyToast()
            }
            return
        }

        val candidates = matchingItems
        val defaultSourceIdx = candidates.indexOfFirst { !gameList.hasInstalledMod(it) }
            .takeIf { it >= 0 }
            ?: candidates.indexOfFirst { it.path.path == selectedItem?.path?.path }.coerceAtLeast(0)
        var chosenSource = candidates[defaultSourceIdx]
        val parentDir = chosenSource.path.parentFile ?: chosenSource.path
        val usedNames = gameList.allExistingDirectoryNames()
        val suggestedName = Rance10InstanceManager.suggestNextDirName(parentDir, descriptor.defaultDirName, usedNames)

        val padH = UiKit.dpInt(this, 20)
        val padTop = UiKit.dpInt(this, 12)
        val gap = UiKit.dpInt(this, 10)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padTop, padH, UiKit.dpInt(this@GameHubActivity, 6))
        }

        val nameLabel = TextView(this).apply {
            setText(R.string.hub_create_instance_name_label)
            setTextColor(getColor(R.color.ui_text_secondary))
            textSize = 13f
        }
        val nameInput = EditText(this).apply {
            setText(suggestedName)
            setHint(R.string.hub_create_instance_name_hint)
            isSingleLine = true
            setSelection(text.length)
        }
        val errorView = TextView(this).apply {
            setTextColor(getColor(R.color.tone_danger_fg))
            textSize = 12f
            visibility = View.GONE
        }
        container.addView(nameLabel)
        container.addView(nameInput)
        container.addView(errorView)

        val cleanVanillaCheck = CheckBox(this).apply {
            setText(R.string.hub_create_instance_clean_vanilla)
            setTextColor(getColor(R.color.ui_text_primary))
            textSize = 14f
            isChecked = true
            setPadding(0, gap, 0, 0)
        }
        val descView = TextView(this).apply {
            setTextColor(getColor(R.color.ui_text_secondary))
            textSize = 13f
            setPadding(0, gap, 0, 0)
        }

        var sizeJob: kotlinx.coroutines.Job? = null
        fun updateSourcePreview() {
            val src = chosenSource
            val hasMod = gameList.hasInstalledMod(src)
            val canRestore = hasMod && Item.canRestoreVanillaDir(src.path)
            cleanVanillaCheck.visibility = if (canRestore) View.VISIBLE else View.GONE
            val clean = canRestore && cleanVanillaCheck.isChecked
            sizeJob?.cancel()
            sizeJob = scope.launch {
                val planBytes = withContext(Dispatchers.IO) {
                    Rance10InstanceManager.planCopy(src.path, clean).totalBytes
                }
                if (isFinishing || isDestroyed) return@launch
                val sizeStr = OnlineInstaller.formatSize(planBytes).ifEmpty { "0 B" }
                descView.text = when {
                    !hasMod || clean -> getString(R.string.hub_create_instance_desc_vanilla, src.path.name, sizeStr)
                    !canRestore -> getString(R.string.hub_create_instance_desc_repack, sizeStr)
                    else -> getString(R.string.hub_create_instance_desc_copy, src.path.name, sizeStr)
                }
            }
        }

        if (candidates.size > 1) {
            val srcLabel = TextView(this).apply {
                setText(R.string.hub_create_instance_source_label)
                setTextColor(getColor(R.color.ui_text_secondary))
                textSize = 13f
                setPadding(0, gap, 0, 0)
            }
            val srcSpinner = Spinner(this).apply {
                adapter = ArrayAdapter(
                    this@GameHubActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    candidates.map { formatInstanceLabel(it) }
                )
                setSelection(defaultSourceIdx)
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                        if (pos in candidates.indices) {
                            chosenSource = candidates[pos]
                            cleanVanillaCheck.isChecked = true
                            updateSourcePreview()
                        }
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            }
            container.addView(srcLabel)
            container.addView(srcSpinner)
        }

        cleanVanillaCheck.setOnCheckedChangeListener { _, _ -> updateSourcePreview() }
        container.addView(cleanVanillaCheck)
        container.addView(descView)
        updateSourcePreview()

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.hub_create_instance_title)
            .setView(container)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnDismissListener { sizeJob?.cancel() }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                val src = chosenSource
                val targetParent = gameList.cloneInstallRoot(src)
                val result = Rance10InstanceManager.validateDirName(
                    targetParent,
                    nameInput.text?.toString().orEmpty(),
                    gameList.allExistingDirectoryNames()
                )
                if (!result.isValid) {
                    val errRes = when (result.error) {
                        Rance10InstanceManager.NameValidationError.EMPTY -> R.string.hub_create_instance_err_empty
                        Rance10InstanceManager.NameValidationError.INVALID_CHARS -> R.string.hub_create_instance_err_invalid
                        Rance10InstanceManager.NameValidationError.TOO_LONG -> R.string.hub_create_instance_err_too_long
                        Rance10InstanceManager.NameValidationError.ALREADY_EXISTS -> R.string.hub_create_instance_err_exists
                        null -> R.string.hub_create_instance_err_invalid
                    }
                    errorView.setText(errRes)
                    errorView.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                val clean = cleanVanillaCheck.visibility == View.VISIBLE && cleanVanillaCheck.isChecked
                dialog.dismiss()
                ensureGameNotRunning(this) {
                    InstallForegroundService.requestNotificationPermissionIfNeeded(this)
                    pendingCreatedInstanceName = result.sanitizedName
                    val started = gameList.cloneRance10Directory(src, result.sanitizedName, cleanVanilla = clean)
                    if (!started) {
                        pendingCreatedInstanceName = null
                        showOtherInstallBusyToast()
                    } else {
                        refreshUi()
                        renderInstallState(gameList.installState)
                    }
                }
            }
        }
        trackDialog(dialog)
        dialog.show()
    }

    private fun onPrimaryPlayClicked() {
        if (gameList.isRestoringMod) return
        if (UncensoredPatchManager.isAnyDownloading()) {
            if (selectedItem?.path?.path?.let { UncensoredPatchManager.isDownloadingFor(it) } == true) {
                renderUncensoredInstallState(UncensoredPatchManager.state)
            } else {
                showOtherInstallBusyToast()
            }
            return
        }
        if (isInstallingCurrentHubGame()) {
            gameList.restoreInstallProgress()
            renderInstallState(gameList.installState)
            return
        }
        val curItem = selectedItem
        if (curItem != null) {
            GameCatalog.launchGameItem(this, curItem)
            return
        }
        if (gameList.installState is InstallState.Installing) {
            showOtherInstallBusyToast()
            return
        }
        if (descriptor.supportsOnlineBaseInstall && AppSettings.isOnlineInstallEnabled(this)) {
            val opts = arrayOf(
                onlineInstallLabel(),
                getString(R.string.action_local_install)
            )
            trackDialog(
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
            )
        } else {
            openLocalInstallBaseDialog()
        }
    }

    private fun openOnlineInstall() {
        if (gameList.isRestoringMod) return
        if (UncensoredPatchManager.isAnyDownloading()) {
            showOtherInstallBusyToast()
            return
        }
        if (gameList.installState is InstallState.Installing) {
            if (isInstallingCurrentHubGame()) {
                gameList.restoreInstallProgress()
                renderInstallState(gameList.installState)
            } else {
                showOtherInstallBusyToast()
            }
            return
        }
        confirmOnlineBaseGameInstall()
    }

    private fun confirmOnlineBaseGameInstall() {
        if (gameList.isRestoringMod) return
        if (gameList.installState is InstallState.Installing) {
            if (isInstallingCurrentHubGame()) {
                gameList.restoreInstallProgress()
                renderInstallState(gameList.installState)
            } else {
                showOtherInstallBusyToast()
            }
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
        trackDialog(
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
        )
    }

    private fun openLocalInstall() {
        if (gameList.isRestoringMod) return
        if (UncensoredPatchManager.isAnyDownloading()) {
            showOtherInstallBusyToast()
            return
        }
        if (gameList.installState is InstallState.Installing) {
            if (isInstallingCurrentHubGame()) {
                gameList.restoreInstallProgress()
                renderInstallState(gameList.installState)
            } else {
                showOtherInstallBusyToast()
            }
            return
        }
        openLocalInstallBaseDialog()
    }

    private fun openLocalInstallBaseDialog() {
        if (gameList.installState is InstallState.Installing || gameList.isRestoringMod) return
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
            trackDialog(
                AlertDialog.Builder(this)
                    .setTitle(if (descriptor.isCnPatch) R.string.action_install_cn_patch else R.string.mod_mgr_activity_title)
                    .setMessage(R.string.mod_no_game_installed)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            )
            return
        }
        val intent = Intent(this, ModManagerActivity::class.java)
            .putExtra(ModManagerActivity.EXTRA_GAME_PATH, target.path.path)
            .putExtra(ModManagerActivity.EXTRA_GAME_ID, descriptor.id)
        startActivity(intent)
    }

    private fun openUncensoredPatchDialog() {
        val target = selectedItem
        if (target == null) {
            trackDialog(
                AlertDialog.Builder(this)
                    .setTitle(R.string.uncensored_patch_title)
                    .setMessage(R.string.mod_no_game_installed)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            )
            return
        }
        if (UncensoredPatchManager.isDownloadingFor(target.path.path)) {
            renderUncensoredInstallState(UncensoredPatchManager.state)
            return
        }
        if (UncensoredPatchManager.isAnyDownloading() || gameList.installState is InstallState.Installing || gameList.isRestoringMod) {
            showOtherInstallBusyToast()
            return
        }
        val isInstalled = UncensoredPatchManager.detectInstalled(target.path)
        val installedInfo = InstalledUncensoredInfo.read(target.path)
        val cdnUrl = descriptor.onlineBaseCdnUrl
        val onlineEnabled = cdnUrl != null && AppSettings.isOnlineInstallEnabled(this)

        if (!onlineEnabled || cdnUrl == null) {
            if (!isInstalled) return
            val detail = installedInfo?.let {
                val sz = OnlineInstaller.formatSize(it.size)
                if (it.cgCount > 0 && sz.isNotEmpty()) "${it.cgCount} CG · $sz" else it.fileName
            } ?: target.path.name
            trackDialog(
                AlertDialog.Builder(this)
                    .setTitle(R.string.uncensored_patch_title)
                    .setMessage(getString(R.string.uncensored_patch_info_installed, detail))
                    .setPositiveButton(R.string.uncensored_patch_uninstall_btn) { _, _ ->
                        confirmUninstallUncensoredPatch(target)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            )
            return
        }

        val loadingDlg = trackDialog(
            AlertDialog.Builder(this)
                .setTitle(R.string.uncensored_patch_title)
                .setMessage(R.string.uncensored_patch_fetching)
                .setCancelable(true)
                .show()
        )
        val fetchCdnUrl = AppSettings.canonicalCdnUrl(cdnUrl)
        scope.launch {
            val manifest = try {
                withContext(Dispatchers.IO) {
                    UncensoredPatchManager.fetchManifest(fetchCdnUrl)
                }
            } catch (_: Throwable) {
                null
            }
            if (isFinishing || isDestroyed) return@launch
            safeDismiss(loadingDlg)

            if (manifest == null) {
                val builder = AlertDialog.Builder(this@GameHubActivity)
                    .setTitle(R.string.uncensored_patch_title)
                    .setMessage(R.string.uncensored_patch_fetch_failed)
                    .setNegativeButton(R.string.cancel, null)
                if (isInstalled) {
                    builder.setPositiveButton(R.string.uncensored_patch_uninstall_btn) { _, _ ->
                        confirmUninstallUncensoredPatch(target)
                    }
                }
                trackDialog(builder.show())
                return@launch
            }

            val sizeText = OnlineInstaller.formatSize(manifest.size)
            val msg = if (isInstalled) {
                val detail = installedInfo?.let {
                    val sz = OnlineInstaller.formatSize(it.size)
                    if (it.cgCount > 0 && sz.isNotEmpty()) "${it.cgCount} CG · $sz" else it.fileName
                } ?: sizeText
                getString(R.string.uncensored_patch_info_installed, detail)
            } else {
                getString(
                    R.string.uncensored_patch_info_not_installed,
                    manifest.summary,
                    sizeText,
                    manifest.cgCount
                )
            }
            val builder = AlertDialog.Builder(this@GameHubActivity)
                .setTitle(manifest.title.ifEmpty { getString(R.string.uncensored_patch_title) })
                .setMessage(msg)
                .setPositiveButton(
                    if (isInstalled) R.string.uncensored_patch_reinstall_btn
                    else R.string.uncensored_patch_install_btn
                ) { _, _ ->
                    ensureGameNotRunning(this@GameHubActivity) {
                        val resolvedManifest = manifest.copy(
                            downloadUrl = AppSettings.canonicalCdnUrl(manifest.downloadUrl)
                        )
                        val maxConcurrency = AppSettings.getDownloadConcurrency(this@GameHubActivity)
                        if (!UncensoredPatchManager.startInstall(target.path, descriptor.id, resolvedManifest, maxConcurrency)) {
                            showOtherInstallBusyToast()
                        }
                    }
                }
                .setNegativeButton(R.string.cancel, null)
            if (isInstalled) {
                builder.setNeutralButton(R.string.uncensored_patch_uninstall_btn) { _, _ ->
                    confirmUninstallUncensoredPatch(target)
                }
            }
            trackDialog(builder.show())
        }
    }

    private fun confirmUninstallUncensoredPatch(target: Item) {
        trackDialog(
            AlertDialog.Builder(this)
                .setTitle(R.string.uncensored_patch_uninstall_btn)
                .setMessage(R.string.uncensored_patch_uninstall_confirm)
                .setPositiveButton(R.string.ok) { _, _ ->
                    ensureGameNotRunning(this) {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                UncensoredPatchManager.uninstall(target.path)
                            }
                            if (!isFinishing && !isDestroyed) {
                                refreshUi()
                                Toast.makeText(
                                    this@GameHubActivity,
                                    if (ok) R.string.uncensored_patch_uninstalled_toast else R.string.error,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        )
    }

    private fun renderUncensoredInstallState(st: UncensoredInstallState) {
        if (isFinishing || isDestroyed) return
        val curPath = selectedItem?.path?.path
        when (st) {
            UncensoredInstallState.Idle -> dismissUncensoredProgressDialog()
            is UncensoredInstallState.Downloading -> {
                if (curPath == null || curPath != st.gameDirPath) {
                    dismissUncensoredProgressDialog()
                    return
                }
                val now = android.os.SystemClock.uptimeMillis()
                if (uncensoredProgressDialog != null && now - lastUncensoredRenderMs < 100L && st.doneBytes < st.totalBytes) {
                    return
                }
                lastUncensoredRenderMs = now
                showUncensoredProgressDialog(st)
            }
            is UncensoredInstallState.Succeeded -> {
                if (curPath == null || curPath != st.gameDirPath) return
                dismissUncensoredProgressDialog()
                UncensoredPatchManager.consumeTerminalState()
                refreshUi()
                Toast.makeText(this, R.string.uncensored_patch_installed_toast, Toast.LENGTH_SHORT).show()
            }
            is UncensoredInstallState.Cancelled -> {
                if (curPath == null || curPath != st.gameDirPath) return
                dismissUncensoredProgressDialog()
                UncensoredPatchManager.consumeTerminalState()
                refreshUi()
                Toast.makeText(this, R.string.online_download_paused_toast, Toast.LENGTH_SHORT).show()
            }
            is UncensoredInstallState.Failed -> {
                if (curPath == null || curPath != st.gameDirPath) return
                dismissUncensoredProgressDialog()
                UncensoredPatchManager.consumeTerminalState()
                refreshUi()
                trackDialog(
                    AlertDialog.Builder(this)
                        .setTitle(R.string.error)
                        .setMessage(st.message.ifBlank { getString(R.string.online_mod_download_error) })
                        .setPositiveButton(R.string.ok, null)
                        .show()
                )
            }
        }
    }

    private fun showUncensoredProgressDialog(st: UncensoredInstallState.Downloading) {
        if (isFinishing || isDestroyed) return
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (uncensoredProgressDialog == null) {
            uncensoredProgressDialog = Dialog(this).apply {
                setTitle(R.string.uncensored_patch_title)
                setCancelable(false)
                setContentView(R.layout.progress_dialog)
                findViewById<View>(R.id.btn_bg_install)?.visibility = View.GONE
                findViewById<View>(R.id.btn_pause_install)?.visibility = View.GONE
                findViewById<Button>(R.id.btn_cancel_install)?.apply {
                    visibility = View.VISIBLE
                    setText(R.string.cancel)
                    setOnClickListener { UncensoredPatchManager.cancelActiveInstall() }
                }
                show()
            }
        }
        val dlg = uncensoredProgressDialog ?: return
        val pct = if (st.totalBytes > 0L) ((st.doneBytes * 100L) / st.totalBytes).toInt().coerceIn(0, 100) else 0
        val doneMb = st.doneBytes / (1024L * 1024L)
        val totalMb = (st.totalBytes / (1024L * 1024L)).coerceAtLeast(1L)
        val speed = OnlineInstaller.formatSpeed(st.speedBps)
        dlg.findViewById<TextView>(R.id.text)?.text = getString(
            R.string.uncensored_patch_downloading,
            pct,
            speed,
            doneMb,
            totalMb,
            st.fileName
        )
        val bar = dlg.findViewById<ProgressBar>(R.id.progress_bar)
        if (bar != null) {
            val pm = st.permille
            if (pm != null) {
                bar.visibility = View.VISIBLE
                bar.progress = pm
            } else {
                bar.visibility = View.GONE
            }
        }
    }

    private fun safeDismiss(dlg: Dialog?) {
        if (dlg != null && !isDestroyed && dlg.isShowing) {
            try {
                dlg.dismiss()
            } catch (_: Throwable) {
            }
        }
    }

    private fun dismissUncensoredProgressDialog() {
        val dlg = uncensoredProgressDialog
        uncensoredProgressDialog = null
        safeDismiss(dlg)
        if (progressDialog == null && uninstallDialog == null && restoreModDialog == null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
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
        if (gameList.isRestoringMod) return
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
            trackDialog(
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
            )
        }
    }

    private fun confirmUninstallMod(item: Item) {
        if (gameList.isRestoringMod) return
        if (gameList.installState is InstallState.Installing) {
            if (isInstallingCurrentHubGame()) {
                gameList.restoreInstallProgress()
                renderInstallState(gameList.installState)
            } else {
                showOtherInstallBusyToast()
            }
            return
        }
        val titleRes = if (descriptor.isCnPatch) R.string.action_uninstall_cn_patch else R.string.action_uninstall_mod
        if (!gameList.canRestoreVanilla(item)) {
            trackDialog(
                AlertDialog.Builder(this)
                    .setTitle(titleRes)
                    .setMessage(
                        if (descriptor.isCnPatch) R.string.uninstall_cn_patch_no_backup_msg
                        else R.string.uninstall_mod_no_backup_msg
                    )
                    .setPositiveButton(R.string.ok, null)
                    .show()
            )
            return
        }
        trackDialog(
            AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setMessage(
                    if (descriptor.isCnPatch) R.string.cn_patch_mgr_restore_confirm
                    else R.string.mod_mgr_restore_confirm
                )
                .setPositiveButton(R.string.ok) { _, _ ->
                    ensureGameNotRunning(this) {
                        startUninstallModAsync(item)
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        )
    }

    private fun startUninstallModAsync(item: Item) {
        if (gameList.installState is InstallState.Installing || gameList.isRestoringMod) return
        val restoredToastRes = if (descriptor.isCnPatch) R.string.cn_patch_restored_toast else R.string.mod_restored_toast
        val appCtx = AppSettings.wrapThemeContext(applicationContext)
        val started = gameList.launchRestoreMod(item) { ok ->
            if (!isFinishing && !isDestroyed) {
                dismissRestoreModProgress()
                rebindGameState()
                refreshUi()
            }
            if (ok) {
                Toast.makeText(appCtx, restoredToastRes, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(appCtx, R.string.error, Toast.LENGTH_SHORT).show()
            }
        }
        if (started) {
            showRestoreModProgress()
            refreshUi()
        }
    }

    private fun showRestoreModProgress() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (restoreModDialog == null) {
            val titleRes = if (descriptor.isCnPatch) R.string.action_uninstall_cn_patch else R.string.action_uninstall_mod
            restoreModDialog = Dialog(this).apply {
                setTitle(titleRes)
                setCancelable(false)
                setContentView(R.layout.progress_dialog)
                findViewById<View>(R.id.action_bar)?.visibility = View.GONE
                findViewById<ProgressBar>(R.id.progress_bar)?.visibility = View.GONE
                findViewById<TextView>(R.id.text)?.text = GamepadBridge.tr(
                    this@GameHubActivity,
                    "正在还原原版文件…",
                    "バニラファイルを復元中…",
                    "Restoring vanilla files…"
                )
                show()
            }
            scope.launch {
                while (gameList.isRestoringMod) {
                    delay(150)
                }
                if (!isFinishing && !isDestroyed && restoreModDialog != null) {
                    dismissRestoreModProgress()
                    rebindGameState()
                    refreshUi()
                }
            }
        }
    }

    private fun dismissRestoreModProgress() {
        val dlg = restoreModDialog
        restoreModDialog = null
        safeDismiss(dlg)
        if (progressDialog == null && uninstallDialog == null && uncensoredProgressDialog == null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onInstallProgress(path: String) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastProgressRenderMs < 80L) return
        lastProgressRenderMs = now
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
        if (isFinishing || isDestroyed) return
        when (state) {
            InstallState.Idle -> dismissProgressDialog()
            is InstallState.Installing -> {
                if (!isInstallingCurrentHubGame() || gameList.isProgressMinimized) {
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
                val createdName = pendingCreatedInstanceName
                pendingCreatedInstanceName = null
                val installed = gameList.lastInstalledItem
                if (installed != null && GameCatalog.classifyItem(installed) == descriptor.id) {
                    selectedPath = installed.path.path
                    persistSelectedInstancePath(descriptor.id, selectedPath)
                }
                rebindGameState()
                refreshUi()
                if (!createdName.isNullOrBlank()) {
                    Toast.makeText(
                        this,
                        getString(R.string.hub_create_instance_done_toast, installed?.path?.name ?: createdName),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(this, R.string.install_done_toast, Toast.LENGTH_SHORT).show()
                }
                gameList.consumeInstallResult()
            }
            is InstallState.Cancelled -> {
                dismissProgressDialog()
                pendingCreatedInstanceName = null
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
                pendingCreatedInstanceName = null
                rebindGameState()
                refreshUi()
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
        val dlg = uninstallDialog
        uninstallDialog = null
        safeDismiss(dlg)
        if (progressDialog == null && restoreModDialog == null && uncensoredProgressDialog == null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun dismissProgressDialog() {
        val dlg = progressDialog
        progressDialog = null
        safeDismiss(dlg)
        if (uninstallDialog == null && restoreModDialog == null && uncensoredProgressDialog == null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
