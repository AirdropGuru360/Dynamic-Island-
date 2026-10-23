package com.akira.island.service

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.akira.island.R
import com.akira.island.core.LiveInfo
import com.akira.island.core.Notice
import com.akira.island.core.NoticeStyle
import com.akira.island.core.Turn
import com.akira.island.island.Ios
import com.akira.island.island.liveTint
import android.os.SystemClock
import com.akira.island.core.IslandBus
import com.akira.island.data.SettingsRepo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Разбор постоянных уведомлений навигаторов, такси и доставки. Кроме обычных полей берётся то, что
 * приложение рисует своей раскладкой (RemoteViews): у Яндекс Навигатора расстояние и время часто
 * лежат только там, а в заголовке — одно «Навигатор запущен».
 */
object LiveParser {
    /** Что нашлось в раскладке уведомления: тексты по порядку и маленькая картинка (значок манёвра). */
    private class Remote(val texts: List<String>, val image: Bitmap?, val note: String)

    private val distance = Regex("""(\d{1,3}(?:[.,]\d{1,2})?)\s?(км|метров|метра|м|km|mi|ft|m)\.?(?!\p{L})""", RegexOption.IGNORE_CASE)
    private val clock = Regex("""\b\d{1,2}:\d{2}\b""")
    private val minutes = Regex("""\b\d{1,3}\s?(мин|ч|min|h)\b""", RegexOption.IGNORE_CASE)

    fun parse(context: Context, sbn: StatusBarNotification, fallbackTitle: String): LiveInfo? {
        val n = sbn.notification
        val ex = n.extras
        val kind = LiveApps.kind(sbn.packageName, n.category) ?: return null
        val head = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val sub = (ex.getCharSequence(Notification.EXTRA_SUB_TEXT) ?: ex.getCharSequence(Notification.EXTRA_INFO_TEXT))
            ?.toString()?.trim().orEmpty()
        val fields = listOf(head, text, big, sub)
        val remote = if (findDistance(fields) != null && findEta(fields) != null) {
            Remote(emptyList(), null, "не разбиралась: расстояние и время есть в полях")
        } else {
            remoteFor(context, sbn.key, n)
        }
        LiveDebug.record(context, sbn, head, text, big, sub, remote.texts, remote.note)
        if (!sbn.isOngoing) return null
        if (head.isEmpty() && text.isEmpty() && remote.texts.isEmpty()) return null

        val all = fields + remote.texts
        val hint = instructionOf(all)
        val turn = hint?.let { detectTurn(listOf(it)) }
        val art = if (turn == null) {
            runCatching { n.getLargeIcon()?.loadDrawable(context)?.toBitmap(128, 128) }.getOrNull() ?: remote.image
        } else {
            null
        }
        val instruction = hint ?: (listOf(text, big) + remote.texts)
            .firstOrNull { it.isNotBlank() && it != head && it.length > 8 && distance.matchEntire(it.trim()) == null }
            .orEmpty()
        return LiveInfo(
            key = sbn.key,
            pkg = sbn.packageName,
            kind = kind,
            title = head.ifEmpty { fallbackTitle },
            text = instruction,
            sub = sub,
            icon = NotificationParser.icon(context, sbn.packageName),
            art = art?.asImageBitmap(),
            postTime = sbn.postTime,
            open = n.contentIntent,
            distance = findDistance(all),
            eta = findEta(all),
            turn = turn,
        )
    }

    private val lastBanner = HashMap<String, Long>()

    /** Показать широкую плашку, но не чаще раза в минуту для одного приложения. */
    fun postBanner(context: Context, l: LiveInfo) {
        if (!SettingsRepo.current.cardLive) return
        val now = SystemClock.elapsedRealtime()
        if (now - (lastBanner[l.pkg] ?: -120_000L) < 60_000) return
        lastBanner[l.pkg] = now
        IslandBus.post(banner(context, l))
    }

    /**
     * Новая активность из уведомления: плашку показываем через полторы секунды — за это время остров
     * успевает прочитать экран навигатора, и на плашке уже есть расстояние и стрелка.
     */
    fun postBannerLater(context: Context, key: String) {
        IslandBus.scope.launch {
            delay(1600)
            val l = IslandBus.live.value[key] ?: return@launch
            postBanner(context, NavScreen.merge(l, NavScreen.data.value[l.pkg]))
        }
    }

    /** Широкая плашка при запуске: iPhone показывает новую «живую активность» развёрнутой, потом сворачивает. */
    fun banner(context: Context, l: LiveInfo): Notice = Notice(
        title = l.title,
        icon = Ios.load(context, R.drawable.ios_navigate),
        tint = liveTint(l.kind),
        style = NoticeStyle.LIVE,
        live = l,
        durationMs = 4500,
    )

    /** «300 м», «3,7 км» — сначала короткие подписи, где расстояние стоит отдельно, потом любое упоминание. */
    internal fun findDistance(texts: List<String>): String? {
        val cleaned = texts.map { it.trim() }.filter { it.isNotEmpty() }
        cleaned.firstOrNull { it.length <= 12 && distance.matchEntire(it) != null }?.let { return normalize(it) }
        for (t in cleaned) distance.find(t)?.let { return normalize(it.value) }
        return null
    }

    private fun normalize(s: String) = s.trim().trimEnd('.').replace(Regex("""\s+"""), " ")

