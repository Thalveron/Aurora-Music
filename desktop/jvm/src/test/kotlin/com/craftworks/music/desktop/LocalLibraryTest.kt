package com.craftworks.music.desktop

import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalLibraryTest {
    @get:Rule val fixture = TemporaryFolder(File("build"))

    @Test fun selectedFoldersPersistAndRecursiveResultsAreUniqueAndSearchable() = runBlocking {
        val settingsFile = fixture.root.toPath().resolve("settings/folders.json")
        val settings = LocalFoldersStore(settingsFile)
        val root = fixture.newFolder("chosen").toPath().toRealPath()
        val album = Files.createDirectory(root.resolve("星空"))
        val file = Files.createFile(album.resolve("Artist - Moon #1%.MP3"))
        Files.createFile(album.resolve("not-a-song.txt"))
        Files.createFile(fixture.root.toPath().resolve("unselected.wav"))
        assertTrue(scanLocalFolders(settings.load()).tracks.isEmpty())
        settings.save(listOf(root, album))
        val restored = LocalFoldersStore(settingsFile).load()
        assertEquals(listOf(root, album), restored)
        val result = scanLocalFolders(restored + listOf(root.resolve("unplugged")))
        assertEquals(1, result.skipped)
        val track = result.tracks.single()
        assertEquals(file.toUri(), track.playbackUri())
        assertEquals("Moon #1%", track.song.title)
        for (query in listOf("  artist ", "MOON", "星空")) assertEquals(listOf(track), searchLocalTracks(result.tracks, query))
        assertTrue(searchLocalTracks(result.tracks, "unselected").isEmpty())
        settings.save(emptyList())
        assertTrue(LocalFoldersStore(settingsFile).load().isEmpty())
        Files.delete(file)
        assertThrows(IOException::class.java) { track.playbackUri() }
        Unit
    }

    @Test fun sharedParserHandlesSiblingLyricsAndRescanReflectsRemoval() = runBlocking {
        val root = fixture.newFolder("album").toPath().toRealPath()
        val file = Files.createFile(root.resolve("track.wav"))
        val track = scanLocalFolders(listOf(root)).tracks.single()
        assertTrue(loadLocalLyrics(track).isEmpty())
        val lrc = Files.write(root.resolve("track.lrc"), "[offset:-50]\n[00:01.00]<00:01.00>星<00:01.50>空".toByteArray(Charsets.UTF_16))
        val lyric = loadLocalLyrics(track).single()
        assertEquals(950, lyric.startMs)
        assertEquals(listOf("星空"), lyric.text)
        assertEquals(listOf("星", "空"), lyric.words!!.map { it.text })
        Files.write(lrc, ByteArray(1024 * 1024 + 1))
        assertThrows(IOException::class.java) { runBlocking { loadLocalLyrics(track) } }
        Files.delete(file)
        assertTrue(scanLocalFolders(listOf(root)).tracks.isEmpty())
    }

    @Test fun linkedDirectoriesAndLyricSidecarsDoNotEscapeSelection() = runBlocking {
        val root = fixture.newFolder("chosen").toPath().toRealPath()
        val other = fixture.newFolder("outside").toPath().toRealPath()
        Files.createFile(root.resolve("inside.wav"))
        Files.createFile(other.resolve("outside.wav"))
        Files.writeString(other.resolve("outside.lrc"), "Not selected")
        try {
            Files.createSymbolicLink(root.resolve("external"), other)
            Files.createSymbolicLink(root.resolve("inside.lrc"), other.resolve("outside.lrc"))
        } catch (e: IOException) {
            assumeNoException("Symbolic link creation unavailable", e)
        }
        val track = scanLocalFolders(listOf(root)).tracks.single()
        assertEquals("inside", track.song.title)
        assertTrue(loadLocalLyrics(track).isEmpty())
    }

    @Test fun radioAcceptsOnlyDirectHttpStationsWithoutUserInfo() {
        val valid = "https://radio.example.test/live.mp3"
        assertEquals(valid, radioSource(URI(valid)))
        for (url in listOf("file:///station.wav", "http:relative", "/relative", "ftp://host/stream",
            "https://user:password@host/stream", "https://@host/stream", "https://host/stream?u=test&t=fake&s=salt")) {
            assertThrows(IllegalArgumentException::class.java) { radioSource(URI(url)) }
        }
    }
}
