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

val decoderBinary by configurations.creating
val decoderPlatform = when {
    System.getProperty("os.name").startsWith("Windows") -> "win64"
    System.getProperty("os.name").startsWith("Mac") -> if (System.getProperty("os.arch") == "aarch64") "osxm1" else "osx64"
    else -> if (System.getProperty("os.arch") == "aarch64") "linux-arm64" else "linux64"
}
dependencies { decoderBinary("ws.schild:jave-nativebin-$decoderPlatform:4.2.0@jar") }
val prepareDistributionResources by tasks.registering(Sync::class) {
    from("src/main/app-resources")
    from({ zipTree(decoderBinary.singleFile) }) {
        include("**/ffmpeg-*")
        eachFile { path = "common/decoder/" + if (decoderPlatform == "win64") "ffmpeg.exe" else "ffmpeg" }
        includeEmptyDirs = false
        filePermissions { unix("755") }
    }
    into(layout.buildDirectory.dir("distribution-resources"))
    into("common/licenses/aurora") {
        from("../../LICENSE", "../../NOTICE")
    }

}

tasks.withType<Test>().configureEach {
    dependsOn(prepareDistributionResources)
    systemProperty("compose.application.resources.dir", layout.buildDirectory.dir("distribution-resources/common").get().asFile)
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
            packageName = "Aurora Music"
            packageVersion = "1.33.0"
            appResourcesRootDir.set(layout.buildDirectory.dir("distribution-resources"))
            modules("java.net.http", "java.prefs", "jdk.httpserver", "jdk.unsupported", "jdk.crypto.ec")
            windows {
                iconFile.set(project.file("branding/aurora-music.ico"))
                menuGroup = "Aurora Music"
                shortcut = true
            }

            tasks.matching { it.name == "prepareAppResources" }.configureEach {
                dependsOn(prepareDistributionResources)
            }
        }
    }
}
