package com.akira.island.service

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale
import androidx.palette.graphics.Palette
import com.akira.island.core.Controls
import com.akira.island.core.IslandBus
import com.akira.island.core.Lyrics
import com.akira.island.core.MediaInfo
import com.akira.island.core.Tints
import com.akira.island.data.SettingsRepo
import com.akira.island.lyrics.LyricsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Следит за системными медиасессиями (любой плеер) и выбирает ту, что играет. */
class MediaMonitor(private val context: Context) {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(context, NotifListener::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val bound = ArrayList<Pair<MediaController, MediaController.Callback>>()
    private var current: MediaController? = null
    private var lastArt: Bitmap? = null
    private var artJob: Job? = null
    private var lyricsJob: Job? = null
    private var hideJob: Job? = null
    private var hiddenKey: String? = null
    private var pendingHideKey: String? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        bind(list.orEmpty())
    }

    fun start() {
        try {
            manager?.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
            bind(manager?.getActiveSessions(component).orEmpty())
        } catch (_: SecurityException) {
            // Доступ к уведомлениям ещё не выдан
        }
        // Включили текст песен в настройках — подгрузим для текущего трека
        scope.launch {
            SettingsRepo.state.map { it.lyricsEnabled }.distinctUntilChanged().collect { enabled ->
                val m = IslandBus.media.value
                if (enabled && m != null && IslandBus.lyrics.value?.key != m.key) loadLyrics(m)
            }
        }
    }

    fun stop() {
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        unbindAll()
        scope.cancel()
        Controls.mediaController = null
        IslandBus.media.value = null
        IslandBus.lyrics.value = null
    }

    private fun bind(list: List<MediaController>) {
        unbindAll()
        list.forEach { c ->
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
                override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
                override fun onSessionDestroyed() = refresh()
            }
            c.registerCallback(cb, handler)
            bound += c to cb
        }
        refresh()
    }

    private fun unbindAll() {
        bound.forEach { (c, cb) -> runCatching { c.unregisterCallback(cb) } }
        bound.clear()
    }

    private fun isPlaying(c: MediaController): Boolean {
        val st = c.playbackState?.state
        return st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING
    }

    private fun refresh() {
        val controllers = bound.map { it.first }
        val chosen = controllers.firstOrNull { isPlaying(it) }
            ?: current?.let { cur -> controllers.firstOrNull { it.sessionToken == cur.sessionToken } }
            ?: controllers.firstOrNull { it.metadata != null }
        current = chosen
        Controls.mediaController = chosen

        val meta = chosen?.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        if (chosen == null || meta == null || title.isNullOrBlank()) {
            publish(null)
            return
        }

        val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""
        val state = chosen.playbackState
        val playing = isPlaying(chosen)
        val key = "${chosen.packageName}|$title|$artist"
        val prev = IslandBus.media.value
        val sameTrack = prev?.key == key

        val info = MediaInfo(
            key = key,
            pkg = chosen.packageName,
            title = title,
            artist = artist,
            album = meta.getString(MediaMetadata.METADATA_KEY_ALBUM),
            art = if (sameTrack) prev?.art else null,
            accent = if (sameTrack && prev != null) prev.accent else Tints.Lavender,
            durationMs = meta.getLong(MediaMetadata.METADATA_KEY_DURATION),
            positionMs = state?.position ?: 0,
            positionTime = state?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = state?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            playing = playing,
            canSeek = (state?.actions ?: 0L) and PlaybackState.ACTION_SEEK_TO != 0L,
        )

        // На паузе карточка держится ещё немного и уходит, как на iPhone
        if (playing) {
            hideJob?.cancel()
            hiddenKey = null
        } else {
            if (hiddenKey == key) {
                publish(null)
                return
            }
            if (hideJob?.isActive != true || pendingHideKey != key) {
                hideJob?.cancel()
                pendingHideKey = key
                hideJob = scope.launch {
                    delay(PAUSE_KEEP_MS)
                    hiddenKey = key
                    publish(null)
                }
            }
        }

        publish(info)
        val art = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (!sameTrack || art !== lastArt) loadArt(key, art)
        if (!sameTrack) loadLyrics(info)
    }

    private fun publish(info: MediaInfo?) {
        IslandBus.media.value = info
        if (info == null) {
            lastArt = null
        }
    }

    private fun loadArt(key: String, art: Bitmap?) {
        lastArt = art
        artJob?.cancel()
        if (art == null) return
        artJob = scope.launch {
            val (image, accent) = withContext(Dispatchers.Default) {
                val small = if (art.width > 300 || art.height > 300) art.scale(300, 300 * art.height / art.width.coerceAtLeast(1)) else art
                val palette = Palette.from(small).maximumColorCount(16).generate()
                val rgb = palette.getVibrantColor(palette.getLightVibrantColor(palette.getDominantColor(0xFFC6C3F7.toInt())))
                small.asImageBitmap() to Color(rgb)
            }
            val m = IslandBus.media.value
            if (m?.key == key) IslandBus.media.value = m.copy(art = image, accent = accent)
        }
    }

    private fun loadLyrics(m: MediaInfo) {
        lyricsJob?.cancel()
        IslandBus.lyrics.value = null
        if (!SettingsRepo.current.lyricsEnabled) return
        lyricsJob = scope.launch {
            val lines = LyricsRepo.find(m.title, m.artist, m.album, m.durationMs)
            if (!lines.isNullOrEmpty() && IslandBus.media.value?.key == m.key) {
                IslandBus.lyrics.value = Lyrics(m.key, lines)
            }
        }
    }

    private companion object {
        const val PAUSE_KEEP_MS = 45_000L
    }
}
