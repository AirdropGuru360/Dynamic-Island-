@file:OptIn(ExperimentalFoundationApi::class)

package com.akira.island.island

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.BatteryInfo
import com.akira.island.core.CallInfo
import com.akira.island.core.ChronoInfo
import com.akira.island.core.Controls
import com.akira.island.core.Lyrics
import com.akira.island.core.MediaInfo
import com.akira.island.core.ProgressInfo
import com.akira.island.core.StopwatchState
import com.akira.island.core.Tints
import com.akira.island.core.TimerState
import com.akira.island.core.TorchState
import com.akira.island.core.formatDuration
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Dim = Color.White.copy(alpha = 0.55f)

@Composable
fun ExpandedContent(act: Act, s: IslandSettings, lyrics: Lyrics?, battery: BatteryInfo, width: Dp) {
    Box(Modifier.width(width).padding(horizontal = 20.dp, vertical = 18.dp)) {
        when (act) {
            is Act.MediaA -> MediaExpanded(act.m, s, lyrics)
            is Act.TimerA -> TimerExpanded(act.t)
            is Act.StopwatchA -> StopwatchExpanded(act.w)
            is Act.TorchA -> TorchExpanded(act.t)
            is Act.ChronoA -> ChronoExpanded(act.c)
            is Act.ProgressA -> ProgressExpanded(act.p)
            is Act.ChargingA -> ChargingExpanded(act.b)
            is Act.CallA -> CallExpanded(act.c)
            else -> IdleExpanded(s, battery)
        }
    }
}

@Composable
private fun MediaExpanded(m: MediaInfo, s: IslandSettings, lyrics: Lyrics?) {
    val context = LocalContext.current
    val now = rememberNow(150, active = m.playing)
    val pos = m.positionAt(now)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Art(m.art, 54.dp, 13.dp, Modifier.clickable { Controls.openApp(context, m.pkg) })
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(m.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE))
                if (m.artist.isNotBlank()) {
                    Text(m.artist, color = Dim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(10.dp))
            Equalizer(m.accent, m.playing, !s.reduceEffects, Modifier.size(30.dp, 20.dp), bars = 6)
        }

        val lines = lyrics?.takeIf { it.key == m.key && s.lyricsEnabled && it.lines.isNotEmpty() }
        if (lines != null) {
            Spacer(Modifier.height(12.dp))
            LyricsBlock(lines, pos, m.accent)
        }

        // Время по краям полосы, как на iPhone
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatDuration(pos), color = Dim, fontSize = 12.sp, style = Digits, modifier = Modifier.width(38.dp))
            SeekBar(
                fraction = if (m.durationMs > 0) pos.toFloat() / m.durationMs else 0f,
                color = Color.White,
                enabled = m.canSeek && m.durationMs > 0,
                onSeek = { f -> Controls.seekTo((f * m.durationMs).toLong()) },
                modifier = Modifier.weight(1f),
            )
            Text(
                if (m.durationMs > 0) "-" + formatDuration(m.durationMs - pos) else "",
                color = Dim, fontSize = 12.sp, style = Digits, textAlign = TextAlign.End, modifier = Modifier.width(42.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Icons.Rounded.FastRewind, size = 52.dp, bg = Color.Transparent, iconScale = 0.62f) { Controls.prev() }
                RoundButton(if (m.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, size = 58.dp, bg = Color.Transparent, iconScale = 0.74f) {
                    Controls.playPause()
                }
                RoundButton(Icons.Rounded.FastForward, size = 52.dp, bg = Color.Transparent, iconScale = 0.62f) { Controls.next() }
            }
            // Вывод звука — как кнопка AirPlay
            RoundButton(Icons.Rounded.Cast, Modifier.align(Alignment.CenterEnd), size = 40.dp, bg = Color.Transparent,
                fg = Dim, iconScale = 0.55f) { Controls.openOutput(context) }
        }
    }
}

