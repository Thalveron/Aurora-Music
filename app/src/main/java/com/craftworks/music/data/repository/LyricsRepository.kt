package com.craftworks.music.data.repository

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaMetadata
import com.craftworks.music.data.datasource.lrclib.LrclibDataSource
import com.craftworks.music.data.datasource.navidrome.NavidromeDataSource
import com.craftworks.music.data.datasource.netease.NeteaseDataSource
import com.craftworks.music.data.model.*
import com.craftworks.music.managers.NavidromeManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

object LyricsState {
 val lyrics=MutableStateFlow<List<Lyric>>(emptyList())
 val loading=MutableStateFlow(false)
 var open=mutableStateOf(false)
 var useLrcLib by mutableStateOf(true)
 var useNetEase by mutableStateOf(false)
}
@Singleton
class LyricsRepository @Inject constructor(
 val lrclibDataSource:LrclibDataSource,
 val neteaseDataSource:NeteaseDataSource,
 val navidromeDataSource:NavidromeDataSource,
 @ApplicationContext private val context:Context
) {
 private var lyricsFetchJob:Job?=null
 private val generation=java.util.concurrent.atomic.AtomicLong()

 suspend fun getLyrics(metadata:MediaMetadata?,ignoreCachedResponse:Boolean=false) {
  val request=generation.incrementAndGet()
  lyricsFetchJob?.cancel()
  LyricsState.lyrics.value=emptyList()
  if(metadata==null||metadata.mediaType==MediaMetadata.MEDIA_TYPE_RADIO_STATION)return
  coroutineScope {
   lyricsFetchJob=launch {
    LyricsState.loading.value=true
    val report=StringBuilder("歌曲："+metadata.title+"\n")
    var selected="无可用歌词"
    suspend fun fetch(name:String,block:suspend ()->List<Lyric>):List<Lyric> {
     val raw=try{withTimeoutOrNull(5000){block()}.orEmpty()}catch(e:CancellationException){throw e}catch(_:Exception){emptyList()}
     val clean=cleanLyrics(raw)
     val joined=raw.flatMap{it.text}.joinToString("\n")
     report.append(name+": lines="+raw.size+", replacements="+joined.count{it=='\uFFFD'}+", damage="+lyricTextDamage(joined)+"\n")
     report.append("sample="+joined.take(180).replace("\n"," / ")+"\n")
     return clean
    }
    try {
     val id=metadata.extras?.getString("navidromeID").orEmpty()
     val nav=if(NavidromeManager.checkActiveServers()&&!id.startsWith("Local_"))fetch("Navidrome同步"){navidromeDataSource.getNavidromeSyncedLyrics(id,true)} else emptyList()
     var chosen:List<Lyric> = emptyList()
     val localPath=metadata.extras?.getString("path").orEmpty()
     if(localPath.isNotEmpty()){val local=withContext(Dispatchers.IO){com.craftworks.music.providers.local.LocalMusicTags.localLyrics(context,localPath)};if(local.isNotBlank()){chosen=com.craftworks.music.data.model.parseLocalLrc(local);selected="本地内嵌 / LRC / 缓存"}}
     if(chosen.isEmpty()&&usableLyrics(nav)){chosen=nav;selected="Navidrome同步"}
     if(chosen.isEmpty()&&localPath.isNotEmpty()){chosen=fetch("本地音乐联网歌词"){com.craftworks.music.providers.local.LocalMusicOnline.lyrics(context,metadata)};selected="本地音乐联网歌词"}
     if(chosen.isEmpty()&&localPath.isEmpty()&&LyricsState.useLrcLib) {
      val lrc=fetch("LRCLIB"){lrclibDataSource.getLrcLibLyrics(metadata,true)}
      if(usableLyrics(lrc)){chosen=lrc;selected="LRCLIB";if(localPath.isNotEmpty())withContext(Dispatchers.IO){val f=File(localPath);val cache=File(com.craftworks.music.providers.local.LocalMusicTags.directory(context),com.craftworks.music.providers.local.LocalMusicTags.key(f)+".network.v2.lrc");cache.writeText(lrc.joinToString("\n"){line->val t=line.startMs.coerceAtLeast(0);String.format(java.util.Locale.ROOT,"[%02d:%02d.%03d]",t/60000,t/1000%60,t%1000)+line.text.joinToString(" ")})}}
     }
     if(chosen.isEmpty()&&localPath.isEmpty()&&(LyricsState.useNetEase||nav.isNotEmpty())) {
      val net=fetch("网易备用"){neteaseDataSource.getNeteaseLyrics(metadata)}
      if(usableLyrics(net)){chosen=net;selected="网易备用"}
     }
     if(chosen.isEmpty()&&NavidromeManager.checkActiveServers()&&!id.startsWith("Local_")) {
      val plain=fetch("Navidrome纯文本"){navidromeDataSource.getNavidromePlainLyrics(metadata,true)}
      if(usableLyrics(plain)){chosen=plain;selected="Navidrome纯文本"}
     }
     ensureActive()
     if(request!=generation.get())return@launch
     LyricsState.lyrics.value=chosen
     report.append("选择："+selected+"\n")
     withContext(Dispatchers.IO){runCatching { File(context.getExternalFilesDir(null),"lyrics-source-report.txt").writeText(report.toString()) }}
    }finally{if(request==generation.get())LyricsState.loading.value=false}
   }
  }
 }
}
