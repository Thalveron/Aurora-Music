package com.craftworks.music.providers.navidrome

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.net.UnknownServiceException
import java.nio.channels.UnresolvedAddressException
import javax.net.ssl.SSLException

/** Outcome of testing a server before it is saved. Has no Android dependencies. */
sealed class ServerCheck {
    data object Ok : ServerCheck()
    data object InvalidUrl : ServerCheck()
    data object WrongCredentials : ServerCheck()
    /** The host answered, but not with a Subsonic API response. */
    data object NotSubsonic : ServerCheck()
    data class ServerError(val code: Int, val message: String) : ServerCheck()
    data class Http(val status: Int) : ServerCheck()
    data class Network(val kind: NetworkFailure, val detail: String) : ServerCheck()
}

enum class NetworkFailure { UNKNOWN_HOST, UNREACHABLE, TIMEOUT, TLS, CLEARTEXT_BLOCKED, OTHER }

private val schemePattern = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
private val restSuffix = Regex("/rest$", RegexOption.IGNORE_CASE)

/**
 * Turns what a user typed into the base URL requests are built from, or null when it cannot be one.
 * TV keyboards often produce full-width punctuation and people paste URLs ending in "/" or "/rest".
 */
fun normalizeServerUrl(input: String): String? {
    var value = input.trim()
        .replace('：', ':')
        .replace('。', '.')
        .replace('／', '/')
    if (value.isEmpty() || value.any { it.isWhitespace() }) return null
    if (!schemePattern.containsMatchIn(value)) value = "http://$value"

    val uri = try {
        URI(value)
    } catch (_: Exception) {
        return null
    }
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return null
    val authority = uri.rawAuthority
    if (authority.isNullOrEmpty() || authority.startsWith(":")) return null

    val path = (uri.rawPath ?: "").trimEnd('/').replace(restSuffix, "").trimEnd('/')
    return "$scheme://$authority$path"
}

private val pingJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** Subsonic reports failures inside the body, usually with HTTP 200, so the body is read first. */
fun interpretPingResponse(httpStatus: Int, body: String): ServerCheck {
    val response = runCatching {
        (pingJson.parseToJsonElement(body) as JsonObject)["subsonic-response"] as JsonObject
    }.getOrNull()

    if (response == null) {
        return if (httpStatus in 200..299) ServerCheck.NotSubsonic else ServerCheck.Http(httpStatus)
    }
    if (runCatching { response["status"]?.jsonPrimitive?.contentOrNull }.getOrNull() == "ok") {
        return ServerCheck.Ok
    }

    val error = response["error"] as? JsonObject
    val code = runCatching { error?.get("code")?.jsonPrimitive?.intOrNull }.getOrNull() ?: 0
    val message = runCatching { error?.get("message")?.jsonPrimitive?.contentOrNull }.getOrNull().orEmpty()
    return if (code == 40) ServerCheck.WrongCredentials else ServerCheck.ServerError(code, message)
}

fun classifyNetworkFailure(error: Throwable): ServerCheck.Network {
    val causes = generateSequence(error) { it.cause }.take(8).toList()
    val kind = when {
        causes.any { it is UnknownHostException || it is UnresolvedAddressException } -> NetworkFailure.UNKNOWN_HOST
        causes.any { it is SocketTimeoutException } -> NetworkFailure.TIMEOUT
        causes.any { it is UnknownServiceException } -> NetworkFailure.CLEARTEXT_BLOCKED
        causes.any { it is SSLException } -> NetworkFailure.TLS
        causes.any { it is ConnectException || it is NoRouteToHostException } -> NetworkFailure.UNREACHABLE
        else -> NetworkFailure.OTHER
    }
    return ServerCheck.Network(kind, error.message ?: error.javaClass.simpleName)
}

/** Status strings the phone dialogs already compare against. */
fun ServerCheck.toLegacyStatus(): String = when (this) {
    ServerCheck.Ok -> "ok"
    ServerCheck.InvalidUrl -> "Invalid URL"
    ServerCheck.WrongCredentials -> "Wrong username or password"
    ServerCheck.NotSubsonic -> "Not a Subsonic server"
    is ServerCheck.ServerError -> "Error $code: $message"
    is ServerCheck.Http -> "HTTP $status"
    is ServerCheck.Network -> detail
}
