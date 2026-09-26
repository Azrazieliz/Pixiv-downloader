package com.azrael.pixivdumpsync

import java.util.concurrent.atomic.AtomicBoolean

enum class SyncMode {
    LIVE,
    BACKFILL
}

object SyncControl {
    data class Snapshot(
        val running: Boolean,
        val paused: Boolean,
        val stopping: Boolean,
        val mode: SyncMode?,
        val message: String
    )

    private val active = AtomicBoolean(false)

    @Volatile private var paused = false
    @Volatile private var stopRequested = false
    @Volatile private var mode: SyncMode? = null
    @Volatile private var message = "Idle"

    fun tryStart(newMode: SyncMode): Boolean {
        if (!active.compareAndSet(false, true)) return false
        paused = false
        stopRequested = false
        mode = newMode
        message = if (newMode == SyncMode.LIVE) "Checking for new works…" else "Backfill starting…"
        return true
    }

    fun checkpoint(): Boolean {
        while (paused && !stopRequested) {
            Thread.sleep(200L)
        }
        return !stopRequested
    }

    fun pause() {
        if (active.get() && !stopRequested) {
            paused = true
            message = "Paused"
        }
    }

    fun resume() {
        if (active.get() && !stopRequested) {
            paused = false
            message = "Resuming…"
        }
    }

    fun stop() {
        if (active.get()) {
            stopRequested = true
            paused = false
            message = "Stopping safely…"
        }
    }

    fun updateMessage(value: String) {
        if (active.get()) message = value
    }

    fun finish(finalMessage: String = "Idle") {
        message = finalMessage
        paused = false
        stopRequested = false
        mode = null
        active.set(false)
    }

    fun snapshot(): Snapshot = Snapshot(
        running = active.get(),
        paused = paused,
        stopping = stopRequested,
        mode = mode,
        message = message
    )
}
