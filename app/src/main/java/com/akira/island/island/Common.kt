package com.akira.island.island

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.data.ExpandStyle
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import kotlinx.coroutines.delay

/** Анимация формы острова по настройкам «Стиль раскрытия», «Длительность» и «Упругость». */
fun <T> islandSpec(s: IslandSettings): FiniteAnimationSpec<T> {
    val damping = if (s.reduceEffects) 1f else s.elasticity.damping
    return when (s.expandStyle) {
        ExpandStyle.INSTANT -> snap()
        ExpandStyle.SMOOTH -> tween(animDurationMs(s), easing = FastOutSlowInEasing)
        ExpandStyle.SPRING -> spring(dampingRatio = (damping + 0.1f).coerceAtMost(1f), stiffness = animStiffness(s))
        ExpandStyle.FRUIT -> spring(dampingRatio = damping, stiffness = animStiffness(s))
    }
}

/** 0% — быстрее всего, 100% — медленнее всего */
fun animStiffness(s: IslandSettings): Float {
    val t = s.durationPercent.coerceIn(0, 100) / 100f
    return 1400f + (110f - 1400f) * t
}

fun animDurationMs(s: IslandSettings): Int = 160 + s.durationPercent.coerceIn(0, 100) * 6

/** Текущее время (elapsedRealtime) с обновлением раз в [periodMs]. */
@Composable
fun rememberNow(periodMs: Long, active: Boolean = true): Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(periodMs, active) {
        while (active) {
            now = SystemClock.elapsedRealtime()
            delay(periodMs)
        }
    }
    return now
}

/** То же для настенных часов (System.currentTimeMillis). */
@Composable
fun rememberWallNow(periodMs: Long): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(periodMs)
        }
    }
    return now
}

@Composable
fun rememberHaptic(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        { if (SettingsRepo.current.haptics) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
    }
}

@Composable
fun Equalizer(color: Color, playing: Boolean, animate: Boolean, modifier: Modifier = Modifier) {
    val bars = 4
    val transition = rememberInfiniteTransition(label = "eq")
    val durations = intArrayOf(420, 560, 360, 500)
    val rest = floatArrayOf(0.35f, 0.55f, 0.3f, 0.45f)
    val heights = (0 until bars).map { i ->
        if (playing && animate) {
            transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(durations[i], easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "bar$i",
            ).value
        } else {
            if (playing) rest[i] + 0.2f else 0.18f
        }
    }
    Canvas(modifier) {
        val w = size.width / (bars * 2 - 1)
        heights.forEachIndexed { i, h ->
            val bh = (size.height * h).coerceAtLeast(w)
            drawRoundRect(
                color = color,
                topLeft = Offset(i * 2 * w, (size.height - bh) / 2),
                size = Size(w, bh),
                cornerRadius = CornerRadius(w / 2),
            )
        }
    }
}

@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    stroke: Dp = 3.dp,
    track: Color = color.copy(alpha = 0.22f),
) {
    Canvas(modifier) {
        val px = stroke.toPx()
        val inset = px / 2
        val arcSize = Size(size.width - px, size.height - px)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(px))
        drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize,
            style = Stroke(px, cap = StrokeCap.Round))
    }
}

@Composable
fun Art(art: ImageBitmap?, size: Dp, corner: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(Color(0xFF2C2C2E)),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) {
            Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(size * 0.6f))
        }
    }
}

@Composable
fun AppIcon(icon: ImageBitmap?, size: Dp, fallback: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    if (icon != null) {
        Image(icon, null, modifier.size(size).clip(CircleShape))
    } else {
        Icon(fallback, null, tint = tint, modifier = modifier.size(size))
    }
}

@Composable
fun RoundButton(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    bg: Color = Color.White.copy(alpha = 0.14f),
    fg: Color = Color.White,
    iconScale: Float = 0.52f,
    onClick: () -> Unit,
) {
    val haptic = rememberHaptic()
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .clickable { haptic(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(size * iconScale))
    }
}

@Composable
fun PillButton(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    bg: Color = Color.White.copy(alpha = 0.14f),
    fg: Color = Color.White,
    onClick: () -> Unit,
) {
    val haptic = rememberHaptic()
    Row(
        modifier
            .clip(CircleShape)
            .background(bg)
            .clickable { haptic(); onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
