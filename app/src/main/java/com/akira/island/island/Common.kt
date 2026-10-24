package com.akira.island.island

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.Tints
import com.akira.island.core.Turn
import com.akira.island.data.AccentMode
import com.akira.island.data.ExpandStyle
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

/** Звуковая волна из тонких полосок, как в острове iPhone. */
@Composable
fun Equalizer(color: Color, playing: Boolean, animate: Boolean, modifier: Modifier = Modifier, bars: Int = 5) {
    val transition = rememberInfiniteTransition(label = "eq")
    val heights = (0 until bars).map { i ->
        val rest = 0.3f + 0.45f * ((i * 37) % 10) / 10f
        if (playing && animate) {
            transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(320 + (i * 97) % 280, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse,
                ),
                label = "bar$i",
            ).value
        } else {
            if (playing) rest else 0.15f
        }
    }
    Canvas(modifier) {
        // Полоска и промежуток одной ширины
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

/** Батарейка как в iOS: заливка по уровню заряда и «носик» справа. */
@Composable
fun BatteryGlyph(level: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val nub = size.width * 0.07f
        val bodyW = size.width - nub - 1.dp.toPx()
        val r = size.height * 0.3f
        drawRoundRect(color.copy(alpha = 0.3f), size = Size(bodyW, size.height), cornerRadius = CornerRadius(r))
        val inset = size.height * 0.12f
        val fillW = (bodyW - inset * 2) * (level.coerceIn(0, 100) / 100f)
        if (fillW > 0f) {
            drawRoundRect(
                color,
                topLeft = Offset(inset, inset),
                size = Size(fillW.coerceAtLeast(r * 0.5f), size.height - inset * 2),
                cornerRadius = CornerRadius(r * 0.7f),
            )
        }
        drawRoundRect(
            color.copy(alpha = 0.45f),
            topLeft = Offset(bodyW + 1.dp.toPx(), size.height * 0.32f),
            size = Size(nub, size.height * 0.36f),
            cornerRadius = CornerRadius(nub / 2),
        )
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
            Icon(Ios.music, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(size * 0.5f))
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
    iconRotation: Float = 0f,
    onClick: () -> Unit,
) {
    val haptic = rememberHaptic()
    // Кнопка чуть проседает под пальцем, как на iPhone
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(bg)
            .clickable(interaction, indication = null) { haptic(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(size * iconScale).graphicsLayer { rotationZ = iconRotation })
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

/**
 * Liquid Glass по образцу iOS 26: стекло, которое ловит и преломляет свет. Настоящего преломления
 * того, что лежит под островом, Android не даёт (поверх экрана нельзя «заглянуть» в чужое окно),
 * поэтому рисуется всё, что видно у настоящего стекла:
 *  - дымчатое полупрозрачное тело, светлее чёрной подложки, плотнее и темнее к низу (толщина);
 *  - пятно света сверху и каустика — светлое пятно у противоположного края, где свет собрало стекло;
 *  - мягкое свечение вдоль кромки и яркая линза-кромка: блики в верхнем левом и нижнем правом углу;
 *  - тонкая цветная дисперсия по краю (голубая и розовая каймы);
 *  - блики живут: при наклоне телефона ([tilt] от −1 до 1) они сдвигаются, а при смене содержимого
 *    по стеклу пробегает световая полоса ([sheen] от 0 до 1, иначе скрыта).
 */
fun Modifier.glassSurface(
    shape: Shape,
    base: Color,
    opacity: Float,
    tilt: () -> Float = { 0f },
    sheen: () -> Float = { -1f },
): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val a = opacity.coerceIn(0.3f, 0.95f)
    val outline = shape.createOutline(size, layoutDirection, this)
    val dp = 1.dp.toPx()
    // Всё, что не зависит от наклона, собирается один раз на размер, а кадр только рисует
    val veil = Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.20f),
        0.5f to Color.White.copy(alpha = 0.05f),
        1f to Color.White.copy(alpha = 0.10f),
    )
    val shade = Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.28f))
    val glow = List(7) { i -> Stroke(width = (i + 1) * 2 * dp) }
    val glowColor = Color.White.copy(alpha = 0.035f)
    val wideRim = Stroke(width = 10 * dp)
    val thinRim = Stroke(width = 3.2f * dp)
    val fringe = Stroke(width = 4 * dp)

    onDrawWithContent {
        val t = tilt().coerceIn(-1f, 1f)

        // Тело стекла
        drawRect(base.copy(alpha = a * 0.8f))
        drawRect(veil)
        drawRect(shade)

        // Свет сверху и каустика у противоположного края
        drawRect(
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.26f), Color.Transparent),
                center = Offset(w * (0.22f + 0.30f * t), 0f),
                radius = w * 0.55f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.20f), Color.Transparent),
                center = Offset(w * (0.80f + 0.15f * t), h),
                radius = w * 0.45f,
            ),
        )

        // Бегущий блик при смене содержимого
        val shine = sheen()
        if (shine in 0f..1f) {
            val x = -w * 0.3f + w * 1.6f * shine
            drawRect(
                Brush.linearGradient(
                    0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = 0.24f),
                    1f to Color.Transparent,
                    start = Offset(x - w * 0.25f, 0f),
                    end = Offset(x + w * 0.25f, h * 0.6f),
                ),
            )
        }

        // Свечение вдоль кромки: полоски убывающей яркости складываются в плавный спад внутрь
        for (stroke in glow) drawOutline(outline, glowColor, style = stroke)

        drawContent()

        // Кромка-линза: яркая слева сверху и справа снизу, посередине почти гаснет; при наклоне сдвигается
        val shift = t * w * 0.35f
        val rim = Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.95f),
            0.18f to Color.White.copy(alpha = 0.35f),
            0.45f to Color.White.copy(alpha = 0.06f),
            0.72f to Color.White.copy(alpha = 0.22f),
            1f to Color(0xFFD6E8FF).copy(alpha = 0.9f),
            start = Offset(-shift, 0f),
            end = Offset(w - shift, h),
        )
        drawOutline(outline, rim, alpha = 0.35f, style = wideRim)
        drawOutline(outline, rim, style = thinRim)
        // Дисперсия: свет раскладывается на цвета по краям
        translate(-dp, -dp) { drawOutline(outline, Color(0xFF7DF9FF).copy(alpha = 0.20f), style = fringe) }
        translate(dp, dp) { drawOutline(outline, Color(0xFFFF8AD8).copy(alpha = 0.18f), style = fringe) }
    }
}

