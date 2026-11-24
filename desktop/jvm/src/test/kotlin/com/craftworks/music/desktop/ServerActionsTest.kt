package com.craftworks.music.desktop

import com.craftworks.music.data.model.MediaData
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ServerActionsTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun removesDisplayedOccurrenceAndRejectsStaleList() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val mutations = mutableListOf<String>()
        server.createContext("/rest/") { e ->
            val data = if (e.requestURI.path.endsWith("/getPlaylist.view")) {
                ""","playlist":{"id":"p","name":"mix","entry":[{"id":"s","title":"A"},{"id":"s","title":"A"}]}"""
            } else { mutations.add(e.requestURI.rawQuery); "" }
            val body = """{"subsonic-response":{"status":"ok"$data}}""".toByteArray()
            e.sendResponseHeaders(200, body.size.toLong())
            e.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val client = SubsonicClient("http://127.0.0.1:${server.address.port}", "test", "fake", true)
            val song = MediaData.Song("s", title = "A")
            client.removePlaylistEntry("p", 1, listOf(song, song))
            assertThrows(IOException::class.java) { client.removePlaylistEntry("p", 0, listOf(song)) }
            assertTrue(mutations.single().split("&").contains("songIndexToRemove=1"))
        } finally { server.stop(0) }
    }

    @Test fun rejectsErrorDocumentsAndNeverOverwritesDownloads() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var type = "application/json"
        server.createContext("/rest/download.view") { e ->
            e.responseHeaders.set("Content-Type", type)
            e.sendResponseHeaders(200, 3)
            e.responseBody.use { it.write(byteArrayOf(1, 2, 3)) }
        }
        server.start()
        try {
            val client = SubsonicClient("http://127.0.0.1:${server.address.port}", "test", "fake", true)
            val song = MediaData.Song("s", title = "../a:b", format = "FLAC")
            val dir = folder.root.toPath()
            assertThrows(IOException::class.java) { client.downloadSong(song, dir) }
            assertEquals(0L, Files.list(dir).use { it.count() })
            type = "audio/flac"
            val saved = client.downloadSong(song, dir)
            assertEquals(dir, saved.parent)
            Files.writeString(saved, "keep")
            assertThrows(IOException::class.java) { client.downloadSong(song, dir) }
            assertEquals("keep", Files.readString(saved))
        } finally { server.stop(0) }
    }
}
