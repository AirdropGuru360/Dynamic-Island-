package com.akira.island.island

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.akira.island.core.BatteryInfo
import com.akira.island.core.Controls
import com.akira.island.core.IslandBus
import com.akira.island.core.Lyrics
import com.akira.island.core.Tints
import com.akira.island.data.ExpandStyle
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import com.akira.island.data.TapAction
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Запас вокруг острова в окне: под тень, «желе» и пузырь. */
const val ISLAND_PAD_DP = 12

private data class Frame(val act: Act, val expanded: Boolean)

@Composable
fun IslandOverlay(
    onTransition: () -> Unit = {},
    onBodyBounds: (Rect) -> Unit = {},
    onBubbleBounds: (Rect) -> Unit = {},
) {
    val s by SettingsRepo.state.collectAsState()
    val media by IslandBus.media.collectAsState()
    val lyrics by IslandBus.lyrics.collectAsState()
    val progress by IslandBus.progress.collectAsState()
    val chrono by IslandBus.chrono.collectAsState()
    val calls by IslandBus.calls.collectAsState()
    val torch by IslandBus.torch.collectAsState()
    val timer by IslandBus.timer.collectAsState()
    val stopwatch by IslandBus.stopwatch.collectAsState()
    val notice by IslandBus.notice.collectAsState()
    val volume by IslandBus.volume.collectAsState()
    val battery by IslandBus.battery.collectAsState()
    val foreground by IslandBus.foregroundPackage.collectAsState()
    val expanded by IslandBus.expanded.collectAsState()
    val focus by IslandBus.focus.collectAsState()

    val call = if (s.cardCall) calls.values.maxByOrNull { it.postTime } else null
    val acts = buildList {
        // Входящий звонок важнее всего, как на iPhone
        if (call != null && call.incoming) add(Act.CallA(call))
        notice?.let { add(Act.NoticeA(it)) }
        if (volume != null && s.cardVolume) add(Act.VolumeA(volume!!))

        val persistent = mutableListOf<Act>()
        val t = timer
        if (t != null && t.ringing) persistent += Act.TimerA(t)
        if (call != null && !call.incoming) persistent += Act.CallA(call)
        if (torch.on && s.cardTorch) persistent += Act.TorchA(torch)
        if (t != null && !t.ringing) persistent += Act.TimerA(t)
        stopwatch?.let { persistent += Act.StopwatchA(it) }
        if (s.cardTimer) chrono.values.maxByOrNull { it.postTime }?.let { persistent += Act.ChronoA(it) }
        val m = media
        if (s.cardMedia && m != null && !(s.hideWhenPlayerOpen && foreground == m.pkg)) persistent += Act.MediaA(m)
        if (s.cardProgress) progress.values.maxByOrNull { it.postTime }?.let { persistent += Act.ProgressA(it) }
        if (s.cardCharging && battery.charging) persistent += Act.ChargingA(battery)

        // Выбранная активность выходит вперёд, но звонящий таймер всегда главный
        val ringing = (persistent.firstOrNull() as? Act.TimerA)?.t?.ringing == true || call?.incoming == true
        val i = persistent.indexOfFirst { it.id == focus }
        if (i > 0 && !ringing) persistent.add(0, persistent.removeAt(i))
        addAll(persistent)
    }

    val primary = acts.firstOrNull() ?: Act.Idle
    val incoming = (primary as? Act.CallA)?.c?.incoming == true
    val secondary = if (primary.transient || incoming) null else acts.drop(1).firstOrNull { !it.transient }
    val showExpanded = expanded && !primary.transient && !incoming
    val h = s.height.dp

    // Пузырь уходит с анимацией, поэтому помним последнюю активность в нём
    val lastSecondary = remember { arrayOfNulls<Act>(1) }
    if (secondary != null) lastSecondary[0] = secondary
    val bubbleVisible = secondary != null && !showExpanded
    val slot by animateDpAsState(if (bubbleVisible) h + 6.dp else 0.dp, islandSpec(s), label = "slot")

    // Раскрытая активность пропала (музыку закрыли, таймер отменили) — сворачиваемся, а не показываем часы
    val focusGone = expanded && focus != null && focus != Act.Idle.id && acts.none { it.id == focus }
    LaunchedEffect(focusGone) { if (focusGone) Controls.collapse() }

    val hidden = s.hideIdle && primary is Act.Idle && !showExpanded

    // Перед сменой формы просим окно стать большим, чтобы анимация шла внутри неподвижного окна
    val transitionKey = "${primary.id}|$showExpanded|${secondary?.id}|$hidden"
    val lastKey = remember { arrayOfNulls<String>(1) }
    SideEffect {
        if (lastKey[0] != transitionKey) {
            lastKey[0] = transitionKey
            onTransition()
        }
    }

    if (hidden) {
        SideEffect {
            onBodyBounds(Rect.Zero)
            onBubbleBounds(Rect.Zero)
        }
        return
    }

    // Остров меряется по своему размеру, а не по окну, и стоит сверху по центру окна
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        IslandRow(slot, s, primary, showExpanded, lyrics, battery, bubbleVisible, lastSecondary[0], onBodyBounds, onBubbleBounds)
    }
}

