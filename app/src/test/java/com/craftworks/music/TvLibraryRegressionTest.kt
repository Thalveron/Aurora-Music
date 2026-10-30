package com.craftworks.music
import com.craftworks.music.data.model.MediaData
import com.craftworks.music.providers.navidrome.SubsonicResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException
import org.junit.Assert.*
import org.junit.Test
import com.craftworks.music.data.model.toLyrics
class TvLibraryRegressionTest {
 private val json=Json { ignoreUnknownKeys=true; coerceInputValues=true }
 private fun song(i:Int)= """{"id":"$i","title":"Song $i","created":"2026-10-21T00:00:00Z"}"""
 private fun page(start:Int,count:Int):List<MediaData.Song> {
  val songs=(start until start+count).joinToString(","){song(it)}
  val response="""{"status":"ok","version":"1.16.1","type":"navidrome","serverVersion":"0.64.2","openSubsonic":true,"searchResult3":{"song":[$songs]}}"""
  return json.decodeFromString<SubsonicResponse>(response).searchResult3!!.song!!
 }
 @Test fun albumWithoutCoverOrArtistIdIsValid(){val album=json.decodeFromString<MediaData.Album>("""{"id":"a","artist":"Artist","songCount":1}""");assertNull(album.coverArt);assertNull(album.artistId)}
 @Test fun songWithoutCoverAndBpmIsValid(){val s=json.decodeFromString<MediaData.Song>(song(1));assertEquals("",s.imageUrl);assertEquals(0,s.bpm)}
 @Test fun nullCoverFallsBackToEmpty(){val s=json.decodeFromString<MediaData.Song>(song(1).dropLast(1)+",\"coverArt\":null}");assertEquals("",s.imageUrl)}
 @Test fun pagesKeepAll117SongsAndCanBeReloaded(){val all=page(0,100)+page(100,17);assertEquals(117,all.size);assertEquals(117,all.map{it.navidromeID}.distinct().size);assertEquals(100,page(0,100).size)}
 @Test(expected=SerializationException::class) fun missingSongIdentityStillFails(){json.decodeFromString<MediaData.Song>("""{"title":"bad","created":"2026"}""")}
 @Test fun lineProgressUsesTimestamps(){val l=com.craftworks.music.data.model.Lyric(1000,listOf("歌词"));assertEquals(.5f,com.craftworks.music.player.desktopLyricProgress(l,3000,2000,5000),.001f);assertEquals(0f,com.craftworks.music.player.desktopLyricProgress(l,3000,0,5000),.001f)}
 @Test fun wordProgressRespectsWordLengths(){val l=com.craftworks.music.data.model.Lyric(1000,listOf("你好"),listOf(com.craftworks.music.data.model.SyncedWord("你",1000,1500),com.craftworks.music.data.model.SyncedWord("好",2000,2500)));assertEquals(.5f,com.craftworks.music.player.desktopLyricProgress(l,3000,1750,5000),.001f);assertEquals(1f,com.craftworks.music.player.desktopLyricProgress(l,3000,4000,5000),.001f)}
 @Test fun plainLyricsDoNotPretendToBeSynced(){assertEquals(0f,com.craftworks.music.player.desktopLyricProgress(com.craftworks.music.data.model.Lyric(-1,listOf("普通歌词")),null,10000,20000),.001f)}

 @Test fun validChineseIsNeverReencoded(){val text="星月神话，我的音乐";assertEquals(text,com.craftworks.music.data.model.repairLyricText(text))}
 @Test fun reversibleLatinEncodingIsRepaired(){val bad="你好".toByteArray(Charsets.UTF_8).toString(Charsets.ISO_8859_1);assertEquals("你好",com.craftworks.music.data.model.repairLyricText(bad))}
 @Test fun replacementCharactersAreRejected(){assertFalse(com.craftworks.music.data.model.usableLyrics(listOf(com.craftworks.music.data.model.Lyric(0,listOf("歌词\uFFFD")))))}
 @Test fun blankWordLyricsFallsBackToLrc(){
  val result=com.craftworks.music.data.model.LrcLibLyrics(1,false,syncedLyrics="[ar:artist]\n[00:01.20]你好",lyricsfile="").toLyrics()
  assertEquals(1,result.size);assertEquals(1200,result[0].startMs);assertEquals("你好",result[0].text[0])
 }
 @Test fun lyricFractionsSupportOneTwoAndThreeDigits(){
  assertEquals(1200L,com.craftworks.music.data.model.mmssToMilliseconds("00:01.2"))
  assertEquals(1230L,com.craftworks.music.data.model.mmssToMilliseconds("00:01.23"))
  assertEquals(1234L,com.craftworks.music.data.model.mmssToMilliseconds("00:01.234"))
 }

 @Test fun completeLibraryCountIncludesTheSecondPage()=kotlinx.coroutines.runBlocking{
  val pages=mutableListOf<Int>()
  val all=com.craftworks.music.data.repository.collectPages(key={it:Int->it.toString()}){size,offset->
   pages.add(offset);(offset until minOf(offset+size,117)).toList()
  }
  assertEquals(117,all.size);assertEquals(listOf(0,100),pages)
 }
 @Test fun exactPageBoundaryFinishesAfterEmptyPage()=kotlinx.coroutines.runBlocking{
  val all=com.craftworks.music.data.repository.collectPages(key={it:Int->it.toString()}){size,offset->(offset until minOf(offset+size,200)).toList()}
  assertEquals(200,all.size)
 }
 @Test fun repeatedServerPageCannotInflateCount()=kotlinx.coroutines.runBlocking{
  var requests=0
  val all=com.craftworks.music.data.repository.collectPages(key={it:Int->it.toString()}){size,_->requests++;(0 until size).toList()}
  assertEquals(100,all.size);assertEquals(2,requests)
 }
}
