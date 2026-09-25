package com.azrael.pixivdumpsync

object PixivWebLoginResult {
    fun isAuthenticated(value: String?): Boolean =
        value?.trim()?.equals("true", ignoreCase = true) == true
}
