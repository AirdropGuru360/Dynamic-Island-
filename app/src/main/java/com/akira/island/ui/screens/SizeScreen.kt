package com.akira.island.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.IslandBus
import com.akira.island.core.tr
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import com.akira.island.island.rememberHaptic
import com.akira.island.ui.Group
import com.akira.island.ui.GroupStepSlider
import com.akira.island.ui.Page
import com.akira.island.ui.Pos
import com.akira.island.ui.Tile
import com.akira.island.ui.Ui

/**
 * Положение острова: только сдвиг по горизонтали и отступ сверху. Пока экран открыт, остров
 * на телефоне красный с белым ободком — так видно, где он стоит относительно камеры.
 * «Сохранить» оставляет новые значения, «назад» возвращает прежние.
 */
@Composable
fun SizeScreen(onBack: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    val original = remember { SettingsRepo.current }
    val haptic = rememberHaptic()

    DisposableEffect(Unit) {
        IslandBus.calibrating.value = true
        onDispose { IslandBus.calibrating.value = false }
    }

    val cancel = {
        SettingsRepo.update {
            copy(offsetX = original.offsetX, offsetY = original.offsetY)
        }
        onBack()
    }
    BackHandler(onBack = cancel)

    fun set(block: IslandSettings.() -> IslandSettings) = SettingsRepo.update(block)

    Box(Modifier.fillMaxSize()) {
        Page(
            tr("Положение", "Position"),
            onBack = cancel,
            action = {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Ui.Card).clickable {
                        val d = IslandSettings()
                        set { copy(offsetX = d.offsetX, offsetY = d.offsetY) }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Restore, tr("Сбросить", "Reset"), tint = Ui.Text)
                }
            },
        ) {
            Group {
                GroupStepSlider(
                    Pos.First, Icons.Rounded.SwapHoriz, tr("По горизонтали", "Horizontal"), s.offsetX, -200..200,
                    action = tr("Центр", "Center") to { set { copy(offsetX = 0) } }, color = Tile.Blue,
                ) { v -> set { copy(offsetX = v) } }
                GroupStepSlider(Pos.Last, Icons.Rounded.SwapVert, tr("Отступ сверху", "Top offset"), s.offsetY, -10..120, color = Tile.Indigo) { v ->
                    set { copy(offsetY = v) }
                }
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(Ui.Primary)
                .clickable { haptic(); onBack() }
                .padding(horizontal = 30.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Check, null, tint = Ui.OnPrimary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(tr("Сохранить", "Save"), color = Ui.OnPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
