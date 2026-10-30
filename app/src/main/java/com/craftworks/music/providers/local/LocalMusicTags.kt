package com.craftworks.music.providers.local
import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
object LocalMusicTags{
 fun key(f:File)=MessageDigest.getInstance("SHA-256").digest((f.canonicalPath+":"+f.length()+":"+f.lastModified()).toByteArray()).take(16).joinToString(""){"%02x".format(it)}
 fun directory(c:Context)=File(c.filesDir,"local-music").apply{mkdirs()}
 fun decode(bytes:ByteArray):String=try{Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")}catch(_:Exception){String(bytes,charset("GB18030"))}
 /** Only re-decode reversible Latin-1 mojibake; do not rewrite valid Chinese or lossy replacement characters. */
 fun repair(s:String):String{val repaired=com.craftworks.music.data.model.repairLyricText(s);if(repaired!=s)return repaired;if(!s.any{it.code in 128..255}||s.any{it.code>255})return s;val bytes=s.toByteArray(Charsets.ISO_8859_1);val fixed=decode(bytes);return if(fixed.any{it.code in 0x3400..0x9fff}&&!fixed.contains('\uFFFD'))fixed else s}
 fun cover(c:Context,f:File,embedded:ByteArray?):Uri?{
  val out=File(directory(c),key(f)+".jpg");if(out.isFile)return Uri.fromFile(out)
  val source=if(embedded!=null&&embedded.size<=8*1024*1024)embedded else f.parentFile?.listFiles()?.firstOrNull{it.name.lowercase() in setOf("cover.jpg","folder.jpg","cover.png","folder.png")&&it.length()<=8*1024*1024}?.readBytes()
  if(source!=null){val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(source,0,source.size,bounds);var sample=1;while(bounds.outWidth/sample>1024||bounds.outHeight/sample>1024)sample*=2;val bmp=BitmapFactory.decodeByteArray(source,0,source.size,BitmapFactory.Options().apply{inSampleSize=sample})?:return null;out.outputStream().use{bmp.compress(Bitmap.CompressFormat.JPEG,88,it)};bmp.recycle();return Uri.fromFile(out)}
  return null
 }
 fun lyricsFile(c:Context,f:File)=File(directory(c),key(f)+".embedded.lrc")
 fun extractLyrics(c:Context,f:File){val output=lyricsFile(c,f);if(output.exists())return;runCatching{
  RandomAccessFile(f,"r").use{raf->
   val header=ByteArray(10);if(raf.read(header)!=10)return@use
   if(String(header,0,3,Charsets.ISO_8859_1)=="ID3"&&header[3].toInt() in 3..4){
    val end=10+sync(header,6).coerceAtMost(4*1024*1024);var frames=0
    while(raf.filePointer+10<=end&&frames++<10000){val h=ByteArray(10);raf.readFully(h);val id=String(h,0,4,Charsets.ISO_8859_1);val size=if(header[3].toInt()==4)sync(h,4)else ByteBuffer.wrap(h,4,4).int;if(size<=0||size>1024*1024||raf.filePointer+size>end)break;val b=ByteArray(size);raf.readFully(b)
     if(id=="USLT"&&b.size>4){val enc=b[0].toInt();var start=4;if(enc==1||enc==2){while(start+1<b.size&&(b[start].toInt()!=0||b[start+1].toInt()!=0))start+=2;start+=2}else{while(start<b.size&&b[start].toInt()!=0)start++;start++};if(start<b.size){val raw=b.copyOfRange(start,b.size);val text=when(enc){1->String(raw,Charsets.UTF_16);2->String(raw,Charsets.UTF_16BE);3->decode(raw);else->repair(String(raw,Charsets.ISO_8859_1))};output.writeText(text.trim('\u0000'));return@use}}
    }
   }else if(String(header,0,4,Charsets.ISO_8859_1)=="fLaC"){
    raf.seek(4);var blocks=0;while(raf.filePointer+4<f.length()&&blocks++<100){val type=raf.readUnsignedByte();val size=(raf.readUnsignedByte() shl 16)or(raf.readUnsignedByte() shl 8)or raf.readUnsignedByte();if(size>4*1024*1024)break
     if(type and 127==4){val b=ByteArray(size);raf.readFully(b);val buf=ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN);val vendor=buf.int;if(vendor<0||vendor>buf.remaining())break;buf.position(buf.position()+vendor);val count=buf.int.coerceIn(0,10000);repeat(count){if(buf.remaining()<4)return@repeat;val len=buf.int;if(len<0||len>buf.remaining())return@use;val raw=ByteArray(len);buf.get(raw);val text=decode(raw);if(text.substringBefore("=").uppercase() in setOf("LYRICS","UNSYNCEDLYRICS","SYNCEDLYRICS")){output.writeText(text.substringAfter("="));return@use}}}else raf.seek(raf.filePointer+size)
     if(type and 128!=0)break
    }
   }
  }
 };if(!output.exists())output.writeText("")}
 private fun sync(b:ByteArray,i:Int)=((b[i].toInt()and 127)shl 21)or((b[i+1].toInt()and 127)shl 14)or((b[i+2].toInt()and 127)shl 7)or(b[i+3].toInt()and 127)
 fun localLyrics(c:Context,path:String):String{val f=File(path);if(!f.isFile)return "";extractLyrics(c,f);val embedded=lyricsFile(c,f).takeIf{it.isFile&&it.length()<=1024*1024}?.readText().orEmpty();if(embedded.isNotBlank())return embedded;val lrc=f.parentFile?.listFiles()?.firstOrNull{it.nameWithoutExtension==f.nameWithoutExtension&&it.extension.equals("lrc",true)&&it.length()<=1024*1024};if(lrc!=null)return decode(lrc.readBytes());return File(directory(c),key(f)+".network.v2.lrc").takeIf{it.isFile}?.readText().orEmpty()}
}
