package com.akira.island.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.FormatLineSpacing
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import com.akira.island.R
import com.akira.island.data.SettingsRepo
import com.akira.island.ui.ChoiceDialog
import com.akira.island.ui.Group
import com.akira.island.ui.GroupItem
import com.akira.island.ui.GroupSlider
import com.akira.island.ui.GroupToggle
import com.akira.island.ui.Page
import com.akira.island.ui.Perms
import com.akira.island.ui.Pos
import com.akira.island.ui.Screen
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.Tile
import com.akira.island.ui.Ui
import com.akira.island.ui.UpdateSection
import com.akira.island.ui.updateStatusText
import com.akira.island.update.UpdateChecker
import kotlinx.coroutines.launch

private val GithubPurple = Color(0xFF8250DF)

/** Главная страница проекта на GitHub — в браузере или приложении GitHub. */
private fun openGithub(context: android.content.Context) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${UpdateChecker.REPO}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Composable
fun GeneralScreen(open: (Screen) -> Unit) {
    val context = LocalContext.current
    val s by SettingsRepo.state.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }
    var collapseDialog by remember { mutableStateOf(false) }

    val update by UpdateChecker.state.collectAsState()
    val scope = rememberCoroutineScope()

    Page("Общие") {
        UpdateSection()
        SectionHeader("Поведение")
        Group {
            GroupToggle(Pos.First, Icons.Rounded.Apps, "Прятать плеер в его приложении",
                "Карточка музыки не показывается, пока открыт сам плеер", s.hideWhenPlayerOpen, color = Tile.Blue) { v ->
                SettingsRepo.update { copy(hideWhenPlayerOpen = v) }
            }
            GroupItem(Pos.Middle, Icons.Rounded.UnfoldLess, "Сворачивать раскрытый остров", collapseLabel(s.autoCollapseSec),
                color = Tile.Indigo, onClick = { collapseDialog = true })
            GroupToggle(Pos.Middle, Icons.Rounded.Circle, "Вторая активность рядом",
                "Когда активностей две, вторая — кружком рядом с капсулой, как на iPhone", s.bubble, color = Tile.Purple) { v ->
                SettingsRepo.update { copy(bubble = v) }
            }
            GroupToggle(Pos.Middle, Icons.Rounded.LightMode, "Включать экран",
                "Когда звонит таймер, будильник или входящий вызов — экран включается и не гаснет, пока звонит",
                s.wakeForAlerts, color = Tile.Yellow) { v -> SettingsRepo.update { copy(wakeForAlerts = v) } }
            GroupItem(Pos.Last, Icons.Rounded.NotificationsOff, "Системные плашки",
                "Чтобы звонок, таймер и батарею показывал только остров, без плашек Android",
                onClick = { open(Screen.BANNERS) }, color = Tile.Red) { Arrow() }
        }

        SectionHeader("Уведомления в острове")
        Group {
            GroupToggle(Pos.First, Icons.Rounded.Smartphone, "Из открытого приложения",
                "Показывать и уведомления приложения, которое сейчас на экране", s.msgInApp, color = Tile.Blue) { v ->
                SettingsRepo.update { copy(msgInApp = v) }
            }
            GroupSlider(
                Pos.Last, Icons.Rounded.FormatLineSpacing, "Строк текста",
                value = s.msgLines.toFloat(), range = 1f..6f, steps = 4, label = "${s.msgLines}", color = Tile.Green,
            ) { v -> SettingsRepo.update { copy(msgLines = Math.round(v)) } }
        }

        SectionHeader("Обновления")
        Group {
            GroupToggle(Pos.First, Icons.Rounded.SystemUpdate, "Проверять обновления",
                "Раз в 6 часов смотреть на GitHub, не вышла ли новая версия", s.autoUpdateCheck, color = Tile.Green) { v ->
                SettingsRepo.update { copy(autoUpdateCheck = v) }
            }
            GroupItem(Pos.Last, Icons.Rounded.Refresh, "Проверить сейчас",
                updateStatusText(update, UpdateChecker.currentVersion(context)),
                onClick = { scope.launch { UpdateChecker.check(context.applicationContext, force = true) } }, color = Tile.Blue)
        }

        SectionHeader("Помощь")
        Group {
            GroupItem(Pos.First, ImageVector.vectorResource(R.drawable.ic_github), "Страница GitHub",
                "Описание, новые версии и исходный код",
                onClick = { openGithub(context) }, color = GithubPurple) { Arrow() }
            GroupItem(Pos.Middle, Icons.Rounded.Lightbulb, "Инструкция", "Как включить остров на Android 13+",
                onClick = { open(Screen.INSTRUCTION) }, color = Tile.Yellow) { Arrow() }
            GroupItem(Pos.Middle, Icons.Rounded.Navigation, "Диагностика навигации",
                "Что присылает навигатор, такси или доставка — если не видно расстояния",
                onClick = { open(Screen.DIAG) }, color = Tile.Teal) { Arrow() }
            GroupItem(Pos.Middle, Icons.Rounded.Security, "О приложении в системе", "Разрешения, ограниченные настройки, батарея",
                onClick = { Perms.openAppDetails(context) }, color = Tile.Gray)
            GroupItem(Pos.Last, Icons.Rounded.Info, "О приложении", "Версия и откуда идея",
                onClick = { open(Screen.ABOUT) }, color = Tile.Indigo) { Arrow() }
        }

        SectionHeader("Сброс")
        GroupItem(Pos.Single, Icons.Rounded.Restore, "Сбросить все настройки", "Вернуть всё как было после установки",
            onClick = { confirmReset = true }, color = Tile.Red)
    }

    if (collapseDialog) {
        ChoiceDialog("Сворачивать раскрытый остров", listOf(0, 5, 10, 20, 30), s.autoCollapseSec, ::collapseLabel,
            { v -> SettingsRepo.update { copy(autoCollapseSec = v) } }) { collapseDialog = false }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = Ui.Card,
            title = { Text("Сбросить настройки?") },
            text = { Text("Размер, цвета, анимации, карточки и события вернутся к значениям по умолчанию.") },
            confirmButton = {
                TextButton(onClick = {
                    SettingsRepo.reset()
                    confirmReset = false
                }) { Text("Сбросить", color = Ui.Primary) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Отмена", color = Ui.Sub) } },
        )
    }
}

/** Подпись для «Сворачивать раскрытый остров». */
private fun collapseLabel(sec: Int) = if (sec <= 0) "Только касанием мимо или свайпом вверх" else "Через $sec секунд без касаний"
