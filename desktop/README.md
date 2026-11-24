# Aurora Music for Windows

The Windows app is under development. The Kotlin/JVM project in `jvm/` directly
reuses the Android app's Subsonic models, authentication, and lyric parsing.

The development app supports connecting to one Navidrome or compatible
Subsonic server, song search, album and playlist browsing, streaming, desktop lyrics, a local background,
Chinese and English, and update checks. It is not yet a full replacement for
the Android app. FLAC, OGG, Opus, WMA and ALAC playback requests MP3
transcoding from the server; supported formats depend on JavaFX and the
server. Windows testing, packaging and distribution-license review are
required before release.

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

The installer includes a Java runtime. It is unsigned unless separately
signed. `.github/workflows/windows-desktop.yml` can build a test artifact on a
Windows runner; that workflow does not publish a release.

Windows releases use a matching
`Aurora-Music-vX.Y.Z-windows-amd64.exe` asset with a GitHub SHA-256 digest.
Windows assets must not replace the Android APK or change its update metadata.
