package com.kclaw.memory.indexing

import com.kclaw.memory.chunking.Chunk
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.*

private val logger = KotlinLogging.logger {}

/**
 * Stored entry combining a chunk with its embedding vector.
 * Uses `List<Float>` for JSON serialization; convert to/from `FloatArray` at boundaries.
 */
@Serializable
data class StoredEntry(
    val chunk: Chunk,
    val embedding: List<Float>
) {
    fun embeddingArray(): FloatArray = FloatArray(embedding.size) { embedding[it] }
}

/**
 * In-memory vector store with synchronized mutations and disk persistence.
 */
class VectorStore {
    private val entries = mutableListOf<StoredEntry>()
    private val lock = Any()

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    fun addAll(newEntries: List<StoredEntry>) {
        synchronized(lock) {
            entries.addAll(newEntries)
        }
    }

    fun removeBySource(sourcePath: String) {
        synchronized(lock) {
            entries.removeAll { it.chunk.sourcePath == sourcePath }
        }
    }

    fun allEntries(): List<StoredEntry> {
        synchronized(lock) {
            return entries.toList()
        }
    }

    fun entriesBySource(sourcePath: String): List<StoredEntry> {
        synchronized(lock) {
            return entries.filter { it.chunk.sourcePath == sourcePath }
        }
    }

    fun size(): Int {
        synchronized(lock) {
            return entries.size
        }
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
        }
    }

    /**
     * Save all entries to a JSON file on disk.
     */
    fun saveToDisk(path: Path) {
        val snapshot = synchronized(lock) { entries.toList() }
        path.parent?.createDirectories()
        val content = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(StoredEntry.serializer()),
            snapshot
        )
        path.writeText(content)
        logger.debug { "Saved ${snapshot.size} entries to $path" }
    }

    /**
     * Load entries from a JSON file on disk.
     * Returns true if the file existed and was loaded successfully.
     */
    fun loadFromDisk(path: Path): Boolean {
        if (!path.exists()) return false
        return try {
            val content = path.readText()
            val loaded = json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(StoredEntry.serializer()),
                content
            )
            synchronized(lock) {
                entries.clear()
                entries.addAll(loaded)
            }
            logger.debug { "Loaded ${loaded.size} entries from $path" }
            true
        } catch (e: Exception) {
            logger.warn(e) { "Failed to load vector store from $path" }
            false
        }
    }
}
