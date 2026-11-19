package io.github.kichikuou.xsystem4

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

private const val LAUNCHER_INSTALL_ARCHIVE_REQ = 31
private const val LAUNCHER_INSTALL_DIR_REQ = 32
private const val STATE_SEARCH_VISIBLE = "STATE_SEARCH_VISIBLE"
private const val STATE_SEARCH_QUERY = "STATE_SEARCH_QUERY"

private class GameSelectAdapter(
    private val activity: Activity,
    private val gameList: GameList
) : BaseAdapter() {
    private val context: Context = activity
    private var allEntries: List<GameSelectEntry> = GameCatalog.buildSelectEntries(activity, gameList)
    private var entries: List<GameSelectEntry> = allEntries
    private var filterQuery: String = ""

    fun reload() {
        allEntries = GameCatalog.buildSelectEntries(context, gameList)
        applyFilter()
    }

    fun setFilter(query: String) {
        val trimmed = query.trim()
        if (trimmed == filterQuery) return
        filterQuery = trimmed
        applyFilter()
    }

    fun hasQuery(): Boolean = filterQuery.isNotEmpty()

    private fun applyFilter() {
        entries = if (filterQuery.isEmpty()) {
            allEntries
        } else {
            allEntries.filter { matchesQuery(it, filterQuery) }
        }
        notifyDataSetChanged()
    }

    private fun matchesQuery(entry: GameSelectEntry, q: String): Boolean {
        val desc = entry.descriptor
        if (desc.title.contains(q, ignoreCase = true) ||
            desc.defaultDirName.contains(q, ignoreCase = true) ||
            desc.id.removePrefix(GameCatalog.PREFIX_CUSTOM).contains(q, ignoreCase = true) ||
            (desc.expectedAinName?.contains(q, ignoreCase = true) == true) ||
            (desc.id == GameCatalog.ID_RANCE06 && GameCatalog.isRance06Alias(q)) ||
            (desc.id == GameCatalog.ID_DAIAKUJI && GameCatalog.isDaiakujiAlias(q)) ||
            (desc.id == GameCatalog.ID_GALZOO && GameCatalog.isGalzooAlias(q)) ||
            (desc.id == GameCatalog.ID_IXSEAL && GameCatalog.isIxsealAlias(q)) ||
            (desc.id == GameCatalog.ID_TSUMASHIBORI && GameCatalog.isTsumashiboriAlias(q))
        ) {
            return true
        }
        return entry.items.any {
            it.name.contains(q, ignoreCase = true) || it.path.name.contains(q, ignoreCase = true)
        }
    }

    override fun getCount(): Int = entries.size
    override fun getItem(position: Int): GameSelectEntry = entries[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val entry = entries[position]
        val desc = entry.descriptor
        val primary = entry.primaryItem
        val view = convertView
            ?: LayoutInflater.from(context).inflate(R.layout.launcher_item, parent, false)

        val card = view.findViewById<View>(R.id.card_container)
        card.background = UiKit.accentCard(
            context, desc.accentColor, R.dimen.ui_radius_card, clickable = true
        )

        UiKit.bindGameIcon(
            frame = view.findViewById(R.id.icon_frame),
            iconView = view.findViewById(R.id.icon),
            desc = desc,
            item = primary,
            radiusDp = 16f
        )

        view.findViewById<TextView>(R.id.title).text = desc.title

        val runningRoot = GameCatalog.getRunningGameRoot(context)
        val isRunning = runningRoot != null && entry.items.any { it.path.path == runningRoot }
        val isInstalling = gameList.isInstallingForGame(desc.id)

        val badge = view.findViewById<TextView>(R.id.status_badge)
        val badgeTone: UiKit.Tone
        val badgeLabel: String

        when {
            isInstalling -> {
                badgeLabel = context.getString(R.string.game_status_installing)
                badgeTone = UiKit.Tone.INFO
            }
            isRunning -> {
                badgeLabel = context.getString(R.string.game_status_running)
                badgeTone = UiKit.Tone.SUCCESS
            }
            primary != null && primary.error != null -> {
                badgeLabel = context.getString(R.string.game_status_error)
                badgeTone = UiKit.Tone.DANGER
            }
            primary != null && gameList.hasInstalledMod(primary) -> {
                val modInfo = gameList.getInstalledModInfo(primary)
                val isCnPatch = desc.isCnPatch || modInfo?.kind == InstalledModInfo.KIND_CN_PATCH
                val ver = modInfo?.version?.takeIf { it.isNotBlank() } ?: if (isCnPatch) "V1.00" else "MOD"
                val statusRes = if (isCnPatch) {
                    R.string.game_status_installed_cn_patch
                } else {
                    R.string.game_status_installed_mod
                }
                badgeLabel = context.getString(statusRes, ver)
                badgeTone = UiKit.Tone.SUCCESS
            }
            primary != null -> {
                badgeLabel = context.getString(R.string.game_status_installed)
                badgeTone = UiKit.Tone.SUCCESS
            }
            else -> {
                badgeLabel = context.getString(R.string.game_status_not_installed)
                badgeTone = UiKit.Tone.NEUTRAL
            }
        }

        badge.text = badgeLabel
        UiKit.styleChip(badge, badgeTone, dot = true)

        return view
    }
}

