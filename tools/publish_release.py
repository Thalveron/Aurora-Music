#!/usr/bin/env python3
"""Publish Aurora Music APK and source; advance update metadata only after upload."""
import argparse,hashlib,json,os,re,shlex,subprocess,tempfile,shutil
from pathlib import Path
REPO='Thalveron/Aurora-Music'
def run(args,cwd=None):
    return subprocess.run([str(x) for x in args],cwd=cwd,check=True,capture_output=True,text=True).stdout.strip()
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--apk',required=True,type=Path);p.add_argument('--notes',required=True,type=Path);p.add_argument('--gh',default=shutil.which('gh'));a=p.parse_args()
    root=Path(run(['git','rev-parse','--show-toplevel']))
    if not a.gh:p.error('GitHub CLI is required')
    if run(['git','status','--porcelain'],root):p.error('Commit the intended public source first')
    sdk=Path(os.environ['ANDROID_HOME']);tools=sorted((sdk/'build-tools').glob('*'),key=lambda x:[int(n) for n in x.name.split('.')])[-1]
    apk=a.apk.resolve();notes=a.notes.resolve();run([tools/'apksigner','verify',apk]);badging=run([tools/'aapt','dump','badging',apk]);m=re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",badging)
    if not m or m[1]!='com.craftworks.music.tvcustom' or not re.fullmatch(r'\d+\.\d+\.\d+',m[3]):p.error('Unexpected APK identity')
    code=int(m[2]);version=m[3];tag='v'+version;path=root/'updates/latest.json';old=json.loads(path.read_text()) if path.exists() else {}
    if code<=old.get('latest',{}).get('versionCode',0):p.error('Increase versionCode before publishing')
    if subprocess.run([a.gh,'release','view',tag,'--repo',REPO],capture_output=True).returncode==0:p.error('Release already exists; do not replace its APK')
    digest=hashlib.sha256(apk.read_bytes()).hexdigest();name='Aurora-Music-v'+version+'.apk'
    with tempfile.TemporaryDirectory(prefix='aurora-music-release-') as temp:
        asset=Path(temp)/name;shutil.copy2(apk,asset);checksum=Path(temp)/'SHA256SUMS.txt';checksum.write_text(digest+'  '+name+'\n')
        run([a.gh,'release','create',tag,asset,checksum,'--repo',REPO,'--target',run(['git','rev-parse','HEAD'],root),'--title','Aurora Music '+tag,'--notes-file',notes],root)
    latest={'package':m[1],'version':version,'versionCode':code,'minSdk':23,'url':'https://github.com/'+REPO+'/releases/download/'+tag+'/'+name,'sha256':digest,'bytes':apk.stat().st_size,'notes':notes.read_text()[:12000]}
    history=([old['latest']] if old.get('latest') else [])+old.get('history',[]);path.parent.mkdir(exist_ok=True);path.write_text(json.dumps({'schema':1,'latest':latest,'history':history},ensure_ascii=False,indent=2)+'\n')
    run(['git','add','updates/latest.json'],root);run(['git','commit','-m','Publish update metadata for '+tag],root);run(['git','-c','credential.helper=','-c','credential.helper=!'+shlex.quote(str(a.gh))+' auth git-credential','push','origin','HEAD:main'],root)
    print('Published https://github.com/'+REPO+'/releases/tag/'+tag)
if __name__=='__main__':main()
