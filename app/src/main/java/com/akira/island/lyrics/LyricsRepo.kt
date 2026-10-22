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

/** Синхронный текст песен с LRCLIB (https://lrclib.net), как в ПК-версии острова. */
object LyricsRepo {
    private const val UA = "DynamicIslandAndroid/1.0 (https://github.com/akira9021/-68999)"

    private val cache = object : LinkedHashMap<String, List<LyricLine>?>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<LyricLine>?>?) = size > 40
    }

    private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2}(?:[.:]\d{1,3})?)]""")
    private val noise = Regex(
        """\s*[(\[][^)\]]*(official|video|lyric|audio|clip|remaster|visuali[sz]er|hd|4k|mv)[^)\]]*[)\]]""",
        RegexOption.IGNORE_CASE,
    )

    suspend fun find(title: String, artist: String, album: String?, durationMs: Long): List<LyricLine>? =
        withContext(Dispatchers.IO) {
            var t = title
            var a = artist
            // У видео из браузера исполнитель часто сидит в названии: «Исполнитель - Песня»
            if (a.isBlank() && " - " in t) {
                a = t.substringBefore(" - ").trim()
                t = t.substringAfter(" - ").trim()
            }
            t = t.replace(noise, "").trim()
            val key = "${a.lowercase()}|${t.lowercase()}"
            synchronized(cache) { if (cache.containsKey(key)) return@withContext cache[key] }

            val result = runCatching { query(t, a, album, durationMs) }
            // Сетевую ошибку не кешируем — попробуем на следующем треке
            if (result.isSuccess) synchronized(cache) { cache[key] = result.getOrNull() }
            result.getOrNull()
        }

    private fun query(title: String, artist: String, album: String?, durationMs: Long): List<LyricLine>? {
        if (title.isBlank()) return null
        if (artist.isNotBlank() && durationMs > 0) {
            val params = buildMap {
                put("track_name", title)
                put("artist_name", artist)
                if (!album.isNullOrBlank()) put("album_name", album)
                put("duration", (durationMs / 1000).toString())
            }
            get("https://lrclib.net/api/get?" + encode(params))?.let { body ->
                synced(JSONObject(body))?.let { return parse(it) }
            }
        }
        val params = buildMap {
            put("track_name", title)
            if (artist.isNotBlank()) put("artist_name", artist)
        }
        val body = get("https://lrclib.net/api/search?" + encode(params)) ?: return null
        val arr = JSONArray(body)
        var best: String? = null
        var bestDiff = Double.MAX_VALUE
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val lrc = synced(o) ?: continue
            val diff = if (durationMs > 0) abs(o.optDouble("duration", 0.0) - durationMs / 1000.0) else 0.0
            if (diff < bestDiff) {
                best = lrc
                bestDiff = diff
            }
        }
        // Слишком непохожая длительность — скорее всего, другая песня
        if (best == null || (durationMs > 0 && bestDiff > 8)) return null
        return parse(best)
    }

    private fun synced(o: JSONObject): String? =
        if (o.isNull("syncedLyrics")) null else o.optString("syncedLyrics").takeIf { it.isNotBlank() }

    private fun encode(params: Map<String, String>) =
        params.entries.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, "UTF-8") }

    private fun get(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 6000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", UA)
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
                val sec = m.groupValues[2].replace(':', '.').toDouble()
                out += LyricLine(min * 60_000 + (sec * 1000).toLong(), text)
            }
        }
        out.sortBy { it.timeMs }
        return out
    }
}
