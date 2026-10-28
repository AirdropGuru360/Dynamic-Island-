package com.akira.island.island

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.akira.island.core.NoticeStyle
import com.akira.island.core.Tints
import com.akira.island.data.IslandSettings
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Оплата вокруг острова — как в ролике. Пока ждём терминал, от острова расходятся сиреневые волны NFC;
 * когда оплата прошла, остров подпрыгивает, от него разлетаются зелёное кольцо и искры.
 * Выключается в «Эффектах» («Волны оплаты») и вместе со всеми — «Уменьшить эффекты».
 */
object PayFx {
    /** Насколько остров сейчас больше обычного: на «Оплачено» он на миг подрастает на 6% */
    val pop = mutableFloatStateOf(1f)
}

@Composable
fun PayEffects(primary: Act, body: () -> Rect, s: IslandSettings) {
    if (s.reduceEffects || !s.fxPay) return
    val notice = (primary as? Act.NoticeA)?.n
    when {
        primary is Act.PayA -> PayWaves(body, s.notch)
        notice?.style == NoticeStyle.PAY_DONE -> key(notice.id) { PayBurst(body, notice.tint, s.notch) }
    }
}

/** Волны NFC: новая каждые 0,9 с, расходится от краёв острова и гаснет за 1,35 с. */
@Composable
private fun PayWaves(body: () -> Rect, notch: Boolean) {
    val clock = rememberInfiniteTransition(label = "nfc")
    val t by clock.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "t")
    Canvas(Modifier.fillMaxSize()) {
        val b = body()
        if (b.isEmpty) return@Canvas
        for (j in 0..1) {
            val p = (t + j) / 1.5f
            if (p >= 1f) continue
            val e = ExpoOut.transform(p)
            val d = 6.dp.toPx() + e * 90.dp.toPx()
            val alpha = (1f - p) * 0.75f
            outline(b, d, Tints.Lavender.copy(alpha = alpha * 0.25f), 7.dp.toPx(), notch)
            outline(b, d, Tints.Lavender.copy(alpha = alpha), 1.6.dp.toPx(), notch)
        }
    }
}

/** Успех: кольцо уходит от острова, искры разлетаются вниз и в стороны, остров подпрыгивает. */
@Composable
private fun PayBurst(body: () -> Rect, color: Color, notch: Boolean) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val pop = Animatable(1.06f)
        PayFx.pop.floatValue = 1.06f
        try {
            launch { pop.animateTo(1f, tween(450, easing = ExpoOut)) { PayFx.pop.floatValue = value } }
            p.animateTo(1f, tween(1000, easing = ExpoOut))
        } finally {
            PayFx.pop.floatValue = 1f
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val b = body()
        if (b.isEmpty || p.value >= 1f) return@Canvas
        val e = p.value
        outline(b, 4.dp.toPx() + e * 130.dp.toPx(), color.copy(alpha = 1f - e), 3.dp.toPx(), notch)
        // 22 искры веером вниз: зелёные и белые, как в ролике
        val c = b.center
        for (i in 0 until 22) {
            val a = Math.toRadians(8.0 + 164.0 * i / 21).toFloat()
            val spread = 18.dp.toPx() + e * (40.dp.toPx() + Seeds[i] * 50.dp.toPx())
            val x = c.x + cos(a) * (b.width / 2 + spread)
            val y = c.y + sin(a) * (b.height / 2 + spread * 0.8f)
            drawCircle(if (i % 2 == 0) color else Color.White, radius = 2.dp.toPx(), center = Offset(x, y), alpha = 1f - e)
        }
    }
}

/** Случайные, но одни и те же на каждом кадре — искры не мерцают. */
private val Seeds = FloatArray(22) { i -> ((sin(i * 127.1 + 311.7) * 43758.5453) % 1.0).let { if (it < 0) it + 1.0 else it }.toFloat() }

/** Скруглённый контур вокруг острова на расстоянии [d]: та же форма, только больше (у «Как вырез» — с прямым верхом). */
private fun DrawScope.outline(b: Rect, d: Float, color: Color, width: Float, notch: Boolean) {
    val corner = min(b.height / 2f, 46.dp.toPx())
    drawIslandRect(
        color,
        topLeft = Offset(b.left - d, b.top - d),
        size = Size(b.width + d * 2, b.height + d * 2),
        corner = corner + d,
        notch = notch,
        style = Stroke(width),
    )
}
