package com.akira.island.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.provider.AlarmClock
import android.view.accessibility.AccessibilityNodeInfo
import com.akira.island.core.ChronoInfo
import com.akira.island.core.IslandBus
import com.akira.island.data.SettingsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs

/** Приложения «Часы»: те, что умеют ставить таймер, плюс известные по пакету. */
object ClockApps {
    private val known = setOf(
        "com.google.android.deskclock",
        "com.android.deskclock",
        "com.sec.android.app.clockpackage",
        "com.oneplus.deskclock",
        "com.coloros.alarmclock",
        "com.oplus.alarmclock",
        "com.huawei.deskclock",
        "com.motorola.timeweatherwidget",
    )
    private var found: Set<String>? = null

    fun packages(context: Context): Set<String> = found ?: (known + runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.queryIntentActivities(Intent(AlarmClock.ACTION_SET_TIMER), 0).map { it.activityInfo.packageName }
    }.getOrDefault(emptyList())).also { found = it }

    fun isClock(context: Context, pkg: String) = pkg in packages(context)
}

/** Разбор надписей экрана «Часов». Отдельно от Android, чтобы можно было проверить на примерах. */
object ClockScreenParser {
    /** «4:59», «00:04:59», «01:05,23», «1:02:03.4» */
    private val time = Regex("""^(?:(\d{1,2}):)?(\d{1,2}):(\d{2})(?:[.,](\d{1,3}))?$""")

    fun parse(text: String): Long? {
        val m = time.matchEntire(text.filterNot { it.isWhitespace() }) ?: return null
        val h = m.groupValues[1].toLongOrNull() ?: 0L
        val min = m.groupValues[2].toLong()
        val sec = m.groupValues[3].toLong()
        if (sec >= 60 || (m.groupValues[1].isNotEmpty() && min >= 60)) return null
        val frac = m.groupValues[4].takeIf { it.isNotEmpty() }?.padEnd(3, '0')?.toLong() ?: 0L
        return ((h * 60 + min) * 60 + sec) * 1000 + frac
    }

    enum class Change { TIMER, STOPWATCH, STILL, OTHER }

    /**
     * Как изменилось значение [before] → [after] за [elapsed] мс настоящего времени. Таймер убывает,
     * секундомер растёт с ходом часов; допуск — секунда, на которую округлены цифры.
     */
    fun change(before: Long, after: Long, elapsed: Long): Change {
        val delta = after - before
        return when {
            delta == 0L -> if (elapsed >= 2_000) Change.STILL else Change.OTHER
            delta < 0 && abs(-delta - elapsed) <= 1_100 -> Change.TIMER
            delta > 0 && abs(delta - elapsed) <= 1_100 -> Change.STOPWATCH
            else -> Change.OTHER
        }
    }
}

/**
 * Таймер и секундомер приложения «Часы» — с его экрана. Если у «Часов» выключены уведомления, Android
 * не отдаёт их никому, и узнать о таймере можно только так: пока «Часы» открыты, раз в полсекунды остров
 * читает время на их экране. Убывает с ходом часов — это таймер, растёт — секундомер. Когда приложение
 * закрывают, остров ведёт отсчёт сам. Другие приложения так не читаются.
 */
class ClockScreenReader(private val service: AccessibilityService, private val scope: CoroutineScope) {
    /** Надпись со временем: где стоит, высота (главная — самая крупная) и значение в мс */
    private class Item(val key: String, val height: Int, val value: Long)
    private class Snapshot(val at: Long, val items: Map<String, Item>, val tab: Kind?)
    private enum class Kind { TIMER, STOPWATCH }

    /** Что показывает остров: [base] — настенное время конца таймера или начала секундомера */
    private class Track(val key: String, val base: Long, val pausedMs: Long?)

    private var job: Job? = null
    private var endJob: Job? = null
    private var current: String? = null
    private val history = ArrayDeque<Snapshot>()
    private var timer: Track? = null
    private var stopwatch: Track? = null
    private var timerMisses = 0
    private var stopwatchMisses = 0

