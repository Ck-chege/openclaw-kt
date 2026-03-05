package com.openclaw.session.postgres

import com.openclaw.protocol.identifiers.SessionKey
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.upsert

/**
 * Snapshot of agent-graph state for crash recovery.
 */
data class GraphCheckpoint(
    val sessionKey: SessionKey,
    val graphNode: String,
    val graphState: String,
    val messageCount: Int
)

/**
 * Postgres-backed checkpoint storage.
 *
 * Requires a running Postgres instance with the [CheckpointTable] schema created
 * (typically via Exposed's `SchemaUtils.create`).
 *
 * @param db Optional [Database] instance for testing. When null, uses the default connection.
 */
open class PostgresCheckpointProvider(private val db: Database? = null) {

    /**
     * Upsert a checkpoint for the given session.
     */
    open suspend fun saveCheckpoint(checkpoint: GraphCheckpoint): Unit = newSuspendedTransaction(db = db) {
        CheckpointTable.upsert(CheckpointTable.sessionKey) {
            it[sessionKey] = checkpoint.sessionKey.raw
            it[graphNode] = checkpoint.graphNode
            it[graphState] = checkpoint.graphState
            it[messageCount] = checkpoint.messageCount
            it[updatedAt] = Clock.System.now()
        }
    }

    /**
     * Load the most recent checkpoint for a session, or null if none exists.
     */
    open suspend fun loadCheckpoint(key: SessionKey): GraphCheckpoint? = newSuspendedTransaction(db = db) {
        CheckpointTable.selectAll()
            .where { CheckpointTable.sessionKey eq key.raw }
            .firstOrNull()
            ?.let { row ->
                GraphCheckpoint(
                    sessionKey = key,
                    graphNode = row[CheckpointTable.graphNode],
                    graphState = row[CheckpointTable.graphState],
                    messageCount = row[CheckpointTable.messageCount]
                )
            }
    }

    /**
     * Delete all checkpoints for a session (e.g. on session deletion or reset).
     */
    open suspend fun deleteCheckpoints(key: SessionKey): Unit = newSuspendedTransaction(db = db) {
        CheckpointTable.deleteWhere { sessionKey eq key.raw }
    }
}
