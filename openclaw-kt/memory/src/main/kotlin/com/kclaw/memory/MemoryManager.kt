package com.kclaw.memory

import com.kclaw.gateway.config.MemoryConfig
import com.kclaw.memory.chunking.MarkdownChunker
import com.kclaw.memory.embedding.EmbeddingCache
import com.kclaw.memory.embedding.EmbeddingProvider
import com.kclaw.memory.embedding.OllamaEmbeddings
import com.kclaw.memory.embedding.OpenAIEmbeddings
import com.kclaw.memory.indexing.IndexStats
import com.kclaw.memory.indexing.MemoryIndexManager
import com.kclaw.memory.indexing.VectorStore
import com.kclaw.memory.search.*
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path
import kotlin.io.path.createDirectories

private val logger = KotlinLogging.logger {}

/**
 * Facade for the memory system.
 * Creates all components from config and provides a simple API for indexing and searching.
 */
class MemoryManager(
    val config: MemoryConfig,
    private val workspacePath: Path,
    apiKeyResolver: (String) -> String?
) : MemoryFacade {
    private val memoryDir: Path = workspacePath.resolve(".memory")
    private val cacheDir: Path = memoryDir.resolve("cache")
    private val indexPath: Path = memoryDir.resolve("index.json")

    val embeddingProvider: EmbeddingProvider
    private val embeddingCache: EmbeddingCache
    private val vectorStore: VectorStore
    private val indexManager: MemoryIndexManager
    private val hybridSearch: HybridSearch

    override val isEnabled: Boolean = config.enabled

    private var lastIndexedAt: String? = null

    init {
        memoryDir.createDirectories()

        embeddingProvider = when (config.provider.lowercase()) {
            "openai" -> {
                val apiKey = apiKeyResolver("OPENAI_API_KEY")
                    ?: throw IllegalStateException("OPENAI_API_KEY is required for OpenAI embeddings")
                OpenAIEmbeddings(apiKey = apiKey, model = config.model)
            }
            "ollama" -> {
                OllamaEmbeddings(
                    model = config.model.ifBlank { "nomic-embed-text" },
                    baseUrl = config.ollamaBaseUrl
                )
            }
            else -> throw IllegalArgumentException("Unknown embedding provider: ${config.provider}")
        }

        embeddingCache = EmbeddingCache(cacheDir)
        vectorStore = VectorStore()

        val chunker = MarkdownChunker(
            chunkSize = config.chunkSize,
            overlap = config.chunkOverlap
        )

        indexManager = MemoryIndexManager(
            embeddingProvider = embeddingProvider,
            embeddingCache = embeddingCache,
            vectorStore = vectorStore,
            chunker = chunker,
            workspacePath = workspacePath
        )

        val vectorSearch = VectorSearch(vectorStore, embeddingProvider)
        val bm25Search = BM25Search(vectorStore)
        hybridSearch = HybridSearch(vectorSearch, bm25Search)

        // Try to load existing index from disk
        if (vectorStore.loadFromDisk(indexPath)) {
            logger.info { "Loaded existing memory index: ${vectorStore.size()} entries" }
        }
    }

    /**
     * Scan and index workspace files.
     */
    override suspend fun index(paths: List<Path>?): IndexStats {
        val filesToIndex = paths ?: indexManager.scanFiles(workspacePath)
        val stats = indexManager.index(filesToIndex)
        vectorStore.saveToDisk(indexPath)
        lastIndexedAt = java.time.Instant.now().toString()
        return stats
    }

    /**
     * Hybrid search across indexed memory.
     */
    override suspend fun search(query: String, limit: Int): List<MemoryResult> {
        return hybridSearch.search(query, limit)
    }

    /**
     * Get current status of the memory system.
     */
    override fun status(): MemoryStatus {
        val sources = vectorStore.allEntries().map { it.chunk.sourcePath }.toSet()
        return MemoryStatus(
            enabled = isEnabled,
            indexed = vectorStore.size() > 0,
            totalChunks = vectorStore.size(),
            totalFiles = sources.size,
            embeddingProvider = embeddingProvider.providerName,
            embeddingDimensions = embeddingProvider.dimensions,
            cacheSize = embeddingCache.size(),
            lastIndexedAt = lastIndexedAt
        )
    }
}
