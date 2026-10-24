package com.akira.island.service

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.akira.island.data.SettingsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Суммы и слова с экрана кошелька. Отдельно от чтения экрана, чтобы разбор можно было проверить без телефона.
 */
object PayScreenParser {
    private val spaces = Regex("[\\u00A0\\u202F\\u2007\\u2009]")

    /** Сумма с разрядами и копейками: «89,99», «1 250», «12500.5» */
    private const val NUMBER = """\d{1,3}(?:[ ]?\d{3})*(?:[.,]\d{1,2})?"""
    private const val CURRENCY = """₽|руб\.?|р\.?(?!\p{L})|RUB|\$|USD|€|EUR|£|₸|₴|BYN"""

    /** Списание на экране: «−89,99 ₽», «- 1 250 ₽», «–$12.50». Нужна валюта или копейки — иначе это не деньги. */
    private val charge = Regex(
        """(?<![\p{L}\p{N}])[−–-]\s?(?:($NUMBER)\s?($CURRENCY)?|([$€£])\s?($NUMBER))""",
        RegexOption.IGNORE_CASE,
    )

    private val successWords = Regex(
        "оплачено|оплата прошла|оплата выполнена|оплата совершена|покупка совершена|покупка прошла|платёж выполнен|" +
            "платеж выполнен|платёж прошёл|платеж прошел|успешно|готово|одобрено|payment successful|payment complete|" +
            "\\bpaid\\b|\\bdone\\b|approved|success",
        RegexOption.IGNORE_CASE,
    )

    private val failureWords = Regex(
        "не прошл|не удал|отклон|отказ|ошибк|недостаточно|не хватает|не оплач|declined|failed|error|insufficient|unsuccess|try again|повтор",
        RegexOption.IGNORE_CASE,
    )

    /** Все списания в строке — как их показать: «89,99 ₽», «$12.50». */
    fun charges(text: String): List<String> {
        val t = text.replace(spaces, " ")
        return charge.findAll(t).mapNotNull { m ->
            val (num, cur, preCur, preNum) = m.destructured
            when {
                preNum.isNotEmpty() -> "$preCur$preNum"
                cur.isNotEmpty() -> "$num ${cur.trimEnd('.')}"
                num.contains(',') || num.contains('.') -> num
                else -> null
            }
        }.toList()
    }

    fun success(text: String) = successWords.containsMatchIn(text) && !failureWords.containsMatchIn(text)

    fun failure(text: String) = failureWords.containsMatchIn(text)
}

/**
 * Оплата с экрана кошелька. Mir Pay, Google Кошелёк и банки после оплаты показывают у себя галочку и сумму
 * («−89,99 ₽»), а уведомление банка приходит позже или не приходит вовсе. Пока такое приложение открыто,
 * остров смотрит на изменения его экрана: появилось списание — сразу «Оплачено» с этой суммой.
 * Чтобы не принять за оплату историю операций, учитывается только то, чего не было на экране в первые
 * полторы секунды, и только если списание на экране одно. У банков нужно ещё слово «Оплачено» или «Готово».
 */
class PayScreenReader(private val service: AccessibilityService, private val scope: CoroutineScope) {
    val active: Boolean get() = current != null

    private var current: String? = null
    private var since = 0L
    private var baseline: MutableSet<String>? = null
    private var done = false
    private var scanJob: Job? = null
    private var stopJob: Job? = null

    fun onForeground(pkg: String) {
        if (pkg == current) return
        stop()
        if (PayMonitor.isPayApp(pkg)) start(pkg)
    }

    /** Окно «Оплата» сервисов Google поверх любого приложения: читаем его минуту, пока оно на экране. */
    fun onTapAndPay() {
        if (current == PayMonitor.GMS) return
        stop()
        start(PayMonitor.GMS)
        stopJob = scope.launch {
            delay(WINDOW_MS)
            if (current == PayMonitor.GMS) stop()
        }
    }

    fun onContentChanged(event: AccessibilityEvent) {
        if (done || event.packageName?.toString() != current) return
        schedule(150)
    }

    fun stop() {
        scanJob?.cancel()
        stopJob?.cancel()
        scanJob = null
        current = null
        baseline = null
    }

    private fun start(pkg: String) {
        if (!SettingsRepo.current.enabled || !SettingsRepo.current.evPaySuccess) return
        current = pkg
        since = SystemClock.elapsedRealtime()
        baseline = null
        done = false
        schedule(300)
    }

    private fun schedule(wait: Long) {
        if (scanJob?.isActive == true) return
        scanJob = scope.launch {
            delay(wait)
            runCatching { scan() }
        }
    }

    private fun scan() {
        val pkg = current ?: return
        if (done) return
        val age = SystemClock.elapsedRealtime() - since
        // Кошелёк открыт давно — дальше он скорее показывает историю, чем оплату
        if (age > WINDOW_MS) return
        val root = rootFor(pkg) ?: return
        val texts = LinkedHashSet<String>()
        collect(root, texts, 0)
        // Пока экран кошелька загружается, всё на нём — «как было», а не оплата
        val warmUp = if (pkg == PayMonitor.GMS) 500L else 1_500L
        val base = baseline ?: HashSet<String>().also { baseline = it }
        if (age < warmUp) {
            base += texts
            return
        }
        val fresh = texts.filter { it !in base }
        if (fresh.isEmpty()) return
        val all = texts.flatMap { PayScreenParser.charges(it) }.distinct()
        val freshCharges = fresh.flatMap { PayScreenParser.charges(it) }.distinct()
        if (all.size != 1 || freshCharges.size != 1) return
        if (fresh.any { PayScreenParser.failure(it) }) return
        if (!PayMonitor.isWallet(pkg) && fresh.none { PayScreenParser.success(it) }) return
        done = true
        PayMonitor.onScreenPaid(service, pkg, freshCharges[0])
    }

    private fun rootFor(pkg: String): AccessibilityNodeInfo? =
        service.rootInActiveWindow?.takeIf { it.packageName?.toString() == pkg }
            ?: runCatching { service.windows.firstNotNullOfOrNull { w -> w.root?.takeIf { it.packageName?.toString() == pkg } } }.getOrNull()

    private fun collect(node: AccessibilityNodeInfo, out: MutableSet<String>, depth: Int) {
        if (depth > 30 || out.size > 300) return
        if (node.isVisibleToUser) {
            node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() && it.length < 200 }?.let { out += it }
            node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() && it.length < 200 }?.let { out += it }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, out, depth + 1)
        }
    }

    private companion object {
        const val WINDOW_MS = 90_000L
    }
}
