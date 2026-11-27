package io.github.kichikuou.xsystem4

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log

class InstallForegroundService : Service() {
    companion object {
        const val EXTRA_RESTORE_INSTALL_DIALOG = "EXTRA_RESTORE_INSTALL_DIALOG"
        const val REQ_POST_NOTIFICATIONS = 9101

        private const val TAG = "InstallFgService"
        private const val ACTION_START_FOREGROUND = "io.github.kichikuou.xsystem4.ACTION_START_INSTALL_FG"
        private const val ACTION_PAUSE_INSTALL = "io.github.kichikuou.xsystem4.ACTION_PAUSE_INSTALL"
        private const val ACTION_CANCEL_INSTALL = "io.github.kichikuou.xsystem4.ACTION_CANCEL_INSTALL"
        private const val ACTION_STOP = "io.github.kichikuou.xsystem4.ACTION_STOP_INSTALL_FG"

        private const val CHANNEL_PROGRESS = "install_progress"
        private const val CHANNEL_RESULT = "install_result"
        private const val NOTIF_ID_PROGRESS = 1001
        private const val NOTIF_ID_RESULT = 1002
        private const val NOTIFY_INTERVAL_MS = 500L
        private const val MAX_LOCK_TIMEOUT_MS = 6L * 60L * 60L * 1000L
        private const val PERM_POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
        private const val PREFS_NOTIF_PERM = "notif_perm_prefs"
        private const val KEY_ASKED_ONCE = "asked_once"

        private val stateLock = Any()

        @Volatile
        private var activeSession: Boolean = false

        @Volatile
        private var pendingStop: Boolean = false

        private var pendingForegroundStarts: Int = 0

        @Volatile
        private var taskTitle: String = ""

        @Volatile
        private var progressText: String = ""

        @Volatile
        private var permille: Int? = null

        @Volatile
        private var cancellable: Boolean = false

        @Volatile
        private var resumable: Boolean = false

        @Volatile
        private var modTargetPath: String? = null

        @Volatile
        private var hubTargetGameId: String? = null

        @Volatile
        private var lastNotifyElapsedMs: Long = 0L

        @Volatile
        private var runningInstance: InstallForegroundService? = null

        @Volatile
        var pauseCallback: (() -> Unit)? = null

        @Volatile
        var cancelCallback: (() -> Unit)? = null

        fun requestNotificationPermissionIfNeeded(activity: Activity, forceIfRationale: Boolean = false) {
            if (Build.VERSION.SDK_INT < 33) return
            try {
                if (activity.checkSelfPermission(PERM_POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    return
                }
                val prefs = activity.getSharedPreferences(PREFS_NOTIF_PERM, Context.MODE_PRIVATE)
                val askedBefore = prefs.getBoolean(KEY_ASKED_ONCE, false)
                if (!askedBefore || (forceIfRationale && activity.shouldShowRequestPermissionRationale(PERM_POST_NOTIFICATIONS))) {
                    prefs.edit().putBoolean(KEY_ASKED_ONCE, true).apply()
                    activity.requestPermissions(arrayOf(PERM_POST_NOTIFICATIONS), REQ_POST_NOTIFICATIONS)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to request notification permission", t)
            }
        }

        fun startSession(
            context: Context,
            title: String,
            initialText: String?,
            initialPermille: Int?,
            isCancellable: Boolean,
            isResumable: Boolean,
            targetGamePathForMod: String? = null,
            targetGameId: String? = null
        ) {
            val appCtx = context.applicationContext
            ensureChannels(appCtx)
            clearResultNotification(appCtx)

            val svcIntent = Intent(appCtx, InstallForegroundService::class.java).apply {
                action = ACTION_START_FOREGROUND
            }
            synchronized(stateLock) {
                taskTitle = title
                progressText = initialText ?: ""
                permille = initialPermille
                cancellable = isCancellable
                resumable = isResumable
                modTargetPath = targetGamePathForMod
                hubTargetGameId = targetGameId
                lastNotifyElapsedMs = SystemClock.elapsedRealtime()
                activeSession = true
                pendingStop = false
                pendingForegroundStarts++
            }

            try {
                appCtx.startForegroundService(svcIntent)
            } catch (t: Throwable) {
                synchronized(stateLock) {
                    if (pendingForegroundStarts > 0) {
                        pendingForegroundStarts--
                    }
                }
                Log.w(TAG, "startForegroundService failed, falling back to notification", t)
                postProgressNotification(appCtx)
            }
        }

        fun updateProgress(
            context: Context,
            text: String?,
            newPermille: Int?,
            isCancellable: Boolean,
            isResumable: Boolean,
            forceImmediate: Boolean = false
        ) {
            if (!activeSession) return
            if (text != null) {
                progressText = text
            }
            permille = newPermille
            cancellable = isCancellable
            resumable = isResumable

            val now = SystemClock.elapsedRealtime()
            if (forceImmediate || now - lastNotifyElapsedMs >= NOTIFY_INTERVAL_MS) {
                lastNotifyElapsedMs = now
                postProgressNotification(context.applicationContext)
            }
        }

        fun flushLatestNotification(context: Context) {
            if (!activeSession) return
            lastNotifyElapsedMs = SystemClock.elapsedRealtime()
            postProgressNotification(context.applicationContext)
        }

        fun finishSessionSucceeded(context: Context, postResultNotification: Boolean) {
            val appCtx = context.applicationContext
            val lctx = AppSettings.wrapThemeContext(appCtx)
            val finishedTitle = taskTitle.ifBlank { lctx.getString(R.string.install_dialog_title) }
            val targetPath = modTargetPath
            val targetGameId = hubTargetGameId
            stopSessionInternal(appCtx)
            if (postResultNotification) {
                postOutcomeNotification(
                    context = appCtx,
                    title = lctx.getString(R.string.notif_done_title),
                    body = lctx.getString(R.string.notif_done_msg, finishedTitle),
                    targetPath = targetPath,
                    targetGameId = targetGameId
                )
            }
        }

        fun finishSessionFailed(context: Context, errorMsg: String, postResultNotification: Boolean) {
            val appCtx = context.applicationContext
            val lctx = AppSettings.wrapThemeContext(appCtx)
            val targetPath = modTargetPath
            val targetGameId = hubTargetGameId
            stopSessionInternal(appCtx)
            if (postResultNotification) {
                postOutcomeNotification(
                    context = appCtx,
                    title = lctx.getString(R.string.notif_failed_title),
                    body = errorMsg,
                    targetPath = targetPath,
                    targetGameId = targetGameId
                )
            }
        }

        fun finishSessionCancelled(context: Context) {
            stopSessionInternal(context.applicationContext)
        }

        fun clearResultNotification(context: Context) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(NOTIF_ID_RESULT)
            } catch (_: Throwable) {
            }
        }

        private fun stopSessionInternal(appCtx: Context) {
            val instanceToStop: InstallForegroundService?
            synchronized(stateLock) {
                activeSession = false
                pendingStop = true
                instanceToStop = if (pendingForegroundStarts == 0) runningInstance else null
            }
            if (instanceToStop != null) {
                instanceToStop.stopFromSession()
            } else if (runningInstance == null && synchronized(stateLock) { pendingForegroundStarts == 0 }) {
                cancelProgressNotification(appCtx)
            }
        }

        private fun cancelProgressNotification(context: Context) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(NOTIF_ID_PROGRESS)
            } catch (_: Throwable) {
            }
        }

        private fun ensureChannels(context: Context) {
            val lctx = AppSettings.wrapThemeContext(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS,
                lctx.getString(R.string.notif_channel_progress_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            val resultChannel = NotificationChannel(
                CHANNEL_RESULT,
                lctx.getString(R.string.notif_channel_result_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            nm.createNotificationChannel(progressChannel)
            nm.createNotificationChannel(resultChannel)
        }

        private fun buildOpenAppPendingIntent(
            context: Context,
            targetPath: String?,
            targetGameId: String?,
            restoreDialog: Boolean
        ): PendingIntent {
            val openIntent = when {
                !targetPath.isNullOrBlank() -> Intent(context, ModManagerActivity::class.java).apply {
                    putExtra(ModManagerActivity.EXTRA_GAME_PATH, targetPath)
                    if (!targetGameId.isNullOrBlank()) {
                        putExtra(ModManagerActivity.EXTRA_GAME_ID, targetGameId)
                    }
                }
                !targetGameId.isNullOrBlank() -> Intent(context, GameHubActivity::class.java).apply {
                    putExtra(GameHubActivity.EXTRA_GAME_ID, targetGameId)
                }
                else -> Intent(context, LauncherActivity::class.java)
            }
            openIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (restoreDialog) {
                openIntent.putExtra(EXTRA_RESTORE_INSTALL_DIALOG, true)
            }
            return PendingIntent.getActivity(
                context,
                if (restoreDialog) 1 else 2,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun buildProgressNotification(context: Context): Notification {
            ensureChannels(context)
            val lctx = AppSettings.wrapThemeContext(context)
            val curPermille = permille
            val baseTitle = taskTitle.ifBlank { lctx.getString(R.string.install_dialog_title) }
            val fullTitle = if (curPermille != null) {
                "$baseTitle · ${(curPermille.coerceIn(0, 1000)) / 10}%"
            } else {
                baseTitle
            }
            val rawBody = progressText.ifBlank { lctx.getString(R.string.online_fetching_manifest) }
            val singleLineBody = rawBody.lineSequence().firstOrNull()?.trim().orEmpty().ifEmpty { rawBody }

            val builder = Notification.Builder(context, CHANNEL_PROGRESS)
                .setSmallIcon(R.drawable.ic_notif_download)
                .setContentTitle(fullTitle)
                .setContentText(singleLineBody)
                .setStyle(Notification.BigTextStyle().bigText(rawBody))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setContentIntent(buildOpenAppPendingIntent(context, modTargetPath, hubTargetGameId, restoreDialog = true))

            if (curPermille != null) {
                builder.setProgress(1000, curPermille.coerceIn(0, 1000), false)
            } else {
                builder.setProgress(0, 0, true)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            }

            if (cancellable) {
                if (resumable) {
                    val pauseIntent = Intent(context, InstallForegroundService::class.java).apply {
                        action = ACTION_PAUSE_INSTALL
                    }
                    val pausePi = PendingIntent.getService(
                        context,
                        3,
                        pauseIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val pauseAction = Notification.Action.Builder(
                        null as Icon?,
                        lctx.getString(R.string.notif_action_pause),
                        pausePi
                    ).build()
                    builder.addAction(pauseAction)
                }

                val cancelIntent = Intent(context, InstallForegroundService::class.java).apply {
                    action = ACTION_CANCEL_INSTALL
                }
                val cancelPi = PendingIntent.getService(
                    context,
                    4,
                    cancelIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val cancelAction = Notification.Action.Builder(
                    null as Icon?,
                    lctx.getString(R.string.cancel),
                    cancelPi
                ).build()
                builder.addAction(cancelAction)
            }

            return builder.build()
        }

        private fun postProgressNotification(context: Context) {
            if (!activeSession) return
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.notify(NOTIF_ID_PROGRESS, buildProgressNotification(context))
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to update progress notification", t)
            }
        }

        private fun postOutcomeNotification(
            context: Context,
            title: String,
            body: String,
            targetPath: String?,
            targetGameId: String?
        ) {
            try {
                ensureChannels(context)
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                val singleLine = body.lineSequence().firstOrNull()?.trim().orEmpty().ifEmpty { body }
                val notif = Notification.Builder(context, CHANNEL_RESULT)
                    .setSmallIcon(R.drawable.ic_notif_done)
                    .setContentTitle(title)
                    .setContentText(singleLine)
                    .setStyle(Notification.BigTextStyle().bigText(body))
                    .setAutoCancel(true)
                    .setContentIntent(buildOpenAppPendingIntent(context, targetPath, targetGameId, restoreDialog = false))
                    .build()
                nm.notify(NOTIF_ID_RESULT, notif)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to post result notification", t)
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    @Volatile
    private var promotedInForeground: Boolean = false

    override fun onCreate() {
        super.onCreate()
        promotedInForeground = false
        runningInstance = this
        ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runningInstance = this
        val action = intent?.action

        // Dispatch notification pause/cancel actions BEFORE calling startForeground(), so a
        // background PendingIntent.getService click is never dropped if FGS promotion fails.
        when (action) {
            ACTION_PAUSE_INSTALL -> {
                val cb = pauseCallback ?: cancelCallback
                if (cb != null) {
                    Handler(Looper.getMainLooper()).post { cb() }
                }
                val needsPromote = synchronized(stateLock) { pendingForegroundStarts > 0 }
                if (!needsPromote && !promotedInForeground) {
                    if (!activeSession || pendingStop) {
                        stopSelf(startId)
                    }
                    return START_NOT_STICKY
                }
            }
            ACTION_CANCEL_INSTALL -> {
                val cb = cancelCallback
                if (cb != null) {
                    Handler(Looper.getMainLooper()).post { cb() }
                }
                val needsPromote = synchronized(stateLock) { pendingForegroundStarts > 0 }
                if (!needsPromote && !promotedInForeground) {
                    if (!activeSession || pendingStop) {
                        stopSelf(startId)
                    }
                    return START_NOT_STICKY
                }
            }
        }

        // Promote to foreground so Android's 5s FGS timer is satisfied even when the session
        // already ended or ACTION_STOP was requested.
        val promoted = promoteToForeground(buildProgressNotification(this))
        val shouldStopNow: Boolean
        synchronized(stateLock) {
            if (action == ACTION_START_FOREGROUND && pendingForegroundStarts > 0) {
                pendingForegroundStarts--
            }
            if (action == ACTION_STOP) {
                activeSession = false
                pendingStop = true
            }
            shouldStopNow = (!activeSession || pendingStop) && pendingForegroundStarts == 0
        }

        if (!promoted) {
            releaseLocks()
            return START_NOT_STICKY
        }

        if (shouldStopNow) {
            stopNow()
        } else if (activeSession) {
            acquireLocks()
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int) {
        Log.w(TAG, "Foreground service timeout reached (startId=$startId); pausing active install")
        val cb = pauseCallback ?: cancelCallback
        if (cb != null) {
            Handler(Looper.getMainLooper()).post { cb() }
        }
        synchronized(stateLock) {
            activeSession = false
            pendingStop = true
        }
        releaseLocks()
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Throwable) {
        }
        cancelProgressNotification(applicationContext)
        stopSelf(startId)
    }

    fun onTimeout(startId: Int, @Suppress("UNUSED_PARAMETER") fgsType: Int) {
        onTimeout(startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        synchronized(stateLock) {
            if (runningInstance === this) {
                runningInstance = null
            }
        }
        promotedInForeground = false
        releaseLocks()
        super.onDestroy()
    }

    private fun promoteToForeground(notif: Notification): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIF_ID_PROGRESS,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIF_ID_PROGRESS, notif)
            }
            promotedInForeground = true
            true
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground failed; calling stopSelf() to avoid FGS timeout crash", t)
            try {
                stopSelf()
            } catch (_: Throwable) {
            }
            false
        }
    }

    private fun stopFromSession() {
        val canStopImmediately = synchronized(stateLock) {
            pendingStop = true
            pendingForegroundStarts == 0
        }
        if (!canStopImmediately) {
            // Defer stopNow() until the in-flight onStartCommand promotes to foreground first.
            return
        }
        if (!promotedInForeground) {
            if (!promoteToForeground(buildProgressNotification(this))) {
                releaseLocks()
                return
            }
        }
        stopNow()
    }

    private fun stopNow() {
        releaseLocks()
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Throwable) {
        }
        promotedInForeground = false
        cancelProgressNotification(applicationContext)
        stopSelf()
    }

    private fun acquireLocks() {
        try {
            if (wakeLock == null) {
                val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "re-xsystem4:InstallWakeLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(MAX_LOCK_TIMEOUT_MS)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to acquire WakeLock", t)
        }

        try {
            if (wifiLock == null) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                @Suppress("DEPRECATION")
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wm?.createWifiLock(mode, "re-xsystem4:InstallWifiLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            if (wifiLock?.isHeld == false) {
                wifiLock?.acquire()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to acquire WifiLock", t)
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Throwable) {
        }
        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        } catch (_: Throwable) {
        }
    }
}
