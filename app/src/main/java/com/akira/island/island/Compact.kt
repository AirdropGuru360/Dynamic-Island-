@file:OptIn(ExperimentalFoundationApi::class)

package com.akira.island.island

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.Image
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.AppMessage
import com.akira.island.core.CallInfo
import com.akira.island.core.Controls
import com.akira.island.core.Lyrics
import com.akira.island.core.MediaInfo
import com.akira.island.core.Notice
import com.akira.island.core.NoticeStyle
import com.akira.island.core.Tints
import com.akira.island.core.VolumeInfo
import com.akira.island.core.formatDuration
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import androidx.compose.runtime.collectAsState
import com.akira.island.ui.Inter
import kotlinx.coroutines.launch

/** Цифры одной ширины, чтобы время не «прыгало» */
val Digits = TextStyle(fontFamily = Inter, fontFeatureSettings = "tnum", fontWeight = FontWeight.SemiBold)

/** Высота капсулы, когда в ней что-то есть: чуть толще пустой, чтобы не выглядеть тонкой полоской. */
fun activeHeight(s: IslandSettings): Dp = (s.height + s.activeExtra).dp

/** Свободное место над камерой посередине — содержимое туда не заходит. */
fun compactGap(s: IslandSettings): Dp = s.cameraGap.dp.coerceAtLeast(8.dp)

/** Самое широкое содержимое одной боковой части. */
fun sideMax(s: IslandSettings): Dp = activeHeight(s) * 2.6f

/** Высота события: заметно выше свёрнутого острова, как у раскрытых уведомлений iPhone. */
fun noticeHeight(s: IslandSettings): Dp = (s.height.dp * 1.75f).coerceAtLeast(58.dp)

/** Ниже этой линии камера уже не мешает тексту — сюда ставится содержимое раскрытых карточек. */
fun cameraInset(s: IslandSettings): Dp = s.height.dp * 0.5f + 15.dp

@Composable
fun CompactContent(act: Act, s: IslandSettings, lyrics: Lyrics?, wideWidth: Dp) {
    val h = activeHeight(s)
    val gap = compactGap(s)
    val side = sideMax(s)
    val icon = h * 0.5f
    when (act) {
        is Act.Idle -> Box(Modifier.size(s.width.dp, s.height.dp))

        is Act.MediaA -> MediaCompact(act.m, s, lyrics)

        is Act.TimerA -> {
            val now = rememberNow(250)
            if (act.t.ringing) {
                val wobble = rememberInfiniteTransition(label = "bell")
                val angle = wobble.animateFloat(-18f, 18f, infiniteRepeatable(tween(120), RepeatMode.Reverse), label = "a")
                CompactRow(h, gap, side * 1.4f,
                    left = {
                        Icon(Ios.bell, null, tint = Tints.Orange, modifier = Modifier.size(icon).graphicsLayer {
                            rotationZ = if (s.reduceEffects) 0f else angle.value
                            transformOrigin = TransformOrigin(0.5f, 0.1f)
                        })
                    },
                    right = { Text("Таймер", color = Tints.Orange, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
                )
            } else {
                val left = act.t.left(now)
                val color = if (left <= 10_000 && !act.t.paused) Tints.Red else Tints.Orange
                CompactRow(h, gap, side * 1.4f,
                    left = { Icon(if (act.t.paused) Ios.pause else Ios.timer, null, tint = color, modifier = Modifier.size(icon)) },
                    right = { TimeText(formatDuration(left + 999), color) },
                )
            }
        }

        is Act.StopwatchA -> {
            val now = rememberNow(250, active = act.w.running)
            CompactRow(h, gap, side * 1.4f,
                left = { Icon(Ios.stopwatch, null, tint = Tints.Orange, modifier = Modifier.size(icon)) },
                right = { TimeText(formatDuration(act.w.elapsed(now)), Tints.Orange) },
            )
        }

        is Act.ChronoA -> {
            val now = rememberWallNow(500)
            // Запись экрана — красная, как на iPhone; таймеры и секундомеры Часов — оранжевые
            val recording = act.c.isRecording
            val tint = if (recording) Tints.Red else Tints.Orange
            CompactRow(h, gap, side * 1.4f,
                left = {
                    if (recording) Equalizer(tint, true, !s.reduceEffects, Modifier.size(h * 0.62f, h * 0.42f), bars = 5)
                    else AppIcon(act.c.icon, h * 0.56f, Ios.timer, tint)
                },
                right = { TimeText(formatDuration(act.c.elapsed(now)), tint) },
            )
        }

        is Act.CallA -> if (act.c.incoming) {
            IncomingCall(act.c, s, wideWidth)
        } else {
            val now = rememberWallNow(500)
            CompactRow(h, gap, side * 2f,
                left = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ios.call, null, tint = Tints.Green, modifier = Modifier.size(h * 0.4f))
                        Spacer(Modifier.width(5.dp))
                        TimeText(formatDuration(now - act.c.base), Tints.Green, 15)
                    }
                },
                right = { Equalizer(Tints.Green, true, !s.reduceEffects, Modifier.size(h * 0.62f, h * 0.42f), bars = 5) },
            )
        }

        is Act.TorchA -> CompactRow(h, gap, side,
            left = { Icon(Ios.flashlight, null, tint = Tints.Yellow, modifier = Modifier.size(icon)) },
            right = { GlowDot(Tints.Yellow, h * 0.26f, !s.reduceEffects) },
        )

        is Act.ProgressA -> CompactRow(h, gap, side,
            left = { AppIcon(act.p.icon, h * 0.56f, Ios.download, Tints.Blue) },
            right = {
                if (act.p.indeterminate) {
                    Icon(Ios.download, null, tint = Tints.Blue, modifier = Modifier.size(icon))
                } else {
                    ProgressRing(act.p.fraction, Tints.Blue, Modifier.size(h * 0.52f), stroke = 3.dp)
                }
            },
        )

        is Act.ChargingA -> CompactRow(h, gap, side * 1.6f,
            left = { Icon(Ios.flash, null, tint = Tints.Green, modifier = Modifier.size(icon)) },
            right = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimeText("${act.b.level}%", Tints.Green, 15)
                    Spacer(Modifier.width(5.dp))
                    BatteryGlyph(act.b.level, Tints.Green, Modifier.size(h * 0.6f, h * 0.3f))
                }
            },
        )

        is Act.VolumeA -> VolumeCompact(act.v, s, wideWidth)

        is Act.NoticeA -> NoticeCompact(act.n, s, wideWidth)
    }
}

