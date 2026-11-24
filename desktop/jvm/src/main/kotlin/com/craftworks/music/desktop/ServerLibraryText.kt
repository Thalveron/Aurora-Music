package com.craftworks.music.desktop

import java.io.StringReader
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.swing.text.MutableAttributeSet
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.parser.ParserDelegator

internal fun serverLibraryRadioUri(value: String): URI? {
    val uri = try {
        URI(value.trim())
    } catch (_: URISyntaxException) {
        return null
    }
    if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank() ||
        uri.rawUserInfo != null || uri.rawFragment != null || uri.port == 0 || uri.port > 65535) return null
    val keys = uri.rawQuery.orEmpty().split('&', ';').map {
        URLDecoder.decode(it.substringBefore('='), StandardCharsets.UTF_8).lowercase()
    }
    // Never treat an authenticated Subsonic playback URL as a standalone station.
    if ("u" in keys && ("p" in keys || "t" in keys)) return null
    return uri
}

internal fun serverLibraryBiography(value: String): String {
    if ('<' !in value && '&' !in value) return value.trim()
    // Decode markup and entities as text only; never load links or embedded media.
    return buildString {
        val callback = object : HTMLEditorKit.ParserCallback() {
            override fun handleText(data: CharArray, pos: Int) { append(data) }
            override fun handleSimpleTag(t: HTML.Tag, a: MutableAttributeSet, pos: Int) {
                if (t == HTML.Tag.BR) append('\n')
            }
            override fun handleEndTag(t: HTML.Tag, pos: Int) {
                if (t == HTML.Tag.P || t == HTML.Tag.DIV) append('\n')
            }
        }
        ParserDelegator().parse(StringReader(value), callback, true)
    }.trim()
}