    fun onForeground(pkg: String) {
        if (!ClockApps.isClock(service, pkg)) {
            stopReading()
            return
        }
        if (pkg == current && job?.isActive == true) return
        stopReading()
        current = pkg
        job = scope.launch {
            while (isActive) {
                if (!IslandBus.screenOn.value) IslandBus.screenOn.first { it }
                if (SettingsRepo.current.cardTimer) runCatching { read(pkg) }
                delay(500)
            }
        }
    }

    /** Ушли из «Часов»: читать перестаём, но таймер и секундомер остров ведёт дальше сам. */
    fun stopReading() {
        job?.cancel()
        job = null
        current = null
        history.clear()
    }

    fun stop() {
        stopReading()
        endJob?.cancel()
        timer = null
        stopwatch = null
        IslandBus.chrono.value = IslandBus.chrono.value - TIMER_KEY - STOPWATCH_KEY
    }

    private fun read(pkg: String) {
        val root = service.rootInActiveWindow ?: return
        val items = HashMap<String, Item>()
        val tab = arrayOfNulls<Kind>(1)
        if (root.packageName?.toString() == pkg) collect(root, items, tab, 0)
        @Suppress("DEPRECATION")
        root.recycle()

        val now = SystemClock.elapsedRealtime()
        val wall = System.currentTimeMillis()
        dropClockFace(items, wall)
        history.addLast(Snapshot(now, items, tab[0]))
        while (history.size > 10) history.removeFirst()
        // Сравниваем с самым давним чтением за последние 4 секунды, но не моложе 0,9 с
        val ref = history.firstOrNull { now - it.at in 900L..4_000L } ?: return
        val elapsed = now - ref.at

        var runningTimer: Item? = null
        var runningStopwatch: Item? = null
        for (item in items.values) {
            val before = ref.items[item.key] ?: continue
            when (ClockScreenParser.change(before.value, item.value, elapsed)) {
                ClockScreenParser.Change.TIMER -> if (item.height > (runningTimer?.height ?: -1)) runningTimer = item
                ClockScreenParser.Change.STOPWATCH -> if (item.height > (runningStopwatch?.height ?: -1)) runningStopwatch = item
                else -> Unit
            }
        }

        if (runningTimer != null) timerMisses = 0
        if (runningStopwatch != null) stopwatchMisses = 0
        timer = when {
            runningTimer != null -> running(timer, runningTimer.key, wall + runningTimer.value)
            else -> follow(timer, items, ref, elapsed, tab[0] == Kind.TIMER, isTimer = true)
        }
        stopwatch = when {
            runningStopwatch != null -> running(stopwatch, runningStopwatch.key, wall - runningStopwatch.value)
            else -> follow(stopwatch, items, ref, elapsed, tab[0] == Kind.STOPWATCH, isTimer = false)
        }
        publish(pkg)
    }

    /**
     * Идёт. Цифры на экране округлены до секунды, поэтому новый расчёт гуляет на ±1 с — пока он близок
     * к прежнему, оставляем прежний, иначе время в острове дёргалось бы. Сильно сдвинулось (+1:00) — берём новое.
     */
    private fun running(old: Track?, key: String, base: Long): Track = when {
        old == null || old.pausedMs != null || abs(old.base - base) >= 1_500 -> Track(key, base, null)
        old.key == key -> old
        else -> Track(key, old.base, null)
    }

    /**
     * Уже показанный таймер или секундомер, который сейчас не идёт: стоит на месте — пауза,
     * обнулился — сброшен. Пропал с открытой вкладки «Таймер»/«Секундомер» — удалён.
     */
    private fun follow(track: Track?, items: Map<String, Item>, ref: Snapshot, elapsed: Long, onItsTab: Boolean, isTimer: Boolean): Track? {
        track ?: return null
        val item = items[track.key]
        if (item == null) {
            if (!onItsTab) return track
            val misses = if (isTimer) ++timerMisses else ++stopwatchMisses
            return if (misses >= 3) null else track
        }
        if (isTimer) timerMisses = 0 else stopwatchMisses = 0
        if (item.value == 0L) return null
        val before = ref.items[track.key] ?: return track
        return if (ClockScreenParser.change(before.value, item.value, elapsed) == ClockScreenParser.Change.STILL) {
            Track(track.key, track.base, item.value)
        } else {
            track
        }
    }

