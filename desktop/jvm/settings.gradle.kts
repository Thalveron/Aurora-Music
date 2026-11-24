pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        exclusiveContent {
            forRepository {
                ivy {
                    url = uri("https://download.videolan.org/pub/videolan/vlc/")
                    patternLayout { artifact("[revision]/win64/[artifact]-[revision]-win64.[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("org.videolan", "vlc") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "aurora-music-desktop"
