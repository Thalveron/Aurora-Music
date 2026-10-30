package com.craftworks.music.providers.local
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap
object LocalMusicOnline{
 private val worker=Executors.newSingleThreadExecutor()
 private val queued=ConcurrentHashMap.newKeySet<String>()
 fun config(c:Context):JSONObject{val p=c.getSharedPreferences("local_music_online",0);return JSONObject().put("coverEnabled",p.getBoolean("covers",false)).put("lyricsEnabled",p.getBoolean("lyrics",true)).put("lyricsUrls",JSONArray(p.getString("urls","[\"https://lrclib.net\"]"))).put("library",LocalFileLibrary.status())}
 fun save(c:Context,v:JSONObject):JSONObject{val urls=v.getJSONArray("lyricsUrls");require(urls.length() in 1..5){"歌词服务需1～5个"};for(i in 0 until urls.length()){val url=URL(urls.getString(i));require(url.protocol=="https"&&url.userInfo==null&&url.host.isNotBlank()&&url.query==null&&url.ref==null){"歌词服务只支持HTTPS基础地址"}};c.getSharedPreferences("local_music_online",0).edit().putBoolean("covers",v.getBoolean("coverEnabled")).putBoolean("lyrics",v.getBoolean("lyricsEnabled")).putString("urls",urls.toString()).apply();return config(c)}
 private fun request(url:String,deadline:Long,limit:Int):ByteArray{
  var address=url;repeat(4){val u=URL(address);require(u.protocol=="https");val remaining=(deadline-android.os.SystemClock.elapsedRealtime()).toInt();require(remaining>0);val conn=u.openConnection() as HttpURLConnection;conn.connectTimeout=remaining;conn.readTimeout=remaining;conn.instanceFollowRedirects=false;conn.setRequestProperty("User-Agent","AuroraLocalMusic/1.0 (Android TV; local library metadata)")
   try{val status=conn.responseCode;if(status in 300..399){address=URL(u,conn.getHeaderField("Location")?:error("redirect")).toString()}else{require(status==200);return conn.inputStream.use{input->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){require(android.os.SystemClock.elapsedRealtime()<deadline);val n=input.read(buffer);if(n<0)break;require(out.size()+n<=limit);out.write(buffer,0,n)};out.toByteArray()}}}finally{conn.disconnect()}}
  error("redirect limit")
 }
 private fun norm(v:String)=v.lowercase().replace(Regex("[\\p{P}\\p{Z}\\s]"),"")
 fun enrich(c:Context,file:File,item:MediaItem){val settings=config(c);if(!settings.optBoolean("coverEnabled")||item.mediaMetadata.artworkUri!=null||queued.size>200)return;val key=LocalMusicTags.key(file);if(!queued.add(key))return;worker.execute{try{val dir=LocalMusicTags.directory(c);val attempt=File(dir,key+".cover-attempt");if(attempt.exists()&&System.currentTimeMillis()-attempt.lastModified()<86400000)return@execute;attempt.writeText("");Thread.sleep(1100);val m=item.mediaMetadata;val title=m.title.toString();val artist=m.artist.toString();if(artist=="未知歌手"||title.isBlank()||(m.durationMs?:0)<=0)return@execute;val deadline=android.os.SystemClock.elapsedRealtime()+5000;val q="recording:\""+title.replace("\"","")+"\" AND artist:\""+artist.replace("\"","")+"\"";val data=JSONObject(String(request("https://musicbrainz.org/ws/2/recording/?fmt=json&limit=5&query="+URLEncoder.encode(q,"UTF-8"),deadline,1024*1024),Charsets.UTF_8));val recordings=data.optJSONArray("recordings")?:return@execute
  for(i in 0 until recordings.length()){val record=recordings.getJSONObject(i);val credits=record.optJSONArray("artist-credit")?:continue;val names=(0 until credits.length()).map{credits.getJSONObject(it).optJSONObject("artist")?.optString("name").orEmpty()};if(norm(record.optString("title"))!=norm(title)||names.none{norm(it)==norm(artist)}||kotlin.math.abs(record.optLong("length")-(m.durationMs?:0))>3000)continue;val release=record.optJSONArray("releases")?.optJSONObject(0)?.optString("id").orEmpty();if(!release.matches(Regex("[a-fA-F0-9-]{36}")))continue;val bytes=request("https://coverartarchive.org/release/"+release+"/front-500",deadline,4*1024*1024);if(file.isFile)LocalMusicTags.cover(c,file,bytes);com.craftworks.music.managers.DataRefreshManager.notifyDataSourcesChanged();break}
 }catch(_:Exception){}finally{queued.remove(key)}}}
 suspend fun lyrics(c:Context,m:androidx.media3.common.MediaMetadata):List<com.craftworks.music.data.model.Lyric> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){
  val config=config(c);if(!config.optBoolean("lyricsEnabled"))return@withContext emptyList();val duration=(m.durationMs?:0)/1000.0;if(duration<=0)return@withContext emptyList();val title=m.title?.toString().orEmpty();val artist=m.artist?.toString().orEmpty();if(title.isBlank()||artist.isBlank()||artist=="未知歌手")return@withContext emptyList();val urls=config.getJSONArray("lyricsUrls");val deadline=android.os.SystemClock.elapsedRealtime()+5000
  for(i in 0 until urls.length())try{val address=urls.getString(i).trimEnd('/')+"/api/get?track_name="+URLEncoder.encode(title,"UTF-8")+"&artist_name="+URLEncoder.encode(artist,"UTF-8")+"&duration="+duration;val obj=JSONObject(String(request(address,deadline,1024*1024),Charsets.UTF_8));if(!obj.has("duration")||kotlin.math.abs(obj.getDouble("duration")-duration)>3||norm(obj.optString("trackName"))!=norm(title)||norm(obj.optString("artistName"))!=norm(artist))continue;val raw=obj.optString("syncedLyrics").takeIf{it!="null"}.orEmpty().ifBlank{obj.optString("plainLyrics").takeIf{it!="null"}.orEmpty()};if(raw.isBlank())continue;val path=m.extras?.getString("path");if(path!=null){val file=File(path);if(file.isFile)File(LocalMusicTags.directory(c),LocalMusicTags.key(file)+".network.v2.lrc").writeText(raw)};return@withContext com.craftworks.music.data.model.parseLocalLrc(raw)}catch(_:Exception){}
  emptyList()
 }
}
