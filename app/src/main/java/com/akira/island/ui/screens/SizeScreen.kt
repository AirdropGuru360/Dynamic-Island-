package com.akira.island.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.akira.island.core.Controls
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import com.akira.island.ui.Group
import com.akira.island.ui.GroupItem
import com.akira.island.ui.GroupSlider
import com.akira.island.ui.Page
import com.akira.island.ui.Pos
import com.akira.island.ui.SectionHeader

@Composable
fun SizeScreen(onBack: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    Page("Размер и положение", onBack, subtitle = "Капсула на экране меняется сразу — подгоните её под вырез камеры") {
        IslandPreview()
        SectionHeader("Размер")
        Group {
            GroupSlider(Pos.First, Icons.Rounded.Straighten, "Ширина", s.width.toFloat(), 50f..220f, "${s.width} dp") { v ->
                SettingsRepo.update { copy(width = v.toInt()) }
            }
            GroupSlider(Pos.Last, Icons.Rounded.Height, "Высота", s.height.toFloat(), 22f..56f, "${s.height} dp") { v ->
                SettingsRepo.update { copy(height = v.toInt()) }
            }
        }
        SectionHeader("Положение")
        Group {
            GroupSlider(Pos.First, Icons.Rounded.SwapHoriz, "По горизонтали", s.offsetX.toFloat(), -160f..160f, "${s.offsetX} dp") { v ->
                SettingsRepo.update { copy(offsetX = v.toInt()) }
            }
            GroupSlider(Pos.Last, Icons.Rounded.SwapVert, "Отступ сверху", s.offsetY.toFloat(), -10f..120f, "${s.offsetY} dp") { v ->
                SettingsRepo.update { copy(offsetY = v.toInt()) }
            }
        }
        Group {
            GroupItem(Pos.First, Icons.Rounded.Science, "Проверить", "Запустить таймер на 10 секунд", onClick = {
                Controls.startTimerSeconds(10)
            })
            GroupItem(Pos.Last, Icons.Rounded.Restore, "Сбросить размер и положение", null, onClick = {
                val d = IslandSettings()
                SettingsRepo.update { copy(width = d.width, height = d.height, offsetX = d.offsetX, offsetY = d.offsetY) }
            })
        }
    }
}
