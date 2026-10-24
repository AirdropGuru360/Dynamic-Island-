package com.akira.island.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.KeyboardVoice
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.Reply
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.akira.island.data.SettingsRepo
import com.akira.island.ui.Page
import com.akira.island.ui.IconAction
import com.akira.island.ui.Tile
import com.akira.island.ui.ToggleCard

/** «Динамические карточки» — как на первом скриншоте. */
@Composable
fun CardsScreen(onBack: () -> Unit, onInstruction: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()

    Page("Динамические карточки", onBack, action = { IconAction(Icons.Rounded.Lightbulb, "Инструкция", onInstruction) }) {
        ToggleCard(Icons.Outlined.PlayCircle, "Управление проигрывателем", "Когда воспроизведение активно и плеер свернут",
            s.cardMedia, color = Tile.Pink) { v -> SettingsRepo.update { copy(cardMedia = v) } }
        ToggleCard(Icons.Rounded.FlashlightOn, "Управление вспышкой", "Когда включен фонарик",
            s.cardTorch, color = Tile.Yellow) { v -> SettingsRepo.update { copy(cardTorch = v) } }
        ToggleCard(Icons.Rounded.Downloading, "Индикатор прогресса",
            "Когда разные приложения (Google Play, Telegram и т.д.) показывают прогресс",
            s.cardProgress, color = Tile.Teal) { v -> SettingsRepo.update { copy(cardProgress = v) } }
        ToggleCard(Icons.Rounded.HourglassEmpty, "Таймер/Секундомер",
            "Когда таймер или секундомер «Часов» работает в фоне — даже если уведомления «Часов» выключены: " +
                "остров запоминает их, пока вы в приложении",
            s.cardTimer, color = Tile.Orange) { v -> SettingsRepo.update { copy(cardTimer = v) } }
        ToggleCard(Icons.Rounded.TouchApp, "Кнопки для «Часов»",
            "Пауза и сброс в острове нажимают кнопки самих «Часов»; если их уведомления выключены — на миг открывая приложение. " +
                "Выключено — кнопки просто открывают «Часы»",
            s.clockAutoTap, color = Tile.Orange) { v -> SettingsRepo.update { copy(clockAutoTap = v) } }
        ToggleCard(Icons.Rounded.KeyboardVoice, "Таймер от помощника",
            "«Поставь таймер на 5 минут» от голосового помощника или виджета можно отдать острову — он сразу появится в нём",
            s.timerFromAssistant, color = Tile.Pink) { v -> SettingsRepo.update { copy(timerFromAssistant = v) } }
        ToggleCard(Icons.Rounded.Bedtime, "Будильник",
            "За 12 часов до будильника — время подъёма и сколько осталось спать, с циферблатом сна, как на iPhone",
            s.cardAlarm, color = Tile.Indigo) { v -> SettingsRepo.update { copy(cardAlarm = v) } }
        ToggleCard(Icons.Rounded.OfflineBolt, "Индикатор зарядки", "Когда устройство заряжается",
            s.cardCharging, color = Tile.Green) { v -> SettingsRepo.update { copy(cardCharging = v) } }
        ToggleCard(Icons.Rounded.ChatBubble, "Уведомления приложений",
            "Сообщения из Telegram, WhatsApp, почты и других приложений — карточкой в острове, как на iPhone",
            s.cardMessages, color = Tile.Blue) { v -> SettingsRepo.update { copy(cardMessages = v) } }
        ToggleCard(Icons.Rounded.Reply, "Кнопки в уведомлениях",
            "«Ответить» с вводом текста прямо в острове и «Пометить прочитанным» — кнопки из самого уведомления",
            s.msgActions, color = Tile.Indigo) { v -> SettingsRepo.update { copy(msgActions = v) } }
        ToggleCard(Icons.Rounded.Navigation, "Навигация, такси и доставка",
            "Google Карты, Яндекс Карты и Навигатор, 2ГИС, Яндекс Go, Uber, Яндекс Еда и другие — расстояние и время в капсуле, подробности в раскрытом виде",
            s.cardLive, color = Tile.Teal) { v -> SettingsRepo.update { copy(cardLive = v) } }
        ToggleCard(Icons.Rounded.Call, "Звонки", "Входящий вызов с кнопками ответа и время разговора",
            s.cardCall, color = Tile.Green) { v -> SettingsRepo.update { copy(cardCall = v) } }
        ToggleCard(Icons.Rounded.AutoAwesome, "Пасхалки",
            "Одну особенную песню остров узнаёт и устраивает для неё маленькое шоу в такт музыке. Какую — попробуйте угадать",
            s.easterEggs, color = Tile.Red) { v -> SettingsRepo.update { copy(easterEggs = v) } }
    }
}
