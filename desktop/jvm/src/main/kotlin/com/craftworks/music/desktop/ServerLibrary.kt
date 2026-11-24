package com.craftworks.music.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftworks.music.data.model.MediaData
import com.craftworks.music.desktop.SubsonicClient.FavoriteKind
import com.craftworks.music.providers.navidrome.Starred
import java.io.IOException
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

private sealed interface Page {
    data object Artists : Page
    data object Favorites : Page
    data object Radio : Page
    data class Artist(val id: String) : Page
    data class Album(val id: String) : Page
}

private sealed interface Content {
    data class Artists(val artists: List<MediaData.Artist>) : Content
    data class Favorites(val items: Starred) : Content
    data class Radio(val stations: List<MediaData.Radio>) : Content
    data class Artist(val artist: MediaData.Artist) : Content
    data class Album(val album: MediaData.Album) : Content
}

private fun Page.load(client: SubsonicClient): Content = when (this) {
    Page.Artists -> Content.Artists(client.artists())
    Page.Favorites -> Content.Favorites(client.favorites())
    Page.Radio -> Content.Radio(client.internetRadioStations())
    is Page.Artist -> Content.Artist(client.artist(id))
    is Page.Album -> Content.Album(client.album(id))
}

private typealias Label = (String, String) -> String

private fun IOException.description(label: Label): String = when {
    this is SubsonicHttpException && status in setOf(404, 405, 501) ||
        this is SubsonicServerException && code in setOf(20, 30, 70) ->
        label("服务器未提供此功能或项目", "This feature or item is unavailable on the server")
    this is SubsonicServerException && code in setOf(40, 41, 50) ->
        label("服务器拒绝访问", "The server denied access")
    else -> label("请求失败，请重试", "The request failed. Please try again.")
}

/** Radio playback must use the supplied standalone URI, not a Subsonic stream or authenticated transport. */
@Composable
fun ServerLibraryPanel(
    client: SubsonicClient,
    chinese: Boolean,
    onPlayQueue: (List<MediaData.Song>, Int) -> Unit,
    onPlayRadio: (name: String, uri: URI) -> Unit,
    modifier: Modifier = Modifier,
) = key(client) {
    val label: Label = { zh, en -> if (chinese) zh else en }
    var path by remember { mutableStateOf(listOf<Page>(Page.Artists)) }
    val pages = rememberSaveableStateHolder()
    Column(modifier.fillMaxSize()) {
        Row {
            listOf(
                Page.Artists to label("歌手", "Artists"),
                Page.Favorites to label("收藏", "Favorites"),
                Page.Radio to label("网络电台", "Radio"),
            ).forEach { (tab, text) ->
                TextButton({ path = listOf(tab) }, enabled = path.last() != tab) { Text(text) }
            }
        }
        val page = path.last()
        val stateKey = when (page) {
            Page.Artists -> "artists"
            Page.Favorites -> "favorites"
            Page.Radio -> "radio"
            is Page.Artist -> "artist:${page.id}"
            is Page.Album -> "album:${page.id}"
        }
        pages.SaveableStateProvider(stateKey) {
            ServerPage(
                client, page, label, onPlayQueue, onPlayRadio, navigate = { path = path + it },
                back = if (path.size > 1) { { path = path.dropLast(1) } } else null,
            )
        }
    }
}

