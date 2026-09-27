package com.azrael.pixivdumpsync

import android.app.job.JobParameters
import android.app.job.JobService
import java.util.concurrent.Executors

class SyncJobService : JobService() {
    private val executor = Executors.newSingleThreadExecutor()

    override fun onStartJob(params: JobParameters?): Boolean {
        if (
            params == null ||
            !SessionStore.isLoggedIn(this) ||
            !SessionStore.autoSync(this)
        ) {
            return false
        }

        if (SyncControl.snapshot().running) {
            return false
        }

        executor.execute {
            try {
                val offset = SessionStore.backgroundArtistOffset(applicationContext)
                val stats = SyncEngine(applicationContext).run(
                    mode = SyncMode.LIVE,
                    selectedOnly = false,
                    maxArtists = BACKGROUND_ARTIST_BATCH,
                    artistOffset = offset,
                    queueLiveIfBusy = false
                )
                SessionStore.setBackgroundArtistOffset(
                    applicationContext,
                    offset + stats.artists.coerceAtLeast(BACKGROUND_ARTIST_BATCH)
                )
                jobFinished(params, false)
            } catch (_: Throwable) {
                jobFinished(params, true)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        SyncControl.stop()
        return true
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val BACKGROUND_ARTIST_BATCH = 8
    }
}
