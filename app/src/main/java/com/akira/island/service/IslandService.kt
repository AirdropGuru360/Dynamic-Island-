package com.akira.island.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.akira.island.core.Controls
import com.akira.island.core.IslandBus
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import com.akira.island.island.ISLAND_PAD_DP
import com.akira.island.island.IslandOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Служба спец. возможностей: только она может рисовать поверх строки состояния
 * (TYPE_ACCESSIBILITY_OVERLAY), поэтому капсула живёт здесь.
 */
class IslandService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wm: WindowManager? = null
    private var root: OverlayRoot? = null
    private val owner = OverlayLifecycleOwner()
    private var events: SystemEvents? = null
    private var torch: TorchController? = null
    private var alarm: TimerAlarm? = null

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        title = "DynamicIsland"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        SettingsRepo.init(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        IslandBus.landscape.value = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        createOverlay()
        events = SystemEvents(this).also { it.start() }
        torch = TorchController(this).also { it.start() }
        alarm = TimerAlarm(this, scope).also { it.start() }
        IslandBus.serviceRunning.value = true

        scope.launch {
            combine(SettingsRepo.state, IslandBus.landscape) { s, land -> s to land }
                .collect { (s, land) -> applyLayout(s, land) }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createOverlay() {
        val compose = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) { IslandOverlay() }
            }
        }
        val view = OverlayRoot(this) {
            // Касание мимо раскрытого острова сворачивает его
            if (IslandBus.expanded.value) Controls.collapse()
        }
        view.addView(compose)
        owner.create()
        owner.attach(view)
        runCatching { wm?.addView(view, params) }.onSuccess { root = view }
    }

    private fun applyLayout(s: IslandSettings, landscape: Boolean) {
        val view = root ?: return
        val visible = s.enabled && !(s.hideInLandscape && landscape)
        view.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) Controls.collapse()
        params.x = dp(s.offsetX)
        params.y = dp(s.offsetY - ISLAND_PAD_DP)
        runCatching { wm?.updateViewLayout(view, params) }
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Шторка и клавиатура не меняют «открытое приложение»
        if (pkg == "com.android.systemui" || event.className?.contains("InputMethod") == true) return
        IslandBus.foregroundPackage.value = pkg
    }

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        IslandBus.landscape.value = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    override fun onDestroy() {
        IslandBus.serviceRunning.value = false
        events?.stop()
        torch?.stop()
        alarm?.stop()
        scope.cancel()
        root?.let { runCatching { wm?.removeView(it) } }
        root = null
        owner.destroy()
        super.onDestroy()
    }
}

/** Корневой вид окна: ловит касания вне острова. */
@SuppressLint("ViewConstructor")
class OverlayRoot(context: Context, private val onOutside: () -> Unit) : FrameLayout(context) {
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            onOutside()
            return true
        }
        return super.dispatchTouchEvent(ev)
    }
}
