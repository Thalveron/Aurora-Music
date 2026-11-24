package com.craftworks.music.desktop

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Crypt32Util
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.Base64
import java.util.prefs.BackingStoreException
import java.util.prefs.Preferences

@Serializable
internal data class SavedServer(
    val address: String,
    val username: String,
    val encryptedPassword: String,
    val allowHttp: Boolean,
)

internal class DesktopSettings(
    private val preferences: Preferences = Preferences.userRoot().node("com/craftworks/auroramusic"),
) {
    var language: String
        get() = preferences.get("language", "zh")
        set(value) { preferences.put("language", value) }
    var lyricColor: Int
        get() = preferences.getInt("lyricColor", 0xffffffff.toInt())
        set(value) { preferences.putInt("lyricColor", value) }
    var background: String
        get() = preferences.get("background", "")
        set(value) { preferences.put("background", value) }
    var overlay: Boolean
        get() = preferences.getBoolean("overlay", false)
        set(value) { preferences.putBoolean("overlay", value) }

    fun flush() {
        try {
            preferences.flush()
        } catch (_: BackingStoreException) {
            throw IOException("Unable to save desktop settings")
        }
    }

    fun server(): SavedServer? {
        val saved = preferences.get("server", null) ?: return null
        return try {
            Json.decodeFromString<SavedServer>(saved)
        } catch (_: IllegalArgumentException) {
            throw IOException("Saved server settings are invalid")
        }
    }

    fun password(server: SavedServer): String {
        if (!Platform.isWindows()) throw IOException("Saved credentials require Windows")
        return try {
            String(Crypt32Util.cryptUnprotectData(Base64.getDecoder().decode(server.encryptedPassword), 1), Charsets.UTF_8)
        } catch (_: RuntimeException) {
            throw IOException("Unable to decrypt saved credentials for this Windows user")
        }
    }

    fun saveServer(address: String, username: String, password: String, allowHttp: Boolean) {
        if (!Platform.isWindows()) throw IOException("Saving server credentials is available on Windows only")
        val encrypted = try {
            Base64.getEncoder().encodeToString(Crypt32Util.cryptProtectData(password.toByteArray(Charsets.UTF_8), 1))
        } catch (_: RuntimeException) {
            throw IOException("Unable to protect server credentials")
        }
        preferences.put("server", Json.encodeToString(SavedServer(address, username, encrypted, allowHttp)))
        try {
            preferences.flush()
        } catch (_: BackingStoreException) {
            throw IOException("Unable to save server settings")
        }
    }

    fun removeServer() {
        preferences.remove("server")
        try {
            preferences.flush()
        } catch (_: BackingStoreException) {
            throw IOException("Unable to remove server settings")
        }
    }
}
