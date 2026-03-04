package com.openclaw.session.compaction

import com.openclaw.session.SessionMessage

/**
 * Result of summarizing a batch of messages.
 */
data class CompactionResult(
    val summary: String,
    val tokensUsed: Int
)

/**
 * Pluggable summarizer: production uses LLM, tests use mock.
 */
fun interface MessageSummarizer {
    suspend fun summarize(messages: List<SessionMessage>): CompactionResult
}
