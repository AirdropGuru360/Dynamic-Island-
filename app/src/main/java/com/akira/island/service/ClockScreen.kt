package com.akira.island.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.provider.AlarmClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.akira.island.core.ChronoInfo
import com.akira.island.core.IslandBus
import com.akira.island.data.SettingsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
}

/**
 * Таймер и секундомер приложения «Часы» — с его экрана. Если у «Часов» выключены уведомления, Android
 * не отдаёт их никому, и узнать о таймере можно только так. Пока «Часы» открыты, остров запоминает
 * надписи со временем и обновляет их четыре раза в секунду, а ещё получает изменения экрана и нажатия:
 * «Старт» на вкладке «Таймер» — таймер сразу в острове, «Пауза» — замирает, «Сброс» — исчезает.
 * Без нажатия таймер узнаётся по тому, что время идёт: убывает — таймер, растёт — секундомер.
 * Когда приложение закрывают, остров ведёт отсчёт сам. Другие приложения так не читаются.
 */
class ClockScreenReader(private val service: AccessibilityService, private val scope: CoroutineScope) {
    private enum class Kind { TIMER, STOPWATCH }
    private enum class Hint { START, PAUSE, RESET }

    /** Надпись со временем на экране; [key] — id вида или место, [height] — главная надпись крупнее */
    private class Display(val key: String, val node: AccessibilityNodeInfo, val height: Int)

    /** Что последним видели в надписи: значение, когда оно сменилось и сколько смен подряд шли «как часы» */
    private class Seen(var value: Long, var changedAt: Long, var kind: Kind? = null, var streak: Int = 0)

    /** Что показывает остров: [base] — настенное время конца таймера или начала секундомера */
    private class Track(val key: String, val base: Long, val pausedMs: Long?)

    val active: Boolean get() = current != null

    private var current: String? = null
    private var loop: Job? = null
    private var endJob: Job? = null
    private var displays: List<Display> = emptyList()
    private var tab: Kind? = null
    private var scannedAt = 0L
    private var lastTick = 0L
    private var needRescan = true
    private val seen = HashMap<String, Seen>()
    private var hint: Hint? = null
    private var hintAt = 0L
    private var timer: Track? = null
    private var stopwatch: Track? = null
    private var timerMisses = 0
    private var stopwatchMisses = 0
    private val labels = HashMap<String, String>()

    fun onForeground(pkg: String) {
        if (!ClockApps.isClock(service, pkg)) {
            stopReading()
            return
        }
        if (pkg == current) return
        stopReading()
        current = pkg
        loop = scope.launch {
            while (isActive) {
                if (SettingsRepo.current.cardTimer && IslandBus.screenOn.value) runCatching { tick() }
                delay(250)
            }
        }
    }

    /** Экран «Часов» изменился — читаем сразу, не дожидаясь следующей четверти секунды. */
    fun onContentChanged(event: AccessibilityEvent) {
        if (event.packageName?.toString() != current) return
        if (event.contentChangeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE != 0) needRescan = true
        if (SystemClock.elapsedRealtime() - lastTick >= 80) runCatching { tick() }
    }

    /** Нажали кнопку в «Часах»: «Старт», «Пауза», «Сброс» — экран вот-вот поменяется, читаем его несколько раз. */
    fun onClicked(event: AccessibilityEvent) {
        if (event.packageName?.toString() != current) return
        val words = (event.text.joinToString(" ") + " " + (event.contentDescription ?: ""))
            .lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        hint = when {
            words.any { w -> RESET.any { w.startsWith(it) } } -> Hint.RESET
            words.any { w -> w in PAUSE_WORDS || w.startsWith("приостанов") } -> Hint.PAUSE
            words.any { w -> START.any { w.startsWith(it) } } -> Hint.START
            else -> null
        }
        hintAt = SystemClock.elapsedRealtime()
        // После нажатия экран меняется — перечитываем его сразу, без ожидания
        needRescan = true
        scannedAt = 0L
        scope.launch {
            for (wait in longArrayOf(60, 140, 250, 350)) {
                delay(wait)
                runCatching { tick() }
            }
        }
    }

    /** Ушли из «Часов»: читать перестаём, но таймер и секундомер остров ведёт дальше сам. */
    fun stopReading() {
        loop?.cancel()
        loop = null
        current = null
        displays = emptyList()
        seen.clear()
        hint = null
        needRescan = true
    }

    fun stop() {
        stopReading()
        endJob?.cancel()
        timer = null
        stopwatch = null
        IslandBus.chrono.value = IslandBus.chrono.value - TIMER_KEY - STOPWATCH_KEY
    }

