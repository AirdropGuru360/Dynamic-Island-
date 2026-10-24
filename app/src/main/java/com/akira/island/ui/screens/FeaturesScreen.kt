package com.akira.island.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.FormatLineSpacing
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.Tints
import com.akira.island.data.AccentMode
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import com.akira.island.island.FitText
import com.akira.island.island.onAccent
import com.akira.island.island.resolveAccent
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
 * «Функции»: что остров показывает и как с ним обращаться. Внешний вид — во вкладке «Оформление»,
 * версия, помощь и ссылки — в «О приложении».
 */
@Composable
fun FeaturesScreen(open: (Screen) -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }

    Page("Функции") {
        SectionHeader("Что показывает остров")
        Group {
            GroupItem(Pos.First, Icons.Rounded.Dashboard, "Динамические карточки", "Музыка, звонки, таймеры, фонарик, навигация",
                color = Tile.Purple, onClick = { open(Screen.CARDS) }) { Arrow() }
            GroupItem(Pos.Middle, Icons.Rounded.ChatBubble, "Уведомления", "Сообщения приложений с ответом прямо в острове",
                color = Tile.Blue, onClick = { open(Screen.NOTIFICATIONS) }) { Arrow() }
            GroupItem(Pos.Middle, Icons.Rounded.NotificationsActive, "События", "Зарядка, Wi-Fi, наушники, оплата, будильник",
                color = Tile.Orange, onClick = { open(Screen.EVENTS) }) { Arrow() }
            GroupItem(Pos.Last, Icons.Rounded.LibraryMusic, "Текст песен", "Синхронный текст в раскрытом плеере",
                color = Tile.Pink, onClick = { open(Screen.LYRICS) }) { Arrow() }
        }

        SectionHeader("Управление")
        Group {
            GroupItem(Pos.First, Icons.Rounded.Gesture, "Жесты", "Нажатия и свайпы по капсуле",
                color = Tile.Teal, onClick = { open(Screen.GESTURES) }) { Arrow() }
            GroupItem(Pos.Middle, Icons.Rounded.Tune, "Поведение", "Когда показывать и сворачивать остров, виброотклик",
                color = Tile.Indigo, onClick = { open(Screen.BEHAVIOR) }) { Arrow() }
            GroupItem(Pos.Last, Icons.Rounded.NotificationsOff, "Системные плашки", "Чтобы звонок, таймер и батарею показывал только остров",
                color = Tile.Red, onClick = { open(Screen.BANNERS) }) { Arrow() }
        }

        SectionHeader("Сброс")
        GroupItem(Pos.Single, Icons.Rounded.Restore, "Сбросить все настройки", "Вернуть всё как было после установки",
            onClick = { confirmReset = true }, color = Tile.Gray)
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

/** «Поведение»: когда остров виден, как сворачивается и чем отзывается. */
@Composable
fun BehaviorScreen(onBack: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    var collapseDialog by remember { mutableStateOf(false) }

    Page("Поведение", onBack) {
        SectionHeader("Когда показывать")
        Group {
            GroupToggle(Pos.First, Icons.Rounded.VisibilityOff, "Скрывать пустую капсулу", "Показывать капсулу, только когда в ней что-то есть",
                s.hideIdle, color = Tile.Gray) { v -> SettingsRepo.update { copy(hideIdle = v) } }
            GroupToggle(Pos.Middle, Icons.Rounded.ScreenRotation, "Скрывать в альбомной ориентации", "Не мешает играм и видео",
                s.hideInLandscape, color = Tile.Gray) { v -> SettingsRepo.update { copy(hideInLandscape = v) } }
            GroupToggle(Pos.Last, Icons.Rounded.Apps, "Прятать плеер в его приложении", "Карточка музыки не показывается, пока открыт сам плеер",
                s.hideWhenPlayerOpen, color = Tile.Blue) { v -> SettingsRepo.update { copy(hideWhenPlayerOpen = v) } }
        }

        SectionHeader("Раскрытый остров")
        Group {
            GroupItem(Pos.First, Icons.Rounded.UnfoldLess, "Сворачивать раскрытый остров", collapseLabel(s.autoCollapseSec),
                color = Tile.Indigo, onClick = { collapseDialog = true })
            GroupToggle(Pos.Last, Icons.Rounded.Circle, "Вторая активность рядом",
                "Когда активностей две, вторая — кружком рядом с капсулой, как на iPhone", s.bubble, color = Tile.Purple) { v ->
                SettingsRepo.update { copy(bubble = v) }
            }
        }

        SectionHeader("Отклик")
        Group {
            GroupToggle(Pos.First, Icons.Rounded.Vibration, "Виброотклик", "При открытии, закрытии и нажатиях на капсулу",
                s.haptics, color = Tile.Pink) { v -> SettingsRepo.update { copy(haptics = v) } }
            GroupToggle(Pos.Last, Icons.Rounded.LightMode, "Включать экран",
                "Когда звонит таймер, будильник или входящий вызов — экран включается и не гаснет, пока звонит",
                s.wakeForAlerts, color = Tile.Yellow) { v -> SettingsRepo.update { copy(wakeForAlerts = v) } }
        }
    }

    if (collapseDialog) {
        ChoiceDialog("Сворачивать раскрытый остров", listOf(0, 5, 10, 20, 30), s.autoCollapseSec, ::collapseLabel,
            { v -> SettingsRepo.update { copy(autoCollapseSec = v) } }) { collapseDialog = false }
    }
}

/** Подпись для «Сворачивать раскрытый остров». */
private fun collapseLabel(sec: Int) = if (sec <= 0) "Только касанием мимо или свайпом вверх" else "Через $sec секунд без касаний"

private val accentColors = listOf(
    0xFF0A84FF, 0xFFC6C3F7, 0xFF64D2FF, 0xFF34C759, 0xFFFFD60A, 0xFFFF9F0A,
    0xFFFF453A, 0xFFFF2D92, 0xFFBF5AF2, 0xFFFFFFFF,
)

/** «Уведомления»: сообщения приложений в острове — что показывать, сколько текста и каким цветом кнопки. */
@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    var accentDialog by remember { mutableStateOf(false) }

    Page("Уведомления", onBack) {
        Group {
            GroupToggle(Pos.First, Icons.Rounded.ChatBubble, "Уведомления приложений",
                "Сообщения из Telegram, WhatsApp, почты и других приложений — карточкой в острове, как на iPhone",
                s.cardMessages, color = Tile.Blue) { v -> SettingsRepo.update { copy(cardMessages = v) } }
            GroupToggle(Pos.Middle, Icons.AutoMirrored.Rounded.Reply, "Кнопки в уведомлениях",
                "«Ответить» с вводом текста прямо в острове и «Пометить прочитанным»",
                s.msgActions, color = Tile.Indigo) { v -> SettingsRepo.update { copy(msgActions = v) } }
            GroupToggle(Pos.Middle, Icons.Rounded.Smartphone, "Из открытого приложения",
                "Показывать и уведомления приложения, которое сейчас на экране", s.msgInApp, color = Tile.Teal) { v ->
                SettingsRepo.update { copy(msgInApp = v) }
            }
            GroupSlider(
                Pos.Last, Icons.Rounded.FormatLineSpacing, "Строк текста",
                value = s.msgLines.toFloat(), range = 1f..6f, steps = 4, label = "${s.msgLines}", color = Tile.Green,
            ) { v -> SettingsRepo.update { copy(msgLines = Math.round(v)) } }
        }

        SectionHeader("Цвет кнопок")
        AccentPreview(s)
        GroupItem(Pos.Single, Icons.Rounded.Palette, "Цвет кнопок", "${s.accentMode.title} — ${s.accentMode.hint}",
            onClick = { accentDialog = true }, color = Tile.Orange)
        if (s.accentMode == AccentMode.CUSTOM) {
            Swatches(accentColors, s.accentColor) { c -> SettingsRepo.update { copy(accentColor = c) } }
        }
    }

    if (accentDialog) {
        ChoiceDialog("Цвет кнопок", AccentMode.entries, s.accentMode, { "${it.title} — ${it.hint}" },
            { v -> SettingsRepo.update { copy(accentMode = v) } }) { accentDialog = false }
    }
}

/** Как выглядят «Ответить», «Пометить прочитанным» и кнопка отправки с выбранным цветом. */
@Composable
private fun AccentPreview(s: IslandSettings) {
    val context = LocalContext.current
    // В режиме «Из аватара» цвет зависит от самого уведомления — в примере берём оранжевый
    val accent = resolveAccent(context, s, Tints.Orange)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color.Black).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PreviewTile("Ответить", accent, Modifier.weight(1f))
        PreviewTile("Пометить прочитанным", accent, Modifier.weight(2f))
        Box(Modifier.size(40.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ArrowUpward, null, tint = onAccent(accent), modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun PreviewTile(title: String, accent: Color, modifier: Modifier) {
    Box(
        modifier.height(40.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        FitText(title, accent, 14.sp, 10.sp, FontWeight.SemiBold)
    }
}
