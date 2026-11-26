package io.github.kichikuou.xsystem4

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
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
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

internal const val STORAGE_PERMISSION_REQUEST = 33
private const val WELCOME_DURATION_MS = 1750L

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
    }

class OnboardingActivity : ThemedActivity() {
    private var page = 0
    private var publicSelected = true
    private var onlineEnabled = false
    private var awaitingPermission = false
    private var disclaimer: AlertDialog? = null
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
        welcomeAnimation?.cancel()
        SharedStorageMigration.detach(this)
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
        if (publicSelected &&
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

    private fun confirmStorageSelection(next: Button) {
        AppSettings.setPublicStorage(this, publicSelected)
        awaitingPermission = publicSelected
        next.isEnabled = !awaitingPermission
        if (!awaitingPermission || !requestSharedStorageAccess()) finishStorageSelection()
    }

    private fun showIntro(content: LinearLayout) {
        val icon = WelcomeIcon().apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        content.addView(icon, LinearLayout.LayoutParams(dp(170), dp(170)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(8)
        })
        val (title, subtitle) = content.addHeading(R.string.onboarding_welcome, R.string.onboarding_intro, center = true)
        if (!ValueAnimator.areAnimatorsEnabled()) return
        val ease = DecelerateInterpolator()
        val textOffset = dp(16)
        fun reveal(view: View, time: Float, start: Float, end: Float) {
            val fraction = ease.getInterpolation(phase(time, start, end))
            view.alpha = fraction
            view.translationY = textOffset * (1f - fraction)
        }
        fun render(time: Float) {
            icon.playTime = time
            reveal(title, time, 1110f, 1510f)
            reveal(subtitle, time, 1300f, WELCOME_DURATION_MS.toFloat())
        }
        render(0f)
        welcomeAnimation = ValueAnimator.ofFloat(0f, WELCOME_DURATION_MS.toFloat()).apply {
            duration = WELCOME_DURATION_MS
            interpolator = LinearInterpolator()
            addUpdateListener { render(it.animatedValue as Float) }
            start()
        }
    }

    private fun phase(time: Float, start: Float, end: Float): Float =
        ((time - start) / (end - start)).coerceIn(0f, 1f)

    private data class Tile(val source: Rect, val start: Float, val offsetX: Int, val offsetY: Int)

    private inner class WelcomeIcon : View(this@OnboardingActivity) {
        private val bitmap = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher,
            BitmapFactory.Options().apply { inScaled = false })
        private val paint = Paint().apply { isFilterBitmap = false }
        private val destination = RectF()
        private val bounce = OvershootInterpolator(2f)
        private val tiles = buildList {
            val random = Random(42)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            for (y in 0 until bitmap.height step 16) {
                for (x in 0 until bitmap.width step 16) {
                    val right = minOf(x + 16, bitmap.width)
                    val bottom = minOf(y + 16, bitmap.height)
                    if (!(y until bottom).any { row ->
                            (x until right).any { column -> pixels[row * bitmap.width + column] ushr 24 != 0 }
                        }) continue
                    val start = 360f * (x + y) / (bitmap.width + bitmap.height) + random.nextInt(125)
                    add(Tile(Rect(x, y, right, bottom), start, random.nextInt(-20, 21), random.nextInt(-20, 21)))
                }
            }
        }
        var playTime = WELCOME_DURATION_MS.toFloat()
            set(value) {
                field = value
                invalidate()
            }

        override fun onDraw(canvas: Canvas) {
            val x = width / 2f
            val y = height / 2f
            val size = minOf(width, height) * (112f / 170f)
            val left = x - size / 2f
            val top = y - size / 2f
            if (playTime < 940f) {
                val scaleX = size / bitmap.width
                val scaleY = size / bitmap.height
                for (tile in tiles) {
                    val progress = phase(playTime, tile.start, tile.start + 450f)
                    if (progress == 0f) continue
                    val remaining = 1f - progress
                    val dx = (tile.offsetX * size / 112f * remaining * remaining).roundToInt()
                    val dy = (tile.offsetY * size / 112f * remaining * remaining).roundToInt()
                    val src = tile.source
                    destination.set(left + src.left * scaleX + dx, top + src.top * scaleY + dy,
                        left + src.right * scaleX + dx, top + src.bottom * scaleY + dy)
                    paint.alpha = (255 * (1f - remaining * remaining)).toInt()
                    canvas.drawBitmap(bitmap, src, destination, paint)
                }
            } else {
                val settle = bounce.getInterpolation(phase(playTime, 940f, 1220f))
                val scale = 1f + 0.14f * (settle - 1f) * phase(playTime, 940f, 990f)
                canvas.save()
                canvas.scale(scale, scale, x, y)
                destination.set(left, top, left + size, top + size)
                paint.alpha = 255
                canvas.drawBitmap(bitmap, null, destination, paint)
                canvas.restore()
            }
            val glint = phase(playTime, 1140f, 1390f)
            if (glint > 0f && glint < 1f) {
                val peak = 1f - abs(2f * glint - 1f)
                val pixel = size / 38f
                val cx = x + 0.48f * size
                val cy = y - 0.46f * size
                val arm = pixel * (1f + 2f * peak)
                paint.color = withOpacity(Color.DKGRAY, (220 * peak).toInt())
                canvas.drawRect(cx - arm, cy - pixel, cx + arm, cy + pixel, paint)
                canvas.drawRect(cx - pixel, cy - arm, cx + pixel, cy + arm, paint)
                paint.color = withOpacity(Color.WHITE, (255 * peak).toInt())
                canvas.drawRect(cx - pixel / 2, cy - arm, cx + pixel / 2, cy + arm, paint)
                canvas.drawRect(cx - arm, cy - pixel / 2, cx + arm, cy + pixel / 2, paint)
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
