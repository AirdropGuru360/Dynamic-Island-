package com.akira.island.service

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.akira.island.core.ChronoInfo
import com.akira.island.core.IslandBus
import com.akira.island.core.ProgressInfo
import com.akira.island.data.SettingsRepo

/**
 * Доступ к уведомлениям даёт две вещи: медиасессии всех плееров
 * и уведомления с прогрессом или секундомером (загрузки, Часы, звонки).
 */
class NotifListener : NotificationListenerService() {
    private var media: MediaMonitor? = null

    override fun onListenerConnected() {
        SettingsRepo.init(this)
        media = MediaMonitor(applicationContext).also { it.start() }
        runCatching { activeNotifications?.forEach { NotificationParser.onPosted(this, it) } }
    }

    override fun onListenerDisconnected() {
        media?.stop()
        media = null
        IslandBus.progress.value = emptyMap()
        IslandBus.chrono.value = emptyMap()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        NotificationParser.onPosted(this, sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotificationParser.onRemoved(sbn.key)
    }
}

object NotificationParser {
    private val icons = HashMap<String, ImageBitmap?>()

    fun onPosted(context: Context, sbn: StatusBarNotification) {
        if (sbn.packageName == context.packageName) return
        val n = sbn.notification ?: return
        val ex = n.extras ?: return
        val isMedia = n.category == Notification.CATEGORY_TRANSPORT || ex.containsKey(Notification.EXTRA_MEDIA_SESSION)
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: appLabel(context, sbn.packageName)

        val max = ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val value = ex.getInt(Notification.EXTRA_PROGRESS, 0)
        val indeterminate = ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val ongoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0
        val hasProgress = !isMedia && ((max > 0 && value < max) || (indeterminate && ongoing))
        IslandBus.progress.value = IslandBus.progress.value.toMutableMap().apply {
            if (hasProgress) {
                put(sbn.key, ProgressInfo(sbn.key, sbn.packageName, title, icon(context, sbn.packageName), value, max, indeterminate, sbn.postTime))
            } else {
                remove(sbn.key)
            }
        }

        val chrono = !isMedia && ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false) && n.`when` > 0
        IslandBus.chrono.value = IslandBus.chrono.value.toMutableMap().apply {
            if (chrono) {
                val down = ex.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false)
                put(sbn.key, ChronoInfo(sbn.key, sbn.packageName, title, icon(context, sbn.packageName), n.`when`, down, sbn.postTime))
            } else {
                remove(sbn.key)
            }
        }
    }

    fun onRemoved(key: String) {
        if (key in IslandBus.progress.value) IslandBus.progress.value = IslandBus.progress.value - key
        if (key in IslandBus.chrono.value) IslandBus.chrono.value = IslandBus.chrono.value - key
    }

    fun icon(context: Context, pkg: String): ImageBitmap? = icons.getOrPut(pkg) {
        runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }

    private fun appLabel(context: Context, pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}
