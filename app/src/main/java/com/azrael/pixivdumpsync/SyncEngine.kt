package com.azrael.pixivdumpsync

import android.content.Context
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

class SyncEngine(private val context: Context) {
    data class Stats(
        var artists: Int = 0,
        var worksSeen: Int = 0,
        var worksCompleted: Int = 0,
        var pagesDownloaded: Int = 0,
        var skippedDone: Int = 0,
        var skippedUgoira: Int = 0,
        var errors: Int = 0
    )

    companion object {
        private val syncRunning = AtomicBoolean(false)
    }

    fun run(progress: (String) -> Unit = {}): Stats {
        val stats = Stats()
        if (!syncRunning.compareAndSet(false, true)) {
            progress("A sync is already running; this duplicate run was ignored.")
            return stats
        }

        NetworkCookies.install(context)
        val db = AppDb(context)
        val api = PixivApi(context)

        try {
            val artists = db.listArtists()
            stats.artists = artists.size

            for ((artistIndex, artist) in artists.withIndex()) {
                progress("Artist ${artistIndex + 1}/${artists.size}: ${artist.userId}")

                val ids = try {
                    api.userArtworkIds(artist.userId)
                } catch (t: Throwable) {
                    stats.errors++
                    progress("Failed artist ${artist.userId}: ${t.message}")
                    continue
                }

                for ((workIndex, id) in ids.withIndex()) {
                    stats.worksSeen++

                    if (isCompleteOnDisk(db, id)) {
                        stats.skippedDone++
                        continue
                    }

                    progress("${artist.userId}: work ${workIndex + 1}/${ids.size} ($id)")

                    try {
                        val detail = api.artworkDetail(id)
                        if (detail.userId != artist.userId) continue

                        if (detail.illustType == 2) {
                            db.upsertWork(
                                id,
                                artist.userId,
                                detail.title,
                                "SKIPPED_UGOIRA",
                                0
                            )
                            stats.skippedUgoira++
                            continue
                        }

                        val urls = api.pageOriginalUrls(id)
                        db.upsertWork(
                            id,
                            artist.userId,
                            detail.title,
                            "DOWNLOADING",
                            urls.size
                        )

                        for ((pageIndex, imageUrl) in urls.withIndex()) {
                            val ext = FileStore.extensionFromUrl(imageUrl)
                            val filename = "${id}_p${pageIndex}.$ext"
                            val onDisk = FileStore.exists(context, filename)

                            if (db.isPageSaved(id, pageIndex) && onDisk) {
                                continue
                            }

                            if (!onDisk) {
                                FileStore.saveImage(
                                    context = context,
                                    imageUrl = imageUrl,
                                    filename = filename,
                                    referer = "https://www.pixiv.net/artworks/$id"
                                )
                                stats.pagesDownloaded++
                            }

                            db.markPageSaved(id, pageIndex, filename)
                        }

                        db.setWorkState(id, "DOWNLOADED_UNBOOKMARKED")

                        if (!detail.isBookmarked) {
                            api.bookmark(id)
                        }

                        db.setWorkState(id, "DONE", bookmarkedMarked = true)
                        stats.worksCompleted++

                        Thread.sleep(350L)
                    } catch (t: Throwable) {
                        stats.errors++
                        progress("Error $id: ${t.message}")

                        if (t is HttpStatusException && t.code == 429) {
                            progress("Pixiv rate-limited this run; stopping and retrying later.")
                            return finish(stats)
                        }
                    }
                }
            }

            return finish(stats)
        } finally {
            db.close()
            syncRunning.set(false)
        }
    }

    private fun isCompleteOnDisk(db: AppDb, illustId: String): Boolean {
        if (!db.isWorkDoneAndBookmarked(illustId)) return false

        val expected = db.workPageCount(illustId) ?: return false
        if (expected <= 0) return false

        val files = db.savedPageFilenames(illustId)
        if (files.size != expected) return false

        return files.all { FileStore.exists(context, it) }
    }

    private fun finish(stats: Stats): Stats {
        val stamp = DateFormat.getDateTimeInstance(
            DateFormat.SHORT,
            DateFormat.SHORT
        ).format(Date())

        SessionStore.setLastSyncSummary(
            context,
            "$stamp — ${stats.worksCompleted} works completed, " +
                "${stats.pagesDownloaded} images downloaded, ${stats.errors} errors"
        )

        return stats
    }
}
