package com.craftworks.music.desktop

import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executors

/** Keeps Subsonic credentials out of the media player's URLs and rejects redirects. */
internal class PlaybackProxy : AutoCloseable {
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()
    private val executor = Executors.newCachedThreadPool { task ->
        Thread(task, "aurora-playback-proxy").apply { isDaemon = true }
    }
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    @Volatile private var source: Pair<String, URI>? = null

    init {
        server.executor = executor
        server.createContext("/") { exchange ->
            val current = source
            if (current == null || exchange.requestURI.path != "/${current.first}" ||
                exchange.requestMethod !in setOf("GET", "HEAD")) {
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
            } else {
                try {
                    val builder = HttpRequest.newBuilder(current.second).timeout(Duration.ofSeconds(30))
                    val range = exchange.requestHeaders.getFirst("Range")
                    if (range != null) {
                        if (!range.matches(Regex("bytes=(\\d+-\\d*|-\\d+)"))) {
                            exchange.sendResponseHeaders(416, -1)
                            return@createContext
                        }
                        builder.header("Range", range)
                    }
                    builder.method(exchange.requestMethod, HttpRequest.BodyPublishers.noBody())
                    val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
                    response.body().use { body ->
                        if (response.statusCode() !in setOf(200, 206)) {
                            exchange.sendResponseHeaders(502, -1)
                        } else {
                            listOf("Content-Type", "Content-Length", "Content-Range", "Accept-Ranges").forEach { header ->
                                response.headers().firstValue(header).ifPresent { exchange.responseHeaders.set(header, it) }
                            }
                            val length = response.headers().firstValueAsLong("Content-Length").orElse(0)
                            exchange.sendResponseHeaders(response.statusCode(), if (exchange.requestMethod == "HEAD") -1 else length)
                            if (exchange.requestMethod == "GET") exchange.responseBody.use { body.transferTo(it) }
                        }
                    }
                } catch (_: IOException) {
                    // Closing a player while a stream is active can close either side of the pipe.
                    runCatching { exchange.sendResponseHeaders(502, -1) }
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    runCatching { exchange.sendResponseHeaders(502, -1) }
                } finally {
                    exchange.close()
                }
            }
        }
        server.start()
    }

    fun source(uri: URI): String {
        val token = UUID.randomUUID().toString()
        source = token to uri
        return "http://127.0.0.1:${server.address.port}/$token"
    }

    override fun close() {
        source = null
        server.stop(0)
        executor.shutdownNow()
    }
}
