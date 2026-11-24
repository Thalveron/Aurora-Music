package com.craftworks.music.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.craftworks.music.data.model.Lyric
import com.craftworks.music.data.model.MediaData
import com.sun.jna.Platform
import java.awt.FileDialog
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JColorChooser
import javax.swing.JFileChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun main() = application { DesktopApp(::exitApplication) }

@Composable
private fun DesktopApp(exit: () -> Unit) {
    val settings = remember { DesktopSettings() }
    var chinese by remember { mutableStateOf(settings.language != "en") }
    fun label(zh: String, en: String) = if (chinese) zh else en
    var address by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var allowHttp by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var appliedQuery by remember { mutableStateOf("") }
    var hasMore by remember { mutableStateOf(false) }
    var client by remember { mutableStateOf<SubsonicClient?>(null) }
    var songs by remember { mutableStateOf<List<MediaData.Song>>(emptyList()) }
    var albums by remember { mutableStateOf<List<MediaData.Album>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<MediaData.Playlist>>(emptyList()) }
    var libraryTab by remember { mutableStateOf("songs") }
    var current by remember { mutableStateOf<MediaData.Song?>(null) }
    var lyrics by remember { mutableStateOf<List<Lyric>>(emptyList()) }
    var lyricError by remember { mutableStateOf("") }
    var playback by remember { mutableStateOf(PlaybackState()) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf(settings.overlay) }
    var lyricColor by remember { mutableStateOf(Color(settings.lyricColor)) }
    var background by remember { mutableStateOf<ImageBitmap?>(null) }
    var volume by remember { mutableStateOf(1f) }
    var update by remember { mutableStateOf<Update?>(null) }
    var accelerated by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val player = remember { AudioPlayer { playback = it } }
    DisposableEffect(player) { onDispose { player.close() } }

    fun saveAppearance() {
        try {
            settings.language = if (chinese) "zh" else "en"
            settings.lyricColor = lyricColor.toArgb()
            settings.overlay = overlay
            settings.flush()
        } catch (_: IOException) {
            message = label("无法保存外观设置", "Could not save appearance settings")
        }
    }

    LaunchedEffect(Unit) {
        try {
            val saved = withContext(Dispatchers.IO) {
                settings.server()?.let { it to settings.password(it) }
            }
            if (saved != null) {
                address = saved.first.address
                username = saved.first.username
                allowHttp = saved.first.allowHttp
                password = saved.second
            }
        } catch (e: IOException) {
            message = e.message.orEmpty()
        }
        if (settings.background.isNotBlank()) {
            try {
                background = withContext(Dispatchers.IO) { loadBackground(Path.of(settings.background)) }
            } catch (_: IOException) {
                message = label("无法加载已保存的背景图片", "Could not load the saved background image")
            }
        }
    }

    LaunchedEffect(current, client) {
        lyrics = emptyList()
        lyricError = ""
        val selected = current
        val active = client
        if (selected != null && active != null) {
            try {
                lyrics = withContext(Dispatchers.IO) { loadLyrics(active, selected) }
            } catch (e: IOException) {
                lyricError = label("歌词加载失败", "Could not load lyrics") + ": " + e.message.orEmpty()
            }
        }
    }

    LaunchedEffect(playback.ended) {
        if (playback.ended) {
            val index = songs.indexOfFirst { it.navidromeID == current?.navidromeID }
            val next = songs.getOrNull(index + 1).takeIf { index >= 0 }
            val active = client
            if (next != null && active != null) {
                current = next
                player.play(active, next)
            }
        }
    }

    val lyricText = currentLyric(lyrics, playback.positionMs)
    Window(
        onCloseRequest = exit,
        title = "Aurora Music 1.33.0 - development",
        state = WindowState(width = 1120.dp, height = 760.dp),
    ) {
        MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xffaeb9ff), surface = Color(0xff202432))) {
            Box(Modifier.fillMaxSize().background(Color(0xff10131d))) {
                background?.let {
                    Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.66f)))
                }
                Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Surface(shape = RoundedCornerShape(20.dp), color = Color(0xff202534).copy(alpha = 0.93f), modifier = Modifier.width(190.dp)) {
                        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("AURORA", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                            Text(label("你的音乐空间", "Your music space"), style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(20.dp))
                            TextButton(enabled = !busy, onClick = { showSettings = false }) { Text(label("音乐库", "Library")) }
                            TextButton(enabled = !busy, onClick = { showSettings = true }) { Text(label("设置", "Settings")) }
                            Spacer(Modifier.weight(1f))
                            Text("1.33.0 • dev", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (showSettings) label("设置", "Settings") else label("发现音乐", "Discover music"), style = MaterialTheme.typography.headlineMedium)
                            TextButton(onClick = { chinese = !chinese; saveAppearance() }) { Text(if (chinese) "English" else "中文") }
                        }
                        if (showSettings) {
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(label("外观与桌面歌词", "Appearance and desktop lyrics"), style = MaterialTheme.typography.titleLarge)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(overlay, onCheckedChange = { overlay = it; saveAppearance() })
                                Text(label("显示桌面歌词（置顶、鼠标穿透）", "Show desktop lyrics (always on top, click-through)"))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    val picked = JColorChooser.showDialog(window, label("选择歌词颜色", "Choose lyric color"), java.awt.Color(lyricColor.toArgb(), true))
                                    if (picked != null) { lyricColor = Color(picked.rgb); saveAppearance() }
                                }) { Text(label("选择歌词颜色", "Choose lyric color")) }
                                Button(onClick = {
                                    val dialog = FileDialog(window, label("选择背景图片", "Choose background image"), FileDialog.LOAD)
                                    dialog.isVisible = true
                                    if (dialog.file != null && dialog.directory != null) {
                                        val path = Path.of(dialog.directory, dialog.file)
                                        scope.launch {
                                            try {
                                                background = withContext(Dispatchers.IO) { loadBackground(path) }
                                                settings.background = path.toString()
                                                settings.flush()
                                            } catch (_: IOException) {
                                                message = label("背景图片无效或无法保存设置", "Invalid background image or unable to save settings")
                                            }
                                        }
                                    }
                                }) { Text(label("选择背景", "Choose background")) }
                                TextButton(enabled = !busy, onClick = {
                                    background = null
                                    try { settings.background = ""; settings.flush() }
                                    catch (_: IOException) { message = label("无法保存背景设置", "Unable to save background settings") }
                                }) { Text(label("清除背景", "Clear background")) }
                            }
                            Text(label("背景文件保留在本机；服务器密码仅在 Windows 上使用系统加密保存。", "Backgrounds stay on this device. Passwords are saved using Windows encryption."), style = MaterialTheme.typography.bodyMedium)
                            Text(label("FLAC、OGG、Opus 等格式将请求服务器转码为 MP3。", "FLAC, OGG, Opus and similar formats request MP3 transcoding from the server."), style = MaterialTheme.typography.bodySmall)
                            TextButton(enabled = !busy, onClick = {
                                try {
                                    settings.removeServer()
                                    player.stop()
                                    client = null
                                    current = null
                                    songs = emptyList()
                                    address = ""
                                    username = ""
                                    password = ""
                                    message = label("已删除保存的服务器", "Saved server removed")
                                } catch (e: IOException) {
                                    message = e.message.orEmpty()
                                }
                            }) { Text(label("删除保存的服务器", "Remove saved server")) }
                            Spacer(Modifier.height(8.dp))
                            Text(label("Windows 更新", "Windows updates"), style = MaterialTheme.typography.titleLarge)
                            Text(label("更新信息来自 GitHub；加速线路会向第三方发送公开下载地址。下载后需自行安装。", "Metadata comes from GitHub. The acceleration provider receives the public download URL. Install downloads manually."), style = MaterialTheme.typography.bodyMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(accelerated, onCheckedChange = { accelerated = it })
                                Text(label("使用加速线路", "Use accelerated route"))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(enabled = !busy, onClick = {
                                    busy = true
                                    message = ""
                                    scope.launch {
                                        try {
                                            update = withContext(Dispatchers.IO) { Updater().check() }
                                            message = update?.let { label("发现新版本 ", "Update available: ") + it.version }
                                                ?: label("没有找到可验证的新版本", "No verifiable update found")
                                        } catch (e: IOException) { message = e.message ?: label("更新检查失败", "Update check failed") }
                                        finally { busy = false }
                                    }
                                }) { Text(label("检查更新", "Check for updates")) }
                                val available = update
                                if (available != null) {
                                    Button(enabled = !busy, onClick = {
                                        val chooser = JFileChooser()
                                        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                                        if (chooser.showSaveDialog(window) == JFileChooser.APPROVE_OPTION) {
                                            val directory = chooser.selectedFile.toPath()
                                            val useProxy = accelerated
                                            busy = true
                                            message = ""
                                            scope.launch {
                                                try {
                                                    val saved = withContext(Dispatchers.IO) { Updater().download(available, useProxy, directory) }
                                                    message = label("更新已验证并保存：", "Verified update saved: ") + saved
                                                } catch (e: IOException) { message = e.message ?: label("更新下载失败", "Update download failed") }
                                                finally { busy = false }
                                            }
                                        }
                                    }) { Text(label("下载 ", "Download ") + available.version) }
                                }
                            }
                            }
                        } else if (client == null) {
                            Text(label("连接 Navidrome 或 Subsonic 服务器", "Connect to Navidrome or a Subsonic server"), style = MaterialTheme.typography.titleLarge)
                            OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text(label("服务器地址", "Server address")) }, singleLine = true)
                            OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text(label("用户名", "Username")) }, singleLine = true)
                            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text(label("密码", "Password")) }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(allowHttp, onCheckedChange = { allowHttp = it }, enabled = !busy)
                                Text(label("允许不加密的 HTTP（凭据可能被截获）", "Allow unencrypted HTTP (credentials may be intercepted)"))
                            }
                            Button(enabled = !busy, onClick = {
                                val serverAddress = address
                                val serverUsername = username
                                val serverPassword = password
                                val httpAllowed = allowHttp
                                busy = true
                                message = ""
                                scope.launch {
                                    try {
                                        val connected = withContext(Dispatchers.IO) {
                                            SubsonicClient(serverAddress, serverUsername, serverPassword, httpAllowed).also { it.ping() }
                                        }
                                        client = connected
                                        password = ""
                                        if (Platform.isWindows()) {
                                            withContext(Dispatchers.IO) { settings.saveServer(serverAddress, serverUsername, serverPassword, httpAllowed) }
                                        } else {
                                            message = label("已连接；非 Windows 系统不保存密码", "Connected; passwords are not saved on this platform")
                                        }
                                        val initial = withContext(Dispatchers.IO) { connected.search("").song.orEmpty() }
                                        songs = initial
                                        appliedQuery = ""
                                        hasMore = initial.size == 100
                                    } catch (e: IllegalArgumentException) {
                                        message = e.message ?: label("服务器设置无效", "Invalid server settings")
                                    } catch (e: IOException) {
                                        message = e.message ?: label("连接或保存失败", "Connection or save failed")
                                    } finally { busy = false }
                                }
                            }) { Text(label("连接并保存", "Connect and save")) }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(enabled = !busy, onClick = { libraryTab = "songs" }) { Text(label("歌曲", "Songs")) }
                                TextButton(enabled = !busy, onClick = {
                                    val active = client ?: return@TextButton
                                    busy = true
                                    scope.launch {
                                        try {
                                            albums = withContext(Dispatchers.IO) { active.albums() }
                                            libraryTab = "albums"
                                            message = if (albums.size == 100) label("仅显示前 100 张专辑", "Showing the first 100 albums") else ""
                                        } catch (e: IOException) { message = e.message ?: label("加载专辑失败", "Could not load albums") }
                                        finally { busy = false }
                                    }
                                }) { Text(label("专辑", "Albums")) }
                                TextButton(enabled = !busy, onClick = {
                                    val active = client ?: return@TextButton
                                    busy = true
                                    scope.launch {
                                        try {
                                            playlists = withContext(Dispatchers.IO) { active.playlists() }
                                            libraryTab = "playlists"
                                            message = ""
                                        } catch (e: IOException) { message = e.message ?: label("加载播放列表失败", "Could not load playlists") }
                                        finally { busy = false }
                                    }
                                }) { Text(label("播放列表", "Playlists")) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(query, { query = it }, Modifier.weight(1f), enabled = !busy, label = { Text(label("搜索歌曲", "Search songs")) }, singleLine = true)
                                Button(enabled = !busy, onClick = {
                                    val active = client ?: return@Button
                                    val searchQuery = query
                                    busy = true
                                    message = ""
                                    scope.launch {
                                        try {
                                            val found = withContext(Dispatchers.IO) { active.search(searchQuery).song.orEmpty() }
                                            songs = found
                                            appliedQuery = searchQuery
                                            hasMore = found.size == 100
                                            libraryTab = "songs"
                                        }
                                        catch (e: IOException) { message = e.message ?: label("搜索失败", "Search failed") }
                                        finally { busy = false }
                                    }
                                }) { Text(label("搜索", "Search")) }
                                TextButton(enabled = !busy, onClick = {
                                    player.stop()
                                    current = null
                                    client = null
                                    songs = emptyList()
                                    libraryTab = "songs"
                                    message = ""
                                }) { Text(label("断开", "Disconnect")) }
                            }
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (libraryTab == "albums") itemsIndexed(albums) { _, album ->
                                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xff292f40).copy(alpha = 0.85f),
                                        modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) {
                                            val active = client ?: return@clickable
                                            busy = true
                                            scope.launch {
                                                try {
                                                    songs = withContext(Dispatchers.IO) { active.album(album.navidromeID).songs.orEmpty() }
                                                    hasMore = false
                                                    libraryTab = "songs"
                                                    message = ""
                                                } catch (e: IOException) { message = e.message ?: label("加载专辑失败", "Could not load album") }
                                                finally { busy = false }
                                            }
                                        }) {
                                        Column(Modifier.padding(14.dp)) {
                                            Text(album.name?.takeIf { it.isNotBlank() } ?: album.title.orEmpty())
                                            Text(album.artist, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                                if (libraryTab == "playlists") itemsIndexed(playlists) { _, playlist ->
                                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xff292f40).copy(alpha = 0.85f),
                                        modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) {
                                            val active = client ?: return@clickable
                                            busy = true
                                            scope.launch {
                                                try {
                                                    songs = withContext(Dispatchers.IO) { active.playlist(playlist.navidromeID).songs.orEmpty().toList() }
                                                    hasMore = false
                                                    libraryTab = "songs"
                                                    message = ""
                                                } catch (e: IOException) { message = e.message ?: label("加载播放列表失败", "Could not load playlist") }
                                                finally { busy = false }
                                            }
                                        }) { Text(playlist.name, Modifier.padding(14.dp)) }
                                }
                                if (libraryTab == "songs") itemsIndexed(songs) { index, song ->
                                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xff292f40).copy(alpha = 0.85f),
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            current = song
                                            player.play(client!!, song)
                                        }) {
                                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("${index + 1}", Modifier.width(40.dp), color = MaterialTheme.colorScheme.primary)
                                            Column(Modifier.weight(1f)) {
                                                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(song.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                            }
                                            Text(song.album, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                                if (libraryTab == "songs" && hasMore) item {
                                    TextButton(enabled = !busy, onClick = {
                                        val active = client ?: return@TextButton
                                        val offset = songs.size
                                        val requestedQuery = appliedQuery
                                        busy = true
                                        scope.launch {
                                            try {
                                                val found = withContext(Dispatchers.IO) { active.search(requestedQuery, offset).song.orEmpty() }
                                                songs = songs + found
                                                hasMore = found.size == 100
                                            } catch (e: IOException) {
                                                message = e.message ?: label("加载失败", "Could not load more songs")
                                            } finally { busy = false }
                                        }
                                    }) { Text(label("加载更多", "Load more")) }
                                }
                            }
                            if (current != null) {
                                Surface(shape = RoundedCornerShape(16.dp), color = Color(0xff292f40).copy(alpha = 0.95f)) {
                                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                        Text(current!!.title, style = MaterialTheme.typography.titleMedium)
                                        Text(if (lyricText.isNotBlank()) lyricText else label("暂无歌词", "No lyrics"), color = lyricColor, maxLines = 3)
                                        if (lyricError.isNotBlank()) Text(lyricError, color = MaterialTheme.colorScheme.error)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            TextButton(onClick = {
                                                val index = songs.indexOfFirst { it.navidromeID == current?.navidromeID }
                                                val previous = songs.getOrNull(index - 1)
                                                val active = client
                                                if (previous != null && active != null) { current = previous; player.play(active, previous) }
                                            }) { Text(label("上一首", "Previous")) }
                                            TextButton(onClick = { player.toggle() }) { Text(if (playback.playing) label("暂停", "Pause") else label("播放", "Play")) }
                                            TextButton(onClick = {
                                                val index = songs.indexOfFirst { it.navidromeID == current?.navidromeID }
                                                val next = songs.getOrNull(index + 1).takeIf { index >= 0 }
                                                val active = client
                                                if (next != null && active != null) { current = next; player.play(active, next) }
                                            }) { Text(label("下一首", "Next")) }
                                            Slider(
                                                value = if (playback.durationMs > 0) (playback.positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f) else 0f,
                                                onValueChange = { player.seek((it * playback.durationMs).toLong()) },
                                                enabled = playback.durationMs > 0,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text("${formatTime(playback.positionMs)} / ${formatTime(playback.durationMs)}")
                                            Text(label("音量", "Volume"), Modifier.padding(start = 12.dp))
                                            Slider(volume, onValueChange = { volume = it; player.volume(it) }, modifier = Modifier.width(90.dp))
                                        }
                                        if (playback.error) Text(label("播放失败：请检查服务器转码和音频格式。", "Playback failed: check server transcoding and audio format."), color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                        if (busy) Text(label("处理中…", "Working…"))
                        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
    if (overlay && current != null) {
        LyricOverlay(lyricText, lyricColor, onError = { message = it }, onClose = { overlay = false; saveAppearance() })
    }
}

private fun formatTime(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

private fun loadBackground(path: Path): ImageBitmap {
    val bytes = try {
        Files.newInputStream(path).use { it.readNBytes(20 * 1024 * 1024 + 1) }
    } catch (_: IOException) {
        throw IOException("Could not read background image")
    }
    if (bytes.size > 20 * 1024 * 1024) throw IOException("Background image is too large")
    return try {
        org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (_: RuntimeException) {
        throw IOException("Invalid background image")
    }
}
