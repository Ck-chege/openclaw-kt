package com.kclaw.memory

import com.kclaw.memory.search.MemoryResult
import java.nio.file.Path

/**
 * Interface for the memory system, enabling testability.
 */
interface MemoryFacade {
    val isEnabled: Boolean
    suspend fun search(query: String, limit: Int = 5): List<MemoryResult>
    suspend fun index(paths: List<Path>? = null): com.kclaw.memory.indexing.IndexStats
    fun status(): MemoryStatus
}
