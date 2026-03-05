package com.openclaw.session.jsonl

import com.openclaw.protocol.identifiers.SessionKey
import com.openclaw.session.*
import com.openclaw.session.compaction.CompactableSessionStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.*

/**
 * JSONL file-based [SessionStore] implementation.
 *
 * Layout on disk:
 * ```
 * sessionsDir/
 *   sessions.json          <- index: Map<rawKey, SessionMeta>
 *   <fileId>.jsonl          <- one file per session, one JSON line per message
 * ```
 *
 * Concurrent access to the same session is serialized via [SessionWriteLock].
 */
class JsonlSessionProvider(private val sessionsDir: Path) : CompactableSessionStore {

    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    private val writeLock = SessionWriteLock()

    /**
     * Global mutex protecting sessions.json index file.
     *
     * [SessionWriteLock] is per-session and only covers individual .jsonl files.
     * Without this mutex, two different sessions writing simultaneously both call
     * loadIndex(), modify their own key, and call saveIndex() — the second write
     * silently overwrites the first session's entry.
     */
    private val indexMutex = Mutex()

    private val indexFile: Path get() = sessionsDir / "sessions.json"

    /** In-memory cache of session metadata, avoiding index file I/O on every read. */
    private val metaCache = ConcurrentHashMap<String, SessionMeta>()
    @Volatile
    private var cacheLoaded = false
    private val cacheLoadMutex = Mutex()

    init {
        sessionsDir.createDirectories()
    }

    /** Ensure the cache is populated from disk (called once lazily). */
    private suspend fun ensureCacheLoaded() {
        if (cacheLoaded) return
        cacheLoadMutex.withLock {
            if (cacheLoaded) return@withLock
            metaCache.clear()
            metaCache.putAll(loadIndex())
            cacheLoaded = true
        }
    }

    // ---- Index persistence ----

    private fun loadIndex(): MutableMap<String, SessionMeta> {
        if (!indexFile.exists()) return mutableMapOf()
        val text = indexFile.readText()
        if (text.isBlank()) return mutableMapOf()
        return json.decodeFromString<Map<String, SessionMeta>>(text).toMutableMap()
    }

    private fun saveIndex(index: Map<String, SessionMeta>) {
        indexFile.writeText(json.encodeToString(index))
    }

    /**
     * Load the index, apply [block], save, and return the result.
     * Holds [indexMutex] for the entire load→mutate→save cycle to prevent
     * two sessions from concurrently overwriting each other's index entries.
     */
    private suspend fun <T> withIndexWrite(block: (MutableMap<String, SessionMeta>) -> T): T =
        indexMutex.withLock {
            ensureCacheLoaded()
            val index = HashMap(metaCache)
            val result = block(index)
            // Write-through: update both disk and cache
            saveIndex(index)
            metaCache.clear()
            metaCache.putAll(index)
            result
        }

    // ---- Helpers ----

    private fun transcriptPath(fileId: String): Path = sessionsDir / "$fileId.jsonl"

    // ---- SessionStore implementation ----

    override suspend fun createSession(key: SessionKey, agentId: String, channel: String?): SessionMeta =
        writeLock.withLock(key) {
            val fileId = UUID.randomUUID().toString()
            val meta = SessionMeta(
                key = key,
                agentId = agentId,
                channel = channel,
                createdAt = Clock.System.now(),
                fileId = fileId
            )
            withIndexWrite { index ->
                require(key.raw !in index) { "Session already exists: ${key.raw}" }
                index[key.raw] = meta
            }
            // Create an empty transcript file
            transcriptPath(fileId).createFile()
            meta
        }

    override suspend fun getSession(key: SessionKey): SessionMeta? {
        ensureCacheLoaded()
        return metaCache[key.raw]
    }

    override suspend fun listSessions(): List<SessionMeta> {
        ensureCacheLoaded()
        return metaCache.values.toList()
    }

