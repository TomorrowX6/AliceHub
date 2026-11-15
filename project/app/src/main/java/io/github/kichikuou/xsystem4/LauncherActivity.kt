package io.github.kichikuou.xsystem4

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

private const val IMPORT_OTHER_ZIP_REQ = 11
private const val IMPORT_OTHER_DIR_REQ = 12

private class GameSelectAdapter(
    private val activity: Activity,
    private val gameList: GameList
) : BaseAdapter() {
    private val context: Context = activity
    private var entries: List<GameSelectEntry> = GameCatalog.buildSelectEntries(activity, gameList)

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    fun reload() {
        entries = GameCatalog.buildSelectEntries(context, gameList)
        notifyDataSetChanged()
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
        card.background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(0x16888888)
            setStroke(dp(1), desc.accentBorderColor)
        }

        val accentBar = view.findViewById<View>(R.id.accent_bar)
        accentBar.background = GradientDrawable().apply {
            cornerRadius = dp(2).toFloat()
            setColor(desc.accentColor)
        }

        val iconFrame = view.findViewById<FrameLayout>(R.id.icon_frame)
        iconFrame.background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(desc.iconFrameColor)
            setStroke(dp(1), 0x33FFFFFF)
        }

        val iconView = view.findViewById<ImageView>(R.id.icon)
        if (desc.id == GameCatalog.ID_RANCE10 || desc.id == GameCatalog.ID_DOHNA_DOHNA) {
            iconView.setImageResource(desc.iconRes)
        } else {
            val bmp = primary?.getIconBitmap(dp(52))
            if (bmp != null) {
                iconView.setImageBitmap(bmp)
            } else {
                iconView.setImageResource(desc.iconRes)
            }
        }

        view.findViewById<TextView>(R.id.title).text = desc.title

        val runningRoot = GameCatalog.getRunningGameRoot(context)
        val isRunning = runningRoot != null && entry.items.any { it.path.path == runningRoot }
        val isInstalling = gameList.isInstallingForGame(desc.id)

        val badge = view.findViewById<TextView>(R.id.status_badge)
        val badgeBgColor: Int
        val badgeTextColor: Int
        val badgeLabel: String

        when {
            isInstalling -> {
                badgeLabel = context.getString(R.string.game_status_installing)
                badgeBgColor = 0x332196F3
                badgeTextColor = 0xFF64B5F6.toInt()
            }
            isRunning -> {
                badgeLabel = context.getString(R.string.game_status_running)
                badgeBgColor = 0x334CAF50
                badgeTextColor = 0xFF81C784.toInt()
            }
            primary != null && primary.error != null -> {
                badgeLabel = context.getString(R.string.game_status_error)
                badgeBgColor = 0x33F44336
                badgeTextColor = 0xFFE57373.toInt()
            }
            primary != null && gameList.hasInstalledMod(primary) -> {
                val ver = gameList.getInstalledModInfo(primary)?.version?.takeIf { it.isNotBlank() } ?: "MOD"
                badgeLabel = context.getString(R.string.game_status_installed_mod, ver)
                badgeBgColor = 0x33FFB300
                badgeTextColor = 0xFFFFCA28.toInt()
            }
            primary != null -> {
                badgeLabel = context.getString(R.string.game_status_installed)
                badgeBgColor = 0x334CAF50
                badgeTextColor = 0xFF66BB6A.toInt()
            }
            else -> {
                badgeLabel = context.getString(R.string.game_status_not_installed)
                badgeBgColor = 0x24888888
                badgeTextColor = Color.GRAY
            }
        }

        badge.text = badgeLabel
        badge.setTextColor(badgeTextColor)
        badge.background = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(badgeBgColor)
        }

        return view
    }
}

class LauncherActivity : Activity(), GameListObserver {
    companion object {
        private var savedGameList: GameList? = null

        fun obtainGameList(activity: Activity, refresh: Boolean = false): GameList {
            if (savedGameList == null || (refresh && savedGameList?.installState is InstallState.Idle)) {
                savedGameList = GameList(activity)
            }
            return savedGameList!!
        }
    }

