package com.craftworks.music.data.model

import android.util.Log
import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

// Universal Lyric object
@Stable
data class Lyric(
    val startMs: Int,
    val text: List<String>,
    val words: List<SyncedWord>? = null,
    val endMs: Int? = null
)
@Stable
data class SyncedWord(
    val text: String,
    val startMs: Int,
    val endMs: Int?
)

// LRCLIB Lyrics
@Serializable
data class LrcLibLyrics(
    val id: Int,
    val instrumental: Boolean,
    val duration: Double? = null,
    val plainLyrics: String? = "",
    val syncedLyrics: String? = "",
    val lyricsfile: String? = "",
    val trackName: String? = null,
    val artistName: String? = null,
)

// NetEase Lyrics
@Serializable
data class NeteaseLyricsResponse(
    val pureMusic: Boolean? = false,
    val lrc: NeteaseLrc? = null,
    val tlyric: NeteaseLrc? = null   // translation, may be absent or empty
)
@Serializable
data class NeteaseLrc(
    val lyric: String? = null
)

//region Convert lyric format to app format.
fun MediaData.PlainLyrics.toLyric(): Lyric {
    return Lyric(
        startMs = -1,
        text = if (value.isBlank()) emptyList() else listOf(value)
    )
}

fun MediaData.StructuredLyrics.toLyrics(): List<Lyric> {
    return line
        .groupBy { if (synced) it.start!! + (offset ?: 0) else -1 }
        .map { (timestamp, lines) ->
            Lyric(
                startMs = timestamp,
                text = lines.map { it.value }
            )
        }
        .sortedBy { it.startMs }
}

fun LrcLibLyrics.toLyrics(): List<Lyric> {
    if (instrumental) return listOf()

    if (!lyricsfile.isNullOrBlank() && lyricsfile != "null") {
        val settings = LoadSettings.builder().build()
        val raw = Load(settings).loadFromString(lyricsfile) as? Map<*, *>
            ?: throw IllegalArgumentException("Invalid YAML format")

        val linesList = raw["lines"] as? List<*> ?: emptyList<Any>()
        val lines = linesList.map { lineItem ->
            val lineMap = lineItem as? Map<*, *> ?: emptyMap<Any, Any>()

            val wordsList = lineMap["words"] as? List<*> ?: emptyList<Any>()
            val words = wordsList.map { wordItem ->
                val wordMap = wordItem as? Map<*, *> ?: emptyMap<Any, Any>()
                SyncedWord(
                    text = wordMap["text"]?.toString() ?: "",
                    startMs = wordMap["start_ms"] as? Int ?: 0,
                    endMs = wordMap["end_ms"] as? Int
                )
            }

            Lyric(
                text = listOf(lineMap["text"]?.toString() ?: ""),
                words = words,
                startMs = lineMap["start_ms"]?.toString()?.toInt() ?: 0,
                endMs = lineMap["end_ms"]?.toString()?.toInt() ?: 0
            )
        }
        return lines
    }
    else if (!syncedLyrics.isNullOrBlank() && syncedLyrics != "null") {
        return parseLocalLrc(syncedLyrics)
    }
    else if (!plainLyrics.isNullOrBlank() && plainLyrics != "null") {
        Log.d("LYRICS", "Got LRCLIB plain lyrics: $plainLyrics")
        return listOf(Lyric(-1, listOf(plainLyrics.toString())))
    }
    else
        return listOf()
}

fun NeteaseLyricsResponse.toLyrics(): List<Lyric> {
    if (pureMusic == true)
        return emptyList()

    val originalMap = mutableMapOf<Int, String>()
    val translationMap = mutableMapOf<Int, String>()

    lrc?.lyric?.lines()?.forEach { line ->
        val tags = getTimeStamps(line)
        if (tags.isEmpty()) return@forEach
        val text = line.substringAfter("]").trim()
        tags.forEach { tag ->
            val time = mmssToMilliseconds(tag).toInt()
            originalMap[time] = text
        }
    }
    if (!tlyric?.lyric.isNullOrEmpty()) {
        tlyric.lyric.lines().forEach { line ->
            val tags = getTimeStamps(line)
            if (tags.isEmpty()) return@forEach
            // Group lines sharing the same timestamp
            val text = line.substringAfter("]").trim()
            tags.forEach { tag ->
                val time = mmssToMilliseconds(tag).toInt()
                translationMap[time] = text
            }
        }
    }

    // Group lines sharing the same timestamp
    return originalMap
        .map { (timestamp, origLine) ->
            val lines = buildList {
                add(origLine)
                translationMap[timestamp]?.let { add(it) }
            }
            Lyric(startMs = timestamp, text = lines)
        }
        .sortedBy { it.startMs }
}

//endregion

fun mmssToMilliseconds(mmss: String): Long {
    val match=Regex("""^(\d+):(\d{2})(?:[.:](\d{1,3}))?$""").matchEntire(mmss) ?: return 0L
    val minutes=match.groupValues[1].toLongOrNull() ?: return 0L
    val seconds=match.groupValues[2].toLongOrNull() ?: return 0L
    val fraction=match.groupValues[3].padEnd(3,'0').take(3).toLong()
    return (minutes*60+seconds)*1000+fraction
}

fun getTimeStamps(input: String): List<String> {
    val regex = Regex("\\[(.*?)]")
    val matches = regex.findAll(input)

    val result = mutableListOf<String>()
    for (match in matches) {
        result.add(match.groupValues[1])
    }

    return result
}
/** Local and enhanced LRC; keep word timestamps for desktop karaoke. */
fun parseLocalLrc(raw:String):List<Lyric>{
 if(!Regex("""\[\d{1,3}:\d{2}""").containsMatchIn(raw))return if(raw.isBlank())emptyList()else listOf(Lyric(-1,listOf(raw)))
 val stamp=Regex("""\[(\d{1,3}:\d{2}(?:[.:]\d{1,3})?)\]""")
 val word=Regex("""<(\d{1,3}:\d{2}(?:[.:]\d{1,3})?)>""")
 val offset=Regex("""\[offset:([+-]?\d+)\]""",RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.toIntOrNull()?:0
 return raw.lines().flatMap{line->val tags=stamp.findAll(line).toList();if(tags.isEmpty())emptyList()else{val text=line.substring(tags.last().range.last+1).trim();val marks=word.findAll(text).toList();val words=marks.mapIndexed{i,m->SyncedWord(text.substring(m.range.last+1,marks.getOrNull(i+1)?.range?.first?:text.length),mmssToMilliseconds(m.groupValues[1]).toInt()+offset,marks.getOrNull(i+1)?.let{mmssToMilliseconds(it.groupValues[1]).toInt()+offset})};tags.map{Lyric((mmssToMilliseconds(it.groupValues[1])+offset).toInt().coerceAtLeast(0),listOf(text.replace(word,"")),words.takeIf{it.isNotEmpty()})}}}.sortedBy{it.startMs}
}

/** A duration match alone must never select lyrics for a different recording. */
fun matchesLrcLibRecording(title:String?,artist:String?,duration:Long?,response:LrcLibLyrics):Boolean {
    fun normalized(value:String?)=value.orEmpty().lowercase().filter { it.isLetterOrDigit() }
    if(normalized(title).isBlank()||normalized(artist).isBlank())return false
    if(normalized(title)!=normalized(response.trackName)||normalized(artist)!=normalized(response.artistName))return false
    return duration==null||(response.duration!=null&&kotlin.math.abs(response.duration-duration)<=3)
}