    override suspend fun appendMessage(key: SessionKey, message: SessionMessage): Unit =
        writeLock.withLock(key) {
            ensureCacheLoaded()
            val meta = metaCache[key.raw] ?: error("Session not found: ${key.raw}")

            val line = json.encodeToString(SessionMessage.serializer(), message)
            transcriptPath(meta.fileId).appendText(line + "\n")

            // Update metadata counters under index lock
            val tokensDelta = when (message) {
                is SessionMessage.AssistantMessage -> message.usage?.totalTokens?.toLong() ?: 0L
                else -> 0L
            }
            val compactionDelta = when (message) {
                is SessionMessage.CompactionSummary -> 1
                else -> 0
            }
            withIndexWrite { index ->
                val current = index[key.raw] ?: return@withIndexWrite
                index[key.raw] = current.copy(
                    lastMessageAt = message.timestamp,
                    messageCount = current.messageCount + 1,
                    totalTokens = current.totalTokens + tokensDelta,
                    compactionCount = current.compactionCount + compactionDelta
                )
            }
        }

    override suspend fun getHistory(key: SessionKey, limit: Int?): List<SessionMessage> {
        ensureCacheLoaded()
        val meta = metaCache[key.raw] ?: error("Session not found: ${key.raw}")
        val path = transcriptPath(meta.fileId)
        if (!path.exists()) return emptyList()

        val lines = path.readLines().filter { it.isNotBlank() }
        val messages = lines.map { json.decodeFromString(SessionMessage.serializer(), it) }

        return if (limit != null && limit < messages.size) {
            messages.takeLast(limit)
        } else {
            messages
        }
    }

    override suspend fun deleteSession(key: SessionKey): Unit =
        try {
            writeLock.withLock(key) {
                var fileId: String? = null
                withIndexWrite { index ->
                    val meta = index.remove(key.raw) ?: error("Session not found: ${key.raw}")
                    fileId = meta.fileId
                }
                fileId?.let { transcriptPath(it).deleteIfExists() }
            }
        } finally {
            writeLock.cleanup(key)
        }

    override suspend fun resetSession(key: SessionKey): Unit =
        try {
            writeLock.withLock(key) {
                withIndexWrite { index ->
                    val meta = index[key.raw] ?: error("Session not found: ${key.raw}")
                    transcriptPath(meta.fileId).writeText("")
                    index[key.raw] = meta.copy(
                        lastMessageAt = null,
                        messageCount = 0,
                        compactionCount = 0,
                        totalTokens = 0
                    )
                }
            }
        } finally {
            writeLock.cleanup(key)
        }

    override suspend fun updateMeta(key: SessionKey, updater: (SessionMeta) -> SessionMeta): Unit =
        writeLock.withLock(key) {
            withIndexWrite { index ->
                val meta = index[key.raw] ?: error("Session not found: ${key.raw}")
                index[key.raw] = updater(meta)
            }
        }

    override suspend fun replaceWithCompaction(
        key: SessionKey,
        compactedCount: Int,
        summary: SessionMessage.CompactionSummary,
        remaining: List<SessionMessage>
    ): Unit = writeLock.withLock(key) {
        ensureCacheLoaded()
        val meta = metaCache[key.raw] ?: error("Session not found: ${key.raw}")

        // Build new transcript: summary + remaining messages
        val newLines = buildString {
            appendLine(json.encodeToString(SessionMessage.serializer(), summary))
            remaining.forEach { msg ->
                appendLine(json.encodeToString(SessionMessage.serializer(), msg))
            }
        }

        // Atomic file replace: write to .tmp, then move
        val target = transcriptPath(meta.fileId)
        val tmp = sessionsDir / "${meta.fileId}.tmp"
        tmp.writeText(newLines)
        java.nio.file.Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE)

        // Update metadata under index lock
        val newMessageCount = 1 + remaining.size  // summary + remaining
        withIndexWrite { index ->
            val current = index[key.raw] ?: return@withIndexWrite
            index[key.raw] = current.copy(
                messageCount = newMessageCount,
                compactionCount = current.compactionCount + 1
            )
        }
    }
}
