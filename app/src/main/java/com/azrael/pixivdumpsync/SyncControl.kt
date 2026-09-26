package com.azrael.pixivdumpsync

enum class SyncMode {
    LIVE,
    BACKFILL
}

object SyncControl {
    data class Snapshot(
        val running: Boolean,
        val paused: Boolean,
        val stopping: Boolean,
        val pendingLive: Boolean,
        val mode: SyncMode?,
        val message: String
    )

    private val lock = Any()

    @Volatile private var active = false
    @Volatile private var paused = false
    @Volatile private var stopRequested = false
    @Volatile private var pendingLive = false
    @Volatile private var mode: SyncMode? = null
    @Volatile private var message = "Idle"

    fun tryStart(newMode: SyncMode): Boolean = synchronized(lock) {
        if (active) {
            if (newMode == SyncMode.LIVE && !stopRequested) {
                pendingLive = true
            }
            return@synchronized false
        }

        active = true
        paused = false
        stopRequested = false
        mode = newMode
        message = if (newMode == SyncMode.LIVE) {
            "Checking for new works…"
        } else {
            "Backfill starting…"
        }
        true
    }

    fun checkpoint(): Boolean {
        while (paused && !stopRequested) {
            Thread.sleep(200L)
        }
        return !stopRequested
    }

    fun takePendingLive(): Boolean = synchronized(lock) {
        if (!active || stopRequested || !pendingLive) {
            false
        } else {
            pendingLive = false
            true
        }
    }

    fun completeOrContinueWithQueuedLive(finalMessage: String): Boolean =
        synchronized(lock) {
            if (active && !stopRequested && pendingLive) {
                pendingLive = false
                paused = false
                mode = SyncMode.LIVE
                message = "Queued Live Sync starting…"
                true
            } else {
                message = finalMessage
                paused = false
                stopRequested = false
                pendingLive = false
                mode = null
                active = false
                false
            }
        }

    fun setMode(value: SyncMode) {
        if (active) mode = value
    }

    fun pause() {
        if (active && !stopRequested) {
            paused = true
            message = "Paused"
        }
    }

    fun resume() {
        if (active && !stopRequested) {
            paused = false
            message = "Resuming…"
        }
    }

    fun stop() = synchronized(lock) {
        if (active) {
            stopRequested = true
            pendingLive = false
            paused = false
            message = "Stopping safely…"
        }
    }

    fun updateMessage(value: String) {
        if (active) message = value
    }

    fun forceFinish(finalMessage: String = "Idle") = synchronized(lock) {
        message = finalMessage
        paused = false
        stopRequested = false
        pendingLive = false
        mode = null
        active = false
    }

    fun snapshot(): Snapshot = Snapshot(
        running = active,
        paused = paused,
        stopping = stopRequested,
        pendingLive = pendingLive,
        mode = mode,
        message = message
    )
}
