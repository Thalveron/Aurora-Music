package com.craftworks.music.player
import com.craftworks.music.data.model.Lyric
/** Word timestamps when available, otherwise a linear estimate within the lyric line. */
fun desktopLyricProgress(line:Lyric,nextStart:Int?,position:Long,duration:Long):Float {
 if(line.startMs<0)return 0f
 val lineEnd=(line.endMs?.toLong() ?: nextStart?.toLong() ?: duration).coerceAtLeast(line.startMs.toLong()+1)
 val words=line.words.orEmpty()
 if(words.isEmpty())return ((position-line.startMs).toFloat()/(lineEnd-line.startMs)).coerceIn(0f,1f)
 var completed=0f;var total=0
 words.forEachIndexed { index,word ->
  val weight=word.text.length.coerceAtLeast(1);total+=weight
  val end=(word.endMs?.toLong() ?: words.getOrNull(index+1)?.startMs?.toLong() ?: lineEnd).coerceAtLeast(word.startMs.toLong()+1)
  completed+=weight*((position-word.startMs).toFloat()/(end-word.startMs)).coerceIn(0f,1f)
 }
 return (completed/total.coerceAtLeast(1)).coerceIn(0f,1f)
}