@Composable
private fun IslandRow(
    slot: Dp,
    s: IslandSettings,
    primary: Act,
    showExpanded: Boolean,
    lyrics: Lyrics?,
    battery: BatteryInfo,
    bubbleVisible: Boolean,
    bubbleAct: Act?,
    onBodyBounds: (Rect) -> Unit,
    onBubbleBounds: (Rect) -> Unit,
) {
    Row(
        Modifier
            .offset(x = s.offsetX.dp)
            .wrapContentSize(Alignment.TopCenter, unbounded = true)
            .padding(ISLAND_PAD_DP.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Пустое место слева уравновешивает пузырь, чтобы остров стоял над камерой
        Spacer(Modifier.width(slot))
        IslandBody(primary, showExpanded, s, lyrics, battery, onBodyBounds)
        BubbleSlot(slot, bubbleVisible, bubbleAct, s, onBubbleBounds)
    }
}

/** Отдельная функция: внутри Row подставился бы RowScope.AnimatedVisibility. */
@Composable
private fun BubbleSlot(slot: Dp, visible: Boolean, act: Act?, s: IslandSettings, onBounds: (Rect) -> Unit) {
    Box(
        Modifier.width(slot).onGloballyPositioned { onBounds(it.boundsInWindow()) },
        contentAlignment = Alignment.CenterEnd,
    ) {
        AnimatedVisibility(visible && act != null, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
            if (act != null) Bubble(act, s)
        }
    }
}

@Composable
private fun IslandBody(
    primary: Act,
    expanded: Boolean,
    s: IslandSettings,
    lyrics: Lyrics?,
    battery: BatteryInfo,
    onBounds: (Rect) -> Unit,
) {
    val context = LocalContext.current
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    val conf = LocalConfiguration.current
    val expandedWidth = (conf.screenWidthDp.dp - 20.dp).coerceAtMost(440.dp)
    val h = s.height.dp

    val corner by animateDpAsState(if (expanded) 38.dp else h / 2, islandSpec(s), label = "corner")
    val shape = RoundedCornerShape(corner)
    val accent = (primary as? Act.MediaA)?.m?.accent
    val outlineColor = if (s.outlineFromArt && accent != null) accent else Color(s.outlineColor)

    // «Как фрукт»: при раскрытии остров сплющивается и отпружинивает
    val jellyX = remember { Animatable(1f) }
    val jellyY = remember { Animatable(1f) }
    var firstRun by remember { mutableStateOf(true) }
    LaunchedEffect(expanded) {
        if (firstRun) {
            firstRun = false
            return@LaunchedEffect
        }
        if (s.expandStyle != ExpandStyle.FRUIT || s.reduceEffects) return@LaunchedEffect
        val amp = 0.07f * (1.25f - s.elasticity.damping)
        val dir = if (expanded) 1f else -1f
        launch {
            jellyX.animateTo(1f - amp * dir, tween(110))
            jellyX.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 260f))
        }
        launch {
            jellyY.animateTo(1f + amp * dir, tween(110))
            jellyY.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 260f))
        }
    }
    // Оттягивание вниз пальцем
    val pull = remember { Animatable(0f) }

    val current by rememberUpdatedState(primary)
    val isExpanded by rememberUpdatedState(expanded)
    val settings by rememberUpdatedState(s)

    Box(
        Modifier
            .onGloballyPositioned { onBounds(it.boundsInWindow()) }
            .graphicsLayer {
                scaleX = jellyX.value * (1f + 0.03f * pull.value)
                scaleY = jellyY.value * (1f + 0.08f * pull.value)
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
            .then(if (s.shadow && !s.reduceEffects) Modifier.shadow(10.dp, shape) else Modifier)
            .clip(shape)
            .background(Color(s.color))
            .then(if (s.outline) Modifier.border(s.outlineWidth.dp, outlineColor, shape) else Modifier)
            .pointerInput(s.doubleTapAction != TapAction.NOTHING, s.longPressAction != TapAction.NOTHING) {
                val doubleOn = settings.doubleTapAction != TapAction.NOTHING
                val longOn = settings.longPressAction != TapAction.NOTHING
                val onDouble: ((Offset) -> Unit)? = if (!doubleOn) null else {
                    { _: Offset ->
                        haptic()
                        perform(context, settings.doubleTapAction, current)
                    }
                }
                val onLong: ((Offset) -> Unit)? = if (!longOn) null else {
                    { _: Offset ->
                        haptic()
                        perform(context, settings.longPressAction, current)
                    }
                }
                detectTapGestures(
                    onTap = {
                        haptic()
                        onTap(context, current, isExpanded, settings)
                    },
                    onDoubleTap = onDouble,
                    onLongPress = onLong,
                )
            }
            .pointerInput(Unit) {
                val threshold = 36.dp.toPx()
                val side = 56.dp.toPx()
                var dx = 0f
                var dy = 0f
                detectDragGestures(
                    onDragStart = {
                        dx = 0f
                        dy = 0f
                    },
                    onDragEnd = {
                        scope.launch { pull.animateTo(0f, spring(dampingRatio = 0.45f)) }
                        val vertical = abs(dy) > abs(dx)
                        val act = current
                        when {
                            !isExpanded && vertical && dy > threshold && settings.swipeExpand && !act.transient -> {
                                haptic()
                                Controls.expand(act.id)
                            }
                            isExpanded && vertical && dy < -threshold -> {
                                haptic()
                                Controls.collapse()
                            }
                            !isExpanded && !vertical && abs(dx) > side && act is Act.MediaA && settings.swipeTracks -> {
                                haptic()
                                // Как в ПК-версии: толкнуть влево — следующий трек
                                if (dx < 0) Controls.next() else Controls.prev()
                            }
                        }
                    },
                    onDragCancel = { scope.launch { pull.animateTo(0f) } },
                    onDrag = { change, amount ->
                        change.consume()
                        dx += amount.x
                        dy += amount.y
                        if (!isExpanded && settings.swipeExpand) {
                            scope.launch { pull.snapTo((dy / (threshold * 3)).coerceIn(0f, 1f)) }
                        }
                    },
                )
            },
    ) {
        AnimatedContent(
            targetState = Frame(primary, expanded),
            contentKey = { "${it.act.id}|${it.expanded}" },
            contentAlignment = Alignment.TopCenter,
            transitionSpec = {
                val enter = if (s.reduceEffects) {
                    fadeIn(tween(150))
                } else {
                    fadeIn(tween(220, delayMillis = 70)) + scaleIn(tween(280, delayMillis = 40), initialScale = 0.88f)
                }
                (enter togetherWith fadeOut(tween(90))).using(SizeTransform(clip = true) { _, _ -> islandSpec(s) })
            },
            label = "island",
        ) { f ->
            if (f.expanded) {
                ExpandedContent(f.act, s, lyrics, battery, expandedWidth)
            } else {
                CompactContent(f.act, s, lyrics, expandedWidth)
            }
        }
    }
}

