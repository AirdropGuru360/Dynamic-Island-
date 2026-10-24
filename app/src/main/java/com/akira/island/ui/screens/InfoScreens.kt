package com.akira.island.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.R
import com.akira.island.core.Tints
import com.akira.island.data.SettingsRepo
import com.akira.island.service.LiveDebug
import com.akira.island.ui.Group
import com.akira.island.ui.GroupItem
import com.akira.island.ui.GroupToggle
import com.akira.island.ui.NotesDialog
import com.akira.island.ui.Page
import com.akira.island.ui.Perms
import com.akira.island.ui.PillAction
import com.akira.island.ui.Pos
import com.akira.island.ui.Screen
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.Tile
import com.akira.island.ui.Ui
import com.akira.island.ui.UpdateSection
import com.akira.island.ui.updateStatusText
import com.akira.island.update.UpdateChecker
import com.akira.island.update.UpdateState
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
            "На вкладке «Главная» откройте «Разрешения» → «Звонки» и разрешите доступ к телефону, журналу звонков и контактам. " +
                "Тогда остров покажет входящий вызов с любой звонилкой, подпишет имя из контактов, " +
                "а кнопки «ответить» и «сбросить» будут работать.")
        Step(5, "Поставьте капсулу над камерой",
            "Вкладка «Оформление» → «Положение». Пока экран открыт, остров красный: " +
                "сдвиньте его, пока красное не окажется ровно над камерой, и нажмите «Сохранить».")
        Step(6, "Батарея (Xiaomi, Huawei, Samsung…)",
            "Если остров пропадает, разрешите приложению автозапуск и снимите ограничения батареи — " +
                "иначе прошивка выгружает службу.")
        Step(7, "Ответ на сообщения и оплата",
            "Под уведомлением от мессенджера стоят кнопки из самого уведомления. «Ответить» открывает строку ввода прямо в острове. " +
                "Когда приложение оплаты показывает экран оплаты телефоном, остров показывает «К терминалу», а после оплаты — галочку с суммой. " +
                "Всё это можно попробовать: «Главная» → «Проверка».")
        Step(8, "Навигатор",
            "Пока открыт навигатор (2ГИС, Яндекс Навигатор, Карты и др.), остров читает с его экрана расстояние до манёвра, " +
                "улицу и время прибытия, а стрелку поворота узнаёт по картинке. Экран других приложений не читается. " +
                "Если после обновления остров пропал, выключите и снова включите его в спец. возможностях.")
        Step(9, "Жесты",
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

private val GithubPurple = Color(0xFF8250DF)

/** Главная страница проекта на GitHub — в браузере или приложении GitHub. */
private fun openGithub(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${UpdateChecker.REPO}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** «О приложении»: версия и обновления, помощь и ссылки — отдельно от настроек. */
@Composable
fun AppInfoScreen(open: (Screen) -> Unit) {
    val context = LocalContext.current
    val s by SettingsRepo.state.collectAsState()
    val update by UpdateChecker.state.collectAsState()
    val scope = rememberCoroutineScope()
    val version = remember { UpdateChecker.currentVersion(context) }
    var notes by remember { mutableStateOf(false) }

    Page("О приложении") {
        AppHeader(version, update)
        UpdateSection()

        SectionHeader("Обновления")
        Group {
            GroupToggle(Pos.First, Icons.Rounded.SystemUpdate, "Искать обновления",
                "Раз в 6 часов смотреть на GitHub, не вышла ли новая версия", s.autoUpdateCheck, color = Tile.Green) { v ->
                SettingsRepo.update { copy(autoUpdateCheck = v) }
            }
            GroupItem(Pos.Middle, Icons.Rounded.Refresh, "Проверить сейчас", updateStatusText(update, version),
                onClick = { scope.launch { UpdateChecker.check(context.applicationContext, force = true) } }, color = Tile.Blue)
            GroupItem(Pos.Last, Icons.Rounded.NewReleases, "Что нового", "Изменения в версии $version",
                onClick = { notes = true }, color = Tile.Purple) { Arrow() }
        }

        SectionHeader("Помощь")
        Group {
            GroupItem(Pos.First, Icons.Rounded.Lightbulb, "Инструкция", "Как включить остров на Android 13+",
                onClick = { open(Screen.INSTRUCTION) }, color = Tile.Yellow) { Arrow() }
            GroupItem(Pos.Middle, Icons.Rounded.Navigation, "Диагностика навигации",
                "Что присылает навигатор, такси или доставка — если не видно расстояния",
                onClick = { open(Screen.DIAG) }, color = Tile.Teal) { Arrow() }
            GroupItem(Pos.Last, Icons.Rounded.Security, "О приложении в системе", "Разрешения, ограниченные настройки, батарея",
                onClick = { Perms.openAppDetails(context) }, color = Tile.Gray)
        }

        SectionHeader("Проект")
        Group {
            GroupItem(Pos.First, ImageVector.vectorResource(R.drawable.ic_github), "Страница GitHub",
                "Описание, новые версии и исходный код", onClick = { openGithub(context) }, color = GithubPurple) { Arrow() }
            GroupItem(Pos.Last, Icons.Rounded.Info, "Об острове", "Что умеет и откуда идея",
                onClick = { open(Screen.ABOUT) }, color = Tile.Indigo) { Arrow() }
        }
    }

    if (notes) NotesDialog { notes = false }
}

/** Шапка «О приложении»: значок, название, версия и есть ли обновление. */
@Composable
private fun AppHeader(version: String, update: UpdateState) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Ui.Card).padding(vertical = 26.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Как значок приложения на рабочем столе: капсула на сиреневом
        Box(Modifier.size(84.dp).clip(RoundedCornerShape(24.dp)).background(Ui.Primary), contentAlignment = Alignment.Center) {
            Image(ImageVector.vectorResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(126.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text("Динамический остров", color = Ui.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Версия $version", color = Ui.Sub, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
        val (label, color) = when (update) {
            UpdateState.UpToDate -> "Последняя версия" to Tints.Green
            is UpdateState.Available -> "Доступна версия ${update.release.version}" to Ui.Primary
            UpdateState.Checking -> "Проверяю обновления…" to Ui.Sub
            is UpdateState.Error -> "Не удалось проверить обновления" to Tints.Orange
            UpdateState.Idle -> null to Ui.Sub
        }
        if (label != null) {
            Text(
                label, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp).clip(CircleShape).background(color.copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

/** «Об острове»: что он умеет, откуда идея и как устроен. */
@Composable
fun IdeaScreen(onBack: () -> Unit) {
    Page("Об острове", onBack) {
        IslandPreview()
        SectionHeader("Что умеет")
        InfoText(
            "Капсула в духе iPhone над камерой Android: музыка с обложкой, эквалайзером и синхронным текстом песен, " +
                "таймер с кольцом остатка, секундомер, прогресс загрузок, фонарик с регулировкой яркости, " +
                "уведомления с ответом, оплата, навигация, зарядка и системные события. Вторая активность уходит в пузырь рядом с островом.",
        )
        SectionHeader("Откуда идея")
        InfoText(
            "Идея и поведение перенесены с ПК-версии Dynamic Island для Windows (github.com/mihailkotovski/DynamicIsland): " +
                "пружинные анимации формы, плеер по клику, толчок вбок для смены трека, таймер от 1 до 99 минут " +
                "с кольцом вокруг паузы и звонком, текст песен с LRCLIB.",
        )
        SectionHeader("Как устроено")
        InfoText(
            "Капсула рисуется службой спец. возможностей, данные о музыке берутся из системных медиасессий, " +
                "прогресс и таймеры — из уведомлений. Анимации идут, только пока капсула что-то показывает; " +
                "«Уменьшить эффекты» выключает эквалайзер и «желе».",
        )
    }
}

@Composable
private fun InfoText(text: String) {
    Text(
        text, color = Ui.Sub, fontSize = 14.sp, lineHeight = 20.sp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ui.Card).padding(20.dp),
    )
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