    private fun tick() {
        val pkg = current ?: return
        val now = SystemClock.elapsedRealtime()
        lastTick = now
        // Полный обход экрана дорог: не чаще двух раз в секунду, а если ничего не менялось — раз в 3 секунды
        if ((needRescan && now - scannedAt > 500) || now - scannedAt > 3_000) rescan(pkg, now)
        val wall = System.currentTimeMillis()
        val hinted = hint?.takeIf { now - hintAt < 1_500 }

        var bestTimer: Pair<Display, Long>? = null
        var bestStopwatch: Pair<Display, Long>? = null
        for (d in displays) {
            val alive = runCatching { d.node.refresh() && d.node.isVisibleToUser }.getOrDefault(false)
            if (!alive) {
                needRescan = true
                continue
            }
            val text = (d.node.text ?: d.node.contentDescription)?.toString() ?: continue
            if (isTimeOfDay(text, wall)) continue
            val v = ClockScreenParser.parse(text) ?: continue
            when (observe(d.key, v, now, hinted)) {
                Kind.TIMER -> if (d.height > (bestTimer?.first?.height ?: -1)) bestTimer = d to v
                Kind.STOPWATCH -> if (d.height > (bestStopwatch?.first?.height ?: -1)) bestStopwatch = d to v
                null -> Unit
            }
        }
        bestTimer?.let { (d, v) -> timer = running(timer, d.key, wall + v) }
        bestStopwatch?.let { (d, v) -> stopwatch = running(stopwatch, d.key, wall - v) }

        // Нажали «Старт» на своей вкладке — показываем сразу, не дожидаясь, пока цифры сдвинутся
        val main = displays.maxByOrNull { it.height }
        val mainValue = main?.let { seen[it.key]?.value }
        if (hinted == Hint.START && main != null && mainValue != null) {
            val t = timer
            val w = stopwatch
            if (tab == Kind.TIMER && bestTimer == null && mainValue > 0 && (t == null || t.pausedMs != null)) {
                timer = Track(main.key, wall + mainValue, null)
                seen[main.key]?.changedAt = now
            }
            if (tab == Kind.STOPWATCH && bestStopwatch == null && (w == null || w.pausedMs != null)) {
                stopwatch = Track(main.key, wall - mainValue, null)
                seen[main.key]?.changedAt = now
            }
        }
        if (hinted == Hint.PAUSE && main != null && mainValue != null) {
            if (tab == Kind.TIMER) timer?.takeIf { it.pausedMs == null }?.let { timer = Track(it.key, it.base, mainValue) }
            if (tab == Kind.STOPWATCH) stopwatch?.takeIf { it.pausedMs == null }?.let { stopwatch = Track(it.key, it.base, mainValue) }
        }
        if (hinted == Hint.RESET) {
            if (tab == Kind.TIMER) timer = null
            if (tab == Kind.STOPWATCH) stopwatch = null
        }

        // Сразу после «Старт» цифры ещё не успели сдвинуться — это не пауза
        if (hinted != Hint.START) {
            timer = pauseIfStill(timer, now)
            stopwatch = pauseIfStill(stopwatch, now)
        }
        publish(pkg)
    }

    /**
     * Новое значение надписи. Возвращает, что это — идущий таймер или секундомер, — когда это уже ясно:
     * две смены подряд с ходом часов, или одна, если открыта своя вкладка или только что нажали «Старт».
     */
    private fun observe(key: String, v: Long, now: Long, hinted: Hint?): Kind? {
        val s = seen[key]
        if (s == null) {
            seen[key] = Seen(v, now)
            return null
        }
        if (v == s.value) return null
        val dv = v - s.value
        val since = now - s.changedAt
        val paused = listOfNotNull(timer, stopwatch).firstOrNull { it.key == key && it.pausedMs != null }
        val kind = when {
            dv < 0 && -dv <= since + 1_100 && since <= -dv + 1_300 -> Kind.TIMER
            dv > 0 && dv <= since + 1_100 && since <= dv + 1_300 -> Kind.STOPWATCH
            // Сняли с паузы: после долгой стоянки цифры снова сдвинулись на шаг
            paused != null && kotlin.math.abs(dv) <= 1_500 -> if (dv < 0) Kind.TIMER else Kind.STOPWATCH
            else -> null
        }
        s.streak = if (kind != null && kind == s.kind) s.streak + 1 else if (kind != null) 1 else 0
        s.kind = kind
        s.value = v
        s.changedAt = now
        if (kind == null) {
            // Скачок: таймер сбросили к исходному времени или секундомер обнулили
            if (timer?.key == key && dv > 1_500) timer = null
            if (v == 0L && stopwatch?.key == key) stopwatch = null
            return null
        }
        if (v == 0L) {
            if (timer?.key == key) timer = null
            return null
        }
        val sure = s.streak >= 2 || tab == kind || hinted == Hint.START || paused != null
        return if (sure) kind else null
    }

