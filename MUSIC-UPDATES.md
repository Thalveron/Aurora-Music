# Aurora Music updates

Aurora Music uses its own repository, `Thalveron/Aurora-Music`. It does not read or modify Aurora Launcher update metadata.

## User flow

- Open the music app settings and choose **Aurora Music updates**.
- Automatic checks are enabled by default and run at most once per day when opening the music app. No boot receiver or continuously running update service is added.
- Automatic download is a separate option, off by default. Turning off automatic checks also stops new automatic downloads.
- Manual flow: **Check for updates → Download new version → Install downloaded update → system installation confirmation**.
- If Android asks for permission to install updates from this app, allow it and return. The downloaded APK is retained through permission prompts and cancelled installation.
- The update keeps the same package and signing certificate. Account details, music sources, USB music and application settings are retained. This is not a clean reinstall.
- The first version with this feature must be installed manually. Older music APKs cannot discover this new updater by themselves.

## Sources and validation

Manifest sources:

1. `https://raw.githubusercontent.com/Thalveron/Aurora-Music/main/updates/latest.json`
2. `https://gh-proxy.com/https://raw.githubusercontent.com/Thalveron/Aurora-Music/main/updates/latest.json`

Both sources are checked concurrently. The newest valid version is selected; conflicting metadata for the same version is rejected. Actual APK routes are tested with a bounded range request and tried from fastest to slowest, with fallback on failure. Accelerated downloads prepend `https://gh-proxy.com/` to the complete GitHub release URL.

Only HTTPS and the specified GitHub/asset/proxy hosts are accepted. Update metadata is bound to this repository and package. Before installation the app verifies the full file size, SHA-256, package, version, Android compatibility and equality with the installed signing certificate. Older versions and mismatched APKs are rejected. Verification is repeated before opening the system installer.

Downloads are stored in the app private `music-updates/` directory. A non-exported FileProvider grants temporary read access to the installer. Credentials, server settings and library databases are not included in updates or sent to GitHub.

## Release format

`updates/latest.json` uses schema 1:

```json
{
  "schema": 1,
  "latest": {
    "package": "com.craftworks.music.tvcustom",
    "version": "1.32.0",
    "versionCode": 316,
    "minSdk": 23,
    "url": "https://github.com/Thalveron/Aurora-Music/releases/download/v1.32.0/Aurora-Music-v1.32.0.apk",
    "sha256": "<64 hexadecimal characters from the final APK>",
    "bytes": 0,
    "notes": "Generic release notes"
  },
  "history": []
}
```

`bytes` and `sha256` must be calculated from the final signed APK. The example placeholders above are not installable metadata.

For future releases, increase both `versionName` and `versionCode`, sign with the existing private signing key and run `tools/publish_release.py`. It uploads the APK first, then updates the manifest. All clients share one release per version: if the tag already exists (for example with the Windows installer), the APK and its `.sha256` file are added to it; an APK that is already there is never replaced. Signing keys and user data must never be committed.

## Signing key and the 1.33.0 reinstall

From 1.33.0 the APK is signed with a new, dedicated release key. Android refuses to install it over 1.32.0, which used a different certificate, and the 1.32.0 updater will report a signature error for it. Uninstall 1.32.0 once and install 1.33.0; music sources have to be added again. Later versions update normally. Aurora Launcher's signature-protected lyric bridge only works if the launcher is signed with the same key.

The key lives only in repository secrets, used by `.github/workflows/release.yml`:

| Secret | Content |
| --- | --- |
| `AURORA_KEYSTORE_BASE64` | `base64 -w0 aurora-release.jks` |
| `AURORA_KEYSTORE_PASSWORD` | keystore password |
| `AURORA_KEY_ALIAS` | key alias, for example `aurora` |
| `AURORA_KEY_PASSWORD` | key password |

Create the key once and keep an offline copy; if it is lost, every device has to reinstall again:

```sh
keytool -genkeypair -v -keystore aurora-release.jks -alias aurora -keyalg RSA -keysize 4096 -validity 10950
```

To release: merge to `main`, then run **Actions → Release Android → Run workflow** on `main`.
