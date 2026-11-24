package com.craftworks.music.providers.navidrome

import java.security.MessageDigest
import java.security.SecureRandom

object SubsonicAuth {
    private val random = SecureRandom()

    fun md5Hash(input: String): String {
        val hashBytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun generateSalt(length: Int): String {
        val allowedChars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        return (1..length).map { allowedChars[random.nextInt(allowedChars.size)] }.joinToString("")
    }
}
