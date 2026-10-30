package com.craftworks.music.providers.local
import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.io.File
/** Persistent incremental index: only changed file metadata is re-read. */
internal class LocalMusicIndex(context:Context):SQLiteOpenHelper(context,"usb-music.db",null,1){
 override fun onCreate(db:SQLiteDatabase){db.execSQL("CREATE TABLE songs(path TEXT PRIMARY KEY,size INTEGER,modified INTEGER,title TEXT,artist TEXT,album TEXT,duration INTEGER,cover TEXT,id TEXT,seen INTEGER)")}
 override fun onUpgrade(db:SQLiteDatabase,old:Int,new:Int){}
 fun cached(f:File):MediaItem?=readableDatabase.query("songs",null,"path=? AND size=? AND modified=?",arrayOf(f.canonicalPath,f.length().toString(),f.lastModified().toString()),null,null,null).use{c->
  if(!c.moveToFirst())return@use null
  fun str(k:String)=c.getString(c.getColumnIndexOrThrow(k)).orEmpty()
  val uri=Uri.fromFile(f);val meta=MediaMetadata.Builder().setTitle(str("title")).setArtist(str("artist")).setAlbumTitle(str("album")).setDurationMs(c.getLong(c.getColumnIndexOrThrow("duration"))).setArtworkUri(str("cover").takeIf{it.isNotEmpty()}?.let{Uri.parse(it)}).setIsPlayable(true).setIsBrowsable(false).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).setExtras(Bundle().apply{putString("path",f.canonicalPath);putString("navidromeID",str("id"));putString("lyricsArtist",str("artist"));putString("format",f.extension.uppercase())}).build()
  MediaItem.Builder().setMediaId(uri.toString()).setUri(uri).setMediaMetadata(meta).build()
 }
 fun store(f:File,item:MediaItem,generation:Long){val m=item.mediaMetadata;val v=ContentValues().apply{put("path",f.canonicalPath);put("size",f.length());put("modified",f.lastModified());put("title",m.title?.toString());put("artist",m.artist?.toString());put("album",m.albumTitle?.toString());put("duration",m.durationMs?:0);put("cover",m.artworkUri?.toString());put("id",m.extras?.getString("navidromeID"));put("seen",generation)};writableDatabase.insertWithOnConflict("songs",null,v,SQLiteDatabase.CONFLICT_REPLACE)}
 fun prune(generation:Long){writableDatabase.delete("songs","seen!=?",arrayOf(generation.toString()))}
}
