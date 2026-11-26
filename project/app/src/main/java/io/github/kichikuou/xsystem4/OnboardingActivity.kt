package io.github.kichikuou.xsystem4

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

internal const val STORAGE_PERMISSION_REQUEST = 33

internal fun Activity.requestSharedStorageAccess(): Boolean {
    if (AppStorage.hasPermission(this)) return false
    if (Build.VERSION.SDK_INT < 30) {
        requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
            STORAGE_PERMISSION_REQUEST)
        return true
    }
    return try {
        startActivityForResult(
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")),
            STORAGE_PERMISSION_REQUEST
        )
        true
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.public_storage_settings_unavailable, Toast.LENGTH_LONG).show()
        false
    }
}

internal fun Activity.createStorageOptions(publicSelected: Boolean): View =
    layoutInflater.inflate(R.layout.storage_options, null).apply {
        findViewById<RadioGroup>(R.id.storage_options).check(
            if (publicSelected) R.id.storage_option_public else R.id.storage_option_private
        )
        findViewById<View>(R.id.storage_help).setOnClickListener {
            AlertDialog.Builder(this@createStorageOptions)
                .setTitle(R.string.public_storage_why)
                .setMessage(R.string.public_storage_permission_message)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }

class OnboardingActivity : ThemedActivity() {
    private var page = 0
    private var publicSelected = true
    private var onlineEnabled = false
    private var awaitingPermission = false
    private var disclaimer: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (AppSettings.isOnboardingComplete(this)) {
            setResult(RESULT_OK)
            finish()
            return
        }
        actionBar?.hide()
        setContentView(R.layout.activity_onboarding)
        page = savedInstanceState?.getInt("page") ?: 0
        publicSelected = savedInstanceState?.getBoolean("public_selected") ?: true
        onlineEnabled = savedInstanceState?.getBoolean("online_enabled") ?: AppSettings.isOnlineInstallEnabled(this)
        awaitingPermission = savedInstanceState?.getBoolean("awaiting_permission") ?: false
        showPage()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("page", page)
        outState.putBoolean("public_selected", publicSelected)
        outState.putBoolean("online_enabled", onlineEnabled)
        outState.putBoolean("awaiting_permission", awaitingPermission)
    }

    override fun onDestroy() {
        disclaimer?.dismiss()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (page == 0) super.onBackPressed()
        else if (!awaitingPermission) {
            page--
            showPage(backward = true)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == STORAGE_PERMISSION_REQUEST && awaitingPermission) showOnlinePage()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_REQUEST && awaitingPermission) showOnlinePage()
    }

    private fun showOnlinePage() {
        awaitingPermission = false
        page = 2
        showPage()
    }

    private fun showPage(backward: Boolean = false) {
        val host = findViewById<FrameLayout>(R.id.onboarding_page)
        host.removeAllViews()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(20), 0, dp(20))
        }
        host.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        findViewById<ScrollView>(R.id.onboarding_scroll).scrollTo(0, 0)
        findViewById<TextView>(R.id.onboarding_step).text = getString(R.string.onboarding_step, page + 1, 3)
        findViewById<ProgressBar>(R.id.onboarding_progress).setProgress(page + 1, true)
        findViewById<Button>(R.id.onboarding_back).apply {
            visibility = if (page == 0) View.INVISIBLE else View.VISIBLE
            isEnabled = !awaitingPermission
            @Suppress("DEPRECATION")
            setOnClickListener { onBackPressed() }
        }
        val next = findViewById<Button>(R.id.onboarding_next)
        next.isEnabled = !awaitingPermission
        when (page) {
            0 -> {
                showIntro(content)
                next.setText(R.string.onboarding_next)
                next.setOnClickListener { page = 1; showPage() }
            }
            1 -> {
                content.addHeading(R.string.storage_location, R.string.onboarding_storage_intro)
                val options = createStorageOptions(publicSelected)
                content.addView(options)
                options.findViewById<RadioGroup>(R.id.storage_options).setOnCheckedChangeListener { _, id ->
                    publicSelected = id == R.id.storage_option_public
                }
                next.setText(R.string.ok)
                next.setOnClickListener {
                    AppSettings.setPublicStorage(this, publicSelected)
                    awaitingPermission = publicSelected
                    if (!awaitingPermission || !requestSharedStorageAccess()) showOnlinePage()
                    else next.isEnabled = false
                }
            }
            else -> {
                showOnline(content)
                next.setText(R.string.onboarding_finish)
                next.setOnClickListener {
                    AppSettings.completeOnboarding(this, onlineEnabled)
                    setResult(RESULT_OK)
                    finish()
                }
            }
        }
        if (page != 0 && ValueAnimator.areAnimatorsEnabled()) {
            content.alpha = 0f
            content.translationX = dp(if (backward) -20 else 20).toFloat()
            content.animate().alpha(1f).translationX(0f).setDuration(220).start()
        }
    }

    private fun showIntro(content: LinearLayout) {
        val icon = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val size = dp(112)
        content.addView(icon, LinearLayout.LayoutParams(size, size).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(24)
        })
        content.addHeading(R.string.onboarding_welcome, R.string.onboarding_intro, center = true)
        if (ValueAnimator.areAnimatorsEnabled()) {
            icon.alpha = 0f
            icon.scaleX = 0.72f
            icon.scaleY = 0.72f
            icon.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(650)
                .setInterpolator(DecelerateInterpolator()).start()
        }
    }

    private fun showOnline(content: LinearLayout) {
        content.addHeading(R.string.settings_online_install, R.string.onboarding_online_intro)
        val toggle = Switch(this).apply {
            setBackgroundResource(R.drawable.bg_card)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            minHeight = dp(60)
            setText(R.string.settings_online_install)
            textSize = 16f
            isChecked = onlineEnabled
        }
        toggle.setOnCheckedChangeListener { _, enabled ->
            if (!enabled) {
                onlineEnabled = false
            } else if (!onlineEnabled) {
                toggle.isChecked = false
                disclaimer = AlertDialog.Builder(this)
                    .setTitle(R.string.online_disclaimer_title)
                    .setMessage(R.string.online_disclaimer_msg)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.online_disclaimer_agree) { _, _ ->
                        onlineEnabled = true
                        toggle.isChecked = true
                    }
                    .show()
            }
        }
        content.addView(toggle)
    }

    private fun LinearLayout.addHeading(title: Int, subtitle: Int, center: Boolean = false) {
        addView(TextView(this@OnboardingActivity).apply {
            setText(title)
            setTextColor(getColor(R.color.ui_text_primary))
            typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
            textSize = 28f
            if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
            if (center) gravity = Gravity.CENTER
        })
        addView(TextView(this@OnboardingActivity).apply {
            setText(subtitle)
            setTextColor(getColor(R.color.ui_text_secondary))
            textSize = 16f
            if (center) gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(22))
        })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + if (value >= 0) 0.5f else -0.5f).toInt()
}
