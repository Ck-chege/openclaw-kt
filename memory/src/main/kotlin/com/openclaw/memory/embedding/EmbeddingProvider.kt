package com.openclaw.memory.embedding

import kotlin.math.sqrt

/**
 * Embedding provider interface for converting text to dense vector representations.
 */
interface EmbeddingProvider {
    /** Embed a batch of texts into dense vectors. */
    suspend fun embed(texts: List<String>): List<FloatArray>

    /** Dimensionality of the embedding vectors produced by this provider. */
    val dimensions: Int

    /** Human-readable name (e.g., "openai", "ollama"). */
    val providerName: String
}

/** Dot product of two float arrays. */
fun FloatArray.dot(other: FloatArray): Float {
    require(size == other.size) { "Vectors must have the same dimensions: $size vs ${other.size}" }
    var sum = 0f
    for (i in indices) {
        sum += this[i] * other[i]
    }
    return sum
}

/** Euclidean norm (L2) of the vector. */
fun FloatArray.norm(): Float {
    var sum = 0f
    for (v in this) {
        sum += v * v
    }
    return sqrt(sum)
}

/** Cosine similarity between two float arrays, in range [-1, 1]. */
fun FloatArray.cosineSimilarity(other: FloatArray): Float {
    val dotProduct = this.dot(other)
    val normA = this.norm()
    val normB = other.norm()
    if (normA == 0f || normB == 0f) return 0f
    return dotProduct / (normA * normB)
}
