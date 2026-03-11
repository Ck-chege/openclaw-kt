package com.kclaw.memory.chunking

import kotlinx.serialization.Serializable

/**
 * A chunk of text extracted from a source document for embedding and search.
 */
@Serializable
data class Chunk(
    /** Relative path to the source file. */
    val sourcePath: String,
    /** Zero-based index of this chunk within the source file. */
    val chunkIndex: Int,
    /** The text content of this chunk. */
    val content: String,
    /** Approximate token count (chars / 4). */
    val tokenCount: Int,
    /** 1-based start line in source file. */
    val startLine: Int,
    /** 1-based end line in source file. */
    val endLine: Int,
    /** Nearest ancestor heading, if any. */
    val headingContext: String? = null
)
