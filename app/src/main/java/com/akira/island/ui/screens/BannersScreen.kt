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

    Page("Системные плашки", onBack) {
        Box(
            Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(24.dp)).background(Ui.Card)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                "Android показывает свои всплывающие плашки поверх острова, и запретить их стороннему приложению система не даёт. " +
                    "Нажмите на приложение и выключите «Всплывающие уведомления» (на некоторых телефонах — «Показывать поверх других» " +
                    "или «Баннеры»). Уведомление останется в шторке, а на экране событие покажет только остров.",
                color = Ui.Sub, fontSize = 15.sp, lineHeight = 20.sp,
            )
        }

        SectionHeader("Звонки")
        AppList(calls, Icons.Rounded.Call, Tile.Green,
            "Канал входящих вызовов. Экран при звонке включит сам остров",
            "Звонилки не нашлось — позвоните себе, и она появится здесь") { SystemBanners.openSettings(context, it) }

        SectionHeader("Таймеры и будильники")
        AppList(clocks, Icons.Rounded.Alarm, Tile.Orange,
            "Сигнал таймера и будильника",
            "Приложение «Часы» не нашлось") { SystemBanners.openSettings(context, it) }
        Text(
            "Совет: не выключайте уведомления «Часов» целиком — выберите «Без звука» и «Свернуть» (или выключите только " +
                "«Всплывающие»). Тогда таймер и секундомер появятся в острове мгновенно, а его кнопки будут работать, " +
                "не открывая «Часы». Если уведомления выключены совсем, остров узнаёт таймер с экрана «Часов», " +
                "а кнопки нажимает сам, на мгновение открывая приложение.",
            color = Ui.Sub, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
        )

        SectionHeader("Батарея и зарядка")
        Group {
            GroupItem(Pos.First, Icons.Rounded.BatteryAlert, SystemBanners.label(context, SystemBanners.SYSTEM_UI),
                "«Батарея разряжена» — канал «Батарея». У некоторых прошивок система менять его не даёт",
                color = Tile.Red, onClick = { SystemBanners.openSettings(context, SystemBanners.SYSTEM_UI) }) { Arrow() }
            GroupItem(Pos.Last, Icons.Rounded.Usb, SystemBanners.label(context, SystemBanners.ANDROID),
                "Зарядка через USB и другие сообщения системы",
                color = Tile.Gray, onClick = { SystemBanners.openSettings(context, SystemBanners.ANDROID) }) { Arrow() }
        }
        Text(
            "Анимацию зарядки, которую рисует сама прошивка (Xiaomi, Samsung и др.), отключают в настройках экрана блокировки " +
                "или батареи телефона, если производитель это позволяет.",
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
