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
        Step(4, "Подгоните капсулу под камеру",
            "Вкладка «Настройки Капсулы» → «Размер и положение». Ширина — это пустое место над камерой, " +
                "отступ сверху — расстояние от края экрана.")
        Step(5, "Батарея (Xiaomi, Huawei, Samsung…)",
            "Если остров пропадает, разрешите приложению автозапуск и снимите ограничения батареи — " +
                "иначе прошивка выгружает службу.")
        Step(6, "Жесты",
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
    Page("О приложении", onBack) {
        IslandPreview()
        Text(
            "Динамический остров $version",
            color = Ui.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp),
        )
        Text(
            "Капсула в духе iPhone над камерой Android: музыка с обложкой, эквалайзером и синхронным текстом песен, " +
                "таймер с кольцом остатка, секундомер, прогресс загрузок, фонарик с регулировкой яркости, громкость, " +
                "зарядка и системные события. Вторая активность уходит в пузырь рядом с островом.\n\n" +
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
