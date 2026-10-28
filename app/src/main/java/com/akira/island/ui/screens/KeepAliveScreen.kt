package com.akira.island.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Security
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.akira.island.core.tr
import com.akira.island.data.SettingsRepo
import com.akira.island.service.KeepAliveService
import com.akira.island.ui.Group
import com.akira.island.ui.GroupItem
import com.akira.island.ui.GroupToggle
import com.akira.island.ui.Page
import com.akira.island.ui.Perms
import com.akira.island.ui.Perms.Vendor
import com.akira.island.ui.Pos
import com.akira.island.ui.Screen
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.Tile

/**
 * «Чтобы остров не закрывался»: Xiaomi (HyperOS), Oppo, realme, OnePlus (ColorOS), Samsung и другие сами
 * выгружают приложения в фоне — особенно если смахнуть их из недавних. Здесь кнопки прямо в нужные
 * настройки этой прошивки и подсказка, как закрепить остров в недавних.
 */
@Composable
fun KeepAliveScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vendor = Perms.vendor()
    val (battery, _) = rememberOnResume { Perms.batteryUnrestricted(context) }
    val s by SettingsRepo.state.collectAsState()

    Page(tr("Чтобы остров не закрывался", "Keep the island running"), onBack,
        subtitle = tr("Прошивка телефона может выгружать остров в фоне — тогда он пропадает, пока его не включить снова",
            "Your phone's firmware may stop the island in the background — then it disappears until you turn it on again")) {
        // Процесс со службой на переднем плане Android не закрывает, когда приложение смахивают из недавних
        GroupToggle(
            Pos.Single, Icons.Rounded.PushPin, tr("Не выгружать остров", "Keep the island loaded"),
            tr("Остров не пропадёт, даже если смахнуть приложение из недавних. Android может показать тихое уведомление",
                "The island stays even if you swipe the app away from recent apps. Android may show a quiet notification"),
            s.keepAlive, color = Tile.Purple,
        ) { v ->
            SettingsRepo.update { copy(keepAlive = v) }
            KeepAliveService.sync(context)
        }

        Group {
            GroupItem(
                if (Perms.hasAutostart()) Pos.First else Pos.Single, Icons.Rounded.BatteryChargingFull,
                tr("Батарея без ограничений", "Unrestricted battery"),
                batteryHint(vendor), onClick = { Perms.openBattery(context) }, color = Tile.Green,
            ) { Status(battery) }
            if (Perms.hasAutostart()) {
                GroupItem(
                    Pos.Last, Icons.Rounded.RocketLaunch, tr("Автозапуск", "Autostart"),
                    tr("Включите для «Динамического острова» — тогда он запустится сам после перезагрузки и закрытия",
                        "Turn it on for Dynamic Island — then it starts by itself after a reboot or being closed"),
                    onClick = { Perms.openAutostart(context) }, color = Tile.Orange,
                ) { Arrow() }
            }
        }

        SectionHeader(tr("Закрепите в недавних", "Lock it in recent apps"))
        InfoText(lockHint(vendor))

        SectionHeader(tr("Если остров всё равно пропал", "If the island still disappears"))
        Group {
            GroupItem(
                Pos.First, Icons.Rounded.Lock, tr("Спец. возможности", "Accessibility"),
                tr("Выключите и снова включите службу «Динамический остров»", "Turn the Dynamic Island service off and on again"),
                onClick = { Perms.openAccessibility(context) }, color = Tile.Blue,
            ) { Arrow() }
            AppDetailsItem(Pos.Last)
        }
    }
}

/** Пункт «Чтобы остров не закрывался» — в «Разрешениях» и «О приложении». */
@Composable
internal fun KeepAliveItem(pos: Pos, open: (Screen) -> Unit) {
    GroupItem(pos, Icons.Rounded.BatteryChargingFull, tr("Чтобы остров не закрывался", "Keep the island running"),
        tr("Батарея, автозапуск и закрепление в недавних", "Battery, autostart and locking in recent apps"),
        onClick = { open(Screen.KEEPALIVE) }, color = Tile.Green) { Arrow() }
}

/** «О приложении» в настройках Android: ограниченные настройки, разрешения, батарея. */
@Composable
internal fun AppDetailsItem(pos: Pos) {
    val context = LocalContext.current
    GroupItem(pos, Icons.Rounded.Security, tr("О приложении в системе", "App info in Android"),
        tr("Ограниченные настройки, разрешения, батарея", "Restricted settings, permissions, battery"),
        onClick = { Perms.openAppDetails(context) }, color = Tile.Gray)
}

private fun batteryHint(v: Vendor): String = when (v) {
    Vendor.XIAOMI -> tr("«Контроль активности» или «Экономия энергии» → «Нет ограничений»",
        "Battery saver or “Activity control” → “No restrictions”")
    Vendor.SAMSUNG -> tr("«Батарея» → «Без ограничений», и уберите остров из «Приложений в спящем режиме»",
        "Battery → Unrestricted, and remove the island from Sleeping apps")
    Vendor.OPPO -> tr("«Использование батареи» → разрешите работу в фоне", "Battery usage → allow background activity")
    else -> tr("Найдите «Динамический остров» и выберите «Не оптимизировать» или «Без ограничений»",
        "Find Dynamic Island and choose “Don't optimize” or “Unrestricted”")
}

private fun lockHint(v: Vendor): String = when (v) {
    Vendor.XIAOMI -> tr(
        "Откройте недавние приложения, зажмите карточку «Динамический остров» и нажмите на замок. " +
            "Закреплённое приложение HyperOS и MIUI не закрывают, даже когда вы чистите недавние.",
        "Open recent apps, long-press the Dynamic Island card and tap the lock. HyperOS and MIUI don't close a " +
            "locked app, even when you clear recent apps.",
    )
    Vendor.OPPO -> tr(
        "Откройте недавние приложения, нажмите «⋮» над карточкой «Динамический остров» (или потяните её вниз) " +
            "и выберите «Заблокировать».",
        "Open recent apps, tap “⋮” above the Dynamic Island card (or pull it down) and choose “Lock”.",
    )
    Vendor.SAMSUNG -> tr(
        "Откройте недавние приложения, нажмите на значок над карточкой «Динамический остров» и выберите «Не закрывать».",
        "Open recent apps, tap the icon above the Dynamic Island card and choose “Keep open”.",
    )
    else -> tr(
        "Откройте недавние приложения и потяните карточку «Динамический остров» вниз или зажмите её — " +
            "появится замок. Закреплённое приложение не закрывается, когда вы чистите недавние.",
        "Open recent apps and pull the Dynamic Island card down or long-press it — a lock appears. A locked app " +
            "isn't closed when you clear recent apps.",
    )
}
