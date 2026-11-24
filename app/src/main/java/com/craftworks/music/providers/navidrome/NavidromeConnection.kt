package com.craftworks.music.providers.navidrome

import android.util.Log
fun parseSubsonicResponse(response: String): SubsonicResponse {
    val subsonicResponse = decodeSubsonicResponse(response)

    if (subsonicResponse.status != "ok") {
        val errorCode = subsonicResponse.error?.code
        val errorMessage = subsonicResponse.error?.message
        Log.d("NAVIDROME", "Navidrome Error Code: $errorCode, Message: $errorMessage")
        navidromeStatus.value = "Error $errorCode: $errorMessage"
    }

    return subsonicResponse
}