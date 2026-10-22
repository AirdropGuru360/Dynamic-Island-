package com.akira.island.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.AirplanemodeInactive
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PowerOff
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.UsbOff
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import com.akira.island.ui.Page
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.ToggleCard

/** События — как на третьем скриншоте. */
@Composable
fun EventsScreen(onBack: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    fun set(block: IslandSettings.() -> IslandSettings) = SettingsRepo.update(block)

    Page("События", onBack) {
        SectionHeader("Батарея")
        ToggleCard(Icons.Rounded.Bolt, "Зарядка началась", "Когда вы подключаете зарядное устройство", s.evChargeStart) { set { copy(evChargeStart = it) } }
        ToggleCard(Icons.Rounded.PowerOff, "Зарядка остановлена", "Когда вы отключаете зарядное устройство", s.evChargeStop) { set { copy(evChargeStop = it) } }
        ToggleCard(Icons.Rounded.BatteryAlert, "Предупреждение о низком заряде батареи", "Когда заряд батареи падает до 5%", s.evLowBattery) { set { copy(evLowBattery = it) } }

        SectionHeader("Wi-Fi")
        ToggleCard(Icons.Rounded.Wifi, "Wi-Fi подключён", "Когда сеть Wi-Fi подключена", s.evWifiOn) { set { copy(evWifiOn = it) } }
        ToggleCard(Icons.Rounded.WifiOff, "Wi-Fi отключён", "Когда сеть Wi-Fi отключена", s.evWifiOff) { set { copy(evWifiOff = it) } }

        SectionHeader("Наушники")
        ToggleCard(Icons.Rounded.BluetoothConnected, "Наушники подключены", "Когда вы подключаете их через Bluetooth", s.evHeadsetOn) { set { copy(evHeadsetOn = it) } }
        ToggleCard(Icons.Rounded.BluetoothDisabled, "Наушники отключены", "Когда вы отключаете их через Bluetooth", s.evHeadsetOff) { set { copy(evHeadsetOff = it) } }

        SectionHeader("Режим полёта")
        ToggleCard(Icons.Rounded.AirplanemodeActive, "Режим полёта включён", "Показывается, когда режим полёта включён", s.evAirplaneOn) { set { copy(evAirplaneOn = it) } }
        ToggleCard(Icons.Rounded.AirplanemodeInactive, "Режим полёта выключен", "Показывается, когда режим полёта выключен", s.evAirplaneOff) { set { copy(evAirplaneOff = it) } }

        SectionHeader("Громкость")
        ToggleCard(Icons.Rounded.Vibration, "Режим громкости", "Когда изменяется режим громкости", s.evRinger) { set { copy(evRinger = it) } }

        SectionHeader("Экран")
        ToggleCard(Icons.Rounded.BrightnessAuto, "Режим яркости", "При включении или выключении адаптивной яркости", s.evBrightness) { set { copy(evBrightness = it) } }

        SectionHeader("USB-устройство")
        ToggleCard(Icons.Rounded.Usb, "USB-устройство подключено", "Когда вы подключаете USB-устройство", s.evUsbOn) { set { copy(evUsbOn = it) } }
        ToggleCard(Icons.Rounded.UsbOff, "USB-устройство отключено", "Когда вы отключаете USB-устройство", s.evUsbOff) { set { copy(evUsbOff = it) } }

        SectionHeader("Другое")
        ToggleCard(Icons.Rounded.LockOpen, "Разблокировка устройства", "После разблокировки устройства", s.evUnlock) { set { copy(evUnlock = it) } }
    }
}
