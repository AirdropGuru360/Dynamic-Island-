package com.akira.island.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import android.content.Intent
import android.net.Uri
import com.akira.island.R
import com.akira.island.data.SettingsRepo
import com.akira.island.ui.Group
import com.akira.island.ui.GroupItem
import com.akira.island.ui.GroupSlider
import com.akira.island.ui.GroupToggle
import com.akira.island.ui.Page
import com.akira.island.ui.Perms
import com.akira.island.ui.Pos
import com.akira.island.ui.Tile
import com.akira.island.ui.Screen
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.Ui
import com.akira.island.ui.UpdateSection
import com.akira.island.ui.updateStatusText
import com.akira.island.update.UpdateChecker
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.runtime.rememberCoroutineScope
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
            GroupItem(Pos.Middle, Icons.Rounded.NotificationsOff, "Системные плашки",
                "Чтобы звонок, таймер и батарею показывал только остров, без плашек Android",
                onClick = { open(Screen.BANNERS) }, color = Tile.Red) { Arrow() }
            GroupSlider(
                Pos.Last, Icons.Rounded.Schedule, "Показ событий",
                value = s.noticeDurationMs / 1000f, range = 1.5f..6f, label = "%.1f с".format(s.noticeDurationMs / 1000f), color = Tile.Orange,
            ) { v -> SettingsRepo.update { copy(noticeDurationMs = (v * 1000).toInt()) } }
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
