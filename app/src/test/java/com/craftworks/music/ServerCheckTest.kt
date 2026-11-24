package com.craftworks.music

import com.craftworks.music.providers.navidrome.NetworkFailure
import com.craftworks.music.providers.navidrome.ServerCheck
import com.craftworks.music.providers.navidrome.SubsonicResponse
import com.craftworks.music.providers.navidrome.classifyNetworkFailure
import com.craftworks.music.providers.navidrome.interpretPingResponse
import com.craftworks.music.providers.navidrome.normalizeServerUrl
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class ServerCheckTest {
    @Test fun bareHostGetsHttpScheme() {
        assertEquals("http://192.168.1.10:4533", normalizeServerUrl(" 192.168.1.10:4533 "))
        assertEquals("http://nas.local", normalizeServerUrl("nas.local"))
    }

    @Test fun trailingSlashAndRestSuffixAreDropped() {
        assertEquals("https://music.example.com", normalizeServerUrl("https://music.example.com/"))
        assertEquals("https://music.example.com", normalizeServerUrl("HTTPS://music.example.com/rest/"))
        assertEquals("http://host/navidrome", normalizeServerUrl("http://host/navidrome//"))
    }

    @Test fun fullWidthPunctuationFromTvKeyboardsIsAccepted() {
        assertEquals("http://192.168.1.10:4533", normalizeServerUrl("192。168。1。10：4533"))
    }

    @Test fun unusableAddressesAreRejected() {
        for (bad in listOf("", "   ", "http://", "ftp://host", "my server", "http://:4533"))
            assertNull(bad, normalizeServerUrl(bad))
    }

    @Test fun okPingIsAccepted() {
        val body = """{"subsonic-response":{"status":"ok","version":"1.16.1","type":"navidrome","serverVersion":"0.53.3","openSubsonic":true}}"""
        assertEquals(ServerCheck.Ok, interpretPingResponse(200, body))
    }

    @Test fun classicSubsonicPingWithoutOpenSubsonicFieldsIsAccepted() {
        assertEquals(ServerCheck.Ok, interpretPingResponse(200, """{"subsonic-response":{"status":"ok","version":"1.16.1"}}"""))
    }

    @Test fun wrongPasswordIsReportedAsCredentialsEvenOnHttp401() {
        val body = """{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":40,"message":"Wrong username or password"}}}"""
        assertEquals(ServerCheck.WrongCredentials, interpretPingResponse(200, body))
        assertEquals(ServerCheck.WrongCredentials, interpretPingResponse(401, body))
    }

    @Test fun otherServerErrorsKeepTheirCode() {
        val body = """{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":10,"message":"Missing parameter"}}}"""
        assertEquals(ServerCheck.ServerError(10, "Missing parameter"), interpretPingResponse(200, body))
    }

    @Test fun nonSubsonicAnswersAreDistinguishedFromHttpFailures() {
        assertEquals(ServerCheck.NotSubsonic, interpretPingResponse(200, "<html>router login</html>"))
        assertEquals(ServerCheck.NotSubsonic, interpretPingResponse(200, """{"hello":"world"}"""))
        assertEquals(ServerCheck.Http(404), interpretPingResponse(404, "404 page not found"))
        assertEquals(ServerCheck.Http(502), interpretPingResponse(502, ""))
    }

    @Test fun networkFailuresAreClassifiedThroughWrappedCauses() {
        assertEquals(NetworkFailure.UNKNOWN_HOST, classifyNetworkFailure(UnknownHostException("nas")).kind)
        assertEquals(NetworkFailure.UNREACHABLE, classifyNetworkFailure(ConnectException("refused")).kind)
        assertEquals(NetworkFailure.TIMEOUT, classifyNetworkFailure(IOException("wrapped", SocketTimeoutException())).kind)
        assertEquals(NetworkFailure.TLS, classifyNetworkFailure(SSLHandshakeException("cert")).kind)
        assertEquals(NetworkFailure.OTHER, classifyNetworkFailure(IllegalStateException("x")).kind)
    }

    @Test fun libraryResponsesFromClassicSubsonicServersDecode() {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val response = json.decodeFromString<SubsonicResponse>("""{"status":"ok","version":"1.16.1","musicFolders":{"musicFolder":[{"id":1,"name":"Music"}]}}""")
        assertEquals(1, response.musicFolders!!.musicFolder.size)
        assertEquals(false, response.openSubsonic)
    }
}