/**
 * Знак бесконтактной оплаты (Contactless Indicator, EMVCo) — тот самый, что печатают на терминалах
 * и картах и показывают рядом с Apple Pay: четыре дуги, растущие вправо. Дуги загораются по очереди
 * изнутри наружу, за ними пульсирует мягкое свечение.
 */
@Composable
fun ContactlessWaves(color: Color, animate: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "waves")
    val phase = if (animate) {
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "phase").value
    } else {
        0.6f
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val c = Offset(w * 0.08f, h / 2)
        // Свечение расходится от знака и гаснет
        val glow = if (animate) 1f - phase else 0f
        drawCircle(color.copy(alpha = 0.16f * glow), radius = h * (0.45f + 0.35f * phase), center = Offset(w * 0.5f, h / 2))
        for (i in 0..3) {
            val r = h * (0.16f + 0.19f * i)
            val peak = (i + 1) / 5f
            val pulse = (1f - kotlin.math.abs(phase - peak) * 5f).coerceIn(0f, 1f)
            drawArc(
                color.copy(alpha = if (animate) 0.55f + 0.45f * pulse else 1f),
                startAngle = -40f,
                sweepAngle = 80f,
                useCenter = false,
                topLeft = Offset(c.x - r, c.y - r),
                size = Size(2 * r, 2 * r),
                style = Stroke(width = h * 0.075f, cap = StrokeCap.Round),
            )
        }
    }
}