    /** Цифры показанного таймера или секундомера стоят на месте больше полутора секунд — пауза. */
    private fun pauseIfStill(track: Track?, now: Long): Track? {
        track ?: return null
        if (track.pausedMs != null) return track
        val d = displays.firstOrNull { it.key == track.key } ?: return track
        val s = seen[d.key] ?: return track
        val visible = runCatching { d.node.isVisibleToUser }.getOrDefault(false)
        return if (visible && now - s.changedAt > 1_500) Track(track.key, track.base, s.value) else track
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

    /** Все надписи со временем на экране «Часов» и открытая вкладка. Дорого — только по необходимости. */
    private fun rescan(pkg: String, now: Long) {
        scannedAt = now
        needRescan = false
        val root = service.rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        val found = ArrayList<Display>()
        val tabs = arrayOfNulls<Kind>(1)
        collect(root, found, tabs, 0, HashMap())
        tab = tabs[0]
        @Suppress("DEPRECATION")
        displays.forEach { runCatching { it.node.recycle() } }
        displays = found.sortedByDescending { it.height }.take(6)
        // Пропал с открытой вкладки «Таймер»/«Секундомер» — его удалили
        timerMisses = if (tab == Kind.TIMER && timer != null && displays.none { it.key == timer?.key }) timerMisses + 1 else 0
        stopwatchMisses = if (tab == Kind.STOPWATCH && stopwatch != null && displays.none { it.key == stopwatch?.key }) stopwatchMisses + 1 else 0
        if (timerMisses >= 2) timer = null
        if (stopwatchMisses >= 2) stopwatch = null
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableList<Display>, tabs: Array<Kind?>, depth: Int, ids: HashMap<String, Int>) {
        if (out.size > 20 || depth > 40) return
        val text = (node.text ?: node.contentDescription)?.toString()?.trim().orEmpty()
        var keep = false
        if (text.isNotEmpty() && node.isVisibleToUser) {
            if (ClockScreenParser.parse(text) != null) {
                val b = Rect()
                node.getBoundsInScreen(b)
                // У нескольких таймеров в списке id одинаковый — различаем по порядку
                val id = node.viewIdResourceName?.takeIf { it.isNotEmpty() } ?: "@${b.left},${b.top}"
                val n = (ids[id] ?: 0) + 1
                ids[id] = n
                out += Display("$id#$n", node, b.height())
                keep = true
            }
            if (node.isSelected && tabs[0] == null) {
                val t = text.lowercase()
                tabs[0] = when {
                    "секундомер" in t || "stopwatch" in t -> Kind.STOPWATCH
                    "таймер" in t || "timer" in t -> Kind.TIMER
                    else -> null
                }
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, out, tabs, depth + 1, ids)
        }
        if (!keep && depth > 0) {
            @Suppress("DEPRECATION")
            node.recycle()
        }
    }

    /** Время суток на вкладке «Часы» и в мировом времени тоже похоже на «ч:мм» — его не путаем с таймером. */
    private fun isTimeOfDay(text: String, wall: Long): Boolean {
        val t = text.filterNot { it.isWhitespace() }
        return (-1..1).any { m ->
            val c = Calendar.getInstance().apply { timeInMillis = wall + m * 60_000L }
            val h24 = c.get(Calendar.HOUR_OF_DAY)
            val h12 = (h24 + 11) % 12 + 1
            val mm = "%02d".format(c.get(Calendar.MINUTE))
            t.startsWith("$h24:$mm") || t.startsWith("%02d:$mm".format(h24)) || t.startsWith("$h12:$mm") || t.startsWith("%02d:$mm".format(h12))
        } && t.length <= 8 && seenAsClock(t)
    }

    /** «Ч:мм» без долей секунды — формат часов; у секундомера всегда есть доли или он ещё не дошёл до часа. */
    private fun seenAsClock(t: String): Boolean = !t.contains('.') && !t.contains(',')

    private fun publish(pkg: String) {
        // Если уведомления «Часов» включены, остров берёт таймер из них — второй не нужен
        val fromNotification = IslandBus.chrono.value.any { (k, v) -> v.pkg == pkg && k != TIMER_KEY && k != STOPWATCH_KEY }
        val map = IslandBus.chrono.value.toMutableMap()
        val label = labels.getOrPut(pkg) { NotificationParser.appLabel(service, pkg) }
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

    private companion object {
        const val TIMER_KEY = "clock-screen:timer"
        const val STOPWATCH_KEY = "clock-screen:stopwatch"
        // Подписи кнопок «Часов» по началу слова; «стоп» — только целым словом, иначе совпадёт «Stopwatch»
        val START = listOf("старт", "start", "начать", "пуск", "запуст", "продолж", "возобнов", "resume")
        val PAUSE_WORDS = setOf("пауза", "pause", "стоп", "stop")
        val RESET = listOf("сброс", "reset", "удал", "delete", "отмен", "cancel", "очист", "clear")
    }
}