@Composable
private fun TimeText(text: String, color: Color, size: Int = 16) {
    Text(text, color = color, fontSize = size.sp, style = Digits, maxLines = 1, softWrap = false)
}

/** Мягко светящаяся точка — признак того, что фонарик горит. */
@Composable
private fun GlowDot(color: Color, size: Dp, animate: Boolean) {
    val t = rememberInfiniteTransition(label = "glow")
    val k = if (animate) t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "k").value else 1f
    Box(Modifier.size(size * 1.8f), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size * 1.8f * k).clip(CircleShape).background(color.copy(alpha = 0.25f * k)))
        Box(Modifier.size(size).clip(CircleShape).background(color))
    }
}

/**
 * Левая и правая части свёрнутой капсулы, как на iPhone: значок у левого края, текст или время
 * у правого, посередине камера. Капсула шире пустой на свою высоту с каждой стороны, а если
 * содержимое длиннее — расширяется ровно настолько, чтобы не заходить на камеру.
 * Отступ от края равен отступу сверху — содержимое вписано в скругление, как требует Apple.
 */
@Composable
fun CompactRow(
    h: Dp,
    gap: Dp,
    side: Dp,
    left: @Composable BoxScope.() -> Unit,
    right: @Composable BoxScope.() -> Unit,
) {
    val baseWidth = SettingsRepo.state.collectAsState().value.width.dp + h * 2f
    Layout(
        content = {
            Box(contentAlignment = Alignment.Center, content = left)
            Box(contentAlignment = Alignment.Center, content = right)
        },
    ) { measurables, _ ->
        val hPx = h.roundToPx()
        val pad = (h * 0.3f).roundToPx()
        val gapPx = gap.roundToPx()
        val loose = Constraints(maxWidth = side.roundToPx().coerceAtLeast(1), maxHeight = hPx)
        val l = measurables[0].measure(loose)
        val r = measurables[1].measure(loose)
        val needed = 2 * (pad + maxOf(l.width, r.width)) + gapPx
        val w = maxOf(needed, baseWidth.roundToPx())
        layout(w, hPx) {
            l.place(pad, (hPx - l.height) / 2)
            r.place(w - pad - r.width, (hPx - r.height) / 2)
        }
    }
}

