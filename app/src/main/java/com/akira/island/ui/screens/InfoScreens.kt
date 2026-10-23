package com.akira.island.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.ui.Page
import com.akira.island.ui.PillAction
import com.akira.island.ui.Perms
import com.akira.island.ui.Ui
import com.akira.island.service.LiveDebug
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.ui.text.font.FontFamily
import com.akira.island.ui.UpdateSection
import com.akira.island.ui.updateStatusText
import com.akira.island.update.UpdateChecker
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
fun InstructionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Page("Инструкция", onBack) {
        Step(1, "Спец. возможности",
            "Откройте «Настройки → Спец. возможности → Установленные приложения → Динамический остров» и включите службу. " +
                "Только такая служба может рисовать поверх строки состояния, рядом с камерой.")
        Step(2, "Если переключатель серый (Android 13+)",
            "Система блокирует спец. возможности для приложений, установленных не из магазина. Откройте «О приложении», " +
                "нажмите «⋮» в правом верхнем углу → «Разрешить ограниченные настройки», затем вернитесь к шагу 1.")
        Row { PillAction("Открыть «О приложении»") { Perms.openAppDetails(context) } }
        Step(3, "Доступ к уведомлениям",
            "Нужен для карточек музыки, прогресса загрузок и таймеров/секундомеров Google Часов. " +
                "Остров не хранит и никуда не отправляет уведомления.")
        Row { PillAction("Открыть доступ к уведомлениям") { Perms.openNotifications(context) } }
        Step(4, "Звонки",
            "На главном экране нажмите «Звонки» и разрешите доступ к телефону, журналу звонков и контактам. " +
                "Тогда остров покажет входящий вызов с любой звонилкой, подпишет имя из контактов, " +
                "а кнопки «ответить» и «сбросить» будут работать.")
        Step(5, "Поставьте капсулу над камерой",
            "Вкладка «Настройки Капсулы» → «Положение». Пока экран открыт, остров красный: " +
                "сдвиньте его, пока красное не окажется ровно над камерой, и нажмите «Сохранить».")
        Step(6, "Батарея (Xiaomi, Huawei, Samsung…)",
            "Если остров пропадает, разрешите приложению автозапуск и снимите ограничения батареи — " +
                "иначе прошивка выгружает службу.")
        Step(7, "Ответ на сообщения и оплата",
            "Под уведомлением от мессенджера стоят кнопки из самого уведомления. «Ответить» открывает строку ввода прямо в острове. " +
                "При открытии банка или кошелька остров показывает «К терминалу», а когда банк присылает уведомление об оплате — галочку с суммой. " +
                "Всё это проверяется на главном экране в блоке «Проверка».")
        Step(8, "Жесты",
            "Нажатие — раскрыть, потянуть вниз — раскрыть, вверх или касание вне острова — свернуть, " +
                "толкнуть вбок — следующий/предыдущий трек, нажатие на пузырь — раскрыть вторую активность.")
    }
}

@Composable
private fun Step(n: Int, title: String, text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Ui.Card).padding(18.dp),
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Ui.Primary), contentAlignment = Alignment.Center) {
            Text("$n", color = Ui.OnPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = Ui.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(4.dp))
            Text(text, color = Ui.Sub, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "1.0"
    val update by UpdateChecker.state.collectAsState()
    val scope = rememberCoroutineScope()
    Page("О приложении", onBack) {
        IslandPreview()
        UpdateSection()
        Text(
            "Динамический остров $version",
            color = Ui.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            PillAction("Проверить обновления") { scope.launch { UpdateChecker.check(context.applicationContext, force = true) } }
            Spacer(Modifier.width(12.dp))
            Text(updateStatusText(update, version), color = Ui.Sub, fontSize = 13.sp, modifier = Modifier.weight(1f))
        }
        Text(
            "Капсула в духе iPhone над камерой Android: музыка с обложкой, эквалайзером и синхронным текстом песен, " +
                "таймер с кольцом остатка, секундомер, прогресс загрузок, фонарик с регулировкой яркости, " +
                "уведомления с ответом, оплата, навигация, зарядка и системные события. Вторая активность уходит в пузырь рядом с островом.\n\n" +
                "Идея и поведение перенесены с ПК-версии Dynamic Island для Windows (github.com/mihailkotovski/DynamicIsland): " +
                "пружинные анимации формы, плеер по клику, толчок вбок для смены трека, таймер от 1 до 99 минут " +
                "с кольцом вокруг паузы и звонком, текст песен с LRCLIB.\n\n" +
                "Под Android: капсула рисуется службой спец. возможностей, данные о музыке берутся из системных медиасессий, " +
                "прогресс и таймеры — из уведомлений. Анимации идут только пока капсула что-то показывает; " +
                "«Уменьшить эффекты» выключает эквалайзер и «желе».",
            color = Ui.Sub, fontSize = 14.sp, lineHeight = 20.sp,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Ui.Card).padding(20.dp),
        )
    }
}

/**
 * Что остров видит в уведомлениях навигаторов, такси и доставки: если расстояние или стрелка
 * не показываются, здесь видно, что именно присылает приложение.
 */
@Composable
fun DiagScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val dumps by LiveDebug.dumps.collectAsState()
    Page("Диагностика навигации", onBack) {
        Text(
            "Включите навигатор, такси или доставку и вернитесь сюда: ниже то, что приложение присылает в уведомлении. " +
                "Если расстояние или стрелка поворота не показываются в острове, нажмите «Скопировать» и пришлите этот текст.",
            color = Ui.Sub, fontSize = 14.sp, lineHeight = 20.sp,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ui.Card).padding(20.dp),
        )
        if (dumps.isEmpty()) {
            Text(
                "Пока ничего нет: ни одного уведомления навигатора, такси или доставки не пришло.",
                color = Ui.Sub, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp),
            )
        } else {
            Row {
                PillAction("Скопировать всё") {
                    val text = dumps.values.joinToString("\n\n")
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("Диагностика навигации", text))
                }
            }
            dumps.values.forEach { dump ->
                Text(
                    dump, color = Ui.Text, fontSize = 12.sp, lineHeight = 17.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ui.Card).padding(16.dp),
                )
            }
        }
    }
}
