package com.craftworks.music.providers.local
import android.content.*
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import org.json.JSONObject
class LocalMusicSettingsProvider:ContentProvider(){
 override fun onCreate()=true
 override fun call(method:String,arg:String?,extras:Bundle?):Bundle{val c=context?:error("Context");c.enforceCallingOrSelfPermission("com.gathin.tvhome.permission.DESKTOP_LYRICS","仅允许已授权桌面设置");val json=when(method){"get"->LocalMusicOnline.config(c);"save"->LocalMusicOnline.save(c,JSONObject(extras?.getString("json")?:error("JSON")));"scan"->{UsbMusicMonitor.rescan(c);JSONObject().put("accepted",true)};else->throw IllegalArgumentException("未知操作")};return Bundle().apply{putString("json",json.toString())}}
 override fun query(uri:Uri,p:Array<out String>?,s:String?,args:Array<out String>?,o:String?):Cursor?=null
 override fun getType(uri:Uri):String?=null
 override fun insert(uri:Uri,v:ContentValues?):Uri?=null
 override fun delete(uri:Uri,s:String?,args:Array<out String>?)=0
 override fun update(uri:Uri,v:ContentValues?,s:String?,args:Array<out String>?)=0
}
