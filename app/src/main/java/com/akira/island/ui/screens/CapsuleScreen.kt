package com.akira.island.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.BlurCircular
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Contactless
import androidx.compose.material.icons.rounded.Flare
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LineWeight
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.IslandBus
import com.akira.island.core.Tints
import com.akira.island.data.Elasticity
import com.akira.island.data.ExpandStyle
import com.akira.island.data.IslandSettings
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
 * «Оформление»: где стоит остров и как он выглядит — положение и размер, цвет и стекло, анимации и эффекты.
 * Что остров показывает и как себя ведёт — во вкладке «Функции».
 */
@Composable
fun CapsuleScreen(open: (Screen) -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    Page("Оформление") {
        IslandPreview()

        SectionHeader("Положение и размер")
        Group {
            GroupItem(Pos.First, Icons.Rounded.OpenWith, "Положение", "Сдвинуть капсулу точно над камерой",
                color = Tile.Indigo, onClick = { open(Screen.SIZE) }) { Arrow() }
            GroupItem(Pos.Last, Icons.Rounded.SwapHoriz, "Размер с активностью",
                if (s.activeWidth == 0) "Толщина +${s.activeExtra} dp, ширина как без неё" else "Толщина +${s.activeExtra}, ширина +${s.activeWidth} dp",
                color = Tile.Purple, onClick = { open(Screen.LOOK) }) { Arrow() }
        }

        SectionHeader("Цвет и стекло")
        Group {
            GroupItem(Pos.First, Icons.Rounded.FormatColorFill, "Цвет капсулы", "Цвет, контур и тень",
                color = Tile.Orange, onClick = { open(Screen.COLORS) }) { Arrow() }
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

        SectionHeader("Анимации")
        Group {
            GroupItem(Pos.First, Icons.Rounded.Animation, "Раскрытие острова",
                "${s.expandStyle.title} · ${s.durationPercent}% · ${s.elasticity.title.lowercase()}",
                color = Tile.Blue, onClick = { open(Screen.ANIM) }) { Arrow() }
            GroupItem(Pos.Last, Icons.Rounded.AutoAwesome, "Эффекты", effectsSummary(s),
                color = Tile.Purple, onClick = { open(Screen.EFFECTS) }) { Arrow() }
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

/** «Раскрытие острова»: как остров раскрывается, насколько пружинит и сколько видно события. */
@Composable
fun AnimScreen(onBack: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    var styleDialog by remember { mutableStateOf(false) }
    var elasticDialog by remember { mutableStateOf(false) }

    Page("Раскрытие острова", onBack, action = {
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

/** Что включено в «Эффектах» — подпись пункта. */
private fun effectsSummary(s: IslandSettings): String {
    if (s.reduceEffects) return "Выключены: включено «Уменьшить эффекты»"
    val on = listOfNotNull(
        "размытие".takeIf { s.fxBlur },
        "эквалайзер".takeIf { s.fxEqualizer },
        "свечение".takeIf { s.fxGlow },
        "волны оплаты".takeIf { s.fxPay },
        "вздрагивание".takeIf { s.fxShake },
    )
    return if (on.isEmpty()) "Все выключены" else on.joinToString(", ").replaceFirstChar { it.uppercase() }
}

/** «Эффекты»: каждый живой эффект острова включается и выключается отдельно. */
@Composable
fun EffectsScreen(onBack: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    Page("Эффекты", onBack, action = {
        Text("Сброс", color = Ui.Sub, fontSize = 16.sp, modifier = Modifier.clickable { SettingsRepo.resetEffects() })
    }) {
        IslandPreview()
        if (s.reduceEffects) {
            Text(
                "Сейчас включено «Уменьшить эффекты» в «Раскрытии острова» — пока оно включено, эффектов нет",
                color = Tints.Orange, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        SectionHeader("Движение")
        Group {
            GroupToggle(Pos.First, Icons.Rounded.BlurCircular, "Размытие при смене",
                "Содержимое острова перетекает с размытием, как на iPhone (Android 12+)",
                s.fxBlur, color = Tile.Blue) { v -> SettingsRepo.update { copy(fxBlur = v) } }
            GroupToggle(Pos.Middle, Icons.Rounded.GraphicEq, "Живой эквалайзер",
                "Полоски у музыки и звонка пляшут. Выключено — стоят ровно, так экономнее",
                s.fxEqualizer, color = Tile.Pink) { v -> SettingsRepo.update { copy(fxEqualizer = v) } }
            GroupToggle(Pos.Last, Icons.Rounded.NotificationsActive, "Вздрагивание",
                "Капсула вздрагивает при смене режима звука, колокольчик звонящего таймера качается",
                s.fxShake, color = Tile.Orange) { v -> SettingsRepo.update { copy(fxShake = v) } }
        }

        SectionHeader("Свечение")
        Group {
            GroupToggle(Pos.First, Icons.Rounded.Flare, "Свечение обложки",
                "Мягкий ореол цвета обложки вокруг острова, пока играет музыка",
                s.fxGlow, color = Tile.Purple) { v -> SettingsRepo.update { copy(fxGlow = v) } }
            GroupToggle(Pos.Last, Icons.Rounded.Contactless, "Волны оплаты",
                "Волны NFC вокруг острова, пока ждём терминал, и вспышка с искрами на «Оплачено»",
                s.fxPay, color = Tile.Teal) { v -> SettingsRepo.update { copy(fxPay = v) } }
        }

        SectionHeader("Батарея")
        GroupToggle(Pos.Single, Icons.Rounded.BatterySaver, "Экономия заряда",
            "В режиме энергосбережения Android остров сам обходится без живых эффектов",
            s.batterySaver, color = Tile.Green) { v -> SettingsRepo.update { copy(batterySaver = v) } }
    }
}