class LauncherActivity : ThemedActivity(), GameListObserver {
    companion object {
        private var savedGameList: GameList? = null

        fun obtainGameList(activity: Activity, refresh: Boolean = false): GameList {
            val existing = savedGameList
            if (existing == null || (refresh && existing.installState is InstallState.Idle && !existing.isRestoringMod)) {
                existing?.close()
                val created = GameList(activity)
                savedGameList = created
                return created
            }
            return existing
        }
    }

    private lateinit var gameList: GameList
    private lateinit var adapter: GameSelectAdapter
    private lateinit var listView: ListView
    private lateinit var searchBarContainer: View
    private lateinit var searchInput: EditText
    private lateinit var searchEmptyView: TextView
    private var progressDialog: Dialog? = null
    private var pendingPostResumeAction: (() -> Unit)? = null
    private var searchBackCallback: Any? = null
    private var lastProgressRenderMs: Long = 0L
    private var lastRenderedInstallingGameId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.applyPlatformNightMode(this)
        setContentView(R.layout.launcher)
        title = getString(R.string.select_game_title)

        gameList = obtainGameList(this, refresh = false)
        adapter = GameSelectAdapter(this, gameList)

        val banner = findViewById<TextView>(R.id.active_install_banner)
        banner.setOnClickListener { onActiveInstallBannerClick() }