/** Зелёный круг «выпрыгивает», галочка прорисовывается, и от круга расходится кольцо. */
@Composable
fun CheckBadge(color: Color, animate: Boolean, modifier: Modifier = Modifier) {
    val pop = remember { Animatable(if (animate) 0.3f else 1f) }
    val draw = remember { Animatable(if (animate) 0f else 1f) }
    val ring = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!animate) return@LaunchedEffect
        launch { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f)) }
        launch { ring.animateTo(1f, tween(750, easing = FastOutSlowInEasing)) }
        draw.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
    }
    Canvas(modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }) {
        val w = size.width
        if (ring.value < 1f) {
            drawCircle(color.copy(alpha = 0.55f * (1f - ring.value)), radius = w / 2 * (1f + 0.7f * ring.value),
                style = Stroke(width = 2.dp.toPx()))
        }
        drawCircle(color)
        val tick = Path().apply {
            moveTo(w * 0.28f, w * 0.52f)
            lineTo(w * 0.44f, w * 0.67f)
            lineTo(w * 0.73f, w * 0.35f)
        }
        val part = Path()
        val measure = PathMeasure()
        measure.setPath(tick, false)
        measure.getSegment(0f, measure.length * draw.value, part, true)
        drawPath(part, Color.White, style = Stroke(width = w * 0.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Цвет кнопок карточки уведомления по настройке: сиреневый, системный, из аватара или свой. */
@Composable
fun rememberAccent(s: IslandSettings, adaptive: Color? = null): Color {
    val context = LocalContext.current
    return remember(s.accentMode, s.accentColor, adaptive) { resolveAccent(context, s, adaptive) }
}

fun resolveAccent(context: android.content.Context, s: IslandSettings, adaptive: Color?): Color = when (s.accentMode) {
    AccentMode.DEFAULT -> Tints.Lavender
    AccentMode.CUSTOM -> Color(s.accentColor)
    AccentMode.ADAPTIVE -> adaptive ?: Tints.Lavender
    AccentMode.SYSTEM -> systemAccent(context) ?: Tints.Lavender
}

/** Акцент обоев (Material You) — светлый тон 200, хорошо читается на чёрном. Только Android 12+. */
fun systemAccent(context: android.content.Context): Color? =
    if (android.os.Build.VERSION.SDK_INT >= 31) {
        runCatching { Color(context.getColor(android.R.color.system_accent1_200)) }.getOrNull()
    } else {
        null
    }

/** Чем рисовать поверх [accent]: чёрным на светлом цвете, белым на тёмном. */
fun onAccent(accent: Color): Color = if (accent.luminance() > 0.5f) Color(0xFF1B1830) else Color.White

/**
 * Стрелка манёвра, как в навигаторе: прямо, налево, направо или разворот. Рисуется линией
 * со скруглёнными концами, поэтому одинаково чёткая в капсуле и в раскрытом виде.
 */
@Composable
fun TurnArrow(turn: Turn, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val k = size.minDimension / 100f
        fun p(x: Float, y: Float) = Offset(x * k, y * k)
        // Рисуем «направо», налево — зеркало по горизонтали
        val flip = turn == Turn.LEFT
        fun x(v: Float) = if (flip) 100f - v else v
        val body = Path()
        val head = Path()
        when (turn) {
            Turn.STRAIGHT -> {
                body.moveTo(50 * k, 86 * k); body.lineTo(50 * k, 20 * k)
                head.moveTo(30 * k, 40 * k); head.lineTo(50 * k, 18 * k); head.lineTo(70 * k, 40 * k)
            }
            Turn.RIGHT, Turn.LEFT -> {
                body.moveTo(x(32f) * k, 88 * k); body.lineTo(x(32f) * k, 52 * k)
                body.quadraticBezierTo(x(32f) * k, 30 * k, x(54f) * k, 30 * k); body.lineTo(x(76f) * k, 30 * k)
                head.moveTo(x(60f) * k, 12 * k); head.lineTo(x(80f) * k, 30 * k); head.lineTo(x(60f) * k, 48 * k)
            }
            Turn.UTURN -> {
                body.moveTo(30 * k, 88 * k); body.lineTo(30 * k, 40 * k)
                body.quadraticBezierTo(30 * k, 14 * k, 54 * k, 14 * k)
                body.quadraticBezierTo(76 * k, 14 * k, 76 * k, 40 * k); body.lineTo(76 * k, 62 * k)
                head.moveTo(60 * k, 48 * k); head.lineTo(76 * k, 66 * k); head.lineTo(92 * k, 48 * k)
            }
        }
        val stroke = Stroke(width = 11 * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(body, color, style = stroke)
        drawPath(head, color, style = stroke)
    }
}
