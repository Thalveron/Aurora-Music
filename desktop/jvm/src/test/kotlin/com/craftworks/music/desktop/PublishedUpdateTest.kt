package com.craftworks.music.desktop

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PublishedUpdateTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun fetchesAndVerifiesPublishedInstallerOverBothRoutes() {
        assumeTrue(System.getenv("AURORA_PUBLISHED_UPDATE_TEST") == "1")
        val updater = Updater()
        val update = requireNotNull(updater.check("1.33.0"))
        assertEquals(DESKTOP_VERSION, update.version)
        for (accelerated in listOf(false, true)) {
            val folder = files.newFolder().toPath()
            val path = updater.download(update, accelerated, folder)
            assertEquals(update.asset.size, java.nio.file.Files.size(path))
            assertEquals(path, updater.download(update, accelerated, folder))
        }
    }
}
