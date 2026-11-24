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
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
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
    var profiles by remember { mutableStateOf(emptyList<SavedServer>()) }
    var selectedProfile by remember { mutableStateOf<SavedServer?>(null) }
    var profileMenu by remember { mutableStateOf(false) }
    var allowHttp by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var appliedQuery by remember { mutableStateOf("") }
    var hasMore by remember { mutableStateOf(false) }
    var client by remember { mutableStateOf<SubsonicClient?>(null) }
    var songs by remember { mutableStateOf<List<MediaData.Song>>(emptyList()) }
    var albums by remember { mutableStateOf<List<MediaData.Album>>(emptyList()) }
    var hasMoreAlbums by remember { mutableStateOf(false) }
    var playlists by remember { mutableStateOf<List<MediaData.Playlist>>(emptyList()) }
    var libraryTab by remember { mutableStateOf("songs") }
    var queue by remember { mutableStateOf(PlayQueue<QueueTrack>()) }
    val current = queue.current
    var repeat by remember { mutableStateOf(RepeatMode.OFF) }
    var timerDeadline by remember { mutableStateOf<Long?>(null) }
    var timerMinutes by remember { mutableStateOf<Int?>(null) }
    var lyrics by remember { mutableStateOf<List<Lyric>>(emptyList()) }
    var lyricError by remember { mutableStateOf("") }
    var playback by remember { mutableStateOf(PlaybackState()) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var localLibrary by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf(settings.overlay) }
    var lyricColor by remember { mutableStateOf(Color(settings.lyricColor)) }
    var background by remember { mutableStateOf<ImageBitmap?>(null) }
    var update by remember { mutableStateOf<Update?>(null) }
    var accelerated by remember { mutableStateOf(settings.accelerated) }
    var scrobblePercent by remember { mutableStateOf(settings.scrobblePercent) }
    var songForPlaylist by remember { mutableStateOf<MediaData.Song?>(null) }
    var pendingDelete by remember { mutableStateOf<MediaData.Playlist?>(null) }
    var pendingRename by remember { mutableStateOf<MediaData.Playlist?>(null) }
    var songListOwner by remember { mutableStateOf<String?>(null) }
    var albumSort by remember { mutableStateOf("alphabeticalByName") }
    var albumSortMenu by remember { mutableStateOf(false) }
    var musicFolder by remember { mutableStateOf<com.craftworks.music.data.NavidromeLibrary?>(null) }
    var folderOptions by remember { mutableStateOf<List<com.craftworks.music.data.NavidromeLibrary>?>(null) }
    var playSession by remember { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()
    val appIcon = remember {
        val bytes = checkNotNull(DesktopSettings::class.java.getResourceAsStream("/branding/aurora-music.png")).use { it.readBytes() }
        BitmapPainter(org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap())
    }
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

    fun playIndex(index: Int) {
        val track = queue.entries.getOrNull(index) ?: return
        queue = queue.copy(index = index)
        playSession++
        track.play()
    }

    fun serverTrack(active: SubsonicClient, song: MediaData.Song) =
        QueueTrack(song, { player.play(active, song) }, { loadLyrics(active, song) }, { active.scrobble(song.navidromeID, it) })

    fun disconnect() {
        player.stop()
        queue = PlayQueue()
        client = null
        songs = emptyList()
        albums = emptyList()
        playlists = emptyList()
        songListOwner = null
        musicFolder = null
        folderOptions = null
        query = ""
        appliedQuery = ""
        hasMore = false
        hasMoreAlbums = false
        libraryTab = "songs"
        timerDeadline = null
        message = ""
    }

    fun playSongs(tracks: List<MediaData.Song>, index: Int) {
        val active = client ?: return
        if (index !in tracks.indices || tracks.any { it.navidromeID.isBlank() }) {
            message = label("服务器返回的歌曲 ID 无效", "The server returned an invalid song ID")
            return
        }
        queue = PlayQueue(tracks.map { serverTrack(active, it) }, index)
        playIndex(index)
    }

    LaunchedEffect(Unit) {
        try {
            profiles = withContext(Dispatchers.IO) { settings.servers() }
            val saved = withContext(Dispatchers.IO) {
                settings.server()?.let { it to settings.password(it) }
            }
            if (saved != null) {
                selectedProfile = saved.first
                address = saved.first.address
                username = saved.first.username
                allowHttp = saved.first.allowHttp
                password = saved.second
            }
        } catch (e: IOException) {
            message = describeError(e, chinese)
        }
        if (settings.background.isNotBlank()) {
            try {
                background = withContext(Dispatchers.IO) { loadBackground(Path.of(settings.background)) }
            } catch (_: IOException) {
                message = label("无法加载已保存的背景图片", "Could not load the saved background image")
            }
        }
    }

    LaunchedEffect(current) {
        lyrics = emptyList()
        lyricError = ""
        val selected = current
        if (selected != null) {
            try {
                lyrics = withContext(Dispatchers.IO) { selected.readLyrics() }
            } catch (e: IOException) {
                lyricError = label("歌词加载失败", "Could not load lyrics") + ": " + describeError(e, chinese)
            }
        }
    }

    LaunchedEffect(playback.ended) {
        if (playback.ended) queue.next(repeat, automatic = true)?.let(::playIndex)
    }
    LaunchedEffect(timerDeadline) {
        val deadline = timerDeadline
        if (deadline == null) timerMinutes = null else {
            while (true) {
                val remaining = (deadline - System.nanoTime()) / 1_000_000L
                if (remaining <= 0) break
                timerMinutes = ((remaining + 59_999) / 60_000).toInt()
                kotlinx.coroutines.delay(minOf(remaining, 1000))
            }
            player.pause()
            timerDeadline = null
        }
    }

    val lyricText = currentLyric(lyrics, playback.positionMs)
    LaunchedEffect(current, playSession, scrobblePercent) {
        val report = queue.current?.reportPlay
        if (report != null && scrobblePercent > 0) {
            var announced = false
            var listened = 0L
            var previous = System.nanoTime()
            while (true) {
                kotlinx.coroutines.delay(500)
                val now = System.nanoTime()
                if (playback.error || playback.ended) break
                if (playback.playing) {
                    listened += (now - previous) / 1_000_000
                    val submit = playback.durationMs > 0 && listened >= playback.durationMs * scrobblePercent / 100
                    if (!announced || submit) {
                        try { withContext(Dispatchers.IO) { report(submit) } }
                        catch (e: IOException) { message = describeError(e, chinese); break }
                        if (submit) break
                        announced = true
                    }
                }
                previous = System.nanoTime()
            }
        }
    }
    Window(
        onCloseRequest = exit,
        title = "Aurora Music",
        icon = appIcon,
        state = rememberWindowState(width = 1120.dp, height = 760.dp),
    ) {
        MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xffaeb9ff), surface = Color(0xff202432))) {
            val activeServer = client
            if (activeServer != null) {
                songForPlaylist?.let { AddSongDialog(activeServer, it, chinese) { songForPlaylist = null } }
                pendingRename?.let { playlist ->
                    var title by remember(playlist) { mutableStateOf(playlist.name) }
                    androidx.compose.material3.AlertDialog(onDismissRequest = { pendingRename = null },
                        title = { Text(label("重命名歌单", "Rename playlist")) },
                        text = { OutlinedTextField(title, { title = it }, singleLine = true) },
                        dismissButton = { TextButton({ pendingRename = null }) { Text(label("取消", "Cancel")) } },
                        confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = {
                            pendingRename = null
                            busy = true
                            scope.launch {
                                try {
                                    playlists = withContext(Dispatchers.IO) {
                                        activeServer.renamePlaylist(playlist.navidromeID, title)
                                        activeServer.playlists()
                                    }
                                } catch (e: IOException) { message = describeError(e, chinese) }
                                finally { busy = false }
                            }
                        }) { Text(label("保存", "Save")) } })
                }
                pendingDelete?.let { playlist ->
                    androidx.compose.material3.AlertDialog(onDismissRequest = { pendingDelete = null },
                        title = { Text(label("从服务器删除歌单？", "Delete playlist from server?")) }, text = { Text(playlist.name) },
                        dismissButton = { TextButton({ pendingDelete = null }) { Text(label("取消", "Cancel")) } },
                        confirmButton = { TextButton({
                            pendingDelete = null
                            busy = true
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { activeServer.deletePlaylist(playlist.navidromeID) }
                                    playlists = playlists.filterNot { it.navidromeID == playlist.navidromeID }
                                } catch (e: IOException) { message = describeError(e, chinese) }
                                finally { busy = false }
                            }
                        }) { Text(label("删除", "Delete")) } })
                }
            }
            Surface(Modifier.fillMaxSize(), color = Color(0xff10131d), contentColor = Color(0xffeef0fa)) {
              Box(Modifier.fillMaxSize()) {
                background?.let {
                    Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.66f)))
                }
                Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Surface(shape = RoundedCornerShape(20.dp), color = Color(0xff202534).copy(alpha = 0.93f), modifier = Modifier.width(190.dp)) {
                        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Image(appIcon, null, Modifier.height(52.dp))
                            Text("Aurora Music", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                            Text(label("你的音乐空间", "Your music space"), style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(20.dp))
                            TextButton(enabled = !busy, onClick = { showSettings = false; localLibrary = false }) { Text(label("服务器音乐库", "Server library")) }
                            TextButton(enabled = !busy, onClick = { showSettings = false; localLibrary = true }) { Text(label("本地音乐", "Local music")) }
                            TextButton(enabled = !busy, onClick = { showSettings = true }) { Text(label("设置", "Settings")) }
                            Spacer(Modifier.weight(1f))
                            Text(DESKTOP_VERSION, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (showSettings) label("设置", "Settings") else if (localLibrary) label("本地音乐", "Local music") else label("服务器音乐库", "Server library"), style = MaterialTheme.typography.headlineMedium)
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
                            Text(label("播放原始音频，不请求服务器转码或降低码率。", "Play original audio without server transcoding or bitrate limits."))
                            Text(label("向音乐服务器记录播放", "Report listening history to your music server"))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (threshold in listOf(0, 50, 70, 90)) androidx.compose.material3.FilterChip(
                                    selected = scrobblePercent == threshold, onClick = {
                                        scrobblePercent = threshold
                                        try { settings.scrobblePercent = threshold; settings.flush() }
                                        catch (e: IOException) { message = describeError(e, chinese) }
                                    }, label = { Text(if (threshold == 0) label("关闭", "Off") else "$threshold%") })
                            }
                            TextButton(enabled = !busy && selectedProfile != null, onClick = {
                                try {
                                    settings.removeServer(selectedProfile)
                                    profiles = settings.servers()
                                    selectedProfile = null
                                    disconnect()
                                    address = ""
                                    username = ""
                                    password = ""
                                    message = label("已删除保存的服务器", "Saved server removed")
                                } catch (e: IOException) {
                                    message = describeError(e, chinese)
                                }
                            }) { Text(label("删除保存的服务器", "Remove saved server")) }
                            Spacer(Modifier.height(8.dp))
                            Text(label("Windows 更新", "Windows updates"), style = MaterialTheme.typography.titleLarge)
                            Text(label("更新信息来自 GitHub；加速线路会向第三方发送公开下载地址。下载后需自行安装。", "Metadata comes from GitHub. The acceleration provider receives the public download URL. Install downloads manually."), style = MaterialTheme.typography.bodyMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                listOf(false to label("GitHub 原始线路", "GitHub direct"), true to label("加速线路", "Accelerated")).forEach { (route, text) ->
                                    androidx.compose.material3.FilterChip(selected = accelerated == route, enabled = !busy, onClick = {
                                        accelerated = route
                                        try { settings.accelerated = route; settings.flush() }
                                        catch (e: IOException) { message = describeError(e, chinese) }
                                    }, label = { Text(text) }, modifier = Modifier.padding(end = 8.dp))
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(enabled = !busy, onClick = {
                                    busy = true
                                    message = ""
                                    scope.launch {
                                        try {
                                            update = null
                                            update = withContext(Dispatchers.IO) { Updater().check() }
                                            message = update?.let { label("发现新版本 ", "Update available: ") + it.version }
                                                ?: label("没有找到可验证的新版本", "No verifiable update found")
                                        } catch (e: IOException) { message = describeError(e, chinese) }
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
                                                } catch (e: IOException) { message = describeError(e, chinese) }
                                                finally { busy = false }
                                            }
                                        }
                                    }) { Text(label("下载 ", "Download ") + available.version) }
                                }
                            }
                            }
                        } else if (localLibrary) {
                            LocalLibraryPanel(chinese, Modifier.weight(1f)) { selected, tracks ->
                                val index = tracks.indexOf(selected)
                                if (index >= 0) {
                                    queue = PlayQueue(tracks.map { QueueTrack(it.song, { player.playLocal(it) }, { loadLocalLyrics(it) }) }, index)
                                    playIndex(index)
                                }
                            }
                        } else if (client == null) {
                            Text(label("连接 Navidrome 或 Subsonic 服务器", "Connect to Navidrome or a Subsonic server"), style = MaterialTheme.typography.titleLarge)
                            if (profiles.isNotEmpty()) Box {
                                TextButton({ profileMenu = true }, enabled = !busy) { Text(label("选择服务器或添加账号", "Choose server or add account")) }
                                androidx.compose.material3.DropdownMenu(profileMenu, { profileMenu = false }) {
                                    (profiles + listOf(null)).forEach { profile ->
                                        androidx.compose.material3.DropdownMenuItem(text = {
                                            Text(profile?.let { "${it.username} · ${it.address}" } ?: label("添加服务器", "Add server"))
                                        }, onClick = {
                                            profileMenu = false
                                            try {
                                                val secret = profile?.let(settings::password).orEmpty()
                                                selectedProfile = profile
                                                address = profile?.address.orEmpty()
                                                username = profile?.username.orEmpty()
                                                allowHttp = profile?.allowHttp ?: false
                                                password = secret
                                            } catch (e: IOException) { message = describeError(e, chinese) }
                                        })
                                    }
                                }
                            }
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
                                            selectedProfile = settings.server()
                                            profiles = settings.servers()
                                        } else {
                                            message = label("已连接；非 Windows 系统不保存密码", "Connected; passwords are not saved on this platform")
                                        }
                                        val initial = withContext(Dispatchers.IO) { connected.search("").song.orEmpty() }
                                        songs = initial
                                        appliedQuery = ""
                                        hasMore = initial.size == 100
                                    } catch (e: IllegalArgumentException) {
                                        message = describeError(e, chinese)
                                    } catch (e: IOException) {
                                        message = describeError(e, chinese)
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
                                            albums = withContext(Dispatchers.IO) { active.albums(albumSort, folder = musicFolder?.id) }
                                            hasMoreAlbums = albumSort != "random" && albums.size == 100
                                            libraryTab = "albums"
                                            message = ""
                                        } catch (e: IOException) { message = describeError(e, chinese) }
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
                                        } catch (e: IOException) { message = describeError(e, chinese) }
                                        finally { busy = false }
                                    }
                                }) { Text(label("播放列表", "Playlists")) }
                                TextButton(enabled = !busy, onClick = { libraryTab = "explore" }) { Text(label("歌手 / 收藏 / 电台", "Artists / Favorites / Radio")) }
                                Box {
                                    TextButton(enabled = !busy, onClick = {
                                        val active = client ?: return@TextButton
                                        busy = true
                                        scope.launch {
                                            try { folderOptions = withContext(Dispatchers.IO) { active.musicFolders() } }
                                            catch (e: IOException) { message = describeError(e, chinese) }
                                            finally { busy = false }
                                        }
                                    }) { Text(musicFolder?.name ?: label("歌曲/专辑筛选", "Song/album filter")) }
                                    androidx.compose.material3.DropdownMenu(folderOptions != null, { folderOptions = null }) {
                                        (listOf(null) + folderOptions.orEmpty()).forEach { folder ->
                                            androidx.compose.material3.DropdownMenuItem(text = { Text(folder?.name ?: label("全部文件夹", "All folders")) }, onClick = {
                                                folderOptions = null
                                                val active = client ?: return@DropdownMenuItem
                                                busy = true
                                                scope.launch {
                                                    try {
                                                        songs = withContext(Dispatchers.IO) { active.search(query, folder = folder?.id).song.orEmpty() }
                                                        appliedQuery = query
                                                        musicFolder = folder
                                                        songListOwner = null
                                                        hasMore = songs.size == 100
                                                        libraryTab = "songs"
                                                    } catch (e: IOException) { message = describeError(e, chinese) }
                                                    finally { busy = false }
                                                }
                                            })
                                        }
                                    }
                                }
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
                                            val found = withContext(Dispatchers.IO) { active.search(searchQuery, folder = musicFolder?.id).song.orEmpty() }
                                            songs = found
                                            songListOwner = null
                                            appliedQuery = searchQuery
                                            hasMore = found.size == 100
                                            libraryTab = "songs"
                                        }
                                        catch (e: IOException) { message = describeError(e, chinese) }
                                        finally { busy = false }
                                    }
                                }) { Text(label("搜索", "Search")) }
                                TextButton(enabled = !busy, onClick = {
                                    disconnect()
                                }) { Text(label("断开", "Disconnect")) }
                            }
                            if (libraryTab == "explore" && activeServer != null) {
                                ServerLibraryPanel(activeServer, chinese, ::playSongs, { name, uri ->
                                    val station = MediaData.Song(navidromeID = "", title = name)
                                    queue = PlayQueue(listOf(QueueTrack(station, { player.playRadio(uri) }, { emptyList() })), 0)
                                    playIndex(0)
                                }, Modifier.weight(1f))
                            } else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (libraryTab == "albums") item {
                                    Box {
                                        val sorts = mapOf("alphabeticalByName" to label("按名称", "By name"), "recent" to label("最近播放", "Recently played"),
                                            "newest" to label("最近添加", "Recently added"), "frequent" to label("最多播放", "Most played"), "random" to label("随机专辑", "Random albums"))
                                        TextButton({ albumSortMenu = true }, enabled = !busy) { Text(sorts.getValue(albumSort)) }
                                        androidx.compose.material3.DropdownMenu(albumSortMenu, { albumSortMenu = false }) {
                                            sorts.forEach { (sort, title) -> androidx.compose.material3.DropdownMenuItem(text = { Text(title) }, onClick = {
                                                albumSortMenu = false
                                                val active = client ?: return@DropdownMenuItem
                                                busy = true
                                                scope.launch {
                                                    try {
                                                        albums = withContext(Dispatchers.IO) { active.albums(sort, folder = musicFolder?.id) }
                                                        albumSort = sort
                                                        hasMoreAlbums = albums.size == 100 && sort != "random"
                                                    } catch (e: IOException) { message = describeError(e, chinese) }
                                                    finally { busy = false }
                                                }
                                            }) }
                                        }
                                    }
                                }
                                if (libraryTab == "albums") itemsIndexed(albums) { _, album ->
                                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xff292f40).copy(alpha = 0.85f),
                                        modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) {
                                            val active = client ?: return@clickable
                                            if (album.navidromeID.isBlank()) {
                                                message = label("服务器返回的专辑 ID 无效", "The server returned an invalid album ID")
                                                return@clickable
                                            }
                                            busy = true
                                            scope.launch {
                                                try {
                                                    songs = withContext(Dispatchers.IO) { active.album(album.navidromeID).songs.orEmpty() }
                                                    songListOwner = null
                                                    hasMore = false
                                                    libraryTab = "songs"
                                                    message = ""
                                                } catch (e: IOException) { message = describeError(e, chinese) }
                                                finally { busy = false }
                                            }
                                        }) {
                                        Column(Modifier.padding(14.dp)) {
                                            Text(album.name?.takeIf { it.isNotBlank() } ?: album.title.orEmpty())
                                            Text(album.artist, style = MaterialTheme.typography.bodySmall)
                                            TextButton(enabled = !busy && album.navidromeID.isNotBlank(), onClick = {
                                                val active = client ?: return@TextButton
                                                val chooser = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
                                                if (chooser.showSaveDialog(window) != JFileChooser.APPROVE_OPTION) return@TextButton
                                                val directory = chooser.selectedFile.toPath()
                                                busy = true
                                                scope.launch {
                                                    var saved = 0
                                                    try {
                                                        val tracks = withContext(Dispatchers.IO) { active.album(album.navidromeID).songs.orEmpty() }
                                                        for (track in tracks) {
                                                            message = label("正在下载", "Downloading") + " ${saved + 1}/${tracks.size}: ${track.title}"
                                                            withContext(Dispatchers.IO) { active.downloadSong(track, directory) }
                                                            saved++
                                                        }
                                                        message = label("已保存 $saved 首歌曲", "Saved $saved tracks")
                                                    } catch (e: IOException) {
                                                        message = label("已保存 $saved 首，下载停止：", "Saved $saved tracks; download stopped: ") + describeError(e, chinese)
                                                    } finally { busy = false }
                                                }
                                            }) { Text(label("下载整张专辑", "Download album")) }
                                        }
                                    }
                                }
                                if (libraryTab == "albums" && hasMoreAlbums) item {
                                    TextButton(enabled = !busy, onClick = {
                                        val active = client ?: return@TextButton
                                        val offset = albums.size
                                        busy = true
                                        scope.launch {
                                            try {
                                                val found = withContext(Dispatchers.IO) { active.albums(albumSort, offset = offset, folder = musicFolder?.id) }
                                                albums = albums + found
                                                hasMoreAlbums = found.size == 100
                                            } catch (e: IOException) {
                                                message = describeError(e, chinese)
                                            } finally { busy = false }
                                        }
                                    }) { Text(label("加载更多", "Load more")) }
                                }
                                if (libraryTab == "playlists") itemsIndexed(playlists) { _, playlist ->
                                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xff292f40).copy(alpha = 0.85f),
                                        modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) {
                                            val active = client ?: return@clickable
                                            if (playlist.navidromeID.isBlank()) {
                                                message = label("服务器返回的播放列表 ID 无效", "The server returned an invalid playlist ID")
                                                return@clickable
                                            }
                                            busy = true
                                            scope.launch {
                                                try {
                                                    songs = withContext(Dispatchers.IO) { active.playlist(playlist.navidromeID).songs.orEmpty().toList() }
                                                    songListOwner = playlist.navidromeID
                                                    hasMore = false
                                                    libraryTab = "songs"
                                                    message = ""
                                                } catch (e: IOException) { message = describeError(e, chinese) }
                                                finally { busy = false }
                                            }
                                        }) {
                                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(playlist.name, Modifier.weight(1f).padding(6.dp))
                                            TextButton({ pendingRename = playlist }, enabled = !busy) { Text(label("重命名", "Rename")) }
                                            TextButton({ pendingDelete = playlist }, enabled = !busy) { Text(label("删除", "Delete")) }
                                        }
                                    }
                                }
                                if (libraryTab == "songs") itemsIndexed(songs) { index, song ->
                                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xff292f40).copy(alpha = 0.85f),
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            playSongs(songs, index)
                                        }) {
                                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("${index + 1}", Modifier.width(40.dp), color = MaterialTheme.colorScheme.primary)
                                            Column(Modifier.weight(1f)) {
                                                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(song.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                            }
                                            TextButton(onClick = {
                                                val active = client ?: return@TextButton
                                                if (song.navidromeID.isBlank()) {
                                                    message = label("无效的歌曲 ID", "Invalid song ID")
                                                } else if (queue.entries.isEmpty()) playSongs(listOf(song), 0)
                                                else queue = queue.copy(entries = queue.entries + serverTrack(active, song))
                                            }) { Text(label("加入队列", "Queue +")) }
                                            var more by remember(song.navidromeID) { mutableStateOf(false) }
                                            Box {
                                                TextButton({ more = true }, enabled = !busy) { Text(label("更多", "More")) }
                                                androidx.compose.material3.DropdownMenu(more, { more = false }) {
                                                    songListOwner?.let { playlistId ->
                                                        androidx.compose.material3.DropdownMenuItem(text = { Text(label("从此歌单移除", "Remove from this playlist")) }, onClick = {
                                                            more = false
                                                            val active = client ?: return@DropdownMenuItem
                                                            val displayed = songs
                                                            busy = true
                                                            scope.launch {
                                                                try {
                                                                    withContext(Dispatchers.IO) { active.removePlaylistEntry(playlistId, index, displayed) }
                                                                    songs = displayed.filterIndexed { i, _ -> i != index }
                                                                } catch (e: IOException) { message = describeError(e, chinese) }
                                                                finally { busy = false }
                                                            }
                                                        })
                                                    }
                                                    androidx.compose.material3.DropdownMenuItem(text = { Text(label("加入歌单", "Add to playlist")) }, onClick = {
                                                        more = false
                                                        songForPlaylist = song
                                                    })
                                                    androidx.compose.material3.DropdownMenuItem(text = { Text(label("下载原文件", "Download original")) }, onClick = {
                                                        more = false
                                                        val active = client ?: return@DropdownMenuItem
                                                        val chooser = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
                                                        if (chooser.showSaveDialog(window) == JFileChooser.APPROVE_OPTION) {
                                                            val directory = chooser.selectedFile.toPath()
                                                            busy = true
                                                            scope.launch {
                                                                try {
                                                                    val file = withContext(Dispatchers.IO) { active.downloadSong(song, directory) }
                                                                    message = label("已保存：", "Saved: ") + file.fileName
                                                                } catch (e: IOException) { message = describeError(e, chinese) }
                                                                finally { busy = false }
                                                            }
                                                        }
                                                    })
                                                }
                                            }
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
                                                val found = withContext(Dispatchers.IO) { active.search(requestedQuery, offset, folder = musicFolder?.id).song.orEmpty() }
                                                songs = songs + found
                                                hasMore = found.size == 100
                                            } catch (e: IOException) {
                                                message = describeError(e, chinese)
                                            } finally { busy = false }
                                        }
                                    }) { Text(label("加载更多", "Load more")) }
                                }
                            }
                        }
                        PlayerPanel(queue, playback, player, chinese, lyricText, lyricError, lyricColor, repeat, timerMinutes,
                            onRepeat = { repeat = it }, onTimer = { timerDeadline = it?.let { mins -> System.nanoTime() + mins * 60_000_000_000L } },
                            onSelect = ::playIndex, onReorder = { queue = it })
                        if (busy) Text(label("处理中…", "Working…"))
                        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
                    }
                }
              }
            }
        }
    }
    if (overlay && current != null) {
        LyricOverlay(lyricText, lyricColor, onError = { message = it }, onClose = { overlay = false; saveAppearance() })
    }
}

