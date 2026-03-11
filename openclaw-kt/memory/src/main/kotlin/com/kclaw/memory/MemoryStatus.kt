package com.kclaw.memory

import kotlinx.serialization.Serializable

/**
 * Status snapshot of the memory system.
 */
@Serializable
data class MemoryStatus(
    val enabled: Boolean,
    val indexed: Boolean,
    val totalChunks: Int,
    val totalFiles: Int,
    val embeddingProvider: String,
    val embeddingDimensions: Int,
    val cacheSize: Int,
    val lastIndexedAt: String? = null
)
