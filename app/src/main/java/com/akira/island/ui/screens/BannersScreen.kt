package com.akira.island.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.tr
import com.akira.island.service.SystemBanners
import com.akira.island.ui.Group
import com.akira.island.ui.GroupItem
import com.akira.island.ui.Page
import com.akira.island.ui.Pos
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.Tile
import com.akira.island.ui.Ui
import com.akira.island.ui.posOf

/**
 * Системные плашки, которые дублируют остров. Убрать чужую всплывающую плашку приложение не может,
 * поэтому здесь — быстрый переход к нужной настройке уведомлений: уведомление остаётся в шторке,
 * а на экране событие показывает только остров. Уведомления мессенджеров сюда не входят.
 */
@Composable
fun BannersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val seen by SystemBanners.seen.collectAsState()
    val calls = remember(seen) { SystemBanners.callApps(context, seen) }
    val clocks = remember(seen) { SystemBanners.clockApps(context, seen) }

    Page(tr("Системные плашки", "System banners"), onBack) {
        Box(
            Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(24.dp)).background(Ui.Card)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                tr(
                    "Android показывает свои всплывающие плашки поверх острова, и запретить их стороннему приложению система не даёт. " +
                        "Нажмите на приложение и выключите «Всплывающие уведомления» (на некоторых телефонах — «Показывать поверх других» " +
                        "или «Баннеры»). Уведомление останется в шторке, а на экране событие покажет только остров.",
                    "Android shows its own pop-up banners on top of the island, and the system doesn't let a third-party app " +
                        "block them. Tap an app and turn off “Pop on screen” (on some phones — “Show on top” or “Banners”). The " +
                        "notification stays in the shade, and only the island shows the event on screen.",
                ),
                color = Ui.Sub, fontSize = 15.sp, lineHeight = 20.sp,
            )
        }

        SectionHeader(tr("Звонки", "Calls"))
        AppList(calls, Icons.Rounded.Call, Tile.Green,
            tr("Канал входящих вызовов. Экран при звонке включит сам остров", "The incoming calls channel. The island turns the screen on for calls itself"),
            tr("Звонилки не нашлось — позвоните себе, и она появится здесь",
                "No dialer found — call yourself and it will show up here")) { SystemBanners.openSettings(context, it) }

        SectionHeader(tr("Таймеры и будильники", "Timers and alarms"))
        AppList(clocks, Icons.Rounded.Alarm, Tile.Orange,
            tr("Сигнал таймера и будильника", "Timer and alarm alerts"),
            tr("Приложение «Часы» не нашлось", "No Clock app found")) { SystemBanners.openSettings(context, it) }
        Text(
            tr(
                "Совет: не выключайте уведомления «Часов» целиком — выберите «Без звука» и «Свернуть» (или выключите только " +
                    "«Всплывающие»). Тогда таймер и секундомер появятся в острове мгновенно, а его кнопки будут работать, " +
                    "не открывая «Часы». Если уведомления выключены совсем, остров узнаёт таймер с экрана «Часов», " +
                    "а кнопки нажимает сам, на мгновение открывая приложение.",
                "Tip: don't turn off Clock's notifications completely — choose “Silent” and “Minimize” (or turn off only " +
                    "“Pop on screen”). Then the timer and stopwatch show up in the island instantly, and its buttons work " +
                    "without opening Clock. If the notifications are fully off, the island reads the timer from the Clock " +
                    "screen and presses the buttons itself, opening the app for a moment.",
            ),
            color = Ui.Sub, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
        )

        SectionHeader(tr("Батарея и зарядка", "Battery and charging"))
        Group {
            GroupItem(Pos.First, Icons.Rounded.BatteryAlert, SystemBanners.label(context, SystemBanners.SYSTEM_UI),
                tr("«Батарея разряжена» — канал «Батарея». У некоторых прошивок система менять его не даёт",
                    "“Battery low” — the Battery channel. Some firmwares don't let you change it"),
                color = Tile.Red, onClick = { SystemBanners.openSettings(context, SystemBanners.SYSTEM_UI) }) { Arrow() }
            GroupItem(Pos.Last, Icons.Rounded.Usb, SystemBanners.label(context, SystemBanners.ANDROID),
                tr("Зарядка через USB и другие сообщения системы", "USB charging and other system messages"),
                color = Tile.Gray, onClick = { SystemBanners.openSettings(context, SystemBanners.ANDROID) }) { Arrow() }
        }
        Text(
            tr(
                "Анимацию зарядки, которую рисует сама прошивка (Xiaomi, Samsung и др.), отключают в настройках экрана блокировки " +
                    "или батареи телефона, если производитель это позволяет.",
                "The charging animation drawn by the firmware itself (Xiaomi, Samsung and others) is turned off in the " +
                    "phone's lock screen or battery settings, if the manufacturer allows it.",
            ),
            color = Ui.Sub, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun AppList(
    packages: List<String>,
    icon: ImageVector,
    color: Color,
    hint: String,
    empty: String,
    onOpen: (String) -> Unit,
) {
    val context = LocalContext.current
    if (packages.isEmpty()) {
        GroupItem(Pos.Single, icon, empty, color = Tile.Gray)
        return
    }
    Group {
        packages.forEachIndexed { i, pkg ->
            GroupItem(posOf(i, packages.size), icon, SystemBanners.label(context, pkg), hint, color = color,
                onClick = { onOpen(pkg) }) { Arrow() }
        }
    }
}
