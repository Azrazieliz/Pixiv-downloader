package com.azrael.pixivdumpsync

import android.content.Context

object SessionStore {
    private const val PREFS = "pixivdump_prefs"
    private const val COOKIE_KEY = "pixiv_cookie"
    private const val VERIFIED_KEY = "pixiv_session_verified"
    private const val LAST_SYNC_KEY = "last_sync_summary"
    private const val AUTO_SYNC_KEY = "auto_sync"
    private const val FOLLOWING_FEED_ENABLED_KEY = "following_feed_enabled"
    private const val FOLLOWING_FEED_CURSOR_KEY = "following_feed_cursor"
    private const val FOLLOWING_PREVIEW_KEY = "following_preview_json"
    private const val BACKGROUND_ARTIST_OFFSET_KEY = "background_artist_offset"

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

    fun saveCookie(context: Context, value: String, verified: Boolean = false) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(COOKIE_KEY, normalizeCookieInput(value))
            .putBoolean(VERIFIED_KEY, verified)
            .apply()
    }

    fun markVerified(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(VERIFIED_KEY, true)
            .apply()
    }

    fun clearCookie(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(COOKIE_KEY)
            .remove(VERIFIED_KEY)
            .remove(FOLLOWING_FEED_CURSOR_KEY)
            .apply()
    }

    fun cookie(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(COOKIE_KEY, null)
            ?.takeIf { it.isNotBlank() }

    fun isLoggedIn(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(VERIFIED_KEY, false) &&
            cookie(context)?.contains("PHPSESSID=") == true
    }

    fun pixivUserIdFromCookie(context: Context): String? {
        val session = cookie(context)
            ?.split(';')
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("PHPSESSID=") }
            ?.substringAfter('=')
            ?: return null

        return session.substringBefore('_')
            .takeIf { it.isNotBlank() && it.all(Char::isDigit) }
    }

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

    fun setFollowingFeedEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(FOLLOWING_FEED_ENABLED_KEY, enabled).apply()
    }

    fun followingFeedEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(FOLLOWING_FEED_ENABLED_KEY, true)

    fun setFollowingFeedCursor(context: Context, illustId: String?) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (illustId.isNullOrBlank()) editor.remove(FOLLOWING_FEED_CURSOR_KEY)
        else editor.putString(FOLLOWING_FEED_CURSOR_KEY, illustId)
        editor.apply()
    }

    fun followingFeedCursor(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(FOLLOWING_FEED_CURSOR_KEY, null)
            ?.takeIf { it.isNotBlank() }

    fun setFollowingPreviewJson(context: Context, value: String?) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (value.isNullOrBlank()) editor.remove(FOLLOWING_PREVIEW_KEY)
        else editor.putString(FOLLOWING_PREVIEW_KEY, value)
        editor.apply()
    }

    fun followingPreviewJson(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(FOLLOWING_PREVIEW_KEY, null)
            ?.takeIf { it.isNotBlank() }

    fun backgroundArtistOffset(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(BACKGROUND_ARTIST_OFFSET_KEY, 0)
            .coerceAtLeast(0)

    fun setBackgroundArtistOffset(context: Context, value: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(BACKGROUND_ARTIST_OFFSET_KEY, value.coerceAtLeast(0))
            .apply()
    }
}
