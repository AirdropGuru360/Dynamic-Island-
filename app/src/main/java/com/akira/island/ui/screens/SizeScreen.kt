package com.akira.island.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CenterFocusWeak
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.IslandBus
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import com.akira.island.island.CalibrationRed
import com.akira.island.island.rememberHaptic
import com.akira.island.ui.Group
import com.akira.island.ui.GroupItem
import com.akira.island.ui.GroupStepSlider
import com.akira.island.ui.Page
import com.akira.island.ui.Pos
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.Ui

/**
 * Подгонка острова под камеру. Пока экран открыт, остров на телефоне красный с белым ободком:
 * его двигают, пока красное не спрячется под вырезом и вокруг камеры не останется ровная белая линия.
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
            copy(
                width = original.width, height = original.height, offsetX = original.offsetX,
                offsetY = original.offsetY,
            )
        }
        onBack()
    }
    BackHandler(onBack = cancel)

    fun set(block: IslandSettings.() -> IslandSettings) = SettingsRepo.update(block)

    Box(Modifier.fillMaxSize()) {
        Page(
            "Размер и положение",
            onBack = cancel,
            action = {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Ui.Card).clickable {
                        val d = IslandSettings()
                        set { copy(width = d.width, height = d.height, offsetX = d.offsetX, offsetY = d.offsetY) }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Restore, "Сбросить", tint = Ui.Text)
                }
            },
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Ui.Card).padding(vertical = 22.dp, horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Example()
                Spacer(Modifier.height(16.dp))
                Text(
                    "Пока открыт этот экран, остров красный. Двигайте его, пока красное не спрячется под камерой " +
                        "и вокруг неё не останется ровная белая линия.",
                    color = Ui.Sub, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center,
                )
            }

            SectionHeader("Положение")
            Group {
                GroupStepSlider(
                    Pos.First, Icons.Rounded.SwapHoriz, "По горизонтали", s.offsetX, -200..200,
                    action = "Центр" to { set { copy(offsetX = 0) } },
                ) { v -> set { copy(offsetX = v) } }
                GroupStepSlider(Pos.Last, Icons.Rounded.SwapVert, "Отступ сверху", s.offsetY, -10..120) { v ->
                    set { copy(offsetY = v) }
                }
            }

            SectionHeader("Размер")
            Group {
                GroupStepSlider(Pos.First, Icons.Rounded.Straighten, "Ширина", s.width, 50..260) { v -> set { copy(width = v) } }
                GroupStepSlider(Pos.Last, Icons.Rounded.Height, "Высота", s.height, 22..60) { v -> set { copy(height = v) } }
            }
            Text(
                "По умолчанию остров как на iPhone: 126 × 37 dp и 11 dp от верхнего края. " +
                    "Если камера на вашем телефоне стоит иначе, сдвиньте и растяните остров, пока красное не скроет камеру.",
                color = Ui.Sub.copy(alpha = 0.7f), fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
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
            Text("Сохранить", color = Ui.OnPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Картинка-подсказка: красная капсула не под камерой → после подгонки видна только белая линия. */
@Composable
private fun Example() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(84.dp, 60.dp).clip(RoundedCornerShape(30.dp)).background(Ui.IconBg), contentAlignment = Alignment.Center) {
            Box(Modifier.offset(x = 6.dp, y = 4.dp).size(52.dp, 30.dp).clip(CircleShape).background(CalibrationRed).border(2.dp, Color.White, CircleShape))
            Box(Modifier.offset(x = (-10).dp, y = (-9).dp).size(46.dp, 26.dp).clip(CircleShape).background(Color.Black))
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Ui.Primary, modifier = Modifier.size(26.dp))
        Box(Modifier.size(84.dp, 60.dp).clip(RoundedCornerShape(30.dp)).background(Ui.IconBg), contentAlignment = Alignment.Center) {
            Box(Modifier.size(52.dp, 30.dp).clip(CircleShape).background(Color.Black).border(2.dp, Color.White, CircleShape))
        }
    }
}
