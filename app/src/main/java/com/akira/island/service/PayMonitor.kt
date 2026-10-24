package com.akira.island.service

import android.app.Notification
import android.content.Context
import android.os.SystemClock
import android.provider.Telephony
import android.service.notification.StatusBarNotification
import com.akira.island.core.IslandBus
import com.akira.island.core.Notice
import com.akira.island.core.NoticeStyle
import com.akira.island.core.Tints
import com.akira.island.R
import com.akira.island.data.SettingsRepo
import com.akira.island.island.Ios

/**
 * Бесконтактная оплата, как Apple Pay на iPhone. Остров не видит поле терминала, поэтому опирается
 * на то, что видно снаружи: открыли банк или кошелёк — показываем «Оплата · К терминалу»
 * с волнами; кошелёк показал у себя сумму (PayScreenReader) или пришло уведомление об оплате от банка
 * или СМС с его номера — «Оплачено» с зелёной галочкой и суммой.
 */
object PayMonitor {
    /** Банки и кошельки: открытие любого из них показывает подсказку оплаты. */
    private val apps = setOf(
        "com.google.android.apps.walletnfcrel", // Google Кошелёк / Google Pay
        "com.samsung.android.spay", // Samsung Wallet
        "ru.nspk.mirpay", // Mir Pay
        "com.idamob.tinkoff.android", // Т-Банк (Tinkoff Pay)
        "ru.alfabank.mobile.android", // Альфа-Банк (Alfa Pay)
        "ru.sberbankmobile", // СберБанк (SberPay)
        "ru.vtb24.mobilebanking.android", // ВТБ
        "ru.raiffeisennews", // Райффайзен
        "ru.gazprombank.android.mobilebank.app", // Газпромбанк
        "ru.sovcomcard.halva.v1", // Халва
        "ru.mts.money", // МТС Банк
        "com.paypal.android.p2pmobile",
        "com.revolut.revolut",
    )

    /** Кошельки открывают только чтобы заплатить: в них подсказка держится до оплаты, а не пару секунд. */
    private val wallets = setOf(
        "com.google.android.apps.walletnfcrel",
        "com.samsung.android.spay",
        "ru.nspk.mirpay",
    )

    /** Само окно «Оплата» из сервисов Google: появляется, когда телефон поднесли к терминалу. */
    const val GMS = "com.google.android.gms"

    /** Уведомления этих приложений тоже разбираются как оплата: банки, которых нет в списке выше. */
    private val bankish = Regex(
        "bank|banking|sber|tinkoff|vtb|alfa|raiff|sovcom|halva|gazprom|psbank|otkritie|pochta|rshb|uralsib|" +
            "rosbank|akbars|mkb|yoomoney|qiwi|wallet|\\.pay|pay\\.|mirpay|spay",
        RegexOption.IGNORE_CASE,
    )

    /** Отправитель СМС — банк: короткий номер (900) или название. */
    private val bankSender = Regex(
        "^\\d{3,6}$|bank|банк|sber|сбер|tinkoff|т-банк|t-bank|vtb|втб|alfa|альфа|raiff|райф|gazprom|газпром|sovcom|совком|" +
            "pochta|почта|otkritie|открытие|mts|мтс|ozon|озон|psb|псб|rshb|россельхоз|uralsib|уралсиб|rosbank|росбанк|halva|халва|" +
            "yoomoney|юmoney",
        RegexOption.IGNORE_CASE,
    )

    private var lastReady = 0L
    private var readyId = -1L
    private var readyPkg: String? = null
    private var paidKey: String? = null
    private var paidAt = 0L

    fun isPayApp(pkg: String) = pkg in apps

    fun isWallet(pkg: String) = pkg in wallets || pkg == GMS

    /** Открыли приложение оплаты (смена окна из другого приложения). */
    fun onAppOpened(context: Context, pkg: String) {
        if (pkg !in apps) return
        showReady(context, pkg)
    }

    /** Окно «Оплата» из сервисов Google. */
    fun onTapAndPay(context: Context) = showReady(context, "com.google.android.apps.walletnfcrel")

    /** Открыто другое приложение: ушли из кошелька, так и не заплатив, — подсказка «К терминалу» больше не нужна. */
    fun onForeground(pkg: String) {
        val ready = readyPkg ?: return
        // Окна сервисов Google (то же окно оплаты) приложение не меняют
        if (pkg == ready || pkg == GMS) return
        readyPkg = null
        if (IslandBus.notice.value?.id == readyId) IslandBus.notice.value = null
    }

    private fun showReady(context: Context, pkg: String) {
        val s = SettingsRepo.current
        if (!s.enabled || !s.evPayReady) return
        val now = SystemClock.elapsedRealtime()
        // Не чаще раза в полминуты, чтобы не мигало при каждом переходе по экранам банка
        if (now - lastReady < 30_000) return
        lastReady = now
        val notice = Notice(
            title = "Оплата",
            state = null,
            icon = Ios.load(context, R.drawable.ios_wifi),
            tint = Tints.Lavender,
            caption = "К терминалу",
            style = NoticeStyle.PAY_READY,
            appIcon = NotificationParser.icon(context, pkg),
            // В кошельке подсказка ждёт оплаты (до минуты), в банке — как обычное событие, чуть дольше
            extraMs = if (isWallet(pkg)) 60_000 else 3_500,
        )
        readyId = notice.id
        readyPkg = pkg
        IslandBus.post(notice)
    }

