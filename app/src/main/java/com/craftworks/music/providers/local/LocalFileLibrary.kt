package com.craftworks.music.providers.local

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Direct folder scanning also works when a TV never indexes USB music in MediaStore. */
object LocalFileLibrary {
    private data class Cached(val size:Long,val changed:Long,val item:MediaItem)
    private val cache=ConcurrentHashMap<String,Cached>()
    private val scanLock=Any()
    private val worker=java.util.concurrent.Executors.newSingleThreadExecutor()
    private val scheduled=java.util.concurrent.atomic.AtomicBoolean()
    @Volatile var lastSongs:List<MediaItem> = emptyList();private set
    @Volatile var metadataReads=0;private set
    @Volatile var scanning=false;private set
    fun status()=org.json.JSONObject().put("songCount",lastSongs.size).put("metadataReads",metadataReads).put("scanning",scanning)
    private val extensions=setOf("mp3","flac","wav","m4a","aac","ogg","opus","ape")
    fun invalidate(){cache.clear()}
    fun roots(context:Context):List<File> = buildList {
        add(Environment.getExternalStorageDirectory())
        context.getExternalFilesDirs(null).filterNotNull().forEach { add(File(it.absolutePath.substringBefore("/Android/"))) }
        File("/storage").listFiles()?.filter { it.isDirectory&&it.name !in setOf("self","emulated") }?.let { addAll(it) }
        System.getenv("SECONDARY_STORAGE")?.split(":")?.filter { it.isNotBlank() }?.forEach { add(File(it)) }
    }.filter { it.exists()&&it.canRead() }.distinctBy { it.canonicalPath }

    fun scan(context:Context,folders:List<String>):List<MediaItem>{
        if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper()){if(scheduled.compareAndSet(false,true))worker.execute{try{scan(context.applicationContext,folders);com.craftworks.music.managers.DataRefreshManager.notifyDataSourcesChanged()}finally{scheduled.set(false)}};return lastSongs}
        return synchronized(scanLock){scanning=true;metadataReads=0;try{scanLocked(context,folders).also{lastSongs=it}}finally{scanning=false}}
    }
    private fun scanLocked(context:Context,folders:List<String>):List<MediaItem>{
        val db=LocalMusicIndex(context);val generation=System.currentTimeMillis()
        val pending=java.util.ArrayDeque<File>();folders.forEach { pending.add(File(it)) }
        val visited=hashSetOf<String>();val songs=linkedMapOf<String,MediaItem>();var entries=0
        while(pending.isNotEmpty()&&entries++<100000){
            val file=pending.removeFirst();val path=try{file.canonicalPath}catch(_:Exception){continue}
            if(!visited.add(path)||!file.canRead()||file.isHidden)continue
            if(file.isDirectory){file.listFiles()?.filter { it.name !in setOf("Android",".Trash",".Trashes") }?.forEach { pending.add(it) };continue}
            if(file.extension.lowercase() !in extensions)continue
            val saved=cache[path];val item=if(saved!=null&&saved.size==file.length()&&saved.changed==file.lastModified())saved.item else (db.cached(file)?:metadata(context,file,path)).also { cache[path]=Cached(file.length(),file.lastModified(),it) }
            val cover=if(item.mediaMetadata.artworkUri==null)LocalMusicTags.cover(context,file,null)else null
            val enriched=if(cover!=null)item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setArtworkUri(cover).build()).build()else item
            db.store(file,enriched,generation);songs[path]=enriched;LocalMusicOnline.enrich(context,file,enriched)
        }
        db.prune(generation);db.close()
        cache.keys.removeAll { it !in songs.keys }
        return songs.values.sortedBy { it.mediaMetadata.title.toString().lowercase() }
    }
    private fun metadata(context:Context,file:File,path:String):MediaItem{
        metadataReads++
        var title=file.nameWithoutExtension;var artist="未知歌手";var album=file.parentFile?.name?:"本地音乐";var duration=0L
        var artwork:Uri?=null
        val reader=MediaMetadataRetriever()
        try{reader.setDataSource(path);title=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?:title;artist=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }?:artist;album=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?:album;duration=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?:0L;artwork=LocalMusicTags.cover(context,file,reader.embeddedPicture)}catch(_:Exception){}finally{try{reader.release()}catch(_:Exception){}}
        title=LocalMusicTags.repair(title);artist=LocalMusicTags.repair(artist);album=LocalMusicTags.repair(album)
        if(title==file.nameWithoutExtension){val split=title.split(" - ",limit=2);if(split.size==2){if(artist=="未知歌手")artist=split[0].trim();title=split[1].trim()}}
        if(artwork==null)artwork=LocalMusicTags.cover(context,file,null)
        LocalMusicTags.extractLyrics(context,file)
        val id="Local_File_"+MessageDigest.getInstance("SHA-256").digest(path.toByteArray()).take(12).joinToString(""){"%02x".format(it)}
        val metadata=MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album).setArtworkUri(artwork).setDurationMs(duration).setIsPlayable(true).setIsBrowsable(false).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).setExtras(Bundle().apply{putString("navidromeID",id);putString("path",path);putString("lyricsArtist",artist);putString("format",file.extension.uppercase())}).build()
        return MediaItem.Builder().setMediaId(Uri.fromFile(file).toString()).setUri(Uri.fromFile(file)).setMediaMetadata(metadata).build()
    }
    fun available(context:Context,item:MediaItem,keepRemote:Boolean):Boolean{
        val uri=item.localConfiguration?.uri?:Uri.parse(item.mediaId)
        if(uri.scheme=="http"||uri.scheme=="https")return keepRemote
        val path=item.mediaMetadata.extras?.getString("path")
        if(path!=null)return File(path).isFile&&File(path).canRead()
        if(uri.scheme=="file")return uri.path?.let { File(it).isFile&&File(it).canRead() }?:false
        return try{context.contentResolver.openAssetFileDescriptor(uri,"r")?.use { true }?:false}catch(_:Exception){false}
    }
}
