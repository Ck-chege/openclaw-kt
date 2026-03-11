package com.kclaw.agent.prompt

/**
 * Truncation utilities for prompt content.
 * Keeps first half and last half of content with a marker in between.
 */
object TextTruncation {

    private const val TRUNCATION_MARKER = "\n\n[... content truncated — %d chars removed ...]\n\n"

    /**
     * Truncate text to [maxChars] if it exceeds the limit.
     * Keeps the first half and last half with a marker in between.
     * Returns original text unchanged if within limit or empty.
     */
    fun truncateWithMarkers(text: String, maxChars: Int): String {
        if (text.length <= maxChars || maxChars <= 0) return text

        val marker = TRUNCATION_MARKER.format(text.length - maxChars)
        val available = maxChars - marker.length
        if (available <= 0) return marker.take(maxChars)

        val headSize = available / 2
        val tailSize = available - headSize

        return text.take(headSize) + marker + text.takeLast(tailSize)
    }
}