@Composable
private fun MediaCompact(m: MediaInfo, s: IslandSettings, lyrics: Lyrics?) {
    val h = activeHeight(s)
    val art = h * 0.6f
    val gap = compactGap(s)
    val lines = lyrics?.takeIf { it.key == m.key && s.lyricsEnabled && s.lyricsInCapsule }
    if (lines != null) {
        // Строка песни прямо в капсуле: слева обложка и волна, справа — текст
        val now = rememberNow(200, active = m.playing)
        val text = lines.lines.getOrNull(lines.indexAt(m.positionAt(now)))?.text?.ifBlank { "♪" } ?: "♪"
        CompactRow(h, gap, 112.dp,
            left = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Art(m.art, art, art * 0.27f)
                    Spacer(Modifier.width(8.dp))
                    Equalizer(m.accent, m.playing, !s.reduceEffects, Modifier.size(h * 0.5f, h * 0.4f))
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
                        line, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                        modifier = Modifier.width(104.dp).basicMarquee(iterations = Int.MAX_VALUE),
                    )
                }
            },
        )
    } else {
        CompactRow(h, gap, sideMax(s),
            left = { Art(m.art, art, art * 0.27f) },
            right = { Equalizer(m.accent, m.playing, !s.reduceEffects, Modifier.size(h * 0.62f, h * 0.44f)) },
        )
    }
}

