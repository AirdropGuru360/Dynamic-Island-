package com.akira.island.ui.screens

import android.Manifest
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.akira.island.R
import com.akira.island.core.AlarmInfo
import com.akira.island.core.AppMessage
import com.akira.island.core.Controls
import com.akira.island.core.IslandBus
import com.akira.island.core.MediaInfo
import com.akira.island.core.MsgAction
import com.akira.island.core.Notice
import com.akira.island.core.NoticeStyle
import com.akira.island.core.PayReady
import com.akira.island.core.Tints
import com.akira.island.data.SettingsRepo
import com.akira.island.island.Act
import com.akira.island.island.CompactContent
import com.akira.island.island.Ios
import com.akira.island.island.activeHeight
import com.akira.island.island.glassSurface
import com.akira.island.island.islandGlow
import com.akira.island.island.rememberHaptic
import com.akira.island.service.NotificationParser
import com.akira.island.ui.Group
import com.akira.island.ui.GroupItem
import com.akira.island.ui.ItemIcon
import com.akira.island.ui.Page
import com.akira.island.ui.Perms
import com.akira.island.ui.Pos
import com.akira.island.ui.Screen
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.Tile
import com.akira.island.ui.ToggleCard
import com.akira.island.ui.Ui
import com.akira.island.ui.UpdateSection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Какие разрешения есть. Проверяются заново каждый раз, когда возвращаемся из системных настроек. */
private class PermState(val accessibility: Boolean, val notifications: Boolean, val calls: Boolean, val bluetooth: Boolean) {
    /** Чего не хватает — для подписи на главной */
    val missing: List<String>
        get() = listOfNotNull(
            "спец. возможности".takeIf { !accessibility },
            "уведомления".takeIf { !notifications },
            "звонки".takeIf { !calls },
            "Bluetooth".takeIf { !bluetooth },
        )
}

/** Разрешения и функция «проверить ещё раз» — после ответа на системный запрос. */
@Composable
private fun rememberPermState(): Pair<PermState, () -> Unit> {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    val state = remember(tick) {
        PermState(Perms.accessibility(context), Perms.notifications(context), Perms.calls(context), Perms.bluetooth(context))
    }
    return state to { tick++ }
}

/**
 * «Главная»: живой пример, включён ли остров, разрешения и проверка. Всё, что настраивается, —
 * во вкладках «Функции» и «Оформление», версия и помощь — в «О приложении».
 */
@Composable
fun HomeScreen(open: (Screen) -> Unit) {
    val context = LocalContext.current
    val s by SettingsRepo.state.collectAsState()
    val running by IslandBus.serviceRunning.collectAsState()
    val (perms, _) = rememberPermState()
    val missing = perms.missing

    Page("Динамический остров") {
        IslandPreview()
        UpdateSection()

        // Без службы спец. возможностей острова нет вовсе — это первый шаг
        if (!perms.accessibility) {
            StartCard(onOpen = { Perms.openAccessibility(context) }, onHelp = { open(Screen.INSTRUCTION) })
        }

        ToggleCard(
            Icons.Rounded.PowerSettingsNew,
            "Остров включён",
            when {
                !perms.accessibility -> "Сначала включите службу в спец. возможностях"
                running -> "Капсула показывается над камерой"
                else -> "Служба запускается…"
            },
            s.enabled, color = Tile.Green,
        ) { v -> SettingsRepo.update { copy(enabled = v) } }

        Group {
            GroupItem(
                Pos.First, Icons.Rounded.VerifiedUser, "Разрешения",
                if (missing.isEmpty()) "Всё включено" else "Нужно включить: " + missing.joinToString(", "),
                color = if (missing.isEmpty()) Tile.Green else Tile.Orange,
                onClick = { open(Screen.PERMISSIONS) },
            ) { if (missing.isEmpty()) Arrow() else CountBadge(missing.size) }
            GroupItem(
                Pos.Last, Icons.Rounded.Science, "Проверка", "Событие, сообщение, звонок, оплата — прямо в острове",
                color = Tile.Purple, onClick = { open(Screen.TESTS) },
            ) { Arrow() }
        }
    }
}

/** Первый шаг на главной: пока служба спец. возможностей выключена. */
@Composable
private fun StartCard(onOpen: () -> Unit, onHelp: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Ui.Primary.copy(alpha = 0.14f))
            .border(1.dp, Ui.Primary.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
            .padding(20.dp),
    ) {
        Text("Включите остров", color = Ui.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Капсулу рядом с камерой может рисовать только служба спец. возможностей. " +
                "Откройте «Спец. возможности» → «Динамический остров» и включите её.",
            color = Ui.Sub, fontSize = 14.sp, lineHeight = 20.sp,
        )
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.fillMaxWidth().clip(CircleShape).background(Ui.Primary).clickable(onClick = onOpen).padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Открыть спец. возможности", color = Ui.OnPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        Box(
            Modifier.fillMaxWidth().padding(top = 4.dp).clip(CircleShape).clickable(onClick = onHelp).padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Переключатель серый? Инструкция", color = Ui.Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
    }
}

