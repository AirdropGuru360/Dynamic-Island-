package com.akira.island.service

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.akira.island.core.Controls
import com.akira.island.core.IslandBus
import com.akira.island.core.TorchState

class TorchController(context: Context) {
    private val cm = context.getSystemService(CameraManager::class.java)
    private val states = HashMap<String, Boolean>()
    private val handler = Handler(Looper.getMainLooper())

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
    }
}
