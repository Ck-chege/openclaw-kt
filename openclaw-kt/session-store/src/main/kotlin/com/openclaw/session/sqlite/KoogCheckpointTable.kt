package com.openclaw.session.sqlite

import org.jetbrains.exposed.sql.Table

object KoogCheckpointTable : Table("koog_checkpoints") {
    val checkpointId = text("checkpoint_id")
    val agentId = text("agent_id")
    val createdAtEpochMs = long("created_at_epoch_ms")
    val version = long("version")
    val nodePath = text("node_path")
    val isTombstone = bool("is_tombstone")
    val payload = text("payload")

    override val primaryKey = PrimaryKey(checkpointId)

    init {
        index("idx_koog_checkpoints_agent_created", false, agentId, createdAtEpochMs)
    }
}
