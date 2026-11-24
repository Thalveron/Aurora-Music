package com.craftworks.music.desktop

import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import com.craftworks.music.data.model.MediaData
import java.io.IOException
import java.net.URI
import javax.swing.SwingUtilities

internal data class PlaybackState(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val playing: Boolean = false,
    val error: Boolean = false,
    val ended: Boolean = false,
)

internal class AudioPlayer(private val onChange: (PlaybackState) -> Unit) : AutoCloseable {
    private var proxy: PlaybackProxy? = null
    private var player: MediaPlayer? = null
    private var generation = 0
    private var state = PlaybackState()
    @Volatile private var volume = 1.0

    init {
        JFXPanel()
        Platform.setImplicitExit(false)
    }

    fun play(client: SubsonicClient, song: MediaData.Song, maxBitRate: Int = 0) {
        val transcode = maxBitRate > 0 || song.format.lowercase() in setOf("flac", "ogg", "opus", "wma", "alac")
        val activeProxy = proxy ?: PlaybackProxy().also { proxy = it }
        val url = activeProxy.source(client.streamUri(song.navidromeID, transcode, maxBitRate))
        playSource { url }
    }

    fun playLocal(track: LocalTrack) {
        closeProxy()
        playSource { track.playbackUri().toASCIIString() }
    }

    fun playRadio(uri: URI) {
        val url = radioSource(uri)
        closeProxy()
        playSource { url }
    }

    private fun playSource(source: () -> String) {
        val id = ++generation
        state = PlaybackState()
        onChange(state)
        Platform.runLater {
            player?.dispose()
            player = null
            fun update(transform: (PlaybackState) -> PlaybackState) {
                SwingUtilities.invokeLater {
                    if (id == generation) {
                        state = transform(state)
                        onChange(state)
                    }
                }
            }
            try {
                val next = MediaPlayer(Media(source()))
                next.volume = volume
                player = next
                next.currentTimeProperty().addListener { _, _, time ->
                    update { it.copy(positionMs = time.toMillis().toLong().coerceAtLeast(0)) }
                }
                next.setOnReady {
                    val duration = next.totalDuration.toMillis()
                    update { it.copy(durationMs = if (duration.isFinite()) duration.toLong().coerceAtLeast(0) else 0) }
                    next.play()
                }
                next.setOnPlaying { update { it.copy(playing = true, ended = false) } }
                next.setOnPaused { update { it.copy(playing = false) } }
                next.setOnEndOfMedia { update { it.copy(playing = false, ended = true) } }
                next.setOnError { update { it.copy(playing = false, error = true) } }
                next.media.setOnError { update { it.copy(playing = false, error = true) } }
            } catch (_: IOException) {
                update { it.copy(playing = false, error = true) }
            } catch (_: RuntimeException) {
                update { it.copy(playing = false, error = true) }
            }
        }
    }

    fun toggle() {
        Platform.runLater {
            player?.let {
                if (it.status == MediaPlayer.Status.PLAYING) it.pause() else it.play()
            }

        }
    }

    fun pause() { Platform.runLater { player?.pause() } }

    fun seek(positionMs: Long) {
        Platform.runLater { player?.seek(Duration.millis(positionMs.coerceAtLeast(0).toDouble())) }
    }

    fun volume(value: Float) {
        volume = value.coerceIn(0f, 1f).toDouble()
        Platform.runLater { player?.volume = volume }
    }

    fun stop() {
        closeProxy()
        generation++
        state = PlaybackState()
        onChange(state)
        Platform.runLater { player?.dispose(); player = null }
    }

    override fun close() {
        stop()
        Platform.exit()
    }

    private fun closeProxy() {
        proxy?.close()
        proxy = null
    }
}

internal fun radioSource(uri: URI): String {
    return requireNotNull(serverLibraryRadioUri(uri.toASCIIString())) {
        "Expected a credential-free HTTP(S) station URL"
    }.toASCIIString()
}