@Composable
private fun VolumeCompact(v: VolumeInfo, s: IslandSettings, wideWidth: Dp) {
    val context = LocalContext.current
    val h = activeHeight(s)
    val width = (s.width.dp + 130.dp).coerceAtMost(wideWidth)
    val icon = when {
        v.level == 0 -> Ios.volumeOff
        v.fraction < 0.5f -> Ios.volumeLow
        else -> Ios.volumeHigh
    }
    Row(
        Modifier.width(width).height(h).padding(horizontal = h * 0.36f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(h * 0.5f))
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
                    .height(7.dp)
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

/**
 * Событие как на iPhone. Широкое — остров разворачивается почти на всю ширину экрана:
 * слева значок и подпись, справа состояние, батарейка или кольцо. Пилюля — короткое
 * переключение (режим звука), значок качается. Сообщение — аватар, имя и текст.
 */
@Composable
private fun NoticeCompact(n: Notice, s: IslandSettings, wideWidth: Dp) {
    when {
        n.style == NoticeStyle.MESSAGE && n.message != null -> MessageCard(n, n.message, s, wideWidth)
        n.style == NoticeStyle.PILL || n.title.isBlank() && n.state == null -> PillNotice(n, s)
        else -> WideNotice(n, s, wideWidth)
    }
}

@Composable
private fun WideNotice(n: Notice, s: IslandSettings, wideWidth: Dp) {
    // Значок «выпрыгивает», батарейка и кольцо заполняются — как на iPhone
    val pop = remember(n.id) { Animatable(if (s.reduceEffects) 1f else 0.3f) }
    val fill = remember(n.id) { Animatable(if (s.reduceEffects) 1f else 0f) }
    LaunchedEffect(n.id) {
        launch { pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 320f)) }
        fill.animateTo(1f, tween(900, delayMillis = 150, easing = FastOutSlowInEasing))
    }
    val height = noticeHeight(s)
    val width = wideWidth
    // Посередине — место под камеру: подпись и состояние к ней не заходят
    Row(
        Modifier.width(width).height(height).padding(horizontal = height * 0.42f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(n.icon, null, tint = n.tint, modifier = Modifier.size(height * 0.42f).scale(pop.value))
            Spacer(Modifier.width(10.dp))
            Column {
                if (n.caption != null) {
                    Text(n.caption, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, maxLines = 1)
                }
                Text(
                    n.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(compactGap(s)))
        Row(Modifier.weight(1f), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
            verticalAlignment = Alignment.CenterVertically) {
            if (n.state != null) {
                Text(n.state, color = n.tint, fontSize = 17.sp, style = Digits, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
            }
            if (n.battery != null) {
                Spacer(Modifier.width(8.dp))
                BatteryGlyph((n.battery * fill.value).toInt(), n.tint, Modifier.size(32.dp, 15.dp))
            }
            if (n.ring != null) {
                Spacer(Modifier.width(8.dp))
                // Кольцо с числом внутри — как заряд AirPods
                Box(Modifier.size(height * 0.6f), contentAlignment = Alignment.Center) {
                    ProgressRing(n.ring * fill.value, n.tint, Modifier.fillMaxSize(), stroke = 3.5.dp)
                    Text("${(n.ring * 100).toInt()}", color = n.tint, fontSize = 13.sp, style = Digits)
                }
            }
        }
    }
}

/** Пилюля режима звука: значок слева качается, как колокольчик на iPhone, состояние справа. */
@Composable
private fun PillNotice(n: Notice, s: IslandSettings) {
    val h = activeHeight(s)
    val swing = remember(n.id) { Animatable(0f) }
    LaunchedEffect(n.id) {
        if (s.reduceEffects) return@LaunchedEffect
        // Затухающее покачивание: 22° → -16° → 12° → … → 0
        var amp = 22f
        repeat(6) {
            swing.animateTo(amp, tween(70))
            amp = -amp * 0.75f
        }
        swing.animateTo(0f, spring(dampingRatio = 0.5f))
    }
    CompactRow(h, compactGap(s), h * 2.4f,
        left = {
            Icon(
                n.icon, null, tint = n.tint,
                modifier = Modifier.size(h * 0.5f).graphicsLayer {
                    rotationZ = swing.value
                    transformOrigin = TransformOrigin(0.5f, 0.1f)
                },
            )
        },
        right = {
            if (n.state != null) {
                Text(n.state, color = n.tint, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
        },
    )
}

/**
 * Уведомление приложения. Как в раскрытом острове iPhone, текст обтекает камеру:
 * в верхней полосе слева аватар и имя, справа «сейчас» и значок, а сам текст — ниже камеры.
 */
@Composable
private fun MessageCard(n: Notice, m: AppMessage, s: IslandSettings, wideWidth: Dp) {
    val band = activeHeight(s)
    val width = wideWidth
    val leftMax = (width - compactGap(s)) / 2 - 16.dp
    Column(Modifier.width(width).padding(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 14.dp)) {
        Row(Modifier.fillMaxWidth().height(band), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.widthIn(max = leftMax), verticalAlignment = Alignment.CenterVertically) {
                Avatar(m, n, band * 0.84f)
                Spacer(Modifier.width(9.dp))
                Text(n.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.weight(1f))
            Text("сейчас", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp, maxLines = 1)
            if (m.appIcon != null && m.avatar != null) {
                Spacer(Modifier.width(7.dp))
                Image(m.appIcon, null, Modifier.size(18.dp).clip(CircleShape))
            }
        }
        if (m.text.isNotEmpty()) {
            Text(
                m.text, color = Color.White.copy(alpha = 0.82f), fontSize = 15.sp, lineHeight = 19.sp, maxLines = 3,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        }
    }
}

@Composable
private fun Avatar(m: AppMessage, n: Notice, size: Dp) {
    val big = m.avatar ?: m.appIcon
    Box(Modifier.size(size).clip(CircleShape).background(Color(0xFF2C2C2E)), contentAlignment = Alignment.Center) {
        if (big != null) {
            Image(big, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(n.icon, null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
        }
    }
}

/**
 * Входящий звонок, как на iPhone: аватар, подпись с именем и две кнопки стоят на одной линии
 * по центру. Вся строка опущена под камеру, поэтому вырез не закрывает текст.
 */
@Composable
private fun IncomingCall(c: CallInfo, s: IslandSettings, wideWidth: Dp) {
    val context = LocalContext.current
    // Верх строки — на нижнем крае камеры, низ — с отступом как по бокам
    val top = cameraInset(s) - 10.dp
    Row(
        Modifier.width(wideWidth).padding(start = 14.dp, end = 14.dp, top = top, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0xFF3A3A3C)), contentAlignment = Alignment.Center) {
            if (c.avatar != null) {
                Image(c.avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(c.name.take(1).uppercase(), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            Text("Входящий вызов", color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, lineHeight = 16.sp, maxLines = 1)
            Text(c.name, color = Color.White, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        RoundButton(Ios.call, size = 52.dp, bg = Tints.Red, iconScale = 0.44f, iconRotation = 135f) {
            Controls.endCall(context, c)
        }
        Spacer(Modifier.width(12.dp))
        RoundButton(Ios.call, size = 52.dp, bg = Tints.Green, iconScale = 0.44f) {
            Controls.answerCall(context, c)
        }
    }
}
