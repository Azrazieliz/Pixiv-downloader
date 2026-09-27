package com.azrael.pixivdumpsync

import android.content.Context
import java.net.UnknownHostException
import java.text.DateFormat
import java.util.Date

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

    private enum class WorkResult {
        DONE,
        SKIPPED,
        STOPPED
    }

    fun run(
        mode: SyncMode,
        selectedOnly: Boolean = false,
        targetArtistId: String? = null,
        maxArtists: Int? = null,
        artistOffset: Int = 0,
        queueLiveIfBusy: Boolean = true,
        progress: (String) -> Unit = {}
    ): Stats {
        val stats = Stats()

        if (!SyncControl.tryStart(mode, queueLiveIfBusy = queueLiveIfBusy)) {
            val s = SyncControl.snapshot()
            progress(
                if (mode == SyncMode.LIVE && s.pendingLive) {
                    "Live check queued behind the active sync."
                } else {
                    "Another sync is already running."
                }
            )
            return stats
        }

        NetworkCookies.install(context)
        val db = AppDb(context)
        db.clearTransientNetworkErrors()
        val api = PixivApi(context)
        var finalMessage = "Idle"
        var released = false

        try {
            when (mode) {
                SyncMode.LIVE -> runLivePass(
                    db = db,
                    api = api,
                    selectedOnly = selectedOnly,
                    targetArtistId = targetArtistId,
                    maxArtists = maxArtists,
                    artistOffset = artistOffset,
                    stats = stats,
                    progress = progress
                )
                SyncMode.BACKFILL -> runBackfillPass(
                    db = db,
                    api = api,
                    targetArtistId = targetArtistId,
                    stats = stats,
                    progress = progress
                )
            }

            finalMessage = if (SyncControl.snapshot().stopping) {
                "Stopped"
            } else {
                "${modeLabel(mode)} complete"
            }

            while (
                !SyncControl.snapshot().stopping &&
                SyncControl.completeOrContinueWithQueuedLive(finalMessage)
            ) {
                progress("Running queued Live Sync…")
                runLivePass(
                    db = db,
                    api = api,
                    selectedOnly = false,
                    targetArtistId = null,
                    maxArtists = null,
                    artistOffset = 0,
                    stats = stats,
                    progress = progress
                )
                finalMessage = "Live sync complete"
            }

            if (SyncControl.snapshot().running) {
                SyncControl.forceFinish(finalMessage)
            }
            released = true
            return finish(stats, mode)
        } finally {
            db.close()
            if (!released && SyncControl.snapshot().running) {
                SyncControl.forceFinish(finalMessage)
            }
        }
    }

    private fun runLivePass(
        db: AppDb,
        api: PixivApi,
        selectedOnly: Boolean,
        targetArtistId: String?,
        maxArtists: Int?,
        artistOffset: Int,
        stats: Stats,
        progress: (String) -> Unit
    ) {
        SyncControl.setMode(SyncMode.LIVE)

        if (targetArtistId == null && SessionStore.followingFeedEnabled(context)) {
            val feedMessage = "Following feed • checking newest works"
            SyncControl.updateMessage(feedMessage)
            progress(feedMessage)
            try {
                syncFollowingFeed(db, api, stats, progress)
            } catch (t: Throwable) {
                if (SyncControl.isStopping()) return
                stats.errors++
                progress("Following feed error: ${t.message}")
                if (isGlobalNetworkError(t)) return
                if (t is HttpStatusException && t.code == 429) {
                    progress("Pixiv rate-limited this run; it will retry later.")
                    return
                }
            }
        }

        val allArtists = when {
            targetArtistId != null -> listOfNotNull(db.artist(targetArtistId))
            selectedOnly -> db.listSelectedLiveArtists()
            else -> db.listLiveArtists()
        }
        val artists = if (
            maxArtists != null &&
            maxArtists > 0 &&
            allArtists.size > maxArtists
        ) {
            val start = artistOffset % allArtists.size
            List(maxArtists.coerceAtMost(allArtists.size)) { index ->
                allArtists[(start + index) % allArtists.size]
            }
        } else {
            allArtists
        }

        stats.artists = maxOf(stats.artists, artists.size)

        for ((artistIndex, artist) in artists.withIndex()) {
            if (!SyncControl.checkpoint()) return

            val name = artist.label ?: "Pixiv user ${artist.userId}"
            val message =
                "Live sync • ${artistIndex + 1}/${artists.size} • $name"

            SyncControl.updateMessage(message)
            progress(message)

            try {
                syncLiveArtist(db, api, artist, stats, progress)
            } catch (t: Throwable) {
                if (SyncControl.isStopping()) return
                stats.errors++
                db.setArtistError(
                    artist.userId,
                    t.message ?: "Unknown error"
                )
                progress("Error for $name: ${t.message}")

                if (isGlobalNetworkError(t)) return
                if (t is HttpStatusException && t.code == 429) {
                    progress("Pixiv rate-limited this run; it will retry later.")
                    return
                }
            }
        }
    }

    private fun syncFollowingFeed(
        db: AppDb,
        api: PixivApi,
        stats: Stats,
        progress: (String) -> Unit
    ) {
        val cursor = SessionStore.followingFeedCursor(context)
        val allItems = mutableListOf<PixivApi.ArtworkPreview>()
        var newestId: String? = null
        val maxPages = if (cursor.isNullOrBlank()) 1 else 10

        for (pageNumber in 1..maxPages) {
            if (!SyncControl.checkpoint()) return

            val page = api.followingFeedPage(pageNumber)
            if (newestId == null) newestId = page.items.firstOrNull()?.id
            allItems += page.items.filter { candidate ->
                allItems.none { it.id == candidate.id }
            }

            if (!cursor.isNullOrBlank() && page.items.any { it.id == cursor }) {
                break
            }
            if (page.isLastPage || page.items.isEmpty()) break
        }

        val newItems = if (cursor.isNullOrBlank()) {
            allItems
        } else {
            allItems.takeWhile { it.id != cursor }
        }

        if (newItems.isEmpty()) {
            if (cursor.isNullOrBlank() && newestId != null) {
                SessionStore.setFollowingFeedCursor(context, newestId)
            }
            progress("Following feed • no new works")
            return
        }

        var hadError = false
        for ((index, item) in newItems.asReversed().withIndex()) {
            if (!SyncControl.checkpoint()) return

            stats.worksSeen++
            val message = "Following feed • ${index + 1}/${newItems.size}"
            SyncControl.updateMessage(message)
            progress(message)

            if (isCompleteOnDisk(db, item.id)) {
                stats.skippedDone++
                continue
            }

            try {
                when (syncWork(db, api, item.userId, item.id, stats)) {
                    WorkResult.DONE, WorkResult.SKIPPED -> Unit
                    WorkResult.STOPPED -> return
                }
            } catch (t: Throwable) {
                if (SyncControl.isStopping()) return
                hadError = true
                stats.errors++
                progress("Following feed error ${item.id}: ${t.message}")
                if (t is HttpStatusException && t.code == 429) throw t
            }
        }

        if (!hadError && newestId != null) {
            SessionStore.setFollowingFeedCursor(context, newestId)
        }
    }

    private fun runBackfillPass(
        db: AppDb,
        api: PixivApi,
        targetArtistId: String?,
        stats: Stats,
        progress: (String) -> Unit
    ) {
        SyncControl.setMode(SyncMode.BACKFILL)

        val artists = if (targetArtistId != null) {
            listOfNotNull(db.artist(targetArtistId))
        } else {
            db.listSelectedArtists()
        }
        stats.artists = maxOf(stats.artists, artists.size)

        for ((artistIndex, artist) in artists.withIndex()) {
            if (!SyncControl.checkpoint()) return

            val name = artist.label ?: "Pixiv user ${artist.userId}"
            val message =
                "Archive backfill • ${artistIndex + 1}/${artists.size} • $name"

            SyncControl.updateMessage(message)
            progress(message)

            try {
                syncBackfillArtist(db, api, artist, stats, progress)
            } catch (t: Throwable) {
                if (SyncControl.isStopping()) return
                stats.errors++
                db.setArtistError(
                    artist.userId,
                    t.message ?: "Unknown error"
                )
                progress("Error for $name: ${t.message}")

                if (isGlobalNetworkError(t)) return
                if (t is HttpStatusException && t.code == 429) {
                    progress("Pixiv rate-limited this run; it will retry later.")
                    return
                }
            }

            serviceQueuedLiveDuringBackfill(db, api, stats, progress)
        }
    }

    private fun serviceQueuedLiveDuringBackfill(
        db: AppDb,
        api: PixivApi,
        stats: Stats,
        progress: (String) -> Unit
    ) {
        while (SyncControl.takePendingLive()) {
            if (!SyncControl.checkpoint()) return

            progress("Backfill paused for queued Live Sync…")
            runLivePass(
                db = db,
                api = api,
                selectedOnly = false,
                targetArtistId = null,
                maxArtists = null,
                artistOffset = 0,
                stats = stats,
                progress = progress
            )

            if (!SyncControl.checkpoint()) return
            SyncControl.setMode(SyncMode.BACKFILL)
            progress("Resuming archive backfill…")
        }
    }

    private fun syncLiveArtist(
        db: AppDb,
        api: PixivApi,
        artist: ArtistRecord,
        stats: Stats,
        progress: (String) -> Unit
    ) {
        val snapshot = api.userArtworkSnapshot(
            artist.userId,
            previewLimit = 5,
            resolveMissingPreviews = true
        )
        val ids = snapshot.ids
        val newest = ids.firstOrNull()

        if (snapshot.previews.any { !it.thumbnailUrl.isNullOrBlank() }) {
            db.updateArtistPresentation(
                artist.userId,
                previewJson = ArtworkPreviewCodec.encode(snapshot.previews)
            )
        }

        if (artist.liveCursor.isNullOrBlank()) {
            db.setLiveCursor(artist.userId, newest)
            db.markArtistChecked(artist.userId)

            progress(
                if (newest == null) {
                    "${artist.label ?: artist.userId}: live watch ready; no illustrations yet."
                } else {
                    "${artist.label ?: artist.userId}: live watch baseline established."
                }
            )
            return
        }

        val newIds = LiveCursorLogic.processingOrder(ids, artist.liveCursor)

        if (newIds.isNotEmpty()) {
            val refreshedPreviews = runCatching {
                api.userArtworkSnapshot(
                    artist.userId,
                    previewLimit = 5,
                    resolveMissingPreviews = true
                ).previews
            }.getOrDefault(emptyList())

            if (refreshedPreviews.any { !it.thumbnailUrl.isNullOrBlank() }) {
                db.updateArtistPresentation(
                    artist.userId,
                    previewJson = ArtworkPreviewCodec.encode(refreshedPreviews)
                )
            }
        }

        if (newIds.isEmpty()) {
            db.markArtistChecked(artist.userId)
            progress("${artist.label ?: artist.userId}: no new illustrations.")
            return
        }

        for ((index, id) in newIds.withIndex()) {
            if (!SyncControl.checkpoint()) return

            stats.worksSeen++
            val message =
                "Live • ${artist.label ?: artist.userId} • ${index + 1}/${newIds.size}"

            SyncControl.updateMessage(message)
            progress(message)

            try {
                if (isCompleteOnDisk(db, id)) {
                    stats.skippedDone++
                    db.setLiveCursor(artist.userId, id)
                    continue
                }

                when (syncWork(db, api, artist.userId, id, stats)) {
                    WorkResult.DONE, WorkResult.SKIPPED -> {
                        db.setLiveCursor(artist.userId, id)
                        db.setArtistError(artist.userId, null)
                    }
                    WorkResult.STOPPED -> return
                }
            } catch (t: Throwable) {
                if (SyncControl.isStopping()) return
                db.setArtistError(
                    artist.userId,
                    t.message ?: "Unknown error"
                )
                stats.errors++

                if (t is HttpStatusException && t.code == 429) {
                    throw t
                }
                return
            }
        }

        db.markArtistChecked(artist.userId)
    }

    private fun syncBackfillArtist(
        db: AppDb,
        api: PixivApi,
        artist: ArtistRecord,
        stats: Stats,
        progress: (String) -> Unit
    ) {
        val snapshot = api.userArtworkSnapshot(
            artist.userId,
            previewLimit = 5,
            resolveMissingPreviews = false
        )
        val ids = snapshot.ids

        if (snapshot.previews.isNotEmpty()) {
            db.updateArtistPresentation(
                artist.userId,
                previewJson = ArtworkPreviewCodec.encode(snapshot.previews)
            )
        }

        db.setArchiveMeta(
            artist.userId,
            status = "RUNNING",
            knownTotal = ids.size,
            error = null
        )

        var artistErrors = 0

        for ((index, id) in ids.withIndex()) {
            if (!SyncControl.checkpoint()) {
                db.setArchiveMeta(
                    artist.userId,
                    "PARTIAL",
                    ids.size,
                    null
                )
                return
            }

            if (!SyncControl.checkpoint()) {
                db.setArchiveMeta(
                    artist.userId,
                    "PARTIAL",
                    ids.size,
                    null
                )
                return
            }

            stats.worksSeen++
            val message =
                "Backfill • ${artist.label ?: artist.userId} • ${index + 1}/${ids.size}"

            SyncControl.updateMessage(message)
            progress(message)

            if (isCompleteOnDisk(db, id)) {
                stats.skippedDone++
                continue
            }

            try {
                when (syncWork(db, api, artist.userId, id, stats)) {
                    WorkResult.DONE, WorkResult.SKIPPED -> Unit
                    WorkResult.STOPPED -> {
                        db.setArchiveMeta(
                            artist.userId,
                            "PARTIAL",
                            ids.size,
                            null
                        )
                        return
                    }
                }
            } catch (t: Throwable) {
                if (SyncControl.isStopping()) {
                    db.setArchiveMeta(artist.userId, "PARTIAL", ids.size, null)
                    return
                }
                stats.errors++
                artistErrors++
                db.setArtistError(
                    artist.userId,
                    t.message ?: "Unknown error"
                )
                progress("Error $id: ${t.message}")

                if (t is HttpStatusException && t.code == 429) {
                    throw t
                }
            }
        }

        val p = db.artistProgress(artist.userId, ids.size)
        val complete = p.done + p.skipped >= ids.size

        val status = when {
            complete -> "COMPLETE"
            artistErrors > 0 && p.done == 0 -> "ERROR"
            else -> "PARTIAL"
        }

        db.setArchiveMeta(
            artist.userId,
            status = status,
            knownTotal = ids.size,
            error = if (complete) null else artist.lastError
        )
    }

    private fun syncWork(
        db: AppDb,
        api: PixivApi,
        expectedArtistId: String,
        id: String,
        stats: Stats
    ): WorkResult {
        if (!SyncControl.checkpoint()) {
            return WorkResult.STOPPED
        }

        val detail = api.artworkDetail(id)

        if (detail.userId != expectedArtistId) {
            return WorkResult.SKIPPED
        }

        if (detail.illustType == 2) {
            db.upsertWork(
                id,
                expectedArtistId,
                detail.title,
                "SKIPPED_UGOIRA",
                0
            )
            stats.skippedUgoira++
            return WorkResult.SKIPPED
        }

        val urls = api.pageOriginalUrls(id)

        db.upsertWork(
            id,
            expectedArtistId,
            detail.title,
            "DOWNLOADING",
            urls.size
        )

        for ((pageIndex, imageUrl) in urls.withIndex()) {
            if (!SyncControl.checkpoint()) {
                return WorkResult.STOPPED
            }

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

            db.markPageSaved(
                id,
                pageIndex,
                filename
            )
        }

        if (!SyncControl.checkpoint()) {
            return WorkResult.STOPPED
        }

        db.setWorkState(
            id,
            "DOWNLOADED_UNBOOKMARKED"
        )

        if (!detail.isBookmarked) {
            api.bookmark(id)
        }

        db.setWorkState(
            id,
            "DONE",
            bookmarkedMarked = true
        )

        stats.worksCompleted++
        return WorkResult.DONE
    }

    private fun isCompleteOnDisk(
        db: AppDb,
        illustId: String
    ): Boolean {
        if (!db.isWorkDoneAndBookmarked(illustId)) {
            return false
        }

        val expected =
            db.workPageCount(illustId)
                ?: return false

        if (expected <= 0) {
            return false
        }

        val files =
            db.savedPageFilenames(illustId)

        if (files.size != expected) {
            return false
        }

        return files.all {
            FileStore.exists(context, it)
        }
    }

    private fun finish(
        stats: Stats,
        mode: SyncMode
    ): Stats {
        val stamp =
            DateFormat.getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.SHORT
            ).format(Date())

        val summary =
            "$stamp — ${modeLabel(mode)}: " +
                "${stats.worksCompleted} completed, " +
                "${stats.pagesDownloaded} images, " +
                "${stats.errors} errors"

        SessionStore.setLastSyncSummary(
            context,
            summary
        )

        return stats
    }

    private fun isGlobalNetworkError(t: Throwable): Boolean {
        var current: Throwable? = t
        while (current != null) {
            if (current is UnknownHostException) return true
            current = current.cause
        }
        return t.message?.contains("Unable to resolve host", ignoreCase = true) == true
    }

    private fun modeLabel(mode: SyncMode): String =
        if (mode == SyncMode.LIVE) {
            "Live sync"
        } else {
            "Archive backfill"
        }
}
