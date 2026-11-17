package io.github.kichikuou.xsystem4

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
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
import android.widget.LinearLayout
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var clearingCache: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<View>(R.id.settings_general_card).clipToOutline = true
        findViewById<View>(R.id.settings_about_card).clipToOutline = true

        val accent = getColor(R.color.ui_primary)
        findViewById<ImageView>(R.id.icon_settings_theme)
            .setImageDrawable(UiKit.tonalIcon(this, R.drawable.ic_theme, accent))
        findViewById<ImageView>(R.id.icon_settings_language)
            .setImageDrawable(UiKit.tonalIcon(this, R.drawable.ic_language, accent))
        findViewById<ImageView>(R.id.icon_settings_online)
            .setImageDrawable(UiKit.tonalIcon(this, R.drawable.ic_cloud_download, accent))
        findViewById<ImageView>(R.id.icon_settings_gamepad)
            .setImageDrawable(UiKit.tonalIcon(this, R.drawable.ic_gamepad, accent))
        findViewById<ImageView>(R.id.icon_settings_clear_cache)
            .setImageDrawable(UiKit.tonalIcon(this, R.drawable.ic_clear_cache, accent))

        val btnQqGroup = findViewById<Button>(R.id.btn_settings_qq_group)
        val btnLicenses = findViewById<Button>(R.id.btn_settings_licenses)
        val btnGithub = findViewById<Button>(R.id.btn_settings_github)
        UiKit.styleActionRow(btnQqGroup, R.drawable.ic_qq_group, accent)
        UiKit.styleActionRow(btnLicenses, R.drawable.ic_licenses, accent)
        UiKit.styleActionRow(btnGithub, R.drawable.ic_github, accent)

        findViewById<View>(R.id.row_settings_theme).setOnClickListener {
            showThemeDialog()
        }
        findViewById<View>(R.id.row_settings_language).setOnClickListener {
            showLanguageDialog()
        }
        findViewById<View>(R.id.row_settings_online_install).setOnClickListener {
            onOnlineInstallClicked()
        }
        findViewById<View>(R.id.row_settings_gamepad).setOnClickListener {
            startActivity(Intent(this, GamepadSettingsActivity::class.java))
        }
        findViewById<View>(R.id.row_settings_clear_cache).setOnClickListener {
            onClearCacheClicked()
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
    }

    override fun onDestroy() {
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
        val lang = AppSettings.getLanguageMode(this)
        findViewById<TextView>(R.id.value_settings_language).text = when (lang) {
            AppSettings.LANG_ZH -> getString(R.string.lang_zh)
            AppSettings.LANG_JA -> getString(R.string.lang_ja)
            AppSettings.LANG_EN -> getString(R.string.lang_en)
            else -> getString(R.string.lang_auto)
        }
        findViewById<Switch>(R.id.switch_online_install).isChecked =
            AppSettings.isOnlineInstallEnabled(this)
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
            val bytes = withContext(Dispatchers.IO) {
                computeTotalCacheBytes(appCtx)
            }
            findViewById<TextView>(R.id.value_settings_clear_cache)?.text = formatCacheSize(bytes)
        }
    }

    private fun onClearCacheClicked() {
        if (clearingCache) return
        val gameList = LauncherActivity.obtainGameList(this, refresh = false)
        if (gameList.installState is InstallState.Installing) {
            Toast.makeText(this, R.string.settings_clear_cache_busy_toast, Toast.LENGTH_SHORT).show()
            return
        }
        val appCtx = applicationContext
        scope.launch {
            val totalBytes = withContext(Dispatchers.IO) {
                computeTotalCacheBytes(appCtx)
            }
            findViewById<TextView>(R.id.value_settings_clear_cache)?.text = formatCacheSize(totalBytes)
            if (totalBytes <= 0L) {
                Toast.makeText(
                    this@SettingsActivity,
                    R.string.settings_clear_cache_empty_toast,
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            val sizeLabel = formatCacheSize(totalBytes)
            AlertDialog.Builder(this@SettingsActivity)
                .setTitle(R.string.settings_clear_cache)
                .setMessage(getString(R.string.settings_clear_cache_confirm_msg, sizeLabel))
                .setPositiveButton(R.string.ok) { _, _ ->
                    performClearCache(totalBytes)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun performClearCache(expectedBytes: Long) {
        if (clearingCache) return
        val gameList = LauncherActivity.obtainGameList(this, refresh = false)
        if (gameList.installState is InstallState.Installing) {
            Toast.makeText(this, R.string.settings_clear_cache_busy_toast, Toast.LENGTH_SHORT).show()
            return
        }
        clearingCache = true
        val appCtx = applicationContext
        scope.launch {
            val remainingBytes = try {
                withContext(Dispatchers.IO) {
                    clearAllCacheTargets(appCtx)
                    computeTotalCacheBytes(appCtx)
                }
            } finally {
                clearingCache = false
            }
            findViewById<TextView>(R.id.value_settings_clear_cache)?.text = formatCacheSize(remainingBytes)
            val freed = (expectedBytes - remainingBytes).coerceAtLeast(0L)
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

    private fun collectCacheTargets(context: Context): List<File> {
        val out = LinkedHashSet<File>()
        context.cacheDir?.listFiles()?.forEach { out.add(it) }
        context.externalCacheDirs?.filterNotNull()?.forEach { dir ->
            dir.listFiles()?.forEach { out.add(it) }
        }
        context.filesDir.listFiles()?.filter { it.name.startsWith(SAVE_IMPORT_TMP_PREFIX) }
            ?.forEach { out.add(it) }

        val storageRoots = LinkedHashSet<File>()
        context.getExternalFilesDirs(null)?.filterNotNull()?.forEach { storageRoots.add(it) }
        val internalGames = File(context.filesDir, "games")
        if (internalGames.isDirectory) {
            storageRoots.add(internalGames)
        }

        for (root in storageRoots) {
            val children = root.listFiles() ?: continue
            for (f in children) {
                val n = f.name
                val isTempTarget = n.startsWith(".online_base_staging_") ||
                    n == ".online_mod_cache" ||
                    n.startsWith(".mod_online_extract_temp") ||
                    n.startsWith(".install_temp") ||
                    n.startsWith(".mod_install_temp") ||
                    n.startsWith(".trash_") ||
                    n.startsWith(".import-") ||
                    n.startsWith(".mod-import-") ||
                    (f.isFile && n.startsWith(".archive_src_") && n.endsWith(".tmp"))
                if (isTempTarget) {
                    out.add(f)
                }
            }
        }
        return out.toList()
    }

    private fun computeTotalCacheBytes(context: Context): Long {
        var sum = 0L
        for (target in collectCacheTargets(context)) {
            sum += computeFileTreeBytes(target)
        }
        return sum
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

    private fun clearAllCacheTargets(context: Context) {
        for (target in collectCacheTargets(context)) {
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
                setColor(getColor(R.color.ui_surface_dialog))
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
            navigationBarColor = getColor(R.color.ui_surface_dialog)
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

        AlertDialog.Builder(this)
            .setTitle(R.string.settings_theme)
            .setSingleChoiceItems(labels, checkedIdx) { dlg, which ->
                val nextMode = modes.getOrElse(which) { AppSettings.THEME_AUTO }
                dlg.dismiss()
                if (nextMode != currentMode) {
                    AppSettings.setThemeMode(this, nextMode)
                    recreate()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
    }

    private fun onOnlineInstallClicked() {
        val currentlyEnabled = AppSettings.isOnlineInstallEnabled(this)
        if (currentlyEnabled) {
            AppSettings.setOnlineInstallEnabled(this, false)
            refreshSettingsUi()
            return
        }

        val dlg = AlertDialog.Builder(this)
            .setTitle(R.string.online_disclaimer_title)
            .setMessage(buildDisclaimerText())
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
        dlg.findViewById<TextView>(android.R.id.message)?.apply {
            textSize = 13.5f
            setLineSpacing(0f, 1.28f)
        }
    }

    private fun buildDisclaimerText(): CharSequence {
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
}
