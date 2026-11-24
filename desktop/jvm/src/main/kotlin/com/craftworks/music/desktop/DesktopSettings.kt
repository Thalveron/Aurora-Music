package com.craftworks.music.desktop

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Crypt32Util
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.security.MessageDigest
import java.util.HexFormat
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
    var accelerated: Boolean
        get() = preferences.getBoolean("accelerated", false)
        set(value) { preferences.putBoolean("accelerated", value) }
    var scrobblePercent: Int
        get() = preferences.getInt("scrobblePercent", 0)
        set(value) { preferences.putInt("scrobblePercent", value) }
    var maxBitRate: Int
        get() = preferences.getInt("maxBitRate", 0)
        set(value) { preferences.putInt("maxBitRate", value) }

    fun flush() {
        try {
            preferences.flush()
        } catch (_: BackingStoreException) {
            throw IOException("Unable to save desktop settings")
        }
    }

    fun server(): SavedServer? {
        val saved = preferences.get("server", null) ?: return null
        return decode(saved)
    }

    private fun decode(saved: String): SavedServer {
        return try {
            Json.decodeFromString<SavedServer>(saved)
        } catch (_: IllegalArgumentException) {
            throw IOException("Saved server settings are invalid")
        }
    }

    private val profiles get() = preferences.node("profiles")
    private fun key(server: SavedServer): String {
        val identity = server.address.trim().trimEnd('/') + "\u0000" + server.username
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()))
    }

    fun servers(): List<SavedServer> = try {
        (listOfNotNull(server()) + profiles.keys().mapNotNull { id -> profiles.get(id, null)?.let(::decode) }).distinctBy(::key)
    } catch (_: BackingStoreException) {
        throw IOException("Could not read saved servers")
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
        val profile = SavedServer(address, username, encrypted, allowHttp)
        server()?.let { profiles.put(key(it), Json.encodeToString(it)) }
        profiles.put(key(profile), Json.encodeToString(profile))
        preferences.put("server", Json.encodeToString(profile))
        try {
            preferences.flush()
        } catch (_: BackingStoreException) {
            throw IOException("Unable to save server settings")
        }
    }

    fun removeServer(profile: SavedServer? = server()) {
        if (profile == null) return
        profiles.remove(key(profile))
        if (server()?.let(::key) == key(profile)) preferences.remove("server")
        try {
            preferences.flush()
        } catch (_: BackingStoreException) {
            throw IOException("Unable to remove server settings")
        }
    }
}