private fun onTap(context: Context, act: Act, expanded: Boolean, s: IslandSettings) {
    if (expanded) {
        Controls.collapse()
        return
    }
    when (act) {
        is Act.NoticeA -> IslandBus.notice.value = null
        is Act.VolumeA -> Unit
        is Act.Idle -> Controls.expand(act.id)
        is Act.TimerA -> if (act.t.ringing) Controls.cancelTimer() else perform(context, s.tapAction, act)
        is Act.CallA -> if (act.c.incoming) {
            if (!Controls.send(context, act.c.open)) Controls.openApp(context, act.c.pkg)
        } else {
            perform(context, s.tapAction, act)
        }
        else -> perform(context, s.tapAction, act)
    }
}

private fun perform(context: Context, action: TapAction, act: Act) {
    if (act.transient) return
    val hasMedia = IslandBus.media.value != null
    when (action) {
        TapAction.EXPAND -> Controls.expand(act.id)
        TapAction.OPEN_APP -> act.pkg?.let { Controls.openApp(context, it) } ?: Controls.expand(act.id)
        TapAction.PLAY_PAUSE -> if (hasMedia) Controls.playPause() else Controls.expand(act.id)
        TapAction.NEXT_TRACK -> if (hasMedia) Controls.next() else Controls.expand(act.id)
        TapAction.NOTHING -> Unit
    }
}

