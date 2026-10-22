package com.akira.island.service

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.akira.island.core.IslandBus
import com.akira.island.core.TimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Доводит таймер острова до звонка и звонит, пока его не выключат. */
class TimerAlarm(private val context: Context, private val scope: CoroutineScope) {
    private var collectJob: Job? = null
    private var finishJob: Job? = null
    private var autoStopJob: Job? = null
    private var ringtone: Ringtone? = null
    private var vibrating = false

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun start() {
        collectJob = scope.launch { IslandBus.timer.collect { onTimer(it) } }
    }

    fun stop() {
        collectJob?.cancel()
        finishJob?.cancel()
        stopRinging()
    }

    private fun onTimer(t: TimerState?) {
        finishJob?.cancel()
        if (t == null || !t.ringing) stopRinging()
        if (t == null) return
        if (t.ringing) {
            startRinging()
        } else if (t.pausedLeft == null) {
            finishJob = scope.launch {
                delay(t.left(SystemClock.elapsedRealtime()))
                if (IslandBus.timer.value == t) IslandBus.timer.value = t.copy(pausedLeft = 0, ringing = true)
            }
        }
    }

    private fun startRinging() {
        if (ringtone != null || vibrating) return
        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ringtone = runCatching {
            RingtoneManager.getRingtone(context, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                play()
            }
        }.getOrNull()
        vibrator?.let {
            runCatching { it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0)) }
            vibrating = true
        }
        // Звонить вечно незачем: через минуту таймер гаснет сам
        autoStopJob = scope.launch {
            delay(60_000)
            if (IslandBus.timer.value?.ringing == true) IslandBus.timer.value = null
        }
    }

    private fun stopRinging() {
        autoStopJob?.cancel()
        ringtone?.let { runCatching { it.stop() } }
        ringtone = null
        if (vibrating) runCatching { vibrator?.cancel() }
        vibrating = false
    }
}
