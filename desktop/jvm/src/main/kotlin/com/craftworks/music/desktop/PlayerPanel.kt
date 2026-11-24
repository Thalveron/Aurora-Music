package com.craftworks.music.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftworks.music.data.model.Lyric
import com.craftworks.music.data.model.MediaData

internal class QueueTrack(
    val song: MediaData.Song,
    val play: () -> Unit,
    val readLyrics: suspend () -> List<Lyric>,
    val reportPlay: ((Boolean) -> Unit)? = null,
)

@Composable
internal fun PlayerPanel(
    queue: PlayQueue<QueueTrack>, playback: PlaybackState, player: AudioPlayer, chinese: Boolean,
    lyric: String, lyricError: String, lyricColor: Color, repeat: RepeatMode, timerMinutes: Int?,
    onRepeat: (RepeatMode) -> Unit, onTimer: (Int?) -> Unit, onSelect: (Int) -> Unit,
    onReorder: (PlayQueue<QueueTrack>) -> Unit,
) {
    val current = queue.current ?: return
    fun tr(zh: String, en: String) = if (chinese) zh else en
    var showQueue by remember { mutableStateOf(false) }
    var volume by remember { mutableStateOf(1f) }
    var dragging by remember(current) { mutableStateOf<Float?>(null) }
    var timerMenu by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(current.song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (lyric.isNotBlank()) Text(lyric, color = lyricColor, maxLines = 2)
                }
                TextButton({ showQueue = true }) { Text(tr("队列", "Queue") + " · ${queue.entries.size}") }
                Box {
                    TextButton({ timerMenu = true }) { Text(timerMinutes?.let { "$it min" } ?: tr("定时", "Timer")) }
                    DropdownMenu(timerMenu, { timerMenu = false }) {
                        for (minutes in listOf(null, 5, 10, 15, 30, 60)) {
                            DropdownMenuItem(text = { Text(minutes?.let { "$it min" } ?: tr("关闭", "Off")) }, onClick = {
                                timerMenu = false
                                onTimer(minutes)
                            })
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val previous = queue.previous(repeat)
                TextButton({ previous?.let(onSelect) }, enabled = previous != null) { Text(tr("上一首", "Prev")) }
                TextButton({ if (playback.ended || playback.error) onSelect(queue.index) else player.toggle() }) {
                    Text(if (playback.playing) tr("暂停", "Pause") else tr("播放", "Play"))
                }
                val next = queue.next(repeat, false)
                TextButton({ next?.let(onSelect) }, enabled = next != null) { Text(tr("下一首", "Next")) }
                Slider(dragging ?: if (playback.durationMs > 0) (playback.positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f) else 0f,
                    onValueChange = { dragging = it }, onValueChangeFinished = {
                        dragging?.let { player.seek((it * playback.durationMs).toLong()) }
                        dragging = null
                    }, enabled = playback.durationMs > 0, modifier = Modifier.weight(1f))
                Text(formatTime(playback.positionMs) + " / " + formatTime(playback.durationMs))
                Text(tr("音量", "Vol"), Modifier.padding(start = 12.dp))
                Slider(volume, { volume = it; player.volume(it) }, modifier = Modifier.width(72.dp))
            }
            if (lyricError.isNotBlank()) Text(lyricError, color = MaterialTheme.colorScheme.error)
            if (playback.error) Text(tr("无法播放，请检查音频格式或连接。", "Unable to play. Check the audio format or connection."), color = MaterialTheme.colorScheme.error)
        }
    }
    if (showQueue) AlertDialog(onDismissRequest = { showQueue = false }, title = { Text(tr("播放队列", "Play queue")) },
        text = { Column {
            Row {
                TextButton({ onReorder(queue.shuffleUpcoming()) }, enabled = queue.index + 2 < queue.entries.size) { Text(tr("打乱待播", "Shuffle upcoming")) }
                TextButton({ onRepeat(RepeatMode.entries[(repeat.ordinal + 1) % RepeatMode.entries.size]) }) {
                    Text(when (repeat) {
                        RepeatMode.OFF -> tr("循环：关闭", "Repeat: off")
                        RepeatMode.ONE -> tr("循环：单曲", "Repeat: one")
                        RepeatMode.ALL -> tr("循环：全部", "Repeat: all")
                    })
                }
            }
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                itemsIndexed(queue.entries) { i, track ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton({ onSelect(i) }, Modifier.weight(1f)) {
                            Text((if (i == queue.index) "▶ " else "") + track.song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton({ onReorder(queue.move(i, i - 1)) }, enabled = i > 0) { Text("↑") }
                        TextButton({ onReorder(queue.move(i, i + 1)) }, enabled = i < queue.entries.lastIndex) { Text("↓") }
                    }
                }
            }
        } }, confirmButton = { TextButton({ showQueue = false }) { Text(tr("完成", "Done")) } })
}

private fun formatTime(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
