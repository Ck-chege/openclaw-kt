package com.openclaw.session.composite

import com.openclaw.protocol.identifiers.SessionKey
import com.openclaw.session.SessionCheckpointCleaner
import com.openclaw.session.SessionMessage
import com.openclaw.session.SessionMeta
import com.openclaw.session.compaction.CompactableSessionStore
import com.openclaw.session.postgres.GraphCheckpoint
import com.openclaw.session.postgres.PostgresCheckpointProvider
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Dual-layer session store: JSONL (primary, always available) + Postgres (checkpoints, degradable).
 *
 * All CRUD and history operations delegate to the JSONL store. After writes, checkpoint
 * metadata is opportunistically mirrored to Postgres. If Postgres fails, the store
 * degrades to JSONL-only mode and logs a warning.
 *
 * @param jsonlStore Primary store for message transcripts and session metadata.
 * @param checkpointProvider Optional Postgres checkpoint provider. Null means JSONL-only.
 */
class CompositeSessionStore(
    private val jsonlStore: CompactableSessionStore,
    private val checkpointProvider: PostgresCheckpointProvider? = null,
    private val checkpointCleaner: SessionCheckpointCleaner? = checkpointProvider
) : CompactableSessionStore {

    @Volatile
    var isDegraded: Boolean = false
        private set

    // --- SessionStore delegation to JSONL ---

    override suspend fun createSession(key: SessionKey, agentId: String, channel: String?): SessionMeta {
        return jsonlStore.createSession(key, agentId, channel)
    }

    override suspend fun getSession(key: SessionKey): SessionMeta? {
        return jsonlStore.getSession(key)
    }

    override suspend fun listSessions(): List<SessionMeta> {
        return jsonlStore.listSessions()
    }

    override suspend fun appendMessage(key: SessionKey, message: SessionMessage) {
        jsonlStore.appendMessage(key, message)
    }

    override suspend fun getHistory(key: SessionKey, limit: Int?): List<SessionMessage> {
        return jsonlStore.getHistory(key, limit)
    }

    override suspend fun deleteSession(key: SessionKey) {
        jsonlStore.deleteSession(key)
        tryCheckpointCleanup { checkpointCleaner?.deleteCheckpoints(key.raw) }
    }

    override suspend fun resetSession(key: SessionKey) {
        jsonlStore.resetSession(key)
        tryCheckpointCleanup { checkpointCleaner?.deleteCheckpoints(key.raw) }
    }

    override suspend fun updateMeta(key: SessionKey, updater: (SessionMeta) -> SessionMeta) {
        jsonlStore.updateMeta(key, updater)
    }

    // --- CompactableSessionStore ---

    override suspend fun replaceWithCompaction(
        key: SessionKey,
        compactedCount: Int,
        summary: SessionMessage.CompactionSummary,
        remaining: List<SessionMessage>
    ) {
        jsonlStore.replaceWithCompaction(key, compactedCount, summary, remaining)
    }

    // --- Checkpoint operations ---

    suspend fun saveCheckpoint(checkpoint: GraphCheckpoint) {
        tryPostgres { checkpointProvider?.saveCheckpoint(checkpoint) }
    }

    suspend fun loadCheckpoint(key: SessionKey): GraphCheckpoint? {
        if (checkpointProvider == null) return null
        return tryPostgres { checkpointProvider.loadCheckpoint(key) }
    }

    /**
     * Compare JSONL message count with checkpoint's messageCount to detect drift.
     */
    suspend fun recoverFromCheckpoint(key: SessionKey): RecoveryResult {
        if (checkpointProvider == null) return RecoveryResult.NoCheckpoint

        val checkpoint = try {
            checkpointProvider.loadCheckpoint(key)
        } catch (e: Exception) {
            return RecoveryResult.Failed(e)
        } ?: return RecoveryResult.NoCheckpoint

        val meta = jsonlStore.getSession(key) ?: return RecoveryResult.Failed(
            IllegalStateException("Session ${key.raw} not found in JSONL store")
        )

        return if (meta.messageCount >= checkpoint.messageCount) {
            RecoveryResult.AlreadyCurrent(
                jsonlMessageCount = meta.messageCount,
                checkpointMessageCount = checkpoint.messageCount
            )
        } else {
            RecoveryResult.Recovered(
                checkpointMessageCount = checkpoint.messageCount,
                jsonlMessageCount = meta.messageCount
            )
        }
    }

    // --- Internal helpers ---

    private suspend fun <T> tryPostgres(block: suspend () -> T): T? {
        if (checkpointProvider == null) return null
        return try {
            block()
        } catch (e: Exception) {
            if (!isDegraded) {
                isDegraded = true
                logger.warn(e) { "Postgres unavailable — degrading to JSONL-only mode" }
            }
            null
        }
    }

    private suspend fun tryCheckpointCleanup(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            if (!isDegraded) {
                isDegraded = true
                logger.warn(e) { "Checkpoint cleanup unavailable — resumable state may remain until storage recovers" }
            }
        }
    }
}
