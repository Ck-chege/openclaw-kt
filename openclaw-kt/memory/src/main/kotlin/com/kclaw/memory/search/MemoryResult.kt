package com.kclaw.memory.search

import com.kclaw.memory.chunking.Chunk
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
