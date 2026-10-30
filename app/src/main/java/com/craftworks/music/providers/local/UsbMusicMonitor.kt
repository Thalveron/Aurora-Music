package com.craftworks.music.providers.local
import android.content.*
import com.craftworks.music.managers.*
import java.io.File
import java.util.concurrent.Executors
object UsbMusicMonitor{
 private val worker=Executors.newSingleThreadExecutor()
 @Volatile var mounted:List<String> = emptyList();private set
 fun init(c:Context){val app=c.applicationContext;val filter=IntentFilter().apply{addAction(Intent.ACTION_MEDIA_MOUNTED);addAction(Intent.ACTION_MEDIA_UNMOUNTED);addAction(Intent.ACTION_MEDIA_EJECT);addAction(Intent.ACTION_MEDIA_REMOVED);addDataScheme("file")}
 val receiver=object:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){refresh(app,intent.data?.path?.takeIf{intent.action!=Intent.ACTION_MEDIA_MOUNTED})}}
 if(android.os.Build.VERSION.SDK_INT>=33)app.registerReceiver(receiver,filter,Context.RECEIVER_EXPORTED)else app.registerReceiver(receiver,filter)
 refresh(app,null)}
 fun rescan(c:Context){refresh(c.applicationContext,null)}
 private fun refresh(c:Context,removed:String?){worker.execute{mounted=File("/storage").listFiles()?.filter{it.isDirectory&&it.canRead()&&it.name.matches(Regex("[A-Za-z0-9-]{4,40}"))&&it.name !in setOf("self","emulated")&&it.absolutePath!=removed}?.map{it.canonicalPath}.orEmpty();runCatching{LocalFileLibrary.scan(c,LocalProviderManager.getAllFolders().filter{it!=removed})};DataRefreshManager.notifyDataSourcesChanged();c.sendBroadcast(Intent("com.craftworks.music.tvcustom.DESKTOP_PRUNE").setPackage(c.packageName).putExtra("keep_remote",true),"com.gathin.tvhome.permission.DESKTOP_LYRICS")}}
}
