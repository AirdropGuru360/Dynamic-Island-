@file:OptIn(ExperimentalFoundationApi::class)

package com.akira.island.island

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.CallInfo
import com.akira.island.core.Controls
import com.akira.island.core.Lyrics
import com.akira.island.core.MediaInfo
import com.akira.island.core.Notice
import com.akira.island.core.Tints
import com.akira.island.core.VolumeInfo
import com.akira.island.core.formatDuration
import com.akira.island.data.IslandSettings

/** Цифры одной ширины, чтобы время не «прыгало» */
val Digits = TextStyle(fontFeatureSettings = "tnum", fontWeight = FontWeight.SemiBold)

@Composable
fun CompactContent(act: Act, s: IslandSettings, lyrics: Lyrics?, wideWidth: Dp) {
    val h = s.height.dp
    val gap = compactGap(s)
    // Боковые части свёрнутого острова iPhone — около 52 pt при высоте 37 pt
    val side = h * 1.4f
    val timeSide = h * 1.6f
    val icon = h * 0.58f
    when (act) {
        is Act.Idle -> Box(Modifier.size(s.width.dp, h))

        is Act.MediaA -> MediaCompact(act.m, s, lyrics, gap)

        is Act.TimerA -> {
            val now = rememberNow(250)
            if (act.t.ringing) {
                val wobble = rememberInfiniteTransition(label = "bell")
                val angle = wobble.animateFloat(-18f, 18f, infiniteRepeatable(tween(120), RepeatMode.Reverse), label = "a")
                CompactRow(h, gap, timeSide,
                    left = { Icon(Icons.Rounded.NotificationsActive, null, tint = Tints.Orange, modifier = Modifier.size(icon).rotate(if (s.reduceEffects) 0f else angle.value)) },
                    right = { Text("Таймер", color = Tints.Orange, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
                )
            } else {
                val left = act.t.left(now)
                val color = if (left <= 10_000 && !act.t.paused) Tints.Red else Tints.Orange
                CompactRow(h, gap, timeSide,
                    left = {
                        Icon(if (act.t.paused) Icons.Rounded.Pause else Icons.Rounded.HourglassBottom, null, tint = color, modifier = Modifier.size(icon))
                    },
                    right = { Text(formatDuration(left + 999), color = color, fontSize = 17.sp, style = Digits, maxLines = 1, softWrap = false) },
                )
            }
        }

        is Act.StopwatchA -> {
            val now = rememberNow(250, active = act.w.running)
            CompactRow(h, gap, timeSide,
                left = { Icon(Icons.Rounded.Timer, null, tint = Tints.Orange, modifier = Modifier.size(icon)) },
                right = { Text(formatDuration(act.w.elapsed(now)), color = Tints.Orange, fontSize = 17.sp, style = Digits, maxLines = 1, softWrap = false) },
            )
        }

        is Act.ChronoA -> {
            val now = rememberWallNow(500)
            // Запись экрана — красная, как на iPhone; таймеры и секундомеры Часов — оранжевые
            val recording = act.c.isRecording
            val tint = if (recording) Tints.Red else Tints.Orange
            CompactRow(h, gap, timeSide,
                left = {
                    if (recording) Equalizer(tint, true, !s.reduceEffects, Modifier.size(h * 0.75f, h * 0.45f), bars = 6)
                    else AppIcon(act.c.icon, h * 0.62f, Icons.Rounded.Timer, tint)
                },
                right = { Text(formatDuration(act.c.elapsed(now)), color = tint, fontSize = 17.sp, style = Digits, maxLines = 1, softWrap = false) },
            )
        }

        is Act.CallA -> if (act.c.incoming) {
            IncomingCall(act.c, s, wideWidth)
        } else {
            val now = rememberWallNow(500)
            CompactRow(h, gap, h * 1.9f,
                left = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Call, null, tint = Tints.Green, modifier = Modifier.size(h * 0.45f))
                        Spacer(Modifier.width(4.dp))
                        Text(formatDuration(now - act.c.base), color = Tints.Green,
                            fontSize = 16.sp, style = Digits, maxLines = 1, softWrap = false)
                    }
                },
                right = { Equalizer(Tints.Green, true, !s.reduceEffects, Modifier.size(h * 0.75f, h * 0.45f), bars = 6) },
            )
        }

        is Act.TorchA -> CompactRow(h, gap, side,
            left = { Icon(Icons.Rounded.FlashlightOn, null, tint = Tints.Yellow, modifier = Modifier.size(icon)) },
            right = { Box(Modifier.size(h * 0.28f).clip(CircleShape).background(Tints.Yellow)) },
        )

        is Act.ProgressA -> CompactRow(h, gap, side,
            left = { AppIcon(act.p.icon, h * 0.62f, Icons.Rounded.Downloading, Tints.Blue) },
            right = {
                if (act.p.indeterminate) {
                    Icon(Icons.Rounded.Downloading, null, tint = Tints.Blue, modifier = Modifier.size(icon))
                } else {
                    ProgressRing(act.p.fraction, Tints.Blue, Modifier.size(h * 0.6f), stroke = 3.dp)
                }
            },
        )

        is Act.ChargingA -> CompactRow(h, gap, h * 1.8f,
            left = { Icon(Icons.Rounded.Bolt, null, tint = Tints.Green, modifier = Modifier.size(icon)) },
            right = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${act.b.level}%", color = Tints.Green, fontSize = 16.sp, style = Digits)
                    Spacer(Modifier.width(5.dp))
                    BatteryGlyph(act.b.level, Tints.Green, Modifier.size(h * 0.72f, h * 0.36f))
                }
            },
        )

        is Act.VolumeA -> VolumeCompact(act.v, s, wideWidth)

        is Act.NoticeA -> NoticeCompact(act.n, s, wideWidth)
    }
}