private fun describeError(error: Exception, chinese: Boolean): String {
    if (!chinese) return error.message ?: "Operation failed"
    if (error is SubsonicHttpException) return "音乐服务器返回 HTTP ${error.status}"
    if (error is SubsonicServerException) {
        return when (error.code) {
            40 -> "用户名或密码错误（服务器错误 40）"
            50 -> "此账号没有执行操作的权限（服务器错误 50）"
            else -> "音乐服务器错误 ${error.code}: ${error.message.orEmpty()}"
        }
    }
    return when (error.message) {
        "Invalid server address" -> "服务器地址无效"
        "Use an HTTP(S) server address" -> "请输入 HTTP 或 HTTPS 服务器地址"
        "Unencrypted HTTP requires explicit confirmation" -> "使用 HTTP 前请确认允许不加密连接"
        "Server address must not contain credentials, query, or fragment" -> "服务器地址不能包含账号密码、查询参数或片段"
        "Username and password are required" -> "请输入用户名和密码"
        "Could not connect to the music server" -> "无法连接音乐服务器"
        "Music server request interrupted" -> "音乐服务器请求已中断"
        "Music server returned an invalid response" -> "音乐服务器返回了无效响应"
        "Music server response is too large" -> "音乐服务器响应过大"
        "Unable to protect server credentials" -> "无法加密服务器密码"
        "Unable to save server settings" -> "无法保存服务器设置"
        "Unable to remove server settings" -> "无法删除服务器设置"
        "Unable to decrypt saved credentials for this Windows user" -> "无法为当前 Windows 用户解密保存的密码"
        "Unable to reach the update server" -> "无法连接更新服务器"
        "A different file already exists; choose another download folder" -> "已存在不同的同名文件，请选择其他下载文件夹"
        "Update integrity verification failed" -> "更新文件完整性校验失败"
        "Update exceeds expected size" -> "更新文件超过预期大小"
        "Invalid update metadata" -> "更新信息无效"
        "Update download redirected to an untrusted address" -> "更新下载被重定向到不受信任的地址"
        else -> error.message ?: "操作失败"
    }
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
