package com.azrael.pixivdumpsync

import android.content.Context

object SessionStore {
    private const val PREFS = "pixivdump_prefs"
    private const val COOKIE_KEY = "pixiv_cookie"
    private const val LAST_SYNC_KEY = "last_sync_summary"
    private const val AUTO_SYNC_KEY = "auto_sync"

    fun normalizeCookieInput(raw: String): String {
        var value = raw.trim()
        if (value.startsWith("Cookie:", ignoreCase = true)) {
            value = value.substringAfter(':').trim()
        }
        return value
            .replace("\r", "")
            .split('\n')
            .map { it.trim().trimEnd(';') }
            .filter { it.isNotBlank() }
            .joinToString("; ")
    }

    fun saveCookie(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(COOKIE_KEY, normalizeCookieInput(value))
            .apply()
    }

    fun clearCookie(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(COOKIE_KEY)
            .apply()
    }

    fun cookie(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(COOKIE_KEY, null)
            ?.takeIf { it.isNotBlank() }

    fun isLoggedIn(context: Context): Boolean =
        cookie(context)?.contains("PHPSESSID=") == true

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
