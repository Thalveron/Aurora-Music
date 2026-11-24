package com.craftworks.music.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.craftworks.music.data.model.MediaData
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AddSongDialog(client: SubsonicClient, song: MediaData.Song, chinese: Boolean, close: () -> Unit) {
    fun tr(a: String, b: String) = if (chinese) a else b
    var playlists by remember { mutableStateOf(emptyList<MediaData.Playlist>()) }
    var name by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun submit(action: () -> Unit) {
        working = true
        scope.launch {
            try { withContext(Dispatchers.IO) { action() }; close() }
            catch (e: IOException) { error = e.message.orEmpty() }
            finally { working = false }
        }
    }
    LaunchedEffect(client) {
        try { playlists = withContext(Dispatchers.IO) { client.playlists() } }
        catch (e: IOException) { error = e.message.orEmpty() }
    }
    AlertDialog(onDismissRequest = { if (!working) close() }, title = { Text(tr("加入歌单", "Add to playlist")) },
        text = { Column {
            Text(song.title)
            LazyColumn(Modifier.heightIn(max = 240.dp)) {
                items(playlists) { playlist ->
                    TextButton(onClick = { submit { client.addToPlaylist(playlist.navidromeID, song.navidromeID) } }, enabled = !working) {
                        Text(playlist.name)
                    }
                }
            }
            OutlinedTextField(name, { name = it }, singleLine = true, enabled = !working, label = { Text(tr("新歌单名称", "New playlist name")) })
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        } }, confirmButton = {
            TextButton(onClick = {
                val title = name.trim()
                submit { client.createPlaylist(title, song.navidromeID) }
            }, enabled = !working && name.isNotBlank()) { Text(tr("创建并加入", "Create and add")) }
        }, dismissButton = { TextButton(close, enabled = !working) { Text(tr("取消", "Cancel")) } })
}
