@file:OptIn(ExperimentalFoundationApi::class)

package com.akira.island.island

import android.text.format.DateFormat
import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.Animatable
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.akira.island.core.IslandBus
import com.akira.island.core.LiveInfo
import com.akira.island.core.Lyrics
import com.akira.island.core.LyricsStatus
import com.akira.island.core.MediaInfo
import com.akira.island.core.ProgressInfo
import com.akira.island.core.StopwatchState
import com.akira.island.core.Tints
import com.akira.island.core.TimerState
import com.akira.island.core.TorchState
import com.akira.island.core.formatDuration
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import com.akira.island.service.ClockApps
import com.akira.island.service.ClockControl
import com.akira.island.service.ClockScreenReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Dim = Color.White.copy(alpha = 0.55f)

@Composable
fun ExpandedContent(act: Act, s: IslandSettings, lyrics: Lyrics?, battery: BatteryInfo, width: Dp) {
    // Фонарик, как в iOS 18, — не широкая плашка, а высокая карточка с лучом
    if (act is Act.TorchA) {
        TorchCard(act.t, s)
        return
    }
    // Сверху — полоса камеры: содержимое начинается ниже выреза, чтобы он ничего не закрывал
    Box(Modifier.width(width).padding(start = 22.dp, end = 22.dp, top = cameraInset(s), bottom = 20.dp)) {
        when (act) {
            is Act.MediaA -> MediaExpanded(act.m, s, lyrics)
            is Act.TimerA -> TimerExpanded(act.t)
            is Act.StopwatchA -> StopwatchExpanded(act.w)
            is Act.TorchA -> Unit
            is Act.ChronoA -> ChronoExpanded(act.c)
            is Act.ProgressA -> ProgressExpanded(act.p)
            is Act.ChargingA -> ChargingExpanded(act.b)
            is Act.LiveA -> LiveExpanded(act.l)
            is Act.CallA -> CallExpanded(act.c)
            is Act.AlarmA -> AlarmExpanded(act.a, s)
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
            Art(m.art, 56.dp, 13.dp, Modifier.clickable { Controls.openApp(context, m.pkg) })
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(m.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE))
                if (m.artist.isNotBlank()) {
                    Text(m.artist, color = Dim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(10.dp))
            val egg by IslandBus.egg.collectAsState()
            if (egg) SoulHeart(Modifier.size(24.dp)) else Equalizer(m.accent, m.playing, !s.reduceEffects, Modifier.size(30.dp, 20.dp), bars = 6)
        }

        val lines = lyrics?.takeIf { it.key == m.key && s.lyricsEnabled && it.lines.isNotEmpty() }
        val status by IslandBus.lyricsStatus.collectAsState()
        if (lines != null) {
            Spacer(Modifier.height(12.dp))
            LyricsBlock(lines, pos, m.accent)
        } else if (s.lyricsEnabled && status?.first == m.key) {
            // Видно, что текст ищется или его нет — а не что функция сломалась
            Spacer(Modifier.height(10.dp))
            Text(
                if (status?.second == LyricsStatus.LOADING) "Ищу текст песни…" else "Текст песни не найден",
                color = Color.White.copy(alpha = 0.35f), fontSize = 13.sp,
            )
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
                RoundButton(Ios.back, size = 52.dp, bg = Color.Transparent, iconScale = 0.56f) { Controls.prev() }
                RoundButton(if (m.playing) Ios.pause else Ios.play, size = 58.dp, bg = Color.Transparent, iconScale = 0.6f) {
                    Controls.playPause()
                }
                RoundButton(Ios.forward, size = 52.dp, bg = Color.Transparent, iconScale = 0.56f) { Controls.next() }
            }
            // Вывод звука — как кнопка AirPlay
            RoundButton(Ios.airplay, Modifier.align(Alignment.CenterEnd), size = 40.dp, bg = Color.Transparent,
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
        Column(Modifier.weight(1f).clickable { Controls.openCall(context, c) }) {
            Text(c.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(formatDuration(now - c.base), color = Tints.Green, fontSize = 15.sp, style = Digits)
        }
        RoundButton(Ios.call, size = 48.dp, bg = Tints.Red, iconScale = 0.46f, iconRotation = 135f) {
            Controls.endCall(context, c)
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

/** Таймер как на iPhone: слева оранжевая «пауза» и серый крестик, справа «Таймер» и крупное время. */
@Composable
private fun TimerExpanded(t: TimerState) {
    val now = rememberNow(200)
    if (t.ringing) {
        val wobble = rememberInfiniteTransition(label = "bell")
        val angle = wobble.animateFloat(-16f, 16f, infiniteRepeatable(tween(110), RepeatMode.Reverse), label = "a")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(50.dp).clip(CircleShape).background(Tints.Orange.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                Icon(Ios.bell, null, tint = Tints.Orange,
                    modifier = Modifier.size(28.dp).graphicsLayer { rotationZ = angle.value })
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Таймер", color = Tints.Orange, fontSize = 15.sp)
                Text("Время вышло", color = Tints.Orange, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            }
            PillButton(Ios.stop, "Стоп", bg = Tints.Orange, fg = Color.Black) { Controls.cancelTimer() }
        }
        return
    }
    val left = t.left(now)
    val color = if (left <= 10_000 && !t.paused) Tints.Red else Tints.Orange
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoundButton(if (t.paused) Ios.play else Ios.pause, size = 50.dp,
            bg = color.copy(alpha = 0.25f), fg = color, iconScale = 0.5f) {
            if (t.paused) Controls.resumeTimer() else Controls.pauseTimer()
        }
        Spacer(Modifier.width(12.dp))
        RoundButton(Ios.close, size = 50.dp, bg = Color.White.copy(alpha = 0.2f), iconScale = 0.44f) { Controls.cancelTimer() }
        Spacer(Modifier.weight(1f))
        Text(if (t.paused) "Пауза" else "Таймер", color = color, fontSize = 15.sp, modifier = Modifier.padding(top = 14.dp))
        Spacer(Modifier.width(8.dp))
        Text(formatDuration(left + 999), color = color, fontSize = 44.sp, style = Digits.copy(fontWeight = FontWeight.Normal), maxLines = 1)
    }
}

@Composable
private fun StopwatchExpanded(w: StopwatchState) {
    val now = rememberNow(100, active = w.running)
    val elapsed = w.elapsed(now)
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoundButton(if (w.running) Ios.pause else Ios.play, size = 50.dp,
            bg = Tints.Orange.copy(alpha = 0.25f), fg = Tints.Orange, iconScale = 0.5f) { Controls.toggleStopwatch() }
        Spacer(Modifier.width(12.dp))
        if (w.running) {
            RoundButton(Ios.close, size = 50.dp, bg = Color.White.copy(alpha = 0.2f), iconScale = 0.44f) { Controls.stopStopwatch() }
        } else {
            RoundButton(Ios.refresh, size = 50.dp, bg = Color.White.copy(alpha = 0.2f), iconScale = 0.46f) {
                Controls.startStopwatch()
                Controls.toggleStopwatch()
            }
        }
        Spacer(Modifier.weight(1f))
        Text(formatDuration(elapsed) + ",%d".format((elapsed % 1000) / 100), color = Tints.Orange, fontSize = 40.sp,
            style = Digits.copy(fontWeight = FontWeight.Normal), maxLines = 1)
    }
}

/**
 * Фонарик как в iOS 18: остров становится высокой карточкой, внизу светится фонарик, вверх идёт луч.
 * Провести пальцем вверх или вниз — ярче или тусклее (если телефон это умеет), нажатие — выключить.
 */
@Composable
private fun TorchCard(t: TorchState, s: IslandSettings) {
    val haptic = rememberHaptic()
    val max = t.maxLevel.coerceAtLeast(1)
    var level by remember { mutableFloatStateOf(t.level.toFloat()) }
    LaunchedEffect(t.level) { level = t.level.toFloat() }
    val target = if (t.on) (if (max > 1) level / max else 1f) else 0f
    val intensity by animateFloatAsState(target, spring(dampingRatio = 0.7f, stiffness = 300f), label = "beam")
    // Луч чуть «дышит», как живой свет
    val breath = rememberInfiniteTransition(label = "breath")
    val pulse by breath.animateFloat(0.94f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    val glow = if (s.reduceEffects) 1f else pulse
    // Пока палец на ползунке — фонарик сияет сильнее, а на каждом шаге яркости вспыхивает, как на iPhone
    var dragging by remember { mutableStateOf(false) }
    val boost by animateFloatAsState(if (dragging && !s.reduceEffects) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 220f), label = "boost")
    val flare = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        Modifier
            .size(200.dp, 224.dp)
            .pointerInput(max) {
                detectVerticalDragGestures(
                    onDragStart = {
                        dragging = true
                        IslandBus.lastTouch = SystemClock.elapsedRealtime()
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, dy ->
                    change.consume()
                    IslandBus.lastTouch = SystemClock.elapsedRealtime()
                    if (max <= 1) return@detectVerticalDragGestures
                    val before = Math.round(level)
                    level = (level - dy / (size.height * 0.6f) * max).coerceIn(1f, max.toFloat())
                    val after = Math.round(level)
                    if (after != before) {
                        haptic()
                        Controls.torchLevel(after)
                        if (!s.reduceEffects) scope.launch {
                            flare.snapTo(1f)
                            flare.animateTo(0f, tween(480, easing = FastOutSlowInEasing))
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures {
                    haptic()
                    Controls.torchOff()
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val headY = size.height * 0.64f
            val reach = size.height * 0.58f
            val spread = 34f + 14f * intensity + 6f * boost
            val shine = glow * (1f + 0.35f * boost + 0.3f * flare.value)
            // Луч — конус с мягким светом от головки фонарика вверх
            if (intensity > 0.01f) {
                val beam = Path().apply {
                    moveTo(cx, headY)
                    arcTo(
                        Rect(Offset(cx, headY), reach),
                        startAngleDegrees = -90f - spread, sweepAngleDegrees = spread * 2, forceMoveTo = false,
                    )
                    close()
                }
                drawPath(
                    beam,
                    Brush.radialGradient(
                        0f to Color.White.copy(alpha = (0.95f * intensity * shine).coerceAtMost(1f)),
                        0.35f to Color(0xFFD9D4FF).copy(alpha = (0.55f * intensity * shine).coerceAtMost(1f)),
                        1f to Color(0xFF8E87FF).copy(alpha = 0f),
                        center = Offset(cx, headY),
                        radius = reach,
                    ),
                )
                val halo = 34.dp.toPx() * glow * (1f + 0.6f * boost + 0.45f * flare.value)
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = (0.9f * intensity * shine).coerceAtMost(1f)), Color.Transparent),
                        center = Offset(cx, headY), radius = halo,
                    ),
                    radius = halo, center = Offset(cx, headY),
                )
                // Мягкое лавандовое сияние вокруг всего фонарика, когда его касаются
                if (boost > 0.01f || flare.value > 0.01f) {
                    val bloom = reach * (0.75f + 0.25f * boost)
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Color(0xFFB9B4FF).copy(alpha = 0.32f * intensity * (boost + flare.value * 0.6f)), Color.Transparent),
                            center = Offset(cx, headY + 10.dp.toPx()), radius = bloom,
                        ),
                        radius = bloom, center = Offset(cx, headY + 10.dp.toPx()),
                    )
                }
            }
            // Шкала яркости — пунктирная дуга, пройденная часть сплошная
            val arcR = reach * 0.86f
            val arcRect = Rect(Offset(cx, headY), arcR)
            val dots = 22
            for (i in 0..dots) {
                val a = Math.toRadians((-90f - 44f + 88f * i / dots).toDouble())
                drawCircle(
                    Color.White.copy(alpha = 0.35f), radius = 1.2.dp.toPx(),
                    center = Offset(cx + arcR * cos(a).toFloat(), headY + arcR * sin(a).toFloat()),
                )
            }
            if (t.on) {
                drawArc(
                    Color.White.copy(alpha = 0.9f), startAngle = -90f - 44f, sweepAngle = 88f * (if (max > 1) level / max else 1f),
                    useCenter = false, topLeft = arcRect.topLeft, size = arcRect.size,
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            // Сам фонарик: головка-трапеция и ручка
            val body = if (t.on) Color.White else Color.White.copy(alpha = 0.4f)
            val headW = 30.dp.toPx()
            val neckW = 16.dp.toPx()
            val headH = 14.dp.toPx()
            val head = Path().apply {
                moveTo(cx - headW / 2, headY)
                lineTo(cx + headW / 2, headY)
                lineTo(cx + neckW / 2, headY + headH)
                lineTo(cx - neckW / 2, headY + headH)
                close()
            }
            drawPath(head, body)
            drawRoundRect(
                body, topLeft = Offset(cx - neckW / 2, headY + headH + 2.dp.toPx()),
                size = Size(neckW, 36.dp.toPx()), cornerRadius = CornerRadius(5.dp.toPx()),
            )
            drawCircle(Color.Black.copy(alpha = 0.5f), radius = 3.dp.toPx(), center = Offset(cx, headY + headH + 16.dp.toPx()))
        }
        Icon(
            Ios.sunny, null, tint = Color.White.copy(alpha = 0.7f + 0.3f * boost),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 26.dp).size(14.dp),
        )
    }
}

/** Таймер или секундомер из Часов, звонок по VoIP и запись экрана или звука — запись красной волной и кнопкой «стоп». */
@Composable
private fun ChronoExpanded(c: ChronoInfo) {
    val context = LocalContext.current
    val now = rememberWallNow(500)
    if (c.isRecording) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RecordingWave(Modifier.weight(1f).height(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(formatDuration(c.elapsed(now)), color = Tints.Red, fontSize = 22.sp, style = Digits)
            Spacer(Modifier.width(14.dp))
            // Белое кольцо с красным квадратом — как кнопка «стоп» диктофона на iPhone
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(3.dp, Color.White, CircleShape)
                    .clickable { if (!Controls.send(context, c.stop)) Controls.openApp(context, c.pkg) },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(Tints.Red))
            }
        }
        return
    }
    // «Часы» — с кнопками, как свой таймер; другие приложения с отсчётом — просто время, нажатие открывает их
    if (c.key.startsWith(ClockScreenReader.KEY_PREFIX) || c.pause != null || c.resume != null || ClockApps.isClock(context, c.pkg)) {
        ClockChrono(c)
        return
    }
    Row(Modifier.clickable { Controls.openApp(context, c.pkg) }, verticalAlignment = Alignment.CenterVertically) {
        AppIcon(c.icon, 46.dp, Ios.timer, Tints.Orange)
        Spacer(Modifier.width(14.dp))
        Text(c.title, color = Tints.Orange, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(top = 12.dp))
        Spacer(Modifier.width(8.dp))
        Text(formatDuration(c.elapsed(now)), color = Tints.Orange, fontSize = 40.sp,
            style = Digits.copy(fontWeight = FontWeight.Normal), maxLines = 1)
    }
}

