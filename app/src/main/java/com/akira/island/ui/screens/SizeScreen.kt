package com.akira.island.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.IslandBus
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import com.akira.island.island.CalibrationRed
import com.akira.island.island.rememberHaptic
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.Ui
import kotlin.math.roundToInt

private const val X_RANGE = 200
private const val Y_MIN = -10
private const val Y_MAX = 120

/**
 * Подгонка острова под камеру. Пока экран открыт, остров на телефоне красный с белым ободком:
 * его двигают, пока красное не спрячется под вырезом и вокруг камеры не останется ровная белая линия.
 * «Сохранить» оставляет новые значения, «назад» возвращает прежние.
 */
@Composable
fun SizeScreen(onBack: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    val original = remember { SettingsRepo.current }
    val view = LocalView.current
    val density = LocalDensity.current
    val conf = LocalConfiguration.current
    val haptic = rememberHaptic()
    var autoMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        IslandBus.calibrating.value = true
        onDispose { IslandBus.calibrating.value = false }
    }

    val cancel = {
        SettingsRepo.update {
            copy(width = original.width, height = original.height, offsetX = original.offsetX, offsetY = original.offsetY)
        }
        onBack()
    }
    BackHandler(onBack = cancel)

    fun set(block: IslandSettings.() -> IslandSettings) = SettingsRepo.update(block)

    /** Вырез камеры известен системе — ставим остров ровно по нему. */
    fun autoFit() {
        val rects = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            view.rootWindowInsets?.displayCutout?.boundingRects.orEmpty()
        } else {
            emptyList()
        }
        val screenW = with(density) { conf.screenWidthDp.dp.toPx() }
        val cam = rects.filter { it.top <= 1 || it.top < it.height() }.minByOrNull { kotlin.math.abs(it.exactCenterX() - screenW / 2) }
        if (cam == null) {
            autoMessage = "Система не сообщает о вырезе камеры — подгоните вручную"
            return
        }
        with(density) {
            val camW = cam.width().toDp().value
            val camH = cam.height().toDp().value
            val h = maxOf(s.height.toFloat(), camH + 10f)
            set {
                copy(
                    offsetX = ((cam.exactCenterX() - screenW / 2).toDp().value).roundToInt(),
                    offsetY = (cam.exactCenterY().toDp().value - h / 2).roundToInt(),
                    height = h.roundToInt(),
                    width = maxOf(width, (camW + h).roundToInt()),
                )
            }
        }
        haptic()
        autoMessage = "Готово: остров поставлен по вырезу камеры"
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIcon(Icons.AutoMirrored.Rounded.ArrowBack, cancel)
                Spacer(Modifier.width(18.dp))
                Text("Размер и положение", color = Ui.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                CircleIcon(Icons.Rounded.Restore, bg = Color.Transparent) {
                    val d = IslandSettings()
                    set { copy(width = d.width, height = d.height, offsetX = d.offsetX, offsetY = d.offsetY) }
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionHeader("Пример")
            Spacer(Modifier.height(12.dp))
            Example()
            Text(
                "Разместите красную капсулу прямо под вырезом камеры. Она должна стать невидимой — " +
                    "вокруг камеры должна остаться только ровная белая линия. " +
                    "Если на вашем устройстве нет выреза, разместите её там, где вам удобно.",
                color = Ui.Sub, fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp),
            )
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(
                    Modifier.clip(CircleShape).background(Ui.IconBg).clickable { autoFit() }.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.CenterFocusStrong, null, tint = Ui.Primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Подогнать под камеру автоматически", color = Ui.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            autoMessage?.let {
                Text(it, color = Ui.Sub.copy(alpha = 0.7f), fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }

            Spacer(Modifier.height(18.dp))
            AxisRow(
                "Расположение по оси X", s.offsetX, -X_RANGE, X_RANGE,
                Icons.Rounded.ChevronLeft, Icons.Rounded.ChevronRight,
                action = "Центрировать" to { set { copy(offsetX = 0) } },
            ) { v -> set { copy(offsetX = v) } }
            AxisRow(
                "Расположение по оси Y", s.offsetY, Y_MIN, Y_MAX,
                Icons.Rounded.KeyboardArrowUp, Icons.Rounded.KeyboardArrowDown,
            ) { v -> set { copy(offsetY = v) } }
            AxisRow(
                "Ширина", s.width, 50, 260,
                Icons.Rounded.UnfoldLess, Icons.Rounded.UnfoldMore, rotateIcons = true,
            ) { v -> set { copy(width = v) } }
            AxisRow(
                "Высота", s.height, 22, 60,
                Icons.Rounded.UnfoldLess, Icons.Rounded.UnfoldMore,
            ) { v -> set { copy(height = v) } }

            Spacer(Modifier.height(110.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 18.dp)
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(Ui.Primary)
                .clickable { haptic(); onBack() }
                .padding(horizontal = 40.dp, vertical = 18.dp),
        ) {
            Text("Сохранить", color = Ui.OnPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CircleIcon(icon: ImageVector, bg: Color = Ui.Card, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Ui.Text)
    }
}

/** Картинка-подсказка: красная капсула не под камерой → после подгонки видна только белая линия. */
@Composable
private fun Example() {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(56.dp)).background(Ui.Card).padding(vertical = 34.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(82.dp, 62.dp).clip(RoundedCornerShape(31.dp)).background(Color(0xFF77757E)), contentAlignment = Alignment.Center) {
            Box(Modifier.offset(x = 6.dp, y = 4.dp).size(52.dp, 30.dp).clip(CircleShape).background(CalibrationRed).border(2.dp, Color.White, CircleShape))
            Box(Modifier.offset(x = (-10).dp, y = (-10).dp).size(48.dp, 28.dp).clip(CircleShape).background(Color.Black))
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Ui.Primary, modifier = Modifier.size(28.dp))
        Box(Modifier.size(82.dp, 62.dp).clip(RoundedCornerShape(31.dp)).background(Color(0xFF77757E)), contentAlignment = Alignment.Center) {
            Box(Modifier.size(52.dp, 30.dp).clip(CircleShape).background(Color.Black).border(2.dp, Color.White, CircleShape))
        }
    }
}

