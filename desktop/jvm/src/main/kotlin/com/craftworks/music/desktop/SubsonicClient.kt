package com.craftworks.music.desktop

import com.craftworks.music.data.model.MediaData
import com.craftworks.music.providers.navidrome.SearchResult3
import com.craftworks.music.providers.navidrome.SubsonicAuth
import com.craftworks.music.providers.navidrome.SubsonicResponse
import com.craftworks.music.providers.navidrome.decodeSubsonicResponse
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

internal class SubsonicHttpException(val status: Int) : IOException("Music server returned HTTP $status")
internal class SubsonicServerException(val code: Int, message: String) :
    IOException("Music server error $code: $message")

/**
 * Windows transport for the shared Android Subsonic models and authentication.
 * The server address and credentials must never be logged.
 */
class SubsonicClient(
    address: String,
    private val username: String,
    private val password: String,
    allowHttp: Boolean = false,
) {
    private val base: URI
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    init {
        val parsed = try {
            URI(address.trim())
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid server address")
        }
        require(parsed.scheme == "https" || parsed.scheme == "http") { "Use an HTTP(S) server address" }
        require(parsed.scheme != "http" || allowHttp) { "Unencrypted HTTP requires explicit confirmation" }
        require(!parsed.host.isNullOrBlank() && parsed.userInfo == null && parsed.query == null && parsed.fragment == null) {
            "Server address must not contain credentials, query, or fragment"
        }
        require(username.isNotBlank() && password.isNotEmpty()) { "Username and password are required" }
        base = URI(parsed.toASCIIString().trimEnd('/'))
    }

    fun ping() {
        request("ping")
    }

    fun search(query: String, offset: Int = 0, count: Int = 100): SearchResult3 {
        require(offset >= 0 && count in 1..500) { "Invalid search range" }
        return request(
            "search3",
            mapOf(
                "query" to query,
                "songOffset" to offset.toString(),
                "songCount" to count.toString(),
                "albumCount" to count.toString(),
                "artistCount" to count.toString(),
            )
        ).searchResult3 ?: throw IOException("Music server returned no search result")
    }

    fun albums(sort: String = "alphabeticalByName", offset: Int = 0, count: Int = 100): List<MediaData.Album> {
        require(sort in setOf("alphabeticalByName", "newest", "recent", "frequent", "starred")) {
            "Unsupported album sort"
        }
        require(offset >= 0 && count in 1..500) { "Invalid album range" }
        val result = request("getAlbumList2", mapOf("type" to sort, "offset" to "$offset", "size" to "$count"))
        return (result.albumList2 ?: throw IOException("Music server returned no album list")).album.orEmpty()
    }

    fun playlists(): List<MediaData.Playlist> =
        (request("getPlaylists").playlists ?: throw IOException("Music server returned no playlist list")).playlist.orEmpty()

    fun album(id: String): MediaData.Album {
        require(id.isNotBlank()) { "Album ID is required" }
        return request("getAlbum", mapOf("id" to id)).album
            ?: throw IOException("Music server returned no album")
    }

    fun playlist(id: String): MediaData.Playlist {
        require(id.isNotBlank()) { "Playlist ID is required" }
        return request("getPlaylist", mapOf("id" to id)).playlist
            ?: throw IOException("Music server returned no playlist")
    }

    fun syncedLyrics(songId: String): List<MediaData.StructuredLyrics> {
        require(songId.isNotBlank()) { "Song ID is required" }
        return (request("getLyricsBySongId", mapOf("id" to songId)).lyricsList
            ?: throw IOException("Music server returned no lyrics result")).structuredLyrics.orEmpty()
    }

    fun plainLyrics(artist: String, title: String): String? =
        request("getLyrics", mapOf("artist" to artist, "title" to title)).lyrics?.value

    internal fun streamUri(songId: String, transcode: Boolean = false): URI {
        require(songId.isNotBlank()) { "Song ID is required" }
        val parameters = if (transcode) mapOf("id" to songId, "format" to "mp3") else mapOf("id" to songId)
        return endpointUri("stream", parameters)
    }

    private fun endpointUri(endpoint: String, parameters: Map<String, String>): URI {
        val salt = SubsonicAuth.generateSalt(8)
        val query = parameters + mapOf(
            "u" to username,
            "t" to SubsonicAuth.md5Hash(password + salt),
            "s" to salt,
            "v" to "1.16.1",
            "c" to "Chora",
            "f" to "json",
        )
        val encoded = query.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
        return URI("${base.toASCIIString()}/rest/$endpoint.view?$encoded")
    }

    private fun request(endpoint: String, parameters: Map<String, String> = emptyMap()): SubsonicResponse {
        val request = HttpRequest.newBuilder(endpointUri(endpoint, parameters)).timeout(Duration.ofSeconds(30)).GET().build()
        val response = try {
            http.send(request, HttpResponse.BodyHandlers.ofInputStream())
        } catch (_: IOException) {
            throw IOException("Could not connect to the music server")
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Music server request interrupted")
        }
        response.body().use { body ->
            if (response.statusCode() != 200) {
                throw SubsonicHttpException(response.statusCode())
            }
            val bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1)
            if (bytes.size > MAX_RESPONSE_BYTES) {
                throw IOException("Music server response is too large")
            }
            val parsed = try {
                decodeSubsonicResponse(bytes.toString(StandardCharsets.UTF_8))
            } catch (_: IllegalArgumentException) {
                throw IOException("Music server returned an invalid response")
            }
            if (parsed.status != "ok") {
                val error = parsed.error
                if (error == null) throw IOException("Music server rejected the request")
                throw SubsonicServerException(error.code, error.message)
            }
            return parsed
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private companion object {
        const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
    }
}
