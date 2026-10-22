package com.akira.island.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.LineWeight
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.akira.island.data.Elasticity
import com.akira.island.data.ExpandStyle
import com.akira.island.data.SettingsRepo
import com.akira.island.ui.ChoiceDialog
import com.akira.island.ui.Group
import com.akira.island.ui.GroupItem
import com.akira.island.ui.GroupSlider
import com.akira.island.ui.GroupToggle
import com.akira.island.ui.Page
import com.akira.island.ui.Pos
import com.akira.island.ui.Screen
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.Ui

/** «Настройки Капсулы» — как на втором скриншоте. */
@Composable
fun CapsuleScreen(open: (Screen) -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    var styleDialog by remember { mutableStateOf(false) }
    var elasticDialog by remember { mutableStateOf(false) }

    Page("Настройки Капсулы") {
        IslandPreview()
        GroupItem(Pos.Single, Icons.Rounded.OpenWith, "Размер и положение", "Изменить размер и положение капсулы",
            onClick = { open(Screen.SIZE) }) { Arrow() }

        SectionHeader("Вид острова")
        Group {
            GroupSlider(
                Pos.Single, Icons.Rounded.LineWeight, "Толщина с активностью",
                value = s.activeExtra.toFloat(), range = 0f..14f, label = "+${s.activeExtra} dp", steps = 13,
            ) { v -> SettingsRepo.update { copy(activeExtra = v.toInt()) } }
        }

        SectionHeader("Цвета")
        GroupItem(Pos.Single, Icons.Rounded.FormatColorFill, "Цвет капсулы", "Изменить цвет, настроить контур и тень",
            onClick = { open(Screen.COLORS) })

        SectionHeader("Анимации") {
            Text("Сброс", color = Ui.Sub.copy(alpha = 0.6f), fontSize = 16.sp,
                modifier = Modifier.clickable { SettingsRepo.resetAnimations() })
        }
        Group {
            GroupItem(Pos.First, Icons.Rounded.Animation, "Стиль раскрытия", s.expandStyle.title, onClick = { styleDialog = true })
            GroupSlider(
                Pos.Middle, Icons.Rounded.Speed, "Длительность анимации",
                value = s.durationPercent.toFloat(), range = 0f..100f, label = "${s.durationPercent}%",
            ) { v -> SettingsRepo.update { copy(durationPercent = v.toInt()) } }
            GroupItem(Pos.Middle, Icons.Rounded.Waves, "Упругость анимации", s.elasticity.title, onClick = { elasticDialog = true })
            GroupToggle(Pos.Last, Icons.Rounded.BlurOn, "Уменьшить эффекты", "Может быть полезно для не самых производительных устройств",
                s.reduceEffects) { v -> SettingsRepo.update { copy(reduceEffects = v) } }
        }

        SectionHeader("Виброотклик")
        GroupToggle(Pos.Single, Icons.Rounded.Vibration, "Виброотклик", "Виброотклик при открытии, закрытии и нажатиях на капсулу",
            s.haptics) { v -> SettingsRepo.update { copy(haptics = v) } }

        SectionHeader("Другое")
        Group {
            GroupToggle(Pos.First, Icons.Rounded.VisibilityOff, "Скрывать пустую капсулу", "Показывать капсулу, только когда в ней что-то есть",
                s.hideIdle) { v -> SettingsRepo.update { copy(hideIdle = v) } }
            GroupToggle(Pos.Last, Icons.Rounded.ScreenRotation, "Скрывать в альбомной ориентации", "Не мешает играм и видео",
                s.hideInLandscape) { v -> SettingsRepo.update { copy(hideInLandscape = v) } }
        }
    }

    if (styleDialog) {
        ChoiceDialog("Стиль раскрытия", ExpandStyle.entries, s.expandStyle, { it.title },
            { v -> SettingsRepo.update { copy(expandStyle = v) } }) { styleDialog = false }
    }
    if (elasticDialog) {
        ChoiceDialog("Упругость анимации", Elasticity.entries, s.elasticity, { it.title },
            { v -> SettingsRepo.update { copy(elasticity = v) } }) { elasticDialog = false }
    }
}
