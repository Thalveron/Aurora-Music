package com.craftworks.music

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.*
import com.craftworks.music.managers.*
import com.craftworks.music.managers.settings.LocalDataSettingsManager
import com.craftworks.music.providers.local.LocalFileLibrary
import com.craftworks.music.data.datasource.navidrome.NavidromeDataSource
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class LocalLibraryActivity:Activity(){
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private lateinit var rows:LinearLayout
    private lateinit var status:TextView
    private fun label(value:String,size:Float=18f):TextView=TextView(this).also { it.text=value;it.textSize=size;it.setTextColor(-1);it.setPadding(0,16,0,16);rows.addView(it) }
    private fun button(value:String,action:()->Unit){Button(this).also { it.text=value;it.isAllCaps=false;it.textSize=19f;it.setOnClickListener { action() };rows.addView(it,LinearLayout.LayoutParams(-1,80)) }}
    override fun onCreate(state:Bundle?){super.onCreate(state);render()}
    private fun render(){val scroll=ScrollView(this);scroll.setBackgroundColor(0xff10151c.toInt());rows=LinearLayout(this);rows.orientation=1;rows.setPadding(60,28,60,40);scroll.addView(rows);setContentView(scroll)
        label("本地音乐 / U盘与来源清理",28f)
        status=label("插入U盘后选择音乐文件夹。选歌、桌面卡片和播放列表都能使用本地歌曲。")
        button("添加U盘 / 本地音乐文件夹"){if(android.os.Build.VERSION.SDK_INT<33&&checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),31)else chooseRoots()}
        button("重新扫描本地歌曲"){scan()}
        val online=com.craftworks.music.providers.local.LocalMusicOnline.config(this)
        for(pair in listOf("coverEnabled" to "联网补全专辑封面","lyricsEnabled" to "联网补全本地歌词")){val toggle=Switch(this);toggle.text=pair.second;toggle.isChecked=online.optBoolean(pair.first);toggle.setTextColor(-1);toggle.setOnCheckedChangeListener{_,value->online.put(pair.first,value);com.craftworks.music.providers.local.LocalMusicOnline.save(this,online)};rows.addView(toggle)}
        label("联网缓存只存电视内部，不写入U盘；每次联网最多等待5秒。歌词地址可在 Aurora 网页后台修改。")
        button("仅使用本地 / U盘（保留服务器账号）"){NavidromeManager.setCurrentServer(null);clean(false)}
        button("一键清理失联来源 / 丢失歌曲"){status.text="正在检查服务器和本地音乐…";scope.launch {val online=if(NavidromeManager.checkActiveServers())withTimeoutOrNull(7000){NavidromeDataSource().pingNavidromeServer().contains("ok")}==true else false;if(!online)NavidromeManager.setCurrentServer(null);clean(online)}}
        label("清理只更新歌曲列表和播放记录，不删除U盘文件，也不删除服务器账号。服务器恢复后，可在“音乐来源 → 网络服务器”重新选中。")
        label("已添加文件夹",22f)
        LocalProviderManager.getAllFolders().forEach { folder->button(folder){AlertDialog.Builder(this).setTitle("文件夹").setMessage(folder).setNegativeButton("取消",null).setPositiveButton("移除来源（保留文件）"){_,_->LocalProviderManager.removeFolder(folder);LocalFileLibrary.invalidate();render()}.show()} }
    }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,results:IntArray){super.onRequestPermissionsResult(requestCode,permissions,results);if(requestCode==31&&results.firstOrNull()==android.content.pm.PackageManager.PERMISSION_GRANTED)chooseRoots()else status.text="需要允许读取存储，才能读取U盘歌曲。"}
    private fun chooseRoots(){val roots=LocalFileLibrary.roots(this);if(roots.isEmpty()){status.text="未识别到可读取的存储，请检查U盘后重试。";return};AlertDialog.Builder(this).setTitle("选择存储位置").setItems(roots.map { (if(it.absolutePath.contains("/emulated/")||it.absolutePath=="/sdcard")"电视内置存储"else"外接存储 / U盘")+"  "+it.absolutePath }.toTypedArray()){_,i->browse(roots[i],roots[i])}.setNegativeButton("取消",null).show()}
    private fun browse(dir:File,root:File){val directories=dir.listFiles()?.filter { it.isDirectory&&!it.isHidden&&it.canRead() }?.sortedBy { it.name.lowercase() }?:emptyList();val up=dir.canonicalPath!=root.canonicalPath;val names=(if(up)listOf("返回上一级")else emptyList())+directories.map { it.name };AlertDialog.Builder(this).setTitle("音乐文件夹：${dir.name}").setMessage(null).setItems(names.toTypedArray()){_,i->if(up&&i==0)browse(dir.parentFile?:root,root)else browse(directories[i-(if(up)1 else 0)],root)}.setNegativeButton("取消",null).setPositiveButton("添加此文件夹"){_,_->LocalProviderManager.addFolder(dir.canonicalPath);LocalFileLibrary.invalidate();render();scan()}.show()}
    private fun scan(){status.text="正在扫描本地音乐…";scope.launch {val count=withContext(Dispatchers.IO){LocalFileLibrary.invalidate();LocalFileLibrary.scan(this@LocalLibraryActivity,LocalProviderManager.getAllFolders()).size};DataRefreshManager.notifyDataSourcesChanged();status.text="扫描完成：本地歌曲共 $count 首。"}}
    private fun clean(keepRemote:Boolean){scope.launch {
        val manager=LocalDataSettingsManager(applicationContext);val saved=manager.playbackResumptionPlaylistWithStartPosition.first()
        val filtered=withContext(Dispatchers.IO){saved.mediaItems.filter { LocalFileLibrary.available(applicationContext,it,keepRemote) }}
        manager.setPlaybackResumption(filtered,0,0)
        sendBroadcast(Intent("com.craftworks.music.tvcustom.DESKTOP_PRUNE").setPackage(packageName).putExtra("keep_remote",keepRemote))
        sendBroadcast(Intent("com.gathin.tvhome.LIBRARY_CHANGED").setPackage("com.gathin.tvhome").putExtra("clear_cache",true),"com.gathin.tvhome.permission.DESKTOP_LYRICS")
        LocalFileLibrary.invalidate();DataRefreshManager.notifyDataSourcesChanged();status.text="清理完成：移除 ${saved.mediaItems.size-filtered.size} 条旧播放记录。"+(if(keepRemote)"服务器连接正常，保留网络歌曲。"else"当前仅使用本地 / U盘歌曲，服务器账号已保留。")
    }}
    override fun onDestroy(){scope.cancel();super.onDestroy()}
}