/** Вторая активность — круглый пузырь справа от острова. */
@Composable
private fun Bubble(act: Act, s: IslandSettings) {
    val h = s.height.dp
    val haptic = rememberHaptic()
    val now = rememberNow(500, active = act is Act.TimerA)
    Box(
        Modifier
            .size(h)
            .then(if (s.shadow && !s.reduceEffects) Modifier.shadow(8.dp, CircleShape) else Modifier)
            .clip(CircleShape)
            .background(Color(s.color))
            .then(if (s.outline) Modifier.border(s.outlineWidth.dp, Color(s.outlineColor), CircleShape) else Modifier)
            .clickable {
                haptic()
                Controls.expand(act.id)
            },
        contentAlignment = Alignment.Center,
    ) {
        val inner: Dp = h * 0.62f
        when (act) {
            is Act.MediaA -> Art(act.m.art, h - 10.dp, h / 2)
            is Act.TimerA -> {
                ProgressRing(
                    progress = if (act.t.totalMs > 0) act.t.left(now).toFloat() / act.t.totalMs else 0f,
                    color = Tints.Orange,
                    modifier = Modifier.size(inner),
                    stroke = 2.5.dp,
                )
                Icon(Icons.Rounded.HourglassBottom, null, tint = Tints.Orange, modifier = Modifier.size(inner * 0.55f))
            }
            is Act.StopwatchA -> Icon(Icons.Rounded.Timer, null, tint = Tints.Orange, modifier = Modifier.size(inner))
            is Act.TorchA -> Icon(Icons.Rounded.FlashlightOn, null, tint = Tints.Yellow, modifier = Modifier.size(inner))
            is Act.ChronoA -> AppIcon(act.c.icon, inner, Icons.Rounded.Timer, Tints.Green)
            is Act.ProgressA -> {
                ProgressRing(act.p.fraction, Tints.Blue, Modifier.size(inner), stroke = 2.5.dp)
                Icon(Icons.Rounded.Downloading, null, tint = Tints.Blue, modifier = Modifier.size(inner * 0.55f))
            }
            is Act.CallA -> Icon(Icons.Rounded.Call, null, tint = Tints.Green, modifier = Modifier.size(inner * 0.8f))
            is Act.ChargingA -> Icon(Icons.Rounded.BatteryChargingFull, null, tint = Tints.Green, modifier = Modifier.size(inner))
            else -> Unit
        }
    }
}
