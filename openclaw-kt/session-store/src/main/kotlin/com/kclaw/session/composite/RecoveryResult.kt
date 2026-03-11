package com.kclaw.session.composite

/**
 * Outcome of attempting to recover a session from its Postgres checkpoint.
 */
sealed class RecoveryResult {
    /** Successfully recovered: JSONL state was behind checkpoint and has been noted. */
    data class Recovered(val checkpointMessageCount: Int, val jsonlMessageCount: Int) : RecoveryResult()

    /** No checkpoint exists for this session. */
    data object NoCheckpoint : RecoveryResult()

    /** JSONL is already at or ahead of the checkpoint — no recovery needed. */
    data class AlreadyCurrent(val jsonlMessageCount: Int, val checkpointMessageCount: Int) : RecoveryResult()

    /** Recovery failed due to an error. */
    data class Failed(val cause: Throwable) : RecoveryResult()
}
