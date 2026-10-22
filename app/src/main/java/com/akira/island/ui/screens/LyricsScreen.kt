package com.akira.island.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.data.SettingsRepo
import com.akira.island.ui.Group
import com.akira.island.ui.GroupToggle
import com.akira.island.ui.Page
import com.akira.island.ui.Pos
import com.akira.island.ui.Ui

@Composable
fun LyricsScreen(onBack: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    Page("Текст песен", onBack) {
        Group {
            GroupToggle(Pos.First, Icons.Rounded.LibraryMusic, "Текст песен", "Три строки в раскрытом плеере: текущая крупно, соседние бледнее",
                s.lyricsEnabled) { v -> SettingsRepo.update { copy(lyricsEnabled = v) } }
            GroupToggle(Pos.Last, Icons.Rounded.Subtitles, "Строка в капсуле", "Текущая строка справа от камеры вместо полосок",
                s.lyricsInCapsule && s.lyricsEnabled) { v -> SettingsRepo.update { copy(lyricsInCapsule = v, lyricsEnabled = lyricsEnabled || v) } }
        }
        Text(
            "Текст ищется по названию, исполнителю и длительности трека: сначала на LRCLIB (lrclib.net), " +
                "потом на NetEase — там много русской музыки. Если синхронного текста нет нигде, строки обычного текста " +
                "раскладываются по длине песни. Пока идёт поиск, в плеере написано «Ищу текст песни…», " +
                "если ничего не нашлось — «Текст песни не найден». На сервер уходят только название и исполнитель. " +
                "Работает с любым плеером, который показывает уведомление с управлением: Spotify, YouTube Music, Яндекс Музыка, VK и т.д.",
            color = Ui.Sub,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ui.Card).padding(20.dp),
        )
    }
}
