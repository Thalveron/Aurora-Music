package com.craftworks.music.providers.navidrome

import com.craftworks.music.data.model.MediaData
import kotlinx.serialization.Serializable

@Serializable
data class SearchResult3(
    val song: List<MediaData.Song>? = listOf(),
    val album: List<MediaData.Album>? = listOf(),
    val artist: List<MediaData.Artist>? = listOf(),
)
