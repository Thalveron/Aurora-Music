package com.craftworks.music.updates;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import com.craftworks.music.R;
import org.json.*;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;

/** Independent, opt-out update checks. Never installs without the system confirmation UI. */
public final class MusicUpdates {
 private static final ExecutorService worker=Executors.newSingleThreadExecutor();
 private static final Handler main=new Handler(Looper.getMainLooper());
 private static WeakReference<Activity> foreground=new WeakReference<>(null);
 private static volatile boolean busy;
 private static volatile int message=R.string.music_update_idle,progress=-1;
 private static volatile String detail="",route="";
 private MusicUpdates(){}
 static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("aurora_music_updates",0);}
 static File directory(Context c){File dir=new File(c.getFilesDir(),"music-updates");if(!dir.isDirectory()&&!dir.mkdirs())throw new IllegalStateException("STORAGE");return dir;}
 public static long code(Context c){try{PackageInfo p=c.getPackageManager().getPackageInfo(c.getPackageName(),0);return Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode;}catch(Exception e){throw new IllegalStateException(e);}}
 public static String version(Context c){try{return c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;}catch(Exception e){return "?";}}
 public static boolean autoCheck(Context c){return prefs(c).getBoolean("autoCheck",true);}
 public static boolean autoDownload(Context c){return prefs(c).getBoolean("autoDownload",false);}
 public static void setAutoCheck(Context c,boolean value){if(!prefs(c).edit().putBoolean("autoCheck",value).commit())throw new IllegalStateException("STORAGE");}
 public static void setAutoDownload(Context c,boolean value){if(!prefs(c).edit().putBoolean("autoDownload",value).commit())throw new IllegalStateException("STORAGE");}
 public static boolean busy(){return busy;}
 public static JSONObject latest(Context c){try{return new JSONObject(prefs(c).getString("latest","{}"));}catch(Exception e){return new JSONObject();}}
 public static String status(Context c){return c.getString(message)+(progress>=0?" "+progress+"%":"")+(detail.isEmpty()?"":"\n"+detail)+(route.isEmpty()?"":"\n"+c.getString(R.string.music_update_route)+" "+route);}
 public static long lastChecked(Context c){return prefs(c).getLong("lastChecked",0);}
 public static boolean hasNew(Context c){return latest(c).optLong("versionCode")>code(c);}
 public static boolean hasPending(Context c){
  long target=prefs(c).getLong("pendingCode",0);
  if(target<=code(c)){if(target>0){prefs(c).edit().remove("pendingCode").remove("pendingRelease").apply();new File(directory(c),"update.apk").delete();}return false;}
  return new File(directory(c),"update.apk").isFile();
 }
 public static void onForeground(Activity a){foreground=new WeakReference<>(a);Context c=a.getApplicationContext();main.postDelayed(()->{if(foreground.get()!=a||a.isFinishing()||!autoCheck(c))return;long elapsed=System.currentTimeMillis()-prefs(c).getLong("lastAttempt",0);if(elapsed<0||elapsed>=24*60*60*1000L)request(c,false,true);},2500);}
 public static void onBackground(Activity a){if(foreground.get()==a)foreground.clear();}
 public static void check(Context c){request(c,false,false);}
 public static void download(Context c){request(c,true,false);}
 private static synchronized void request(Context context,boolean download,boolean automatic){
  if(busy)return;busy=true;message=R.string.music_update_checking;detail="";progress=-1;route="";Context c=context.getApplicationContext();
  worker.execute(()->{try{
   if(automatic&&!autoCheck(c))return;
   if(download){JSONObject release=latest(c);if(release.optLong("versionCode")<=code(c))throw new IOException("NO_UPDATE");downloadNow(c,release,false);}
   else{prefs(c).edit().putLong("lastAttempt",System.currentTimeMillis()).commit();JSONObject release=checkNow(c);boolean newer=release.getLong("versionCode")>code(c);
    message=newer?R.string.music_update_found:R.string.music_update_current;
    if(newer&&automatic&&autoCheck(c)){if(autoDownload(c))downloadNow(c,release,true);offer(c);}
   }
  }catch(Exception error){message=R.string.music_update_failed;detail=errorText(c,error);progress=-1;}finally{busy=false;}});
 }
 private static String errorText(Context c,Exception e){String key=String.valueOf(e.getMessage());if(key.equals("SIGNATURE"))return c.getString(R.string.music_update_signature_error);if(key.equals("VERIFY")||key.equals("APK")||key.equals("METADATA")||key.equals("VERSION"))return c.getString(R.string.music_update_verify_error);if(key.equals("STORAGE"))return c.getString(R.string.music_update_storage_error);if(key.equals("NO_UPDATE"))return c.getString(R.string.music_update_current);return c.getString(R.string.music_update_network_error);}
 private static void offer(Context c){main.post(()->{Activity a=foreground.get();if(a==null||a.isFinishing()||a.isDestroyed()||!autoCheck(c))return;new AlertDialog.Builder(a).setTitle(R.string.music_update_title).setMessage(c.getString(hasPending(c)?R.string.music_update_ready:R.string.music_update_found)+" · "+latest(c).optString("version")).setNegativeButton(R.string.music_update_later,null).setPositiveButton(R.string.music_update_open,(d,w)->a.startActivity(new Intent(a,MusicUpdateActivity.class))).show();});}
 private static final class Candidate {String url;JSONObject release;long nanos;Candidate(String u,JSONObject r,long n){url=u;release=r;nanos=n;}}
 private static JSONObject parse(byte[] body)throws Exception{
  JSONObject root=new JSONObject(new String(body,java.nio.charset.StandardCharsets.UTF_8));if(root.optInt("schema")!=1)throw new IOException("METADATA");JSONObject release=root.getJSONObject("latest");
  if(!MusicUpdateRules.PACKAGE.equals(release.getString("package")))throw new IOException("APK");
  long sdk=integer(release,"minSdk");if(sdk<23||sdk>1000)throw new IOException("METADATA");
  MusicUpdateRules.validate(release.getString("version"),integer(release,"versionCode"),integer(release,"bytes"),release.getString("sha256"),release.getString("url"),(int)sdk);return release;
 }
 private static long integer(JSONObject object,String key)throws Exception{Object v=object.get(key);if(!(v instanceof Number)||((Number)v).doubleValue()!=((Number)v).longValue())throw new IOException("METADATA");return ((Number)v).longValue();}
 private static JSONObject checkNow(Context c)throws Exception{
  String direct=MusicUpdateRules.DIRECT_MANIFEST;ExecutorService racers=Executors.newFixedThreadPool(2);List<Candidate> choices=new ArrayList<>();
  try{List<Callable<Candidate>> tasks=new ArrayList<>();for(String url:new String[]{direct,MusicUpdateRules.PROXY+direct})tasks.add(()->{long start=System.nanoTime();return new Candidate(url,parse(read(url,1024*1024,null)),System.nanoTime()-start);});
   for(Future<Candidate> task:racers.invokeAll(tasks,18,TimeUnit.SECONDS))try{choices.add(task.get());}catch(Exception ignored){}
  }finally{racers.shutdownNow();}
  if(choices.isEmpty())throw new IOException("NETWORK");long highest=0;for(Candidate x:choices)highest=Math.max(highest,x.release.getLong("versionCode"));final long bestCode=highest;choices.removeIf(x->x.release.optLong("versionCode")!=bestCode);choices.sort(Comparator.comparingLong(x->x.nanos));
  JSONObject selected=choices.get(0).release;for(Candidate x:choices)if(!selected.getString("sha256").equalsIgnoreCase(x.release.getString("sha256"))||selected.getLong("bytes")!=x.release.getLong("bytes"))throw new IOException("METADATA");
  if(selected.getInt("minSdk")>Build.VERSION.SDK_INT)throw new IOException("APK");
  if(!prefs(c).edit().putString("latest",selected.toString()).putLong("lastChecked",System.currentTimeMillis()).commit())throw new IOException("STORAGE");return selected;
 }
 private static List<String> routes(String direct)throws Exception{
  ExecutorService racers=Executors.newFixedThreadPool(2);List<Candidate> choices=new ArrayList<>();
  try{List<Callable<Candidate>> tasks=new ArrayList<>();for(String url:new String[]{direct,MusicUpdateRules.PROXY+direct})tasks.add(()->{long start=System.nanoTime();probe(url);return new Candidate(url,null,System.nanoTime()-start);});for(Future<Candidate> task:racers.invokeAll(tasks,18,TimeUnit.SECONDS))try{choices.add(task.get());}catch(Exception ignored){}
  }finally{racers.shutdownNow();}
  choices.sort(Comparator.comparingLong(x->x.nanos));List<String> result=new ArrayList<>();for(Candidate x:choices)result.add(x.url);for(String url:new String[]{direct,MusicUpdateRules.PROXY+direct})if(!result.contains(url))result.add(url);return result;
 }
 private static void downloadNow(Context c,JSONObject release,boolean automatic)throws Exception{
  message=R.string.music_update_testing;progress=-1;File target=new File(directory(c),"update.apk"),partial=new File(directory(c),"partial.apk");long expected=release.getLong("bytes");
  if(directory(c).getUsableSpace()<expected+32L*1024*1024)throw new IOException("STORAGE");
  if(hasPending(c)&&prefs(c).getLong("pendingCode",0)==release.getLong("versionCode")){try{verify(c,target,release);message=R.string.music_update_ready;return;}catch(Exception stale){prefs(c).edit().remove("pendingCode").remove("pendingRelease").commit();}}
  Exception failure=null;
  for(String url:routes(release.getString("url"))){
   HttpsURLConnection connection=null;
   try{if(automatic&&(!autoCheck(c)||!autoDownload(c)))return;message=R.string.music_update_downloading;progress=0;route=c.getString(url.startsWith(MusicUpdateRules.PROXY)?R.string.music_update_proxy:R.string.music_update_direct);connection=open(url,null,20000);long size=-1;try{size=Long.parseLong(connection.getHeaderField("Content-Length"));}catch(Exception ignored){}if(size>expected)throw new IOException("VERIFY");long count=0;
    try(InputStream in=connection.getInputStream();OutputStream out=new FileOutputStream(partial)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1){if(automatic&&(!autoCheck(c)||!autoDownload(c)))throw new IOException("CANCELLED");count+=n;if(count>expected)throw new IOException("VERIFY");out.write(buffer,0,n);progress=(int)(count*100/expected);}}
    verify(c,partial,release);prefs(c).edit().remove("pendingCode").remove("pendingRelease").commit();if(target.exists()&&!target.delete())throw new IOException("STORAGE");if(!partial.renameTo(target))throw new IOException("STORAGE");
    if(!prefs(c).edit().putLong("pendingCode",release.getLong("versionCode")).putString("pendingRelease",release.toString()).commit())throw new IOException("STORAGE");message=R.string.music_update_ready;progress=-1;return;
   }catch(Exception e){failure=e;partial.delete();}finally{if(connection!=null)connection.disconnect();}
  }
  throw failure==null?new IOException("NETWORK"):failure;
 }
 static File verifiedPending(Context c)throws Exception{File file=new File(directory(c),"update.apk");JSONObject release=new JSONObject(prefs(c).getString("pendingRelease","{}"));verify(c,file,release);return file;}
 @SuppressWarnings("deprecation") private static void verify(Context c,File file,JSONObject release)throws Exception{
  if(!file.isFile()||file.length()!=release.getLong("bytes"))throw new IOException("VERIFY");
  MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);}StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",b&255));if(!hex.toString().equalsIgnoreCase(release.getString("sha256")))throw new IOException("VERIFY");
  PackageManager pm=c.getPackageManager();int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;PackageInfo installed=pm.getPackageInfo(c.getPackageName(),flags),incoming=pm.getPackageArchiveInfo(file.getPath(),flags);
  if(incoming==null||!c.getPackageName().equals(incoming.packageName))throw new IOException("APK");long candidate=Build.VERSION.SDK_INT>=28?incoming.getLongVersionCode():incoming.versionCode;
  if(candidate<=code(c)||candidate!=release.getLong("versionCode")||!release.getString("version").equals(incoming.versionName)||incoming.applicationInfo==null||incoming.applicationInfo.minSdkVersion>Build.VERSION.SDK_INT)throw new IOException("APK");
  Signature[] oldSignatures=Build.VERSION.SDK_INT>=28?installed.signingInfo.getApkContentsSigners():installed.signatures,newSignatures=Build.VERSION.SDK_INT>=28?incoming.signingInfo.getApkContentsSigners():incoming.signatures;
  if(oldSignatures==null||newSignatures==null||oldSignatures.length!=newSignatures.length)throw new IOException("SIGNATURE");Set<Signature> set=new HashSet<>(Arrays.asList(oldSignatures));if(!set.equals(new HashSet<>(Arrays.asList(newSignatures))))throw new IOException("SIGNATURE");
 }
 private static HttpsURLConnection open(String url,String range,int timeout)throws Exception{
  for(int redirect=0;redirect<7;redirect++){
   if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("CANCELLED");
   if(!MusicUpdateRules.trusted(url))throw new IOException("METADATA");HttpsURLConnection c=(HttpsURLConnection)new URL(url).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(6000);c.setReadTimeout(timeout);c.setRequestProperty("User-Agent","Aurora-Music-Updates");c.setRequestProperty("Accept-Encoding","identity");if(range!=null)c.setRequestProperty("Range",range);
   try{int status=c.getResponseCode();if(status>=300&&status<400){String next=c.getHeaderField("Location");c.disconnect();if(next==null)throw new IOException("NETWORK");url=new URL(new URL(url),next).toString();continue;}if(status!=200&&status!=206)throw new IOException("NETWORK");return c;}catch(Exception failure){c.disconnect();throw failure;}
  }throw new IOException("NETWORK");
 }
 private static byte[] read(String url,int max,String range)throws Exception{HttpsURLConnection c=open(url,range,8000);try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>max)throw new IOException("METADATA");out.write(buffer,0,n);}return out.toByteArray();}finally{c.disconnect();}}
 private static void probe(String url)throws Exception{HttpsURLConnection c=open(url,"bytes=0-65535",5000);try(InputStream in=c.getInputStream()){byte[] b=new byte[8192];int count=0,n;while(count<65536&&(n=in.read(b,0,Math.min(b.length,65536-count)))!=-1)count+=n;if(count<4)throw new IOException("NETWORK");}finally{c.disconnect();}}
}