        searchBarContainer = findViewById(R.id.search_bar_container)
        searchInput = findViewById(R.id.search_input)
        searchEmptyView = findViewById(R.id.search_empty_view)
        listView = findViewById(R.id.list)
        UiKit.bindEmptyState(searchEmptyView, R.drawable.ic_search, getString(R.string.search_empty))

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.setFilter(s?.toString().orEmpty())
                updateSearchEmptyState()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideSoftKeyboard()
                searchInput.clearFocus()
                true
            } else {
                false
            }
        }
        findViewById<ImageButton>(R.id.btn_search_clear).setOnClickListener {
            if (searchInput.text.isNotEmpty()) {
                searchInput.setText("")
            } else {
                setSearchVisible(false)
            }
        }

        findViewById<ImageButton>(R.id.fab_import_game).setOnClickListener {
            onImportOtherGameClicked()
        }

        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, pos, _ ->
            val entry = adapter.getItem(pos)
            openGameHub(
                gameId = entry.descriptor.id,
                restoreInstallDialog = gameList.isInstallingForGame(entry.descriptor.id)
            )
        }

        if (savedInstanceState != null) {
            val savedVisible = savedInstanceState.getBoolean(STATE_SEARCH_VISIBLE, false)
            val savedQuery = savedInstanceState.getString(STATE_SEARCH_QUERY, "")
            if (savedVisible || savedQuery.isNotEmpty()) {
                searchBarContainer.visibility = View.VISIBLE
                searchInput.setText(savedQuery)
                searchInput.setSelection(searchInput.text.length)
                adapter.setFilter(savedQuery)
                updateSearchEmptyState()
                syncSearchBackCallback(true)
            }
        }

        handleRestoreDialogIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::searchBarContainer.isInitialized) {
            outState.putBoolean(STATE_SEARCH_VISIBLE, searchBarContainer.visibility == View.VISIBLE)
            outState.putString(STATE_SEARCH_QUERY, searchInput.text?.toString().orEmpty())
        }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (::searchBarContainer.isInitialized && searchBarContainer.visibility == View.VISIBLE) {
            setSearchVisible(false)
            return
        }
        super.onBackPressed()
    }

    private fun syncSearchBackCallback(active: Boolean) {
        if (Build.VERSION.SDK_INT < 33) return
        if (active) {
            if (searchBackCallback == null) {
                val cb = OnBackInvokedCallback { setSearchVisible(false) }
                onBackInvokedDispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    cb
                )
                searchBackCallback = cb
            }
        } else {
            val cb = searchBackCallback as? OnBackInvokedCallback ?: return
            try {
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(cb)
            } catch (_: Throwable) {
            }
            searchBackCallback = null
        }
    }

    private fun reloadEntries() {
        adapter.reload()
        updateSearchEmptyState()
    }

    private fun updateSearchEmptyState() {
        val showEmpty = adapter.count == 0 && adapter.hasQuery()
        searchEmptyView.visibility = if (showEmpty) View.VISIBLE else View.GONE
        listView.visibility = if (showEmpty) View.GONE else View.VISIBLE
    }

    private fun setSearchVisible(visible: Boolean) {
        if (visible) {
            searchBarContainer.visibility = View.VISIBLE
            syncSearchBackCallback(true)
            searchInput.requestFocus()
            searchInput.post {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
            }
        } else {
            syncSearchBackCallback(false)
            hideSoftKeyboard()
            searchInput.setText("")
            searchInput.clearFocus()
            searchBarContainer.visibility = View.GONE
            adapter.setFilter("")
            updateSearchEmptyState()
        }
    }

    private fun hideSoftKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(searchInput.windowToken, 0)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRestoreDialogIntent(intent)
        if (::gameList.isInitialized) {
            reloadEntries()
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            LAUNCHER_INSTALL_DIR_REQ -> {
                pendingPostResumeAction = {
                    InstallForegroundService.requestNotificationPermissionIfNeeded(this)
                    gameList.installDirectory(uri = uri)
                    reloadEntries()
                    renderInstallState(gameList.installState)
                }
            }
            LAUNCHER_INSTALL_ARCHIVE_REQ -> {
                pendingPostResumeAction = {
                    InstallForegroundService.requestNotificationPermissionIfNeeded(this)
                    gameList.installArchive(uri = uri)
                    reloadEntries()
                    renderInstallState(gameList.installState)
                }
            }
        }
    }

    private fun onImportOtherGameClicked() {
        if (gameList.installState is InstallState.Installing) {
            onActiveInstallBannerClick()
            return
        }
        GameCatalog.showLocalGameImportDialog(
            activity = this,
            titleRes = R.string.action_import_other_game,
            archiveReqCode = LAUNCHER_INSTALL_ARCHIVE_REQ,
            dirReqCode = LAUNCHER_INSTALL_DIR_REQ
        )
    }

    private fun handleRestoreDialogIntent(srcIntent: Intent?) {
        if (srcIntent?.getBooleanExtra(InstallForegroundService.EXTRA_RESTORE_INSTALL_DIALOG, false) == true) {
            srcIntent.removeExtra(InstallForegroundService.EXTRA_RESTORE_INSTALL_DIALOG)
            if (::gameList.isInitialized) {
                val targetGameId = gameList.activeTargetGameId
                if (!targetGameId.isNullOrBlank()) {
                    pendingPostResumeAction = {
                        openGameHub(targetGameId, restoreInstallDialog = true)
                    }
                } else {
                    gameList.restoreInstallProgress()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val latestList = obtainGameList(this, refresh = true)
        if (latestList !== gameList) {
            setGameList(latestList)
        } else {
            gameList.observer = this
            reloadEntries()
            renderInstallState(gameList.installState)
        }
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
        syncSearchBackCallback(false)
        if (::gameList.isInitialized && gameList.observer === this) {
            gameList.observer = null
        }
        dismissProgressDialog()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.launcher_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                setSearchVisible(searchBarContainer.visibility != View.VISIBLE)
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openGameHub(gameId: String, restoreInstallDialog: Boolean = false) {
        val intent = Intent(this, GameHubActivity::class.java)
            .putExtra(GameHubActivity.EXTRA_GAME_ID, gameId)
        if (restoreInstallDialog) {
            intent.putExtra(InstallForegroundService.EXTRA_RESTORE_INSTALL_DIALOG, true)
        }
        startActivity(intent)
    }

    private fun onActiveInstallBannerClick() {
        val targetGameId = gameList.activeTargetGameId
        if (!targetGameId.isNullOrBlank()) {
            openGameHub(targetGameId, restoreInstallDialog = true)
        } else {
            gameList.restoreInstallProgress()
            renderInstallState(gameList.installState)
        }
    }

    override fun onInstallProgress(path: String) {
        val now = android.os.SystemClock.uptimeMillis()
        val curTargetId = gameList.activeTargetGameId
        if (curTargetId != lastRenderedInstallingGameId) {
            lastRenderedInstallingGameId = curTargetId
            adapter.notifyDataSetChanged()
        }
        if (now - lastProgressRenderMs < 80L) return
        lastProgressRenderMs = now
        renderInstallState(gameList.installState)
    }

    override fun onInstallSuccess() {
        lastProgressRenderMs = 0L
        lastRenderedInstallingGameId = null
        reloadEntries()
        renderInstallState(gameList.installState)
    }

    override fun onInstallCancelled() {
        lastProgressRenderMs = 0L
        lastRenderedInstallingGameId = null
        reloadEntries()
        renderInstallState(gameList.installState)
    }

    override fun onInstallFailure(msgId: Int) {
        lastProgressRenderMs = 0L
        lastRenderedInstallingGameId = null
        reloadEntries()
        renderInstallState(gameList.installState)
    }

    private fun setGameList(newGameList: GameList) {
        if (gameList.observer === this) {
            gameList.observer = null
        }
        gameList = newGameList
        gameList.observer = this
        adapter = GameSelectAdapter(this, gameList)
        adapter.setFilter(searchInput.text?.toString().orEmpty())
        listView.adapter = adapter
        updateSearchEmptyState()
        renderInstallState(gameList.installState)
    }

    private fun renderInstallState(state: InstallState) {
        val banner = findViewById<TextView>(R.id.active_install_banner)
        banner?.visibility = if (state is InstallState.Installing) View.VISIBLE else View.GONE

        when (state) {
            InstallState.Idle -> dismissProgressDialog()
            is InstallState.Installing -> {
                val targetGameId = gameList.activeTargetGameId
                if (gameList.isProgressMinimized || !targetGameId.isNullOrBlank()) {
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
                reloadEntries()
                Toast.makeText(this, R.string.install_done_toast, Toast.LENGTH_SHORT).show()
                gameList.consumeInstallResult()
            }
            is InstallState.Cancelled -> {
                dismissProgressDialog()
                reloadEntries()
                if (state.paused) {
                    Toast.makeText(this, R.string.online_download_paused_toast, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, R.string.install_cancelled_toast, Toast.LENGTH_SHORT).show()
                }
                gameList.consumeInstallResult()
            }
            is InstallState.Failed -> {
                dismissProgressDialog()
                reloadEntries()
                gameList.consumeInstallResult()
                AlertDialog.Builder(this).setTitle(R.string.error)
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
                    renderInstallState(gameList.installState)
                    InstallForegroundService.requestNotificationPermissionIfNeeded(
                        this@LauncherActivity,
                        forceIfRationale = true
                    )
                    Toast.makeText(
                        this@LauncherActivity,
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
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        progressDialog?.dismiss()
        progressDialog = null
    }
}
