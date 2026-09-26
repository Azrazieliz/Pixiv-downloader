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
        const val ACTION_START = "com.azrael.pixivdumpsync.START"
        const val ACTION_PAUSE = "com.azrael.pixivdumpsync.PAUSE"
        const val ACTION_RESUME = "com.azrael.pixivdumpsync.RESUME"
        const val ACTION_STOP = "com.azrael.pixivdumpsync.STOP"
        const val EXTRA_MODE = "sync_mode"
        const val EXTRA_SELECTED_ONLY = "selected_only"

        private const val CHANNEL_ID = "pixiflow_sync"
        private const val NOTIFICATION_ID = 41
    }

    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "PixiFlow sync",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                SyncControl.pause()
                updateNotification()
                return START_NOT_STICKY
            }
            ACTION_RESUME -> {
                SyncControl.resume()
                updateNotification()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                SyncControl.stop()
                updateNotification()
                return START_NOT_STICKY
            }
        }

        val mode = runCatching {
            SyncMode.valueOf(intent?.getStringExtra(EXTRA_MODE) ?: SyncMode.LIVE.name)
        }.getOrDefault(SyncMode.LIVE)
        val selectedOnly = intent?.getBooleanExtra(EXTRA_SELECTED_ONLY, false) ?: false

        startForeground(NOTIFICATION_ID, notification())

        executor.execute {
            try {
                SyncEngine(applicationContext).run(
                    mode = mode,
                    selectedOnly = selectedOnly
                ) {
                    updateNotification()
                }
            } catch (t: Throwable) {
                SyncControl.updateMessage("Sync failed: ${t.message}")
                updateNotification()
            } finally {
                updateNotification()
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

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification())
    }

    private fun notification(): Notification {
        val snapshot = SyncControl.snapshot()

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleAction = if (snapshot.paused) ACTION_RESUME else ACTION_PAUSE
        val toggleLabel = if (snapshot.paused) "Resume" else "Pause"
        val toggleIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, SyncForegroundService::class.java).setAction(toggleAction),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, SyncForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("PixiFlow")
            .setContentText(snapshot.message.take(180))
            .setContentIntent(open)
            .setOngoing(snapshot.running)
            .addAction(
                Notification.Action.Builder(
                    null,
                    toggleLabel,
                    toggleIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    null,
                    "Stop",
                    stopIntent
                ).build()
            )
            .build()
    }
}
