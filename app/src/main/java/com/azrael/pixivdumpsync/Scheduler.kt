package com.azrael.pixivdumpsync

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context

object Scheduler {
    private const val JOB_ID = 7751
    private const val FIFTEEN_MINUTES = 15L * 60L * 1000L

    fun ensure(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (!SessionStore.autoSync(context)) {
            scheduler.cancel(JOB_ID)
            return
        }
        if (scheduler.allPendingJobs.any { it.id == JOB_ID }) return

        val job = JobInfo.Builder(JOB_ID, ComponentName(context, SyncJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .setPeriodic(FIFTEEN_MINUTES)
            .build()
        scheduler.schedule(job)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        SessionStore.setAutoSync(context, enabled)
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (!enabled) scheduler.cancel(JOB_ID) else ensure(context)
    }
}