/**
 * Таймер и секундомер «Часов» — с теми же кнопками, что свои: пауза или продолжить и сброс.
 * Кнопки нажимают кнопки «Часов» (см. ClockControl); нажатие на время открывает приложение.
 */
@Composable
private fun ClockChrono(c: ChronoInfo) {
    val context = LocalContext.current
    val paused = c.pausedMs != null
    val now = rememberWallNow(if (paused) 1_000 else if (c.countDown) 250 else 100)
    val value = c.elapsed(now)
    val color = if (c.countDown && !paused && value <= 10_000) Tints.Red else Tints.Orange
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoundButton(if (paused) Ios.play else Ios.pause, size = 50.dp, bg = color.copy(alpha = 0.25f), fg = color, iconScale = 0.5f) {
            ClockControl.toggle(context, c)
        }
        Spacer(Modifier.width(12.dp))
        RoundButton(if (c.countDown) Ios.close else Ios.refresh, size = 50.dp, bg = Color.White.copy(alpha = 0.2f),
            iconScale = if (c.countDown) 0.44f else 0.46f) {
            ClockControl.reset(context, c)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).clickable { Controls.openApp(context, c.pkg) }, horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(c.icon, 16.dp, Ios.timer, color)
                Spacer(Modifier.width(5.dp))
                Text(if (paused) "Пауза" else if (c.countDown) "Таймер" else "Секундомер", color = color, fontSize = 14.sp, maxLines = 1)
            }
            val text = if (c.countDown) formatDuration(value + 999) else formatDuration(value) + ",%d".format((value % 1000) / 100)
            Text(text, color = color, fontSize = 40.sp, style = Digits.copy(fontWeight = FontWeight.Normal), maxLines = 1, softWrap = false)
        }
    }
}

