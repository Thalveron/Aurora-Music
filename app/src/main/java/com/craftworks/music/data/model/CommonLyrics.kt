package com.craftworks.music.data.model

import androidx.compose.runtime.Stable

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

fun MediaData.PlainLyrics.toLyric(): Lyric = Lyric(
    startMs = -1,
    text = if (value.isBlank()) emptyList() else listOf(value)
)

fun MediaData.StructuredLyrics.toLyrics(): List<Lyric> =
    line
        .groupBy { if (synced) it.start!! + (offset ?: 0) else -1 }
        .map { (timestamp, lines) -> Lyric(startMs = timestamp, text = lines.map { it.value }) }
        .sortedBy { it.startMs }

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
