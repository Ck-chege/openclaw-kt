package com.openclaw.memory.indexing

import com.openclaw.memory.chunking.MarkdownChunker
import com.openclaw.memory.embedding.EmbeddingCache
import com.openclaw.memory.embedding.EmbeddingProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.*

private val logger = KotlinLogging.logger {}

@Serializable
data class IndexStats(
    val filesScanned: Int,
    val chunksCreated: Int,
    val embeddingsComputed: Int,
    val embeddingsCached: Int,
    val durationMs: Long
)

/**
 * Manages scanning, chunking, and indexing of workspace files.
 */
class MemoryIndexManager(
    private val embeddingProvider: EmbeddingProvider,
    private val embeddingCache: EmbeddingCache,
    private val vectorStore: VectorStore,
    private val chunker: MarkdownChunker,
    private val workspacePath: Path
) {
    companion object {
        private const val BATCH_SIZE = 100
        private val INDEXABLE_EXTENSIONS = setOf("md", "txt")
        private val HIDDEN_DIR_PREFIX = "."
    }

    /**
     * Scan a directory tree for indexable files (.md, .txt).
     * Skips hidden directories and `.memory/`.
     */
    fun scanFiles(root: Path): List<Path> {
        if (!root.exists()) return emptyList()
        return root.toFile().walkTopDown()
            .onEnter { dir ->
                val name = dir.name
                !name.startsWith(HIDDEN_DIR_PREFIX) && name != ".memory"
            }
            .filter { file ->
                file.isFile && file.extension.lowercase() in INDEXABLE_EXTENSIONS
            }
            .map { it.toPath() }
            .toList()
    }

    /**
     * Index the given files: scan > chunk > check cache > embed uncached > store.
     */
    suspend fun index(paths: List<Path>): IndexStats {
        val startTime = System.currentTimeMillis()
        var embeddingsComputed = 0
        var embeddingsCached = 0
        var totalChunks = 0

        for (path in paths) {
            val relativePath = workspacePath.relativize(path).toString()
            val content = try {
                path.readText()
            } catch (e: Exception) {
                logger.warn { "Failed to read $path: ${e.message}" }
                continue
            }

            // Remove old entries for this file
            vectorStore.removeBySource(relativePath)

            val chunks = chunker.chunk(content, relativePath)
            if (chunks.isEmpty()) continue

            totalChunks += chunks.size

            // Check cache for each chunk, collect uncached
            val cachedEmbeddings = mutableMapOf<Int, FloatArray>()
            val uncachedIndices = mutableListOf<Int>()

            for ((i, chunk) in chunks.withIndex()) {
                val hash = embeddingCache.contentHash(chunk.content)
                val cached = embeddingCache.get(hash)
                if (cached != null) {
                    cachedEmbeddings[i] = cached
                    embeddingsCached++
                } else {
                    uncachedIndices.add(i)
                }
            }

            // Batch embed uncached chunks
            if (uncachedIndices.isNotEmpty()) {
                val textsToEmbed = uncachedIndices.map { chunks[it].content }
                for (batchStart in textsToEmbed.indices step BATCH_SIZE) {
                    val batchEnd = minOf(batchStart + BATCH_SIZE, textsToEmbed.size)
                    val batchTexts = textsToEmbed.subList(batchStart, batchEnd)
                    val batchEmbeddings = embeddingProvider.embed(batchTexts)

                    for ((j, embedding) in batchEmbeddings.withIndex()) {
                        val globalIdx = uncachedIndices[batchStart + j]
                        cachedEmbeddings[globalIdx] = embedding
                        embeddingCache.put(
                            embeddingCache.contentHash(chunks[globalIdx].content),
                            embedding
                        )
                        embeddingsComputed++
                    }
                }
            }

            // Store all entries
            val entries = chunks.mapIndexed { i, chunk ->
                val embedding = cachedEmbeddings[i]!!
                StoredEntry(chunk, embedding.toList())
            }
            vectorStore.addAll(entries)
        }

        val durationMs = System.currentTimeMillis() - startTime
        val stats = IndexStats(
            filesScanned = paths.size,
            chunksCreated = totalChunks,
            embeddingsComputed = embeddingsComputed,
            embeddingsCached = embeddingsCached,
            durationMs = durationMs
        )
        logger.info { "Indexing complete: $stats" }
        return stats
    }
}
