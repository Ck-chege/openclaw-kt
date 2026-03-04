package com.openclaw.session.jsonl

import com.openclaw.protocol.identifiers.SessionKey
import com.openclaw.session.*
import com.openclaw.session.compaction.CompactableSessionStore
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
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

    private val indexFile: Path get() = sessionsDir / "sessions.json"

    init {
        sessionsDir.createDirectories()
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

    // ---- Helpers ----

    private fun transcriptPath(fileId: String): Path = sessionsDir / "$fileId.jsonl"

    // ---- SessionStore implementation ----

    override suspend fun createSession(key: SessionKey, agentId: String, channel: String?): SessionMeta =
        writeLock.withLock(key) {
            val index = loadIndex()
            require(key.raw !in index) { "Session already exists: ${key.raw}" }

            val fileId = UUID.randomUUID().toString()
            val meta = SessionMeta(
                key = key,
                agentId = agentId,
                channel = channel,
                createdAt = Clock.System.now(),
                fileId = fileId
            )

            index[key.raw] = meta
            saveIndex(index)

            // Create an empty transcript file
            transcriptPath(fileId).createFile()

            meta
        }

    override suspend fun getSession(key: SessionKey): SessionMeta? {
        val index = loadIndex()
        return index[key.raw]
    }

    override suspend fun listSessions(): List<SessionMeta> {
        return loadIndex().values.toList()
    }

    override suspend fun appendMessage(key: SessionKey, message: SessionMessage): Unit =
        writeLock.withLock(key) {
            val index = loadIndex()
            val meta = index[key.raw] ?: error("Session not found: ${key.raw}")

            val line = json.encodeToString(SessionMessage.serializer(), message)
            transcriptPath(meta.fileId).appendText(line + "\n")

            // Update metadata counters
            val tokensDelta = when (message) {
                is SessionMessage.AssistantMessage -> message.usage?.totalTokens?.toLong() ?: 0L
                else -> 0L
            }
            val compactionDelta = when (message) {
                is SessionMessage.CompactionSummary -> 1
                else -> 0
            }

            val updated = meta.copy(
                lastMessageAt = message.timestamp,
                messageCount = meta.messageCount + 1,
                totalTokens = meta.totalTokens + tokensDelta,
                compactionCount = meta.compactionCount + compactionDelta
            )
            index[key.raw] = updated
            saveIndex(index)
        }

    override suspend fun getHistory(key: SessionKey, limit: Int?): List<SessionMessage> {
        val meta = loadIndex()[key.raw] ?: error("Session not found: ${key.raw}")
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
        writeLock.withLock(key) {
            val index = loadIndex()
            val meta = index.remove(key.raw) ?: error("Session not found: ${key.raw}")
            saveIndex(index)

            val path = transcriptPath(meta.fileId)
            path.deleteIfExists()
        }

    override suspend fun resetSession(key: SessionKey): Unit =
        writeLock.withLock(key) {
            val index = loadIndex()
            val meta = index[key.raw] ?: error("Session not found: ${key.raw}")

            // Truncate the transcript file
            transcriptPath(meta.fileId).writeText("")

            // Reset counters but keep session entry
            val updated = meta.copy(
                lastMessageAt = null,
                messageCount = 0,
                compactionCount = 0,
                totalTokens = 0
            )
            index[key.raw] = updated
            saveIndex(index)
        }

    override suspend fun updateMeta(key: SessionKey, updater: (SessionMeta) -> SessionMeta): Unit =
        writeLock.withLock(key) {
            val index = loadIndex()
            val meta = index[key.raw] ?: error("Session not found: ${key.raw}")
            val updated = updater(meta)
            index[key.raw] = updated
            saveIndex(index)
        }

    override suspend fun replaceWithCompaction(
        key: SessionKey,
        compactedCount: Int,
        summary: SessionMessage.CompactionSummary,
        remaining: List<SessionMessage>
    ): Unit = writeLock.withLock(key) {
        val index = loadIndex()
        val meta = index[key.raw] ?: error("Session not found: ${key.raw}")

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

        // Update metadata
        val newMessageCount = 1 + remaining.size  // summary + remaining
        val updated = meta.copy(
            messageCount = newMessageCount,
            compactionCount = meta.compactionCount + 1
        )
        index[key.raw] = updated
        saveIndex(index)
    }
}
