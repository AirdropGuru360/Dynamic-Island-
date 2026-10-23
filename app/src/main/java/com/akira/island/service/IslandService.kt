package com.akira.island.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.akira.island.core.Controls
import com.akira.island.core.IslandBus
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import com.akira.island.island.ISLAND_PAD_DP
import com.akira.island.island.IslandOverlay
import com.akira.island.ui.InterTypography
import com.akira.island.update.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * Служба спец. возможностей: только она может рисовать поверх строки состояния
 * (TYPE_ACCESSIBILITY_OVERLAY), поэтому капсула живёт здесь.
 *
 * Окон два. Окно рисования — на всю ширину экрана, сквозь него проходят касания, и его ширина
 * никогда не меняется: если менять размер окна во время анимации, система на кадр показывает
 * старую картинку в новом месте, и остров дёргается. Невидимое окно касаний лежит ровно
 * над островом, двигается за ним и передаёт касания в окно рисования.
 */
class IslandService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var display: FrameLayout? = null
    private var touch: TouchRoot? = null
    private val owner = OverlayLifecycleOwner()
    private var events: SystemEvents? = null
    private var torch: TorchController? = null
    private var alarm: TimerAlarm? = null
    private var calls: CallMonitor? = null
    private var navReader: NavScreenReader? = null
    private var clockReader: ClockScreenReader? = null
    private var alarmMonitor: AlarmMonitor? = null

    /** Клавиатура уже появлялась, пока открыт ввод ответа: когда её спрячут, ввод закрывается */
    private var keyboardSeen = false
    /** Звонит таймер или идёт входящий вызов; [wakeBlocked] — экран выключили кнопкой, пока звонило */
    private var alerting = false
    private var wakeBlocked = false
    private var lastScreenOn = true
    private var replySince = 0L
    private var imePackages: Set<String> = emptySet()
    private var imeCheckedAt = 0L

    private var tiltOn = false
    private var tiltSmooth = 0f
    private val tiltListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            // Наклон влево-вправо от -1 до 1; сглаживаем, чтобы блик плыл, а не дрожал
            tiltSmooth += (e.values[0] / 9.81f).coerceIn(-1f, 1f).minus(tiltSmooth) * 0.12f
            if (abs(tiltSmooth - IslandBus.glassTilt.value) > 0.015f) IslandBus.glassTilt.value = tiltSmooth
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private var visible = true
    private var body = Rect.Zero
    private var bubble = Rect.Zero
    /** Высокое окно рисования на время анимации; в покое — по нижнему краю острова. */
    private var tall = true
    private val settle = Runnable {
        tall = false
        updateDisplayHeight()
    }

    private val displayParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = "DynamicIsland"
        cutoutAlways()
    }

    private val touchParams = WindowManager.LayoutParams(
        1,
        1,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = "DynamicIslandTouch"
        x = -10
        y = -10
        cutoutAlways()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        SettingsRepo.init(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        IslandBus.landscape.value = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        createWindows()
        events = SystemEvents(this).also { it.start() }
        torch = TorchController(this).also { it.start() }
        alarm = TimerAlarm(this, scope).also { it.start() }
        calls = CallMonitor(this, scope).also { it.start() }
        navReader = NavScreenReader(this, scope)
        clockReader = ClockScreenReader(this, scope)
        alarmMonitor = AlarmMonitor(this).also { it.start() }
        IslandBus.serviceRunning.value = true

        // Пока вводят ответ на уведомление, окну нужен фокус, иначе клавиатура не покажется
        scope.launch {
            IslandBus.reply.collect {
                keyboardSeen = false
                replySince = SystemClock.elapsedRealtime()
                setInputFocus(it != null)
            }
        }
        // Звонит таймер, будильник «Часов» или входящий вызов: экран включается и не гаснет, пока звонит
        scope.launch {
            combine(IslandBus.timer, IslandBus.calls, IslandBus.ringingAlarms, IslandBus.screenOn) { t, calls, alarms, on ->
                (t?.ringing == true || calls.values.any { it.incoming } || alarms.isNotEmpty()) to on
            }.distinctUntilChanged().collect { (alert, on) -> onAlert(alert, on) }
        }
        // Телефон заблокировали — отвечать с экрана блокировки нельзя
        scope.launch { IslandBus.locked.collect { if (it) IslandBus.reply.value = null } }

        // Новая версия на GitHub: проверка сама ограничена разом в 6 часов, здесь только будим её
        scope.launch {
            while (isActive) {
                if (IslandBus.screenOn.value) UpdateChecker.check(this@IslandService, force = false, announce = true)
                delay(60 * 60 * 1000L)
            }
        }

        // Liquid Glass: блики сдвигаются при наклоне телефона, датчик слушаем только пока стекло включено и экран горит
        scope.launch {
            combine(SettingsRepo.state.map { it.liquidGlass && it.enabled }, IslandBus.screenOn) { glass, on -> glass && on }
                .distinctUntilChanged()
                .collect { setTiltEnabled(it) }
        }

        scope.launch {
            combine(SettingsRepo.state, IslandBus.landscape) { s, land -> s to land }
                .collect { (s, land) -> applySettings(s, land) }
        }
    }

    private fun createWindows() {
        val compose = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                MaterialTheme(colorScheme = darkColorScheme(), typography = InterTypography) {
                    IslandOverlay(
                        onTransition = ::onTransition,
                        onBodyBounds = { r -> if (r != body) { body = r; onBoundsChanged() } },
                        onBubbleBounds = { r -> if (r != bubble) { bubble = r; onBoundsChanged() } },
                    )
                }
            }
        }
        val drawRoot = object : FrameLayout(this) {
            // Пока вводят ответ, окно острова в фокусе и «Назад» приходит сюда: закрываем ввод, а не глотаем жест
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && IslandBus.reply.value != null) {
                    if (event.action == KeyEvent.ACTION_UP) IslandBus.reply.value = null
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        drawRoot.addView(compose)
        owner.create()
        owner.attach(drawRoot)
        displayParams.height = tallHeight()
        runCatching { wm?.addView(drawRoot, displayParams) }.onSuccess { display = drawRoot }

        val touchRoot = TouchRoot(this, { display }, ::onOutsideTouch)
        runCatching { wm?.addView(touchRoot, touchParams) }.onSuccess { touch = touchRoot }
        scheduleSettle()
    }

    /**
     * Касание мимо раскрытого острова сворачивает его, мимо поля ответа — закрывает ввод.
     * Нажатия на клавиатуру тоже приходят сюда как «мимо» (она — отдельное окно), а где именно
     * коснулись, система не сообщает. Поэтому, пока клавиатура на экране, ввод не закрываем:
     * его закрывает «Назад» (клавиатура прячется) или смахивание плашки вверх.
     */
    private fun onOutsideTouch() {
        if (IslandBus.reply.value != null) {
            if (!keyboardShown()) IslandBus.reply.value = null
            return
        }
        if (IslandBus.expanded.value) Controls.collapse()
    }

    /** Видна ли клавиатура. Если список окон недоступен, считаем, что видна, — так ввод не оборвётся. */
    private fun keyboardShown(): Boolean = runCatching {
        val list = windows
        list.isEmpty() || list.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
    }.getOrDefault(true)

    private fun applySettings(s: IslandSettings, landscape: Boolean) {
        visible = s.enabled && !(s.hideInLandscape && landscape)
        display?.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) Controls.collapse()
        val y = dp(s.offsetY - ISLAND_PAD_DP)
        if (displayParams.y != y) {
            displayParams.y = y
            display?.let { v -> runCatching { wm?.updateViewLayout(v, displayParams) } }
        }
        updateTouchWindow()
    }

    private fun onTransition() {
        if (!tall) {
            tall = true
            updateDisplayHeight()
        }
        scheduleSettle()
    }

    private fun onBoundsChanged() {
        updateTouchWindow()
        val bottom = islandBottom()
        if (!tall && bottom > displayParams.height) {
            tall = true
            updateDisplayHeight()
        }
        scheduleSettle()
    }

    private fun scheduleSettle() {
        handler.removeCallbacks(settle)
        handler.postDelayed(settle, SETTLE_MS)
    }

    private fun islandBottom(): Int =
        ceil(max(body.bottom, bubble.bottom)).toInt() + dp(ISLAND_PAD_DP)

    /** Меняется только высота окна, верх остаётся на месте — картинка при этом не прыгает. */
    private fun updateDisplayHeight() {
        val view = display ?: return
        val h = if (tall || IslandBus.expanded.value) tallHeight() else islandBottom().coerceAtLeast(dp(ISLAND_PAD_DP * 2))
        if (displayParams.height == h) return
        displayParams.height = h
        runCatching { wm?.updateViewLayout(view, displayParams) }
    }

    private fun updateTouchWindow() {
        val view = touch ?: return
        val area = when {
            body.isEmpty -> bubble
            bubble.isEmpty -> body
            else -> Rect(
                minOf(body.left, bubble.left), minOf(body.top, bubble.top),
                maxOf(body.right, bubble.right), maxOf(body.bottom, bubble.bottom),
            )
        }
        val usable = visible && !area.isEmpty
        val origin = IntArray(2)
        display?.getLocationOnScreen(origin)
        val x = if (usable) origin[0] + floor(area.left).toInt() else -10
        val y = if (usable) origin[1] + floor(area.top).toInt() else -10
        val w = if (usable) ceil(area.width).toInt().coerceAtLeast(1) else 1
        val h = if (usable) ceil(area.height).toInt().coerceAtLeast(1) else 1
        val flags = if (usable) touchParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        else touchParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (touchParams.x == x && touchParams.y == y && touchParams.width == w && touchParams.height == h &&
            touchParams.flags == flags
        ) return
        touchParams.x = x
        touchParams.y = y
        touchParams.width = w
        touchParams.height = h
        touchParams.flags = flags
        runCatching { wm?.updateViewLayout(view, touchParams) }
    }

    private fun setTiltEnabled(on: Boolean) {
        if (on == tiltOn) return
        tiltOn = on
        val sm = getSystemService(SensorManager::class.java) ?: return
        if (on) {
            val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
            sm.registerListener(tiltListener, sensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            sm.unregisterListener(tiltListener)
            tiltSmooth = 0f
            IslandBus.glassTilt.value = 0f
        }
    }

    private fun setInputFocus(on: Boolean) {
        val view = display ?: return
        val flag = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        val flags = if (on) displayParams.flags and flag.inv() else displayParams.flags or flag
        if (flags == displayParams.flags) return
        displayParams.flags = flags
        displayParams.softInputMode = if (on) WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        else WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
        runCatching { wm?.updateViewLayout(view, displayParams) }
    }

    /**
     * Звонит таймер или идёт входящий вызов — экран включается и не гаснет. Если его выключили кнопкой
     * питания, пока звонит, остров больше не включает его до следующего звонка.
     */
    private fun onAlert(alert: Boolean, screenOn: Boolean) {
        if (!alert) {
            wakeBlocked = false
        } else if (alerting && lastScreenOn && !screenOn) {
            wakeBlocked = true
        }
        if (alert && !alerting && !screenOn) ScreenWaker.wake(this, scope) { alerting && !wakeBlocked }
        alerting = alert
        lastScreenOn = screenOn
        setAlarmScreen(keepOn = alert, turnOn = alert && !wakeBlocked)
    }

    /**
     * Флаг «включить экран» у окна без Activity система выполняет при каждой его перекладке, если экран
     * погашен, — так остров будит телефон сам. «Не гасить экран» держит его включённым, пока звонит.
     */
    @Suppress("DEPRECATION")
    private fun setAlarmScreen(keepOn: Boolean, turnOn: Boolean) {
        val view = display ?: return
        val mask = WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        var flags = displayParams.flags and mask.inv()
        if (keepOn) flags = flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        if (turnOn) flags = flags or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        if (flags == displayParams.flags) return
        displayParams.flags = flags
        runCatching { wm?.updateViewLayout(view, displayParams) }
    }

    private fun tallHeight() = dp(480)

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> onWindowsChanged()
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> onWindowState(event)
        }
    }

    /** Пока открыт ввод ответа: клавиатуру спрятали («Назад») — ввод закрывается, как в шторке. */
    private fun onWindowsChanged() {
        if (IslandBus.reply.value == null) return
        if (keyboardShown()) {
            keyboardSeen = true
        } else if (keyboardSeen && SystemClock.elapsedRealtime() - replySince > 1000) {
            // Первую секунду не смотрим: клавиатура другого приложения может спрятаться перед нашей
            IslandBus.reply.value = null
        }
    }

    private fun onWindowState(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        // Шторка, клавиатура и сам остров не меняют «открытое приложение»
        val cls = event.className?.toString().orEmpty()
        if (pkg == "com.android.systemui" || isKeyboard(pkg, cls)) return
        // Окно оплаты из сервисов Google (поднесли телефон к терминалу) — не «открытое приложение»
        if (pkg == PayMonitor.GMS && "tapandpay" in cls.lowercase()) {
            PayMonitor.onTapAndPay(this)
            return
        }
        if (pkg == packageName && !cls.endsWith("Activity")) return
        // Открыли банк или кошелёк из другого приложения — подсказка оплаты
        if (IslandBus.foregroundPackage.value != pkg && pkg != packageName) PayMonitor.onAppOpened(this, pkg)
        // Открыт навигатор — читаем с его экрана расстояние и манёвр, ушли из него — перестаём
        navReader?.onForeground(pkg)
        // Открыты «Часы» — читаем с экрана таймер и секундомер (если их уведомления выключены)
        clockReader?.onForeground(pkg)
        IslandBus.foregroundPackage.value = pkg
    }

    /**
     * Окно клавиатуры: у всех клавиатур это android.inputmethodservice.SoftInputWindow, на всякий
     * случай сверяемся и со списком включённых клавиатур (обновляется раз в минуту).
     */
    private fun isKeyboard(pkg: String, cls: String): Boolean {
        if (cls.contains("inputmethod", ignoreCase = true)) return true
        val now = SystemClock.elapsedRealtime()
        if (now - imeCheckedAt > 60_000) {
            imeCheckedAt = now
            imePackages = runCatching {
                getSystemService(InputMethodManager::class.java)?.enabledInputMethodList.orEmpty().map { it.packageName }.toSet()
            }.getOrDefault(emptySet())
        }
        return pkg in imePackages
    }

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        IslandBus.landscape.value = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    override fun onDestroy() {
        handler.removeCallbacks(settle)
        setTiltEnabled(false)
        IslandBus.serviceRunning.value = false
        events?.stop()
        torch?.stop()
        alarm?.stop()
        calls?.stop()
        navReader?.stop()
        clockReader?.stop()
        alarmMonitor?.stop()
        scope.cancel()
        touch?.let { runCatching { wm?.removeView(it) } }
        display?.let { runCatching { wm?.removeView(it) } }
        touch = null
        display = null
        owner.destroy()
        super.onDestroy()
    }
}

private fun WindowManager.LayoutParams.cutoutAlways() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
}

/** Невидимое окно над островом: пересылает касания в окно рисования. */
@SuppressLint("ViewConstructor")
class TouchRoot(
    context: Context,
    private val target: () -> View?,
    private val onOutside: () -> Unit,
) : View(context) {
    private val origin = IntArray(2)

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            onOutside()
            return true
        }
        val view = target() ?: return false
        view.getLocationOnScreen(origin)
        val copy = MotionEvent.obtain(ev)
        // rawX - x — это левый верхний угол окна касаний на экране
        copy.offsetLocation(ev.rawX - ev.x - origin[0], ev.rawY - ev.y - origin[1])
        view.dispatchTouchEvent(copy)
        copy.recycle()
        return true
    }
}

/** Сколько ждать после последнего изменения формы, прежде чем опустить высоту окна рисования. */
private const val SETTLE_MS = 700L
