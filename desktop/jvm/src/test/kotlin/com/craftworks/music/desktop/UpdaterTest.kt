package com.craftworks.music.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.security.MessageDigest

class UpdaterTest {
    @get:Rule val folder = TemporaryFolder()
    private val digest = "a".repeat(64)

    private fun release(version: String, url: String, digestValue: String = "sha256:$digest", draft: Boolean = false): String =
        """{"tag_name":"v$version","draft":$draft,"prerelease":true,"assets":[{"name":"Aurora-Music-v$version-windows-amd64.exe","browser_download_url":"$url","size":100,"digest":"$digestValue"}]}"""

    private fun url(version: String) =
        "https://github.com/Thalveron/Aurora-Music/releases/download/v$version/Aurora-Music-v$version-windows-amd64.exe"

    @Test
    fun picksNewestVerifiedWindowsAssetIncludingPrereleases() {
        val data = "[${release("1.34.0", url("1.34.0"))},${release("1.35.0", url("1.35.0"))},${release("1.36.0", url("1.36.0"), draft = true)}]"
        assertEquals("1.35.0", selectUpdate(data, "1.33.0")?.version)
        assertNull(selectUpdate(data, "1.35.0"))
    }

    @Test
    fun rejectsUntrustedUrlsAndMissingDigests() {
        val data = "[${release("1.34.0", "https://example.test/installer.exe")},${release("1.35.0", url("1.35.0"), digestValue = "")}]"
        assertNull(selectUpdate(data, "1.33.0"))
    }

    @Test
    fun verifiesDownloadAndNeverReplacesExistingFile() {
        val content = "test update".toByteArray()
        val hash = MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }
        val asset = ReleaseAsset("Aurora-Music-v1.34.0-windows-amd64.exe", url("1.34.0"), content.size.toLong(), "sha256:$hash")
        val directory = folder.root.toPath()
        val saved = saveVerifiedUpdate(content.inputStream(), asset, directory)
        assertTrue(Files.readAllBytes(saved).contentEquals(content))
        assertTrue(runCatching { saveVerifiedUpdate(content.inputStream(), asset, directory) }.isFailure)
        assertTrue(Files.readAllBytes(saved).contentEquals(content))
        Files.delete(saved)
        assertTrue(runCatching { saveVerifiedUpdate("wrong bytes".byteInputStream(), asset, directory) }.isFailure)
        assertTrue(Files.list(directory).use { it.count() } == 0L)
    }
}
