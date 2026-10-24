package com.akira.island.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.AirplanemodeInactive
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Nfc
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
import com.akira.island.ui.Group
import com.akira.island.ui.GroupToggle
import com.akira.island.ui.Page
import com.akira.island.ui.Pos
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.Tile

/** «События»: короткие плашки о том, что произошло, — по разделам. */
@Composable
fun EventsScreen(onBack: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    fun set(block: IslandSettings.() -> IslandSettings) = SettingsRepo.update(block)

    Page("События", onBack) {
        SectionHeader("Батарея")
        Group {
            GroupToggle(Pos.First, Icons.Rounded.Bolt, "Зарядка началась", null, s.evChargeStart, color = Tile.Green) { set { copy(evChargeStart = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.PowerOff, "Зарядка остановлена", null, s.evChargeStop, color = Tile.Gray) { set { copy(evChargeStop = it) } }
            GroupToggle(Pos.Last, Icons.Rounded.BatteryAlert, "Низкий заряд", "Когда заряд батареи падает до 5%",
                s.evLowBattery, color = Tile.Red) { set { copy(evLowBattery = it) } }
        }

        SectionHeader("Подключения")
        Group {
            GroupToggle(Pos.First, Icons.Rounded.Wifi, "Wi-Fi подключён", null, s.evWifiOn, color = Tile.Blue) { set { copy(evWifiOn = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.WifiOff, "Wi-Fi отключён", null, s.evWifiOff, color = Tile.Gray) { set { copy(evWifiOff = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.BluetoothConnected, "Наушники подключены", "По Bluetooth, с зарядом, если он известен",
                s.evHeadsetOn, color = Tile.Indigo) { set { copy(evHeadsetOn = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.BluetoothDisabled, "Наушники отключены", null, s.evHeadsetOff, color = Tile.Gray) { set { copy(evHeadsetOff = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.Usb, "USB-устройство подключено", null, s.evUsbOn, color = Tile.Teal) { set { copy(evUsbOn = it) } }
            GroupToggle(Pos.Last, Icons.Rounded.UsbOff, "USB-устройство отключено", null, s.evUsbOff, color = Tile.Gray) { set { copy(evUsbOff = it) } }
        }

        SectionHeader("Режимы")
        Group {
            GroupToggle(Pos.First, Icons.Rounded.AirplanemodeActive, "Режим полёта включён", null, s.evAirplaneOn, color = Tile.Orange) { set { copy(evAirplaneOn = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.AirplanemodeInactive, "Режим полёта выключен", null, s.evAirplaneOff, color = Tile.Gray) { set { copy(evAirplaneOff = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.DoNotDisturbOn, "Не беспокоить", "Луна и состояние при переключении",
                s.evDnd, color = Tile.Indigo) { set { copy(evDnd = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.Vibration, "Режим громкости", "Звонок, вибрация или без звука",
                s.evRinger, color = Tile.Pink) { set { copy(evRinger = it) } }
            GroupToggle(Pos.Last, Icons.Rounded.BrightnessAuto, "Режим яркости", "Когда включают или выключают адаптивную яркость",
                s.evBrightness, color = Tile.Yellow) { set { copy(evBrightness = it) } }
        }

        SectionHeader("Оплата")
        Group {
            GroupToggle(Pos.First, Icons.Rounded.Nfc, "Приложите к терминалу",
                "Когда на экране оплата телефоном — Mir Pay, T-Pay, SberPay, Яндекс Пэй и другие. Держится, пока не заплатите или не выйдете из приложения",
                s.evPayReady, color = Tile.Blue) { set { copy(evPayReady = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.AccountBalance, "Подсказка при открытии банка",
                "«К терминалу» сразу, как открыли банк или кошелёк, а не только на экране оплаты",
                s.payHintOnOpen, color = Tile.Gray) { set { copy(payHintOnOpen = it) } }
            GroupToggle(Pos.Last, Icons.Rounded.CheckCircle, "Оплачено",
                "Галочка и сумма — с экрана кошелька, из уведомления или СМС банка",
                s.evPaySuccess, color = Tile.Green) { set { copy(evPaySuccess = it) } }
        }

        SectionHeader("Другое")
        Group {
            GroupToggle(Pos.First, Icons.Rounded.Alarm, "Будильник поставлен", "Время подъёма и сколько осталось спать",
                s.evAlarmSet, color = Tile.Orange) { set { copy(evAlarmSet = it) } }
            GroupToggle(Pos.Last, Icons.Rounded.LockOpen, "Разблокировка устройства", "Замок открывается, когда вы разблокируете телефон",
                s.evUnlock, color = Tile.Purple) { set { copy(evUnlock = it) } }
        }
    }
}
