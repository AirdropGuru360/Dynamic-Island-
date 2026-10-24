package com.akira.island.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.KeyboardVoice
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import com.akira.island.ui.Group
import com.akira.island.ui.GroupToggle
import com.akira.island.ui.IconAction
import com.akira.island.ui.Page
import com.akira.island.ui.Pos
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.Tile

/** «Динамические карточки»: что остров показывает, пока это идёт, — по разделам. */
@Composable
fun CardsScreen(onBack: () -> Unit, onInstruction: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    fun set(block: IslandSettings.() -> IslandSettings) = SettingsRepo.update(block)

    Page("Динамические карточки", onBack, action = { IconAction(Icons.Rounded.Lightbulb, "Инструкция", onInstruction) }) {
        SectionHeader("Музыка и звонки")
        Group {
            GroupToggle(Pos.First, Icons.Outlined.PlayCircle, "Управление проигрывателем", "Когда играет музыка и плеер свёрнут",
                s.cardMedia, color = Tile.Pink) { set { copy(cardMedia = it) } }
            GroupToggle(Pos.Last, Icons.Rounded.Call, "Звонки", "Входящий вызов с кнопками ответа и время разговора",
                s.cardCall, color = Tile.Green) { set { copy(cardCall = it) } }
        }

        SectionHeader("Таймеры и будильник")
        Group {
            GroupToggle(Pos.First, Icons.Rounded.HourglassEmpty, "Таймер и секундомер",
                "Таймер и секундомер «Часов» в фоне — даже если их уведомления выключены",
                s.cardTimer, color = Tile.Orange) { set { copy(cardTimer = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.TouchApp, "Кнопки для «Часов»",
                "Пауза и сброс в острове нажимают кнопки самих «Часов». Выключено — просто открывают «Часы»",
                s.clockAutoTap, color = Tile.Orange) { set { copy(clockAutoTap = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.KeyboardVoice, "Таймер от помощника",
                "«Поставь таймер на 5 минут» от помощника или виджета можно отдать острову",
                s.timerFromAssistant, color = Tile.Pink) { set { copy(timerFromAssistant = it) } }
            GroupToggle(Pos.Last, Icons.Rounded.Bedtime, "Будильник",
                "За 12 часов до будильника — время подъёма и циферблат сна, как на iPhone",
                s.cardAlarm, color = Tile.Indigo) { set { copy(cardAlarm = it) } }
        }

        SectionHeader("Система")
        Group {
            GroupToggle(Pos.First, Icons.Rounded.FlashlightOn, "Управление вспышкой", "Когда включён фонарик",
                s.cardTorch, color = Tile.Yellow) { set { copy(cardTorch = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.OfflineBolt, "Индикатор зарядки", "Когда устройство заряжается",
                s.cardCharging, color = Tile.Green) { set { copy(cardCharging = it) } }
            GroupToggle(Pos.Last, Icons.Rounded.Downloading, "Индикатор прогресса",
                "Загрузки и отправка в Google Play, Telegram и других приложениях",
                s.cardProgress, color = Tile.Teal) { set { copy(cardProgress = it) } }
        }

        SectionHeader("В дороге")
        GroupToggle(Pos.Single, Icons.Rounded.Navigation, "Навигация, такси и доставка",
            "Google и Яндекс Карты, Навигатор, 2ГИС, Яндекс Go, Uber и другие — расстояние и время в капсуле, подробности в раскрытом виде",
            s.cardLive, color = Tile.Teal) { set { copy(cardLive = it) } }

        SectionHeader("Сюрпризы")
        GroupToggle(Pos.Single, Icons.Rounded.AutoAwesome, "Пасхалки",
            "Одну особенную песню остров узнаёт и устраивает для неё маленькое шоу в такт музыке. Какую — попробуйте угадать",
            s.easterEggs, color = Tile.Red) { set { copy(easterEggs = it) } }
    }
}
