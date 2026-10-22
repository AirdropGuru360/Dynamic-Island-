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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bolt
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val gap = (s.width.dp - h).coerceAtLeast(12.dp)
    val side = h * 1.15f
    val icon = h * 0.6f
    when (act) {
        is Act.Idle -> Box(Modifier.size(s.width.dp, h))

        is Act.MediaA -> MediaCompact(act.m, s, lyrics, gap)

        is Act.TimerA -> {
            val now = rememberNow(250)
            if (act.t.ringing) {
                val wobble = rememberInfiniteTransition(label = "bell")
                val angle = wobble.animateFloat(-18f, 18f, infiniteRepeatable(tween(120), RepeatMode.Reverse), label = "a")
                CompactRow(h, gap, side * 1.4f,
                    left = { Icon(Icons.Rounded.NotificationsActive, null, tint = Tints.Orange, modifier = Modifier.size(icon).rotate(if (s.reduceEffects) 0f else angle.value)) },
                    right = { Text("Таймер", color = Tints.Orange, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) },
                )
            } else {
                val left = act.t.left(now)
                val color = if (left <= 10_000 && !act.t.paused) Tints.Red else Tints.Orange
                CompactRow(h, gap, side * 1.4f,
                    left = {
                        Icon(if (act.t.paused) Icons.Rounded.Pause else Icons.Rounded.HourglassBottom, null, tint = color, modifier = Modifier.size(icon))
                    },
                    right = { Text(formatDuration(left + 999), color = color, fontSize = 15.sp, style = Digits) },
                )
            }
        }

        is Act.StopwatchA -> {
            val now = rememberNow(250, active = act.w.running)
            CompactRow(h, gap, side * 1.4f,
                left = { Icon(Icons.Rounded.Timer, null, tint = Tints.Orange, modifier = Modifier.size(icon)) },
                right = { Text(formatDuration(act.w.elapsed(now)), color = Tints.Orange, fontSize = 15.sp, style = Digits) },
            )
        }

        is Act.ChronoA -> {
            val now = rememberWallNow(500)
            CompactRow(h, gap, side * 1.4f,
                left = { AppIcon(act.c.icon, h * 0.62f, Icons.Rounded.Timer, Tints.Green) },
                right = { Text(formatDuration(act.c.elapsed(now)), color = Tints.Green, fontSize = 15.sp, style = Digits) },
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

        is Act.ChargingA -> CompactRow(h, gap, side * 1.2f,
            left = { Icon(Icons.Rounded.Bolt, null, tint = Tints.Green, modifier = Modifier.size(icon)) },
            right = { Text("${act.b.level}%", color = Tints.Green, fontSize = 14.sp, style = Digits) },
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
    Row(Modifier.height(h).padding(horizontal = h * 0.2f), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(side).fillMaxHeight(), contentAlignment = Alignment.CenterStart, content = left)
        Spacer(Modifier.width(gap))
        Box(Modifier.width(side).fillMaxHeight(), contentAlignment = Alignment.CenterEnd, content = right)
    }
}

@Composable
private fun MediaCompact(m: MediaInfo, s: IslandSettings, lyrics: Lyrics?, gap: Dp) {
    val h = s.height.dp
    val art = h - 10.dp
    val lines = lyrics?.takeIf { it.key == m.key && s.lyricsEnabled && s.lyricsInCapsule }
    if (lines != null) {
        // Строка песни прямо в капсуле: слева обложка и полоски, справа — текст
        val now = rememberNow(200, active = m.playing)
        val text = lines.lines.getOrNull(lines.indexAt(m.positionAt(now)))?.text?.ifBlank { "♪" } ?: "♪"
        CompactRow(h, gap, 104.dp,
            left = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Art(m.art, art, art * 0.3f)
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
        CompactRow(h, gap, h * 1.1f,
            left = { Art(m.art, art, art * 0.3f) },
            right = { Equalizer(m.accent, m.playing, !s.reduceEffects, Modifier.size(h * 0.62f, h * 0.48f)) },
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

@Composable
private fun NoticeCompact(n: Notice, s: IslandSettings, wideWidth: Dp) {
    val h = s.height.dp
    val width = (s.width.dp + 200.dp).coerceIn(260.dp, wideWidth.coerceAtLeast(260.dp))
    // Значок «выпрыгивает» на своей подложке
    val pop = remember(n.id) { Animatable(if (s.reduceEffects) 1f else 0.4f) }
    LaunchedEffect(n.id) { pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 300f)) }
    Row(
        Modifier.width(width).height(h + 22.dp).padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(h + 2.dp).scale(pop.value).clip(CircleShape).background(n.tint.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(n.icon, null, tint = n.tint, modifier = Modifier.size(h * 0.6f))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(n.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (n.subtitle != null) {
                Text(n.subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (n.trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(n.trailing, color = n.tint, fontSize = 15.sp, style = Digits)
        }
        Spacer(Modifier.width(4.dp))
    }
}
