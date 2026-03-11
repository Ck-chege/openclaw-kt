package com.kclaw.session.postgres

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.json.jsonb

/**
 * Postgres table for agent-graph checkpoints.
 *
 * Used for crash recovery: after a restart the orchestrator can resume
 * from the last persisted graph node / state rather than replaying the
 * entire JSONL transcript.
 */
object CheckpointTable : Table("checkpoints") {
    val sessionKey = text("session_key")
    val graphNode = text("graph_node")
    val graphState = jsonb<String>("graph_state", { it }, { it })
    val messageCount = integer("message_count")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(sessionKey)
}