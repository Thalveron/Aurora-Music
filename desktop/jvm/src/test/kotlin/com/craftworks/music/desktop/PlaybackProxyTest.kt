package com.craftworks.music.desktop

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlaybackProxyTest {
    @Test
    fun forwardsRangesWithoutExposingCredentialsInLocalUrl() {
        val upstream = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        upstream.createContext("/stream") { exchange ->
            assertEquals("bytes=2-4", exchange.requestHeaders.getFirst("Range"))
            assertEquals("token=secret", exchange.requestURI.rawQuery)
            exchange.responseHeaders.add("Content-Range", "bytes 2-4/6")
            exchange.sendResponseHeaders(206, 3)
            exchange.responseBody.use { it.write("cde".toByteArray()) }
        }
        upstream.start()
        try {
            PlaybackProxy().use { proxy ->
                val local = proxy.source(URI("http://127.0.0.1:${upstream.address.port}/stream?token=secret"))
                assertFalse(local.contains("secret"))
                val request = HttpRequest.newBuilder(URI(local)).header("Range", "bytes=2-4").build()
                val result = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
                assertEquals(206, result.statusCode())
                assertEquals("cde", result.body())
                assertEquals("bytes 2-4/6", result.headers().firstValue("Content-Range").orElse(""))
                proxy.source(URI("http://127.0.0.1:${upstream.address.port}/stream?token=new"))
                assertEquals(404, HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode())
            }
        } finally {
            upstream.stop(0)
        }
    }

    @Test
    fun doesNotFollowRedirects() {
        val upstream = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var targetCalled = false
        upstream.createContext("/stream") { exchange ->
            exchange.responseHeaders.add("Location", "/target")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        upstream.createContext("/target") { exchange ->
            targetCalled = true
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        upstream.start()
        try {
            PlaybackProxy().use { proxy ->
                val local = proxy.source(URI("http://127.0.0.1:${upstream.address.port}/stream?token=secret"))
                val response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI(local)).build(), HttpResponse.BodyHandlers.discarding()
                )
                assertEquals(502, response.statusCode())
                assertFalse(targetCalled)
            }
        } finally {
            upstream.stop(0)
        }
    }
}
