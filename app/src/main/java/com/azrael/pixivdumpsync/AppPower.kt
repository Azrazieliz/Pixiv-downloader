package com.azrael.pixivdumpsync

import android.app.job.JobScheduler
import android.content.Context

/** One persistent master switch, independent of all artist and site preferences.
 * OFF prevents scheduled and manual background networking, preserving all queues. */
object AppPower {
    private const val NAME="asterion_master_power"
    fun enabled(context:Context):Boolean =
        context.getSharedPreferences(NAME,Context.MODE_PRIVATE).getBoolean("enabled",true)

    fun set(context:Context,value:Boolean) {
        context.getSharedPreferences(NAME,Context.MODE_PRIVATE).edit()
            .putBoolean("enabled",value).commit()
        if(value){
            Scheduler.ensure(context)
            XPhotoScheduler.ensure(context)
            com.azrael.galleryflow.Scheduler.ensure(context)
            com.asterion.videoflow.Scheduling.ensure(context)
        }else{
            Scheduler.cancel(context)
            XPhotoScheduler.cancel(context)
            com.azrael.galleryflow.Scheduler.cancel(context)
            com.asterion.videoflow.Scheduling.cancel(context)
            com.azrael.galleryflow.SyncControl.stop()
            SyncControl.stop()
            // A running upload has its own stop flag and is cooperatively cancelled.
            runCatching {
                context.startService(android.content.Intent(context,NduUploadService::class.java)
                    .setAction(NduUploadService.ACTION_STOP))
            }
            context.getSystemService(JobScheduler::class.java)?.let { jobs->
                // Work scheduled by components must not restart the app while OFF.
                for(id in listOf(7751,7752,9811,28914,42681))jobs.cancel(id)
            }
        }
    }
}
