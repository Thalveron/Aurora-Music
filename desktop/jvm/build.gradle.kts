plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    kotlin("plugin.compose") version "2.3.21"
    id("org.jetbrains.compose") version "1.10.3"
    id("org.openjfx.javafxplugin") version "0.1.0"
}

kotlin {
    jvmToolchain(21)
    sourceSets {
        main {
            kotlin.srcDir("../../app/src/main/java")
            kotlin.include(
                "com/craftworks/music/data/model/CommonLyrics.kt",
                "com/craftworks/music/data/model/LyricTextQuality.kt",
                "com/craftworks/music/data/model/MediaData.kt",
                "com/craftworks/music/data/model/SongModels.kt",
                "com/craftworks/music/data/MediaNavidromeProvider.kt",
                "com/craftworks/music/player/DesktopLyricProgress.kt",
                "com/craftworks/music/providers/navidrome/SubsonicAuth.kt",
                "com/craftworks/music/providers/navidrome/LyricsModels.kt",
                "com/craftworks/music/providers/navidrome/SearchResult3.kt",
                "com/craftworks/music/providers/navidrome/SubsonicModels.kt",
                "com/craftworks/music/desktop/**"
            )
        }
        test {
            kotlin.srcDir("../../app/src/test/java")
            kotlin.include(
                "com/craftworks/music/data/model/LocalLrcTest.kt",
                "com/craftworks/music/desktop/**"
            )
        }
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("net.java.dev.jna:jna-platform:5.18.1")
    testImplementation("junit:junit:4.13.2")
}

val prepareDistributionResources by tasks.registering(Sync::class) {
    from("src/main/app-resources")
    into(layout.buildDirectory.dir("distribution-resources"))
    into("common/licenses/aurora") {
        from("../../LICENSE", "../../NOTICE")
    }
}

javafx {
    version = "21.0.8"
    modules = listOf("javafx.media", "javafx.swing")
}

compose.desktop {
    application {
        mainClass = "com.craftworks.music.desktop.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe)
            packageName = "AuroraMusic"
            packageVersion = "1.33.0"
            appResourcesRootDir.set(layout.buildDirectory.dir("distribution-resources"))
            modules("java.net.http", "java.prefs", "jdk.httpserver", "jdk.unsupported", "jdk.crypto.ec")
            windows {
                menuGroup = "Aurora Music"
                shortcut = true
            }

            tasks.matching { it.name == "prepareAppResources" }.configureEach {
                dependsOn(prepareDistributionResources)
            }
        }
    }
}
