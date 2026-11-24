package com.craftworks.music.desktop

import com.craftworks.music.data.model.MediaData
import com.craftworks.music.providers.navidrome.SearchResult3
import com.craftworks.music.providers.navidrome.Starred
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

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
        } catch (_: java.net.URISyntaxException) {
            throw IllegalArgumentException("Invalid server address")
        }
        require(parsed.scheme == "https" || parsed.scheme == "http") { "Use an HTTP(S) server address" }
        require(parsed.scheme != "http" || allowHttp) { "Unencrypted HTTP requires explicit confirmation" }
        require(!parsed.host.isNullOrBlank() && parsed.userInfo == null && parsed.query == null && parsed.fragment == null) {
            "Server address must not contain credentials, query, or fragment"
        }
        require(username.isNotBlank() && password.isNotEmpty()) { "Username and password are required" }
        base = URI(parsed.toASCIIString().trimEnd('/').removeSuffix("/rest"))
    }

    fun ping() {
        request("ping")
    }

    fun musicFolders() = request("getMusicFolders").musicFolders?.musicFolder
        ?: throw IOException("Music server returned no folder list")

    fun search(query: String, offset: Int = 0, count: Int = 100, folder: Int? = null): SearchResult3 {
        require(offset >= 0 && count in 1..500) { "Invalid search range" }
        return request(
            "search3",
            mapOf(
                "query" to query,
                "songOffset" to offset.toString(),
                "songCount" to count.toString(),
                "albumCount" to count.toString(),
                "artistCount" to count.toString(),
            ) + folderParameters(folder)
        ).searchResult3 ?: throw IOException("Music server returned no search result")
    }

    private fun folderParameters(folder: Int?) = if (folder == null) emptyMap() else mapOf("musicFolderId" to "$folder")

    fun albums(sort: String = "alphabeticalByName", offset: Int = 0, count: Int = 100, folder: Int? = null): List<MediaData.Album> {
        require(sort in setOf("alphabeticalByName", "newest", "recent", "frequent", "starred", "random")) {
            "Unsupported album sort"
        }
        require(offset >= 0 && count in 1..500) { "Invalid album range" }
        val result = request("getAlbumList2", mapOf("type" to sort, "offset" to "$offset", "size" to "$count") + folderParameters(folder))
        return (result.albumList2 ?: throw IOException("Music server returned no album list")).album.orEmpty()
    }

    fun playlists(): List<MediaData.Playlist> =
        (request("getPlaylists").playlists ?: throw IOException("Music server returned no playlist list")).playlist.orEmpty()

    fun artists(): List<MediaData.Artist> =
        request("getArtists").artists?.index.orEmpty().flatMap { it.artist.orEmpty() }
            .filter { it.navidromeID.isNotBlank() }.distinctBy { it.navidromeID }

    fun artist(id: String): MediaData.Artist {
        require(id.isNotBlank()) { "Artist ID is required" }
        return request("getArtist", mapOf("id" to id)).artist ?: throw IOException("Music server returned no artist")
    }

    fun artistInfo(id: String): MediaData.ArtistInfo? {
        require(id.isNotBlank()) { "Artist ID is required" }
        return request("getArtistInfo", mapOf("id" to id)).artistInfo
    }

    fun favorites(): Starred = request("getStarred").starred ?: Starred()

    fun internetRadioStations(): List<MediaData.Radio> =
        request("getInternetRadioStations").internetRadioStations?.internetRadioStation.orEmpty()

    fun saveRadioStation(id: String?, name: String, stream: URI, homepage: URI?) {
        require(id == null || id.isNotBlank()) { "Station ID is required" }
        require(name.isNotBlank() && serverLibraryRadioUri(stream.toString()) != null) { "Invalid station" }
        require(homepage == null || serverLibraryRadioUri(homepage.toString()) != null) { "Invalid homepage" }
        val params = mutableMapOf("name" to name.trim(), "streamUrl" to stream.toASCIIString())
        if (homepage != null || id != null) params["homepageUrl"] = homepage?.toASCIIString().orEmpty()
        if (id != null) params["id"] = id
        request(if (id == null) "createInternetRadioStation" else "updateInternetRadioStation", params)
    }

    fun deleteRadioStation(id: String) {
        require(id.isNotBlank()) { "Station ID is required" }
        request("deleteInternetRadioStation", mapOf("id" to id))
    }

    enum class FavoriteKind(internal val parameter: String) {
        SONG("id"), ALBUM("albumId"), ARTIST("artistId")
    }

    fun setFavorite(kind: FavoriteKind, id: String, favorite: Boolean) {
        require(id.isNotBlank()) { "Item ID is required" }
        request(if (favorite) "star" else "unstar", mapOf(kind.parameter to id))
    }

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

    internal fun streamUri(songId: String): URI {
        require(songId.isNotBlank()) { "Song ID is required" }
        return endpointUri("stream", mapOf("id" to songId, "format" to "raw"))
    }

    internal fun endpointUri(endpoint: String, parameters: Map<String, String>): URI {
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

    internal fun request(endpoint: String, parameters: Map<String, String> = emptyMap()): SubsonicResponse {
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
                val text = bytes.toString(StandardCharsets.UTF_8)
                try {
                    decodeSubsonicResponse(text)
                } catch (e: IllegalArgumentException) {
                    decodeEmptyLibraryCollection(endpoint, text) ?: throw e
                }
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

    // Some servers omit the list inside an empty container; Android's shared serializers require it.
    private fun decodeEmptyLibraryCollection(endpoint: String, text: String): SubsonicResponse? {
        val (field, list) = when (endpoint) {
            "getArtists" -> "artists" to "index"
            "getInternetRadioStations" -> "internetRadioStations" to "internetRadioStation"
            else -> return null
        }
        val root = Json.parseToJsonElement(text) as? JsonObject ?: return null
        val response = root["subsonic-response"] as? JsonObject ?: return null
        val container = response[field] as? JsonObject ?: return null
        if (container[list] != null && container[list] != JsonNull) return null
        val fixed = JsonObject(container + (list to JsonArray(emptyList())))
        return decodeSubsonicResponse(JsonObject(root + ("subsonic-response" to JsonObject(response + (field to fixed)))).toString())
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
    }
}
