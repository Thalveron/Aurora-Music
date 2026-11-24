package com.craftworks.music.desktop

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration

private const val REPOSITORY = "Thalveron/Aurora-Music"
private const val MAX_UPDATE_BYTES = 512L * 1024 * 1024
private val tagPattern = Regex("^v(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$")
private val digestPattern = Regex("^sha256:[0-9a-f]{64}$")

@Serializable
internal data class ReleaseAsset(
    val name: String,
    @SerialName("browser_download_url") val url: String,
    val size: Long,
    val digest: String? = null,
)

@Serializable
internal data class Release(
    @SerialName("tag_name") val tag: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val assets: List<ReleaseAsset>,
)

internal data class Update(val version: String, val asset: ReleaseAsset)

internal fun saveVerifiedUpdate(body: InputStream, asset: ReleaseAsset, directory: Path): Path {
    val temp = Files.createTempFile(directory, ".aurora-update-", ".tmp")
    try {
        val hash = MessageDigest.getInstance("SHA-256")
        var count = 0L
        Files.newOutputStream(temp).use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = body.read(buffer)
                if (read == -1) break
                count += read
                if (count > asset.size) throw IOException("Update exceeds expected size")
                hash.update(buffer, 0, read)
                output.write(buffer, 0, read)
            }
        }
        val digest = "sha256:" + hash.digest().joinToString("") { "%02x".format(it) }
        if (count != asset.size || digest != asset.digest) throw IOException("Update integrity verification failed")
        val destination = directory.resolve(asset.name)
        // Both paths are in the chosen directory; moving also works on exFAT
        // removable drives, where making a hard link is not supported.
        Files.move(temp, destination)
        return destination
    } finally {
        Files.deleteIfExists(temp)
    }
}

private fun version(tag: String): List<Long>? =
    tagPattern.matchEntire(tag)?.groupValues?.drop(1)?.map { it.toLongOrNull() ?: return null }

private fun newer(a: List<Long>, b: List<Long>): Boolean {
    for (i in a.indices) if (a[i] != b[i]) return a[i] > b[i]
    return false
}

internal fun selectUpdate(json: String, current: String): Update? {
    val currentVersion = version("v$current") ?: throw IOException("Invalid current version")
    val releases = try {
        Json { ignoreUnknownKeys = true }.decodeFromString<List<Release>>(json)
    } catch (_: IllegalArgumentException) {
        throw IOException("Invalid update metadata")
    }
    return releases.asSequence().filterNot { it.draft }.mapNotNull { release ->
        val parsed = version(release.tag) ?: return@mapNotNull null
        if (!newer(parsed, currentVersion)) return@mapNotNull null
        val ver = release.tag.removePrefix("v")
        val name = "Aurora-Music-v$ver-windows-amd64.exe"
        val expectedUrl = "https://github.com/$REPOSITORY/releases/download/${release.tag}/$name"
        val asset = release.assets.firstOrNull {
            it.name == name && it.url == expectedUrl && it.size in 1..MAX_UPDATE_BYTES &&
                it.digest?.matches(digestPattern) == true
        } ?: return@mapNotNull null
        Update(ver, asset)
    }.reduceOrNull { best, candidate ->
        if (newer(version("v${candidate.version}")!!, version("v${best.version}")!!)) candidate else best
    }
}

internal class Updater {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NEVER).build()

    fun check(): Update? {
        var best: Update? = null
        var page = 1
        do {
            val response = send(URI("https://api.github.com/repos/$REPOSITORY/releases?per_page=30&page=$page"))
            response.body().use { body ->
                if (response.statusCode() != 200) throw IOException("GitHub update metadata returned HTTP ${response.statusCode()}")
                val bytes = body.readNBytes(4 * 1024 * 1024 + 1)
                if (bytes.size > 4 * 1024 * 1024) throw IOException("Update metadata is too large")
                selectUpdate(bytes.toString(Charsets.UTF_8), best?.version ?: "1.33.0")?.let { best = it }
            }
            page++
        } while (response.headers().allValues("Link").any { "rel=\"next\"" in it })
        return best
    }

    fun download(update: Update, accelerated: Boolean, directory: Path): Path {
        // Revalidate metadata before using its URL or filename.
        val expectedName = "Aurora-Music-v${update.version}-windows-amd64.exe"
        val asset = update.asset
        if (version("v${update.version}") == null || asset.name != expectedName ||
            asset.url != "https://github.com/$REPOSITORY/releases/download/v${update.version}/$expectedName" ||
            asset.size !in 1..MAX_UPDATE_BYTES || asset.digest?.matches(digestPattern) != true) {
            throw IOException("Invalid update metadata")
        }
        var uri = URI(if (accelerated) "https://gh-proxy.com/${asset.url}" else asset.url)
        var response: HttpResponse<java.io.InputStream>? = null
        repeat(6) {
            if (response != null) return@repeat
            if (uri.scheme != "https" || uri.userInfo != null || uri.port !in listOf(-1, 443) ||
                uri.host !in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com", "gh-proxy.com")) {
                throw IOException("Update download redirected to an untrusted address")
            }
            val result = send(uri)
            if (result.statusCode() in setOf(301, 302, 303, 307, 308)) {
                result.body().close()
                val location = result.headers().firstValue("Location").orElseThrow { IOException("Update redirect has no destination") }
                uri = try { uri.resolve(location) } catch (_: IllegalArgumentException) { throw IOException("Invalid update redirect") }
            } else {
                response = result
            }
        }
        val result = response ?: throw IOException("Too many update redirects")
        result.body().use { body ->
            if (result.statusCode() != 200) throw IOException("Update download returned HTTP ${result.statusCode()}")
            return saveVerifiedUpdate(body, asset, directory)
        }
    }

    private fun send(uri: URI): HttpResponse<java.io.InputStream> {
        val request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Aurora-Music-Desktop").GET().build()
        return try {
            http.send(request, HttpResponse.BodyHandlers.ofInputStream())
        } catch (_: IOException) {
            throw IOException("Unable to reach the update server")
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Update request interrupted")
        }
    }
}