/** Волна записи: слева идут красные столбики, справа ждёт пунктир — как у диктофона в острове. */
@Composable
private fun RecordingWave(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "rec")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "ph")
    Canvas(modifier) {
        val step = 4.dp.toPx()
        val n = (size.width / step).toInt()
        val active = (n * 0.55f).toInt()
        for (i in 0 until n) {
            val x = i * step + step / 2
            if (i < active) {
                val k = 0.25f + 0.75f * abs(sin((i * 0.7f + phase * 6.28f).toDouble())).toFloat()
                val h = size.height * k
                drawLine(Tints.Red, Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2),
                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            } else {
                drawCircle(Tints.Red.copy(alpha = 0.45f), radius = 1.dp.toPx(), center = Offset(x, size.height / 2))
            }
        }
    }
}

@Composable
private fun ProgressExpanded(p: ProgressInfo) {
    val context = LocalContext.current
    Column(Modifier.clickable { Controls.openApp(context, p.pkg) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(p.icon, 44.dp, Ios.download, Tints.Blue)
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

/** Навигатор, такси, доставка: стрелка или картинка, расстояние крупно, подсказка, время и кнопка «Открыть». */
@Composable
private fun LiveExpanded(l: LiveInfo) {
    val context = LocalContext.current
    val tint = liveTint(l.kind)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                LiveGlyph(l, 32.dp, tint)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(l.distance ?: l.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                if (l.text.isNotEmpty()) {
                    Text(l.text, color = Dim, fontSize = 14.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                val line = l.eta ?: l.sub.takeIf { it.isNotEmpty() }
                if (line != null) {
                    Text(line, color = tint, fontSize = 13.sp, style = Digits, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        PillButton(Ios.navigate, "Открыть", bg = tint, fg = Color.Black) {
            if (!Controls.send(context, l.open)) Controls.openApp(context, l.pkg)
            Controls.collapse()
        }
    }
}

@Composable
private fun ChargingExpanded(b: BatteryInfo) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(Tints.Green.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Icon(Ios.flash, null, tint = Tints.Green, modifier = Modifier.size(26.dp))
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
    val alarm by IslandBus.nextAlarm.collectAsState()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(time, color = Color.White, fontSize = 36.sp, style = Digits)
                Text(date, color = Dim, fontSize = 14.sp)
                // Ближайший будильник в пределах суток — под датой, как на экране блокировки iPhone
                alarm?.takeIf { it.triggerAt - now in 1..86_400_000L }?.let { a ->
                    Row(
                        Modifier.padding(top = 3.dp).clickable { openAlarm(context, a) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Ios.alarm, null, tint = AlarmTint, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("${wakeTime(context, a.triggerAt)} · через ${durationWords(a.triggerAt - now)}",
                            color = AlarmTint, fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
            if (battery.level >= 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (battery.charging) Ios.batteryCharging else Ios.batteryFull, null,
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
            RoundButton(Ios.remove, size = 38.dp, iconScale = 0.44f) {
                SettingsRepo.update { copy(timerMinutes = (timerMinutes - 1).coerceAtLeast(1)) }
            }
            Text(
                "${s.timerMinutes} мин", color = Color.White, fontSize = 16.sp, style = Digits,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            RoundButton(Ios.add, size = 38.dp, iconScale = 0.44f) {
                SettingsRepo.update { copy(timerMinutes = (timerMinutes + 1).coerceAtMost(99)) }
            }
            Spacer(Modifier.width(8.dp))
            PillButton(Ios.play, "Таймер", bg = Tints.Orange, fg = Color.Black) {
                Controls.startTimer(s.timerMinutes)
                Controls.expand("timer")
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(Ios.stopwatch, "Секундомер", Modifier.weight(1f)) {
                Controls.startStopwatch()
                Controls.expand("stopwatch")
            }
            PillButton(Ios.flashlight, "Фонарик", Modifier.weight(1f).alpha(0.95f)) {
                Controls.torchOn()
                Controls.collapse()
            }
        }
    }
}
