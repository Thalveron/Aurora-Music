package com.craftworks.music.providers.navidrome

import com.craftworks.music.data.model.MediaData
import kotlinx.serialization.Serializable

@Serializable
data class LyricsList(
    val structuredLyrics: List<MediaData.StructuredLyrics>? = null
)

@Serializable
data class SyncedLyrics(
    val start: Int? = null,
    val value: String
)
