package com.openclaw.memory.search

import com.openclaw.memory.chunking.Chunk
import kotlinx.serialization.Serializable

/**
 * A search result from the memory system.
 */
@Serializable
data class MemoryResult(
    val chunk: Chunk,
    val score: Float,
    val snippet: String,
    val source: String
)
