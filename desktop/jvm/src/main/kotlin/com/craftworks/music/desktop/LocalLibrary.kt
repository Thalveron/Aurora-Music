package com.craftworks.music.desktop

import com.craftworks.music.data.model.Lyric
import com.craftworks.music.data.model.MediaData
import com.craftworks.music.data.model.parseLocalLrc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.BasicFileAttributes

internal class LocalFoldersStore(
    private val file: Path = Path.of(
        System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "AuroraMusic", "local-folders.json",
    ),
) {
    fun load(): List<Path> {
        val json = try { Files.readString(file) } catch (_: NoSuchFileException) { return emptyList() }
        return try {
            Json.decodeFromString<List<String>>(json).map { Path.of(it) }
                .onEach { require(it.isAbsolute) }.map { it.normalize() }.distinct()
        } catch (e: IllegalArgumentException) {
            throw IOException("Invalid local folder settings", e)
        }
    }

    fun save(folders: List<Path>) {
        require(folders.all { it.isAbsolute })
        writeLibraryFile(file, Json.encodeToString(folders.map { it.normalize() }.distinct().map { it.toString() }))
    }
}

internal class LocalPlaylistsStore(private val file: Path = Path.of(
    System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "AuroraMusic", "local-playlists.json",
)) {
    fun load(): Map<String, List<String>> {
        val text = try { Files.readString(file) } catch (_: NoSuchFileException) { return emptyMap() }
        return try { Json.decodeFromString<Map<String, List<String>>>(text) }
        catch (e: IllegalArgumentException) { throw IOException("Invalid local playlists", e) }
    }

    fun save(lists: Map<String, List<String>>) = writeLibraryFile(file, Json.encodeToString(lists))
}

private fun writeLibraryFile(file: Path, text: String) {
    Files.createDirectories(file.parent)
    val next = Files.createTempFile(file.parent, "library-", ".json")
    try {
        Files.writeString(next, text)
        try {
            Files.move(next, file, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(next, file, REPLACE_EXISTING)
        }
    } finally {
        Files.deleteIfExists(next)
    }
}

internal class LocalTrack internal constructor(val file: Path, val song: MediaData.Song) {
    fun playbackUri() = file.also {
        if (!Files.isRegularFile(it, NOFOLLOW_LINKS) || it.toRealPath() != it) {
            throw IOException("Local track is unavailable")
        }
    }.toUri()
}

internal data class LocalScan(val tracks: List<LocalTrack> = emptyList(), val skipped: Int = 0)

private val localFormats = setOf("mp3", "wav", "aif", "aiff", "m4a") + offlineFormats

internal suspend fun scanLocalFolders(folders: List<Path>): LocalScan = withContext(Dispatchers.IO) {
    val context = coroutineContext
    val visited = mutableSetOf<Path>()
    val tracks = mutableListOf<LocalTrack>()
    var skipped = 0
    for (folder in folders.distinct()) {
        context.ensureActive()
        if (!folder.isAbsolute || !Files.isDirectory(folder, NOFOLLOW_LINKS)) {
            skipped++
            continue
        }
        try {
            val root = folder.normalize()
            if (root != root.toRealPath()) {
                skipped++
                continue
            }
            Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    context.ensureActive()
                    // Junctions on Windows need not be reported as symbolic links.
                    if (attrs.isOther || Files.isHidden(dir) || dir != dir.toRealPath() || !visited.add(dir)) {
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    context.ensureActive()
                    val name = file.fileName.toString()
                    val format = name.substringAfterLast('.', "").lowercase()
                    if (attrs.isRegularFile && !attrs.isOther && format in localFormats &&
                        !Files.isHidden(file) && Files.isReadable(file)) {
                        val stem = name.substringBeforeLast('.')
                        val artist = stem.substringBefore(" - ", "").trim()
                        val title = stem.substringAfter(" - ").trim().ifEmpty { stem }
                        val song = MediaData.Song(
                            navidromeID = file.toUri().toASCIIString(), title = title, artist = artist,
                            album = file.parent.fileName?.toString().orEmpty(), format = format,
                            path = file.toString(), contentType = null,
                        )
                        tracks += LocalTrack(file, song)
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                    context.ensureActive()
                    skipped++
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    if (exc != null) skipped++
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (_: IOException) {
            skipped++
        } catch (_: SecurityException) {
            skipped++
        }
    }
    LocalScan(tracks.sortedWith(compareBy({ it.song.title.lowercase() }, { it.file })), skipped)
}

internal fun searchLocalTracks(tracks: List<LocalTrack>, query: String): List<LocalTrack> {
    val term = query.trim()
    if (term.isEmpty()) return tracks
    return tracks.filter { track ->
        with(track.song) { title.contains(term, true) || artist.contains(term, true) || album.contains(term, true) }
    }
}

internal suspend fun loadLocalLyrics(track: LocalTrack): List<Lyric> = withContext(Dispatchers.IO) {
    val file = track.file.resolveSibling(track.file.fileName.toString().substringBeforeLast('.') + ".lrc")
    if (!Files.isRegularFile(file, NOFOLLOW_LINKS) || file != file.toRealPath()) return@withContext emptyList()
    val limit = 1024 * 1024
    val bytes = Files.newInputStream(file, NOFOLLOW_LINKS).use { it.readNBytes(limit + 1) }
    if (bytes.size > limit) throw IOException("Local lyrics exceed 1 MiB")
    val utf16 = bytes.size >= 2 && ((bytes[0].toInt() == -1 && bytes[1].toInt() == -2) ||
        (bytes[0].toInt() == -2 && bytes[1].toInt() == -1))
    parseLocalLrc(bytes.toString(if (utf16) Charsets.UTF_16 else Charsets.UTF_8).removePrefix("\uFEFF"))
}
