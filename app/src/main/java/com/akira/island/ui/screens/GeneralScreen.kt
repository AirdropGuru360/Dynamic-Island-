package com.akira.island.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Lightbulb
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
import com.akira.island.data.SettingsRepo
import com.akira.island.ui.Group
import com.akira.island.ui.GroupItem
import com.akira.island.ui.GroupSlider
import com.akira.island.ui.GroupToggle
import com.akira.island.ui.Page
import com.akira.island.ui.Perms
import com.akira.island.ui.Pos
import com.akira.island.ui.Screen
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.Ui

@Composable
fun GeneralScreen(open: (Screen) -> Unit) {
    val context = LocalContext.current
    val s by SettingsRepo.state.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }

    Page("Общие") {
        SectionHeader("Поведение")
        Group {
            GroupToggle(Pos.First, Icons.Rounded.Apps, "Прятать плеер в его приложении",
                "Карточка музыки не показывается, пока открыт сам плеер", s.hideWhenPlayerOpen) { v ->
                SettingsRepo.update { copy(hideWhenPlayerOpen = v) }
            }
            GroupSlider(
                Pos.Last, Icons.Rounded.Schedule, "Показ событий",
                value = s.noticeDurationMs / 1000f, range = 1.5f..6f, label = "%.1f с".format(s.noticeDurationMs / 1000f),
            ) { v -> SettingsRepo.update { copy(noticeDurationMs = (v * 1000).toInt()) } }
        }

        SectionHeader("Помощь")
        Group {
            GroupItem(Pos.First, Icons.Rounded.Lightbulb, "Инструкция", "Как включить остров на Android 13+",
                onClick = { open(Screen.INSTRUCTION) }) { Arrow() }
            GroupItem(Pos.Middle, Icons.Rounded.Security, "О приложении в системе", "Разрешения, ограниченные настройки, батарея",
                onClick = { Perms.openAppDetails(context) })
            GroupItem(Pos.Last, Icons.Rounded.Info, "О приложении", "Версия и откуда идея",
                onClick = { open(Screen.ABOUT) }) { Arrow() }
        }

        SectionHeader("Сброс")
        GroupItem(Pos.Single, Icons.Rounded.Restore, "Сбросить все настройки", "Вернуть всё как было после установки",
            onClick = { confirmReset = true })
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