/** Левая и правая части по бокам от камеры, одинаковой ширины — так вырез остаётся посередине. */
@Composable
fun CompactRow(
    h: Dp,
    gap: Dp,
    side: Dp,
    left: @Composable BoxScope.() -> Unit,
    right: @Composable BoxScope.() -> Unit,
) {
    Row(Modifier.height(h).padding(horizontal = h * 0.3f), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(side).fillMaxHeight(), contentAlignment = Alignment.CenterStart, content = left)
        Spacer(Modifier.width(gap))
        Box(Modifier.width(side).fillMaxHeight(), contentAlignment = Alignment.CenterEnd, content = right)
    }
}

@Composable
private fun MediaCompact(m: MediaInfo, s: IslandSettings, lyrics: Lyrics?, gap: Dp) {
    val h = s.height.dp
    val art = h * 0.65f
    val lines = lyrics?.takeIf { it.key == m.key && s.lyricsEnabled && s.lyricsInCapsule }
    if (lines != null) {
        // Строка песни прямо в капсуле: слева обложка и полоски, справа — текст
        val now = rememberNow(200, active = m.playing)
        val text = lines.lines.getOrNull(lines.indexAt(m.positionAt(now)))?.text?.ifBlank { "♪" } ?: "♪"
        CompactRow(h, gap, 104.dp,
            left = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Art(m.art, art, art * 0.27f)
                    Spacer(Modifier.width(8.dp))
                    Equalizer(m.accent, m.playing, !s.reduceEffects, Modifier.size(h * 0.55f, h * 0.42f))
                }
            },
            right = {
                AnimatedContent(
                    targetState = text,
                    transitionSpec = {
                        (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                    },
                    label = "line",
                ) { line ->
                    Text(
                        line,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
                    )
                }
            },
        )
    } else {
        CompactRow(h, gap, h * 1.4f,
            left = { Art(m.art, art, art * 0.27f) },
            right = { Equalizer(m.accent, m.playing, !s.reduceEffects, Modifier.size(h * 0.7f, h * 0.5f)) },
        )
    }
}