    /** «15:52 · 9 мин» из любых подписей уведомления. */
    internal fun findEta(texts: List<String>): String? {
        val joined = texts.joinToString(" ")
        val time = clock.find(joined)?.value
        val min = minutes.find(joined)?.value
        return listOfNotNull(time?.let { "Прибытие в $it" }, min?.replace(Regex("""\s+"""), " ")).takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private val maneuverWords = listOf(
        "поверн", "держит", "съезд", "съехать", "развер", "разворот", "прямо", "налево", "направо", "левее", "правее",
        "turn", "keep", "exit", "u-turn", "continue",
    )
    private val notInstruction = listOf("по шагам", "по gps", "шаг ")

    /** Подсказка навигатора: «Через 300 м поверните направо на Тверскую», «Держитесь левее», «Съезд 2». */
    internal fun instructionOf(texts: List<String>): String? = texts.map { it.trim() }.firstOrNull { t ->
        val l = t.lowercase()
        t.length in 6..90 && maneuverWords.any { it in l } && notInstruction.none { it in l }
    }

    /** Куда поворачивать, по тексту подсказки или описанию значка манёвра. */
    internal fun detectTurn(texts: List<String>): Turn? {
        val t = texts.joinToString(" ").lowercase()
        return when {
            listOf("разворот", "развернитесь", "u-turn", "uturn", "make a u").any { it in t } -> Turn.UTURN
            listOf("налево", "влево", "левее", "left").any { it in t } -> Turn.LEFT
            listOf("направо", "вправо", "правее", "right").any { it in t } -> Turn.RIGHT
            listOf("прямо", "straight", "продолжайте", "continue").any { it in t } -> Turn.STRAIGHT
            else -> null
        }
    }

    /** Раскладка, которую приложение рисует само: достаём тексты и описания картинок по порядку. */
    @Suppress("DEPRECATION")
    /** Последняя разобранная раскладка по ключу уведомления и когда её разобрали */
    private val remoteCache = HashMap<String, Pair<Long, Remote>>()

    /**
     * Навигатор обновляет уведомление каждую секунду, а собрать его раскладку — это создать целое дерево
     * View. Поэтому разбираем её не чаще раза в 2 секунды, а при погашенном экране берём прошлую.
     */
    private fun remoteFor(context: Context, key: String, n: Notification): Remote {
        val now = SystemClock.elapsedRealtime()
        val cached = remoteCache[key]
        if (cached != null && (now - cached.first < 2000 || !IslandBus.screenOn.value)) return cached.second
        return remoteViews(context, n).also { remoteCache[key] = now to it }
    }

    fun forget(key: String) {
        remoteCache.remove(key)
    }

    private fun remoteViews(context: Context, n: Notification): Remote {
        val rv = n.bigContentView ?: n.contentView ?: n.headsUpContentView
            ?: return Remote(emptyList(), null, "своей раскладки нет")
        return runCatching {
            val root = rv.apply(context, FrameLayout(context))
            val texts = mutableListOf<String>()
            var image: Bitmap? = null
            fun walk(v: View) {
                when (v) {
                    is TextView -> v.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(texts::add)
                    is ImageView -> {
                        v.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(texts::add)
                        val d = v.drawable
                        if (image == null && d != null && d.intrinsicWidth in 1..220) image = d.toBitmap(96, 96)
                    }
                }
                if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
            }
            walk(root)
            Remote(texts, image, "раскладка есть, текстов: ${texts.size}")
        }.getOrElse { Remote(emptyList(), null, "раскладку не удалось прочитать: ${it.javaClass.simpleName}") }
    }
}

/**
 * Что остров видит в уведомлениях навигаторов, такси и доставки. Если у вашего приложения расстояние
 * не показывается, этот текст (Общие → Диагностика навигации) покажет, что именно оно присылает.
 */
object LiveDebug {
    val dumps = MutableStateFlow<Map<String, String>>(emptyMap())

    fun record(
        context: Context,
        sbn: StatusBarNotification,
        title: String,
        text: String,
        bigText: String,
        sub: String,
        remote: List<String>,
        remoteNote: String,
    ) {
        val n = sbn.notification
        val body = buildString {
            appendLine(NotificationParser.appLabel(context, sbn.packageName) + " (" + sbn.packageName + ")")
            appendLine("постоянное: ${sbn.isOngoing}, категория: ${n.category ?: "—"}")
            appendLine("заголовок: ${title.ifEmpty { "—" }}")
            appendLine("текст: ${text.ifEmpty { "—" }}")
            if (bigText.isNotEmpty() && bigText != text) appendLine("большой текст: $bigText")
            appendLine("подпись: ${sub.ifEmpty { "—" }}")
            appendLine("раскладка: $remoteNote")
            remote.forEachIndexed { i, t -> appendLine("  [$i] $t") }
            append("кнопки: " + (n.actions?.joinToString { it.title?.toString().orEmpty() } ?: "—"))
        }
        dumps.value = dumps.value + (sbn.key to body)
    }

    /** Подписи экрана навигатора — чтобы было видно, откуда остров берёт расстояние. */
    fun recordScreen(context: Context, pkg: String, items: List<ScreenItem>, r: NavScreenParser.Result) {
        val body = buildString {
            appendLine("Экран: " + NotificationParser.appLabel(context, pkg) + " (" + pkg + ")")
            appendLine("нашлось: расстояние ${r.distance ?: "—"}, улица ${r.street ?: "—"}, прибытие ${r.eta ?: "—"}, поворот ${r.turn ?: "по картинке"}")
            items.filter { it.text.isNotEmpty() }.sortedWith(compareBy({ it.top }, { it.left })).take(40).forEach {
                appendLine("  «${it.text}» ${it.left},${it.top}–${it.right},${it.bottom}")
            }
        }.trimEnd()
        val key = "screen:$pkg"
        if (dumps.value[key] != body) dumps.value = dumps.value + (key to body)
    }
}
