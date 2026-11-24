package com.craftworks.music.desktop

import com.sun.jna.Platform
import java.util.UUID
import java.util.prefs.Preferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test

class DesktopSettingsTest {
    @Test
    fun savesCredentialsEncryptedForCurrentWindowsUser() {
        assumeTrue(Platform.isWindows())
        val preferences = Preferences.userRoot().node("com/craftworks/auroramusic-test-${UUID.randomUUID()}")
        try {
            val settings = DesktopSettings(preferences)
            settings.saveServer("https://example.test", "tester", "fake-test-password", false)
            assertFalse(preferences.get("server", "").contains("fake-test-password"))
            assertEquals("fake-test-password", settings.password(settings.server()!!))
            // Simulate a profile created by the older single-server version.
            preferences.node("profiles").clear()
            settings.saveServer("https://second.example.test", "another", "another-fake-password", false)
            assertEquals(listOf("another", "tester"), settings.servers().map { it.username })
            settings.removeServer()
            assertEquals(null, settings.server())
            assertEquals("fake-test-password", settings.password(settings.servers().single()))
        } finally {
            val parent = preferences.parent()
            preferences.removeNode()
            parent.flush()
        }
    }
}
