package com.craftworks.music.desktop

import com.craftworks.music.data.model.MediaData
import java.io.IOException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

internal fun SubsonicClient.addToPlaylist(playlistId: String, songId: String) {
    request("updatePlaylist", mapOf("playlistId" to playlistId, "songIdToAdd" to songId))
}

internal fun SubsonicClient.createPlaylist(name: String, songId: String) {
    require(name.isNotBlank())
    request("createPlaylist", mapOf("name" to name.trim(), "songId" to songId))
}

internal fun SubsonicClient.deletePlaylist(id: String) {
    request("deletePlaylist", mapOf("id" to id))
}

internal fun SubsonicClient.renamePlaylist(id: String, name: String) {
    require(name.isNotBlank())
    request("updatePlaylist", mapOf("playlistId" to id, "name" to name.trim()))
}

internal fun SubsonicClient.removePlaylistEntry(id: String, position: Int, displayed: List<MediaData.Song>) {
    val latest = playlist(id).songs.orEmpty()
    if (position !in latest.indices || latest.map { it.navidromeID } != displayed.map { it.navidromeID }) {
        throw IOException("Playlist changed; reopen it before removing a track")
    }
    request("updatePlaylist", mapOf("playlistId" to id, "songIndexToRemove" to position.toString()))
}

internal fun SubsonicClient.scrobble(id: String, submission: Boolean) {
    request("scrobble", mapOf("id" to id, "submission" to submission.toString()))
}

private val downloadTransport = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
    .followRedirects(HttpClient.Redirect.NEVER).build()
private val safeExtensions = setOf("mp3", "flac", "m4a", "mp4", "aac", "wav", "ogg", "opus", "wma", "alac", "aiff", "aif", "ape", "wv", "dsf", "dff")

internal fun downloadName(song: MediaData.Song): String {
    val extension = song.format.lowercase().takeIf { it in safeExtensions } ?: throw IOException("Unknown audio file format")
    var stem = listOf(song.title, song.artist).filter { it.isNotBlank() }.joinToString(" - ")
        .replace(Regex("""[<>:"/\\|?*\p{Cntrl}]"""), "_").take(160).trim().trimEnd('.', ' ')
    if (stem.isEmpty()) stem = "Track"
    if (stem.substringBefore('.').uppercase().matches(Regex("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]"))) stem = "_$stem"
    return "$stem.$extension"
}

internal fun SubsonicClient.downloadSong(song: MediaData.Song, directory: Path): Path {
    val target = directory.resolve(downloadName(song))
    if (Files.exists(target)) throw IOException("File already exists: ${target.fileName}")
    val request = HttpRequest.newBuilder(endpointUri("download", mapOf("id" to song.navidromeID))).GET()
        .timeout(Duration.ofMinutes(5)).build()
    val response = try {
        downloadTransport.send(request, HttpResponse.BodyHandlers.ofInputStream())
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        throw IOException("Download interrupted")
    } catch (_: IOException) {
        throw IOException("Could not download from the music server")
    }
    response.body().use { input ->
        if (response.statusCode() != 200) throw SubsonicHttpException(response.statusCode())
        val type = response.headers().firstValue("Content-Type").orElse("").lowercase()
        if (type.startsWith("text/") || "json" in type || "xml" in type) throw IOException("Server returned a document instead of an audio file")
        val temp = Files.createTempFile(directory, ".aurora-", ".part")
        try {
            val bytes = Files.newOutputStream(temp).use { input.transferTo(it) }
            val expected = response.headers().firstValueAsLong("Content-Length")
            if (bytes == 0L || expected.isPresent && expected.asLong != bytes) throw IOException("Incomplete audio download")
            return Files.move(temp, target)
        } finally {
            Files.deleteIfExists(temp)
        }
    }
}
