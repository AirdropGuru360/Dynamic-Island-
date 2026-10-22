package com.akira.island.ui.screens

import android.app.Activity
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
import com.akira.island.ui.Cutout
import com.akira.island.ui.Group
import com.akira.island.ui.GroupItem
import com.akira.island.ui.GroupStepSlider
import com.akira.island.ui.Page
import com.akira.island.ui.Pos
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.Ui
import kotlin.math.roundToInt

/**
 * Подгонка острова под камеру. Пока экран открыт, остров на телефоне красный с белым ободком:
 * его двигают, пока красное не спрячется под вырезом и вокруг камеры не останется ровная белая линия.
 * «Сохранить» оставляет новые значения, «назад» возвращает прежние.
 */
@Composable
fun SizeScreen(onBack: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    val original = remember { SettingsRepo.current }
    val context = LocalContext.current
    val haptic = rememberHaptic()
    var autoStatus by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        IslandBus.calibrating.value = true
        onDispose { IslandBus.calibrating.value = false }
    }

    val cancel = {
        SettingsRepo.update {
            copy(
                width = original.width, height = original.height, offsetX = original.offsetX,
                offsetY = original.offsetY, cameraGap = original.cameraGap,
            )
        }
        onBack()
    }
    BackHandler(onBack = cancel)

    fun set(block: IslandSettings.() -> IslandSettings) = SettingsRepo.update(block)

    fun autoFit() {
        val hole = (context as? Activity)?.let { Cutout.find(it) }
        if (hole == null) {
            autoStatus = "Телефон не сообщает, где камера. Подгоните остров вручную ползунками ниже."
            return
        }
        val r = hole.rect
        val camW = Cutout.dp(context, r.width().toFloat())
        val camH = Cutout.dp(context, r.height().toFloat())
        // Некоторые телефоны отдают вырез высотой во всю строку состояния — тогда толщину не трогаем
        val h = if (camH + 6 in (s.height + 1)..46) camH + 6 else s.height
        set {
            copy(
                offsetX = Cutout.dp(context, r.exactCenterX() - hole.screenWidth / 2f),
                offsetY = (Cutout.dp(context, r.exactCenterY()) - h / 2f).roundToInt(),
                height = h,
                width = maxOf(width, camW + h),
                cameraGap = (camW + 6).coerceIn(12, 80),
            )
        }
        haptic()
        autoStatus = "Готово: остров стоит по центру камеры. Если красное ещё видно — поправьте ползунками."
    }

    Box(Modifier.fillMaxSize()) {
        Page(
            "Размер и положение",
            onBack = cancel,
            action = {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Ui.Card).clickable {
                        val d = IslandSettings()
                        set { copy(width = d.width, height = d.height, offsetX = d.offsetX, offsetY = d.offsetY, cameraGap = d.cameraGap) }
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

            GroupItem(
                Pos.Single, Icons.Rounded.CenterFocusStrong, "Подогнать под камеру",
                autoStatus ?: "Поставить остров ровно по вырезу камеры автоматически",
                onClick = { autoFit() },
            ) {
                Box(Modifier.clip(CircleShape).background(Ui.Primary).padding(horizontal = 14.dp, vertical = 7.dp)) {
                    Text("Авто", color = Ui.OnPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
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
                GroupStepSlider(Pos.Middle, Icons.Rounded.Height, "Высота", s.height, 22..60) { v -> set { copy(height = v) } }
                GroupStepSlider(Pos.Last, Icons.Rounded.CenterFocusWeak, "Вырез камеры", s.cameraGap, 8..80) { v ->
                    set { copy(cameraGap = v) }
                }
            }
            Text(
                "«Вырез камеры» — свободное место посередине, к которому прижимаются значки и текст, " +
                    "когда в острове музыка, таймер или зарядка.",
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
