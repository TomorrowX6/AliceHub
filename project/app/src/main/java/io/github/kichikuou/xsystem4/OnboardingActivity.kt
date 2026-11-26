package io.github.kichikuou.xsystem4

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
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

internal fun Activity.showStorageUpgradeNotice(onMove: () -> Unit, onPrivate: () -> Unit): AlertDialog =
    AlertDialog.Builder(this)
        .setTitle(R.string.storage_upgrade_title)
        .setMessage(R.string.storage_upgrade_message)
        .setNegativeButton(R.string.storage_upgrade_private) { _, _ -> onPrivate() }
        .setPositiveButton(R.string.storage_upgrade_move) { _, _ -> onMove() }
        .show()

internal fun Activity.createStorageOptions(publicSelected: Boolean, onSelection: (Boolean) -> Unit = {}): View =
    (layoutInflater.inflate(R.layout.storage_options, null) as ViewGroup).apply {
        val group = findViewById<RadioGroup>(R.id.storage_options)
        val benefits = findViewById<View>(R.id.storage_public_benefits)
        val drawbacks = findViewById<View>(R.id.storage_private_drawbacks)
        fun showDetails(usePublic: Boolean) {
            benefits.visibility = if (usePublic) View.VISIBLE else View.GONE
            drawbacks.visibility = if (usePublic) View.GONE else View.VISIBLE
        }
        group.check(
            if (publicSelected) R.id.storage_option_public else R.id.storage_option_private
        )
        showDetails(publicSelected)
        group.setOnCheckedChangeListener { _, id ->
            val usePublic = id == R.id.storage_option_public
            if (ValueAnimator.areAnimatorsEnabled() && isLaidOut) {
                TransitionManager.beginDelayedTransition(
                    this,
                    TransitionSet().addTransition(ChangeBounds()).addTransition(Fade()).setDuration(250)
                )
            }
            showDetails(usePublic)
            onSelection(usePublic)
        }
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
    private var upgradeNotice: AlertDialog? = null
    private var upgradeNoticeAnswered = false
    private var welcomeAnimation: ValueAnimator? = null

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
        upgradeNoticeAnswered = savedInstanceState?.getBoolean("upgrade_notice_answered") ?: false
        showPage()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("page", page)
        outState.putBoolean("public_selected", publicSelected)
        outState.putBoolean("online_enabled", onlineEnabled)
        outState.putBoolean("awaiting_permission", awaitingPermission)
        outState.putBoolean("upgrade_notice_answered", upgradeNoticeAnswered)
    }

    override fun onDestroy() {
        welcomeAnimation?.cancel()
        SharedStorageMigration.detach(this)
        disclaimer?.dismiss()
        upgradeNotice?.dismiss()
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
        if (requestCode == STORAGE_PERMISSION_REQUEST && awaitingPermission) finishStorageSelection()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_REQUEST && awaitingPermission) finishStorageSelection()
    }

    override fun onResume() {
        super.onResume()
        if (awaitingPermission && AppStorage.hasPermission(this)) finishStorageSelection()
    }

    private fun finishStorageSelection() {
        if (upgradeNoticeAnswered && publicSelected &&
            LauncherActivity.showStorageMigration(this, onComplete = ::showOnlinePage)
        ) return
        showOnlinePage()
    }

    private fun showOnlinePage() {
        awaitingPermission = false
        page = 2
        showPage()
    }

    private fun showPage(backward: Boolean = false) {
        welcomeAnimation?.cancel()
        welcomeAnimation = null
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
                content.addPageIcon(R.drawable.ic_folder_open)
                content.addHeading(R.string.storage_location, R.string.onboarding_storage_intro)
                content.addView(createStorageOptions(publicSelected) { publicSelected = it })
                next.setText(R.string.ok)
                next.setOnClickListener { confirmStorageSelection(next) }
            }
            else -> {
                showOnline(content)
                next.setText(R.string.onboarding_finish)
                next.setOnClickListener {
                    AppSettings.completeOnboarding(this, onlineEnabled,
                        !upgradeNoticeAnswered && AppStorage.hasLegacyData(this))
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

    private fun confirmStorageSelection(next: Button) {
        fun proceed(usePublic: Boolean) {
            publicSelected = usePublic
            AppSettings.setPublicStorage(this, usePublic)
            awaitingPermission = usePublic
            next.isEnabled = !awaitingPermission
            if (!awaitingPermission || !requestSharedStorageAccess()) finishStorageSelection()
        }
        if (publicSelected && !upgradeNoticeAnswered && AppStorage.hasLegacyData(this)) {
            upgradeNotice = showStorageUpgradeNotice(
                onMove = {
                    upgradeNoticeAnswered = true
                    proceed(true)
                },
                onPrivate = {
                    upgradeNoticeAnswered = true
                    proceed(false)
                }
            )
        } else {
            proceed(publicSelected)
        }
    }

    private fun showIntro(content: LinearLayout) {
        val halo = WelcomeHalo()
        val icon = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val stage = FrameLayout(this).apply {
            addView(halo, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
            addView(icon, FrameLayout.LayoutParams(dp(112), dp(112), Gravity.CENTER))
        }
        content.addView(stage, LinearLayout.LayoutParams(dp(170), dp(170)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(8)
        })
        val (title, subtitle) = content.addHeading(R.string.onboarding_welcome, R.string.onboarding_intro, center = true)
        if (!ValueAnimator.areAnimatorsEnabled()) return
        val bounce = OvershootInterpolator(1.8f)
        val ease = DecelerateInterpolator()
        fun progress(time: Float, start: Float, end: Float): Float = ((time - start) / (end - start)).coerceIn(0f, 1f)
        fun reveal(view: View, fraction: Float) {
            view.alpha = fraction
            view.translationY = dp(16) * (1f - fraction)
        }
        fun render(time: Float) {
            val arrival = bounce.getInterpolation(progress(time, 0f, 0.59f))
            icon.alpha = progress(time, 0f, 0.21f)
            icon.scaleX = 0.35f + 0.65f * arrival
            icon.scaleY = icon.scaleX
            icon.rotation = -24f * (1f - arrival)
            reveal(title, ease.getInterpolation(progress(time, 0.33f, 0.68f)))
            reveal(subtitle, ease.getInterpolation(progress(time, 0.45f, 0.81f)))
            halo.reveal = progress(time, 0.02f, 0.46f)
            halo.pulse = progress(time, 0.21f, 0.91f)
            halo.sweep = progress(time, 0.31f, 1f)
            halo.invalidate()
        }
        render(0f)
        welcomeAnimation = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1450L
            interpolator = LinearInterpolator()
            addUpdateListener { render(it.animatedFraction) }
            start()
        }
    }

    private inner class WelcomeHalo : View(this@OnboardingActivity) {
        private val accent = getColor(R.color.ui_primary)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private lateinit var glow: RadialGradient
        var reveal = 1f
        var pulse = 1f
        var sweep = 1f

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            val radius = minOf(w, h) / 2f
            if (radius > 0f) {
                glow = RadialGradient(w / 2f, h / 2f, radius, withOpacity(accent, 90), withOpacity(accent, 0), Shader.TileMode.CLAMP)
            }
        }

        override fun onDraw(canvas: Canvas) {
            if (!::glow.isInitialized) return
            val x = width / 2f
            val y = height / 2f
            val radius = minOf(x, y)
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.shader = glow
            paint.alpha = (reveal * 220 + (1f - pulse) * pulse * 130).toInt().coerceAtMost(255)
            canvas.drawCircle(x, y, radius, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1).toFloat()
            paint.color = withOpacity(accent, (reveal * 49).toInt())
            val orbit = radius * 0.80f
            canvas.drawCircle(x, y, orbit, paint)
            if (pulse > 0f && pulse < 1f) {
                paint.color = withOpacity(accent, (115 * (1f - pulse)).toInt())
                canvas.drawCircle(x, y, radius * (0.58f + 0.38f * pulse), paint)
            }
            if (sweep > 0f && sweep < 1f) {
                val start = -170f + sweep * 420f
                paint.strokeWidth = dp(2).toFloat()
                paint.strokeCap = Paint.Cap.ROUND
                for (segment in 0..6) {
                    paint.color = withOpacity(accent, (segment * 32 * 4f * sweep * (1f - sweep)).toInt())
                    canvas.drawArc(x - orbit, y - orbit, x + orbit, y + orbit, start + segment * 13f, 12f, false, paint)
                }
            }
        }
    }

    private fun LinearLayout.addPageIcon(drawable: Int) {
        val accent = getColor(R.color.ui_primary)
        val badge = ImageView(this@OnboardingActivity).apply {
            setImageResource(drawable)
            imageTintList = ColorStateList.valueOf(accent)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(withOpacity(accent, 32))
            }
            val inset = dp(11)
            setPadding(inset, inset, inset, inset)
        }
        addView(badge, LinearLayout.LayoutParams(dp(48), dp(48)).apply { bottomMargin = dp(14) })
    }

    private fun showOnline(content: LinearLayout) {
        content.addPageIcon(R.drawable.ic_cloud_download)
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

    private fun LinearLayout.addHeading(title: Int, subtitle: Int, center: Boolean = false): Pair<TextView, TextView> {
        val heading = TextView(this@OnboardingActivity).apply {
            setText(title)
            setTextColor(getColor(R.color.ui_text_primary))
            typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
            textSize = 28f
            if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
            if (center) gravity = Gravity.CENTER
        }
        val description = TextView(this@OnboardingActivity).apply {
            setText(subtitle)
            setTextColor(getColor(R.color.ui_text_secondary))
            textSize = 16f
            if (center) gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(22))
        }
        addView(heading)
        addView(description)
        return heading to description
    }

    private fun withOpacity(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + if (value >= 0) 0.5f else -0.5f).toInt()
}
