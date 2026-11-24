package com.craftworks.music.data.model

import android.util.Log
import kotlinx.serialization.Serializable
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

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

/** A duration match alone must never select lyrics for a different recording. */
fun matchesLrcLibRecording(title:String?,artist:String?,duration:Long?,response:LrcLibLyrics):Boolean {
    fun normalized(value:String?)=value.orEmpty().lowercase().filter { it.isLetterOrDigit() }
    if(normalized(title).isBlank()||normalized(artist).isBlank())return false
    if(normalized(title)!=normalized(response.trackName)||normalized(artist)!=normalized(response.artistName))return false
    return duration==null||(response.duration!=null&&kotlin.math.abs(response.duration-duration)<=3)
}