/** Идущий разговор: аватар, имя, длительность и кнопка «завершить». */
@Composable
private fun CallExpanded(c: CallInfo) {
    val context = LocalContext.current
    val now = rememberWallNow(500)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(50.dp).clip(CircleShape).background(Color(0xFF3A3A3C)), contentAlignment = Alignment.Center) {
            if (c.avatar != null) {
                Image(c.avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(c.name.take(1).uppercase(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).clickable { if (!Controls.send(context, c.open)) Controls.openApp(context, c.pkg) }) {
            Text(c.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(formatDuration(now - c.base), color = Tints.Green, fontSize = 15.sp, style = Digits)
        }
        if (c.hangUp != null) {
            RoundButton(Icons.Rounded.CallEnd, size = 48.dp, bg = Tints.Red, iconScale = 0.5f) {
                Controls.send(context, c.hangUp)
            }
        }
    }
}

/** Три строки: спетая, текущая (крупно) и следующая — прокручиваются вверх. */
@Composable
private fun LyricsBlock(lyrics: Lyrics, pos: Long, accent: Color) {
    val index = lyrics.indexAt(pos)
    AnimatedContent(
        targetState = index,
        transitionSpec = {
            (slideInVertically(tween(320)) { it / 3 } + fadeIn(tween(320))) togetherWith
                (slideOutVertically(tween(320)) { -it / 3 } + fadeOut(tween(200)))
        },
        label = "lyrics",
    ) { i ->
        Column(Modifier.fillMaxWidth().height(86.dp), verticalArrangement = Arrangement.Center) {
            val prev = lyrics.lines.getOrNull(i - 1)?.text.orEmpty()
            val cur = lyrics.lines.getOrNull(i)?.text?.ifBlank { "• • •" } ?: "• • •"
            val next = lyrics.lines.getOrNull(i + 1)?.text.orEmpty()
            Text(prev, color = Color.White.copy(alpha = 0.3f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(cur, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 3.dp))
            Text(next, color = accent.copy(alpha = 0.55f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SeekBar(
    fraction: Float,
    color: Color,
    enabled: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val seek by rememberUpdatedState(onSeek)
    val thick by animateDpAsState(if (drag != null) 10.dp else 6.dp, label = "thick")
    val shown = (drag ?: fraction).coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(22.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        drag?.let(seek)
                        drag = null
                    },
                    onDragCancel = { drag = null },
                ) { change, _ ->
                    change.consume()
                    drag = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { o -> seek((o.x / size.width).coerceIn(0f, 1f)) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(thick)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
        ) {
            Box(Modifier.fillMaxWidth(shown).fillMaxHeight().background(if (enabled) color else color.copy(alpha = 0.6f)))
        }
    }
}

@Composable
private fun TimerExpanded(t: TimerState) {
    val now = rememberNow(200)
    if (t.ringing) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(Tints.Orange.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.NotificationsActive, null, tint = Tints.Orange, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Таймер", color = Dim, fontSize = 14.sp)
                Text("Время вышло", color = Tints.Orange, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            }
            PillButton(Icons.Rounded.Stop, "Стоп", bg = Tints.Orange, fg = Color.Black) { Controls.cancelTimer() }
        }
        return
    }
    val left = t.left(now)
    val color = if (left <= 10_000 && !t.paused) Tints.Red else Tints.Orange
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Кольцо остатка вокруг кнопки паузы, как в ПК-версии
        Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
            ProgressRing(if (t.totalMs > 0) left.toFloat() / t.totalMs else 0f, color, Modifier.size(58.dp), stroke = 4.dp)
            RoundButton(if (t.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, size = 44.dp, bg = color.copy(alpha = 0.2f), fg = color) {
                if (t.paused) Controls.resumeTimer() else Controls.pauseTimer()
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(if (t.paused) "Пауза" else "Таймер", color = Dim, fontSize = 14.sp)
            Text(formatDuration(left + 999), color = color, fontSize = 38.sp, style = Digits, maxLines = 1)
        }
        RoundButton(Icons.Rounded.Close, size = 44.dp) { Controls.cancelTimer() }
    }
}

@Composable
private fun StopwatchExpanded(w: StopwatchState) {
    val now = rememberNow(100, active = w.running)
    val elapsed = w.elapsed(now)
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoundButton(if (w.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, size = 52.dp,
            bg = Tints.Orange.copy(alpha = 0.2f), fg = Tints.Orange) { Controls.toggleStopwatch() }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("Секундомер", color = Dim, fontSize = 14.sp)
            Text(formatDuration(elapsed) + ".%d".format((elapsed % 1000) / 100), color = Tints.Orange, fontSize = 36.sp, style = Digits, maxLines = 1)
        }
        if (!w.running) {
            RoundButton(Icons.Rounded.Replay, size = 44.dp) { Controls.startStopwatch(); Controls.toggleStopwatch() }
            Spacer(Modifier.width(8.dp))
        }
        RoundButton(Icons.Rounded.Close, size = 44.dp) { Controls.stopStopwatch() }
    }
}

@Composable
private fun TorchExpanded(t: TorchState) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(Tints.Yellow.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.FlashlightOn, null, tint = Tints.Yellow, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Фонарик", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(if (t.maxLevel > 1) "Яркость ${t.level} из ${t.maxLevel}" else "Включён", color = Dim, fontSize = 13.sp)
            }
            PillButton(Icons.Rounded.FlashlightOff, "Выключить", bg = Tints.Yellow, fg = Color.Black) { Controls.torchOff() }
        }
        if (t.maxLevel > 1) {
            Spacer(Modifier.height(10.dp))
            Slider(
                value = t.level.toFloat(),
                onValueChange = { Controls.torchLevel(Math.round(it)) },
                valueRange = 1f..t.maxLevel.toFloat(),
                steps = (t.maxLevel - 2).coerceAtLeast(0),
                colors = SliderDefaults.colors(
                    thumbColor = Tints.Yellow,
                    activeTrackColor = Tints.Yellow,
                    inactiveTrackColor = Color.White.copy(alpha = 0.18f),
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
            )
        }
    }
}

@Composable
private fun ChronoExpanded(c: ChronoInfo) {
    val context = LocalContext.current
    val now = rememberWallNow(500)
    Row(Modifier.clickable { Controls.openApp(context, c.pkg) }, verticalAlignment = Alignment.CenterVertically) {
        AppIcon(c.icon, 48.dp, Icons.Rounded.Timer, Tints.Green)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(c.title, color = Dim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatDuration(c.elapsed(now)), color = Tints.Green, fontSize = 36.sp, style = Digits)
        }
    }
}

@Composable
private fun ProgressExpanded(p: ProgressInfo) {
    val context = LocalContext.current
    Column(Modifier.clickable { Controls.openApp(context, p.pkg) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(p.icon, 44.dp, Icons.Rounded.Downloading, Tints.Blue)
            Spacer(Modifier.width(14.dp))
            Text(p.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (!p.indeterminate) {
                Spacer(Modifier.width(8.dp))
                Text("${(p.fraction * 100).toInt()}%", color = Tints.Blue, fontSize = 18.sp, style = Digits)
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f))) {
            Box(Modifier.fillMaxWidth(if (p.indeterminate) 1f else p.fraction).fillMaxHeight().clip(CircleShape)
                .background(if (p.indeterminate) Tints.Blue.copy(alpha = 0.4f) else Tints.Blue))
        }
    }
}

@Composable
private fun ChargingExpanded(b: BatteryInfo) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(Tints.Green.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Bolt, null, tint = Tints.Green, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Заряжается", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f))) {
                Box(Modifier.fillMaxWidth(b.level.coerceIn(0, 100) / 100f).fillMaxHeight().clip(CircleShape).background(Tints.Green))
            }
        }
        Spacer(Modifier.width(14.dp))
        Text("${b.level}%", color = Tints.Green, fontSize = 26.sp, style = Digits)
    }
}

