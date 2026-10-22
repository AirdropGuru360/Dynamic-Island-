package com.akira.island.island

import com.akira.island.core.BatteryInfo
import com.akira.island.core.CallInfo
import com.akira.island.core.ChronoInfo
import com.akira.island.core.MediaInfo
import com.akira.island.core.Notice
import com.akira.island.core.ProgressInfo
import com.akira.island.core.StopwatchState
import com.akira.island.core.TimerState
import com.akira.island.core.TorchState
import com.akira.island.core.VolumeInfo

/** То, что сейчас может показывать остров. Короткие (transient) не раскрываются и не уходят в пузырь. */
sealed class Act(val id: String, val transient: Boolean = false) {
    open val pkg: String? get() = null

    data object Idle : Act("idle")
    data class NoticeA(val n: Notice) : Act("notice", transient = true)
    data class VolumeA(val v: VolumeInfo) : Act("volume", transient = true)
    data class TimerA(val t: TimerState) : Act("timer")
    data class StopwatchA(val w: StopwatchState) : Act("stopwatch")
    data class TorchA(val t: TorchState) : Act("torch")
    data class ChronoA(val c: ChronoInfo) : Act("chrono") {
        override val pkg: String get() = c.pkg
    }
    data class MediaA(val m: MediaInfo) : Act("media") {
        override val pkg: String get() = m.pkg
    }
    data class ProgressA(val p: ProgressInfo) : Act("progress") {
        override val pkg: String get() = p.pkg
    }
    data class CallA(val c: CallInfo) : Act("call") {
        override val pkg: String get() = c.pkg
    }
    data class ChargingA(val b: BatteryInfo) : Act("charging")
}
