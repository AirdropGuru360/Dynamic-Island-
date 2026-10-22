package com.akira.island.island

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.akira.island.R

/**
 * Иконки в стиле iOS — набор Ionicons (MIT, https://ionic.io/ionicons), переведённый в векторы Android.
 * В острове используются они, в приложении настроек — Material.
 */
object Ios {
    private val cache = HashMap<Int, ImageVector>()

    /** Для кода вне Compose: службы и события. */
    fun load(context: Context, @DrawableRes id: Int): ImageVector = synchronized(cache) {
        cache.getOrPut(id) { ImageVector.vectorResource(null, context.resources, id) }
    }

    val airplane @Composable get() = ImageVector.vectorResource(R.drawable.ios_airplane)
    val alarm @Composable get() = ImageVector.vectorResource(R.drawable.ios_alarm)
    val add @Composable get() = ImageVector.vectorResource(R.drawable.ios_add)
    val remove @Composable get() = ImageVector.vectorResource(R.drawable.ios_remove)
    val download @Composable get() = ImageVector.vectorResource(R.drawable.ios_arrow_down_circle)
    val batteryCharging @Composable get() = ImageVector.vectorResource(R.drawable.ios_battery_charging)
    val batteryFull @Composable get() = ImageVector.vectorResource(R.drawable.ios_battery_full)
    val call @Composable get() = ImageVector.vectorResource(R.drawable.ios_call)
    val chat @Composable get() = ImageVector.vectorResource(R.drawable.ios_chatbubble_ellipses)
    val close @Composable get() = ImageVector.vectorResource(R.drawable.ios_close)
    val flash @Composable get() = ImageVector.vectorResource(R.drawable.ios_flash)
    val flashlight @Composable get() = ImageVector.vectorResource(R.drawable.ios_flashlight)
    val headset @Composable get() = ImageVector.vectorResource(R.drawable.ios_headset)
    val music @Composable get() = ImageVector.vectorResource(R.drawable.ios_musical_notes)
    val bell @Composable get() = ImageVector.vectorResource(R.drawable.ios_notifications)
    val pause @Composable get() = ImageVector.vectorResource(R.drawable.ios_pause)
    val play @Composable get() = ImageVector.vectorResource(R.drawable.ios_play)
    val back @Composable get() = ImageVector.vectorResource(R.drawable.ios_play_back)
    val forward @Composable get() = ImageVector.vectorResource(R.drawable.ios_play_forward)
    val airplay @Composable get() = ImageVector.vectorResource(R.drawable.ios_radio)
    val refresh @Composable get() = ImageVector.vectorResource(R.drawable.ios_refresh)
    val stop @Composable get() = ImageVector.vectorResource(R.drawable.ios_stop)
    val stopwatch @Composable get() = ImageVector.vectorResource(R.drawable.ios_stopwatch)
    val sunny @Composable get() = ImageVector.vectorResource(R.drawable.ios_sunny)
    val timer @Composable get() = ImageVector.vectorResource(R.drawable.ios_timer)
    val volumeHigh @Composable get() = ImageVector.vectorResource(R.drawable.ios_volume_high)
    val volumeLow @Composable get() = ImageVector.vectorResource(R.drawable.ios_volume_low)
    val volumeOff @Composable get() = ImageVector.vectorResource(R.drawable.ios_volume_mute)
}
