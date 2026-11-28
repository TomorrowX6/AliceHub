package io.github.kichikuou.xsystem4

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsActivity : ThemedActivity() {

    private companion object {
        const val RECENT_SHARED_REPORT_PROTECT_MS = 3_600_000L
    }

    private data class CacheScanResult(
        val routineTargets: List<File>,
        val routineBytes: Long,
        val resumableTargets: List<File>,
        val resumableBytes: Long
    ) {
        val totalBytes: Long get() = routineBytes + resumableBytes
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var clearingCache: Boolean = false
    private val trackedDialogs = mutableListOf<Dialog>()

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
        setContentView(R.layout.activity_settings)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<View>(R.id.settings_appearance_card).clipToOutline = true
        findViewById<View>(R.id.settings_storage_card).clipToOutline = true
        findViewById<View>(R.id.settings_controls_card).clipToOutline = true
        findViewById<View>(R.id.settings_about_card).clipToOutline = true

        val accent = UiKit.primaryColor(this)
        findViewById<ImageView>(R.id.icon_settings_theme)
            .setImageDrawable(UiKit.tonalIcon(this, R.drawable.ic_theme, accent))
        findViewById<ImageView>(R.id.icon_settings_palette)
            .setImageDrawable(UiKit.tonalIcon(this, R.drawable.ic_palette, accent))
        findViewById<ImageView>(R.id.icon_settings_language)
            .setImageDrawable(UiKit.tonalIcon(this, R.drawable.ic_language, accent))
        findViewById<ImageView>(R.id.icon_settings_game_sort)
            .setImageDrawable(UiKit.tonalIcon(this, R.drawable.ic_sort, accent))
        findViewById<ImageView>(R.id.icon_settings_online)
            .setImageDrawable(UiKit.tonalIcon(this, R.drawable.ic_cloud_download, accent))
        findViewById<ImageView>(R.id.icon_settings_updates)
            .setImageDrawable(UiKit.tonalIcon(this, R.drawable.ic_download, accent))
        findViewById<ImageView>(R.id.icon_settings_download_concurrency)
            .setImageDrawable(UiKit.tonalIcon(this, R.drawable.ic_download_concurrency, accent))
        findViewById<ImageView>(R.id.icon_settings_gamepad)
            .setImageDrawable(UiKit.tonalIcon(this, R.drawable.ic_gamepad, accent))
        findViewById<ImageView>(R.id.icon_settings_clear_cache)
            .setImageDrawable(UiKit.tonalIcon(this, R.drawable.ic_clear_cache, accent))
        findViewById<ImageView>(R.id.icon_settings_storage)
            .setImageDrawable(UiKit.tonalIcon(this, R.drawable.ic_folder_open, accent))

        UiKit.tintSwitch(findViewById(R.id.switch_online_install), accent)
        UiKit.tintSwitch(findViewById(R.id.switch_updates), accent)

        val btnQqGroup = findViewById<Button>(R.id.btn_settings_qq_group)
        val btnLicenses = findViewById<Button>(R.id.btn_settings_licenses)
        val btnGithub = findViewById<Button>(R.id.btn_settings_github)
        UiKit.styleActionRow(btnQqGroup, R.drawable.ic_qq_group, accent)
        UiKit.styleActionRow(btnLicenses, R.drawable.ic_licenses, accent)
        UiKit.styleActionRow(btnGithub, R.drawable.ic_github, accent)

        findViewById<TextView>(R.id.text_settings_version)?.text =
            "AliceHub · ${BuildConfig.VERSION_NAME}"

        findViewById<View>(R.id.row_settings_theme).setOnClickListener {
            showThemeDialog()
        }
        findViewById<View>(R.id.row_settings_palette).setOnClickListener {
            showColorPaletteDialog()
        }
        findViewById<View>(R.id.row_settings_language).setOnClickListener {
            showLanguageDialog()
        }
        findViewById<View>(R.id.row_settings_game_sort).setOnClickListener {
            showGameSortDialog()
        }
        findViewById<View>(R.id.row_settings_online_install).setOnClickListener {
            onOnlineInstallClicked()
        }
        findViewById<View>(R.id.row_settings_updates).setOnClickListener {
            AppSettings.setUpdateCheckEnabled(this, !AppSettings.isUpdateCheckEnabled(this))
            refreshSettingsUi()
        }
        findViewById<View>(R.id.row_settings_download_concurrency).setOnClickListener {
            showDownloadConcurrencyDialog()
        }
        findViewById<View>(R.id.row_settings_gamepad).setOnClickListener {
            startActivity(Intent(this, GamepadSettingsActivity::class.java))
        }
        findViewById<View>(R.id.row_settings_clear_cache).setOnClickListener {
            onClearCacheClicked()
        }
        findViewById<View>(R.id.row_settings_storage).setOnClickListener {
            showStorageLocationDialog()
        }
        btnQqGroup.setOnClickListener {
            showQqGroupDialog()
        }
        btnLicenses.setOnClickListener {
            startActivity(Intent(this, LicensesMenuActivity::class.java))
        }
        btnGithub.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GameCatalog.GITHUB_URL)))
        }

        refreshSettingsUi()
    }

    override fun onResume() {
        super.onResume()
        refreshSettingsUi()
        if (!SharedStorageMigration.attach(this, ::refreshStorageLabel)) {
            SharedStorageMigration.showPendingFailureNotice(this)
        }
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(code, permissions, grantResults)
        if (code == STORAGE_PERMISSION_REQUEST) applyStoragePermission()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == STORAGE_PERMISSION_REQUEST) applyStoragePermission()
    }

    private fun applyStoragePermission() {
        refreshStorageLabel()
        if (!LauncherActivity.showStorageMigration(this, retry = true, onComplete = ::refreshStorageLabel)) {
            ErrorReportKit.markExistingCrashesSeen(this)
        }
    }

    private fun refreshStorageLabel() {
        val label = when {
            !AppSettings.prefersPublicStorage(this) -> R.string.storage_location_private
            !AppStorage.hasPermission(this) -> R.string.storage_location_pending
            else -> R.string.storage_location_public
        }
        findViewById<TextView>(R.id.value_settings_storage).setText(label)
    }

    private fun showStorageLocationDialog() {
        val options = createStorageOptions(AppSettings.prefersPublicStorage(this))
        options.findViewById<RadioGroup>(R.id.storage_options).apply {
            background = null
            setPadding(0, 0, 0, 0)
        }
        options.findViewById<TextView>(R.id.storage_option_public).setText(R.string.storage_option_shared_directory)
        options.findViewById<TextView>(R.id.storage_option_private).setText(R.string.storage_option_app_directory)
        trackDialog(AlertDialog.Builder(this)
            .setTitle(R.string.storage_location)
            .setView(UiKit.dialogContent(this, options))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                val selected = options.findViewById<RadioGroup>(R.id.storage_options).checkedRadioButtonId
                val usePublic = selected == R.id.storage_option_public
                AppSettings.setPublicStorage(this, usePublic)
                refreshStorageLabel()
                if (usePublic) {
                    if (!requestSharedStorageAccess()) applyStoragePermission()
                } else {
                    ErrorReportKit.markExistingCrashesSeen(this)
                }
            }
            .show())
    }

    override fun onDestroy() {
        SharedStorageMigration.detach(this)
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

    private fun refreshSettingsUi() {
        val mode = AppSettings.getThemeMode(this)
        findViewById<TextView>(R.id.value_settings_theme).text = when (mode) {
            AppSettings.THEME_LIGHT -> getString(R.string.theme_light)
            AppSettings.THEME_DARK -> getString(R.string.theme_dark)
            else -> getString(R.string.theme_auto)
        }
        val activePalette = AppSettings.getColorPalette(this)
        findViewById<TextView>(R.id.value_settings_palette)?.let { badge ->
            badge.text = colorPaletteLabel(activePalette)
            UiKit.styleChip(badge, UiKit.Tone.PRIMARY)
        }
        rebuildPaletteStrip(activePalette)
        val sortLabel = gameSortSummaryLabel(AppSettings.getGameSortMode(this))
        findViewById<TextView>(R.id.value_settings_game_sort).text = sortLabel
        findViewById<View>(R.id.row_settings_game_sort).contentDescription =
            getString(R.string.settings_game_sort) + ", " + sortLabel
        val lang = AppSettings.getLanguageMode(this)
        findViewById<TextView>(R.id.value_settings_language).text = when (lang) {
            AppSettings.LANG_ZH -> getString(R.string.lang_zh)
            AppSettings.LANG_JA -> getString(R.string.lang_ja)
            AppSettings.LANG_EN -> getString(R.string.lang_en)
            else -> getString(R.string.lang_auto)
        }
        findViewById<Switch>(R.id.switch_online_install).isChecked =
            AppSettings.isOnlineInstallEnabled(this)
        refreshStorageLabel()
        findViewById<Switch>(R.id.switch_updates).isChecked = AppSettings.isUpdateCheckEnabled(this)
        val concurrencyLabel = downloadConcurrencyLabel(AppSettings.getDownloadConcurrency(this))
        findViewById<TextView>(R.id.value_settings_download_concurrency).text = concurrencyLabel
        findViewById<View>(R.id.row_settings_download_concurrency).contentDescription =
            getString(R.string.settings_download_concurrency) + ", " + concurrencyLabel
        val gp = GamepadBridge.loadProfile(this, null)
        val connectedPads = GamepadBridge.queryConnectedControllers(this).size
        findViewById<TextView>(R.id.value_settings_gamepad)?.text = when {
            gp.physical.enabled && connectedPads > 0 -> GamepadBridge.tr(this, "已连 ${connectedPads} 手柄", "${connectedPads}台接続", "${connectedPads} Connected")
            gp.virtual.enabled && gp.physical.enabled -> GamepadBridge.tr(this, "全开启", "すべてON", "All ON")
            gp.virtual.enabled -> GamepadBridge.tr(this, "虚拟开", "仮想ON", "Virtual ON")
            gp.physical.enabled -> GamepadBridge.tr(this, "实体开", "物理ON", "Physical ON")
            else -> GamepadBridge.tr(this, "已关闭", "OFF", "OFF")
        }
        refreshCacheSizeAsync()
    }

    private fun refreshCacheSizeAsync() {
        val appCtx = applicationContext
        scope.launch {
            val scan = withContext(Dispatchers.IO) {
                scanCacheTargets(appCtx)
            }
            findViewById<TextView>(R.id.value_settings_clear_cache)?.text = formatCacheSize(scan.totalBytes)
        }
    }

    private fun onClearCacheClicked() {
        if (clearingCache) return
        val gameList = LauncherActivity.obtainGameList(this, refresh = false)
        if (gameList.installState is InstallState.Installing || gameList.isRestoringMod || UncensoredPatchManager.isAnyDownloading()) {
            Toast.makeText(this, R.string.settings_clear_cache_busy_toast, Toast.LENGTH_SHORT).show()
            return
        }
        val appCtx = applicationContext
        scope.launch {
            val scan = withContext(Dispatchers.IO) {
                scanCacheTargets(appCtx)
            }
            findViewById<TextView>(R.id.value_settings_clear_cache)?.text = formatCacheSize(scan.totalBytes)
            if (scan.totalBytes <= 0L) {
                Toast.makeText(
                    this@SettingsActivity,
                    R.string.settings_clear_cache_empty_toast,
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            if (scan.routineBytes > 0L && scan.resumableBytes <= 0L) {
                val sizeLabel = formatCacheSize(scan.routineBytes)
                trackDialog(
                    AlertDialog.Builder(this@SettingsActivity)
                        .setTitle(R.string.settings_clear_cache)
                        .setMessage(getString(R.string.settings_clear_cache_confirm_msg, sizeLabel))
                        .setPositiveButton(R.string.ok) { _, _ ->
                            performClearCache(includeResumable = false, expectedBytes = scan.routineBytes)
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                )
            } else if (scan.routineBytes > 0L && scan.resumableBytes > 0L) {
                val routineLabel = formatCacheSize(scan.routineBytes)
                val resumableLabel = formatCacheSize(scan.resumableBytes)
                val msg = GamepadBridge.tr(
                    this@SettingsActivity,
                    "${getString(R.string.settings_clear_cache_confirm_msg, routineLabel)}\n\n已自动保留暂停的断点续传下载数据（$resumableLabel）。",
                    "${getString(R.string.settings_clear_cache_confirm_msg, routineLabel)}\n\n一時停止中のダウンロードデータ（$resumableLabel）は保持されます。",
                    "${getString(R.string.settings_clear_cache_confirm_msg, routineLabel)}\n\nPaused download progress ($resumableLabel) will be preserved."
                )
                val clearAllLabel = GamepadBridge.tr(
                    this@SettingsActivity,
                    "连同续传数据一起清除",
                    "再開データも削除",
                    "Also Clear Paused Data"
                )
                trackDialog(
                    AlertDialog.Builder(this@SettingsActivity)
                        .setTitle(R.string.settings_clear_cache)
                        .setMessage(msg)
                        .setPositiveButton(R.string.ok) { _, _ ->
                            performClearCache(includeResumable = false, expectedBytes = scan.routineBytes)
                        }
                        .setNeutralButton(clearAllLabel) { _, _ ->
                            confirmDiscardResumableDownloads(
                                includeRoutine = true,
                                resumableBytes = scan.resumableBytes,
                                expectedTotalBytes = scan.totalBytes
                            )
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                )
            } else {
                confirmDiscardResumableDownloads(
                    includeRoutine = false,
                    resumableBytes = scan.resumableBytes,
                    expectedTotalBytes = scan.resumableBytes
                )
            }
        }
    }

    private fun confirmDiscardResumableDownloads(
        includeRoutine: Boolean,
        resumableBytes: Long,
        expectedTotalBytes: Long
    ) {
        val resumableLabel = formatCacheSize(resumableBytes)
        val warnMsg = GamepadBridge.tr(
            this,
            "检测到 $resumableLabel 未完成的断点续传下载缓存。清除后下载进度将全部丢失，需要从头重新下载。确定要删除吗？",
            "一時停止中のダウンロードデータ（$resumableLabel）があります。削除すると進捗が失われ、最初から再ダウンロードが必要になります。削除しますか？",
            "Found $resumableLabel of paused download data. Deleting it will discard your download progress and require starting over. Delete anyway?"
        )
        trackDialog(
            AlertDialog.Builder(this)
                .setTitle(R.string.settings_clear_cache)
                .setMessage(warnMsg)
                .setPositiveButton(R.string.ok) { _, _ ->
                    performClearCache(includeResumable = true, expectedBytes = expectedTotalBytes, includeRoutine = includeRoutine)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        )
    }

    private fun performClearCache(
        includeResumable: Boolean,
        expectedBytes: Long,
        includeRoutine: Boolean = true
    ) {
        if (clearingCache) return
        val gameList = LauncherActivity.obtainGameList(this, refresh = false)
        if (gameList.installState is InstallState.Installing || gameList.isRestoringMod || UncensoredPatchManager.isAnyDownloading()) {
            Toast.makeText(this, R.string.settings_clear_cache_busy_toast, Toast.LENGTH_SHORT).show()
            return
        }
        clearingCache = true
        val appCtx = applicationContext
        scope.launch {
            val (beforeTotal, afterTotal) = try {
                withContext(Dispatchers.IO) {
                    val before = scanCacheTargets(appCtx)
                    val toDelete = ArrayList<File>()
                    if (includeRoutine) {
                        toDelete.addAll(before.routineTargets)
                    }
                    if (includeResumable) {
                        toDelete.addAll(before.resumableTargets)
                    }
                    deleteCacheTargetList(toDelete)
                    val after = scanCacheTargets(appCtx)
                    before.totalBytes to after.totalBytes
                }
            } finally {
                clearingCache = false
            }
            findViewById<TextView>(R.id.value_settings_clear_cache)?.text = formatCacheSize(afterTotal)
            val freed = (beforeTotal - afterTotal).coerceAtLeast(0L)
            val freedLabel = formatCacheSize(if (freed > 0L) freed else expectedBytes)
            Toast.makeText(
                this@SettingsActivity,
                getString(R.string.settings_clear_cache_done_toast, freedLabel),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun formatCacheSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        return OnlineInstaller.formatSize(bytes).ifEmpty { "0 B" }
    }

    private fun collectCacheRootChildren(cacheRoot: File?, nowMs: Long, out: MutableSet<File>) {
        val children = cacheRoot?.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory && child.name == "shared_reports") {
                val reports = child.listFiles().orEmpty()
                for (rep in reports) {
                    val modified = rep.lastModified()
                    if (modified <= 0L || nowMs - modified >= RECENT_SHARED_REPORT_PROTECT_MS) {
                        out.add(rep)
                    }
                }
            } else {
                out.add(child)
            }
        }
    }

    private fun scanCacheTargets(context: Context): CacheScanResult {
        val nowMs = System.currentTimeMillis()
        val routine = LinkedHashSet<File>()
        val resumable = LinkedHashSet<File>()

        collectCacheRootChildren(context.cacheDir, nowMs, routine)
        context.externalCacheDirs?.filterNotNull()?.forEach { dir ->
            collectCacheRootChildren(dir, nowMs, routine)
        }
        context.filesDir.listFiles()?.filter { f ->
            f.name.startsWith(SAVE_IMPORT_TMP_PREFIX) &&
                (f.lastModified() <= 0L || nowMs - f.lastModified() >= 600_000L)
        }?.forEach { routine.add(it) }

        for (root in AppStorage.gameRoots(context)) {
            val children = root.listFiles() ?: continue
            for (f in children) {
                val n = f.name
                val isResumableDir = n.startsWith(".online_base_staging_") ||
                    n == ".online_mod_cache" ||
                    n == UncensoredPatchManager.CACHE_DIR_NAME
                if (isResumableDir) {
                    if (computeFileTreeBytes(f) > 0L) {
                        resumable.add(f)
                    } else {
                        routine.add(f)
                    }
                    continue
                }
                val isRoutineTemp = n.startsWith(".mod_online_extract_temp") ||
                    n.startsWith(".install_temp") ||
                    n.startsWith(".mod_install_temp") ||
                    n.startsWith(".trash_") ||
                    n.startsWith(".import-") ||
                    n.startsWith(".mod-import-") ||
                    (f.isFile && n.startsWith(".archive_src_") && n.endsWith(".tmp"))
                if (isRoutineTemp) {
                    routine.add(f)
                }
            }
        }

        var routineSum = 0L
        for (target in routine) {
            routineSum += computeFileTreeBytes(target)
        }
        var resumableSum = 0L
        for (target in resumable) {
            resumableSum += computeFileTreeBytes(target)
        }
        return CacheScanResult(
            routineTargets = routine.toList(),
            routineBytes = routineSum,
            resumableTargets = resumable.toList(),
            resumableBytes = resumableSum
        )
    }

    private fun computeFileTreeBytes(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length().coerceAtLeast(0L)
        var sum = 0L
        val children = file.listFiles() ?: return 0L
        for (child in children) {
            sum += computeFileTreeBytes(child)
        }
        return sum
    }

    private fun deleteCacheTargetList(targets: List<File>) {
        for (target in targets) {
            try {
                if (target.isDirectory) {
                    target.deleteRecursively()
                } else {
                    target.delete()
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun showQqGroupDialog() {
        val dm = resources.displayMetrics
        val sheetWidth = if (dm.widthPixels > UiKit.dpInt(this, 520)) {
            UiKit.dpInt(this, 440)
        } else {
            ViewGroup.LayoutParams.MATCH_PARENT
        }
        val qrWidth = (dm.widthPixels - UiKit.dpInt(this, 64))
            .coerceIn(UiKit.dpInt(this, 220), UiKit.dpInt(this, 310))
        val qrMaxHeight = (dm.heightPixels * 0.68f).toInt()

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val scrim = View(this).apply {
            setBackgroundColor(0x8A000000.toInt())
            alpha = 0f
        }

        val handleView = View(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(this@SettingsActivity, 2f)
                setColor(UiKit.withAlpha(getColor(R.color.ui_text_tertiary), 0x66))
            }
        }

        val qrBitmap = BitmapFactory.decodeResource(
            resources,
            R.drawable.qq_group_qr,
            BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        )
        val qrView = ImageView(this).apply {
            if (qrBitmap != null) {
                setImageBitmap(qrBitmap)
            } else {
                setImageResource(R.drawable.qq_group_qr)
            }
            adjustViewBounds = true
            maxHeight = qrMaxHeight
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(this@SettingsActivity, 16f)
                setColor(Color.WHITE)
            }
            clipToOutline = true
        }

        val topRadius = UiKit.dp(this, 24f)
        val basePadH = UiKit.dpInt(this, 24)
        val basePadTop = UiKit.dpInt(this, 12)
        val basePadBottom = UiKit.dpInt(this, 28)

        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(basePadH, basePadTop, basePadH, basePadBottom)
            elevation = UiKit.dp(this@SettingsActivity, 16f)
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(
                    topRadius, topRadius,
                    topRadius, topRadius,
                    0f, 0f,
                    0f, 0f
                )
                setColor(UiKit.surfaceDialogColor(this@SettingsActivity))
            }
            isClickable = true

            addView(
                handleView,
                LinearLayout.LayoutParams(
                    UiKit.dpInt(this@SettingsActivity, 36),
                    UiKit.dpInt(this@SettingsActivity, 4)
                ).apply {
                    bottomMargin = UiKit.dpInt(this@SettingsActivity, 16)
                }
            )
            addView(
                qrView,
                LinearLayout.LayoutParams(
                    qrWidth,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val root = FrameLayout(this).apply {
            addView(
                scrim,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                sheet,
                FrameLayout.LayoutParams(
                    sheetWidth,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                )
            )
            setOnApplyWindowInsetsListener { _, insets ->
                @Suppress("DEPRECATION")
                val bottomInset = insets.systemWindowInsetBottom.coerceAtLeast(0)
                sheet.setPadding(basePadH, basePadTop, basePadH, basePadBottom + bottomInset)
                insets
            }
        }

        var dismissing = false
        fun dismissSheet() {
            if (dismissing) return
            dismissing = true
            val targetY = sheet.height.toFloat().coerceAtLeast(UiKit.dp(this, 320f))
            scrim.animate()
                .alpha(0f)
                .setDuration(200L)
                .start()
            sheet.animate()
                .translationY(targetY)
                .setDuration(200L)
                .setInterpolator(AccelerateInterpolator(1.6f))
                .withEndAction {
                    if (dialog.isShowing) {
                        dialog.dismiss()
                    }
                }
                .start()
        }

        scrim.setOnClickListener { dismissSheet() }

        var touchDownRawY = 0f
        sheet.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownRawY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = (event.rawY - touchDownRawY).coerceAtLeast(0f)
                    sheet.translationY = dy
                    val h = sheet.height.coerceAtLeast(1)
                    scrim.alpha = (1f - (dy / h) * 0.7f).coerceIn(0f, 1f)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val dy = (event.rawY - touchDownRawY).coerceAtLeast(0f)
                    val h = sheet.height.coerceAtLeast(1)
                    if (dy > h * 0.22f) {
                        dismissSheet()
                    } else {
                        sheet.animate()
                            .translationY(0f)
                            .setDuration(180L)
                            .setInterpolator(DecelerateInterpolator(2f))
                            .start()
                        scrim.animate()
                            .alpha(1f)
                            .setDuration(180L)
                            .start()
                    }
                    true
                }
                else -> false
            }
        }

        dialog.setContentView(root)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                dismissSheet()
                true
            } else {
                false
            }
        }

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setWindowAnimations(0)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.BOTTOM)
            navigationBarColor = UiKit.surfaceDialogColor(this@SettingsActivity)
        }

        sheet.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                sheet.viewTreeObserver.removeOnPreDrawListener(this)
                sheet.translationY = sheet.height.toFloat()
                scrim.animate()
                    .alpha(1f)
                    .setDuration(220L)
                    .start()
                sheet.animate()
                    .translationY(0f)
                    .setDuration(260L)
                    .setInterpolator(DecelerateInterpolator(2.2f))
                    .start()
                return true
            }
        })

        dialog.show()
        trackDialog(dialog)
    }

    private fun gameSortSummaryLabel(mode: GameSortMode): String = getString(when (mode) {
        GameSortMode.CATALOG -> R.string.game_sort_catalog_short
        GameSortMode.NAME -> R.string.game_sort_name_short
        GameSortMode.RATING -> R.string.game_sort_rating_short
        GameSortMode.RELEASE_DATE -> R.string.game_sort_release_date_short
    })

    private fun gameSortLabel(mode: GameSortMode): String = getString(when (mode) {
        GameSortMode.CATALOG -> R.string.game_sort_catalog
        GameSortMode.NAME -> R.string.game_sort_name
        GameSortMode.RATING -> R.string.game_sort_rating
        GameSortMode.RELEASE_DATE -> R.string.game_sort_release_date
    })

    private fun showGameSortDialog() {
        val modes = GameSortMode.values()
        trackDialog(
            AlertDialog.Builder(this)
                .setTitle(R.string.settings_game_sort)
                .setSingleChoiceItems(modes.map { gameSortLabel(it) }.toTypedArray(),
                    modes.indexOf(AppSettings.getGameSortMode(this))) { dlg, which ->
                    AppSettings.setGameSortMode(this, modes[which])
                    dlg.dismiss()
                    refreshSettingsUi()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        )
    }

    private data class PalettePreview(
        val primary: Int,
        val onPrimary: Int,
        val tonalBg: Int,
        val canvasBg: Int
    )

    private fun colorPaletteLabel(palette: String): String = getString(when (palette) {
        AppSettings.PALETTE_MONET -> R.string.palette_monet
        AppSettings.PALETTE_SAKURA -> R.string.palette_sakura
        AppSettings.PALETTE_OCEAN -> R.string.palette_ocean
        AppSettings.PALETTE_FOREST -> R.string.palette_forest
        AppSettings.PALETTE_AMBER -> R.string.palette_amber
        AppSettings.PALETTE_VIOLET -> R.string.palette_violet
        else -> R.string.palette_indigo
    })

    private fun colorPaletteShortLabel(palette: String): String = getString(when (palette) {
        AppSettings.PALETTE_MONET -> R.string.palette_monet_short
        AppSettings.PALETTE_SAKURA -> R.string.palette_sakura_short
        AppSettings.PALETTE_OCEAN -> R.string.palette_ocean_short
        AppSettings.PALETTE_FOREST -> R.string.palette_forest_short
        AppSettings.PALETTE_AMBER -> R.string.palette_amber_short
        AppSettings.PALETTE_VIOLET -> R.string.palette_violet_short
        else -> R.string.palette_indigo_short
    })

    private fun palettePreviewColors(palette: String): PalettePreview = when (palette) {
        AppSettings.PALETTE_MONET -> PalettePreview(
            primary = getColor(R.color.monet_primary),
            onPrimary = getColor(R.color.monet_on_primary),
            tonalBg = getColor(R.color.monet_tone_primary_bg),
            canvasBg = getColor(R.color.monet_bg)
        )
        AppSettings.PALETTE_SAKURA -> PalettePreview(
            primary = getColor(R.color.sakura_primary),
            onPrimary = getColor(R.color.sakura_on_primary),
            tonalBg = getColor(R.color.sakura_tone_primary_bg),
            canvasBg = getColor(R.color.sakura_bg)
        )
        AppSettings.PALETTE_OCEAN -> PalettePreview(
            primary = getColor(R.color.ocean_primary),
            onPrimary = getColor(R.color.ocean_on_primary),
            tonalBg = getColor(R.color.ocean_tone_primary_bg),
            canvasBg = getColor(R.color.ocean_bg)
        )
        AppSettings.PALETTE_FOREST -> PalettePreview(
            primary = getColor(R.color.forest_primary),
            onPrimary = getColor(R.color.forest_on_primary),
            tonalBg = getColor(R.color.forest_tone_primary_bg),
            canvasBg = getColor(R.color.forest_bg)
        )
        AppSettings.PALETTE_AMBER -> PalettePreview(
            primary = getColor(R.color.amber_primary),
            onPrimary = getColor(R.color.amber_on_primary),
            tonalBg = getColor(R.color.amber_tone_primary_bg),
            canvasBg = getColor(R.color.amber_bg)
        )
        AppSettings.PALETTE_VIOLET -> PalettePreview(
            primary = getColor(R.color.violet_primary),
            onPrimary = getColor(R.color.violet_on_primary),
            tonalBg = getColor(R.color.violet_tone_primary_bg),
            canvasBg = getColor(R.color.violet_bg)
        )
        else -> PalettePreview(
            primary = getColor(R.color.ui_primary),
            onPrimary = getColor(R.color.ui_on_primary),
            tonalBg = getColor(R.color.tone_primary_bg),
            canvasBg = getColor(R.color.ui_bg)
        )
    }

    private fun rebuildPaletteStrip(activePalette: String) {
        val strip = findViewById<LinearLayout>(R.id.settings_palette_strip) ?: return
        strip.removeAllViews()
        val swatchSize = UiKit.dpInt(this, 48)
        val itemWidth = UiKit.dpInt(this, 62)
        val itemGap = UiKit.dpInt(this, 4)
        val rippleColor = ColorStateList.valueOf(getColor(R.color.ui_ripple))
        val itemCorner = UiKit.dp(this, 14f)
        val accent = UiKit.primaryColor(this)
        val secondaryText = getColor(R.color.ui_text_secondary)

        val palettes = AppSettings.availableColorPalettes()
        palettes.forEachIndexed { idx, palette ->
            val selected = palette == activePalette
            val colors = palettePreviewColors(palette)
            val fullName = colorPaletteLabel(palette)
            val shortName = colorPaletteShortLabel(palette)

            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                isClickable = true
                isFocusable = true
                isSelected = selected
                tag = palette
                contentDescription = fullName
                setPadding(UiKit.dpInt(this@SettingsActivity, 4), UiKit.dpInt(this@SettingsActivity, 6), UiKit.dpInt(this@SettingsActivity, 4), UiKit.dpInt(this@SettingsActivity, 6))
                val mask = GradientDrawable().apply {
                    cornerRadius = itemCorner
                    setColor(Color.WHITE)
                }
                background = RippleDrawable(rippleColor, null, mask)
                setOnClickListener {
                    if (palette != AppSettings.getColorPalette(this@SettingsActivity)) {
                        AppSettings.setColorPalette(this@SettingsActivity, palette)
                        recreateWithFade()
                    }
                }
            }

            val disc = ImageView(this).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setImageDrawable(
                    UiKit.paletteSwatchDrawable(
                        context = this@SettingsActivity,
                        primary = colors.primary,
                        onPrimary = colors.onPrimary,
                        tonalBg = colors.tonalBg,
                        canvasBg = colors.canvasBg,
                        selected = selected
                    )
                )
            }
            tile.addView(disc, LinearLayout.LayoutParams(swatchSize, swatchSize))

            val label = TextView(this).apply {
                text = shortName
                textSize = 11.5f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (selected) accent else secondaryText)
            }
            tile.addView(
                label,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = UiKit.dpInt(this@SettingsActivity, 5) }
            )

            strip.addView(
                tile,
                LinearLayout.LayoutParams(itemWidth, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    if (idx < palettes.lastIndex) marginEnd = itemGap
                }
            )
        }
    }

    private fun showColorPaletteDialog() {
        val palettes = AppSettings.availableColorPalettes()
        val labels = palettes.map { colorPaletteLabel(it) }.toTypedArray()
        val currentPalette = AppSettings.getColorPalette(this)
        val checkedIdx = palettes.indexOf(currentPalette).coerceAtLeast(0)

        trackDialog(
            AlertDialog.Builder(this)
                .setTitle(R.string.settings_color_palette)
                .setSingleChoiceItems(labels, checkedIdx) { dlg, which ->
                    val nextPalette = palettes.getOrElse(which) { currentPalette }
                    dlg.dismiss()
                    if (nextPalette != currentPalette) {
                        AppSettings.setColorPalette(this, nextPalette)
                        recreateWithFade()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        )
    }

    @Suppress("DEPRECATION")
    private fun recreateWithFade() {
        recreate()
        if (Build.VERSION.SDK_INT < 34) {
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private fun showThemeDialog() {
        val modes = intArrayOf(
            AppSettings.THEME_AUTO,
            AppSettings.THEME_LIGHT,
            AppSettings.THEME_DARK
        )
        val labels = arrayOf(
            getString(R.string.theme_auto),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )
        val currentMode = AppSettings.getThemeMode(this)
        val checkedIdx = modes.indexOf(currentMode).coerceAtLeast(0)

        trackDialog(
            AlertDialog.Builder(this)
                .setTitle(R.string.settings_theme)
                .setSingleChoiceItems(labels, checkedIdx) { dlg, which ->
                    val nextMode = modes.getOrElse(which) { AppSettings.THEME_AUTO }
                    dlg.dismiss()
                    if (nextMode != currentMode) {
                        AppSettings.setThemeMode(this, nextMode)
                        recreateWithFade()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        )
    }

    private fun showLanguageDialog() {
        val codes = arrayOf(
            AppSettings.LANG_AUTO,
            AppSettings.LANG_ZH,
            AppSettings.LANG_JA,
            AppSettings.LANG_EN
        )
        val labels = arrayOf(
            getString(R.string.lang_auto),
            getString(R.string.lang_zh),
            getString(R.string.lang_ja),
            getString(R.string.lang_en)
        )
        val currentLang = AppSettings.getLanguageMode(this)
        val checkedIdx = codes.indexOf(currentLang).coerceAtLeast(0)

        trackDialog(
            AlertDialog.Builder(this)
                .setTitle(R.string.settings_language)
                .setSingleChoiceItems(labels, checkedIdx) { dlg, which ->
                    val nextLang = codes.getOrElse(which) { AppSettings.LANG_AUTO }
                    dlg.dismiss()
                    if (nextLang != currentLang) {
                        AppSettings.setLanguageMode(this, nextLang)
                        recreate()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        )
    }

    private fun downloadConcurrencyLabel(count: Int): String =
        getString(R.string.download_concurrency_value, AppSettings.normalizeDownloadConcurrency(count))

    private fun showDownloadConcurrencyDialog() {
        val minVal = AppSettings.MIN_DOWNLOAD_CONCURRENCY
        val maxVal = AppSettings.MAX_DOWNLOAD_CONCURRENCY
        var selected = AppSettings.getDownloadConcurrency(this)
        val density = resources.displayMetrics.density
        val padH = (24 * density).toInt()
        val padV = (14 * density).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padV, padH, padV / 2)
        }
        val valueView = TextView(this).apply {
            text = downloadConcurrencyLabel(selected)
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_HORIZONTAL
            setTextColor(getColor(R.color.ui_text_primary))
        }
        val seekBar = SeekBar(this).apply {
            max = maxVal - minVal
            progress = selected - minVal
            UiKit.tintSeekBar(this, UiKit.primaryColor(this@SettingsActivity))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    selected = AppSettings.normalizeDownloadConcurrency(minVal + progress)
                    valueView.text = downloadConcurrencyLabel(selected)
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        val ticksRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (2 * density).toInt(), 0, (10 * density).toInt())
        }
        val tickColor = getColor(R.color.ui_text_tertiary)
        ticksRow.addView(TextView(this).apply {
            text = minVal.toString()
            textSize = 12f
            setTextColor(tickColor)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        ticksRow.addView(TextView(this).apply {
            text = AppSettings.DEFAULT_DOWNLOAD_CONCURRENCY.toString()
            textSize = 12f
            gravity = Gravity.CENTER_HORIZONTAL
            setTextColor(tickColor)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        ticksRow.addView(TextView(this).apply {
            text = maxVal.toString()
            textSize = 12f
            gravity = Gravity.END
            setTextColor(tickColor)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        val summaryView = TextView(this).apply {
            text = getString(R.string.download_concurrency_summary)
            textSize = 13f
            setTextColor(getColor(R.color.ui_text_secondary))
        }
        container.addView(valueView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (10 * density).toInt() })
        container.addView(seekBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        container.addView(ticksRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        container.addView(summaryView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        trackDialog(
            AlertDialog.Builder(this)
                .setTitle(R.string.settings_download_concurrency)
                .setView(container)
                .setPositiveButton(R.string.ok) { _, _ ->
                    AppSettings.setDownloadConcurrency(this, selected)
                    refreshSettingsUi()
                }
                .setNeutralButton(R.string.download_concurrency_reset) { _, _ ->
                    AppSettings.setDownloadConcurrency(this, AppSettings.DEFAULT_DOWNLOAD_CONCURRENCY)
                    refreshSettingsUi()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        )
    }

    private fun onOnlineInstallClicked() {
        val currentlyEnabled = AppSettings.isOnlineInstallEnabled(this)
        if (currentlyEnabled) {
            AppSettings.setOnlineInstallEnabled(this, false)
            refreshSettingsUi()
            return
        }

        trackDialog(
            AlertDialog.Builder(this)
                .setTitle(R.string.online_disclaimer_title)
                .setMessage(buildOnlineDisclaimerText())
                .setPositiveButton(R.string.online_disclaimer_agree) { _, _ ->
                    AppSettings.setOnlineInstallEnabled(this, true)
                    refreshSettingsUi()
                }
                .setNegativeButton(R.string.cancel) { _, _ ->
                    refreshSettingsUi()
                }
                .setOnCancelListener {
                    refreshSettingsUi()
                }
                .show()
                .applyOnlineDisclaimerTypography()
        )
    }
}

internal fun Context.buildOnlineDisclaimerText(): CharSequence {
    val raw = getString(R.string.online_disclaimer_msg)
    val lines = raw.split('\n')
    val sb = SpannableStringBuilder()
    val headingColor = getColor(R.color.ui_text_primary)
    for ((index, line) in lines.withIndex()) {
        if (index > 0) sb.append('\n')
        val start = sb.length
        sb.append(line)
        val end = sb.length
        if (line.startsWith("第") || line.startsWith("Article ")) {
            sb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(ForegroundColorSpan(headingColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    return sb
}

internal fun AlertDialog.applyOnlineDisclaimerTypography(): AlertDialog = apply {
    findViewById<TextView>(android.R.id.message)?.apply {
        textSize = 13.5f
        setLineSpacing(0f, 1.28f)
    }
}
