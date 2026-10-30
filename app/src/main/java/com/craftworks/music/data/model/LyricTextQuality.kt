package com.craftworks.music.data.model

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

fun lyricTextDamage(text:String):Int = text.count { it=='\uFFFD' }*20 +
 text.count { it.isISOControl() && it!='\n' && it!='\r' && it!='\t' }*10 +
 Regex("Ã|Â|â€|ðŸ|锟斤拷|[\u00E0-\u00EF][\u0080-\u00BF]{2}|[\u00C2-\u00DF][\u0080-\u00BF]").findAll(text).count()*4

/** Only reversible decoding errors with a strictly lower damage score are repaired. */
fun repairLyricText(text:String):String {
 if(lyricTextDamage(text)==0)return text
 var best=text
 for(name in listOf("ISO-8859-1","windows-1252","GB18030")) {
  try {
   val encoder=Charset.forName(name).newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
   val bytes=encoder.encode(CharBuffer.wrap(text))
   val decoded=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(bytes).toString()
   if(lyricTextDamage(decoded)<lyricTextDamage(best))best=decoded
  }catch(_:Exception){}
 }
 return best
}
fun cleanLyrics(lines:List<Lyric>):List<Lyric> = lines.map { it.copy(text=it.text.map(::repairLyricText),words=it.words?.map { word->word.copy(text=repairLyricText(word.text)) }) }
fun usableLyrics(lines:List<Lyric>):Boolean {
 val text=lines.flatMap { it.text }.joinToString("\n")
 return text.isNotBlank() && lyricTextDamage(text)==0
}
