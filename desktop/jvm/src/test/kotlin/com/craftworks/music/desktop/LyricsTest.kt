package com.craftworks.music.desktop

import com.craftworks.music.data.model.MediaData
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsTest {
    @Test
    fun usesSharedStructuredLyricsAndLegacyFallback() {
        val fallback = AtomicBoolean(false)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/rest/") { exchange ->
            val payload = if (exchange.requestURI.path.endsWith("getLyricsBySongId.view")) {
                if (fallback.get()) """{"subsonic-response":{"status":"failed","error":{"code":20,"message":"Unsupported"}}}"""
                else """{"subsonic-response":{"status":"ok","lyricsList":{"structuredLyrics":[{"synced":true,"offset":100,"line":[{"start":1000,"value":"first"},{"start":2000,"value":"second"}]}]}}}"""
            } else {
                """{"subsonic-response":{"status":"ok","lyrics":{"value":"[00:01.00]legacy"}}}"""
            }.toByteArray()
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.start()
        try {
            val client = SubsonicClient("http://127.0.0.1:${server.address.port}", "test", "test", true)
            val song = MediaData.Song(navidromeID = "1", title = "Song", artist = "Artist")
            val synced = loadLyrics(client, song)
            assertEquals("", currentLyric(synced, 1099))
            assertEquals("first", currentLyric(synced, 1100))
            assertEquals("second", currentLyric(synced, 2100))
            fallback.set(true)
            assertEquals("legacy", currentLyric(loadLyrics(client, song), 1000))
        } finally {
            server.stop(0)
        }
    }
}
