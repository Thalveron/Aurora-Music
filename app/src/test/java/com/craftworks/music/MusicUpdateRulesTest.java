package com.craftworks.music;
import com.craftworks.music.updates.MusicUpdateRules;
import org.junit.Test;
import static org.junit.Assert.*;

public class MusicUpdateRulesTest {
 private final String sha=new String(new char[64]).replace('\0','a');
 @Test public void independentRepositoryAndTwoRoutes(){String direct=MusicUpdateRules.apkUrl("1.32.0");assertTrue(direct.contains("/Aurora-Music/"));assertFalse(direct.contains("Aurora-Launcher"));assertTrue(MusicUpdateRules.trusted(direct));assertTrue(MusicUpdateRules.trusted(MusicUpdateRules.PROXY+direct));assertTrue(MusicUpdateRules.trusted(MusicUpdateRules.DIRECT_MANIFEST));assertTrue(MusicUpdateRules.trusted(MusicUpdateRules.PROXY+MusicUpdateRules.DIRECT_MANIFEST));}
 @Test public void rejectsCredentialsPortsAndDowngradedTransport(){for(String s:new String[]{"http://github.com/Thalveron/Aurora-Music/a.apk","https://user:pass@github.com/a.apk","https://github.com:8443/a.apk","https://github.com.evil.invalid/a.apk","https://example.org/a.apk","https://gh-proxy.com/https://example.org/a.apk","https://github.com/a.apk#fragment"})assertFalse(s,MusicUpdateRules.trusted(s));}
 @Test public void acceptsOfficialReleaseAssetRedirects(){assertTrue(MusicUpdateRules.trusted("https://release-assets.githubusercontent.com/github-production-release-asset/file.apk?token=opaque"));assertTrue(MusicUpdateRules.trusted("https://objects.githubusercontent.com/file.apk"));}
 @Test public void bindsManifestToItsExactRelease(){MusicUpdateRules.validate("1.32.0",316,20000000,sha,MusicUpdateRules.apkUrl("1.32.0"),23);try{MusicUpdateRules.validate("1.32.0",316,20000000,sha,"https://github.com/another/repo/a.apk",23);fail();}catch(IllegalArgumentException expected){}}
 @Test public void rejectsInvalidSizeVersionAndHash(){for(long size:new long[]{0,1023,MusicUpdateRules.MAX_BYTES+1})try{MusicUpdateRules.validate("1.32.0",316,size,sha,MusicUpdateRules.apkUrl("1.32.0"),23);fail();}catch(IllegalArgumentException expected){}try{MusicUpdateRules.apkUrl("../../other");fail();}catch(IllegalArgumentException expected){}try{MusicUpdateRules.validate("1.32.0",316,100000,"invalid",MusicUpdateRules.apkUrl("1.32.0"),23);fail();}catch(IllegalArgumentException expected){}}
}
