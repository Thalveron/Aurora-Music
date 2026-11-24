package com.craftworks.music.desktop

import com.craftworks.music.providers.navidrome.SubsonicAuth
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubsonicClientTest {
    @Test
    fun connectsAndSearchesUsingSharedAndroidModels() {
        val requests = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/music/rest/") { exchange ->
            val query = exchange.requestURI.rawQuery.split("&").associate {
                val parts = it.split("=", limit = 2)
                URLDecoder.decode(parts[0], StandardCharsets.UTF_8) to
                    URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
            }
            assertEquals("tester", query["u"])
            assertEquals(SubsonicAuth.md5Hash("test-password" + query["s"]), query["t"])
            requests.add(exchange.requestURI.path)
            val result = if (exchange.requestURI.path.endsWith("search3.view")) {
                assertEquals("hello & music", query["query"])
                """{"subsonic-response":{"status":"ok","searchResult3":{"song":[{"id":"s1","title":"A"}]}}}"""
            } else {
                """{"subsonic-response":{"status":"ok","version":"1.16.1"}}"""
            }.toByteArray()
            exchange.sendResponseHeaders(200, result.size.toLong())
            exchange.responseBody.use { it.write(result) }
        }
        server.start()
        try {
            val client = SubsonicClient("http://127.0.0.1:${server.address.port}/music/rest/", "tester", "test-password", allowHttp = true)
            client.ping()
            assertEquals("s1", client.search("hello & music").song!!.single().navidromeID)
            assertEquals(listOf("/music/rest/ping.view", "/music/rest/search3.view"), requests)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun rejectsUnsafeAddressesAndRequiresHttpConfirmation() {
        val invalid = listOf(
            "http://example.test",
            "https://user:password@example.test",
            "https://example.test/?token=private",
            "ftp://example.test",
            "https://bad host.test",
        )
        invalid.forEach { address ->
            val error = runCatching { SubsonicClient(address, "tester", "test-password") }.exceptionOrNull()
            assertTrue("accepted $address", error is IllegalArgumentException)
            assertFalse(error!!.message.orEmpty().contains("test-password"))
        }
    }

    @Test
    fun surfacesServerAuthenticationErrors() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val result = """{"subsonic-response":{"status":"failed","error":{"code":40,"message":"Wrong username or password"}}}""".toByteArray()
            exchange.sendResponseHeaders(200, result.size.toLong())
            exchange.responseBody.use { it.write(result) }
        }
        server.start()
        try {
            val client = SubsonicClient("http://127.0.0.1:${server.address.port}", "tester", "test-password", allowHttp = true)
            val error = runCatching { client.ping() }.exceptionOrNull()
            assertTrue(error?.message.orEmpty().contains("40"))
            assertFalse(error?.message.orEmpty().contains("test-password"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun refusesRedirectsAndMissingSearchResults() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var redirected = false
        server.createContext("/redirect/rest/ping.view") { exchange ->
            exchange.responseHeaders.add("Location", "/target")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/target") { exchange ->
            redirected = true
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.createContext("/rest/search3.view") { exchange ->
            val result = """{"subsonic-response":{"status":"ok"}}""".toByteArray()
            exchange.sendResponseHeaders(200, result.size.toLong())
            exchange.responseBody.use { it.write(result) }
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val redirectedClient = SubsonicClient("$base/redirect", "tester", "test-password", allowHttp = true)
            val redirectError = runCatching { redirectedClient.ping() }.exceptionOrNull()
            assertTrue(redirectError?.message.orEmpty().contains("302"))
            assertFalse(redirected)

            val client = SubsonicClient(base, "tester", "test-password", allowHttp = true)
            val searchError = runCatching { client.search("music") }.exceptionOrNull()
            assertEquals("Music server returned no search result", searchError?.message)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun browsesAlbumsAndPlaylistEntries() {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/rest/") { exchange ->
                val payload = when (exchange.requestURI.path.substringAfterLast("/")) {
                    "getAlbumList2.view" -> """"albumList2":{"album":[{"id":"a","name":"Album"}]}"""
                    "getAlbum.view" -> """"album":{"id":"a","name":"Album","song":[{"id":"s","title":"Track"}]}"""
                    "getPlaylists.view" -> """"playlists":{"playlist":[{"id":"p","name":"Playlist"}]}"""
                    "getPlaylist.view" -> """"playlist":{"id":"p","name":"Playlist","created":"","changed":"","songCount":1,"duration":2,"entry":[{"id":"s","title":"Track"}]}"""
                    else -> error("Unexpected endpoint")
                }
                val bytes = """{"subsonic-response":{"status":"ok",$payload}}""".toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
            try {
                val client = SubsonicClient("http://127.0.0.1:${server.address.port}", "tester", "test-password", allowHttp = true)
                assertEquals("a", client.albums().single().navidromeID)
                assertEquals("s", client.album("a").songs!!.single().navidromeID)
                assertEquals("p", client.playlists().single().navidromeID)
                assertEquals("s", client.playlist("p").songs!!.single().navidromeID)
            } finally {
                server.stop(0)
        }
    }
}
