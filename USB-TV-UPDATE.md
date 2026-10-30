# Chora TV USB update
This source tree extends the existing Chora TV customization (Apache-2.0; original LICENSE retained).
Current version: 1.31.1-TV2-USB / 312.
USB implementation: app/src/main/java/com/craftworks/music/providers/local/
Parser regressions: app/src/test/java/com/craftworks/music/data/model/LocalLrcTest.kt

Use the existing JDK 21 / Android SDK 37 / Gradle wrapper setup. Build:
./gradlew assembleRelease testReleaseUnitTest --tests com.craftworks.music.data.model.LocalLrcTest
The local deployment signs with the same existing debug certificate as the launcher, required for
the signature-protected desktop/settings bridge. Do not commit signing keys or local.properties.

Features and real-TV acceptance/limitations are documented in the companion Aurora repo:
docs/aurora/studio-usb.md. No USB files are modified by indexing or online metadata caches.
