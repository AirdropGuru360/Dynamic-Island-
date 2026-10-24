package com.akira.island.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.tr
import com.akira.island.data.SettingsRepo
import com.akira.island.ui.Group
import com.akira.island.ui.GroupToggle
import com.akira.island.ui.Page
import com.akira.island.ui.Pos
import com.akira.island.ui.Tile
import com.akira.island.ui.Ui

@Composable
fun LyricsScreen(onBack: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    Page(tr("Текст песен", "Lyrics"), onBack) {
        Group {
            GroupToggle(Pos.Single, Icons.Rounded.LibraryMusic, tr("Текст песен", "Lyrics"),
                tr("Строка песни в раскрытом плеере заливается белым по мере пения", "The current line in the expanded player fills with white as it's sung"),
                s.lyricsEnabled, color = Tile.Pink) { v -> SettingsRepo.update { copy(lyricsEnabled = v) } }
        }
        Text(
            tr(
                "Текст ищется по названию, исполнителю и длительности трека: сначала на LRCLIB (lrclib.net), " +
                    "потом на NetEase — там много русской музыки. Если синхронного текста нет нигде, строки обычного текста " +
                    "раскладываются по длине песни. Пока идёт поиск, в плеере написано «Ищу текст песни…», " +
                    "если ничего не нашлось — «Текст песни не найден». На сервер уходят только название и исполнитель. " +
                    "Работает с любым плеером, который показывает уведомление с управлением: Spotify, YouTube Music, Яндекс Музыка, VK и т.д.",
                "Lyrics are looked up by title, artist and track length: first on LRCLIB (lrclib.net), then on NetEase, " +
                    "which has a lot of Russian music. If there are no synced lyrics anywhere, plain lyrics are spread over " +
                    "the length of the song. While searching, the player says “Looking for lyrics…”, and if nothing is found —" +
                    " “No lyrics found”. Only the title and artist are sent to the server. Works with any player that shows a " +
                    "notification with controls: Spotify, YouTube Music, Yandex Music, VK and others.",
            ),
            color = Ui.Sub,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ui.Card).padding(20.dp),
        )
    }
}