/** «Разрешения»: что острову нужно от системы и зачем. */
@Composable
fun PermissionsScreen(onBack: () -> Unit, open: (Screen) -> Unit) {
    val context = LocalContext.current
    val (perms, refresh) = rememberPermState()
    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    val callLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        refresh()
        // Если система больше не показывает запрос — открываем настройки приложения
        if (result.values.none { it } && !Perms.calls(context)) Perms.openAppDetails(context)
    }

    Page("Разрешения", onBack, subtitle = "Остров ничего не хранит и никуда не отправляет: разрешения нужны, только чтобы показывать события") {
        Group {
            GroupItem(
                Pos.First, Icons.Rounded.AccessibilityNew, "Спец. возможности",
                if (perms.accessibility) "Включено — остров рисуется поверх строки состояния" else "Нужно включить: без этого острова не будет",
                onClick = { Perms.openAccessibility(context) }, color = Tile.Blue,
            ) { Status(perms.accessibility) }
            GroupItem(
                Pos.Middle, Icons.Rounded.Notifications, "Доступ к уведомлениям",
                if (perms.notifications) "Включено — музыка, сообщения, прогресс и таймеры" else "Нужен для музыки, сообщений, прогресса и таймеров",
                onClick = { Perms.openNotifications(context) }, color = Tile.Red,
            ) { Status(perms.notifications) }
            GroupItem(
                Pos.Middle, Icons.Rounded.Call, "Звонки",
                if (perms.calls) "Входящий вызов, имя из контактов, ответ кнопкой" else "Чтобы остров показывал входящие звонки",
                onClick = { if (perms.calls) Perms.openAppDetails(context) else callLauncher.launch(Perms.CALL_PERMISSIONS) },
                color = Tile.Green,
            ) { Status(perms.calls) }
            GroupItem(
                Pos.Last, Icons.Rounded.Bluetooth, "Bluetooth",
                if (perms.bluetooth) "Имена наушников при подключении" else "Чтобы видеть подключение наушников",
                onClick = {
                    if (!perms.bluetooth && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) btLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    else Perms.openAppDetails(context)
                },
                color = Tile.Indigo,
            ) { Status(perms.bluetooth) }
        }

        SectionHeader("Если не включается")
        Group {
            GroupItem(Pos.First, Icons.Rounded.Lightbulb, "Инструкция", "Серый переключатель на Android 13+, батарея и автозапуск",
                onClick = { open(Screen.INSTRUCTION) }, color = Tile.Yellow) { Arrow() }
            GroupItem(Pos.Last, Icons.Rounded.Security, "О приложении в системе", "Ограниченные настройки, разрешения, батарея",
                onClick = { Perms.openAppDetails(context) }, color = Tile.Gray)
        }
    }
}

/** Пример для «Проверки»: значок, название, что увидите, и что делает нажатие. */
private class Demo(val icon: ImageVector, val title: String, val caption: String, val color: Color, val run: () -> Unit)

