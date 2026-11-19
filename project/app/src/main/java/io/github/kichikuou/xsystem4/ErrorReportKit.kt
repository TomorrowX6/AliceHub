package io.github.kichikuou.xsystem4

import android.app.Activity
import android.app.ActivityManager
import android.app.Dialog
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.text.TextUtils
import android.util.Log
import android.util.SparseArray
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.PrintWriter
import java.io.RandomAccessFile
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

internal data class ErrorDialogButtonSpec(
    val id: Int,
    val label: String,
    val isPrimary: Boolean,
    val isEscape: Boolean,
    val isReturnDefault: Boolean
)

internal object ErrorReportKit {
    const val REQ_EXPORT_ERROR_REPORT = 9208

    private const val TAG = "ErrorReportKit"
    private const val SEEN_MTIME_FILE = ".crash_seen_mtime"
    private const val MAX_EXPORT_BYTES = 512 * 1024
    private const val MAX_DISPLAY_CHARS = 64 * 1024
    private const val STALE_SHARE_MS = 24L * 60L * 60L * 1000L

    enum class ErrorKind(val labelRes: Int) {
        FATAL(R.string.error_report_kind_fatal),
        SIGNAL(R.string.error_report_kind_signal),
        SCRIPT(R.string.error_report_kind_script),
        RUNTIME(R.string.error_report_kind_runtime)
    }

    data class ReportData(
        val kind: ErrorKind,
        val subtitle: String,
        val summary: String,
        val displayLogText: String,
        val exportText: String,
        val totalLines: Int,
        val totalBytes: Long,
        val suggestedFileName: String
    )

    @Volatile
    private var uncaughtHandlerInstalled: Boolean = false

    @Volatile
    private var pendingExportPayload: String? = null

    private var activeStandaloneDialog: Dialog? = null

    private val epochLineRegex = Regex("""^(Timestamp\s*:\s*)epoch:(\d+)\s*$""", RegexOption.MULTILINE)
    private val whitespaceRegex = Regex("""\s+""")

