package com.akira.island.ui

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.WindowManager
import com.akira.island.core.IslandBus
import kotlin.math.abs
import kotlin.math.roundToInt

/** Где камера: прямоугольник выреза в пикселях экрана и ширина экрана. */
data class CameraHole(val rect: Rect, val screenWidth: Int)

object Cutout {
    /**
     * Ищем вырез камеры по очереди: у окна острова (оно рисуется поверх выреза), у дисплея
     * и у окна приложения. Берём прямоугольник у верхнего края, ближайший к середине.
     */
    fun find(activity: Activity): CameraHole? {
        val wm = activity.getSystemService(WindowManager::class.java)
        val width = screenWidth(activity, wm)
        val candidates = buildList {
            IslandBus.cutout.value?.let { add(it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) activity.display else wm?.defaultDisplay
                display?.cutout?.boundingRects?.let { addAll(it) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                wm?.currentWindowMetrics?.windowInsets?.displayCutout?.boundingRects?.let { addAll(it) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                activity.window?.decorView?.rootWindowInsets?.displayCutout?.boundingRects?.let { addAll(it) }
            }
        }
        val top = candidates.filter { !it.isEmpty && it.top < it.height() && it.top < width / 4 }
        val best = top.minByOrNull { abs(it.exactCenterX() - width / 2f) } ?: return null
        return CameraHole(Rect(best), width)
    }

    private fun screenWidth(context: Context, wm: WindowManager?): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm != null) {
            wm.maximumWindowMetrics.bounds.width()
        } else {
            context.resources.displayMetrics.widthPixels
        }

    /** Пиксели → dp с округлением. */
    fun dp(context: Context, px: Float): Int = (px / context.resources.displayMetrics.density).roundToInt()
}
