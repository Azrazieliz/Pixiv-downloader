package com.azrael.pixivdumpsync

import android.app.job.JobParameters
import android.app.job.JobService
import java.util.concurrent.Executors

class SyncJobService : JobService() {
    private val executor = Executors.newSingleThreadExecutor()

    override fun onStartJob(params: JobParameters?): Boolean {
        if (params == null || !SessionStore.isLoggedIn(this)) return false

        executor.execute {
            try {
                SyncEngine(applicationContext).run(
                    mode = SyncMode.LIVE,
                    selectedOnly = false
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
}
