package com.akira.island.ui.screens

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
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.Reply
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.data.SettingsRepo
import com.akira.island.data.TapAction
import com.akira.island.ui.ChoiceDialog
import com.akira.island.ui.Page
import com.akira.island.ui.PillAction
import com.akira.island.ui.ToggleCard
import com.akira.island.ui.Ui

/** «Динамические карточки» — как на первом скриншоте. */
@Composable
fun CardsScreen(onBack: () -> Unit, onInstruction: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    var tapDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Page("Динамические карточки", onBack, action = { PillAction("Инструкция", onInstruction) }) {
            ToggleCard(Icons.Outlined.PlayCircle, "Управление проигрывателем", "Когда воспроизведение активно и плеер свернут",
                s.cardMedia) { v -> SettingsRepo.update { copy(cardMedia = v) } }
            ToggleCard(Icons.Rounded.FlashlightOn, "Управление вспышкой", "Когда включен фонарик",
                s.cardTorch) { v -> SettingsRepo.update { copy(cardTorch = v) } }
            ToggleCard(Icons.Rounded.Downloading, "Индикатор прогресса",
                "Когда разные приложения (Google Play, Telegram и т.д.) показывают прогресс",
                s.cardProgress) { v -> SettingsRepo.update { copy(cardProgress = v) } }
            ToggleCard(Icons.Rounded.HourglassEmpty, "Таймер/Секундомер",
                "Когда таймер или секундомер Google Clock работает в фоне",
                s.cardTimer) { v -> SettingsRepo.update { copy(cardTimer = v) } }
            ToggleCard(Icons.Rounded.OfflineBolt, "Индикатор зарядки", "Когда устройство заряжается",
                s.cardCharging) { v -> SettingsRepo.update { copy(cardCharging = v) } }
            ToggleCard(Icons.Rounded.ChatBubble, "Уведомления приложений",
                "Сообщения из Telegram, WhatsApp, почты и других приложений — карточкой в острове, как на iPhone",
                s.cardMessages) { v -> SettingsRepo.update { copy(cardMessages = v) } }
            ToggleCard(Icons.Rounded.Reply, "Кнопки в уведомлениях",
                "«Ответить» с вводом текста прямо в острове и «Пометить прочитанным» — кнопки из самого уведомления",
                s.msgActions) { v -> SettingsRepo.update { copy(msgActions = v) } }
            ToggleCard(Icons.Rounded.Navigation, "Навигация, такси и доставка",
                "Google Карты, Яндекс Карты и Навигатор, 2ГИС, Яндекс Go, Uber, Яндекс Еда и другие — расстояние и время в капсуле, подробности в раскрытом виде",
                s.cardLive) { v -> SettingsRepo.update { copy(cardLive = v) } }
            ToggleCard(Icons.Rounded.Call, "Звонки", "Входящий вызов с кнопками ответа и время разговора",
                s.cardCall) { v -> SettingsRepo.update { copy(cardCall = v) } }
        }

        // Розовая кнопка внизу: что делает одиночное нажатие на капсулу
        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 24.dp)
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(Ui.Pink)
                .clickable { tapDialog = true }
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.TouchApp, null, tint = Ui.OnPink, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Text("Одиночное нажатие", color = Ui.OnPink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    if (tapDialog) {
        ChoiceDialog("Одиночное нажатие", TapAction.entries, s.tapAction, { it.title },
            { v -> SettingsRepo.update { copy(tapAction = v) } }) { tapDialog = false }
    }
}
