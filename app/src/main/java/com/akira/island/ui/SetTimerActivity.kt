package com.akira.island.ui

import android.app.Activity
import android.os.Bundle
import android.provider.AlarmClock
import com.akira.island.core.Controls
import com.akira.island.data.SettingsRepo

/**
 * «Поставь таймер на 5 минут» от голосового помощника, виджета или другого приложения: если выбрать
 * «Динамический остров», таймер сразу запускается в острове — без «Часов» и без задержки, с его кнопками.
 */
class SetTimerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsRepo.init(this)
        // По правилам Android таймер — от 1 секунды до суток
        val seconds = (intent?.getIntExtra(AlarmClock.EXTRA_LENGTH, 0) ?: 0).coerceAtMost(86_400)
        if (seconds > 0) Controls.startTimerSeconds(seconds) else Controls.startTimer(SettingsRepo.current.timerMinutes)
        Controls.expand("timer")
        finish()
    }
}
