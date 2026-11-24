package com.craftworks.music.providers.navidrome

import com.craftworks.music.data.NavidromeLibrary
import com.craftworks.music.data.model.MediaData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

@Serializable
@SerialName("subsonic-response")
data class SubsonicResponse(
    val status: String,
    val error: SubsonicError? = null,
    val version: String = "",
    val type: String = "",
    val serverVersion: String = "",
    val openSubsonic: Boolean = false,
    val musicFolders: MusicFolder? = null,
    val song: MediaData.Song? = null,
    val searchResult3: SearchResult3? = null,
    val albumList: albumList? = null,
    val albumList2: albumList? = null,
    val album: MediaData.Album? = null,
    val artists: Artists? = null,
    val artist: MediaData.Artist? = null,
    val artistInfo: MediaData.ArtistInfo? = null,
    val internetRadioStations: internetRadioStations? = null,
    val playlist: MediaData.Playlist? = null,
    val playlists: PlaylistContainer? = null,
    val lyrics: MediaData.PlainLyrics? = null,
    val lyricsList: LyricsList? = null,
    val starred: Starred? = null,
    val sonicMatch: List<MediaData.Song>? = null
)

@Serializable
@SerialName("error")
data class SubsonicError(val code: Int, val message: String)

@Serializable
data class MusicFolder(val musicFolder: List<NavidromeLibrary>)

@Serializable
data class albumList(val album: List<MediaData.Album>? = listOf())

@Serializable
@SerialName("artists")
data class Artists(val index: List<index>)

@Serializable
data class index(val artist: List<MediaData.Artist>? = listOf())

@Serializable
@SerialName("artists")
data class internetRadioStations(val internetRadioStation: List<MediaData.Radio>)

@Serializable
data class PlaylistContainer(val playlist: List<MediaData.Playlist>? = listOf())

@Serializable
@SerialName("starred")
data class Starred(
    val song: List<MediaData.Song>? = null,
    val album: List<MediaData.Album>? = null,
    val artist: List<MediaData.Artist>? = null
)

private val subsonicJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

fun decodeSubsonicResponse(response: String): SubsonicResponse =
    subsonicJson.decodeFromJsonElement(
        subsonicJson.parseToJsonElement(response).jsonObject["subsonic-response"]
            ?: throw IllegalArgumentException("Missing Subsonic response")
    )
