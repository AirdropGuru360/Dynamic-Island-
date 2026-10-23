package com.akira.island.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.AirplanemodeInactive
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Nfc
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
import com.akira.island.ui.Tile
import com.akira.island.ui.ToggleCard

/** События — как на третьем скриншоте. */
@Composable
fun EventsScreen(onBack: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    fun set(block: IslandSettings.() -> IslandSettings) = SettingsRepo.update(block)

    Page("События", onBack) {
        SectionHeader("Батарея")
        ToggleCard(Icons.Rounded.Bolt, "Зарядка началась", "Когда вы подключаете зарядное устройство", s.evChargeStart, color = Tile.Green) { set { copy(evChargeStart = it) } }
        ToggleCard(Icons.Rounded.PowerOff, "Зарядка остановлена", "Когда вы отключаете зарядное устройство", s.evChargeStop, color = Tile.Gray) { set { copy(evChargeStop = it) } }
        ToggleCard(Icons.Rounded.BatteryAlert, "Предупреждение о низком заряде батареи", "Когда заряд батареи падает до 5%", s.evLowBattery, color = Tile.Red) { set { copy(evLowBattery = it) } }

        SectionHeader("Wi-Fi")
        ToggleCard(Icons.Rounded.Wifi, "Wi-Fi подключён", "Когда сеть Wi-Fi подключена", s.evWifiOn, color = Tile.Blue) { set { copy(evWifiOn = it) } }
        ToggleCard(Icons.Rounded.WifiOff, "Wi-Fi отключён", "Когда сеть Wi-Fi отключена", s.evWifiOff, color = Tile.Gray) { set { copy(evWifiOff = it) } }

        SectionHeader("Наушники")
        ToggleCard(Icons.Rounded.BluetoothConnected, "Наушники подключены", "Когда вы подключаете их через Bluetooth", s.evHeadsetOn, color = Tile.Indigo) { set { copy(evHeadsetOn = it) } }
        ToggleCard(Icons.Rounded.BluetoothDisabled, "Наушники отключены", "Когда вы отключаете их через Bluetooth", s.evHeadsetOff, color = Tile.Gray) { set { copy(evHeadsetOff = it) } }

        SectionHeader("Режим полёта")
        ToggleCard(Icons.Rounded.AirplanemodeActive, "Режим полёта включён", "Показывается, когда режим полёта включён", s.evAirplaneOn, color = Tile.Orange) { set { copy(evAirplaneOn = it) } }
        ToggleCard(Icons.Rounded.AirplanemodeInactive, "Режим полёта выключен", "Показывается, когда режим полёта выключен", s.evAirplaneOff, color = Tile.Gray) { set { copy(evAirplaneOff = it) } }

        SectionHeader("Громкость")
        ToggleCard(Icons.Rounded.Vibration, "Режим громкости", "Когда изменяется режим громкости", s.evRinger, color = Tile.Pink) { set { copy(evRinger = it) } }

        SectionHeader("Экран")
        ToggleCard(Icons.Rounded.BrightnessAuto, "Режим яркости", "При включении или выключении адаптивной яркости", s.evBrightness, color = Tile.Yellow) { set { copy(evBrightness = it) } }

        SectionHeader("USB-устройство")
        ToggleCard(Icons.Rounded.Usb, "USB-устройство подключено", "Когда вы подключаете USB-устройство", s.evUsbOn, color = Tile.Teal) { set { copy(evUsbOn = it) } }
        ToggleCard(Icons.Rounded.UsbOff, "USB-устройство отключено", "Когда вы отключаете USB-устройство", s.evUsbOff, color = Tile.Gray) { set { copy(evUsbOff = it) } }

        SectionHeader("Оплата")
        ToggleCard(Icons.Rounded.Nfc, "Приложите к терминалу",
            "Когда открываете Google Кошелёк, Т-Банк, Альфа-Банк, Сбер, ВТБ или другое приложение оплаты — карта и волны, как у Apple Pay",
            s.evPayReady, color = Tile.Blue) { set { copy(evPayReady = it) } }
        ToggleCard(Icons.Rounded.CheckCircle, "Оплачено",
            "Когда банк или кошелёк присылает уведомление об оплате — зелёная галочка и сумма",
            s.evPaySuccess, color = Tile.Green) { set { copy(evPaySuccess = it) } }

        SectionHeader("Режимы")
        ToggleCard(Icons.Rounded.DoNotDisturbOn, "Не беспокоить", "При включении и выключении режима «Не беспокоить»", s.evDnd, color = Tile.Indigo) { set { copy(evDnd = it) } }

        SectionHeader("Другое")
        ToggleCard(Icons.Rounded.LockOpen, "Разблокировка устройства", "После разблокировки устройства", s.evUnlock, color = Tile.Purple) { set { copy(evUnlock = it) } }
    }
}
