package com.craftworks.music.updates;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import com.craftworks.music.R;
import java.io.File;
import java.util.concurrent.Executors;

/** TV/phone shared update UI. Uploaded music and music-provider settings are never touched. */
public final class MusicUpdateActivity extends Activity {
 private static final int PERMISSION=41,INSTALL=42;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final java.util.concurrent.ExecutorService installerWorker=Executors.newSingleThreadExecutor();
 private TextView status,versions,lastCheck;private Button check,download,install;
 private boolean visible,waiting,installing;private String installResult="";
 private final Runnable refresh=new Runnable(){public void run(){if(!visible)return;render();main.postDelayed(this,750);}};
 public static void open(Context context){context.startActivity(new Intent(context,MusicUpdateActivity.class));}
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private TextView text(LinearLayout column,int resource,int size){TextView v=new TextView(this);v.setText(resource);v.setTextColor(Color.WHITE);v.setTextSize(size);v.setPadding(0,dp(8),0,dp(8));v.setFocusable(false);column.addView(v);return v;}
 private Button button(LinearLayout column,int label,Runnable action){Button b=new Button(this);b.setText(label);b.setTextSize(18);b.setAllCaps(false);b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{android.R.attr.state_focused},new int[]{}},new int[]{0xff8194a7,0xff081923,Color.WHITE}));b.setBackgroundTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_focused},new int[]{android.R.attr.state_pressed},new int[]{}},new int[]{0xff7cebc9,0xff7cebc9,0xff283b4b}));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));p.bottomMargin=dp(12);column.addView(b,p);b.setOnClickListener(v->action.run());return b;}
 @Override public void onCreate(Bundle state){super.onCreate(state);if(state!=null){waiting=state.getBoolean("waiting");installing=state.getBoolean("installing");installResult=state.getString("installResult","");}
  ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(0xff101923);LinearLayout column=new LinearLayout(this);column.setOrientation(1);column.setPadding(dp(36),dp(20),dp(36),dp(36));scroll.addView(column);setContentView(scroll);
  text(column,R.string.music_update_title,28);versions=text(column,R.string.music_update_idle,19);lastCheck=text(column,R.string.music_update_idle,15);status=text(column,R.string.music_update_idle,18);status.setMinHeight(dp(82));
  Switch automatic=new Switch(this);automatic.setText(R.string.music_update_auto_check);automatic.setTextColor(Color.WHITE);automatic.setTextSize(19);automatic.setChecked(MusicUpdates.autoCheck(this));automatic.setPadding(0,dp(10),0,dp(10));column.addView(automatic);automatic.setOnCheckedChangeListener((b,on)->{try{MusicUpdates.setAutoCheck(this,on);}catch(Exception e){toast(R.string.music_update_storage_error);}});
  Switch automaticDownload=new Switch(this);automaticDownload.setText(R.string.music_update_auto_download);automaticDownload.setTextColor(Color.WHITE);automaticDownload.setTextSize(19);automaticDownload.setChecked(MusicUpdates.autoDownload(this));automaticDownload.setEnabled(automatic.isChecked());automaticDownload.setPadding(0,dp(10),0,dp(10));column.addView(automaticDownload);automaticDownload.setOnCheckedChangeListener((b,on)->{try{MusicUpdates.setAutoDownload(this,on);}catch(Exception e){toast(R.string.music_update_storage_error);}});automatic.setOnCheckedChangeListener((b,on)->{try{MusicUpdates.setAutoCheck(this,on);automaticDownload.setEnabled(on);}catch(Exception e){toast(R.string.music_update_storage_error);}});
  text(column,R.string.music_update_auto_help,15);
  check=button(column,R.string.music_update_check,()->{installResult="";MusicUpdates.check(this);render();});
  download=button(column,R.string.music_update_download,()->{installResult="";MusicUpdates.download(this);render();});
  install=button(column,R.string.music_update_install,()->new AlertDialog.Builder(this).setTitle(R.string.music_update_install).setMessage(R.string.music_update_install_help).setNegativeButton(R.string.music_update_cancel,null).setPositiveButton(R.string.music_update_install,(d,w)->beginInstall()).show());
  text(column,R.string.music_update_route_help,15);button(column,R.string.music_update_back,this::finish);check.requestFocus();render();
 }
 @Override protected void onResume(){super.onResume();visible=true;main.removeCallbacks(refresh);main.post(refresh);if(waiting&&android.os.Build.VERSION.SDK_INT>=26&&getPackageManager().canRequestPackageInstalls()){waiting=false;beginInstall();}}
 @Override protected void onPause(){visible=false;main.removeCallbacks(refresh);super.onPause();}
 @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);installerWorker.shutdownNow();super.onDestroy();}
 @Override protected void onSaveInstanceState(Bundle out){out.putBoolean("waiting",waiting);out.putBoolean("installing",installing);out.putString("installResult",installResult);super.onSaveInstanceState(out);}
 private void toast(int resource){Toast.makeText(this,resource,Toast.LENGTH_LONG).show();}
 private void render(){if(status==null)return;status.setText(installResult.isEmpty()?MusicUpdates.status(this):installResult);String next=MusicUpdates.latest(this).optString("version","");versions.setText(getString(R.string.music_update_versions,MusicUpdates.version(this),next.isEmpty()?getString(R.string.music_update_unknown):next));long stamp=MusicUpdates.lastChecked(this);lastCheck.setText(getString(R.string.music_update_last_check,stamp==0?getString(R.string.music_update_never):java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(stamp))));boolean busy=MusicUpdates.busy()||installing,pending=MusicUpdates.hasPending(this);check.setEnabled(!busy);boolean canDownload=!busy&&MusicUpdates.hasNew(this),canInstall=!busy&&pending;if(download.hasFocus()&&!canDownload||install.hasFocus()&&!canInstall)check.requestFocus();download.setEnabled(canDownload);install.setEnabled(canInstall);}
 private void beginInstall(){if(installing||MusicUpdates.busy())return;
  if(Build.VERSION.SDK_INT>=26&&!getPackageManager().canRequestPackageInstalls()){
   Intent permission=new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName()));
   if(permission.resolveActivity(getPackageManager())!=null){try{waiting=true;startActivityForResult(permission,PERMISSION);return;}catch(RuntimeException ignored){waiting=false;}}
  }
  installing=true;installResult=getString(R.string.music_update_verifying);render();
  installerWorker.execute(()->{try{File apk=MusicUpdates.verifiedPending(getApplicationContext());main.post(()->{if(isFinishing()||isDestroyed()){installing=false;return;}try{Uri uri=FileProvider.getUriForFile(this,getPackageName()+".updates",apk);Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_RETURN_RESULT,true);intent.setClipData(ClipData.newRawUri("APK",uri));installResult=getString(R.string.music_update_confirm_on_tv);startActivityForResult(intent,INSTALL);}catch(Exception e){installing=false;installResult=getString(R.string.music_update_installer_unavailable);render();}});}catch(Exception error){main.post(()->{if(isFinishing()||isDestroyed())return;installing=false;installResult=getString(R.string.music_update_verify_error);render();});}});
 }
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==PERMISSION){boolean allowed=Build.VERSION.SDK_INT<26||getPackageManager().canRequestPackageInstalls();boolean resume=waiting;waiting=false;if(resume&&allowed)beginInstall();else installResult=getString(R.string.music_update_permission_needed);}else if(request==INSTALL){installing=false;int error=data==null?0:data.getIntExtra("android.intent.extra.INSTALL_RESULT",0);installResult=getString(result==RESULT_OK?R.string.music_update_installed:error<0?R.string.music_update_install_failed:R.string.music_update_install_returned)+(error<0?" ("+error+")":"");render();}}
}
