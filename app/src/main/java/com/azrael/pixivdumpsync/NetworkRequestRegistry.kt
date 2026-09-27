package com.azrael.pixivdumpsync

import java.io.IOException
import java.net.HttpURLConnection
import java.util.Collections
import java.util.IdentityHashMap

object NetworkRequestRegistry {
    private val active = Collections.newSetFromMap(
        IdentityHashMap<HttpURLConnection, Boolean>()
    )

    @Synchronized
    fun register(connection: HttpURLConnection) {
        active.add(connection)
    }

    @Synchronized
    fun unregister(connection: HttpURLConnection) {
        active.remove(connection)
    }

    fun cancelAll() {
        val snapshot = synchronized(this) {
            active.toList().also { active.clear() }
        }
        snapshot.forEach { connection ->
            runCatching { connection.disconnect() }
        }
    }
}

class SyncCancelledException : IOException("Sync stopped")
