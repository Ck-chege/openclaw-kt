package com.kclaw.session

import com.kclaw.protocol.identifiers.SessionKey

/**
 * Abstraction for session persistence.
 *
 * Implementations must be safe for concurrent use within a single JVM;
 * per-session write serialization is handled by [SessionWriteLock].
 */
interface SessionStore {

    /** Create a new session and return its metadata. Throws if the key already exists. */
    suspend fun createSession(key: SessionKey, agentId: String, channel: String? = null): SessionMeta

    /** Retrieve metadata for an existing session, or null if not found. */
    suspend fun getSession(key: SessionKey): SessionMeta?

    /** List metadata for every known session. */
    suspend fun listSessions(): List<SessionMeta>

    /** Append a message to the session transcript. */
    suspend fun appendMessage(key: SessionKey, message: SessionMessage)

    /** Read the full (or tail-limited) history for a session. */
    suspend fun getHistory(key: SessionKey, limit: Int? = null): List<SessionMessage>

    /** Permanently delete a session and its transcript. */
    suspend fun deleteSession(key: SessionKey)

    /** Clear the transcript and reset counters, keeping the session entry. */
    suspend fun resetSession(key: SessionKey)

    /** Atomically update session metadata. */
    suspend fun updateMeta(key: SessionKey, updater: (SessionMeta) -> SessionMeta)
}
