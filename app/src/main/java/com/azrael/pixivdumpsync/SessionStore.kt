package com.azrael.pixivdumpsync

import android.content.Context
import android.webkit.CookieManager

object SessionStore {
    private const val PREFS = "pixivdump_prefs"
    private const val COOKIE_KEY = "pixiv_cookie"
    private const val LAST_SYNC_KEY = "last_sync_summary"
    private const val AUTO_SYNC_KEY = "auto_sync"

    fun captureFromWebView(context: Context): String? {
        val manager = CookieManager.getInstance()
        val value = manager.getCookie("https://www.pixiv.net")
        if (!value.isNullOrBlank() && value.contains("PHPSESSID=")) {
            saveCookie(context, value)
            return value
        }
        return null
    }

    fun saveCookie(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(COOKIE_KEY, value.trim())
            .apply()
    }

    fun cookie(context: Context): String? {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(COOKIE_KEY, null)
        if (!saved.isNullOrBlank()) return saved
        return captureFromWebView(context)
    }

    fun isLoggedIn(context: Context): Boolean = cookie(context)?.contains("PHPSESSID=") == true

    fun setLastSyncSummary(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(LAST_SYNC_KEY, value).apply()
    }

    fun lastSyncSummary(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(LAST_SYNC_KEY, "Never synced") ?: "Never synced"

    fun setAutoSync(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(AUTO_SYNC_KEY, enabled).apply()
    }

    fun autoSync(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(AUTO_SYNC_KEY, true)
}