@Composable
private fun ServerPage(
    client: SubsonicClient, page: Page, label: Label,
    onPlayQueue: (List<MediaData.Song>, Int) -> Unit, onPlayRadio: (String, URI) -> Unit,
    navigate: (Page) -> Unit, back: (() -> Unit)?,
) {
    val scope = rememberCoroutineScope()
    var revision by remember { mutableStateOf(0) }
    var content by remember { mutableStateOf<Content?>(null) }
    var failure by remember { mutableStateOf<IOException?>(null) }
    var busy by remember { mutableStateOf(false) }
    var noSongs by remember { mutableStateOf(false) }
    var radioDialog by remember { mutableStateOf<RadioDialog?>(null) }
    var artistFilter by rememberSaveable { mutableStateOf("") }
    var favoriteArtistsOnly by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(revision) {
        busy = true
        failure = null
        noSongs = false
        try {
            content = runInterruptible(Dispatchers.IO) { page.load(client) }
        } catch (e: IOException) {
            failure = e
        } finally {
            busy = false
        }
    }
    fun action(work: suspend () -> Unit) {
        if (busy) return
        busy = true
        failure = null
        noSongs = false
        scope.launch {
            try {
                work()
            } catch (e: IOException) {
                failure = e
            } finally {
                busy = false
            }
        }
    }
    val favorite: (FavoriteKind, String, Boolean) -> Unit = { kind, id, newValue ->
        action {
            runInterruptible(Dispatchers.IO) { client.setFavorite(kind, id, newValue) }
            revision++
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        back?.let { TextButton(it) { Text(label("返回", "Back")) } }
        TextButton({ revision++ }, enabled = !busy) { Text(label("刷新", "Refresh")) }
        if (busy) Text(label("加载中…", "Loading…"))
    }
    failure?.let { Text(it.description(label), color = MaterialTheme.colorScheme.error) }
    if (noSongs) Text(label("没有可播放的歌曲", "No playable songs"))
    if (page == Page.Artists) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(artistFilter, { artistFilter = it }, Modifier.weight(1f), singleLine = true,
                label = { Text(label("查找歌手", "Find artists")) })
            Checkbox(favoriteArtistsOnly, { favoriteArtistsOnly = it })
            Text(label("仅收藏", "Favorites only"))
        }
    }
    val bio = if (page is Page.Artist) biography(client, page.id, revision, label) else ""
    radioDialog?.let { dialog ->
        StationDialog(client, dialog, label, dismiss = { radioDialog = null }, saved = {
            radioDialog = null
            revision++
        })
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        when (val data = content) {
            null -> Unit
            is Content.Artists -> {
                val visible = data.artists.filter {
                    (!favoriteArtistsOnly || !it.starred.isNullOrBlank()) && it.name.contains(artistFilter.trim(), ignoreCase = true)
                }
                if (visible.isEmpty()) item { Text(label("没有歌手", "No artists")) }
                artists(visible, false, busy, label, navigate, favorite)
            }
            is Content.Favorites -> {
                val artists = data.items.artist.orEmpty()
                val albums = data.items.album.orEmpty()
                val songs = data.items.song.orEmpty()
                if (artists.isEmpty() && albums.isEmpty() && songs.isEmpty()) {
                    item { Text(label("暂无收藏", "No favorites yet")) }
                }
                if (artists.isNotEmpty()) item { Text(label("歌手", "Artists")) }
                artists(artists, true, busy, label, navigate, favorite)
                if (albums.isNotEmpty()) item { Text(label("专辑", "Albums")) }
                albums(albums, true, busy, label, navigate, favorite)
                if (songs.isNotEmpty()) item { Text(label("歌曲", "Songs")) }
                songs(songs, true, busy, label, onPlayQueue, favorite)
            }
            is Content.Radio -> {
                item { TextButton({ radioDialog = RadioDialog(null) }) { Text(label("新建电台", "Add station")) } }
                if (data.stations.isEmpty()) item { Text(label("没有网络电台", "No internet radio stations")) }
                items(data.stations) { station ->
                    val uri = remember(station.media) { serverLibraryRadioUri(station.media) }
                    LibraryRow(station.name) {
                        TextButton({ uri?.let { onPlayRadio(station.name, it) } }, enabled = uri != null) {
                            Text(if (uri != null) label("播放", "Play") else label("无效地址", "Invalid URL"))
                        }
                        TextButton({ radioDialog = RadioDialog(station) }, enabled = station.navidromeID.isNotBlank()) {
                            Text(label("编辑", "Edit"))
                        }
                        TextButton({ radioDialog = RadioDialog(station, delete = true) }, enabled = station.navidromeID.isNotBlank()) {
                            Text(label("删除", "Delete"))
                        }
                    }
                }
            }
            is Content.Album -> {
                val album = data.album
                item {
                    LibraryRow(album.displayName()) {
                        FavoriteButton(!album.starred.isNullOrBlank(), busy || album.navidromeID.isBlank(), label) {
                            favorite(FavoriteKind.ALBUM, album.navidromeID, it)
                        }
                    }
                }
                if (album.songs.isNullOrEmpty()) item { Text(label("没有歌曲", "No songs")) }
                songs(album.songs.orEmpty(), false, busy, label, onPlayQueue, favorite)
            }
            is Content.Artist -> {
                val artist = data.artist
                val albums = artist.album.orEmpty()
                item {
                    LibraryRow(artist.name) {
                        FavoriteButton(!artist.starred.isNullOrBlank(), busy || artist.navidromeID.isBlank(), label) {
                            favorite(FavoriteKind.ARTIST, artist.navidromeID, it)
                        }
                    }
                }
                item {
                    var expanded by remember { mutableStateOf(false) }
                    Text(bio, Modifier.clickable { expanded = !expanded }, maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis)
                }
                if (albums.isEmpty()) item { Text(label("没有专辑", "No albums")) }
                else item {
                    Row {
                        listOf(false, true).forEach { shuffle ->
                            TextButton({
                                action {
                                    val queue = albums.filter { it.navidromeID.isNotBlank() }.flatMap {
                                        runInterruptible(Dispatchers.IO) { client.album(it.navidromeID).songs.orEmpty() }
                                    }.filter { it.navidromeID.isNotBlank() }
                                    if (queue.isEmpty()) noSongs = true
                                    else onPlayQueue(if (shuffle) queue.shuffled() else queue, 0)
                                }
                            }, enabled = !busy) {
                                Text(if (shuffle) label("随机播放", "Shuffle") else label("播放全部", "Play all"))
                            }
                        }
                    }
                }
                albums(albums, false, busy, label, navigate, favorite)
            }
        }
    }
}