/** Раскрытый пустой остров: часы, заряд и быстрый запуск таймера, секундомера и фонарика. */
@Composable
private fun IdleExpanded(s: IslandSettings, battery: BatteryInfo) {
    val context = LocalContext.current
    val now = rememberWallNow(1000)
    val time = DateFormat.getTimeFormat(context).format(Date(now))
    val date = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now))
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(time, color = Color.White, fontSize = 36.sp, style = Digits)
                Text(date, color = Dim, fontSize = 14.sp)
            }
            if (battery.level >= 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (battery.charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryFull, null,
                        tint = when {
                            battery.charging -> Tints.Green
                            battery.level <= 15 -> Tints.Red
                            else -> Color.White
                        },
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("${battery.level}%", color = Color.White, fontSize = 16.sp, style = Digits)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        // Таймер от 1 до 99 минут
        Row(
            Modifier.fillMaxWidth().clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(Icons.Rounded.Remove, size = 38.dp) {
                SettingsRepo.update { copy(timerMinutes = (timerMinutes - 1).coerceAtLeast(1)) }
            }
            Text(
                "${s.timerMinutes} мин", color = Color.White, fontSize = 16.sp, style = Digits,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            RoundButton(Icons.Rounded.Add, size = 38.dp) {
                SettingsRepo.update { copy(timerMinutes = (timerMinutes + 1).coerceAtMost(99)) }
            }
            Spacer(Modifier.width(8.dp))
            PillButton(Icons.Rounded.PlayArrow, "Таймер", bg = Tints.Orange, fg = Color.Black) {
                Controls.startTimer(s.timerMinutes)
                Controls.expand("timer")
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(Icons.Rounded.Timer, "Секундомер", Modifier.weight(1f)) {
                Controls.startStopwatch()
                Controls.expand("stopwatch")
            }
            PillButton(Icons.Rounded.FlashlightOn, "Фонарик", Modifier.weight(1f).alpha(0.95f)) {
                Controls.torchOn()
                Controls.collapse()
            }
        }
    }
}
