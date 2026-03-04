package com.openclaw.session.compaction

import com.openclaw.protocol.identifiers.SessionKey
import com.openclaw.session.SessionMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Clock
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Outcome of a compaction attempt.
 */
sealed class CompactionOutcome {
    data class Success(
        val messagesCompacted: Int,
        val tokensBefore: Int,
        val tokensAfter: Int
    ) : CompactionOutcome()

    data class Skipped(val reason: String) : CompactionOutcome()
    data class Failed(val cause: Throwable) : CompactionOutcome()
}

/**
 * Orchestrates session compaction: evaluates whether compaction is needed,
 * delegates summarization, and performs the atomic transcript rewrite.
 */
class CompactionEngine(
    private val store: CompactableSessionStore,
    private val summarizer: MessageSummarizer,
    private val config: CompactionConfig = CompactionConfig()
) {

    /**
     * Estimate the token count of a list of messages using chars/4 heuristic.
     */
    fun estimateTokens(messages: List<SessionMessage>): Int {
        if (messages.isEmpty()) return 0
        return messages.sumOf { messageTextLength(it) } / 4
    }

    /**
     * Whether the estimated token count exceeds the trigger threshold.
     */
    fun shouldCompact(estimatedTokens: Int): Boolean {
        return estimatedTokens >= config.triggerThreshold
    }

    /**
     * Run compaction on a session.
     *
     * 1. Load history
     * 2. Estimate tokens — skip if below threshold
     * 3. Check minimum message count — skip if too few
     * 4. Select oldest messages to compact
     * 5. Summarize via [MessageSummarizer]
     * 6. Atomic rewrite via [CompactableSessionStore.replaceWithCompaction]
     */
    suspend fun compact(key: SessionKey): CompactionOutcome {
        val messages = store.getHistory(key)

        val totalTokens = estimateTokens(messages)
        if (!shouldCompact(totalTokens)) {
            return CompactionOutcome.Skipped("Token count $totalTokens below threshold ${config.triggerThreshold}")
        }

        // Count non-summary messages
        val nonSummaryMessages = messages.filter { it !is SessionMessage.CompactionSummary }
        if (nonSummaryMessages.size < config.minMessagesToCompact) {
            return CompactionOutcome.Skipped(
                "Only ${nonSummaryMessages.size} non-summary messages (minimum: ${config.minMessagesToCompact})"
            )
        }

        // Determine how many recent messages to keep. Budget: half of the trigger threshold,
        // so the post-compaction state (summary + remaining) is well under the limit.
        val keepTokenBudget = config.triggerThreshold / 2
        var remainingTokens = 0
        var keepFromIndex = messages.size
        for (i in messages.indices.reversed()) {
            val msgTokens = messageTextLength(messages[i]) / 4
            if (remainingTokens + msgTokens > keepTokenBudget) break
            remainingTokens += msgTokens
            keepFromIndex = i
        }

        // Ensure we compact at least some messages
        if (keepFromIndex <= 0) {
            return CompactionOutcome.Skipped("No messages to compact after calculating keep threshold")
        }

        // Also skip compaction of CompactionSummary-only prefixes — only compact if
        // there are actual content messages in the old portion
        val oldMessages = messages.subList(0, keepFromIndex)
        val oldNonSummary = oldMessages.filter { it !is SessionMessage.CompactionSummary }
        if (oldNonSummary.isEmpty()) {
            return CompactionOutcome.Skipped("Old messages contain only compaction summaries")
        }

        val remaining = messages.subList(keepFromIndex, messages.size)

        // Summarize
        val result = try {
            summarizer.summarize(oldMessages)
        } catch (e: Exception) {
            logger.error(e) { "Summarization failed for session ${key.raw}" }
            return CompactionOutcome.Failed(e)
        }

        val tokensBefore = totalTokens
        val tokensAfter = estimateTokens(remaining) + result.tokensUsed

        val summary = SessionMessage.CompactionSummary(
            id = UUID.randomUUID().toString(),
            timestamp = Clock.System.now(),
            summary = result.summary,
            messagesCompacted = oldMessages.size,
            tokensBefore = tokensBefore,
            tokensAfter = tokensAfter
        )

        store.replaceWithCompaction(key, oldMessages.size, summary, remaining)

        logger.info {
            "Compacted session ${key.raw}: ${oldMessages.size} messages, " +
                "$tokensBefore → $tokensAfter tokens"
        }

        return CompactionOutcome.Success(
            messagesCompacted = oldMessages.size,
            tokensBefore = tokensBefore,
            tokensAfter = tokensAfter
        )
    }

    private fun messageTextLength(message: SessionMessage): Int = when (message) {
        is SessionMessage.UserMessage -> message.text.length
        is SessionMessage.AssistantMessage -> message.text.length
        is SessionMessage.ToolCall -> message.args.toString().length
        is SessionMessage.ToolResult -> message.result.length
        is SessionMessage.SystemEvent -> (message.data?.toString()?.length ?: 0) + message.event.length
        is SessionMessage.CompactionSummary -> message.summary.length
    }
}