    /** Сумма с разрядами и копейками и валютой: «89,99 ₽», «1 250 руб.», «12.50 USD», «$12.50». */
    private val amount = Regex(
        """(?<![\d*])(?:\d{1,3}(?:[ ]?\d{3})*(?:[.,]\d{1,2})?\s?(?:₽|руб\.?|р\.?(?!\p{L})|RUB|USD|EUR|€|£|₸|₴|BYN|\$)|[$€£]\s?\d{1,3}(?:[ ,]?\d{3})*(?:[.,]\d{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )
    private val success = Regex("покупк|оплат|списан|списал|paid|payment|purchase|charged|spent", RegexOption.IGNORE_CASE)
    /**
     * Не оплата: пополнения, переводы, возвраты, неудачные платежи, а также напоминания и счета
     * («К оплате», «Не забудьте оплатить», «будет списано», «Payment due»).
     */
    private val skip = Regex(
        "пополн|зачисл|поступил|перевод|кэшбэк|кешбэк|cashback|возврат|refund|отклон|отказ|declined|не удал|ошибк|" +
            "не прошл|не выполн|недостаточно|не хватает|не оплач|к оплате|на оплату|оплатит|заплатит|" +
            "напомина|будет списан|спишем|спишется|предстоящ|due|remind|failed|unsuccessful|insufficient|request|received",
        RegexOption.IGNORE_CASE,
    )

    /** Первая сумма с валютой в тексте уведомления. */
    internal fun amountIn(text: String): String? = amount.find(text)?.value?.trim()

    /** Похоже ли уведомление на оплату: есть слово об оплате и нет слов о переводе, возврате, ошибке. */
    internal fun looksPaid(text: String): Boolean = success.containsMatchIn(text) && !skip.containsMatchIn(text)

    /** Цифры суммы — чтобы одна оплата с экрана и из уведомления не показалась дважды. */
    private fun key(sum: String) = sum.filter { it.isDigit() }.trimEnd('0')

    /** Эта сумма уже показана как «Оплачено» за последние три минуты. */
    private fun alreadyShown(sum: String) = key(sum) == paidKey && SystemClock.elapsedRealtime() - paidAt < 180_000

    private fun showPaid(context: Context, sum: String, caption: String, pkg: String) {
        paidKey = key(sum)
        paidAt = SystemClock.elapsedRealtime()
        readyPkg = null
        IslandBus.postNow(
            Notice(
                title = "Оплачено",
                state = sum,
                icon = Ios.load(context, R.drawable.ios_checkmark_circle),
                tint = Tints.Green,
                caption = caption,
                style = NoticeStyle.PAY_DONE,
                appIcon = NotificationParser.icon(context, pkg),
                extraMs = 900,
            ),
        )
    }

    /** Кошелёк показал у себя галочку и сумму. */
    fun onScreenPaid(context: Context, pkg: String, sum: String) {
        val s = SettingsRepo.current
        if (!s.enabled || !s.evPaySuccess || alreadyShown(sum)) return
        val app = if (pkg == GMS) "com.google.android.apps.walletnfcrel" else pkg
        showPaid(context, sum, NotificationParser.appLabel(context, app), app)
    }

    /**
     * Уведомление от банка, кошелька или СМС банка об оплате. Возвращает true, если оно показано
     * как «Оплачено» (или уже было показано с экрана кошелька) и обычной карточкой сообщения показывать его не нужно.
     */
    fun onNotification(context: Context, sbn: StatusBarNotification): Boolean {
        val s = SettingsRepo.current
        if (!s.enabled || !s.evPaySuccess || sbn.isOngoing) return false
        val pkg = sbn.packageName
        val n = sbn.notification ?: return false
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        val ex = n.extras ?: return false
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val sms = pkg == defaultSms(context)
        if (sms) {
            if (!bankSender.containsMatchIn(title.trim())) return false
        } else if (pkg !in apps && !bankish.containsMatchIn(pkg)) {
            return false
        }
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val all = "$title $text".replace(' ', ' ').replace(' ', ' ')
        if (!looksPaid(all)) return false
        // У настоящей оплаты всегда есть сумма; без неё это скорее реклама или новость банка
        val sum = amountIn(all) ?: return false
        if (alreadyShown(sum)) return true
        showPaid(context, sum, if (sms) title else NotificationParser.appLabel(context, pkg), pkg)
        return true
    }

    private var smsPkg: String? = null
    private var smsCheckedAt = 0L

    /** Приложение СМС по умолчанию — раз в минуту, его меняют редко. */
    private fun defaultSms(context: Context): String? {
        val now = SystemClock.elapsedRealtime()
        if (smsCheckedAt == 0L || now - smsCheckedAt > 60_000) {
            smsCheckedAt = now
            smsPkg = runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()
        }
        return smsPkg
    }
}
