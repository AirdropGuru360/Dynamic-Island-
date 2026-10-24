package com.akira.island.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.LineWeight
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WaterDrop
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
import com.akira.island.core.IslandBus
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
import com.akira.island.ui.Tile
import com.akira.island.ui.Ui

/**
 * «Настройки Капсулы» — как раньше: живой пример, «Положение» и разделы с заголовками. Длинные
 * наборы ползунков вынесены в свои категории («Размер с активностью», «Анимации»), переключатели
 * остались на месте.
 */
@Composable
fun CapsuleScreen(open: (Screen) -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    Page("Настройки Капсулы") {
        IslandPreview()
        GroupItem(Pos.Single, Icons.Rounded.OpenWith, "Положение", "Сдвинуть капсулу по горизонтали и вертикали",
            color = Tile.Indigo, onClick = { open(Screen.SIZE) }) { Arrow() }

        SectionHeader("Вид острова")
        Group {
            GroupItem(Pos.First, Icons.Rounded.SwapHoriz, "Размер с активностью",
                if (s.activeWidth == 0) "Толщина +${s.activeExtra} dp, ширина как без неё" else "Толщина +${s.activeExtra}, ширина +${s.activeWidth} dp",
                color = Tile.Purple, onClick = { open(Screen.LOOK) }) { Arrow() }
            GroupToggle(
                if (s.liquidGlass) Pos.Middle else Pos.Last, Icons.Rounded.WaterDrop, "Liquid Glass",
                "Стекло с бликами и сияющей кромкой, как в iOS. Блики двигаются при наклоне",
                s.liquidGlass, color = Tile.Teal, badge = "Экспериментальный",
            ) { v -> SettingsRepo.update { copy(liquidGlass = v) } }
            if (s.liquidGlass) {
                GroupSlider(
                    Pos.Last, Icons.Rounded.Opacity, "Плотность стекла",
                    value = s.glassOpacity.toFloat(), range = 40f..95f, label = "${s.glassOpacity}%", color = Tile.Teal,
                ) { v -> SettingsRepo.update { copy(glassOpacity = v.toInt()) } }
            }
        }

        SectionHeader("Цвета")
        GroupItem(Pos.Single, Icons.Rounded.FormatColorFill, "Цвет капсулы", "Цвет, контур, тень и цвет кнопок в уведомлениях",
            color = Tile.Orange, onClick = { open(Screen.COLORS) }) { Arrow() }

        SectionHeader("Анимации")
        GroupItem(Pos.Single, Icons.Rounded.Animation, "Раскрытие острова",
            "${s.expandStyle.title} · ${s.durationPercent}% · ${s.elasticity.title.lowercase()}",
            color = Tile.Blue, onClick = { open(Screen.ANIM) }) { Arrow() }

        SectionHeader("Виброотклик")
        GroupToggle(Pos.Single, Icons.Rounded.Vibration, "Виброотклик", "Виброотклик при открытии, закрытии и нажатиях на капсулу",
            s.haptics, color = Tile.Pink) { v -> SettingsRepo.update { copy(haptics = v) } }

        SectionHeader("Другое")
        Group {
            GroupToggle(Pos.First, Icons.Rounded.VisibilityOff, "Скрывать пустую капсулу", "Показывать капсулу, только когда в ней что-то есть",
                s.hideIdle, color = Tile.Gray) { v -> SettingsRepo.update { copy(hideIdle = v) } }
            GroupToggle(Pos.Last, Icons.Rounded.ScreenRotation, "Скрывать в альбомной ориентации", "Не мешает играм и видео",
                s.hideInLandscape, color = Tile.Gray) { v -> SettingsRepo.update { copy(hideInLandscape = v) } }
        }
    }
}

/** «Размер с активностью»: насколько капсула с навигатором, фонариком или музыкой толще и шире спокойной. */
@Composable
fun LookScreen(onBack: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    Page("Размер с активностью", onBack) {
        IslandPreview()
        Group {
            GroupSlider(
                Pos.First, Icons.Rounded.LineWeight, "Толщина",
                value = s.activeExtra.toFloat(), range = 0f..14f, label = "+${s.activeExtra} dp", steps = 13, color = Tile.Purple,
            ) { v -> SettingsRepo.update { copy(activeExtra = v.toInt()) } }
            GroupSlider(
                Pos.Last, Icons.Rounded.SwapHoriz, "Ширина",
                value = s.activeWidth.toFloat(), range = 0f..120f, steps = 23,
                label = if (s.activeWidth == 0) "Как без неё" else "+${s.activeWidth} dp", color = Tile.Blue,
            ) { v ->
                // Пока двигают ползунок, на острове пример активности — сразу видно, не налезает ли он на значки
                IslandBus.widthPreviewUntil.value = android.os.SystemClock.elapsedRealtime() + 2500
                SettingsRepo.update { copy(activeWidth = (v / 5).toInt() * 5) }
            }
        }
    }
}

/** «Анимации»: как остров раскрывается и насколько пружинит. */
@Composable
fun AnimScreen(onBack: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    var styleDialog by remember { mutableStateOf(false) }
    var elasticDialog by remember { mutableStateOf(false) }

    Page("Анимации", onBack, action = {
        Text("Сброс", color = Ui.Sub, fontSize = 16.sp, modifier = Modifier.clickable { SettingsRepo.resetAnimations() })
    }) {
        Group {
            GroupItem(Pos.First, Icons.Rounded.Animation, "Стиль раскрытия", s.expandStyle.title, color = Tile.Purple,
                onClick = { styleDialog = true })
            GroupSlider(
                Pos.Middle, Icons.Rounded.Speed, "Длительность",
                value = s.durationPercent.toFloat(), range = 0f..100f, label = "${s.durationPercent}%", color = Tile.Indigo,
            ) { v -> SettingsRepo.update { copy(durationPercent = v.toInt()) } }
            GroupItem(Pos.Middle, Icons.Rounded.Waves, "Упругость", s.elasticity.title, color = Tile.Blue,
                onClick = { elasticDialog = true })
            // Сколько видно событие; плашки, которым нужно больше времени (сообщение с кнопками, оплата), — от него же
            GroupSlider(
                Pos.Middle, Icons.Rounded.Schedule, "Показ событий",
                value = s.noticeDurationMs / 1000f, range = 1.5f..6f, label = "%.1f с".format(s.noticeDurationMs / 1000f), color = Tile.Orange,
            ) { v -> SettingsRepo.update { copy(noticeDurationMs = (v * 1000).toInt()) } }
            GroupToggle(Pos.Last, Icons.Rounded.BlurOn, "Уменьшить эффекты", "Может быть полезно для не самых производительных устройств",
                s.reduceEffects, color = Tile.Gray) { v -> SettingsRepo.update { copy(reduceEffects = v) } }
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
