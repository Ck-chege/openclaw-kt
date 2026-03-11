package com.kclaw.agent.runtime

import com.kclaw.protocol.identifiers.SessionKey
import com.kclaw.session.SessionStore
import com.kclaw.session.compaction.CompactionEngine
import com.kclaw.session.compaction.CompactionOutcome
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Outcome of a compaction trigger check.
 */
sealed class CompactionTriggerResult {
    /** Token count was below threshold — no compaction needed. */
    data object NotNeeded : CompactionTriggerResult()

    /** Compaction was performed (optionally preceded by a memory flush). */
    data class Compacted(
        val flushed: Boolean,
        val outcome: CompactionOutcome
    ) : CompactionTriggerResult()

    /** Memory flush failed; compaction was not attempted. */
    data class FlushFailed(val cause: Throwable) : CompactionTriggerResult()
}

/**
 * Coordinates the check → flush → compact sequence after each agent run.
 *
 * 1. Load session history and estimate tokens
 * 2. If above threshold: run memory flush (if runner provided)
 * 3. Run compaction via [CompactionEngine]
 *
 * Never throws — all failures are returned as [CompactionTriggerResult].
 */
open class CompactionTrigger(
    private val compactionEngine: CompactionEngine,
    private val flushRunner: MemoryFlushRunner?,
    private val sessionStore: SessionStore
) {

    /**
     * Check whether compaction is needed for [sessionKey] and perform it if so.
     */
    open suspend fun checkAndCompact(sessionKey: SessionKey): CompactionTriggerResult {
        return try {
            val history = sessionStore.getHistory(sessionKey)
            val estimatedTokens = compactionEngine.estimateTokens(history)

            if (!compactionEngine.shouldCompact(estimatedTokens)) {
                return CompactionTriggerResult.NotNeeded
            }

            logger.info {
                "Compaction threshold reached for session ${sessionKey.raw}: " +
                    "$estimatedTokens estimated tokens"
            }

            // Run memory flush before compaction
            var flushed = false
            if (flushRunner != null) {
                val flushResult = flushRunner.runFlush(sessionKey, history)
                when (flushResult) {
                    is FlushResult.Flushed -> {
                        flushed = true
                        logger.info { "Memory flush completed before compaction" }
                    }
                    is FlushResult.Skipped -> {
                        logger.info { "Memory flush skipped (nothing to store)" }
                    }
                    is FlushResult.Failed -> {
                        logger.warn(flushResult.cause) {
                            "Memory flush failed, proceeding with compaction anyway"
                        }
                    }
                }
            }

            // Run compaction
            val outcome = compactionEngine.compact(sessionKey)
            logger.info { "Compaction result for ${sessionKey.raw}: $outcome" }

            CompactionTriggerResult.Compacted(flushed = flushed, outcome = outcome)
        } catch (e: Exception) {
            logger.error(e) { "Compaction trigger failed for session ${sessionKey.raw}" }
            CompactionTriggerResult.FlushFailed(e)
        }
    }
}
