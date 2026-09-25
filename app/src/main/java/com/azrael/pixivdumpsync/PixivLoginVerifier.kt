package com.azrael.pixivdumpsync

import org.json.JSONObject

object PixivLoginVerifier {
    fun isAuthenticatedResponse(raw: String): Boolean {
        return try {
            val root = JSONObject(raw)
            !root.optBoolean("error", true) && !root.isNull("body")
        } catch (_: Throwable) {
            false
        }
    }
}
