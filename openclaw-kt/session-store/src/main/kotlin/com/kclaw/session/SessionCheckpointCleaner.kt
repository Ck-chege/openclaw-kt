package com.kclaw.session

/**
 * Deletes resumable agent-execution checkpoints for a session.
 *
 * Session transcript storage and agent execution persistence are separate
 * concerns. This interface lets the session store clear resumable state on
 * reset/delete without owning checkpoint recovery semantics.
 */
fun interface SessionCheckpointCleaner {
    suspend fun deleteCheckpoints(agentId: String)
}

