# Aurora Music for Windows

The Windows app is under development. The Kotlin/JVM project in `jvm/` directly
reuses the Android app's Subsonic models, authentication, and lyric parsing.

The app and its Windows launcher are named **Aurora Music**. Their icons use
the Android app's `aurora_music_icon` artwork. Android TV screens are intentionally
not part of the Windows port.

| Area | Available in the development source |
| --- | --- |
| Server library | Save multiple Navidrome/Subsonic accounts, connect one at a time, search songs, browse artists, biographies, albums, favorites and radio. Album views include recent, newest, frequent and random. Individual endpoints depend on the server. |
| Server playlists and downloads | Create, rename and delete playlists; add songs and remove specific entries. Filter songs and albums by server music folder. Download original tracks or an album to a chosen directory without replacing existing files. |
| Local library | No login needed. Scan selected folders for FLAC, OGG, Opus, MP3, WAV, AIFF and M4A, search filename/folder-based metadata, manage local playlists, and load sibling `.lrc` lyrics. |
| Playback | Original audio only: Subsonic `stream` requests specify `format=raw`, with no bitrate cap or transcoding fallback. Local files and original streams are decoded directly by libVLC. Queue, seek, repeat, sleep timer and optional listening history reporting are supported. |
| Appearance | Chinese and English, local backgrounds, always-on-top desktop lyrics and lyric color picker. |

**Not release-ready:** embedded local tag/artwork indexing and additional Android lyric sources
are among the remaining differences. Windows installation, real-server playback,
desktop lyric behavior, update installation and distribution licensing need
sign-off. A successful build is not evidence that those user journeys work.

Server passwords are encrypted with Windows DPAPI for the current Windows user.
Plain HTTP requires explicit confirmation. Background images stay on the
device. Update metadata comes directly from GitHub; the optional acceleration
provider receives the public release download URL, not server credentials.
Downloads are size- and SHA-256-checked and are never run automatically.

## Development

With JDK 21, run from the repository root:

```sh
./gradlew -p desktop/jvm test
./gradlew -p desktop/jvm run
```

On Windows, with JDK 21 and WiX 3 installed:

```bat
gradlew.bat -p desktop/jvm test packageExe
```

The Windows installer includes a Java runtime and the native playback libraries
from the official VLC 3.0.24 Windows x64 archive. Playback does not create a
temporary WAV or encode another audio file. The Windows audio mixer and output
device still determine the hardware output format. macOS development playback
requires VLC 3 in `/Applications/VLC.app`.
The installer is unsigned unless separately
signed. `.github/workflows/windows-desktop.yml` builds and checks the launcher on a
Windows runner. Uploading an installer requires an explicit manual workflow input;
push builds do not provide downloads, and the workflow does not publish releases.

Windows releases use a matching
`Aurora-Music-vX.Y.Z-windows-amd64.exe` asset with a GitHub SHA-256 digest.
Windows assets must not replace the Android APK or change its update metadata.
