package com.openclaw.session.compaction

import com.openclaw.protocol.identifiers.SessionKey
import com.openclaw.session.SessionMessage
import com.openclaw.session.SessionStore

/**
 * A [SessionStore] that supports atomic compaction: replacing the oldest messages
 * with a [SessionMessage.CompactionSummary] in a single atomic operation.
 */
interface CompactableSessionStore : SessionStore {

    /**
     * Atomically replace the transcript with [summary] + [remaining] messages.
     *
     * @param key Session to compact
     * @param compactedCount Number of old messages being replaced
     * @param summary The compaction summary message to prepend
     * @param remaining Messages to keep after the summary
     */
    suspend fun replaceWithCompaction(
        key: SessionKey,
        compactedCount: Int,
        summary: SessionMessage.CompactionSummary,
        remaining: List<SessionMessage>
    )
}