@Composable
private fun VolumeCompact(v: VolumeInfo, s: IslandSettings, wideWidth: Dp) {
    val context = LocalContext.current
    val h = s.height.dp
    val width = (s.width.dp + 150.dp).coerceAtMost(wideWidth)
    val icon = when {
        v.level == 0 -> Icons.AutoMirrored.Rounded.VolumeOff
        v.fraction < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
        else -> Icons.AutoMirrored.Rounded.VolumeUp
    }
    Row(
        Modifier.width(width).height(h).padding(horizontal = h * 0.3f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(h * 0.58f))
        Spacer(Modifier.width(10.dp))
        // Полоску можно тянуть пальцем
        Box(
            Modifier
                .weight(1f)
                .height(h * 0.7f)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        Controls.setVolume(context, change.position.x / size.width)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { o -> Controls.setVolume(context, o.x / size.width) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
            ) {
                Box(Modifier.fillMaxWidth(v.fraction).fillMaxHeight().clip(CircleShape).background(Color.White))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text("${(v.fraction * 100).toInt()}", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, style = Digits,
            modifier = Modifier.width(26.dp))
    }
}

/** Свободное место над камерой между боковыми частями свёрнутого острова. */
fun compactGap(s: IslandSettings): Dp = (s.width.dp - s.height.dp * 0.6f).coerceAtLeast(8.dp)

/** Высота события: заметно выше свёрнутого острова, как у раскрытых уведомлений iPhone. */
fun noticeHeight(s: IslandSettings): Dp = (s.height.dp * 1.75f).coerceAtLeast(56.dp)

/**
 * Событие как на iPhone: остров разворачивается почти на всю ширину экрана,
 * слева значок и подпись, справа состояние, батарейка или кольцо.
 */
@Composable
private fun NoticeCompact(n: Notice, s: IslandSettings, wideWidth: Dp) {
    val h = s.height.dp
    // Значок «выпрыгивает» при появлении
    val pop = remember(n.id) { Animatable(if (s.reduceEffects) 1f else 0.3f) }
    LaunchedEffect(n.id) { pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 320f)) }

    if (n.title.isBlank() && n.state == null) {
        // Только значок, например замок при разблокировке
        CompactRow(h, compactGap(s), h * 1.4f,
            left = { Icon(n.icon, null, tint = n.tint, modifier = Modifier.size(h * 0.52f).scale(pop.value)) },
            right = {},
        )
        return
    }

    val height = noticeHeight(s)
    Row(
        Modifier.width(wideWidth).height(height).padding(horizontal = height * 0.36f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(n.icon, null, tint = n.tint, modifier = Modifier.size(height * 0.46f).scale(pop.value))
        Spacer(Modifier.width(10.dp))
        Text(
            n.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        if (n.state != null) {
            Text(n.state, color = n.tint, fontSize = 17.sp, style = Digits, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = wideWidth * 0.4f))
        }
        if (n.battery != null) {
            Spacer(Modifier.width(8.dp))
            BatteryGlyph(n.battery, n.tint, Modifier.size(34.dp, 16.dp))
        }
        if (n.ring != null) {
            Spacer(Modifier.width(8.dp))
            ProgressRing(n.ring, n.tint, Modifier.size(24.dp), stroke = 3.5.dp)
        }
    }
}

/** Входящий звонок: аватар, имя и две круглые кнопки — сразу крупно, как на iPhone. */
@Composable
private fun IncomingCall(c: CallInfo, s: IslandSettings, wideWidth: Dp) {
    val context = LocalContext.current
    Row(
        // Высота 84 — как у раскрытой карточки звонка на iPhone
        Modifier.width(wideWidth).height(84.dp).padding(horizontal = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(50.dp).clip(CircleShape).background(Color(0xFF3A3A3C)), contentAlignment = Alignment.Center) {
            if (c.avatar != null) {
                androidx.compose.foundation.Image(c.avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(c.name.take(1).uppercase(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Входящий вызов", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
            Text(c.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        RoundButton(Icons.Rounded.CallEnd, size = 48.dp, bg = Tints.Red, iconScale = 0.5f) {
            Controls.endCall(context, c)
        }
        Spacer(Modifier.width(12.dp))
        RoundButton(Icons.Rounded.Call, size = 48.dp, bg = Tints.Green, iconScale = 0.5f) {
            Controls.answerCall(context, c)
        }
    }
}
