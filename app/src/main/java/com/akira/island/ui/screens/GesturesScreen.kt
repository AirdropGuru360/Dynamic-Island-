package com.akira.island.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.akira.island.data.SettingsRepo
import com.akira.island.data.TapAction
import com.akira.island.ui.ChoiceDialog
import com.akira.island.ui.Group
import com.akira.island.ui.GroupItem
import com.akira.island.ui.GroupToggle
import com.akira.island.ui.Page
import com.akira.island.ui.Pos
import com.akira.island.ui.Tile
import com.akira.island.ui.SectionHeader

private enum class Which { TAP, DOUBLE, LONG }

@Composable
fun GesturesScreen(onBack: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    var dialog by remember { mutableStateOf<Which?>(null) }

    Page("Жесты", onBack, subtitle = "Раскрытый остров сворачивается нажатием или касанием за его пределами") {
        SectionHeader("Нажатия")
        Group {
            GroupItem(Pos.First, Icons.Rounded.TouchApp, "Одиночное нажатие", s.tapAction.title, onClick = { dialog = Which.TAP }, color = Tile.Blue)
            GroupItem(Pos.Middle, Icons.Rounded.TouchApp, "Двойное нажатие", s.doubleTapAction.title, onClick = { dialog = Which.DOUBLE }, color = Tile.Indigo)
            GroupItem(Pos.Last, Icons.Rounded.PanTool, "Долгое нажатие", s.longPressAction.title, onClick = { dialog = Which.LONG }, color = Tile.Orange)
        }
        SectionHeader("Свайпы")
        Group {
            GroupToggle(Pos.First, Icons.Rounded.KeyboardArrowDown, "Потянуть вниз", "Раскрыть капсулу, вверх — свернуть", s.swipeExpand, color = Tile.Teal) { v ->
                SettingsRepo.update { copy(swipeExpand = v) }
            }
            GroupToggle(Pos.Last, Icons.Rounded.SwapHoriz, "Толкнуть вбок", "Влево — следующий трек, вправо — предыдущий", s.swipeTracks, color = Tile.Purple) { v ->
                SettingsRepo.update { copy(swipeTracks = v) }
            }
        }
    }

    when (dialog) {
        Which.TAP -> ChoiceDialog("Одиночное нажатие", TapAction.entries, s.tapAction, { it.title },
            { v -> SettingsRepo.update { copy(tapAction = v) } }) { dialog = null }
        Which.DOUBLE -> ChoiceDialog("Двойное нажатие", TapAction.entries, s.doubleTapAction, { it.title },
            { v -> SettingsRepo.update { copy(doubleTapAction = v) } }) { dialog = null }
        Which.LONG -> ChoiceDialog("Долгое нажатие", TapAction.entries, s.longPressAction, { it.title },
            { v -> SettingsRepo.update { copy(longPressAction = v) } }) { dialog = null }
        null -> Unit
    }
}
