# Aurora Music

Aurora is an Android music player for TV and mobile devices, based on [Chora](https://github.com/CraftWorksMC/Chora).

This repository contains the Aurora music app source and APK releases. Desktop launcher updates are managed in a separate project.

## Download

[Latest APK release](https://github.com/Thalveron/Aurora-Music/releases/latest)

The first build with online updating must be installed manually. Future versions can be checked and downloaded from **Settings → Aurora Music updates**. Installation is confirmed through the Android system installer and keeps existing settings.

## Features

- Navidrome / Subsonic music sources.
- Local and USB music, local tags, artwork and LRC lyrics.
- TV remote navigation, playback resumption and media-session integration.
- Desktop playback and lyric integration where supported.
- Independent update feed, direct and accelerated HTTPS routes, route testing and fallback.
- APK integrity and signing-certificate checks before installation.
- Optional automatic checks and automatic download; no unattended installation.

Music providers, accounts and USB folders are configured on each device. No private server accounts or personal device configuration are bundled in the app.

## Build

Use JDK 21 and the Android SDK version declared by `app/build.gradle.kts`:

```sh
./gradlew :app:assembleRelease :app:testDebugUnitTest
```

Every push is also built by GitHub Actions (`.github/workflows/build.yml`), which runs the unit tests, uploads the APK as an artifact, and walks the TV first-run setup on an emulator with `tools/tv_smoke.sh`. That APK is signed with a throwaway key and is for testing only.

To update an existing installation, keep the original application ID and signing key. Signing keys are not included in this repository. Builds made with a different signing key cannot replace official APKs without uninstalling them first.

## Online updates

See [MUSIC-UPDATES.md](MUSIC-UPDATES.md) for the user flow, route selection, verification rules, update manifest and publishing process.

## License and attribution

Based on Chora by CraftWorksMC and contributors. Original source, copyright notices and Apache-2.0 licensing are retained. Aurora modifications include TV customization, local/USB support, lyric matching and an independent update system. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