@Composable
private fun biography(client: SubsonicClient, id: String, revision: Int, label: Label): String {
    var biography by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<IOException?>(null) }
    LaunchedEffect(id, revision) {
        failure = null
        try {
            biography = runInterruptible(Dispatchers.IO) {
                serverLibraryBiography(client.artistInfo(id)?.biography.orEmpty())
            }
        } catch (e: IOException) {
            failure = e
        }
    }
    return when {
        failure != null -> label("简介：", "Biography: ") + failure!!.description(label)
        biography == null -> label("正在加载简介…", "Loading biography…")
        else -> biography.orEmpty().ifBlank { label("暂无简介", "No biography available") }
    }
}

private data class RadioDialog(val station: MediaData.Radio?, val delete: Boolean = false)

@Composable
private fun StationDialog(
    client: SubsonicClient, dialog: RadioDialog, label: Label, dismiss: () -> Unit, saved: () -> Unit,
) {
    val station = dialog.station
    var name by remember { mutableStateOf(station?.name.orEmpty()) }
    var stream by remember { mutableStateOf(station?.media.orEmpty()) }
    var home by remember { mutableStateOf(station?.homePageUrl.orEmpty()) }
    var failure by remember { mutableStateOf<IOException?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val streamUri = serverLibraryRadioUri(stream)
    val homeUri = serverLibraryRadioUri(home)
    val valid = dialog.delete || name.isNotBlank() && streamUri != null && (home.isBlank() || homeUri != null)
    AlertDialog(
        onDismissRequest = { if (!busy) dismiss() },
        title = { Text(when {
            dialog.delete -> label("删除电台？", "Delete station?")
            station == null -> label("新建电台", "Add station")
            else -> label("编辑电台", "Edit station")
        }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (dialog.delete) Text(station!!.name)
                else {
                    OutlinedTextField(name, { name = it }, enabled = !busy, singleLine = true,
                        label = { Text(label("名称", "Name")) })
                    OutlinedTextField(stream, { stream = it }, enabled = !busy, singleLine = true,
                        isError = stream.isNotBlank() && streamUri == null,
                        label = { Text(label("流地址 (HTTP/S)", "Stream URL (HTTP/S)")) })
                    OutlinedTextField(home, { home = it }, enabled = !busy, singleLine = true,
                        isError = home.isNotBlank() && homeUri == null,
                        label = { Text(label("主页（可选）", "Homepage (optional)")) })
                }
                failure?.let { Text(it.description(label), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton({
                busy = true
                scope.launch {
                    try {
                        runInterruptible(Dispatchers.IO) {
                            if (dialog.delete) client.deleteRadioStation(station!!.navidromeID)
                            else client.saveRadioStation(station?.navidromeID, name, streamUri!!, homeUri)
                        }
                        saved()
                    } catch (e: IOException) {
                        failure = e
                    } finally {
                        busy = false
                    }
                }
            }, enabled = !busy && valid) { Text(if (dialog.delete) label("删除", "Delete") else label("保存", "Save")) }
        },
        dismissButton = { TextButton(dismiss, enabled = !busy) { Text(label("取消", "Cancel")) } },
    )
}

private fun MediaData.Album.displayName() = listOf(name, album, title).firstOrNull { !it.isNullOrBlank() } ?: navidromeID

private fun LazyListScope.artists(
    values: List<MediaData.Artist>, allStarred: Boolean, busy: Boolean, label: Label,
    navigate: (Page) -> Unit, favorite: (FavoriteKind, String, Boolean) -> Unit,
) = items(values) { artist ->
    LibraryRow(artist.name) {
        TextButton({ navigate(Page.Artist(artist.navidromeID)) }, enabled = artist.navidromeID.isNotBlank()) {
            Text(label("查看", "Open"))
        }
        FavoriteButton(allStarred || !artist.starred.isNullOrBlank(), busy || artist.navidromeID.isBlank(), label) {
            favorite(FavoriteKind.ARTIST, artist.navidromeID, it)
        }
    }
}

private fun LazyListScope.albums(
    values: List<MediaData.Album>, allStarred: Boolean, busy: Boolean, label: Label,
    navigate: (Page) -> Unit, favorite: (FavoriteKind, String, Boolean) -> Unit,
) = items(values) { album ->
    LibraryRow(album.displayName()) {
        TextButton({ navigate(Page.Album(album.navidromeID)) }, enabled = album.navidromeID.isNotBlank()) {
            Text(label("查看", "Open"))
        }
        FavoriteButton(allStarred || !album.starred.isNullOrBlank(), busy || album.navidromeID.isBlank(), label) {
            favorite(FavoriteKind.ALBUM, album.navidromeID, it)
        }
    }
}

private fun LazyListScope.songs(
    values: List<MediaData.Song>, allStarred: Boolean, busy: Boolean, label: Label,
    play: (List<MediaData.Song>, Int) -> Unit, favorite: (FavoriteKind, String, Boolean) -> Unit,
) {
    val playable = values.filter { it.navidromeID.isNotBlank() }
    itemsIndexed(playable) { index, song ->
        LibraryRow(listOf(song.title, song.artist).filter { it.isNotBlank() }.joinToString(" · ")) {
            TextButton({ play(playable, index) }) { Text(label("播放", "Play")) }
            FavoriteButton(allStarred || !song.starred.isNullOrBlank(), busy, label) {
                favorite(FavoriteKind.SONG, song.navidromeID, it)
            }
        }
    }
}

@Composable
private fun LibraryRow(title: String, buttons: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        buttons()
    }
}

@Composable
private fun FavoriteButton(starred: Boolean, disabled: Boolean, label: Label, change: (Boolean) -> Unit) {
    TextButton({ change(!starred) }, enabled = !disabled) {
        Text(if (starred) label("取消收藏", "Unfavorite") else label("收藏", "Favorite"))
    }
}
