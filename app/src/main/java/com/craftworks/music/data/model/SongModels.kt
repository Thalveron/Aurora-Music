package com.craftworks.music.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class Genre(
    val name: String? = ""
)

@Immutable
@Serializable
data class ReplayGain(
    val trackGain: Float? = 0f,
    //val trackPeak: Float? = 0f,
    //val albumPeak: Float? = 0f
)

@Immutable
@Serializable
data class Artists(
    val id: String? = "",
    val name: String? = ""
)
