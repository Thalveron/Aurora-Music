package com.craftworks.music.desktop

import com.craftworks.music.data.model.Lyric
import com.craftworks.music.data.model.MediaData
import com.craftworks.music.data.model.parseLocalLrc
import com.craftworks.music.data.model.toLyrics
import java.io.IOException

internal fun loadLyrics(client: SubsonicClient, song: MediaData.Song): List<Lyric> {
    val structured = try {
        client.syncedLyrics(song.navidromeID)
    } catch (e: SubsonicServerException) {
        if (e.code != 0 && e.code != 20) throw e
        emptyList()
    } catch (e: SubsonicHttpException) {
        if (e.status != 404 && e.status != 501) throw e
        emptyList()
    }
    val selected = structured.firstOrNull { it.synced && it.line.isNotEmpty() }
        ?: structured.firstOrNull { it.line.isNotEmpty() }
    if (selected != null) {
        if (selected.synced && selected.line.any { it.start == null }) {
            throw IOException("Music server returned invalid synchronized lyrics")
        }
        return selected.toLyrics()
    }
    return client.plainLyrics(song.artist, song.title)?.let(::parseLocalLrc).orEmpty()
}

internal fun currentLyric(lyrics: List<Lyric>, positionMs: Long): String {
    val line = if (lyrics.firstOrNull()?.startMs == -1) lyrics.firstOrNull()
    else lyrics.lastOrNull { it.startMs <= positionMs }
    return line?.text?.joinToString("\n").orEmpty()
}
