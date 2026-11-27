package io.github.kichikuou.xsystem4

import android.app.Activity
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.text.NumberFormat
import java.util.concurrent.atomic.AtomicReference

internal object SharedStorageMigration {
    private class Session {
        val latest = AtomicReference<AppStorage.MigrationProgress?>()
        var displayed: AppStorage.MigrationProgress? = null
        val dialogs = mutableMapOf<Activity, ProgressDialog>()
    }

    private class ProgressDialog(activity: Activity, val onComplete: () -> Unit) {
        private val content = activity.layoutInflater.inflate(R.layout.storage_migration_progress, null)
        private val progress = content.findViewById<ProgressBar>(R.id.migration_progress)
        private val item = content.findViewById<TextView>(R.id.migration_item)
        private val percent = content.findViewById<TextView>(R.id.migration_percent)
        private val format = NumberFormat.getPercentInstance(activity.resources.configuration.locales[0])
        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.public_storage_migrating)
            .setView(UiKit.dialogContent(activity, content))
            .setCancelable(false)
            .show()

        fun update(status: AppStorage.MigrationProgress?) {
            val value = status?.percent ?: 0
            progress.progress = value
            percent.text = format.format(value / 100.0)
            if (status != null) item.text = status.itemName
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var running: Session? = null

    fun attach(activity: Activity, onComplete: () -> Unit): Boolean {
        val session = running ?: return false
        if (activity.isFinishing || activity.isDestroyed) return false
        session.dialogs.getOrPut(activity) { ProgressDialog(activity, onComplete) }.update(session.displayed)
        return true
    }

    fun detach(activity: Activity) {
        running?.dialogs?.remove(activity)?.dialog?.dismiss()
    }

    fun showPendingFailureNotice(activity: Activity) {
        if (AppStorage.hasPendingPublicStorageFailureNotice(activity)) showFailureNotice(activity)
    }

    private fun showFailureNotice(activity: Activity) {
        if (!activity.isFinishing && !activity.isDestroyed && AppStorage.consumePublicStorageFailureNotice(activity)) {
            Toast.makeText(activity, R.string.public_storage_migration_incomplete, Toast.LENGTH_LONG).show()
        }
    }

    fun start(activity: Activity, retry: Boolean, onFinished: () -> Unit, onComplete: () -> Unit) {
        if (attach(activity, onComplete)) return
        val session = Session()
        running = session
        attach(activity, onComplete)
        val context = activity.applicationContext
        Thread({
            val complete = try {
                AppStorage.preparePublicStorage(context, retry) { status ->
                    if (session.latest.getAndSet(status) == null) handler.post {
                        session.latest.getAndSet(null)?.let { current ->
                            session.displayed = current
                            session.dialogs.values.forEach { it.update(current) }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("SharedStorageMigration", "Could not prepare shared storage", e)
                false
            }
            handler.post {
                running = null
                onFinished()
                var resultShown = false
                for ((host, binding) in session.dialogs) {
                    binding.dialog.dismiss()
                    if (host.isFinishing || host.isDestroyed) continue
                    if (!resultShown) {
                        resultShown = true
                        if (complete) Toast.makeText(host, R.string.public_storage_migration_done, Toast.LENGTH_LONG).show()
                        else showFailureNotice(host)
                    }
                    binding.onComplete()
                }
            }
        }, "AliceHub storage migration").start()
    }
}
