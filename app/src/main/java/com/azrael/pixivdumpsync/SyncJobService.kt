package com.azrael.pixivdumpsync

import android.app.job.JobParameters
import android.app.job.JobService
import java.util.concurrent.Executors

class SyncJobService : JobService() {
    private val executor = Executors.newSingleThreadExecutor()

    override fun onStartJob(params: JobParameters?): Boolean {
        if (params == null) return false
        executor.execute {
            try {
                SyncEngine(applicationContext).run()
                jobFinished(params, false)
            } catch (_: Throwable) {
                jobFinished(params, true)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
