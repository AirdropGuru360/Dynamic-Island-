package com.akira.island.ui.screens

import android.Manifest
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Science
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.akira.island.core.Controls
import com.akira.island.core.IslandBus
import com.akira.island.core.MediaInfo
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import com.akira.island.core.AppMessage
import com.akira.island.core.MsgAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.akira.island.core.Notice
import com.akira.island.core.NoticeStyle
import com.akira.island.service.NotificationParser
import com.akira.island.core.Tints
import com.akira.island.data.SettingsRepo
import com.akira.island.island.Act
import com.akira.island.R
import com.akira.island.island.CompactContent
import com.akira.island.island.Ios
import com.akira.island.island.activeHeight
import com.akira.island.island.glassSurface
import androidx.compose.animation.animateContentSize
import com.akira.island.ui.Group
import com.akira.island.ui.GroupItem
import com.akira.island.ui.Page
import com.akira.island.ui.Perms
import com.akira.island.ui.Pos
import com.akira.island.ui.Tile
import com.akira.island.ui.Screen
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.ToggleCard
import com.akira.island.ui.Ui
import com.akira.island.ui.UpdateSection

@Composable
fun HomeScreen(open: (Screen) -> Unit) {
    val context = LocalContext.current
    val s by SettingsRepo.state.collectAsState()
    val running by IslandBus.serviceRunning.collectAsState()

    // Права проверяем каждый раз, когда возвращаемся из системных настроек
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    val a11y = remember(tick) { Perms.accessibility(context) }
    val notif = remember(tick) { Perms.notifications(context) }
    val bt = remember(tick) { Perms.bluetooth(context) }
    val callsOk = remember(tick) { Perms.calls(context) }
    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val callLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        tick++
        // Если система больше не показывает запрос — открываем настройки приложения
        if (result.values.none { it } && !Perms.calls(context)) Perms.openAppDetails(context)
    }

    Page("Динамический остров") {
        IslandPreview()
        UpdateSection()

        ToggleCard(
            Icons.Rounded.PowerSettingsNew,
            "Остров включён",
            when {
                !a11y -> "Сначала включите службу в спец. возможностях"
                running -> "Капсула показывается над камерой"
                else -> "Служба запускается…"
            },
            s.enabled, color = Tile.Green,
        ) { v -> SettingsRepo.update { copy(enabled = v) } }

        SectionHeader("Разрешения")
        Group {
            GroupItem(
                Pos.First, Icons.Rounded.AccessibilityNew, "Спец. возможности",
                if (a11y) "Включено — остров рисуется поверх строки состояния" else "Нужно включить: без этого острова не будет",
                onClick = { Perms.openAccessibility(context) }, color = Tile.Blue,
            ) { Status(a11y) }
            GroupItem(
                Pos.Middle, Icons.Rounded.Notifications, "Доступ к уведомлениям",
                if (notif) "Включено — музыка, прогресс и таймеры" else "Нужен для музыки, прогресса и таймеров",
                onClick = { Perms.openNotifications(context) }, color = Tile.Red,
            ) { Status(notif) }
            GroupItem(
                Pos.Middle, Icons.Rounded.Call, "Звонки",
                if (callsOk) "Входящий вызов, имя из контактов, ответ кнопкой" else "Чтобы остров показывал входящие звонки",
                onClick = { if (callsOk) Perms.openAppDetails(context) else callLauncher.launch(Perms.CALL_PERMISSIONS) }, color = Tile.Green,
            ) { Status(callsOk) }
            GroupItem(
                Pos.Last, Icons.Rounded.Bluetooth, "Bluetooth",
                if (bt) "Имена наушников при подключении" else "Чтобы видеть подключение наушников",
                onClick = {
                    if (!bt && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) btLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    else Perms.openAppDetails(context)
                }, color = Tile.Indigo,
            ) { Status(bt) }
        }

        SectionHeader("Функции")
        Group {
            GroupItem(Pos.First, Icons.Rounded.Dashboard, "Динамические карточки", "Плеер, фонарик, прогресс, таймер, зарядка, навигация",
                onClick = { open(Screen.CARDS) }, color = Tile.Purple) { Arrow() }
            GroupItem(Pos.Middle, Icons.Rounded.NotificationsActive, "События", "Зарядка, Wi-Fi, наушники, режим полёта, USB…",
                onClick = { open(Screen.EVENTS) }, color = Tile.Orange) { Arrow() }
            GroupItem(Pos.Middle, Icons.Rounded.Gesture, "Жесты", "Нажатия и свайпы по капсуле",
                onClick = { open(Screen.GESTURES) }, color = Tile.Teal) { Arrow() }
            GroupItem(Pos.Last, Icons.Rounded.LibraryMusic, "Текст песен", "Синхронный текст в раскрытом плеере",
                onClick = { open(Screen.LYRICS) }, color = Tile.Pink) { Arrow() }
        }

        SectionHeader("Проверка")
        Group {
            GroupItem(Pos.First, Icons.Rounded.Science, "Показать уведомление", "Тестовое событие в капсуле", color = Tile.Gray, onClick = {
                IslandBus.post(Notice("Зарядка", "75%", Ios.load(context, R.drawable.ios_flash), Tints.Green, battery = 75))
            })
            GroupItem(Pos.Middle, Icons.Rounded.ChatBubble, "Сообщение", "Карточка с кнопками «Ответить» и «Пометить прочитанным»", color = Tile.Green, onClick = {
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
            })
            GroupItem(Pos.Middle, Icons.Rounded.NotificationsOff, "Режим звука", "Капсула вздрагивает, колокольчик качается", color = Tile.Red, onClick = {
                IslandBus.post(Notice("", "Без звука", Ios.load(context, R.drawable.ios_notifications_off), Tints.Red, style = NoticeStyle.PILL))
            })
            GroupItem(Pos.Middle, Icons.Rounded.HourglassBottom, "Таймер на 1 минуту", "Капсула покажет обратный отсчёт", color = Tile.Orange, onClick = {
                Controls.startTimer(1)
            })
            GroupItem(Pos.Middle, Icons.Rounded.Call, "Входящий звонок", "Пробный вызов: можно ответить и сбросить", color = Tile.Green, onClick = {
                Controls.showTestCall()
            })
            GroupItem(Pos.Middle, Icons.Rounded.Nfc, "Оплата картой", "Приложите к терминалу, затем «Оплачено» с галочкой", color = Tile.Blue, onClick = {
                IslandBus.post(
                    Notice(
                        "Оплата", icon = Ios.load(context, R.drawable.ios_wifi), tint = Tints.Blue, caption = "К терминалу",
                        style = NoticeStyle.PAY_READY, appIcon = NotificationParser.icon(context, context.packageName), extraMs = 300,
                    ),
                )
                IslandBus.scope.launch {
                    delay(2400)
                    IslandBus.postNow(
                        Notice(
                            "Оплачено", "1 250 ₽", Ios.load(context, R.drawable.ios_checkmark_circle), Tints.Green, caption = "Остров",
                            style = NoticeStyle.PAY_DONE, extraMs = 900,
                        ),
                    )
                }
            })
            GroupItem(Pos.Last, Icons.Rounded.Navigation, "Навигатор", "Пробная «живая активность» в капсуле", color = Tile.Teal, onClick = {
                Controls.showTestLive(context)
            })
        }
    }
}

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
        val shape = RoundedCornerShape((if (expanded) s.height.dp else activeHeight(s)) / 2)
        Box(
            Modifier
                .padding(top = 18.dp)
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
