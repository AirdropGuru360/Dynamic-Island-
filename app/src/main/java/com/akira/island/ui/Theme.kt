package com.akira.island.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Тёмная тема с лавандовым акцентом, как на скриншотах. */
object Ui {
    val Bg = Color(0xFF16151B)
    val Card = Color(0xFF27262D)
    val IconBg = Color(0xFF35343D)
    val Primary = Color(0xFFC6C3F7)
    val OnPrimary = Color(0xFF2F2D4C)
    val Text = Color(0xFFE6E1E9)
    val Sub = Color(0xFFA9A6B1)
    val Divider = Color(0xFF47464F)
    val Nav = Color(0xFF25242B)
    val Pink = Color(0xFFFBDDF4)
    val OnPink = Color(0xFF5B3A56)
    val Outline = Color(0xFF908D99)
}

@Composable
fun IslandTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Ui.Primary,
            onPrimary = Ui.OnPrimary,
            secondary = Ui.Primary,
            background = Ui.Bg,
            onBackground = Ui.Text,
            surface = Ui.Card,
            onSurface = Ui.Text,
            surfaceVariant = Ui.IconBg,
            onSurfaceVariant = Ui.Sub,
            surfaceContainerHigh = Ui.Card,
            outline = Ui.Outline,
        ),
        content = content,
    )
}
