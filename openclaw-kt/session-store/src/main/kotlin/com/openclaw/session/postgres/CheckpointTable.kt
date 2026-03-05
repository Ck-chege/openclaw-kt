package com.openclaw.session.postgres

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

/**
 * Postgres mirror of [com.openclaw.session.SessionMeta] for fast index lookups
 * and cross-instance consistency.
 */
object SessionMetadataTable : Table("session_metadata") {
    val sessionKey = text("session_key")
    val agentId = text("agent_id")
    val channel = text("channel").nullable()
    val modelId = text("model_id").nullable()
    val createdAt = timestamp("created_at")
    val lastMessage = timestamp("last_message").nullable()
    val messageCount = integer("message_count").default(0)
    val compactionCount = integer("compaction_count").default(0)
    val totalTokens = long("total_tokens").default(0)

    override val primaryKey = PrimaryKey(sessionKey)
}
