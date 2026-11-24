package com.craftworks.music.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Path
import javax.swing.JFileChooser

/** Give this panel bounded height. onPlay receives the selection and its current filtered queue. */
@Composable
internal fun LocalLibraryPanel(
    chinese: Boolean,
    modifier: Modifier = Modifier,
    onPlay: (LocalTrack, List<LocalTrack>) -> Unit,
) {
    fun label(zh: String, en: String) = if (chinese) zh else en
    val store = remember { LocalFoldersStore() }
    val playlistStore = remember { LocalPlaylistsStore() }
    var lists by remember { mutableStateOf(emptyMap<String, List<String>>()) }
    var selectedList by remember { mutableStateOf<String?>(null) }
    var listMenu by remember { mutableStateOf(false) }
    var addTrack by remember { mutableStateOf<LocalTrack?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var listError by remember { mutableStateOf(false) }
    var listReady by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var folders by remember { mutableStateOf<List<Path>>(emptyList()) }
    var saving by remember { mutableStateOf(true) }
    var settingsError by remember { mutableStateOf(false) }
    var revision by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var result by remember(folders, revision) { mutableStateOf<LocalScan?>(null) }
    LaunchedEffect(store) {
        try {
            folders = withContext(Dispatchers.IO) { store.load() }
        } catch (_: IOException) {
            settingsError = true
        } finally {
            saving = false
        }
    }
    LaunchedEffect(playlistStore) {
        try { lists = withContext(Dispatchers.IO) { playlistStore.load() }; listReady = true }
        catch (_: IOException) { listError = true }
    }
    fun saveLists(next: Map<String, List<String>>) {
        listReady = false
        scope.launch {
            try {
                withContext(Dispatchers.IO) { playlistStore.save(next) }
                lists = next
                if (selectedList !in next.keys) selectedList = null
                listError = false
            } catch (_: IOException) { listError = true }
            finally { listReady = true }
        }
    }
    fun save(next: List<Path>) {
        saving = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { store.save(next) }
                folders = next
                settingsError = false
            } catch (_: IOException) {
                settingsError = true
            } finally {
                saving = false
            }
        }
    }
    LaunchedEffect(folders, revision) { result = scanLocalFolders(folders) }
    val tracks = result?.tracks.orEmpty()
    val source = remember(tracks, lists, selectedList) {
        val ids = lists[selectedList]
        if (ids == null) tracks else {
            val indexed = tracks.associateBy { it.song.navidromeID }
            ids.mapNotNull(indexed::get)
        }
    }
    val visible = remember(source, query) { searchLocalTracks(source, query) }
    addTrack?.let { track ->
        var name by remember(track) { mutableStateOf("") }
        fun add(title: String) {
            val existing = lists[title].orEmpty()
            saveLists(lists + (title to (existing + track.song.navidromeID).distinct()))
            addTrack = null
        }
        AlertDialog(onDismissRequest = { addTrack = null }, title = { Text(label("加入本地歌单", "Add to local playlist")) },
            text = { Column {
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(lists.keys.sorted()) { title -> TextButton({ add(title) }) { Text(title) } }
                }
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(label("歌单名称", "Playlist name")) })
            } }, confirmButton = { TextButton({ add(name.trim()) }, enabled = name.isNotBlank()) { Text(label("保存", "Save")) } },
            dismissButton = { TextButton({ addTrack = null }) { Text(label("取消", "Cancel")) } })
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text(label("删除本地歌单？", "Delete local playlist?")) }, text = { Text(selectedList.orEmpty()) },
        confirmButton = { TextButton({ selectedList?.let { saveLists(lists - it) }; confirmDelete = false }) { Text(label("删除", "Delete")) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text(label("取消", "Cancel")) } })
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = !saving, onClick = {
                val chooser = JFileChooser().apply {
                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                    isMultiSelectionEnabled = true
                    dialogTitle = label("选择音乐文件夹", "Select music folders")
                }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    val selected = chooser.selectedFiles.map { it.toPath().toAbsolutePath().normalize() }
                    save((folders + selected).distinct())
                }
            }) { Text(label("添加文件夹", "Add folder")) }
            TextButton(enabled = !saving && folders.isNotEmpty() && result != null, onClick = { revision++ }) {
                Text(label("重新扫描", "Rescan"))
            }
        }
        Text(label("仅扫描所选文件夹：FLAC、OGG、Opus、MP3、WAV、AIFF、M4A。", "Only selected folders are scanned: FLAC, OGG, Opus, MP3, WAV, AIFF, M4A."))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                TextButton({ listMenu = true }, enabled = listReady) { Text(selectedList ?: label("全部本地歌曲", "All local tracks")) }
                DropdownMenu(listMenu, { listMenu = false }) {
                    (listOf(null) + lists.keys.sorted()).forEach { name ->
                        DropdownMenuItem(text = { Text(name ?: label("全部本地歌曲", "All local tracks")) },
                            onClick = { selectedList = name; listMenu = false })
                    }
                }
            }
            if (selectedList != null) TextButton({ confirmDelete = true }, enabled = listReady) { Text(label("删除歌单", "Delete playlist")) }
        }
        if (listError) Text(label("无法读取或保存本地歌单。", "Could not read or save local playlists."), color = MaterialTheme.colorScheme.error)
        if (settingsError) Text(label("无法读取或保存文件夹设置。", "Could not read or save folder settings."),
            color = MaterialTheme.colorScheme.error)
        OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text(label("搜索本地歌曲、歌手或专辑", "Search local songs, artists or albums")) })
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(folders, key = { "folder:$it" }) { folder ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(folder.toString(), Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    TextButton(enabled = !saving, onClick = { save(folders.filterNot { it == folder }) }) {
                        Text(label("移除", "Remove"))
                    }
                }
            }
            item {
                Text(when {
                    saving -> label("正在读取或保存设置…", "Reading or saving settings…")
                    folders.isEmpty() -> label("请选择音乐文件夹。", "Choose a music folder to get started.")
                    result == null -> label("正在扫描…", "Scanning…")
                    visible.isEmpty() -> label("没有找到歌曲。", "No matching songs.")
                    else -> label("${visible.size} 首歌曲", "${visible.size} songs")
                })
                val skipped = result?.skipped ?: 0
                if (skipped > 0) Text(label("无法访问 $skipped 个项目。", "$skipped entries could not be accessed."))
            }
            items(visible, key = { it.song.navidromeID }) { track ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { onPlay(track, visible) }.padding(8.dp)) {
                    Text(track.song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOf(track.song.artist, track.song.album).filter(String::isNotEmpty).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton({ addTrack = track }, enabled = listReady) { Text(label("加入歌单", "Add to playlist")) }
                selectedList?.let { title ->
                    TextButton({ saveLists(lists + (title to (lists[title].orEmpty() - track.song.navidromeID))) }, enabled = listReady) {
                        Text(label("移出歌单", "Remove"))
                    }
                }
                }
            }
        }
    }
}
