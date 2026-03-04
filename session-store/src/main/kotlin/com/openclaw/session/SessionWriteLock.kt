package com.openclaw.session

import com.openclaw.protocol.identifiers.SessionKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-session mutex to prevent concurrent writes to the same session transcript.
 *
 * Mutexes are stored in a [ConcurrentHashMap] keyed by the raw session key string,
 * so two [SessionKey] instances with the same [SessionKey.raw] share a lock.
 */
class SessionWriteLock {
    private val locks = ConcurrentHashMap<String, Mutex>()

    /**
     * Acquire the per-session lock, execute [block], then release.
     */
    suspend fun <T> withLock(key: SessionKey, block: suspend () -> T): T {
        val mutex = locks.getOrPut(key.raw) { Mutex() }
        return mutex.withLock { block() }
    }
}
