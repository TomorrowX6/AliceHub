package io.github.kichikuou.xsystem4

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
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
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

private class GameSelectAdapter(
    private val activity: Activity,
    private val gameList: GameList
) : BaseAdapter() {
    private val context: Context = activity
    private var entries: List<GameSelectEntry> = GameCatalog.buildSelectEntries(activity, gameList)

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
                val ver = gameList.getInstalledModInfo(primary)?.version?.takeIf { it.isNotBlank() } ?: "MOD"
                badgeLabel = context.getString(R.string.game_status_installed_mod, ver)
                badgeTone = UiKit.Tone.WARNING
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.launcher)
        title = getString(R.string.select_game_title)

        gameList = obtainGameList(this, refresh = false)
        adapter = GameSelectAdapter(this, gameList)

        val banner = findViewById<TextView>(R.id.active_install_banner)
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
        val latestList = obtainGameList(this, refresh = true)
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
            R.id.licenses -> {
                startActivity(Intent(this, LicensesMenuActivity::class.java))
                true
            }
            R.id.open_github -> {
                openGitHub()
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
                Toast.makeText(this, R.string.install_done_toast, Toast.LENGTH_SHORT).show()
                gameList.consumeInstallResult()
            }
            is InstallState.Cancelled -> {
                dismissProgressDialog()
                adapter.reload()
                if (state.resumable) {
                    Toast.makeText(this, R.string.online_download_paused_toast, Toast.LENGTH_LONG).show()
                }
                gameList.consumeInstallResult()
            }
            is InstallState.Failed -> {
                dismissProgressDialog()
                adapter.reload()
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
