package com.craftworks.music.desktop

import com.sun.net.httpserver.HttpServer
import com.craftworks.music.data.model.MediaData
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class AudioPlayerSmokeTest {
    @Test
    fun playsAudioFromSubsonicEndpoint() {
        assumeTrue(System.getenv("AURORA_MEDIA_SMOKE_TEST") == "1")
        val samples = 44_100 * 2
        val wave = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(capacity() - 8); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(44_100); putInt(88_200)
            putShort(2); putShort(16); put("data".toByteArray()); putInt(samples * 2)
            repeat(samples) { putShort(0) }
        }.array()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/rest/stream.view") { exchange ->
            assertTrue(exchange.requestURI.rawQuery.split("&").contains("format=raw"))
            assertTrue(!exchange.requestURI.rawQuery.contains("maxBitRate"))
            val start = exchange.requestHeaders.getFirst("Range")?.substringAfter("bytes=")?.substringBefore("-")?.toIntOrNull() ?: 0
            exchange.responseHeaders.add("Content-Type", "audio/wav")
            exchange.responseHeaders.add("Accept-Ranges", "bytes")
            if (start > 0) exchange.responseHeaders.add("Content-Range", "bytes $start-${wave.lastIndex}/${wave.size}")
            exchange.sendResponseHeaders(if (start > 0) 206 else 200, (wave.size - start).toLong())
            exchange.responseBody.use { it.write(wave, start, wave.size - start) }
        }
        server.start()
        val finished = CountDownLatch(1)
        var played = false
        var player: AudioPlayer? = null
        try {
            SwingUtilities.invokeAndWait {
                player = AudioPlayer(silentOutput = true) { state ->
                    if (state.playing) { played = true; finished.countDown() }
                    if (state.error) finished.countDown()
                }
                player!!.play(SubsonicClient("http://127.0.0.1:${server.address.port}", "test", "test", true), MediaData.Song("song", title = "Song", format = "wav"))
            }
            assertTrue("Player did not start", finished.await(20, TimeUnit.SECONDS) && played)
        } finally {
            SwingUtilities.invokeAndWait { player?.close() }
            server.stop(0)
        }
    }
}
