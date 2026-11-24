import java.security.MessageDigest

plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    kotlin("plugin.compose") version "2.3.21"
    id("org.jetbrains.compose") version "1.10.3"
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

val vlcBinary by configurations.creating
dependencies { vlcBinary("org.videolan:vlc:3.0.24@zip") }
val prepareDistributionResources by tasks.registering(Sync::class) {
    from("src/main/app-resources")
    if (System.getProperty("os.name").startsWith("Windows")) from({
        val archive = vlcBinary.singleFile
        check(MessageDigest.getInstance("SHA-256").digest(archive.readBytes()).joinToString("") { "%02x".format(it) } ==
            "fcf30850371ad10c9373cc4f0f4501e7dee49e3e9ae9f20c72fb2661a1ca6323") { "Invalid VLC distribution digest" }
        zipTree(archive)
    }) {
        include("*/libvlc*.dll", "*/plugins/**", "*/COPYING.txt", "*/AUTHORS.txt", "*/THANKS.txt")
        exclude("**/plugins.dat")
        eachFile { path = "common/vlc/" + path.substringAfter('/') }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("distribution-resources"))
    into("common/licenses/aurora") {
        from("../../LICENSE", "../../NOTICE")
    }

}

tasks.withType<Test>().configureEach {
    testLogging { exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
    dependsOn(prepareDistributionResources)
    systemProperty("compose.application.resources.dir", layout.buildDirectory.dir("distribution-resources/common").get().asFile)
}

compose.desktop {
    application {
        mainClass = "com.craftworks.music.desktop.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe)
            packageName = "Aurora Music"
            packageVersion = "1.33.1"
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