/** Строка настройки: подпись со значением, ползунок и кнопки на 1 dp по краям. */
@Composable
private fun AxisRow(
    title: String,
    value: Int,
    min: Int,
    max: Int,
    lessIcon: ImageVector,
    moreIcon: ImageVector,
    rotateIcons: Boolean = false,
    action: Pair<String, () -> Unit>? = null,
    onChange: (Int) -> Unit,
) {
    val haptic = rememberHaptic()
    Column(Modifier.padding(bottom = 18.dp)) {
        Row(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Ui.Primary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(10.dp))
            Text("$value", color = Ui.Sub.copy(alpha = 0.6f), fontSize = 17.sp, modifier = Modifier.weight(1f))
            if (action != null) {
                Text(action.first, color = Ui.Sub.copy(alpha = 0.7f), fontSize = 16.sp,
                    modifier = Modifier.clip(CircleShape).clickable { haptic(); action.second() }.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton(lessIcon, rotateIcons) {
                haptic()
                onChange((value - 1).coerceAtLeast(min))
            }
            Slider(
                value = value.toFloat().coerceIn(min.toFloat(), max.toFloat()),
                onValueChange = { onChange(it.roundToInt()) },
                valueRange = min.toFloat()..max.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Ui.Primary,
                    activeTrackColor = Ui.Primary,
                    inactiveTrackColor = Ui.IconBg,
                ),
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            )
            StepButton(moreIcon, rotateIcons) {
                haptic()
                onChange((value + 1).coerceAtMost(max))
            }
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, rotate: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(56.dp).clip(RoundedCornerShape(20.dp)).background(Ui.Card).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Ui.Text, modifier = Modifier.size(26.dp).rotate(if (rotate) 90f else 0f))
    }
}
