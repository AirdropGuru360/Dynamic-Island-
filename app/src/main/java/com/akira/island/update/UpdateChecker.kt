package com.akira.island.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.akira.island.data.SettingsRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Новая версия на GitHub: номер, что изменилось и откуда скачать APK. */
data class Release(
    val version: String,
    val notes: String,
    val apkUrl: String?,
    val pageUrl: String,
    val sizeBytes: Long,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Error(val message: String) : UpdateState
}

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Running(val progress: Float) : DownloadState
    data class Ready(val file: File) : DownloadState
    data class Failed(val message: String) : DownloadState
}

/**
 * Обновление из приложения: смотрит последний релиз на GitHub, при новой версии показывает кнопку
 * «Обновить», скачивает APK и открывает системную установку. Подпись у всех сборок одна,
 * поэтому APK ставится поверх.
 */
object UpdateChecker {
    const val REPO = "Akira9021/Dynamic-Island-"
    private const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L

    val state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val download = MutableStateFlow<DownloadState>(DownloadState.Idle)

    fun currentVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "0"

    /**
     * [force] — проверка по кнопке: показывает «последняя версия» и ошибки связи.
     * Фоновая раз в 6 часов молчит, если сети нет или версия, от которой отказались, не изменилась.
     */
    suspend fun check(context: Context, force: Boolean) {
        val s = SettingsRepo.current
        val now = System.currentTimeMillis()
        if (!force && (!s.autoUpdateCheck || now - s.lastUpdateCheck < CHECK_EVERY_MS)) return
        if (state.value is UpdateState.Checking) return
        state.value = UpdateState.Checking
        val result = runCatching { withContext(Dispatchers.IO) { fetchLatest() } }
        SettingsRepo.update { copy(lastUpdateCheck = now) }
        val release = result.getOrNull()
        state.value = when {
            result.isFailure -> if (force) UpdateState.Error("Нет связи с GitHub. Проверьте интернет и попробуйте ещё раз.") else UpdateState.Idle
            release == null -> if (force) UpdateState.UpToDate else UpdateState.Idle
            !isNewer(release.version, currentVersion(context)) -> if (force) UpdateState.UpToDate else UpdateState.Idle
            !force && release.version == s.skippedVersion -> UpdateState.Idle
            else -> UpdateState.Available(release)
        }
    }

    /** «Позже»: про эту версию больше не напоминаем, пока не выйдет следующая. */
    fun skip(version: String) {
        SettingsRepo.update { copy(skippedVersion = version) }
        state.value = UpdateState.Idle
        download.value = DownloadState.Idle
    }

    /** Скачивает APK в кэш приложения и сразу открывает установку. */
    suspend fun downloadAndInstall(context: Context, release: Release) {
        val url = release.apkUrl
        if (url == null) {
            download.value = DownloadState.Failed("В релизе нет APK. Откройте страницу релиза.")
            return
        }
        if (download.value is DownloadState.Running) return
        download.value = DownloadState.Running(0f)
        val result = runCatching {
            withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "update").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val file = File(dir, "DynamicIsland-${release.version}.apk")
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    setRequestProperty("User-Agent", "DynamicIsland-Android")
                }
                try {
                    if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                    val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
                    conn.inputStream.use { input ->
                        file.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            var lastShown = -1
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                if (total > 0) {
                                    val percent = (done * 100 / total).toInt()
                                    if (percent != lastShown) {
                                        lastShown = percent
                                        download.value = DownloadState.Running(percent / 100f)
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    conn.disconnect()
                }
                file
            }
        }
        val file = result.getOrNull()
        if (file == null) {
            download.value = DownloadState.Failed("Не удалось скачать обновление. Проверьте интернет и попробуйте ещё раз.")
            return
        }
        download.value = DownloadState.Ready(file)
        install(context, file)
    }

    /**
     * Открывает системную установку. Если приложению ещё не разрешена установка из неизвестных
     * источников, сначала открываются нужные настройки — после разрешения нажмите «Установить» снова.
     */
    fun install(context: Context, file: File) {
        runCatching {
            if (!context.packageManager.canRequestPackageInstalls()) {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                return
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun fetchLatest(): Release? {
        val conn = (URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "DynamicIsland-Android")
        }
        try {
            // Релизов ещё нет
            if (conn.responseCode == 404) return null
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return parseRelease(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    /** Разбор ответа GitHub API `releases/latest`. */
    internal fun parseRelease(json: String): Release? {
        val o = Json.parseToJsonElement(json).jsonObject
        val tag = o["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
        val apk: JsonObject? = o["assets"]?.jsonArray?.map { it.jsonObject }?.firstOrNull { a ->
            val name = a["name"]?.jsonPrimitive?.contentOrNull.orEmpty().lowercase()
            name.endsWith(".apk") && "debug" !in name
        }
        return Release(
            version = tag.removePrefix("v").removePrefix("V"),
            notes = o["body"]?.jsonPrimitive?.contentOrNull.orEmpty().trim(),
            apkUrl = apk?.get("browser_download_url")?.jsonPrimitive?.contentOrNull,
            pageUrl = o["html_url"]?.jsonPrimitive?.contentOrNull ?: "https://github.com/$REPO/releases",
            sizeBytes = apk?.get("size")?.jsonPrimitive?.longOrNull ?: 0L,
        )
    }

    /** 1.10.0 новее 1.9.2: сравниваем по числам, а не по строкам. */
    internal fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.takeWhile { it.isDigit() || it == '.' }.split('.').map { it.toIntOrNull() ?: 0 }
        val r = parts(remote)
        val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
