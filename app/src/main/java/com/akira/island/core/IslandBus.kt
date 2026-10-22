package com.akira.island.core

import com.akira.island.data.SettingsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Общее состояние острова. Службы пишут сюда, капсула и приложение читают.
 * Всё живёт в одном процессе, поэтому хватает обычного синглтона.
 */
object IslandBus {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val serviceRunning = MutableStateFlow(false)
    val expanded = MutableStateFlow(false)
    /** id активности, которую пользователь выбрал (например, кликом по пузырю) */
    val focus = MutableStateFlow<String?>(null)

    val media = MutableStateFlow<MediaInfo?>(null)
    val lyrics = MutableStateFlow<Lyrics?>(null)
    /** Как идёт поиск текста для трека с ключом first */
    val lyricsStatus = MutableStateFlow<Pair<String, LyricsStatus>?>(null)
    val progress = MutableStateFlow<Map<String, ProgressInfo>>(emptyMap())
    val chrono = MutableStateFlow<Map<String, ChronoInfo>>(emptyMap())
    val calls = MutableStateFlow<Map<String, CallInfo>>(emptyMap())
    val torch = MutableStateFlow(TorchState())
    val timer = MutableStateFlow<TimerState?>(null)
    val stopwatch = MutableStateFlow<StopwatchState?>(null)
    val notice = MutableStateFlow<Notice?>(null)
    val volume = MutableStateFlow<VolumeInfo?>(null)
    val battery = MutableStateFlow(BatteryInfo())
    val foregroundPackage = MutableStateFlow<String?>(null)
    val landscape = MutableStateFlow(false)
    /** Открыт экран «Размер и положение»: остров красный, чтобы подогнать его под камеру */
    val calibrating = MutableStateFlow(false)
    /** Последнее касание раскрытого острова — чтобы не сворачивать его из-под пальца */
    @Volatile
    var lastTouch = 0L

    private val queue = Channel<Notice>(capacity = 6, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var volumeJob: Job? = null

    init {
        scope.launch {
            for (n in queue) {
                notice.value = n
                val duration = n.durationMs ?: SettingsRepo.current.noticeDurationMs.toLong()
                // Уведомление уходит по времени или раньше, если его смахнули кликом
                withTimeoutOrNull(duration) { notice.first { it?.id != n.id } }
                if (notice.value?.id == n.id) notice.value = null
                delay(220)
            }
        }
    }

    fun post(n: Notice) {
        queue.trySend(n)
    }

    fun showVolume(level: Int, max: Int) {
        volume.value = VolumeInfo(level, max)
        volumeJob?.cancel()
        volumeJob = scope.launch {
            delay(1800)
            volume.value = null
        }
    }
}
