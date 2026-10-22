package com.akira.island.lyrics

import com.akira.island.core.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/** Найденный текст: [synced] — со временем строк, иначе строки разложены по длине трека. */
data class FoundLyrics(val lines: List<LyricLine>, val synced: Boolean)

/**
 * Текст песен: сначала LRCLIB (https://lrclib.net), как в ПК-версии острова,
 * потом NetEase — там много русской и азиатской музыки.
 */
object LyricsRepo {
    private const val UA = "DynamicIslandAndroid/1.2 (https://github.com/Akira9021/Dynamic-Island-)"

    private val cache = object : LinkedHashMap<String, FoundLyrics?>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FoundLyrics?>?) = size > 40
    }

    private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2}(?:[.:]\d{1,3})?)]""")
    private val noise = Regex(
        """\s*[(\[][^)\]]*(official|video|lyric|audio|clip|remaster|visuali[sz]er|hd|4k|mv|prod\.?)[^)\]]*[)\]]""",
        RegexOption.IGNORE_CASE,
    )
    private val feat = Regex("""\s*[(\[]?\s*(feat\.?|ft\.?|при уч\.?)\s.*$""", RegexOption.IGNORE_CASE)

    suspend fun find(title: String, artist: String, album: String?, durationMs: Long): FoundLyrics? =
        withContext(Dispatchers.IO) {
            var t = title
            var a = artist
            // У видео из браузера исполнитель часто сидит в названии: «Исполнитель - Песня»
            if (a.isBlank() && " - " in t) {
                a = t.substringBefore(" - ").trim()
                t = t.substringAfter(" - ").trim()
            }
            t = t.replace(noise, "").replace(feat, "").trim()
            // «Исполнитель 1, Исполнитель 2» и «A & B» — для поиска хватит первого
            val mainArtist = a.split(",", "&", " x ", " feat", " ft.").first().trim()
            val key = "${mainArtist.lowercase()}|${t.lowercase()}"
            synchronized(cache) { if (cache.containsKey(key)) return@withContext cache[key] }

            var failed = false
            var plain: String? = null
            val found = attempt({ failed = true }) { lrclib(t, mainArtist, album, durationMs) { plain = plain ?: it } }
                ?: attempt({ failed = true }) { netease(t, mainArtist, durationMs) }
                ?: plain?.let { spread(it, durationMs) }
            // Сетевую ошибку не кешируем — попробуем при следующем включении трека
            if (found != null || !failed) synchronized(cache) { cache[key] = found }
            found
        }

    private inline fun attempt(onError: () -> Unit, block: () -> FoundLyrics?): FoundLyrics? =
        try {
            block()
        } catch (e: Exception) {
            onError()
            null
        }

    // LRCLIB

    private fun lrclib(title: String, artist: String, album: String?, durationMs: Long, onPlain: (String) -> Unit): FoundLyrics? {
        if (title.isBlank()) return null
        if (artist.isNotBlank() && durationMs > 0) {
            val params = buildMap {
                put("track_name", title)
                put("artist_name", artist)
                if (!album.isNullOrBlank()) put("album_name", album)
                put("duration", (durationMs / 1000).toString())
            }
            get("https://lrclib.net/api/get?" + encode(params))?.let { body ->
                val o = JSONObject(body)
                synced(o)?.let { return FoundLyrics(parse(it), true) }
                plainOf(o)?.let(onPlain)
            }
        }
        val byFields = buildMap {
            put("track_name", title)
            if (artist.isNotBlank()) put("artist_name", artist)
        }
        pickLrclib(get("https://lrclib.net/api/search?" + encode(byFields)), durationMs, onPlain)?.let { return it }
        // Свободный поиск находит то, что не совпало по полям (другое написание, транслит)
        val q = mapOf("q" to "$artist $title".trim())
        return pickLrclib(get("https://lrclib.net/api/search?" + encode(q)), durationMs, onPlain)
    }

    private fun pickLrclib(body: String?, durationMs: Long, onPlain: (String) -> Unit): FoundLyrics? {
        body ?: return null
        val arr = JSONArray(body)
        var best: String? = null
        var bestDiff = Double.MAX_VALUE
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val diff = if (durationMs > 0) abs(o.optDouble("duration", 0.0) - durationMs / 1000.0) else 0.0
            if (diff > MAX_DIFF_S) continue
            val lrc = synced(o)
            if (lrc == null) {
                plainOf(o)?.let(onPlain)
                continue
            }
            if (diff < bestDiff) {
                best = lrc
                bestDiff = diff
            }
        }
        return best?.let { FoundLyrics(parse(it), true) }
    }

    private fun synced(o: JSONObject): String? =
        if (o.isNull("syncedLyrics")) null else o.optString("syncedLyrics").takeIf { it.isNotBlank() }

    private fun plainOf(o: JSONObject): String? =
        if (o.isNull("plainLyrics")) null else o.optString("plainLyrics").takeIf { it.isNotBlank() }

    // NetEase

    private fun netease(title: String, artist: String, durationMs: Long): FoundLyrics? {
        if (title.isBlank()) return null
        val q = URLEncoder.encode("$artist $title".trim(), "UTF-8")
        val body = get("https://music.163.com/api/search/get?s=$q&type=1&limit=10", netease = true) ?: return null
        val songs = JSONObject(body).optJSONObject("result")?.optJSONArray("songs") ?: return null
        val wanted = norm(title)
        for (i in 0 until songs.length()) {
            val song = songs.optJSONObject(i) ?: continue
            val name = norm(song.optString("name"))
            if (name.isEmpty() || !(name.contains(wanted) || wanted.contains(name))) continue
            val dur = song.optLong("duration", 0)
            if (durationMs > 0 && dur > 0 && abs(dur - durationMs) > MAX_DIFF_S * 1000) continue
            val lyric = get("https://music.163.com/api/song/lyric?id=${song.optLong("id")}&lv=1&kv=1&tv=-1", netease = true)
                ?: continue
            val lrc = JSONObject(lyric).optJSONObject("lrc")?.optString("lyric").orEmpty()
            val lines = parse(lrc).filterNot { isCredit(it.text) }
            if (lines.size >= 4) return FoundLyrics(lines, true)
        }
        return null
    }

    /** NetEase в начале пишет авторов: «作词 : …», «Composer: …» */
    private fun isCredit(text: String): Boolean =
        Regex("""^(作词|作曲|编曲|制作人|lyrics|composer|producer|arranger|автор|музыка|слова)\s*[:：]""", RegexOption.IGNORE_CASE)
            .containsMatchIn(text)

    private fun norm(s: String) = s.lowercase().replace(noise, "").filter { it.isLetterOrDigit() }

    // Общее

    /** Обычный текст без времени: раскладываем строки по треку, пропуская вступление и концовку. */
    private fun spread(plain: String, durationMs: Long): FoundLyrics? {
        val lines = plain.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty() || durationMs <= 0) return null
        val start = durationMs * 0.08
        val step = durationMs * 0.84 / lines.size
        return FoundLyrics(lines.mapIndexed { i, t -> LyricLine((start + step * i).toLong(), t) }, synced = false)
    }

    private fun encode(params: Map<String, String>) =
        params.entries.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, "UTF-8") }

    private fun get(url: String, netease: Boolean = false): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 6000
            c.readTimeout = 8000
            if (netease) {
                c.setRequestProperty("Referer", "https://music.163.com")
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            } else {
                c.setRequestProperty("User-Agent", UA)
            }
            if (c.responseCode == 404) return null
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    fun parse(lrc: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        lrc.lineSequence().forEach { raw ->
            val tags = timeTag.findAll(raw).toList()
            if (tags.isEmpty()) return@forEach
            val text = raw.substring(tags.last().range.last + 1).trim()
            tags.forEach { m ->
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].replace(':', '.').toDoubleOrNull() ?: return@forEach
                out += LyricLine(min * 60_000 + (sec * 1000).toLong(), text)
            }
        }
        out.sortBy { it.timeMs }
        return out
    }

    /** Насколько может отличаться длительность, чтобы это была та же песня, секунд. */
    private const val MAX_DIFF_S = 10.0
}
