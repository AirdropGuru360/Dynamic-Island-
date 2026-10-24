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

/** Откуда берётся цвет кнопок «Ответить», «Пометить прочитанным» и отправки в карточке уведомления. */
@Serializable
enum class AccentMode(val title: String, val hint: String) {
    DEFAULT("Сиреневый", "Цвет приложения"),
    SYSTEM("Системный", "Цвет из обоев Android 12+ (Material You)"),
    ADAPTIVE("Из аватара", "Подбирается по аватару или значку приложения"),
    CUSTOM("Свой", "Любой цвет из набора"),
}

@Serializable
data class IslandSettings(
    val enabled: Boolean = true,

    // Динамические карточки
    val cardMedia: Boolean = true,
    val cardTorch: Boolean = true,
    val cardProgress: Boolean = true,
    val cardTimer: Boolean = true,
    val cardCharging: Boolean = true,
    val cardCall: Boolean = true,
    val cardMessages: Boolean = true,
    /** Кнопки «Ответить» и «Пометить прочитанным» в карточке уведомления */
    val msgActions: Boolean = true,
    /** Навигация, такси и доставка — «живые активности» из постоянных уведомлений */
    val cardLive: Boolean = true,
    /** Будильник: за 12 часов до него — время подъёма и сколько осталось спать */
    val cardAlarm: Boolean = true,
    /** Кнопки острова для таймера «Часов» нажимают кнопки самих «Часов» (без уведомлений — открывая их на миг) */
    val clockAutoTap: Boolean = true,
    /** «Поставь таймер» от помощника и виджетов можно отдать острову */
    val timerFromAssistant: Boolean = true,
    /** Звонит таймер, будильник или входящий вызов — остров включает экран и не даёт ему погаснуть */
    val wakeForAlerts: Boolean = true,
    /** Особые песни остров встречает эффектами в такт музыке */
    val easterEggs: Boolean = true,

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
    val evDnd: Boolean = true,
    /** Открыли банковское приложение или кошелёк — «Приложите к терминалу» */
    val evPayReady: Boolean = true,
    /** Пришло уведомление об оплате — «Оплачено» с галочкой */
    val evPaySuccess: Boolean = true,
    /** Поставили будильник — плашка со временем подъёма */
    val evAlarmSet: Boolean = true,

    // Размер и положение, dp — как у iPhone: капсула 126×37, отступ сверху 11
    val width: Int = 126,
    val height: Int = 37,
    val offsetX: Int = 0,
    val offsetY: Int = 11,
    /** Свободное место над камерой посередине капсулы, куда не заходит содержимое */
    val cameraGap: Int = 34,
    /** Насколько капсула с активностью (музыка, таймер, зарядка) толще пустой */
    val activeExtra: Int = 6,
    /** На сколько свёрнутая капсула с активностью (навигатор, фонарик, музыка) шире спокойной; 0 — такая же */
    val activeWidth: Int = 48,
    // Нет в старых настройках → 1, тогда размеры переносятся на новые
    val layoutVersion: Int = 1,

    // Цвета
    val color: Long = 0xFF000000,
    val outline: Boolean = false,
    val outlineColor: Long = 0xFF3A3A40,
    val outlineWidth: Float = 1f,
    val outlineFromArt: Boolean = true,
    val shadow: Boolean = false,

    // Цвет кнопок в карточке уведомления
    val accentMode: AccentMode = AccentMode.DEFAULT,
    val accentColor: Long = 0xFF0A84FF,

    // Обновления из GitHub Releases
    val autoUpdateCheck: Boolean = true,
    /** Версия, от которой отказались, — про неё больше не напоминаем */
    val skippedVersion: String = "",
    /** Версия, для которой уже показали «Что нового» */
    val lastSeenVersion: String = "",
    val lastUpdateCheck: Long = 0,
    /** Какую версию остров уже видел: после обновления он один раз показывает «Обновлено» */
    val islandVersion: String = "",

    // Liquid Glass: полупрозрачное стекло с бликами вместо чёрной заливки. По умолчанию выключено
    val liquidGlass: Boolean = false,
    val glassOpacity: Int = 68,

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

    /** Настройки уже были сохранены до этого запуска: значит, это обновление, а не первая установка. */
    var hadSavedSettings = false
        private set
    private val _state = MutableStateFlow(IslandSettings())
    val state: StateFlow<IslandSettings> = _state.asStateFlow()
    val current: IslandSettings get() = _state.value

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("island", Context.MODE_PRIVATE)
        prefs = p
        hadSavedSettings = p.contains(KEY)
        p.getString(KEY, null)?.let { raw ->
            runCatching { json.decodeFromString<IslandSettings>(raw) }.onSuccess { _state.value = it }
        }
        // 1.1 и раньше: размеры по умолчанию были меньше, чем у iPhone — переносим, если их не трогали
        val cur = _state.value
        // 1.4: автоподгонка могла выставить странный вырез камеры — возвращаем по умолчанию
        if (cur.layoutVersion in 2..2) update { copy(cameraGap = IslandSettings().cameraGap, layoutVersion = 3) }
        if (cur.layoutVersion < 2) {
            update {
                copy(
                    width = if (width == 96) 126 else width,
                    height = if (height == 34) 37 else height,
                    offsetY = if (offsetY == 8) 11 else offsetY,
                    layoutVersion = 3,
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
            noticeDurationMs = d.noticeDurationMs,
        )
    }
}