    private lateinit var gameList: GameList
    private lateinit var adapter: GameSelectAdapter
    private var progressDialog: Dialog? = null
    private var pendingPostResumeAction: (() -> Unit)? = null
    private var startedGenericImportHere: Boolean = false

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.launcher)
        title = getString(R.string.select_game_title)

        gameList = obtainGameList(this, refresh = false)
        adapter = GameSelectAdapter(this, gameList)

        val banner = findViewById<TextView>(R.id.active_install_banner)
        banner.background = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(0x262196F3)
            setStroke(dp(1), 0x662196F3)
        }
        banner.setTextColor(0xFF64B5F6.toInt())
        banner.setOnClickListener { onActiveInstallBannerClick() }

        val listView = findViewById<ListView>(R.id.list)
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, pos, _ ->
            val entry = adapter.getItem(pos)
            openGameHub(
                gameId = entry.descriptor.id,
                restoreInstallDialog = gameList.isInstallingForGame(entry.descriptor.id)
            )
        }

        findViewById<View>(R.id.btn_import_other).setOnClickListener { openImportOtherGameDialog() }
        findViewById<View>(R.id.open_github).setOnClickListener { openGitHub() }

        handleRestoreDialogIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRestoreDialogIntent(intent)
        if (::gameList.isInitialized) {
            adapter.reload()
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
        val latestList = obtainGameList(this, refresh = false)
        if (latestList !== gameList) {
            setGameList(latestList)
        } else {
            gameList.observer = this
            adapter.reload()
            renderInstallState(gameList.installState)
        }
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
        menuInflater.inflate(R.menu.launcher_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.refresh -> {
                setGameList(obtainGameList(this, refresh = true))
                true
            }
            R.id.import_other_game -> {
                openImportOtherGameDialog()
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
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            IMPORT_OTHER_DIR_REQ -> {
                pendingPostResumeAction = {
                    startedGenericImportHere = true
                    InstallForegroundService.requestNotificationPermissionIfNeeded(this)
                    gameList.installDirectory(uri, expectedAinName = null, targetGameId = null)
                    adapter.reload()
                    renderInstallState(gameList.installState)
                }
            }
            IMPORT_OTHER_ZIP_REQ -> {
                pendingPostResumeAction = {
                    startedGenericImportHere = true
                    InstallForegroundService.requestNotificationPermissionIfNeeded(this)
                    gameList.installArchive(uri, expectedAinName = null, targetGameId = null)
                    adapter.reload()
                    renderInstallState(gameList.installState)
                }
            }
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

    private fun openImportOtherGameDialog() {
        if (gameList.installState is InstallState.Installing) {
            onActiveInstallBannerClick()
            return
        }
        val options = arrayOf(
            getString(R.string.game_opt_zip),
            getString(R.string.game_opt_dir)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.action_import_other_game)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivityForResult(Intent.createChooser(i, getString(R.string.choose_a_file)), IMPORT_OTHER_ZIP_REQ)
                    }
                    1 -> {
                        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, IMPORT_OTHER_DIR_REQ)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openGitHub() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GameCatalog.GITHUB_URL)))
    }

    override fun onInstallProgress(path: String) {
        adapter.reload()
        renderInstallState(gameList.installState)
    }

    override fun onInstallSuccess() {
        adapter.reload()
        renderInstallState(gameList.installState)
    }

    override fun onInstallCancelled() {
        adapter.reload()
        renderInstallState(gameList.installState)
    }

    override fun onInstallFailure(msgId: Int) {
        adapter.reload()
        renderInstallState(gameList.installState)
    }

    private fun setGameList(newGameList: GameList) {
        if (gameList.observer === this) {
            gameList.observer = null
        }
        gameList = newGameList
        gameList.observer = this
        adapter = GameSelectAdapter(this, gameList)
        findViewById<ListView>(R.id.list).adapter = adapter
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
                adapter.reload()
                val installed = gameList.lastInstalledItem
                val openImported = startedGenericImportHere && installed != null
                startedGenericImportHere = false
                Toast.makeText(this, R.string.install_done_toast, Toast.LENGTH_SHORT).show()
                gameList.consumeInstallResult()
                if (openImported && installed != null) {
                    openGameHub(GameCatalog.classifyItem(installed))
                }
            }
            is InstallState.Cancelled -> {
                dismissProgressDialog()
                startedGenericImportHere = false
                adapter.reload()
                if (state.resumable) {
                    Toast.makeText(this, R.string.online_download_paused_toast, Toast.LENGTH_LONG).show()
                }
                gameList.consumeInstallResult()
            }
            is InstallState.Failed -> {
                dismissProgressDialog()
                startedGenericImportHere = false
                adapter.reload()
                gameList.clearPendingModAuthRetry()
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
