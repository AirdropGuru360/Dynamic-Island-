@file:OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)

package com.akira.island.island

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.akira.island.core.AppMessage
import com.akira.island.core.CallInfo
import com.akira.island.core.Controls
import com.akira.island.core.IslandBus
import com.akira.island.core.LiveInfo
import com.akira.island.core.LiveKind
import com.akira.island.core.MsgAction
import com.akira.island.core.ReplyState
import com.akira.island.core.MediaInfo
import com.akira.island.core.Notice
import com.akira.island.core.NoticeStyle
import com.akira.island.core.Tints
import com.akira.island.core.formatDuration
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import com.akira.island.service.Messages
import androidx.compose.runtime.collectAsState
import com.akira.island.ui.Inter
import kotlinx.coroutines.delay
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
fun CompactContent(act: Act, s: IslandSettings, wideWidth: Dp) {
    val h = activeHeight(s)
    val gap = compactGap(s)
    val side = sideMax(s)
    val icon = h * 0.5f
    when (act) {
        is Act.Idle -> Box(Modifier.size(s.width.dp, s.height.dp))

        is Act.MediaA -> MediaCompact(act.m, s)

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
                    when {
                        recording -> Equalizer(tint, true, !s.reduceEffects, Modifier.size(h * 0.62f, h * 0.42f), bars = 5)
                        act.c.pausedMs != null -> Icon(Ios.pause, null, tint = tint, modifier = Modifier.size(icon))
                        else -> AppIcon(act.c.icon, h * 0.56f, Ios.timer, tint)
                    }
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

        is Act.LiveA -> LiveCompact(act.l, s)

        is Act.AlarmA -> AlarmCompact(act.a, s)

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
 * у правого, посередине камера. Капсула шире пустой на «Ширину с активностью»; если содержимое
 * не помещается, оно чуть уменьшается, а если и так длиннее — капсула расширяется ровно настолько,
 * чтобы не заходить на камеру.
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
    // Капсула с активностью шире спокойной на «Ширину с активностью»; длинный текст раздвинет её сам
    val st = SettingsRepo.state.collectAsState().value
    val baseWidth = st.width.dp + st.activeWidth.dp
    val density = LocalDensity.current
    SubcomposeLayout { _ ->
        val hPx = h.roundToPx()
        val pad = (h * 0.3f).roundToPx()
        val gapPx = gap.roundToPx()
        val loose = Constraints(maxWidth = side.roundToPx().coerceAtLeast(1), maxHeight = hPx)
        var l = subcompose("l") { Box(contentAlignment = Alignment.Center, content = left) }.first().measure(loose)
        var r = subcompose("r") { Box(contentAlignment = Alignment.Center, content = right) }.first().measure(loose)
        val base = baseWidth.roundToPx()
        val sideNeed = maxOf(l.width, r.width)
        // В узкой капсуле содержимое сначала уменьшается (до 70%) — как будто нарисовано мельче, —
        // и только если и так не помещается, капсулу раздвигает
        val sideRoom = (base - gapPx) / 2 - pad
        val k = if (sideNeed > sideRoom && sideRoom > 0) (sideRoom.toFloat() / sideNeed).coerceIn(0.7f, 1f) else 1f
        if (k < 1f) {
            val small = Density(density.density * k, density.fontScale)
            l = subcompose("ls") {
                CompositionLocalProvider(LocalDensity provides small) { Box(contentAlignment = Alignment.Center, content = left) }
            }.first().measure(loose)
            r = subcompose("rs") {
                CompositionLocalProvider(LocalDensity provides small) { Box(contentAlignment = Alignment.Center, content = right) }
            }.first().measure(loose)
        }
        val needed = 2 * (pad + maxOf(l.width, r.width)) + gapPx
        val w = maxOf(needed, base)
        layout(w, hPx) {
            l.place(pad, (hPx - l.height) / 2)
            r.place(w - pad - r.width, (hPx - r.height) / 2)
        }
    }
}

@Composable
private fun MediaCompact(m: MediaInfo, s: IslandSettings) {
    val h = activeHeight(s)
    val art = h * 0.6f
    CompactRow(h, compactGap(s), sideMax(s),
        left = { Art(m.art, art, art * 0.27f) },
        right = { Equalizer(m.accent, m.playing, !s.reduceEffects, Modifier.size(h * 0.62f, h * 0.44f)) },
    )
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
        n.style == NoticeStyle.PAY_READY -> PayReady(n, s, wideWidth)
        n.style == NoticeStyle.PAY_DONE -> PayDone(n, s, wideWidth)
        n.style == NoticeStyle.LIVE && n.live != null -> LiveBanner(n, n.live, s, wideWidth)
        n.style == NoticeStyle.ALARM && n.alarm != null -> AlarmBanner(n, n.alarm, s, wideWidth)
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
        fill.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
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
 * Уведомление приложения — большая плашка по образцу шторки уведомлений: слева круглый аватар
 * со значком приложения, рядом имя, «• сейчас» и колокольчик, справа счётчик непрочитанных
 * со стрелкой, ниже текст и кнопки из самого уведомления («Ответить», «Пометить прочитанным»)
 * цветом акцента. Вся плашка стоит ниже выреза камеры, поэтому имя и текст не заходят под неё.
 * «Ответить» открывает строку ввода со стрелкой отправки прямо в плашке, счётчик раскрывает переписку.
 */
@Composable
private fun MessageCard(n: Notice, m: AppMessage, s: IslandSettings, wideWidth: Dp) {
    val context = LocalContext.current
    val haptic = rememberHaptic()
    val iconSize = 46.dp
    val reply by IslandBus.reply.collectAsState()
    val locked by IslandBus.locked.collectAsState()
    // На заблокированном телефоне — как в шторке: без кнопок, а скрытые уведомления без имени и текста
    val hidden = locked && m.sensitive
    val replyAction = reply?.takeIf { it.noticeId == n.id && !locked }?.action
    val accent = rememberAccent(s, m.accent)
    var open by remember(n.id) { mutableStateOf(false) }

    // Пока отвечают или листают переписку, плашка не уходит по таймеру
    DisposableEffect(open, replyAction != null) {
        IslandBus.noticeHold.value = open || replyAction != null
        onDispose { IslandBus.noticeHold.value = false }
    }
    // Плашка ушла — ввод закрывается, окну возвращается «неактивный» режим
    DisposableEffect(n.id) {
        onDispose { if (IslandBus.reply.value?.noticeId == n.id) IslandBus.reply.value = null }
    }

    Column(
        Modifier
            .animateContentSize(islandSpec(s))
            .width(wideWidth)
            .padding(start = 18.dp, end = 18.dp, top = cameraInset(s) - 10.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            NotifIcon(if (hidden) m.copy(avatar = null) else m, n, iconSize, Color(s.color))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 30.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (hidden) m.appName else n.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(" • сейчас", color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp, maxLines = 1, softWrap = false)
                        Spacer(Modifier.width(6.dp))
                        Icon(Ios.bell, null, tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(15.dp))
                    }
                    if (m.history.size > 1 && !hidden) {
                        Spacer(Modifier.width(8.dp))
                        CountPill(m.history.size, open, accent) {
                            haptic()
                            open = !open
                        }
                    }
                }
                if (hidden) {
                    Text("Содержимое скрыто", color = Color.White.copy(alpha = 0.6f), fontSize = 17.sp, lineHeight = 22.sp, maxLines = 1)
                } else if (open && m.history.size > 1) {
                    m.history.forEachIndexed { i, line ->
                        Text(
                            line, color = Color.White.copy(alpha = if (i == m.history.lastIndex) 0.92f else 0.6f),
                            fontSize = 16.sp, lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                } else if (m.text.isNotEmpty()) {
                    Text(
                        m.text, color = Color.White.copy(alpha = 0.9f), fontSize = 17.sp, lineHeight = 22.sp, maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (replyAction != null) {
            ReplyField(replyAction, accent) { text ->
                if (text.isBlank()) return@ReplyField
                if (Controls.sendReply(context, replyAction, text)) {
                    Messages.onReplied(m.key)
                    IslandBus.reply.value = null
                    IslandBus.notice.value = null
                } else {
                    // Поле остаётся открытым с текстом — можно попробовать ещё раз или ответить в приложении
                    Toast.makeText(context, "Не удалось отправить ответ", Toast.LENGTH_SHORT).show()
                }
            }
        } else if (m.actions.isNotEmpty() && !locked) {
            // Кнопки стоят под текстом, на одной линии с ним, как в шторке
            Row(
                Modifier.fillMaxWidth().padding(start = iconSize + 14.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                m.actions.forEachIndexed { i, a ->
                    // Последняя кнопка уступает место, если подпись длинная
                    TextAction(a.title, accent, if (i == m.actions.lastIndex) Modifier.weight(1f, fill = false) else Modifier) {
                        if (a.isReply) {
                            IslandBus.reply.value = ReplyState(n.id, a)
                        } else {
                            Controls.send(context, a.intent)
                            IslandBus.notice.value = null
                        }
                    }
                }
            }
        }
    }
}

/** Круглый аватар (или значок приложения) со значком приложения в углу. */
@Composable
private fun NotifIcon(m: AppMessage, n: Notice, size: Dp, ring: Color) {
    Box(Modifier.size(size + 4.dp)) {
        val big = m.avatar ?: m.appIcon
        if (big != null) {
            Image(big, null, Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.size(size).clip(CircleShape).background(Color(0xFF2C2C2E)), contentAlignment = Alignment.Center) {
                Icon(n.icon, null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
            }
        }
        if (m.avatar != null && m.appIcon != null) {
            Image(
                m.appIcon, null,
                Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape).background(ring).padding(2.dp).clip(CircleShape),
            )
        }
    }
}

/** Счётчик непрочитанных со стрелкой: нажатие раскрывает всю переписку. */
@Composable
private fun CountPill(count: Int, open: Boolean, accent: Color, onClick: () -> Unit) {
    val ink = onAccent(accent)
    val rotation by animateFloatAsState(if (open) 180f else 0f, spring(dampingRatio = 0.6f, stiffness = 400f), label = "chevron")
    Row(
        Modifier.height(28.dp).clip(CircleShape).background(accent).clickable(onClick = onClick).padding(start = 11.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$count", color = ink, fontSize = 15.sp, style = Digits, maxLines = 1)
        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = ink, modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rotation })
    }
}

/** Кнопка под текстом: просто жирная подпись цвета [accent], чуть тускнеет под пальцем. */
@Composable
private fun TextAction(title: String, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val haptic = rememberHaptic()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(interaction, indication = null) {
                haptic()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title, color = accent.copy(alpha = if (pressed) 0.55f else 1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Строка ответа как в быстром ответе iOS: поле со скруглением и круглая кнопка со стрелкой вверх
 * цвета [accent] внутри него. Окну острова на это время выдаётся фокус (см. IslandService),
 * поэтому клавиатура покажется.
 */
@Composable
private fun ReplyField(action: MsgAction, accent: Color, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        // Окно получает фокус не сразу, поэтому просим клавиатуру несколько раз
        repeat(3) {
            delay(180)
            runCatching { focus.requestFocus() }
            keyboard?.show()
        }
    }
    val ready = text.isNotBlank()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(start = 16.dp, end = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontFamily = Inter),
            cursorBrush = SolidColor(accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend(text) }),
            modifier = Modifier.weight(1f).focusRequester(focus),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) {
                        Text("${action.title.ifBlank { "Ответ" }}…", color = Color.White.copy(alpha = 0.4f), fontSize = 15.sp, maxLines = 1)
                    }
                    inner()
                }
            },
        )
        Spacer(Modifier.width(8.dp))
        RoundButton(
            Icons.Rounded.ArrowUpward, size = 34.dp,
            bg = if (ready) accent else accent.copy(alpha = 0.35f), fg = onAccent(accent), iconScale = 0.58f,
        ) { onSend(text) }
    }
}

/**
 * Оплата, как Apple Pay: слева карта и «Приложите к терминалу», справа волны бесконтактной оплаты,
 * которые загораются по очереди. Посередине — место под камеру.
 */
@Composable
private fun PayReady(n: Notice, s: IslandSettings, wideWidth: Dp) {
    val height = noticeHeight(s)
    Row(
        Modifier.width(wideWidth).height(height).padding(horizontal = height * 0.42f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            ContactlessWaves(Color.White, !s.reduceEffects, Modifier.size(height * 0.56f))
            Spacer(Modifier.width(12.dp))
            Text(n.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Spacer(Modifier.width(compactGap(s)))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (n.caption != null) {
                Text(n.caption, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
            }
            if (n.appIcon != null) {
                Spacer(Modifier.width(10.dp))
                Image(n.appIcon, null, Modifier.size(height * 0.4f).clip(RoundedCornerShape(height * 0.1f)))
            }
        }
    }
}

/** Оплата прошла: зелёная галочка прорисовывается, справа сумма — как «Готово» в Apple Pay. */
@Composable
private fun PayDone(n: Notice, s: IslandSettings, wideWidth: Dp) {
    val haptic = rememberHaptic()
    LaunchedEffect(n.id) { haptic() }
    val height = noticeHeight(s)
    Row(
        Modifier.width(wideWidth).height(height).padding(horizontal = height * 0.42f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            CheckBadge(n.tint, !s.reduceEffects, Modifier.size(height * 0.44f))
            Spacer(Modifier.width(12.dp))
            Column {
                if (n.caption != null) {
                    Text(n.caption, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(n.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
        Spacer(Modifier.width(compactGap(s)))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (n.state != null) {
                Text(n.state, color = n.tint, fontSize = 18.sp, style = Digits, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Цвет «живой активности»: навигатор зелёный, такси жёлтое, доставка оранжевая. */
fun liveTint(kind: LiveKind): Color = when (kind) {
    LiveKind.NAV -> Tints.Green
    LiveKind.RIDE -> Tints.Yellow
    LiveKind.DELIVERY -> Tints.Orange
}

/**
 * Главное из уведомления для узкой капсулы: расстояние до манёвра; у навигатора без расстояния —
 * время в пути, у такси и доставки — заголовок. Если показывать нечего, возвращает null.
 */
fun liveShort(l: LiveInfo): String? = l.distance
    ?: if (l.kind == LiveKind.NAV) {
        l.eta?.substringAfterLast(" · ")?.takeIf { it.isNotBlank() }
    } else {
        l.title.ifBlank { l.text }.take(18).takeIf { it.isNotBlank() }
    }

/** Значок «живой активности»: стрелка манёвра, если известно куда ехать, иначе картинка из уведомления или значок приложения. */
@Composable
fun LiveGlyph(l: LiveInfo, size: Dp, tint: Color) {
    when {
        l.turn != null -> TurnArrow(l.turn, tint, Modifier.size(size))
        l.art != null -> Image(l.art, null, Modifier.size(size).clip(CircleShape))
        else -> AppIcon(l.icon, size, Ios.navigate, tint)
    }
}

/** Навигатор, такси или доставка в капсуле: значок приложения у левого края, главное — у правого. */
@Composable
private fun LiveCompact(l: LiveInfo, s: IslandSettings) {
    val h = activeHeight(s)
    val tint = liveTint(l.kind)
    CompactRow(h, compactGap(s), sideMax(s) * 1.5f,
        left = { LiveGlyph(l, if (l.turn != null) h * 0.5f else h * 0.56f, tint) },
        right = {
            val short = liveShort(l)
            if (short != null) {
                Text(short, color = tint, fontSize = 15.sp, style = Digits, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                GlowDot(tint, h * 0.22f, !s.reduceEffects)
            }
        },
    )
}

/**
 * Широкая плашка при запуске навигатора, такси или доставки — как у iPhone: сначала «живая активность»
 * показывается развёрнутой (стрелка и расстояние слева, подсказка и время справа), потом сжимается в капсулу.
 */
@Composable
private fun LiveBanner(n: Notice, l: LiveInfo, s: IslandSettings, wideWidth: Dp) {
    val height = noticeHeight(s)
    val tint = liveTint(l.kind)
    val pop = remember(n.id) { Animatable(if (s.reduceEffects) 1f else 0.3f) }
    LaunchedEffect(n.id) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 320f)) }
    Row(
        Modifier.width(wideWidth).height(height).padding(horizontal = height * 0.42f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.scale(pop.value)) { LiveGlyph(l, height * 0.5f, tint) }
            Spacer(Modifier.width(12.dp))
            Text(
                l.distance ?: l.title, color = Color.White, fontSize = if (l.distance != null) 22.sp else 16.sp,
                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(compactGap(s)))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
            if (l.text.isNotBlank()) {
                Text(l.text, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, lineHeight = 17.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
            }
            // На узкой плашке — коротко: «12:31 · 19 ч»; полностью — в раскрытом виде
            val shortEta = l.eta?.removePrefix("Прибытие в ")?.split(" · ")?.take(2)?.joinToString(" · ")
            if (shortEta != null) {
                Text(shortEta, color = tint, fontSize = 13.sp, style = Digits, maxLines = 1)
            }
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
