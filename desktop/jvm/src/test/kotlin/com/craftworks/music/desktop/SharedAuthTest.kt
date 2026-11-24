package com.craftworks.music.desktop

import com.craftworks.music.providers.navidrome.SubsonicAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedAuthTest {
    @Test
    fun hashesSubsonicPasswordAndSalt() {
        assertEquals("482c811da5d5b4bc6d497ffa98491e38", SubsonicAuth.md5Hash("password123"))
    }

    @Test
    fun generatesAlphanumericSaltOfRequestedLength() {
        val salt = SubsonicAuth.generateSalt(8)
        assertEquals(8, salt.length)
        assertTrue(salt.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' })
    }
}
