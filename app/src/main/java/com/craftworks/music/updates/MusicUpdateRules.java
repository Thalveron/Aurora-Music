package com.craftworks.music.updates;

import java.net.URI;
import java.util.Locale;

/** No device state or Android dependencies: shared update trust and version rules. */
public final class MusicUpdateRules {
 public static final String REPOSITORY="Thalveron/Aurora-Music";
 public static final String PACKAGE="com.craftworks.music.tvcustom";
 public static final String DIRECT_MANIFEST="https://raw.githubusercontent.com/"+REPOSITORY+"/main/updates/latest.json";
 public static final String PROXY="https://gh-proxy.com/";
 public static final long MAX_BYTES=256L*1024*1024;
 private MusicUpdateRules(){}
 public static String apkUrl(String version){if(!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+"))throw new IllegalArgumentException("VERSION");return "https://github.com/"+REPOSITORY+"/releases/download/v"+version+"/Aurora-Music-v"+version+".apk";}
 public static void validate(String version,long code,long bytes,String sha,String url,int minSdk){
  if(code<=0||bytes<1024||bytes>MAX_BYTES||minSdk<23||minSdk>1000||!sha.matches("[a-fA-F0-9]{64}")||!apkUrl(version).equals(url))throw new IllegalArgumentException("METADATA");
 }
 public static boolean trusted(String value){
  try{URI u=new URI(value);if(!"https".equalsIgnoreCase(u.getScheme())||u.getRawUserInfo()!=null||u.getPort()!=-1&&u.getPort()!=443||u.getRawFragment()!=null)return false;
   String host=u.getHost();if(host==null)return false;host=host.toLowerCase(Locale.ROOT);
   if(host.equals("gh-proxy.com"))return value.startsWith(PROXY+"https://github.com/"+REPOSITORY+"/releases/download/")||value.startsWith(PROXY+DIRECT_MANIFEST);
   return host.equals("github.com")||host.equals("raw.githubusercontent.com")||host.equals("release-assets.githubusercontent.com")||host.equals("objects.githubusercontent.com")||host.equals("github-releases.githubusercontent.com");
  }catch(Exception e){return false;}
 }
}