/** «Проверка»: все примеры в одном месте — нажатие показывает событие в настоящем острове. */
@Composable
fun TestsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = rememberHaptic()
    val demos = remember { demos(context) }
    Page("Проверка", onBack, subtitle = "Нажмите — пример появится в острове наверху экрана") {
        demos.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { d ->
                    DemoTile(d, Modifier.weight(1f).fillMaxHeight()) {
                        haptic()
                        d.run()
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DemoTile(d: Demo, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(Ui.Card).clickable(onClick = onClick).padding(16.dp)) {
        ItemIcon(d.icon, d.color, 40.dp)
        Spacer(Modifier.height(14.dp))
        Text(d.title, color = Ui.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
        Spacer(Modifier.height(2.dp))
        Text(d.caption, color = Ui.Sub, fontSize = 13.sp, lineHeight = 17.sp)
    }
}

private fun demos(context: Context): List<Demo> = listOf(
    Demo(Icons.Rounded.Bolt, "Событие", "Зарядка 75% во всю ширину", Tile.Green) {
        IslandBus.post(Notice("Зарядка", "75%", Ios.load(context, R.drawable.ios_flash), Tints.Green, battery = 75))
    },
    Demo(Icons.Rounded.NotificationsOff, "Режим звука", "Капсула вздрагивает, колокольчик качается", Tile.Red) {
        IslandBus.post(Notice("", "Без звука", Ios.load(context, R.drawable.ios_notifications_off), Tints.Red, style = NoticeStyle.PILL))
    },
    Demo(Icons.Rounded.ChatBubble, "Сообщение", "С кнопками «Ответить» и «Пометить прочитанным»", Tile.Blue) {
        // Пробные кнопки: «Ответить» с полем ввода, как у Telegram и WhatsApp
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        val test = PendingIntent.getBroadcast(context, 0, Intent("com.akira.island.TEST").setPackage(context.packageName), flags)
        val input = RemoteInput.Builder("reply").setLabel("Ответить").build()
        IslandBus.post(
            Notice(
                "Анна", icon = Ios.load(context, R.drawable.ios_chatbubble_ellipses), tint = Tints.Blue,
                style = NoticeStyle.MESSAGE,
                extraMs = 4_500,
                message = AppMessage("test", context.packageName, "Остров", "Привет! Так выглядит уведомление в острове",
                    null, NotificationParser.icon(context, context.packageName), null,
                    actions = listOf(
                        MsgAction("Ответить", test, listOf(input)),
                        MsgAction("Пометить прочитанным", test),
                    ),
                    history = listOf("Как дела?", "Есть минутка?", "Привет! Так выглядит уведомление в острове"),
                ),
            ),
        )
    },
    Demo(Icons.Rounded.Call, "Входящий звонок", "Можно ответить и сбросить", Tile.Green) {
        Controls.showTestCall()
    },
    Demo(Icons.Rounded.HourglassBottom, "Таймер", "Обратный отсчёт одной минуты", Tile.Orange) {
        Controls.startTimer(1)
    },
    Demo(Icons.Rounded.Alarm, "Будильник", "Время подъёма и сколько осталось спать", Tile.Indigo) {
        IslandBus.post(
            Notice(
                title = "Будильник",
                icon = Ios.load(context, R.drawable.ios_alarm),
                tint = Tints.Orange,
                style = NoticeStyle.ALARM,
                alarm = AlarmInfo(System.currentTimeMillis() + (8 * 60 + 15) * 60_000L, null, null),
                extraMs = 2_000,
            ),
        )
    },
    Demo(Icons.Rounded.Nfc, "Оплата", "«К терминалу», затем «Оплачено» с суммой", Tile.Teal) {
        // Как настоящая оплата: подсказка держится, пока ждём терминал, потом — «Оплачено»
        val pkg = context.packageName
        IslandBus.payReady.value = PayReady(pkg, NotificationParser.icon(context, pkg))
        IslandBus.scope.launch {
            delay(3000)
            if (IslandBus.payReady.value?.pkg == pkg) IslandBus.payReady.value = null
            IslandBus.post(
                Notice(
                    "Оплачено", "1 250 ₽", Ios.load(context, R.drawable.ios_checkmark_circle), Tints.Green, caption = "Остров",
                    style = NoticeStyle.PAY_DONE, extraMs = 900,
                ),
            )
        }
    },
    Demo(Icons.Rounded.Navigation, "Навигатор", "Стрелка поворота и метры до неё", Tile.Purple) {
        Controls.showTestLive(context)
    },
)

@Composable
private fun Status(ok: Boolean) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (ok) Tints.Green.copy(alpha = 0.18f) else Tints.Orange.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(if (ok) "Да" else "Нет", color = if (ok) Tints.Green else Tints.Orange, fontSize = 13.sp)
    }
}

/** Сколько разрешений не хватает — оранжевый кружок с числом. */
@Composable
private fun CountBadge(n: Int) {
    Box(Modifier.size(26.dp).clip(CircleShape).background(Tile.Orange), contentAlignment = Alignment.Center) {
        Text("$n", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Arrow() {
    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Ui.Sub)
}

/** Живой пример капсулы с текущими настройками: толщина, боковые части, цвет и контур. */
@Composable
fun IslandPreview(expanded: Boolean = false) {
    val s by SettingsRepo.state.collectAsState()
    val demo = remember {
        MediaInfo(
            key = "demo", pkg = "", title = "Демо", artist = "", album = null, art = null,
            accent = Tints.Lavender, durationMs = 0, positionMs = 0, positionTime = SystemClock.elapsedRealtime(),
            speed = 1f, playing = true, canSeek = false,
        )
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF3B3860), Color(0xFF1F1E27)))),
        contentAlignment = Alignment.TopCenter,
    ) {
        val act = if (expanded) Act.Idle else Act.MediaA(demo)
        val corner = (if (expanded) s.height.dp else activeHeight(s)) / 2
        val shape = RoundedCornerShape(corner)
        Box(
            Modifier
                .padding(top = 18.dp)
                // «Свечение обложки» видно и на примере
                .then(if (!expanded && s.fxGlow && !s.reduceEffects) Modifier.drawBehind { islandGlow(demo.accent, 1f, corner.toPx()) } else Modifier)
                .clip(shape)
                .then(
                    if (s.liquidGlass) Modifier.glassSurface(shape, Color(s.color), s.glassOpacity / 100f)
                    else Modifier.background(Color(s.color)),
                )
                .then(if (s.outline) Modifier.border(s.outlineWidth.dp, if (s.outlineFromArt) Tints.Lavender else Color(s.outlineColor), shape) else Modifier)
                .animateContentSize(),
        ) {
            CompactContent(act, s, 400.dp)
        }
    }
}
