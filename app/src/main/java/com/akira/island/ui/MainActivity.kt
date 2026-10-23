package com.akira.island.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FormatPaint
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.akira.island.update.UpdateChecker
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.akira.island.data.SettingsRepo
import com.akira.island.ui.screens.AboutScreen
import com.akira.island.ui.screens.AnimScreen
import com.akira.island.ui.screens.CapsuleScreen
import com.akira.island.ui.screens.LookScreen
import com.akira.island.ui.screens.DiagScreen
import com.akira.island.ui.screens.CardsScreen
import com.akira.island.ui.screens.ColorsScreen
import com.akira.island.ui.screens.EventsScreen
import com.akira.island.ui.screens.GeneralScreen
import com.akira.island.ui.screens.GesturesScreen
import com.akira.island.ui.screens.HomeScreen
import com.akira.island.ui.screens.InstructionScreen
import com.akira.island.ui.screens.LyricsScreen
import com.akira.island.ui.screens.SizeScreen

enum class Screen { CARDS, EVENTS, GESTURES, LYRICS, SIZE, COLORS, INSTRUCTION, ABOUT, DIAG, LOOK, ANIM }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsRepo.init(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent { IslandTheme { AppRoot() } }
    }
}

@Composable
private fun AppRoot() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var stack by remember { mutableStateOf(listOf<Screen>()) }
    var forward by remember { mutableStateOf(true) }
    val open: (Screen) -> Unit = {
        forward = true
        stack = stack + it
    }
    val back: () -> Unit = {
        forward = false
        stack = stack.dropLast(1)
    }
    BackHandler(enabled = stack.isNotEmpty(), onBack = back)

    // Раз в 6 часов спрашиваем GitHub, не вышла ли новая версия; после обновления — «Что нового»
    val context = LocalContext.current
    LaunchedEffect(Unit) { UpdateChecker.check(context.applicationContext, force = false) }
    WhatsNewDialog()

    Box(Modifier.fillMaxSize().background(Ui.Bg)) {
        val key = stack.lastOrNull()?.name ?: "tab$tab"
        AnimatedContent(
            targetState = key,
            transitionSpec = {
                if (targetState.startsWith("tab") && initialState.startsWith("tab")) {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(120))
                } else if (forward) {
                    (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 8 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 8 } + fadeIn()) togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
                }
            },
            label = "nav",
        ) { k ->
            when (k) {
                "tab0" -> HomeScreen(open)
                "tab1" -> CapsuleScreen(open)
                "tab2" -> GeneralScreen(open)
                Screen.CARDS.name -> CardsScreen(back) { open(Screen.INSTRUCTION) }
                Screen.EVENTS.name -> EventsScreen(back)
                Screen.GESTURES.name -> GesturesScreen(back)
                Screen.LYRICS.name -> LyricsScreen(back)
                Screen.SIZE.name -> SizeScreen(back)
                Screen.COLORS.name -> ColorsScreen(back)
                Screen.INSTRUCTION.name -> InstructionScreen(back)
                Screen.ABOUT.name -> AboutScreen(back)
                Screen.DIAG.name -> DiagScreen(back)
                Screen.LOOK.name -> LookScreen(back)
                Screen.ANIM.name -> AnimScreen(back)
            }
        }

        AnimatedVisibility(
            visible = stack.isEmpty(),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BottomBar(tab) { tab = it }
        }
    }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val icons = listOf(Icons.Rounded.FormatPaint, Icons.Rounded.Tune, Icons.Rounded.Settings)
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(bottom = 14.dp)
            .shadow(16.dp, CircleShape)
            .clip(CircleShape)
            .background(Ui.Nav)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icons.forEachIndexed { i, icon ->
            val on = i == selected
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(width = 72.dp, height = 56.dp)
                    .clip(CircleShape)
                    .background(if (on) Ui.Primary else Ui.Nav)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = if (on) Ui.OnPrimary else Ui.Text, modifier = Modifier.size(28.dp))
            }
        }
    }
}
