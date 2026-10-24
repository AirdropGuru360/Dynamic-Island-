package com.akira.island.island

import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
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
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
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
import com.akira.island.core.NoticeStyle
import com.akira.island.core.Tints
import com.akira.island.data.ExpandStyle
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import com.akira.island.data.TapAction
import com.akira.island.service.CallMonitor
import com.akira.island.service.NavScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Пример активности для подбора ширины: стрелка направо и «300 м». */
private val WidthDemo = com.akira.island.core.LiveInfo(
    key = "width-demo", pkg = "", kind = com.akira.island.core.LiveKind.NAV, title = "", text = "", sub = "",
    icon = null, art = null, postTime = 0L, open = null, distance = "300 м", turn = com.akira.island.core.Turn.RIGHT,
)

/** Цвет острова на экране «Размер и положение»: его нужно спрятать под вырезом камеры. */
val CalibrationRed = Color(0xFFFF6B6B)

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
    val live by IslandBus.live.collectAsState()
    val torch by IslandBus.torch.collectAsState()
    val timer by IslandBus.timer.collectAsState()
    val stopwatch by IslandBus.stopwatch.collectAsState()
    val notice by IslandBus.notice.collectAsState()
    val battery by IslandBus.battery.collectAsState()
    val foreground by IslandBus.foregroundPackage.collectAsState()
    val expanded by IslandBus.expanded.collectAsState()
    val focus by IslandBus.focus.collectAsState()
    val calibrating by IslandBus.calibrating.collectAsState()
    val nextAlarm by IslandBus.nextAlarm.collectAsState()
    val egg by IslandBus.egg.collectAsState()
    // Будильник появляется в острове за 12 часов — проверяем раз в полминуты
    var alarmNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(nextAlarm) {
        while (nextAlarm != null) {
            alarmNow = System.currentTimeMillis()
            delay(30_000)
        }
    }

    // Пробная «живая активность» исчезает по времени, даже если таймер удаления не сработал (сон телефона)
    val hasExpiring = live.values.any { it.expiresAt > 0 }
    var wallNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(hasExpiring) {
        while (hasExpiring) {
            wallNow = System.currentTimeMillis()
            delay(1000)
        }
    }
    // Навигатор, у которого в уведомлении пусто, дополняется тем, что видно на его экране
    val screenNav by NavScreen.data.collectAsState()
    val liveActive = live.values.filter { it.expiresAt == 0L || wallNow < it.expiresAt }
        .map { NavScreen.merge(it, screenNav[it.pkg]) }
        .let { list -> list + screenNav.values.filter { sn -> sn.distance != null && list.none { it.pkg == sn.pkg } }.map(NavScreen::synthetic) }

    val call = if (!s.cardCall) null else {
        val tel = calls[CallMonitor.KEY]
        val fromApp = calls.filterKeys { it != CallMonitor.KEY }.values.maxByOrNull { it.postTime }
        when {
            fromApp == null -> tel
            tel == null -> fromApp
            // У звонилки богаче карточка (имя, фото, кнопки), а состояние надёжнее у телефона
            else -> fromApp.copy(
                incoming = tel.incoming,
                base = if (!tel.incoming && fromApp.incoming) tel.base else fromApp.base,
                avatar = fromApp.avatar ?: tel.avatar,
            )
        }
    }
    val acts = buildList {
        // Входящий звонок важнее всего, как на iPhone
        if (call != null && call.incoming) add(Act.CallA(call))
        notice?.let { add(Act.NoticeA(it)) }

        val persistent = mutableListOf<Act>()
        val t = timer
        if (t != null && t.ringing) persistent += Act.TimerA(t)
        if (call != null && !call.incoming) persistent += Act.CallA(call)
        if (torch.on && s.cardTorch) persistent += Act.TorchA(torch)
        if (t != null && !t.ringing) persistent += Act.TimerA(t)
        if (s.cardLive) liveActive.maxByOrNull { it.postTime }?.let { persistent += Act.LiveA(it) }
        stopwatch?.let { persistent += Act.StopwatchA(it) }
        if (s.cardTimer) chrono.values.maxByOrNull { it.postTime }?.let { persistent += Act.ChronoA(it) }
        val m = media
        if (s.cardMedia && m != null && !(s.hideWhenPlayerOpen && foreground == m.pkg)) persistent += Act.MediaA(m)
        if (s.cardProgress) progress.values.maxByOrNull { it.postTime }?.let { persistent += Act.ProgressA(it) }
        if (s.cardCharging && battery.charging) persistent += Act.ChargingA(battery)
        // Будильник — тише всех: показывается, только когда больше ничего нет, и не уходит в пузырь
        val alarm = nextAlarm
        if (s.cardAlarm && alarm != null && alarm.triggerAt - alarmNow in 1..ALARM_LEAD_MS) persistent += Act.AlarmA(alarm)

        // Выбранная активность выходит вперёд, но звонящий таймер всегда главный
        val ringing = (persistent.firstOrNull() as? Act.TimerA)?.t?.ringing == true || call?.incoming == true
        val i = persistent.indexOfFirst { it.id == focus }
        if (i > 0 && !ringing) persistent.add(0, persistent.removeAt(i))
        addAll(persistent)
    }

    // Пока двигают ползунок «Ширина с активностью», на острове пример: стрелка и метры
    val previewUntil by IslandBus.widthPreviewUntil.collectAsState()
    var previewNow by remember { mutableLongStateOf(0L) }
    LaunchedEffect(previewUntil) {
        while (true) {
            previewNow = android.os.SystemClock.elapsedRealtime()
            if (previewNow >= previewUntil) break
            delay(250)
        }
    }
    val previewing = previewNow < previewUntil

    // Во время подгонки размера показываем только пустую красную капсулу
    val primary = when {
        calibrating -> Act.Idle
        previewing -> Act.LiveA(WidthDemo)
        else -> acts.firstOrNull() ?: Act.Idle
    }
    val incoming = (primary as? Act.CallA)?.c?.incoming == true
    val secondary = if (primary.transient || incoming || calibrating) null else acts.drop(1).firstOrNull { !it.transient && it !is Act.AlarmA }
    val showExpanded = expanded && !primary.transient && !incoming && !calibrating
    val h = s.height.dp

    // Пузырь уходит с анимацией, поэтому помним последнюю активность в нём
    val lastSecondary = remember { arrayOfNulls<Act>(1) }
    if (secondary != null) lastSecondary[0] = secondary
    val bubbleVisible = secondary != null && !showExpanded
    val slot by animateDpAsState(if (bubbleVisible) activeHeight(s) + 6.dp else 0.dp, islandSpec(s), label = "slot")

    // Раскрытая активность пропала (музыку закрыли, таймер отменили) — сворачиваемся, а не показываем часы
    val focusGone = expanded && focus != null && focus != Act.Idle.id && acts.none { it.id == focus }
    LaunchedEffect(focusGone) { if (focusGone) Controls.collapse() }

    val hidden = s.hideIdle && primary is Act.Idle && !showExpanded && !calibrating

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

    // Где остров в окне — вокруг него рисуются эффекты пасхалки
    val body = remember { mutableStateOf(Rect.Zero) }
    val onBody = remember(onBodyBounds) {
        { r: Rect ->
            body.value = r
            onBodyBounds(r)
        }
    }
    val eggOn = egg && !calibrating

    // Остров меряется по своему размеру, а не по окну, и стоит сверху по центру окна
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        // Оплата: волны NFC и вспышка успеха вокруг острова, как в ролике
        if (!calibrating) PayEffects(notice, { body.value }, s)
        // Пасхалка: свечение за островом, вспышки и звёзды поверх
        if (eggOn) EggGlow { body.value }
        IslandRow(slot, s, primary, showExpanded, lyrics, battery, bubbleVisible, lastSecondary[0], calibrating, onBody, onBubbleBounds)
        if (eggOn) {
            EggSparks { body.value }
            EggDriver({ body.value }, s)
        }
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
    calibrating: Boolean,
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
        IslandBody(primary, showExpanded, s, lyrics, battery, calibrating, onBodyBounds)
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
    calibrating: Boolean,
    onBounds: (Rect) -> Unit,
) {
    val context = LocalContext.current
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    val conf = LocalConfiguration.current
    // Раскрытый остров iPhone — 371 pt при ширине экрана 393 pt, то есть по 11 с каждой стороны
    val expandedWidth = (conf.screenWidthDp.dp - 22.dp).coerceAtMost(460.dp)
    val h = s.height.dp

    // Скругления — как в ролике: плеер и фонарик 46, навигатор 42, обновление 36, уведомление 32
    val cornerTarget = when {
        expanded && (primary is Act.MediaA || primary is Act.TorchA) -> 46.dp
        expanded && primary is Act.LiveA -> 42.dp
        expanded -> 44.dp
        primary is Act.NoticeA && (primary.n.style == NoticeStyle.MESSAGE || primary.n.style == NoticeStyle.EGG) -> 32.dp
        primary is Act.NoticeA && primary.n.style == NoticeStyle.UPDATE -> 36.dp
        primary is Act.NoticeA && primary.n.style == NoticeStyle.WIDE && primary.n.title.isNotBlank() -> noticeHeight(s) / 2
        primary is Act.NoticeA && (primary.n.style == NoticeStyle.PAY_READY || primary.n.style == NoticeStyle.PAY_DONE ||
            primary.n.style == NoticeStyle.LIVE || primary.n.style == NoticeStyle.ALARM || primary.n.style == NoticeStyle.SENT) -> noticeHeight(s) / 2
        primary is Act.CallA && primary.c.incoming -> callHeight(s) / 2
        primary is Act.Idle -> h / 2
        else -> activeHeight(s) / 2
    }
    val corner by animateDpAsState(cornerTarget, islandSpec(s), label = "corner")
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

    // Liquid Glass: при смене содержимого по стеклу пробегает блик, при наклоне телефона блики сдвигаются
    val tilt = IslandBus.glassTilt.collectAsState()
    val sheen = remember { Animatable(-1f) }
    LaunchedEffect(primary.id, expanded, s.liquidGlass) {
        if (!s.liquidGlass || s.reduceEffects) return@LaunchedEffect
        sheen.snapTo(0f)
        sheen.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        sheen.snapTo(-1f)
    }

    // Короткая пилюля (режим звука) вздрагивает вбок, как остров на iPhone при переключении «Звонок/Бесшумно»
    val shake = remember { Animatable(0f) }
    val pillId = (primary as? Act.NoticeA)?.n?.takeIf { it.style == NoticeStyle.PILL }?.id
    LaunchedEffect(pillId) {
        if (pillId == null || s.reduceEffects) return@LaunchedEffect
        for (x in floatArrayOf(-8f, 7f, -5f, 3.5f, -2f, 0f)) shake.animateTo(x, tween(60))
    }

    val current by rememberUpdatedState(primary)
    val isExpanded by rememberUpdatedState(expanded)
    val settings by rememberUpdatedState(s)
    val egg by IslandBus.egg.collectAsState()

    Box(
        Modifier
            .onGloballyPositioned { onBounds(it.boundsInWindow()) }
            .graphicsLayer {
                // Пасхалка: остров подпрыгивает в такт песни; оплата прошла — на миг подрастает
                val bump = Egg.bump() * PayFx.pop.floatValue
                scaleX = jellyX.value * (1f + 0.03f * pull.value) * bump
                scaleY = jellyY.value * (1f + 0.08f * pull.value) * bump
                translationX = shake.value.dp.toPx()
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
            .then(
                when {
                    s.liquidGlass && !calibrating && !s.reduceEffects ->
                        Modifier.shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.6f))
                    s.shadow && !s.reduceEffects -> Modifier.shadow(10.dp, shape)
                    else -> Modifier
                },
            )
            .clip(shape)
            .then(
                if (s.liquidGlass && !calibrating) {
                    Modifier.glassSurface(shape, Color(s.color), s.glassOpacity / 100f, tilt = { tilt.value }) { sheen.value }
                } else {
                    Modifier.background(if (calibrating) CalibrationRed else Color(s.color))
                },
            )
            .then(
                when {
                    calibrating -> Modifier.border(2.dp, Color.White, shape)
                    s.outline -> Modifier.border(s.outlineWidth.dp, outlineColor, shape)
                    else -> Modifier
                },
            )
            .then(
                if (egg && !calibrating) {
                    Modifier.drawWithContent {
                        drawContent()
                        eggRim(corner.toPx())
                    }
                } else {
                    Modifier
                },
            )
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
                            // Смахнуть уведомление вверх, как баннер на iPhone. Порог полный, чтобы палец,
                            // чуть съехавший при нажатии «Ответить», не убирал плашку
                            !isExpanded && vertical && dy < -threshold && act is Act.NoticeA -> {
                                IslandBus.notice.value = null
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
                // Как в ролике: старое тает за 1/6 с, уменьшаясь и размываясь, новое проявляется чуть позже
                // из 90% размера и из размытия. Скорость — от «Длительности» (по умолчанию как в ролике)
                val m = motionScale(s)
                val origin = TransformOrigin(0.5f, 0.3f)
                val inSpec = tween<Float>((300 * m).toInt(), delayMillis = (100 * m).toInt(), easing = ExpoOut)
                val outSpec = tween<Float>((167 * m).toInt(), easing = LinearEasing)
                val enter = if (s.reduceEffects) {
                    fadeIn(tween(150))
                } else {
                    fadeIn(inSpec) + scaleIn(inSpec, initialScale = 0.9f, transformOrigin = origin)
                }
                val exit = if (s.reduceEffects) {
                    fadeOut(tween(90))
                } else {
                    fadeOut(outSpec) + scaleOut(outSpec, targetScale = 0.95f, transformOrigin = origin)
                }
                (enter togetherWith exit).using(SizeTransform(clip = true) { _, _ -> islandSpec(s) })
            },
            label = "island",
        ) { f ->
            // Содержимое «перетекает» с размытием, как в ролике и в острове iPhone (Android 12+):
            // новое — из 7 dp, уходящее — в 5 dp
            val m = motionScale(s)
            val blur by transition.animateFloat(
                transitionSpec = {
                    if (targetState == EnterExitState.Visible) tween((300 * m).toInt(), delayMillis = (100 * m).toInt(), easing = ExpoOut)
                    else tween((167 * m).toInt())
                },
                label = "blur",
            ) { state ->
                when {
                    s.reduceEffects -> 0f
                    state == EnterExitState.PreEnter -> 7f
                    state == EnterExitState.PostExit -> 5f
                    else -> 0f
                }
            }
            Box(
                Modifier.graphicsLayer {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blur > 0.3f) {
                        renderEffect = BlurEffect(blur.dp.toPx(), blur.dp.toPx(), TileMode.Decal)
                    }
                },
            ) {
                if (f.expanded) {
                    ExpandedContent(f.act, s, lyrics, battery, expandedWidth)
                } else {
                    CompactContent(f.act, s, expandedWidth)
                }
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
        is Act.NoticeA -> {
            // Пока вводят ответ, нажатие по карточке не должно открывать приложение
            if (IslandBus.reply.value != null) return
            // Нажатие на уведомление приложения открывает его, как на iPhone
            act.n.message?.let { m ->
                if (!Controls.send(context, m.intent)) Controls.openApp(context, m.pkg)
            }
            act.n.openPkg?.let { Controls.openApp(context, it) }
            act.n.alarm?.let { openAlarm(context, it) }
            IslandBus.notice.value = null
        }
        is Act.Idle -> Controls.expand(act.id)
        is Act.TimerA -> if (act.t.ringing) Controls.cancelTimer() else perform(context, s.tapAction, act)
        is Act.CallA -> if (act.c.incoming) {
            Controls.openCall(context, act.c)
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
    val h = activeHeight(s)
    val haptic = rememberHaptic()
    val tilt = IslandBus.glassTilt.collectAsState()
    val now = rememberNow(500, active = act is Act.TimerA)
    Box(
        Modifier
            .size(h)
            .then(if (s.shadow && !s.reduceEffects) Modifier.shadow(8.dp, CircleShape) else Modifier)
            .clip(CircleShape)
            .then(
                if (s.liquidGlass) Modifier.glassSurface(CircleShape, Color(s.color), s.glassOpacity / 100f, tilt = { tilt.value })
                else Modifier.background(Color(s.color)),
            )
            .then(if (s.outline) Modifier.border(s.outlineWidth.dp, Color(s.outlineColor), CircleShape) else Modifier)
            .clickable {
                haptic()
                Controls.expand(act.id)
            },
        contentAlignment = Alignment.Center,
    ) {
        val inner: Dp = h * 0.62f
        when (act) {
            is Act.MediaA -> Art(act.m.art, h * 0.72f, h / 2)
            is Act.TimerA -> {
                ProgressRing(
                    progress = if (act.t.totalMs > 0) act.t.left(now).toFloat() / act.t.totalMs else 0f,
                    color = Tints.Orange,
                    modifier = Modifier.size(inner),
                    stroke = 2.5.dp,
                )
                Icon(Ios.timer, null, tint = Tints.Orange, modifier = Modifier.size(inner * 0.5f))
            }
            is Act.StopwatchA -> Icon(Ios.stopwatch, null, tint = Tints.Orange, modifier = Modifier.size(inner * 0.8f))
            is Act.TorchA -> Icon(Ios.flashlight, null, tint = Tints.Yellow, modifier = Modifier.size(inner * 0.75f))
            is Act.ChronoA -> AppIcon(act.c.icon, inner, Ios.timer, Tints.Orange)
            is Act.ProgressA -> {
                ProgressRing(act.p.fraction, Tints.Blue, Modifier.size(inner), stroke = 2.5.dp)
                Icon(Ios.download, null, tint = Tints.Blue, modifier = Modifier.size(inner * 0.5f))
            }
            is Act.CallA -> Icon(Ios.call, null, tint = Tints.Green, modifier = Modifier.size(inner * 0.7f))
            is Act.LiveA -> LiveGlyph(act.l, inner, liveTint(act.l.kind))
            is Act.ChargingA -> Icon(Ios.flash, null, tint = Tints.Green, modifier = Modifier.size(inner * 0.7f))
            else -> Unit
        }
    }
}
