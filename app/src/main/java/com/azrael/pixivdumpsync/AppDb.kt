package com.azrael.pixivdumpsync

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class ArtistRecord(val userId: String, val label: String?)

class AppDb(context: Context) : SQLiteOpenHelper(context, "pixivdump.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE artists(
                user_id TEXT PRIMARY KEY,
                label TEXT,
                enabled INTEGER NOT NULL DEFAULT 1
            )""".trimIndent())
        db.execSQL("""CREATE TABLE works(
                illust_id TEXT PRIMARY KEY,
                artist_id TEXT NOT NULL,
                title TEXT,
                state TEXT NOT NULL,
                page_count INTEGER NOT NULL DEFAULT 0,
                liked_marked INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL
            )""".trimIndent())
        db.execSQL("""CREATE TABLE pages(
                illust_id TEXT NOT NULL,
                page_index INTEGER NOT NULL,
                filename TEXT NOT NULL,
                saved INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(illust_id, page_index)
            )""".trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun addArtist(userId: String, label: String? = null) {
        val values = ContentValues().apply {
            put("user_id", userId)
            put("label", label)
            put("enabled", 1)
        }
        writableDatabase.insertWithOnConflict("artists", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun removeArtist(userId: String) {
        writableDatabase.delete("artists", "user_id=?", arrayOf(userId))
    }

    fun listArtists(): List<ArtistRecord> {
        val out = mutableListOf<ArtistRecord>()
        readableDatabase.query(
            "artists", arrayOf("user_id", "label"), "enabled=1",
            null, null, null, "user_id ASC"
        ).use { c ->
            while (c.moveToNext()) out += ArtistRecord(c.getString(0), c.getString(1))
        }
        return out
    }

    fun workState(illustId: String): String? {
        readableDatabase.query(
            "works", arrayOf("state"), "illust_id=?", arrayOf(illustId),
            null, null, null
        ).use { c -> return if (c.moveToFirst()) c.getString(0) else null }
    }

    fun upsertWork(illustId: String, artistId: String, title: String?, state: String, pageCount: Int) {
        val values = ContentValues().apply {
            put("illust_id", illustId)
            put("artist_id", artistId)
            put("title", title)
            put("state", state)
            put("page_count", pageCount)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("works", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun setWorkState(illustId: String, state: String, likedMarked: Boolean? = null) {
        val values = ContentValues().apply {
            put("state", state)
            put("updated_at", System.currentTimeMillis())
            if (likedMarked != null) put("liked_marked", if (likedMarked) 1 else 0)
        }
        writableDatabase.update("works", values, "illust_id=?", arrayOf(illustId))
    }

    fun isPageSaved(illustId: String, pageIndex: Int): Boolean {
        readableDatabase.query(
            "pages", arrayOf("saved"), "illust_id=? AND page_index=?",
            arrayOf(illustId, pageIndex.toString()), null, null, null
        ).use { c -> return c.moveToFirst() && c.getInt(0) == 1 }
    }

    fun markPageSaved(illustId: String, pageIndex: Int, filename: String) {
        val values = ContentValues().apply {
            put("illust_id", illustId)
            put("page_index", pageIndex)
            put("filename", filename)
            put("saved", 1)
        }
        writableDatabase.insertWithOnConflict("pages", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }
}
