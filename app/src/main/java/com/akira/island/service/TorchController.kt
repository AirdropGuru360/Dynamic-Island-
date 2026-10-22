package com.akira.island.service

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.akira.island.data.SettingsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.akira.island.core.Controls
import com.akira.island.core.IslandBus
import com.akira.island.core.TorchState

class TorchController(context: Context) {
    private val cm = context.getSystemService(CameraManager::class.java)
    private val states = HashMap<String, Boolean>()
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collapseJob: Job? = null

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            states[cameraId] = enabled
            publish()
        }

        override fun onTorchStrengthLevelChanged(cameraId: String, newStrengthLevel: Int) {
            publish()
        }
    }

    fun start() {
        cm ?: return
        runCatching { cm.registerTorchCallback(callback, handler) }
        Controls.torchOff = {
            states.filterValues { it }.keys.forEach { id -> runCatching { cm.setTorchMode(id, false) } }
        }
        Controls.torchOn = {
            flashCamera()?.let { id -> runCatching { cm.setTorchMode(id, true) } }
        }
        Controls.torchLevel = { level ->
            val id = IslandBus.torch.value.cameraId
            if (id != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && level >= 1) {
                runCatching { cm.turnOnTorchWithStrengthLevel(id, level) }
            }
        }
    }

    fun stop() {
        scope.cancel()
        runCatching { cm?.unregisterTorchCallback(callback) }
        Controls.torchOn = {}
        Controls.torchOff = {}
        Controls.torchLevel = {}
        IslandBus.torch.value = TorchState()
    }

    private fun flashCamera(): String? = cm?.let { m ->
        runCatching {
            m.cameraIdList.firstOrNull { id ->
                m.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }

    private fun publish() {
        val id = states.entries.firstOrNull { it.value }?.key
        val wasOn = IslandBus.torch.value.on
        if (id == null || cm == null) {
            IslandBus.torch.value = TorchState()
            return
        }
        var level = 1
        var max = 1
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                max = cm.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
                level = cm.getTorchStrengthLevel(id)
            }
        }
        IslandBus.torch.value = TorchState(true, id, level, max)
        if (!wasOn) showCard()
    }

    /** Как на iPhone: включили фонарик — остров раскрывается в карточку с лучом и через пару секунд сворачивается. */
    private fun showCard() {
        val s = SettingsRepo.current
        if (!s.cardTorch || !s.enabled || IslandBus.expanded.value) return
        Controls.expand("torch")
        val opened = SystemClock.elapsedRealtime()
        IslandBus.lastTouch = opened
        collapseJob?.cancel()
        collapseJob = scope.launch {
            // Пока пальцем крутят яркость — не сворачиваем
            while (SystemClock.elapsedRealtime() - IslandBus.lastTouch < CARD_MS) {
                delay(CARD_MS - (SystemClock.elapsedRealtime() - IslandBus.lastTouch))
            }
            if (IslandBus.focus.value == "torch") Controls.collapse()
        }
    }
}

private const val CARD_MS = 2600L
