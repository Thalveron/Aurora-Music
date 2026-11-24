package com.craftworks.music.desktop

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

internal val offlineFormats = setOf("flac", "ogg", "opus")

internal fun bundledDecoder(): Path {
    val resources = System.getProperty("compose.application.resources.dir") ?: throw IOException("Decoder resources unavailable")
    val name = if (System.getProperty("os.name").startsWith("Windows")) "ffmpeg.exe" else "ffmpeg"
    return Path.of(resources, "decoder", name).also {
        if (!Files.isExecutable(it)) throw IOException("Bundled audio decoder unavailable")
    }
}

/** The decoder reads a single selected file on stdin, never a URL or playlist. */
internal fun decodeLocal(track: LocalTrack): Path {
    val file = Path.of(track.playbackUri())
    val demuxer = when (track.song.format) {
        "flac" -> "flac"
        "ogg", "opus" -> "ogg"
        else -> throw IOException("Unsupported offline audio format")
    }
    val decoder = bundledDecoder()
    val wave = Files.createTempFile("aurora-audio-", ".wav")
    var complete = false
    try {
        val process = ProcessBuilder(decoder.toString(), "-nostdin", "-v", "error", "-xerror",
            "-protocol_whitelist", "pipe", "-f", demuxer, "-i", "pipe:0", "-map", "0:a:0",
            "-map_metadata", "-1", "-c:a", "pcm_s16le", "-y", wave.toString())
            .redirectInput(file.toFile()).redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        try {
            if (process.waitFor() != 0 || Files.size(wave) <= 44) throw IOException("Could not decode local audio")
        } catch (_: InterruptedException) {
            process.destroyForcibly().waitFor()
            throw IOException("Offline decoding interrupted")
        }
        complete = true
        return wave
    } finally {
        if (!complete) Files.deleteIfExists(wave)
    }
}
