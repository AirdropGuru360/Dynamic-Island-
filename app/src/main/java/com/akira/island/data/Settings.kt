package com.akira.island.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class ExpandStyle(val title: String) {
    FRUIT("Как Фрукт"),
    SPRING("Пружина"),
    SMOOTH("Плавно"),
    INSTANT("Без анимации"),
}

@Serializable
enum class Elasticity(val title: String, val damping: Float) {
    NONE("Без упругости", 1f),
    LOW("Слабая упругость", 0.8f),
    MEDIUM("Средняя упругость", 0.62f),
    HIGH("Сильная упругость", 0.48f),
}

@Serializable
enum class TapAction(val title: String) {
    EXPAND("Раскрыть капсулу"),
    OPEN_APP("Открыть приложение"),
    PLAY_PAUSE("Пауза / воспроизведение"),
    NEXT_TRACK("Следующий трек"),
    NOTHING("Ничего"),
}

@Serializable
data class IslandSettings(
    val enabled: Boolean = true,

    // Динамические карточки
    val cardMedia: Boolean = true,
    val cardTorch: Boolean = true,
    val cardVolume: Boolean = true,
    val cardProgress: Boolean = true,
    val cardTimer: Boolean = true,
    val cardCharging: Boolean = true,
    val cardCall: Boolean = true,

    // События
    val evChargeStart: Boolean = true,
    val evChargeStop: Boolean = true,
    val evLowBattery: Boolean = true,
    val evWifiOn: Boolean = true,
    val evWifiOff: Boolean = true,
    val evHeadsetOn: Boolean = true,
    val evHeadsetOff: Boolean = true,
    val evAirplaneOn: Boolean = true,
    val evAirplaneOff: Boolean = true,
    val evRinger: Boolean = true,
    val evBrightness: Boolean = true,
    val evUsbOn: Boolean = true,
    val evUsbOff: Boolean = true,
    val evUnlock: Boolean = false,

    // Размер и положение, dp — как у iPhone: капсула 126×37, отступ сверху 11
    val width: Int = 126,
    val height: Int = 37,
    val offsetX: Int = 0,
    val offsetY: Int = 11,
    // Нет в старых настройках → 1, тогда размеры переносятся на новые
    val layoutVersion: Int = 1,

    // Цвета
    val color: Long = 0xFF000000,
    val outline: Boolean = false,
    val outlineColor: Long = 0xFF3A3A40,
    val outlineWidth: Float = 1f,
    val outlineFromArt: Boolean = true,
    val shadow: Boolean = false,

    // Анимации
    val expandStyle: ExpandStyle = ExpandStyle.FRUIT,
    val durationPercent: Int = 35,
    val elasticity: Elasticity = Elasticity.HIGH,
    val reduceEffects: Boolean = false,
    val haptics: Boolean = true,

    // Жесты
    val tapAction: TapAction = TapAction.EXPAND,
    val doubleTapAction: TapAction = TapAction.NOTHING,
    val longPressAction: TapAction = TapAction.OPEN_APP,
    val swipeTracks: Boolean = true,
    val swipeExpand: Boolean = true,

    // Текст песен
    val lyricsEnabled: Boolean = true,
    val lyricsInCapsule: Boolean = false,

    // Поведение
    val hideIdle: Boolean = false,
    val hideInLandscape: Boolean = true,
    val hideWhenPlayerOpen: Boolean = true,
    val noticeDurationMs: Int = 2500,
    val timerMinutes: Int = 5,
)

object SettingsRepo {
    private const val KEY = "settings_v1"
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private var prefs: SharedPreferences? = null
    private val _state = MutableStateFlow(IslandSettings())
    val state: StateFlow<IslandSettings> = _state.asStateFlow()
    val current: IslandSettings get() = _state.value

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("island", Context.MODE_PRIVATE)
        prefs = p
        p.getString(KEY, null)?.let { raw ->
            runCatching { json.decodeFromString<IslandSettings>(raw) }.onSuccess { _state.value = it }
        }
        // 1.1 и раньше: размеры по умолчанию были меньше, чем у iPhone — переносим, если их не трогали
        val cur = _state.value
        if (cur.layoutVersion < 2) {
            update {
                copy(
                    width = if (width == 96) 126 else width,
                    height = if (height == 34) 37 else height,
                    offsetY = if (offsetY == 8) 11 else offsetY,
                    layoutVersion = 2,
                )
            }
        }
    }

    fun update(block: IslandSettings.() -> IslandSettings) {
        val next = _state.value.block()
        if (next == _state.value) return
        _state.value = next
        prefs?.edit()?.putString(KEY, json.encodeToString(next))?.apply()
    }

    fun reset() = update { IslandSettings() }

    fun resetAnimations() = update {
        val d = IslandSettings()
        copy(
            expandStyle = d.expandStyle,
            durationPercent = d.durationPercent,
            elasticity = d.elasticity,
            reduceEffects = d.reduceEffects,
        )
    }
}
