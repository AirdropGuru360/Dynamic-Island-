package com.akira.island.core

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock

/** Команды, которые капсула отдаёт системе: плеер, таймер, фонарик, громкость. */
object Controls {
    @Volatile
    var mediaController: MediaController? = null

    // Подставляет IslandService, пока он запущен
    var torchOn: () -> Unit = {}
    var torchOff: () -> Unit = {}
    var torchLevel: (Int) -> Unit = {}

    fun expand(id: String) {
        IslandBus.focus.value = id
        IslandBus.expanded.value = true
    }

    fun collapse() {
        IslandBus.expanded.value = false
        IslandBus.focus.value = null
    }

    // Плеер

    fun playPause() {
        val c = mediaController ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }

    fun next() {
        mediaController?.transportControls?.skipToNext()
    }

    fun prev() {
        mediaController?.transportControls?.skipToPrevious()
    }

    fun seekTo(ms: Long) {
        mediaController?.transportControls?.seekTo(ms)
    }

    // Таймер

    fun startTimer(minutes: Int) {
        val total = minutes.coerceIn(1, 99) * 60_000L
        IslandBus.timer.value = TimerState(total, SystemClock.elapsedRealtime() + total)
    }

    fun startTimerSeconds(seconds: Int) {
        val total = seconds * 1000L
        IslandBus.timer.value = TimerState(total, SystemClock.elapsedRealtime() + total)
    }

    fun pauseTimer() {
        val t = IslandBus.timer.value ?: return
        if (t.pausedLeft == null && !t.ringing) {
            IslandBus.timer.value = t.copy(pausedLeft = t.left(SystemClock.elapsedRealtime()))
        }
    }

    fun resumeTimer() {
        val t = IslandBus.timer.value ?: return
        val left = t.pausedLeft ?: return
        if (!t.ringing) IslandBus.timer.value = t.copy(endAt = SystemClock.elapsedRealtime() + left, pausedLeft = null)
    }

    fun cancelTimer() {
        IslandBus.timer.value = null
        if (IslandBus.focus.value == "timer") collapse()
    }

    // Секундомер

    fun startStopwatch() {
        IslandBus.stopwatch.value = StopwatchState(SystemClock.elapsedRealtime(), 0)
    }

    fun toggleStopwatch() {
        val w = IslandBus.stopwatch.value ?: return
        val now = SystemClock.elapsedRealtime()
        IslandBus.stopwatch.value =
            if (w.running) StopwatchState(null, w.elapsed(now)) else StopwatchState(now, w.accumulated)
    }

    fun stopStopwatch() {
        IslandBus.stopwatch.value = null
        if (IslandBus.focus.value == "stopwatch") collapse()
    }

    // Прочее

    fun openApp(context: Context, pkg: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        runCatching { context.startActivity(intent) }
        collapse()
    }

    /** Нажать кнопку из уведомления (ответить, отклонить, открыть). */
    fun send(context: Context, intent: PendingIntent?): Boolean {
        intent ?: return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val options = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                intent.send(context, 0, null, null, null, null, options.toBundle())
            } else {
                intent.send()
            }
        }.isSuccess
    }

    /** Отправить ответ через кнопку «Ответить» из уведомления — так же, как это делает шторка. */
    fun sendReply(context: Context, action: MsgAction, text: String): Boolean {
        val clean = text.trim()
        if (clean.isEmpty() || action.inputs.isEmpty()) return false
        return runCatching {
            val inputs = action.inputs.toTypedArray()
            val results = android.os.Bundle()
            inputs.forEach { results.putCharSequence(it.resultKey, clean) }
            val fill = Intent()
            android.app.RemoteInput.addResultsToIntent(inputs, fill, results)
            action.intent.send(context, 0, fill)
        }.isSuccess
    }

    // Звонки: сначала кнопка из уведомления звонилки, иначе системный сервис звонков

    @Suppress("DEPRECATION", "MissingPermission")
    fun answerCall(context: Context, call: CallInfo) {
        if (call.key == TEST_CALL) {
            IslandBus.calls.value = IslandBus.calls.value + (TEST_CALL to call.copy(incoming = false, base = System.currentTimeMillis()))
            return
        }
        if (send(context, call.answer)) return
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return
        val ok = runCatching { telecom.acceptRingingCall() }.isSuccess
        if (!ok) runCatching { telecom.showInCallScreen(false) }
    }

    @Suppress("DEPRECATION", "MissingPermission")
    fun endCall(context: Context, call: CallInfo) {
        if (call.key == TEST_CALL) {
            IslandBus.calls.value = IslandBus.calls.value - TEST_CALL
            collapse()
            return
        }
        if (send(context, if (call.incoming) call.decline else call.hangUp)) return
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return
        val ok = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && runCatching { telecom.endCall() }.getOrDefault(false)
        if (!ok) runCatching { telecom.showInCallScreen(false) }
    }

    @Suppress("MissingPermission")
    fun openCall(context: Context, call: CallInfo) {
        if (call.key == TEST_CALL) return
        if (send(context, call.open)) return
        val telecom = context.getSystemService(TelecomManager::class.java)
        if (runCatching { telecom?.showInCallScreen(false) }.isSuccess && telecom != null) return
        if (call.pkg.isNotEmpty()) openApp(context, call.pkg)
    }

    /** Пробный входящий звонок из приложения: кнопки работают, через 20 секунд он пропадает сам. */
    fun showTestCall() {
        val now = System.currentTimeMillis()
        IslandBus.calls.value = IslandBus.calls.value +
            (TEST_CALL to CallInfo(TEST_CALL, "", "Анна", null, true, now, now, null, null, null, null))
        IslandBus.scope.launch {
            delay(20_000)
            IslandBus.calls.value = IslandBus.calls.value - TEST_CALL
        }
    }

    const val TEST_CALL = "test-call"

    /** Пробная навигация: широкая плашка, потом капсула; через 10 секунд пропадает сама, даже если выйти из приложения. */
    fun showTestLive(context: Context) {
        val live = LiveInfo(
            key = TEST_LIVE, pkg = context.packageName, kind = LiveKind.NAV,
            title = "300 м", text = "Поверните направо на Тверскую улицу", sub = "",
            icon = com.akira.island.service.NotificationParser.icon(context, context.packageName), art = null,
            postTime = System.currentTimeMillis(), open = null,
            distance = "300 м", eta = "Прибытие в 18:42 · 12 мин · 4,2 км", turn = Turn.RIGHT,
            expiresAt = System.currentTimeMillis() + TEST_LIVE_MS,
        )
        IslandBus.live.value = IslandBus.live.value + (TEST_LIVE to live)
        IslandBus.post(com.akira.island.service.LiveParser.banner(context, live))
        IslandBus.scope.launch {
            delay(TEST_LIVE_MS)
            IslandBus.live.value = IslandBus.live.value - TEST_LIVE
        }
    }

    private const val TEST_LIVE_MS = 10_000L
    const val TEST_LIVE = "test-live"

    /** Куда выводится звук: системная панель громкости с выбором устройства. */
    fun openOutput(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_VOLUME)
        } else {
            Intent(Settings.ACTION_SOUND_SETTINGS)
        }
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        collapse()
    }

}
