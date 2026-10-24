package com.akira.island.service

import android.app.Notification
import android.content.Context
import android.os.SystemClock
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
 * на то, что видно снаружи: открыли банк или кошелёк — показываем «Приложите к терминалу»
 * с волнами; пришло уведомление об оплате — «Оплачено» с зелёной галочкой и суммой.
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

    /** Само окно «Оплата» из сервисов Google: появляется, когда телефон поднесли к терминалу. */
    const val GMS = "com.google.android.gms"

    private var lastReady = 0L

    fun isPayApp(pkg: String) = pkg in apps

    /** Открыли приложение оплаты (смена окна из другого приложения). */
    fun onAppOpened(context: Context, pkg: String) {
        if (pkg !in apps) return
        showReady(context, pkg)
    }

    /** Окно «Оплата» из сервисов Google. */
    fun onTapAndPay(context: Context) = showReady(context, "com.google.android.apps.walletnfcrel")

    private fun showReady(context: Context, pkg: String) {
        val s = SettingsRepo.current
        if (!s.enabled || !s.evPayReady) return
        val now = SystemClock.elapsedRealtime()
        // Не чаще раза в полминуты, чтобы не мигало при каждом переходе по экранам банка
        if (now - lastReady < 30_000) return
        lastReady = now
        IslandBus.post(
            Notice(
                title = "Оплата",
                state = null,
                icon = Ios.load(context, R.drawable.ios_wifi),
                tint = Tints.Blue,
                caption = "К терминалу",
                style = NoticeStyle.PAY_READY,
                appIcon = NotificationParser.icon(context, pkg),
                extraMs = 3_500,
            ),
        )
    }

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
    private val amount = Regex("""\d[\d\s .,]*\s?(?:₽|руб\.?|р\.?(?!\p{L})|RUB|\$|USD|€|EUR|£|₸|₴|BYN)""", RegexOption.IGNORE_CASE)

    /**
     * Уведомление от банка или кошелька об оплате. Возвращает true, если оно показано
     * как «Оплачено» и обычной карточкой сообщения показывать его не нужно.
     */
    fun onNotification(context: Context, sbn: StatusBarNotification): Boolean {
        val s = SettingsRepo.current
        if (!s.enabled || !s.evPaySuccess || sbn.packageName !in apps || sbn.isOngoing) return false
        val n = sbn.notification ?: return false
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        val ex = n.extras ?: return false
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val all = "$title $text"
        if (!success.containsMatchIn(all) || skip.containsMatchIn(all)) return false
        // У настоящей оплаты всегда есть сумма; без неё это скорее реклама или новость банка
        val sum = amount.find(all)?.value?.replace(' ', ' ')?.trim() ?: return false
        IslandBus.postNow(
            Notice(
                title = "Оплачено",
                state = sum,
                icon = Ios.load(context, R.drawable.ios_checkmark_circle),
                tint = Tints.Green,
                caption = NotificationParser.appLabel(context, sbn.packageName),
                style = NoticeStyle.PAY_DONE,
                appIcon = NotificationParser.icon(context, sbn.packageName),
                extraMs = 900,
            ),
        )
        return true
    }
}
