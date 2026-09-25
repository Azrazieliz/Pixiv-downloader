package com.azrael.pixivdumpsync

import android.content.Context
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpCookie
import java.net.URI

object NetworkCookies {
    private val baseUri = URI("https://www.pixiv.net/")

    fun install(context: Context) {
        val raw = SessionStore.cookie(context) ?: return
        val manager = CookieManager(null, CookiePolicy.ACCEPT_ALL)

        raw.split(';').forEach { part ->
            val item = part.trim()
            val eq = item.indexOf('=')
            if (eq <= 0) return@forEach
            val name = item.substring(0, eq).trim()
            val value = item.substring(eq + 1).trim()
            if (name.isBlank()) return@forEach

            val c = HttpCookie(name, value).apply {
                domain = ".pixiv.net"
                path = "/"
                secure = true
                version = 0
            }
            manager.cookieStore.add(baseUri, c)
        }

        CookieHandler.setDefault(manager)
    }
}
