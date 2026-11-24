package com.craftworks.music.desktop

import com.craftworks.music.data.model.MediaData
import com.craftworks.music.providers.navidrome.SearchResult3
import com.craftworks.music.providers.navidrome.decodeSubsonicResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SharedModelsTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun acceptsSongsFromServersThatOmitCreationDate() {
        val result = json.decodeFromString<SearchResult3>(
            """{"song":[{"id":"s1","title":"A","artist":"B"}]}"""
        )
        val song = result.song!!.single()
        assertEquals("s1", song.navidromeID)
        assertEquals("", song.dateAdded)
        assertEquals("B", song.artist)
    }

    @Test
    fun acceptsOptionalAlbumAndLyricMetadata() {
        val album = json.decodeFromString<MediaData.Album>("""{"id":"a1","name":"Album"}""")
        val lyrics = json.decodeFromString<MediaData.StructuredLyrics>("""{"line":[]}""")
        assertEquals(0, album.songCount)
        assertEquals("", album.artist)
        assertEquals("", lyrics.lang)
        assertFalse(lyrics.synced)
    }

    @Test
    fun acceptsStandardSubsonicPingWithoutOpenSubsonicExtensions() {
        val response = decodeSubsonicResponse(
            """{"subsonic-response":{"status":"ok","version":"1.16.1"}}"""
        )
        assertEquals("ok", response.status)
        assertEquals("", response.serverVersion)
        assertFalse(response.openSubsonic)
    }
}
