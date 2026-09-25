package com.azrael.pixivdumpsync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import java.util.concurrent.Executors

class SyncForegroundService : Service() {
    companion object {
        private const val CHANNEL_ID = "pixivdump_sync"
        private const val NOTIFICATION_ID = 41
    }

    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Pixiv sync", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification("Starting sync…"))
        executor.execute {
            try {
                SyncEngine(applicationContext).run { updateNotification(it) }
                updateNotification("Sync complete — ${SessionStore.lastSyncSummary(applicationContext)}")
            } catch (t: Throwable) {
                updateNotification("Sync failed: ${t.message}")
            } finally {
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification(text.take(180)))
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("PixivDump Sync")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }
}
