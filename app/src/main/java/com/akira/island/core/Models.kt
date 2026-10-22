package com.akira.island.core

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.concurrent.atomic.AtomicLong

data class MediaInfo(
    val key: String,
    val pkg: String,
    val title: String,
    val artist: String,
    val album: String?,
    val art: ImageBitmap?,
    val accent: Color,
    val durationMs: Long,
    val positionMs: Long,
    /** SystemClock.elapsedRealtime() момента, к которому относится positionMs */
    val positionTime: Long,
    val speed: Float,
    val playing: Boolean,
    val canSeek: Boolean,
) {
    fun positionAt(now: Long): Long {
        val p = if (playing) positionMs + ((now - positionTime) * speed).toLong() else positionMs
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

data class LyricLine(val timeMs: Long, val text: String)

data class Lyrics(val key: String, val lines: List<LyricLine>) {
    /** Индекс строки, которая поётся в момент [pos], или -1 до первой строки. */
    fun indexAt(pos: Long): Int {
        var lo = 0
        var hi = lines.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= pos) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found
    }
}

data class ProgressInfo(
    val key: String,
    val pkg: String,
    val title: String,
    val icon: ImageBitmap?,
    val progress: Int,
    val max: Int,
    val indeterminate: Boolean,
    val postTime: Long,
) {
    val fraction: Float get() = if (max > 0) (progress.toFloat() / max).coerceIn(0f, 1f) else 0f
}

data class ChronoInfo(
    val key: String,
    val pkg: String,
    val title: String,
    val icon: ImageBitmap?,
    /** Notification.when — настенное время */
    val base: Long,
    val countDown: Boolean,
    val postTime: Long,
) {
    fun elapsed(nowWall: Long): Long = if (countDown) base - nowWall else nowWall - base
}

data class TorchState(
    val on: Boolean = false,
    val cameraId: String? = null,
    val level: Int = 1,
    val maxLevel: Int = 1,
)

data class TimerState(
    val totalMs: Long,
    /** SystemClock.elapsedRealtime() окончания */
    val endAt: Long,
    val pausedLeft: Long? = null,
    val ringing: Boolean = false,
) {
    fun left(now: Long): Long = pausedLeft ?: (endAt - now).coerceAtLeast(0)
    val paused: Boolean get() = pausedLeft != null && !ringing
}

data class StopwatchState(val startedAt: Long?, val accumulated: Long) {
    fun elapsed(now: Long): Long = accumulated + (startedAt?.let { now - it } ?: 0)
    val running: Boolean get() = startedAt != null
}

data class VolumeInfo(val level: Int, val max: Int) {
    val fraction: Float get() = if (max > 0) level.toFloat() / max else 0f
}

data class BatteryInfo(val level: Int = -1, val charging: Boolean = false)

data class Notice(
    val title: String,
    val subtitle: String? = null,
    val icon: ImageVector,
    val tint: Color,
    val trailing: String? = null,
    val id: Long = ids.incrementAndGet(),
) {
    private companion object {
        val ids = AtomicLong()
    }
}

object Tints {
    val Green = Color(0xFF34C759)
    val Red = Color(0xFFFF453A)
    val Blue = Color(0xFF0A84FF)
    val Orange = Color(0xFFFF9F0A)
    val Yellow = Color(0xFFFFD60A)
    val Purple = Color(0xFFBF5AF2)
    val Teal = Color(0xFF64D2FF)
    val Gray = Color(0xFF8E8E93)
    val Lavender = Color(0xFFC6C3F7)
}

fun formatDuration(ms: Long): String {
    val total = ms.coerceAtLeast(0) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
