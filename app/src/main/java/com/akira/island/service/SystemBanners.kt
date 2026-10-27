package com.akira.island.service

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import com.akira.island.core.tr
import com.akira.island.data.SettingsRepo
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Всплывающие плашки системы, которые дублируют остров: входящий вызов, сигнал таймера и будильника,
 * батарея. Убрать чужую плашку приложение не может — её выключают в настройках уведомлений,
 * а остров показывает событие как раньше. Здесь запоминаем, какой канал прислал такую плашку,
 * чтобы открыть в настройках сразу его.
 */
object SystemBanners {
    /** Канал уведомления, приславший плашку, и звонок ли это */
    data class Seen(val channel: String, val call: Boolean)

    val seen = MutableStateFlow<Map<String, Seen>>(emptyMap())

    /** Всплывает ли у мессенджера своя плашка — по последнему сообщению (важность канала «высокая») */
    val popUp = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    const val SYSTEM_UI = "com.android.systemui"
    const val ANDROID = "android"

    fun onPosted(sbn: StatusBarNotification, incomingCall: Boolean) {
        val n = sbn.notification ?: return
        if (!incomingCall && n.category != Notification.CATEGORY_ALARM) return
        val channel = n.channelId ?: return
        val item = Seen(channel, incomingCall)
        if (seen.value[sbn.packageName] != item) seen.value = seen.value + (sbn.packageName to item)
    }

    /**
     * Сообщение мессенджера показано в острове. Его канал запоминается в настройках — после перезапуска
     * список в «Системных плашках» не пустой; запись — только когда канал новый.
     */
    fun onMessage(pkg: String, channel: String?, popsUp: Boolean) {
        if (popUp.value[pkg] != popsUp) popUp.value = popUp.value + (pkg to popsUp)
        if (channel != null && SettingsRepo.current.msgChannels[pkg] != channel) {
            SettingsRepo.update { copy(msgChannels = msgChannels + (pkg to channel)) }
        }
    }

    /** Звонилка по умолчанию и приложения, от которых уже приходили входящие. */
    fun callApps(context: Context, seen: Map<String, Seen>): List<String> {
        val dialer = runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()
        return (listOfNotNull(dialer) + seen.filterValues { it.call }.keys).distinct()
    }

    /** Приложения «Часы» — те, что умеют ставить таймер, и те, что уже присылали сигнал. */
    fun clockApps(context: Context, seen: Map<String, Seen>): List<String> {
        val timers = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(Intent(AlarmClock.ACTION_SET_TIMER), 0).map { it.activityInfo.packageName }
        }.getOrDefault(emptyList())
        return (timers + seen.filterValues { !it.call }.keys).distinct().filter { it != SYSTEM_UI && it != ANDROID && it != context.packageName }
    }

    fun label(context: Context, pkg: String): String = when (pkg) {
        SYSTEM_UI -> tr("Системный интерфейс", "System UI")
        ANDROID -> tr("Система Android", "Android System")
        else -> NotificationParser.appLabel(context, pkg)
    }

    /** Настройки уведомлений приложения [pkg] — сразу нужного канала, если он уже известен. */
    fun openSettings(context: Context, pkg: String) {
        val channel = seen.value[pkg]?.channel ?: SettingsRepo.current.msgChannels[pkg]
        val intent = if (channel != null) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_CHANNEL_ID, channel)
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        }
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isSuccess) return
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
