package com.akira.island.service

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.WindowManager

/**
 * Прозрачный экран без содержимого: включает дисплей, когда звонит таймер острова, и сразу закрывается.
 * Запасной способ (см. ScreenWaker) — обычно экран включает само окно острова.
 */
class WakeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        // Экран включается, как только окно показано; дальше его держит остров со звонящим таймером
        window.decorView.postDelayed({ finish() }, 600)
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
