package com.kclaw.session.compaction

/**
 * Thresholds controlling when and how compaction runs.
 *
 * @param maxContextTokens Maximum context window size (e.g. 200_000 for Claude)
 * @param triggerRatio Fraction of maxContextTokens that triggers compaction (default 0.80)
 * @param targetSummaryTokens Target size for the compaction summary (default 5_000)
 * @param minMessagesToCompact Minimum non-summary messages required before compaction (default 4)
 */
data class CompactionConfig(
    val maxContextTokens: Int = 200_000,
    val triggerRatio: Double = 0.80,
    val targetSummaryTokens: Int = 5_000,
    val minMessagesToCompact: Int = 4
) {
    /** Absolute token count that triggers compaction. */
    val triggerThreshold: Int get() = (maxContextTokens * triggerRatio).toInt()
}