    /** Время суток на вкладке «Часы» тоже похоже на «ч:мм:сс» и идёт вперёд — его не путаем с секундомером. */
    private fun dropClockFace(items: MutableMap<String, Item>, wall: Long) {
        val c = Calendar.getInstance().apply { timeInMillis = wall }
        val day = ((c.get(Calendar.HOUR_OF_DAY) * 60L + c.get(Calendar.MINUTE)) * 60 + c.get(Calendar.SECOND)) * 1000
        val half = day % (12 * 3_600_000L)
        items.values.removeAll { abs(it.value - day) < 3_000 || abs(it.value - half) < 3_000 }
    }

    private fun publish(pkg: String) {
        // Если уведомления «Часов» включены, остров берёт таймер из них — второй не нужен
        val fromNotification = IslandBus.chrono.value.any { (k, v) -> v.pkg == pkg && k != TIMER_KEY && k != STOPWATCH_KEY }
        val map = IslandBus.chrono.value.toMutableMap()
        val label = NotificationParser.appLabel(service, pkg)
        val icon = NotificationParser.icon(service, pkg)
        val t = timer?.takeUnless { fromNotification }
        val w = stopwatch?.takeUnless { fromNotification }
        if (t != null) {
            val old = map[TIMER_KEY]
            map[TIMER_KEY] = ChronoInfo(TIMER_KEY, pkg, "Таймер · $label", icon, t.base, true,
                old?.postTime ?: System.currentTimeMillis(), pausedMs = t.pausedMs)
        } else {
            map.remove(TIMER_KEY)
        }
        if (w != null) {
            val old = map[STOPWATCH_KEY]
            map[STOPWATCH_KEY] = ChronoInfo(STOPWATCH_KEY, pkg, "Секундомер · $label", icon, w.base, false,
                old?.postTime ?: System.currentTimeMillis(), pausedMs = w.pausedMs)
        } else {
            map.remove(STOPWATCH_KEY)
        }
        if (map != IslandBus.chrono.value) IslandBus.chrono.value = map
        scheduleEnd()
    }

    /** Таймер дошёл до нуля — дальше звонят сами «Часы», а остров его убирает. */
    private fun scheduleEnd() {
        endJob?.cancel()
        val t = timer ?: return
        if (t.pausedMs != null) return
        endJob = scope.launch {
            delay((t.base - System.currentTimeMillis()).coerceAtLeast(0) + 1_500)
            if (timer === t) {
                timer = null
                IslandBus.chrono.value = IslandBus.chrono.value - TIMER_KEY
            }
        }
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableMap<String, Item>, tab: Array<Kind?>, depth: Int) {
        if (out.size > 60 || depth > 40) return
        val text = (node.text ?: node.contentDescription)?.toString()?.trim().orEmpty()
        if (text.isNotEmpty() && node.isVisibleToUser) {
            ClockScreenParser.parse(text)?.let { value ->
                val b = Rect()
                node.getBoundsInScreen(b)
                val key = "${node.viewIdResourceName.orEmpty()}@${b.left},${b.top}"
                out[key] = Item(key, b.height(), value)
            }
            if (node.isSelected && tab[0] == null) {
                val t = text.lowercase()
                tab[0] = when {
                    "секундомер" in t || "stopwatch" in t -> Kind.STOPWATCH
                    "таймер" in t || "timer" in t -> Kind.TIMER
                    else -> null
                }
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, out, tab, depth + 1)
            @Suppress("DEPRECATION")
            child.recycle()
        }
    }

    private companion object {
        const val TIMER_KEY = "clock-screen:timer"
        const val STOPWATCH_KEY = "clock-screen:stopwatch"
    }
}
