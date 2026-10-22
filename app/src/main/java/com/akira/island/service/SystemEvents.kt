package com.akira.island.service

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.AirplanemodeInactive
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HeadsetOff
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PowerOff
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.UsbOff
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.akira.island.core.BatteryInfo
import com.akira.island.core.Controls
import com.akira.island.core.IslandBus
import com.akira.island.core.Notice
import com.akira.island.core.Tints
import com.akira.island.data.SettingsRepo

/** Системные события, которые остров показывает короткими уведомлениями. */
class SystemEvents(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val startedAt = SystemClock.elapsedRealtime()
    private val audio = context.getSystemService(AudioManager::class.java)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val s get() = SettingsRepo.current

    private var lowWarned = false
    private var usbConnected: Boolean? = null
    private var brightnessMode = -1
    private val headsetBattery = HashMap<String, Int>()
    private val wifiNetworks = HashSet<Network>()

    /** Первые секунды после запуска система присылает текущее состояние — это не события. */
    private val warmingUp get() = SystemClock.elapsedRealtime() - startedAt < 3000

    private fun notice(enabled: Boolean, n: Notice) {
        if (enabled && !warmingUp) IslandBus.post(n)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) = handle(intent, isInitialStickyBroadcast)
    }

    private val wifiCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            main.post {
                val wasEmpty = wifiNetworks.isEmpty()
                wifiNetworks += network
                if (wasEmpty) notice(s.evWifiOn, Notice("Wi-Fi", "Подключён", Icons.Rounded.Wifi, Tints.Blue))
            }
        }

        override fun onLost(network: Network) {
            main.post {
                if (wifiNetworks.remove(network) && wifiNetworks.isEmpty()) {
                    notice(s.evWifiOff, Notice("Wi-Fi", "Отключён", Icons.Rounded.WifiOff, Tints.Gray))
                }
            }
        }
    }

    private val brightnessObserver = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) {
            val mode = Settings.System.getInt(
                context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0,
            )
            if (mode == brightnessMode) return
            brightnessMode = mode
            val auto = mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            notice(
                s.evBrightness,
                Notice(
                    "Яркость",
                    if (auto) "Авто" else "Вручную",
                    if (auto) Icons.Rounded.BrightnessAuto else Icons.Rounded.BrightnessMedium,
                    Tints.Yellow,
                ),
            )
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(VOLUME_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BT_BATTERY_CHANGED)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(USB_STATE)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)

        brightnessMode = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE), false, brightnessObserver,
        )

        runCatching {
            connectivity?.registerNetworkCallback(
                NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
                wifiCallback,
            )
        }
    }

    fun stop() {
        runCatching { context.unregisterReceiver(receiver) }
        runCatching { context.contentResolver.unregisterContentObserver(brightnessObserver) }
        runCatching { connectivity?.unregisterNetworkCallback(wifiCallback) }
    }

    private fun handle(intent: Intent, initialSticky: Boolean) {
        when (intent.action) {
            Intent.ACTION_BATTERY_CHANGED -> onBattery(intent)

            Intent.ACTION_POWER_CONNECTED -> {
                val level = IslandBus.battery.value.level
                notice(
                    s.evChargeStart,
                    Notice("Зарядка", if (level >= 0) "$level%" else null, Icons.Rounded.Bolt, Tints.Green,
                        battery = level.takeIf { it >= 0 }),
                )
            }

            Intent.ACTION_POWER_DISCONNECTED -> {
                val level = IslandBus.battery.value.level
                notice(
                    s.evChargeStop,
                    Notice("Отключено", if (level >= 0) "$level%" else null, Icons.Rounded.PowerOff, Tints.Orange,
                        battery = level.takeIf { it >= 0 }),
                )
            }

            Intent.ACTION_USER_PRESENT ->
                notice(s.evUnlock, Notice("", null, Icons.Rounded.LockOpen, Color.White))

            Intent.ACTION_SCREEN_OFF -> Controls.collapse()

            Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                val on = intent.getBooleanExtra("state", false)
                if (on) notice(s.evAirplaneOn, Notice("Режим полёта", "Вкл", Icons.Rounded.AirplanemodeActive, Tints.Orange))
                else notice(s.evAirplaneOff, Notice("Режим полёта", "Выкл", Icons.Rounded.AirplanemodeInactive, Tints.Gray))
            }

            AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                val n = when (intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1)) {
                    AudioManager.RINGER_MODE_NORMAL -> Notice("Звонок", "Звук", Icons.AutoMirrored.Rounded.VolumeUp, Tints.Blue)
                    AudioManager.RINGER_MODE_VIBRATE -> Notice("Звонок", "Вибрация", Icons.Rounded.Vibration, Tints.Purple)
                    AudioManager.RINGER_MODE_SILENT -> Notice("Без звука", "Вкл", Icons.Rounded.NotificationsOff, Tints.Red)
                    else -> null
                }
                if (n != null) notice(s.evRinger, n)
            }

            VOLUME_CHANGED -> {
                if (intent.getIntExtra(EXTRA_STREAM, -1) != AudioManager.STREAM_MUSIC) return
                val value = intent.getIntExtra(EXTRA_VALUE, -1)
                val prev = intent.getIntExtra(EXTRA_PREV_VALUE, -1)
                val a = audio ?: return
                if (value < 0 || value == prev || !s.cardVolume || !a.isMusicActive) return
                IslandBus.showVolume(value, a.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
            }

            BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    ?: return
                // Без разрешения BLUETOOTH_CONNECT имени и класса устройства не узнать
                val name = runCatching { device.name }.getOrNull()
                val isAudio = runCatching {
                    device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO
                }.getOrDefault(true)
                if (!isAudio) return
                if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED) {
                    val battery = headsetBattery[device.address]
                    notice(
                        s.evHeadsetOn,
                        Notice(name ?: "Наушники", battery?.let { "$it%" } ?: "Подключено", Icons.Rounded.Headphones, Tints.Green,
                            ring = battery?.let { it / 100f }),
                    )
                } else {
                    headsetBattery.remove(device.address)
                    notice(s.evHeadsetOff, Notice(name ?: "Наушники", "Отключено", Icons.Rounded.HeadsetOff, Tints.Gray))
                }
            }

            BT_BATTERY_CHANGED -> {
                val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    ?: return
                val level = intent.getIntExtra(EXTRA_BT_BATTERY, -1)
                if (level >= 0) headsetBattery[device.address] = level
            }

            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                val dev = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                notice(s.evUsbOn, Notice(dev?.productName ?: "USB-устройство", "Подключено", Icons.Rounded.Usb, Tints.Teal))
            }

            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                val dev = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                notice(s.evUsbOff, Notice(dev?.productName ?: "USB-устройство", "Отключено", Icons.Rounded.UsbOff, Tints.Gray))
            }

            USB_STATE -> {
                val connected = intent.getBooleanExtra("connected", false)
                val prev = usbConnected
                usbConnected = connected
                if (initialSticky || prev == null || prev == connected) return
                if (connected) notice(s.evUsbOn, Notice("USB", "Подключено", Icons.Rounded.Usb, Tints.Teal))
                else notice(s.evUsbOff, Notice("USB", "Отключено", Icons.Rounded.UsbOff, Tints.Gray))
            }
        }
    }

    private fun onBattery(intent: Intent) {
        val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val level = if (raw >= 0) raw * 100 / scale else -1
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val charging = plugged != 0 &&
            (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
        IslandBus.battery.value = BatteryInfo(level, charging)

        if (charging || level > 5) {
            lowWarned = false
        } else if (level >= 0 && !lowWarned) {
            lowWarned = true
            notice(s.evLowBattery, Notice("Низкий заряд", "$level%", Icons.Rounded.BatteryAlert, Tints.Red, battery = level))
        }
    }

    private companion object {
        // Скрытые, но давно стабильные широковещательные сообщения системы
        const val VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        const val EXTRA_STREAM = "android.media.EXTRA_VOLUME_STREAM_TYPE"
        const val EXTRA_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE"
        const val EXTRA_PREV_VALUE = "android.media.EXTRA_PREV_VOLUME_STREAM_VALUE"
        const val BT_BATTERY_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        const val EXTRA_BT_BATTERY = "android.bluetooth.device.extra.BATTERY_LEVEL"
        const val USB_STATE = "android.hardware.usb.action.USB_STATE"
    }
}
