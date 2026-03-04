package com.openclaw.tools.policy

import com.openclaw.gateway.config.LoopDetectionConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import java.security.MessageDigest

private val logger = KotlinLogging.logger {}

/**
 * Detects when an agent gets stuck calling the same tools repeatedly.
 * Implements 4 detection strategies matching OpenClaw's tool-loop-detection.ts:
 *
 * 1. Generic repeat — same tool + same params called N times
 * 2. Poll no-progress — same tool + same params + same outcome N times
 * 3. Ping-pong — alternating between two tools N times
 * 4. Global circuit breaker — any single tool+params called 30+ times
 */
class ToolLoopDetector(
    private val config: LoopDetectionConfig = LoopDetectionConfig()
) {
    /** Severity of a loop detection. */
    enum class Severity { NONE, WARNING, CRITICAL }

    /** Result of checking for a loop. */
    data class LoopCheckResult(
        val severity: Severity,
        val detector: String = "",
        val message: String = ""
    ) {
        val isBlocked: Boolean get() = severity == Severity.CRITICAL
    }

    private data class CallRecord(
        val toolName: String,
        val paramsHash: String,
        val outcomeHash: String? = null
    )

    private val history = mutableListOf<CallRecord>()
    private val maxHistory = 30

    /**
     * Record a tool call and check for loops.
     * Call this after each tool execution with the tool name, params, and outcome.
     */
    fun recordAndCheck(
        toolName: String,
        paramsJson: String,
        outcomeText: String? = null
    ): LoopCheckResult {
        if (!config.enabled) return LoopCheckResult(Severity.NONE)

        val paramsHash = sha256(paramsJson)
        val outcomeHash = outcomeText?.let { sha256(it) }
        val record = CallRecord(toolName, paramsHash, outcomeHash)

        history.add(record)
        if (history.size > maxHistory) {
            history.removeFirst()
        }

        // Check detectors in order of specificity
        return checkCircuitBreaker(record)
            ?: checkPollNoProgress(record)
            ?: checkPingPong()
            ?: checkGenericRepeat(record)
            ?: LoopCheckResult(Severity.NONE)
    }

    /** Reset detector state (e.g. on new conversation turn). */
    fun reset() {
        history.clear()
    }

    // --- Detectors ---

    /**
     * Generic repeat: same tool + same params called repeatedly.
     */
    private fun checkGenericRepeat(current: CallRecord): LoopCheckResult? {
        val count = history.count { it.toolName == current.toolName && it.paramsHash == current.paramsHash }
        return when {
            count >= config.repeatCritical -> LoopCheckResult(
                Severity.CRITICAL,
                "generic_repeat",
                "Tool '${current.toolName}' called $count times with identical params (critical threshold: ${config.repeatCritical})"
            )
            count >= config.repeatWarning -> LoopCheckResult(
                Severity.WARNING,
                "generic_repeat",
                "Tool '${current.toolName}' called $count times with identical params (warning threshold: ${config.repeatWarning})"
            )
            else -> null
        }
    }

    /**
     * Poll no-progress: same tool + same params + same outcome (no progress).
     * Stricter threshold for known polling tools (process with poll/log actions).
     */
    private fun checkPollNoProgress(current: CallRecord): LoopCheckResult? {
        if (current.outcomeHash == null) return null

        val noProgressCount = history.count {
            it.toolName == current.toolName &&
                it.paramsHash == current.paramsHash &&
                it.outcomeHash == current.outcomeHash
        }

        return when {
            noProgressCount >= config.repeatCritical -> LoopCheckResult(
                Severity.CRITICAL,
                "poll_no_progress",
                "Tool '${current.toolName}' called $noProgressCount times with identical params and outcome — no progress"
            )
            noProgressCount >= config.repeatWarning -> LoopCheckResult(
                Severity.WARNING,
                "poll_no_progress",
                "Tool '${current.toolName}' called $noProgressCount times with identical outcome — potential no-progress loop"
            )
            else -> null
        }
    }

    /**
     * Ping-pong: alternating between two different tools.
     */
    private fun checkPingPong(): LoopCheckResult? {
        if (history.size < 4) return null

        val last = history.last()
        val secondLast = history[history.size - 2]
        if (last.toolName == secondLast.toolName) return null // not alternating

        var alternations = 0
        for (i in history.indices.reversed()) {
            val expected = if ((history.size - 1 - i) % 2 == 0) last.toolName else secondLast.toolName
            if (history[i].toolName != expected) break
            alternations++
        }

        return when {
            alternations >= config.repeatCritical -> LoopCheckResult(
                Severity.CRITICAL,
                "ping_pong",
                "Ping-pong between '${secondLast.toolName}' and '${last.toolName}': $alternations alternations"
            )
            alternations >= config.repeatWarning -> LoopCheckResult(
                Severity.WARNING,
                "ping_pong",
                "Possible ping-pong between '${secondLast.toolName}' and '${last.toolName}': $alternations alternations"
            )
            else -> null
        }
    }

    /**
     * Global circuit breaker: hard stop at 30 identical calls regardless of other checks.
     */
    private fun checkCircuitBreaker(current: CallRecord): LoopCheckResult? {
        val count = history.count { it.toolName == current.toolName && it.paramsHash == current.paramsHash }
        return if (count >= config.circuitBreaker) {
            LoopCheckResult(
                Severity.CRITICAL,
                "circuit_breaker",
                "Circuit breaker: tool '${current.toolName}' called $count times (limit: ${config.circuitBreaker})"
            )
        } else null
    }

    companion object {
        private fun sha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}