    fun installUncaughtExceptionHandler(context: Context) {
        if (uncaughtHandlerInstalled) return
        synchronized(this) {
            if (uncaughtHandlerInstalled) return
            val appCtx = context.applicationContext
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    writeJvmCrashLog(appCtx, thread, throwable)
                } catch (_: Throwable) {
                } finally {
                    if (previous != null) {
                        previous.uncaughtException(thread, throwable)
                    } else {
                        Process.killProcess(Process.myPid())
                    }
                }
            }
            uncaughtHandlerInstalled = true
        }
    }

    fun ensureSeenBaselineInitialized(context: Context, offsetMs: Long = 0L) {
        val marker = File(context.filesDir, SEEN_MTIME_FILE)
        if (!marker.exists()) {
            writeSeenCrashMtime(context, System.currentTimeMillis() + offsetMs)
        }
    }

    fun checkAndShowPendingCrash(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (activeStandaloneDialog?.isShowing == true) return
        if (isGameProcessRunning(activity)) return

        val marker = File(activity.filesDir, SEEN_MTIME_FILE)
        if (!marker.exists()) {
            writeSeenCrashMtime(activity, System.currentTimeMillis())
            return
        }
        val seenMtime = readSeenCrashMtime(activity)
        val candidates = collectCrashLogCandidates(activity)
        var newestFile: File? = null
        var newestMtime = 0L
        for (f in candidates) {
            if (f.isFile && f.length() > 0L) {
                val m = f.lastModified()
                if (m > newestMtime) {
                    newestMtime = m
                    newestFile = f
                }
            }
        }
        val targetFile = newestFile ?: return
        if (newestMtime <= seenMtime) return

        writeSeenCrashMtime(activity, maxOf(System.currentTimeMillis(), newestMtime))
        val report = buildFromLogFile(activity, targetFile)
        showReportDialog(activity, report)
    }

    fun showFromFile(activity: Activity, file: File) {
        if (!file.isFile) return
        val report = buildFromLogFile(activity, file)
        showReportDialog(activity, report)
    }

    fun shareFromFile(activity: Activity, file: File) {
        if (!file.isFile) return
        val report = buildFromLogFile(activity, file)
        shareReport(activity, report)
    }

    fun buildFromGameMessageBox(
        context: Context,
        rawTitle: String,
        rawMessage: String,
        gameRoot: String?,
        saveDir: String?,
        hasMultipleChoices: Boolean
    ): ReportData {
        val lctx = AppSettings.wrapThemeContext(context)
        val now = System.currentTimeMillis()
        val seenMtime = readSeenCrashMtime(lctx)

        val crashCandidates = ArrayList<File>(4)
        if (!saveDir.isNullOrBlank()) {
            crashCandidates.add(File(saveDir, "crash.log"))
        }
        if (!gameRoot.isNullOrBlank()) {
            crashCandidates.add(File(gameRoot, "crash.log"))
        }
        lctx.getExternalFilesDir(null)?.let { crashCandidates.add(File(it, "crash.log")) }

        val mentionsSavedCrash = rawMessage.contains("Crash log saved to:", ignoreCase = true)
        val freshFile = crashCandidates
            .filter { it.isFile && it.length() > 0L }
            .maxByOrNull { it.lastModified() }
            ?.takeIf { f ->
                val m = f.lastModified()
                (mentionsSavedCrash && abs(now - m) < 60_000L) ||
                    (m > seenMtime && abs(now - m) < 15_000L)
            }

        val maxObservedMtime = crashCandidates
            .filter { it.isFile }
            .maxOfOrNull { it.lastModified() } ?: 0L
        writeSeenCrashMtime(lctx, maxOf(now + 1500L, maxObservedMtime))

        val gameTitle = resolveGameDisplayLabel(lctx, gameRoot, saveDir)

        if (freshFile != null) {
            return buildFromLogFile(lctx, freshFile, overrideGameLabel = gameTitle)
        }

        val tsHuman = formatHumanTime(now)
        val tsFile = formatFileTime(now)
        val kind = if (hasMultipleChoices || rawMessage.contains("0:", ignoreCase = false)) {
            ErrorKind.SCRIPT
        } else if (rawTitle.contains("Fatal", ignoreCase = true)) {
            ErrorKind.FATAL
        } else {
            ErrorKind.RUNTIME
        }
        val kindEn = when (kind) {
            ErrorKind.FATAL -> "FATAL ERROR"
            ErrorKind.SIGNAL -> "FATAL SIGNAL"
            ErrorKind.SCRIPT -> "SCRIPT ERROR"
            ErrorKind.RUNTIME -> "RUNTIME ERROR"
        }

        val cleanedRawMsg = stripSavedPathNotice(rawMessage).trim().ifEmpty { "Unknown runtime error" }
        val synthesized = buildString {
            append("============================================================\n")
            append("AliceHub Error Report\n")
            append("============================================================\n")
            append("Timestamp   : ").append(tsHuman).append('\n')
            append("App         : AliceHub ").append(appVersionName(lctx)).append('\n')
            append("Device      : ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" · Android ").append(Build.VERSION.RELEASE)
                .append(" · SDK ").append(Build.VERSION.SDK_INT).append('\n')
            if (!gameTitle.isNullOrBlank()) {
                append("Game        : ").append(gameTitle).append('\n')
            }
            if (!gameRoot.isNullOrBlank()) {
                append("Game Root   : ").append(gameRoot).append('\n')
            }
            append("Crash Type  : ").append(kindEn).append('\n')
            append("\n[Error Details]\n")
            append(cleanedRawMsg).append('\n')
            append("============================================================\n")
        }

        val exportBytes = synthesized.toByteArray(Charsets.UTF_8).size.toLong()
        val lineCount = synthesized.lineSequence().count().coerceAtLeast(1)
        val summary = extractSummary(cleanedRawMsg, synthesized, lctx.getString(kind.labelRes))
        val subtitle = if (!gameTitle.isNullOrBlank()) "$gameTitle · $tsHuman" else tsHuman

        return ReportData(
            kind = kind,
            subtitle = sanitizeChromeText(subtitle),
            summary = summary,
            displayLogText = synthesized.trimEnd(),
            exportText = synthesized,
            totalLines = lineCount,
            totalBytes = exportBytes,
            suggestedFileName = "AliceHub_ErrorReport_${tsFile}.txt"
        )
    }

    fun buildFromLogFile(
        context: Context,
        file: File,
        overrideGameLabel: String? = null
    ): ReportData {
        val lctx = AppSettings.wrapThemeContext(context)
        val fallbackMs = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
        val rawExport = readFileTailUtf8(file, MAX_EXPORT_BYTES)
        val normalized = normalizeCrashLogText(rawExport, fallbackMs)
        val displayLog = if (normalized.length > MAX_DISPLAY_CHARS) {
            "…\n" + normalized.takeLast(MAX_DISPLAY_CHARS)
        } else {
            normalized.trimEnd()
        }

        val kind = detectErrorKind(normalized)
        val parsedTs = extractHeaderField(normalized, "Timestamp")
            ?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
            ?: formatHumanTime(fallbackMs)

        val inferredGame = overrideGameLabel ?: inferGameLabelFromFileOrLog(lctx, file, normalized)
        val subtitle = if (!inferredGame.isNullOrBlank()) {
            "$inferredGame · $parsedTs"
        } else {
            parsedTs
        }

        val summary = extractSummary(null, normalized, lctx.getString(kind.labelRes))
        val totalBytes = file.length().coerceAtLeast(normalized.toByteArray(Charsets.UTF_8).size.toLong())
        val lineCount = normalized.lineSequence().count().coerceAtLeast(1)
        val fileTs = formatFileTime(fallbackMs)

        return ReportData(
            kind = kind,
            subtitle = sanitizeChromeText(subtitle),
            summary = summary,
            displayLogText = displayLog,
            exportText = normalized,
            totalLines = lineCount,
            totalBytes = totalBytes,
            suggestedFileName = "AliceHub_ErrorReport_${fileTs}.txt"
        )
    }

    fun showReportDialog(activity: Activity, report: ReportData) {
        if (activity.isFinishing || activity.isDestroyed) return
        activeStandaloneDialog?.let { prev ->
            if (prev.isShowing) {
                try {
                    prev.dismiss()
                } catch (_: Throwable) {
                }
            }
        }
        val uiCtx = ContextThemeWrapper(AppSettings.wrapThemeContext(activity), R.style.Theme_Launcher)
        val closeSpec = ErrorDialogButtonSpec(
            id = 0,
            label = uiCtx.getString(R.string.error_report_btn_close),
            isPrimary = true,
            isEscape = true,
            isReturnDefault = true
        )
        val dlg = createAndShowDialog(
            activity = activity,
            uiCtx = uiCtx,
            report = report,
            decisionSpecs = listOf(closeSpec),
            dangerPrimary = false,
            immersiveMatchWindow = false,
            onSelected = { _ -> },
            onDismissed = { closedDlg ->
                if (activeStandaloneDialog === closedDlg) {
                    activeStandaloneDialog = null
                }
            }
        )
        activeStandaloneDialog = dlg
    }

    fun showInGameErrorDialog(
        activity: Activity,
        report: ReportData,
        decisionSpecs: List<ErrorDialogButtonSpec>,
        onSelected: (Int) -> Unit,
        onDismissed: () -> Unit
    ): Dialog {
        val uiCtx = ContextThemeWrapper(AppSettings.wrapThemeContext(activity), R.style.Theme_Launcher)
        val specs = decisionSpecs.ifEmpty {
            listOf(
                ErrorDialogButtonSpec(
                    id = 0,
                    label = uiCtx.getString(R.string.error_report_btn_close),
                    isPrimary = true,
                    isEscape = true,
                    isReturnDefault = true
                )
            )
        }
        return createAndShowDialog(
            activity = activity,
            uiCtx = uiCtx,
            report = report,
            decisionSpecs = specs,
            dangerPrimary = true,
            immersiveMatchWindow = true,
            onSelected = onSelected,
            onDismissed = { onDismissed() }
        )
    }

    fun startExport(activity: Activity, report: ReportData) {
        val lctx = AppSettings.wrapThemeContext(activity)
        try {
            pendingExportPayload = report.exportText
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, report.suggestedFileName)
            }
            activity.startActivityForResult(intent, REQ_EXPORT_ERROR_REPORT)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to launch SAF create document for error report", t)
            pendingExportPayload = null
            Toast.makeText(
                lctx,
                R.string.error_report_export_failed_toast,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun handleActivityResult(
        activity: Activity,
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ): Boolean {
        if (requestCode != REQ_EXPORT_ERROR_REPORT) return false
        val payload = pendingExportPayload
        pendingExportPayload = null
        if (resultCode != Activity.RESULT_OK || data?.data == null || payload == null) {
            return true
        }
        val destUri = data.data ?: return true
        val appCtx = activity.applicationContext
        Thread {
            val ok = try {
                appCtx.contentResolver.openOutputStream(destUri)?.use { out ->
                    out.write(payload.toByteArray(Charsets.UTF_8))
                    out.flush()
                } != null
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to export error report", t)
                false
            }
            Handler(Looper.getMainLooper()).post {
                val lctx = AppSettings.wrapThemeContext(appCtx)
                Toast.makeText(
                    lctx,
                    if (ok) R.string.error_report_exported_toast else R.string.error_report_export_failed_toast,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }.start()
        return true
    }

    fun shareReport(activity: Activity, report: ReportData) {
        val lctx = AppSettings.wrapThemeContext(activity)
        try {
            val sharedDir = File(activity.cacheDir, ErrorReportProvider.SHARED_DIR_NAME)
            sharedDir.mkdirs()
            val cutoff = System.currentTimeMillis() - STALE_SHARE_MS
            sharedDir.listFiles()?.forEach { f ->
                if (f.isFile && f.lastModified() < cutoff) {
                    f.delete()
                }
            }
            val safeName = report.suggestedFileName
                .replace(Regex("""[^A-Za-z0-9._\-]"""), "_")
                .let { if (it.endsWith(".txt", ignoreCase = true)) it else "$it.txt" }
            val outFile = File(sharedDir, safeName)
            outFile.writeText(report.exportText, Charsets.UTF_8)

            val uri = Uri.Builder()
                .scheme("content")
                .authority(ErrorReportProvider.authorityFor(activity.packageName))
                .appendPath(outFile.name)
                .build()

            val clip = ClipData.newRawUri(outFile.name, uri)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, outFile.name.removeSuffix(".txt"))
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = clip
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(
                sendIntent,
                lctx.getString(R.string.error_report_btn_share)
            ).apply {
                clipData = clip
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(chooser)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to share error report", t)
            Toast.makeText(
                lctx,
                R.string.error_report_share_failed_toast,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun createAndShowDialog(
        activity: Activity,
        uiCtx: Context,
        report: ReportData,
        decisionSpecs: List<ErrorDialogButtonSpec>,
        dangerPrimary: Boolean,
        immersiveMatchWindow: Boolean,
        onSelected: (Int) -> Unit,
        onDismissed: (Dialog) -> Unit
    ): Dialog {
        fun dp(v: Int): Int = UiKit.dpInt(uiCtx, v)

        val dm = uiCtx.resources.displayMetrics
        val isCompactLandscape = dm.heightPixels < dp(460)
        val isWideScreen = dm.widthPixels > dm.heightPixels

        val cardWidth = if (isWideScreen) {
            (dm.widthPixels * 0.78f).toInt()
                .coerceIn(dp(340), dp(580))
                .coerceAtMost((dm.widthPixels - dp(32)).coerceAtLeast(dp(280)))
        } else {
            (dm.widthPixels - dp(32)).coerceIn(dp(276), dp(440))
        }

        val maxLogHeight = if (isCompactLandscape) {
            (dm.heightPixels - dp(218)).coerceIn(dp(88), dp(175))
        } else {
            (dm.heightPixels * 0.35f).toInt().coerceIn(dp(150), dp(310))
        }

        val padH = if (isCompactLandscape) dp(18) else dp(20)
        val padTop = if (isCompactLandscape) dp(15) else dp(20)
        val padBottom = if (isCompactLandscape) dp(14) else dp(18)
        val sectionGap = if (isCompactLandscape) dp(10) else dp(13)

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val keyMapping = SparseArray<Button>()
        var escapeButtonId: Int? = null
        var selectionMade = false

        val cardRadius = uiCtx.resources.getDimension(R.dimen.ui_radius_dialog)
        val dangerColor = uiCtx.getColor(R.color.tone_danger_fg)
        val cardSurface = GradientDrawable().apply {
            cornerRadius = cardRadius
            setColor(uiCtx.getColor(R.color.ui_surface_dialog))
            setStroke(dp(1), uiCtx.getColor(R.color.ui_outline))
        }
        val topWashAlpha = if (UiKit.isNight(uiCtx)) 0x26 else 0x16
        val topWash = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                UiKit.withAlpha(dangerColor, topWashAlpha),
                UiKit.withAlpha(dangerColor, 0),
                UiKit.withAlpha(dangerColor, 0)
            )
        ).apply {
            cornerRadius = cardRadius
        }

        val card = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padTop, padH, padBottom)
            elevation = dp(22).toFloat()
            background = LayerDrawable(arrayOf(cardSurface, topWash))
            clipToOutline = true
            isClickable = true
        }

        // 1. Header row: Tonal danger icon + Title/Subtitle + ErrorKind chip
        val headerRow = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val badgePlateSize = if (isCompactLandscape) dp(36) else dp(40)
        val badgeIconSize = if (isCompactLandscape) dp(19) else dp(21)
        val iconBadge = View(uiCtx).apply {
            val plate = GradientDrawable().apply {
                cornerRadius = UiKit.dp(uiCtx, 12f)
                setColor(uiCtx.getColor(R.color.tone_danger_bg))
            }
            val ic = uiCtx.getDrawable(R.drawable.ic_error_report)!!.mutate().apply {
                setTint(dangerColor)
            }
            background = LayerDrawable(arrayOf(plate, ic)).apply {
                setLayerSize(1, badgeIconSize, badgeIconSize)
                setLayerGravity(1, Gravity.CENTER)
            }
        }
        headerRow.addView(iconBadge, LinearLayout.LayoutParams(badgePlateSize, badgePlateSize))

        val titleCol = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.VERTICAL
        }
        val titleView = TextView(uiCtx).apply {
            text = uiCtx.getString(R.string.error_report_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isCompactLandscape) 16.5f else 18f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(uiCtx.getColor(R.color.ui_text_primary))
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        titleCol.addView(titleView)

        if (report.subtitle.isNotBlank()) {
            val subView = TextView(uiCtx).apply {
                text = report.subtitle
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setTextColor(uiCtx.getColor(R.color.ui_text_secondary))
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
            }
            titleCol.addView(
                subView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(2)
                }
            )
        }

        headerRow.addView(
            titleCol,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
                marginEnd = dp(8)
            }
        )

        val mediumFont = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val kindChip = TextView(uiCtx).apply {
            text = uiCtx.getString(report.kind.labelRes)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = mediumFont
            setPadding(dp(9), dp(4), dp(10), dp(4))
            isSingleLine = true
        }
        UiKit.styleChip(kindChip, UiKit.Tone.DANGER, dot = true)
        headerRow.addView(
            kindChip,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        card.addView(
            headerRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // 2. Concise error summary callout banner
        if (report.summary.isNotBlank()) {
            val summaryBox = TextView(uiCtx).apply {
                text = report.summary
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = mediumFont
                setTextColor(dangerColor)
                setLineSpacing(UiKit.dp(uiCtx, 2f), 1.12f)
                maxLines = if (isCompactLandscape) 2 else 3
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = GradientDrawable().apply {
                    cornerRadius = UiKit.dp(uiCtx, 12f)
                    setColor(
                        UiKit.withAlpha(
                            uiCtx.getColor(R.color.tone_danger_bg),
                            if (UiKit.isNight(uiCtx)) 0xB8 else 0xDC
                        )
                    )
                    setStroke(dp(1), UiKit.withAlpha(dangerColor, 0x2E))
                }
            }
            card.addView(
                summaryBox,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = sectionGap
                }
            )
        }

        // 3. Framed monospace diagnostic log card
        val logCard = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(uiCtx, 14f)
                setColor(uiCtx.getColor(R.color.ui_bg))
                setStroke(dp(1), uiCtx.getColor(R.color.ui_outline))
            }
            clipToOutline = true
        }

        val logHeader = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        val logTitle = TextView(uiCtx).apply {
            text = uiCtx.getString(R.string.error_report_details_label)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(uiCtx.getColor(R.color.ui_text_secondary))
        }
        val logMeta = TextView(uiCtx).apply {
            text = uiCtx.getString(
                R.string.error_report_lines_meta,
                report.totalLines,
                OnlineInstaller.formatSize(report.totalBytes).ifEmpty { "${report.totalBytes} B" }
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(uiCtx.getColor(R.color.ui_text_tertiary))
        }
        logHeader.addView(logTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        logHeader.addView(logMeta)
        logCard.addView(logHeader)

        val logDivider = View(uiCtx).apply {
            setBackgroundColor(uiCtx.getColor(R.color.ui_divider))
        }
        logCard.addView(logDivider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))

        val logTextView = TextView(uiCtx).apply {
            text = report.displayLogText
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTextColor(uiCtx.getColor(R.color.ui_text_primary))
            setLineSpacing(UiKit.dp(uiCtx, 2f), 1.15f)
            setPadding(dp(12), dp(9), dp(12), dp(10))
            setTextIsSelectable(true)
        }

        val logScroll = object : ScrollView(uiCtx) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val cappedSpec = MeasureSpec.makeMeasureSpec(maxLogHeight, MeasureSpec.AT_MOST)
                super.onMeasure(widthMeasureSpec, cappedSpec)
            }
        }.apply {
            isVerticalScrollBarEnabled = true
            isScrollbarFadingEnabled = false
            addView(
                logTextView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        logCard.addView(
            logScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        card.addView(
            logCard,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = sectionGap
            }
        )

        // 4. Action buttons: Export + Share + Close/Decision(s)
        val btnMinH = if (isCompactLandscape) dp(38) else dp(40)

        fun buildUtilityButton(
            iconRes: Int,
            labelRes: Int,
            fgColor: Int,
            bgColor: Int,
            onClick: () -> Unit
        ): Button {
            val shape = GradientDrawable().apply {
                cornerRadius = UiKit.dp(uiCtx, 12f)
                setColor(bgColor)
            }
            val ripple = RippleDrawable(
                ColorStateList.valueOf(uiCtx.getColor(R.color.ui_ripple)),
                shape,
                null
            )
            return Button(uiCtx).apply {
                text = UiKit.iconLabel(
                    uiCtx,
                    iconRes,
                    fgColor,
                    uiCtx.getString(labelRes),
                    iconDp = 16,
                    gapDp = 5
                )
                isAllCaps = false
                isSingleLine = true
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                typeface = mediumFont
                setTextColor(fgColor)
                gravity = Gravity.CENTER
                minWidth = 0
                minimumWidth = 0
                minHeight = btnMinH
                minimumHeight = btnMinH
                elevation = 0f
                stateListAnimator = null
                setPadding(dp(12), dp(6), dp(13), dp(6))
                background = ripple
                setOnClickListener { onClick() }
            }
        }

        val exportBtn = buildUtilityButton(
            iconRes = R.drawable.ic_export,
            labelRes = R.string.error_report_btn_export,
            fgColor = uiCtx.getColor(R.color.ui_text_primary),
            bgColor = uiCtx.getColor(R.color.ui_surface_variant),
            onClick = { startExport(activity, report) }
        )

        val shareBtn = buildUtilityButton(
            iconRes = R.drawable.ic_share,
            labelRes = R.string.error_report_btn_share,
            fgColor = uiCtx.getColor(R.color.tone_primary_fg),
            bgColor = uiCtx.getColor(R.color.tone_primary_bg),
            onClick = { shareReport(activity, report) }
        )

        fun buildDecisionButton(spec: ErrorDialogButtonSpec): Button {
            return Button(uiCtx).apply {
                text = spec.label
                isAllCaps = false
                isSingleLine = true
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                typeface = mediumFont
                gravity = Gravity.CENTER
                minWidth = dp(68)
                minimumWidth = dp(68)
                minHeight = btnMinH
                minimumHeight = btnMinH
                elevation = 0f
                stateListAnimator = null
                setPadding(dp(15), dp(6), dp(15), dp(6))
                if (spec.isPrimary) {
                    if (dangerPrimary) {
                        background = uiCtx.getDrawable(R.drawable.bg_btn_danger)?.mutate()
                        setTextColor(uiCtx.getColor(R.color.tone_danger_fg))
                    } else {
                        background = uiCtx.getDrawable(R.drawable.bg_btn_filled)?.mutate()
                        setTextColor(uiCtx.getColor(R.color.ui_on_primary))
                    }
                } else {
                    background = uiCtx.getDrawable(R.drawable.bg_btn_tonal)?.mutate()
                    setTextColor(uiCtx.getColor(R.color.ui_text_primary))
                }
                setOnClickListener {
                    if (!selectionMade) {
                        selectionMade = true
                        onSelected(spec.id)
                    }
                    dialog.dismiss()
                }
            }.also { btn ->
                if (spec.isReturnDefault || spec.isPrimary) {
                    keyMapping.put(KeyEvent.KEYCODE_ENTER, btn)
                    keyMapping.put(KeyEvent.KEYCODE_NUMPAD_ENTER, btn)
                }
                if (spec.isEscape) {
                    escapeButtonId = spec.id
                    keyMapping.put(KeyEvent.KEYCODE_ESCAPE, btn)
                    keyMapping.put(KeyEvent.KEYCODE_BACK, btn)
                }
            }
        }

        val stackRows = decisionSpecs.size >= 2 && cardWidth < dp(410)
        if (stackRows) {
            val topToolsRow = LinearLayout(uiCtx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            topToolsRow.addView(
                exportBtn,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            topToolsRow.addView(
                shareBtn,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(8)
                }
            )
            card.addView(
                topToolsRow,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = sectionGap + dp(2)
                }
            )

            val bottomDecisionRow = LinearLayout(uiCtx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            decisionSpecs.forEachIndexed { idx, spec ->
                val btn = buildDecisionButton(spec)
                bottomDecisionRow.addView(
                    btn,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (idx > 0) marginStart = dp(8)
                    }
                )
            }
            card.addView(
                bottomDecisionRow,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(8)
                }
            )
        } else {
            val actionRow = LinearLayout(uiCtx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            actionRow.addView(
                exportBtn,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            actionRow.addView(
                shareBtn,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dp(8)
                }
            )
            actionRow.addView(
                Space(uiCtx),
                LinearLayout.LayoutParams(0, dp(1), 1f)
            )
            decisionSpecs.forEachIndexed { idx, spec ->
                val btn = buildDecisionButton(spec)
                actionRow.addView(
                    btn,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginStart = if (idx == 0) dp(10) else dp(8)
                    }
                )
            }
            card.addView(
                actionRow,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = sectionGap + dp(2)
                }
            )
        }

        val backdrop = FrameLayout(activity).apply {
            setOnClickListener {
                val escId = escapeButtonId
                if (escId != null && !selectionMade) {
                    selectionMade = true
                    onSelected(escId)
                    dialog.dismiss()
                }
            }
            addView(
                card,
                FrameLayout.LayoutParams(
                    cardWidth,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
        }

        dialog.setContentView(backdrop)
        dialog.setCancelable(escapeButtonId != null)
        dialog.setCanceledOnTouchOutside(escapeButtonId != null)
        dialog.setOnCancelListener {
            val escId = escapeButtonId
            if (!selectionMade && escId != null) {
                selectionMade = true
                onSelected(escId)
            }
        }
        dialog.setOnDismissListener {
            onDismissed(dialog)
        }
        dialog.setOnKeyListener { _, keyCode, event ->
            val mapped = keyMapping.get(keyCode)
            if (mapped != null) {
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                    mapped.performClick()
                }
                true
            } else if (keyCode == KeyEvent.KEYCODE_BACK) {
                true
            } else {
                false
            }
        }

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.CENTER)
            setDimAmount(0.58f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            if (immersiveMatchWindow) {
                addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            }
        }

        card.alpha = 0f
        card.scaleX = 0.96f
        card.scaleY = 0.96f

        dialog.show()

        if (immersiveMatchWindow) {
            dialog.window?.let { dlgWin ->
                @Suppress("DEPRECATION")
                dlgWin.decorView.systemUiVisibility = activity.window.decorView.systemUiVisibility
                dlgWin.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            }
        }

        card.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(160L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        return dialog
    }

    private fun writeJvmCrashLog(context: Context, thread: Thread, throwable: Throwable) {
        val now = System.currentTimeMillis()
        val tsHuman = formatHumanTime(now)
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val procName = currentProcessName(context)
        val report = buildString {
            append("============================================================\n")
            append("AliceHub Crash Report\n")
            append("============================================================\n")
            append("Timestamp   : ").append(tsHuman).append('\n')
            append("App         : AliceHub ").append(appVersionName(context)).append('\n')
            append("Process     : ").append(procName).append(" · PID ").append(Process.myPid()).append('\n')
            append("Thread      : ").append(thread.name).append('\n')
            append("Device      : ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" · Android ").append(Build.VERSION.RELEASE)
                .append(" · SDK ").append(Build.VERSION.SDK_INT).append('\n')
            append("Crash Type  : UNCAUGHT EXCEPTION\n")
            append("Exception   : ").append(throwable.javaClass.name)
            val msg = throwable.message?.trim()
            if (!msg.isNullOrEmpty()) {
                append(" - ").append(msg)
            }
            append("\n\n[JVM Stack Trace]\n")
            append(sw.toString().trimEnd()).append('\n')
            append("============================================================\n\n")
        }
        val bytes = report.toByteArray(Charsets.UTF_8)
        try {
            File(context.filesDir, "crash.log").writeBytes(bytes)
            File(context.filesDir, "crash_history.log").appendBytes(bytes)
        } catch (_: Throwable) {
        }
        try {
            context.getExternalFilesDir(null)?.let { ext ->
                File(ext, "crash.log").writeBytes(bytes)
                File(ext, "crash_history.log").appendBytes(bytes)
            }
        } catch (_: Throwable) {
        }
    }

    private fun appVersionName(context: Context): String {
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)?.versionName?.takeIf { it.isNotBlank() }
                ?: "unknown"
        } catch (_: Throwable) {
            "unknown"
        }
    }

    private fun currentProcessName(context: Context): String {
        if (Build.VERSION.SDK_INT >= 28) {
            return android.app.Application.getProcessName()
        }
        val myPid = Process.myPid()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return am?.runningAppProcesses?.firstOrNull { it.pid == myPid }?.processName
            ?: context.packageName
    }

    private fun collectCrashLogCandidates(context: Context): List<File> {
        val out = LinkedHashSet<File>()
        out.add(File(context.filesDir, "crash.log"))
        val savesRoot = File(context.filesDir, "saves")
        savesRoot.listFiles()?.forEach { sub ->
            if (sub.isDirectory && !sub.name.startsWith(".")) {
                out.add(File(sub, "crash.log"))
            }
        }
        context.getExternalFilesDirs(null).filterNotNull().forEach { ext ->
            out.add(File(ext, "crash.log"))
            ext.listFiles()?.forEach { sub ->
                if (sub.isDirectory && !sub.name.startsWith(".")) {
                    out.add(File(sub, "crash.log"))
                }
            }
        }
        return out.toList()
    }

    private fun readSeenCrashMtime(context: Context): Long {
        return try {
            val f = File(context.filesDir, SEEN_MTIME_FILE)
            if (f.isFile) f.readText(Charsets.UTF_8).trim().toLongOrNull() ?: 0L else 0L
        } catch (_: Throwable) {
            0L
        }
    }

    private fun writeSeenCrashMtime(context: Context, mtime: Long) {
        try {
            File(context.filesDir, SEEN_MTIME_FILE).writeText(mtime.toString(), Charsets.UTF_8)
        } catch (_: Throwable) {
        }
    }

    private fun readFileTailUtf8(file: File, maxBytes: Int): String {
        return try {
            val len = file.length()
            if (len <= maxBytes) {
                file.readText(Charsets.UTF_8)
            } else {
                RandomAccessFile(file, "r").use { raf ->
                    val start = len - maxBytes
                    raf.seek(start)
                    val buf = ByteArray(maxBytes)
                    raf.readFully(buf)
                    val text = String(buf, Charsets.UTF_8)
                    val firstNewline = text.indexOf('\n')
                    if (firstNewline in 0..256) text.substring(firstNewline + 1) else text
                }
            }
        } catch (_: Throwable) {
            ""
        }
    }

    private fun normalizeCrashLogText(raw: String, fallbackTimeMs: Long): String {
        if (raw.isBlank()) return "AliceHub Crash Report\nTimestamp   : ${formatHumanTime(fallbackTimeMs)}\n"
        val cleaned = raw
            .replace("\r\n", "\n")
            .replace("XSystem4 Crash Report", "AliceHub Crash Report")
        return epochLineRegex.replace(cleaned) { m ->
            val prefix = m.groupValues[1]
            val sec = m.groupValues[2].toLongOrNull()
            val formatted = if (sec != null && sec > 0L) {
                formatHumanTime(sec * 1000L)
            } else {
                formatHumanTime(fallbackTimeMs)
            }
            "$prefix$formatted"
        }
    }

    private fun detectErrorKind(text: String): ErrorKind {
        return when {
            text.contains("Crash Type  : FATAL SIGNAL", ignoreCase = true) ||
                text.contains("Crash Type  : UNCAUGHT EXCEPTION", ignoreCase = true) ||
                text.contains("Signal      :", ignoreCase = true) -> ErrorKind.SIGNAL
            text.contains("Crash Type  : FATAL ERROR", ignoreCase = true) -> ErrorKind.FATAL
            text.contains("Crash Type  : SCRIPT ERROR", ignoreCase = true) -> ErrorKind.SCRIPT
            text.contains("[VM Call Stack]", ignoreCase = true) -> ErrorKind.SCRIPT
            else -> ErrorKind.RUNTIME
        }
    }

    private fun extractHeaderField(text: String, fieldName: String): String? {
        val prefix = fieldName.lowercase(Locale.ROOT)
        for (line in text.lineSequence().take(24)) {
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val key = line.substring(0, colon).trim().lowercase(Locale.ROOT)
            if (key == prefix) {
                return line.substring(colon + 1).trim()
            }
        }
        return null
    }

    private fun stripSavedPathNotice(raw: String): String {
        val lines = raw.replace("\r\n", "\n").lines()
        val out = ArrayList<String>(lines.size)
        var skipNextPath = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.equals("Crash log saved to:", ignoreCase = true)) {
                skipNextPath = true
                continue
            }
            if (skipNextPath) {
                skipNextPath = false
                if (trimmed.startsWith("/") || trimmed.endsWith(".log", ignoreCase = true)) {
                    continue
                }
            }
            out.add(line)
        }
        return out.joinToString("\n")
    }

    private fun extractSummary(
        directMessage: String?,
        fullLog: String,
        fallbackLabel: String
    ): String {
        extractHeaderField(fullLog, "Error")?.takeIf { it.isNotBlank() }?.let { err ->
            return sanitizeChromeText(err)
        }
        extractHeaderField(fullLog, "Exception")?.takeIf { it.isNotBlank() }?.let { exc ->
            return sanitizeChromeText(exc)
        }
        extractHeaderField(fullLog, "Signal")?.takeIf { it.isNotBlank() }?.let { sig ->
            val fault = extractHeaderField(fullLog, "Fault Addr")?.takeIf { it.isNotBlank() }
            val combined = if (fault != null) "Signal $sig · $fault" else "Signal $sig"
            return sanitizeChromeText(combined)
        }
        val source = directMessage?.takeIf { it.isNotBlank() } ?: fullLog
        val candidate = source.lineSequence()
            .map { it.trim() }
            .firstOrNull { line ->
                line.isNotEmpty() &&
                    !line.startsWith("===") &&
                    !line.startsWith("[") &&
                    !line.equals("AliceHub Crash Report", ignoreCase = true) &&
                    !line.equals("AliceHub Error Report", ignoreCase = true) &&
                    !line.startsWith("Timestamp", ignoreCase = true) &&
                    !line.startsWith("Engine", ignoreCase = true) &&
                    !line.startsWith("Platform", ignoreCase = true) &&
                    !line.startsWith("App ", ignoreCase = true) &&
                    !line.startsWith("Device", ignoreCase = true) &&
                    !line.startsWith("PID", ignoreCase = true) &&
                    !line.startsWith("Crash Type", ignoreCase = true)
            }
        return sanitizeChromeText(candidate ?: fallbackLabel)
    }

    private fun resolveGameDisplayLabel(
        context: Context,
        gameRoot: String?,
        saveDir: String?
    ): String? {
        val gDir = gameRoot?.takeIf { it.isNotBlank() }?.let { File(it) }
        val sDir = saveDir?.takeIf { it.isNotBlank() }?.let { File(it) }
        val dirName = gDir?.name ?: sDir?.name ?: return null
        val gid = GameCatalog.classifyDirectory(
            gameDir = gDir,
            dirName = dirName,
            saveDir = sDir,
            displayName = dirName
        )
        return GameCatalog.descriptorFor(context, gid).title
    }

    private fun inferGameLabelFromFileOrLog(
        context: Context,
        file: File,
        logText: String
    ): String? {
        extractHeaderField(logText, "Game")?.takeIf { it.isNotBlank() }?.let { return it }
        val parent = file.parentFile
        if (parent != null &&
            parent.name != "files" &&
            parent.name != "saves" &&
            !parent.name.startsWith(".")
        ) {
            val isSaveFolder = parent.parentFile?.name == "saves"
            val gid = GameCatalog.classifyDirectory(
                gameDir = if (isSaveFolder) null else parent,
                dirName = parent.name,
                saveDir = if (isSaveFolder) parent else null,
                displayName = parent.name
            )
            return GameCatalog.descriptorFor(context, gid).title
        }
        val lower = logText.lowercase(Locale.ROOT)
        return when {
            lower.contains("rance10") || lower.contains("兰斯10") ->
                context.getString(R.string.game_rance10_title)
            GameCatalog.isRance06Alias(lower) ->
                context.getString(R.string.game_rance06_title)
            GameCatalog.isGalzooAlias(lower) ->
                context.getString(R.string.game_galzoo_title)
            lower.contains("dohnadohna") || lower.contains("多娜多娜") ->
                context.getString(R.string.game_dohnadohna_title)
            GameCatalog.isEvenicle2Alias(lower) ->
                context.getString(R.string.game_evenicle2_title)
            GameCatalog.isEscalayerRebootAlias(lower) ->
                context.getString(R.string.game_escalayer_reboot_title)
            GameCatalog.isIxsealAlias(lower) ->
                context.getString(R.string.game_ixseal_title)
            lower.contains("escalayer") || lower.contains("エスカレイヤー") ||
                lower.contains("超昂天使") || lower.contains("艾斯卡蕾雅") ->
                context.getString(R.string.game_escalayer_title)
            lower.contains("evenicle") || lower.contains("イブニクル") || lower.contains("夏娃") ->
                context.getString(R.string.game_evenicle_title)
            GameCatalog.isDaiakujiAlias(lower) ->
                context.getString(R.string.game_daiakuji_title)
            else -> null
        }
    }

    private fun sanitizeChromeText(raw: String): String {
        return raw
            .replace('(', ' ')
            .replace(')', ' ')
            .replace('（', ' ')
            .replace('）', ' ')
            .replace(whitespaceRegex, " ")
            .trim()
    }

    private fun formatHumanTime(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ms))

    private fun formatFileTime(ms: Long): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(ms))
}
