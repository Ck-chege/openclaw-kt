package com.openclaw.session.compaction

import com.openclaw.protocol.identifiers.SessionKey
import com.openclaw.session.SessionMessage
import com.openclaw.session.SessionStore

/**
 * A [SessionStore] that supports compaction by archiving the previous transcript
 * and promoting a new active transcript containing a [SessionMessage.CompactionSummary]
 * plus the remaining recent messages.
 */
interface CompactableSessionStore : SessionStore {

    /**
     * Archive the current active transcript and replace it with a new active
     * transcript containing [summary] + [remaining] messages.
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
