package com.craftworks.music.desktop

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerLibraryTest {
    @Test fun browsesSharedModelsAndWritesTypedFavorites() {
        val requests = mutableListOf<Pair<String, Map<String, String>>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/rest/") { exchange ->
            val method = exchange.requestURI.path.substringAfterLast('/').removeSuffix(".view")
            val params = exchange.requestURI.rawQuery.split('&').associate {
                it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), StandardCharsets.UTF_8)
            }
            requests += method to params.filterKeys { it !in listOf("u", "t", "s", "v", "c", "f") }
            val body = when (method) {
                "getArtists" -> ""","artists":{"index":[{"artist":[{"name":"Artist","id":"a/b"}]},{"artist":[{"id":"a/b"},{"id":"c"}]}]}"""
                "getArtist" -> ""","artist":{"id":"a/b","album":[{"id":"album","name":"Album"}]}"""
                "getArtistInfo" -> ""","artistInfo":{"biography":"Some bio"}"""
                "getStarred" -> ""","starred":{"artist":[{"id":"ar"}],"album":[{"id":"al"}],"song":[{"id":"s","title":"Song"}]}"""
                "getInternetRadioStations" -> ""","internetRadioStations":{"internetRadioStation":[{"id":"r","name":"R","streamUrl":"https://radio.example/stream"}]}"""
                else -> ""
            }
            val result = """{"subsonic-response":{"status":"ok"$body}}""".toByteArray()
            exchange.sendResponseHeaders(200, result.size.toLong())
            exchange.responseBody.use { it.write(result) }
        }
        server.start()
        try {
            val client = SubsonicClient("http://127.0.0.1:${server.address.port}", "u", "p", true)
            assertEquals(listOf("a/b", "c"), client.artists().map { it.navidromeID })
            assertEquals("album", client.artist("a/b").album!!.single().navidromeID)
            assertEquals("Some bio", client.artistInfo("a/b")?.biography)
            with(client.favorites()) {
                assertEquals("ar", artist!!.single().navidromeID)
                assertEquals("al", album!!.single().navidromeID)
                assertEquals("s", song!!.single().navidromeID)
            }
            assertEquals("https://radio.example/stream", client.internetRadioStations().single().media)
            client.setFavorite(SubsonicClient.FavoriteKind.ARTIST, "a/b", true)
            client.setFavorite(SubsonicClient.FavoriteKind.ALBUM, "x&y", false)
            client.setFavorite(SubsonicClient.FavoriteKind.SONG, "s", true)
            assertEquals(
                listOf("star" to mapOf("artistId" to "a/b"), "unstar" to mapOf("albumId" to "x&y"), "star" to mapOf("id" to "s")),
                requests.takeLast(3),
            )
            assertEquals(mapOf("id" to "a/b"), requests[1].second)
            assertEquals(mapOf("id" to "a/b"), requests[2].second)
            val stream = URI("https://radio.example/play?a=1&b=2")
            client.saveRadioStation(null, "A & B", stream, null)
            client.saveRadioStation("r", "New", stream, null)
            client.deleteRadioStation("r")
            assertEquals(listOf(
                "createInternetRadioStation" to mapOf("name" to "A & B", "streamUrl" to stream.toASCIIString()),
                "updateInternetRadioStation" to mapOf("id" to "r", "name" to "New", "streamUrl" to stream.toASCIIString(), "homepageUrl" to ""),
                "deleteInternetRadioStation" to mapOf("id" to "r"),
            ), requests.takeLast(3))
        } finally {
            server.stop(0)
        }
    }

    @Test fun acceptsEmptyArtistAndRadioContainers() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/rest/") {
            val field = if (it.requestURI.path.endsWith("getArtists.view")) "artists" else "internetRadioStations"
            val result = """{"subsonic-response":{"status":"ok","$field":{}}}""".toByteArray()
            it.sendResponseHeaders(200, result.size.toLong())
            it.responseBody.use { out -> out.write(result) }
        }
        server.start()
        try {
            val client = SubsonicClient("http://127.0.0.1:${server.address.port}", "u", "p", true)
            assertEquals(emptyList<Any>(), client.artists())
            assertEquals(emptyList<Any>(), client.internetRadioStations())
        } finally {
            server.stop(0)
        }
    }

    @Test fun missingBiographyDoesNotMaskArtistAlbums() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/rest/getArtistInfo.view") { it.sendResponseHeaders(404, -1); it.close() }
        server.createContext("/rest/getArtist.view") {
            val result = """{"subsonic-response":{"status":"ok","artist":{"id":"a","album":[{"id":"b"}]}}}""".toByteArray()
            it.sendResponseHeaders(200, result.size.toLong())
            it.responseBody.use { out -> out.write(result) }
        }
        server.start()
        try {
            val client = SubsonicClient("http://127.0.0.1:${server.address.port}", "u", "p", true)
            assertThrows(SubsonicHttpException::class.java) { client.artistInfo("a") }
            assertEquals("b", client.artist("a").album!!.single().navidromeID)
        } finally {
            server.stop(0)
        }
    }

    @Test fun radioRetainsStationParametersButRejectsAuthenticatedAndNonNetworkUrls() {
        val station = "https://stream.example:8443/live?channel=A%26B&token=station"
        assertEquals(station, serverLibraryRadioUri(station)?.toASCIIString())
        listOf("file:///etc/passwd", "https://u:p@host/stream", "https:///bad", "//host/stream", "not a URL",
            "https://radio.example/stream?%75=test&t=token&s=salt", "http://host/?u=user&p=secret"
        ).forEach { assertNull(it, serverLibraryRadioUri(it)) }
    }

    @Test fun formatsBiographiesWithoutFollowingEmbeddedLinks() {
        assertEquals("First & last\nMore", serverLibraryBiography(
            """<p>First &amp; last</p><a href="https://example.invalid/">More</a><img src="file:///nope">"""
        ))
    }
}
