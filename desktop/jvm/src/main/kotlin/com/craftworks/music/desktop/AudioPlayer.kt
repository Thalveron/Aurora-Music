package com.craftworks.music.desktop

import com.craftworks.music.data.model.MediaData
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Platform
import com.sun.jna.Pointer
import java.io.IOException
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

internal data class PlaybackState(
    val positionMs: Long = 0, val durationMs: Long = 0, val playing: Boolean = false,
    val error: Boolean = false, val ended: Boolean = false,
)

internal interface Vlc : Library {
    fun libvlc_new(argc: Int, argv: Array<String>): Pointer?
    fun libvlc_release(instance: Pointer)
    fun libvlc_media_new_location(instance: Pointer, uri: String): Pointer?
    fun libvlc_media_release(media: Pointer)
    fun libvlc_media_player_new_from_media(media: Pointer): Pointer?
    fun libvlc_media_player_release(player: Pointer)
    fun libvlc_media_player_play(player: Pointer): Int
    fun libvlc_media_player_stop(player: Pointer)
    fun libvlc_media_player_pause(player: Pointer)
    fun libvlc_media_player_set_pause(player: Pointer, paused: Int)
    fun libvlc_media_player_get_state(player: Pointer): Int
    fun libvlc_media_player_get_time(player: Pointer): Long
    fun libvlc_media_player_get_length(player: Pointer): Long
    fun libvlc_media_player_set_time(player: Pointer, millis: Long)
    fun libvlc_audio_set_volume(player: Pointer, volume: Int): Int
}

private object NativePlayback {
    val api: Vlc by lazy {
        val library = if (Platform.isWindows()) {
            val dir = Path.of(requireNotNull(System.getProperty("compose.application.resources.dir")), "vlc")
            NativeLibrary.getInstance(dir.resolve("libvlccore.dll").toString())
            dir.resolve("libvlc.dll").toString()
        } else if (Platform.isMac()) "/Applications/VLC.app/Contents/MacOS/lib/libvlc.dylib" else "vlc"
        Native.load(library, Vlc::class.java, mapOf(Library.OPTION_STRING_ENCODING to "UTF-8"))
    }
}

internal class AudioPlayer(private val silentOutput: Boolean = false, private val onChange: (PlaybackState) -> Unit) : AutoCloseable {
    private val worker = Executors.newSingleThreadScheduledExecutor { Thread(it, "aurora-audio").apply { isDaemon = true } }
    private val api get() = NativePlayback.api
    private var instance: Pointer? = null
    private var player: Pointer? = null
    private var proxy: PlaybackProxy? = null
    private var level = 100
    @Volatile private var generation = 0L
    private var active = 0L
    private var last: PlaybackState? = null

    init {
        worker.scheduleWithFixedDelay({ player?.let { p ->
            val status = api.libvlc_media_player_get_state(p)
            publish(PlaybackState(api.libvlc_media_player_get_time(p).coerceAtLeast(0),
                api.libvlc_media_player_get_length(p).coerceAtLeast(0), status == 3, status == 7, status == 6))
        } }, 0, 150, TimeUnit.MILLISECONDS)
    }

    private fun publish(state: PlaybackState) {
        if (last == state) return
        last = state
        val id = active
        SwingUtilities.invokeLater { if (id == generation) onChange(state) }
    }

    private fun releaseTrack() {
        player?.let { api.libvlc_media_player_stop(it); api.libvlc_media_player_release(it) }
        player = null
        proxy?.close()
        proxy = null
    }

    private fun start(source: () -> String) {
        val id = ++generation
        onChange(PlaybackState())
        worker.execute {
            releaseTrack()
            if (id != generation) return@execute
            active = id
            last = null
            try {
                val options = mutableListOf("--no-video", "--no-spu", "--no-osd", "--no-media-library", "--no-metadata-network-access")
                if (silentOutput) options.add("--aout=dummy")
                val engine = instance ?: (api.libvlc_new(options.size, options.toTypedArray()) ?: throw IOException("Audio engine unavailable")).also { instance = it }
                val media = api.libvlc_media_new_location(engine, source()) ?: throw IOException("Cannot open original audio")
                val p = try { api.libvlc_media_player_new_from_media(media) } finally { api.libvlc_media_release(media) }
                player = p ?: throw IOException("Cannot create audio player")
                if (api.libvlc_media_player_play(p) != 0) throw IOException("Cannot play original audio")
                api.libvlc_audio_set_volume(p, level)
            } catch (_: IOException) { releaseTrack(); publish(PlaybackState(error = true)) }
            catch (_: UnsatisfiedLinkError) { releaseTrack(); publish(PlaybackState(error = true)) }
        }
    }

    fun play(client: SubsonicClient, song: MediaData.Song) = start {
        PlaybackProxy().also { proxy = it }.source(client.streamUri(song.navidromeID))
    }
    fun playLocal(track: LocalTrack) = start { track.playbackUri().toASCIIString() }
    fun playRadio(uri: URI) = start { radioSource(uri) }
    fun toggle() = worker.execute { player?.let { api.libvlc_media_player_pause(it) } }
    fun pause() = worker.execute { player?.let { api.libvlc_media_player_set_pause(it, 1) } }
    fun seek(positionMs: Long) = worker.execute { player?.let { api.libvlc_media_player_set_time(it, positionMs.coerceAtLeast(0)) } }
    fun volume(value: Float) = worker.execute {
        level = (value.coerceIn(0f, 1f) * 100).toInt()
        player?.let { api.libvlc_audio_set_volume(it, level) }
    }
    fun stop() {
        generation++
        onChange(PlaybackState())
        worker.execute(::releaseTrack)
    }
    override fun close() {
        generation++
        worker.execute { releaseTrack(); instance?.let { api.libvlc_release(it) }; instance = null }
        worker.shutdown()
    }
}

internal fun radioSource(uri: URI): String =
    requireNotNull(serverLibraryRadioUri(uri.toASCIIString())) { "Expected a credential-free HTTP(S) station URL" }.toASCIIString()
