package com.craftworks.music.desktop

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.swing.SwingUtilities

class LocalPlaybackSmokeTest {
    @get:Rule val fixture = TemporaryFolder(File("build"))

    @Test fun commonPlayerStartsSelectedWaveWithoutServer() {
        assumeTrue(System.getenv("AURORA_LOCAL_MEDIA_SMOKE_TEST") == "1")
        val root = fixture.root.toPath().toRealPath()
        val wave = root.resolve("Fixture #1 %.wav")
        val format = AudioFormat(44100f, 16, 1, true, false)
        AudioInputStream(ByteArray(2 * 44100).inputStream(), format, 44100).use {
            Files.newOutputStream(wave).use { out -> AudioSystem.write(it, AudioFileFormat.Type.WAVE, out) }
        }
        val track = runBlocking { scanLocalFolders(listOf(root)).tracks.single() }
        val finished = CountDownLatch(1)
        var playing = false
        var player: AudioPlayer? = null
        try {
            SwingUtilities.invokeAndWait {
                player = AudioPlayer(silentOutput = true) {
                    if (it.playing) playing = true
                    if (playing || it.error) finished.countDown()
                }.also { it.playLocal(track) }
            }
            assertTrue("Local WAV did not start", finished.await(20, TimeUnit.SECONDS) && playing)
        } finally {
            SwingUtilities.invokeAndWait { player?.close() }
        }
    }
}
