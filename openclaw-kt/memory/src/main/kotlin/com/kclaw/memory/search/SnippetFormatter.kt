package com.kclaw.memory.search

import com.kclaw.memory.chunking.Chunk

/**
 * Formats chunks into human-readable snippets for display.
 */
object SnippetFormatter {

    private const val MAX_SNIPPET_LENGTH = 700

    /**
     * Format a chunk as: `[sourcePath:L1-L42] heading\ncontent...`
     * Truncated to ~700 characters.
     */
    fun format(chunk: Chunk): String {
        val header = buildString {
            append("[${chunk.sourcePath}:L${chunk.startLine}-L${chunk.endLine}]")
            if (chunk.headingContext != null) {
                append(" ${chunk.headingContext}")
            }
        }

        val content = chunk.content.trim()
        val full = "$header\n$content"

        return if (full.length > MAX_SNIPPET_LENGTH) {
            full.take(MAX_SNIPPET_LENGTH - 3) + "..."
        } else {
            full
        }
    }
}
