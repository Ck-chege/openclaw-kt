package com.kclaw.agent.tools

import com.kclaw.protocol.identifiers.SessionKey
import com.kclaw.session.SessionMeta
import com.kclaw.session.SessionMessage
import com.kclaw.session.SessionStore
import kotlinx.datetime.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory [SessionStore] for testing native tools without file I/O.
 */
class FakeSessionStore : SessionStore {
    private val sessions = ConcurrentHashMap<String, SessionMeta>()
    private val transcripts = ConcurrentHashMap<String, MutableList<SessionMessage>>()

    override suspend fun createSession(key: SessionKey, agentId: String, channel: String?): SessionMeta {
        val meta = SessionMeta(
            key = key,
            agentId = agentId,
            channel = channel,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            fileId = UUID.randomUUID().toString()
        )
        sessions[key.raw] = meta
        transcripts[key.raw] = mutableListOf()
        return meta
    }

    override suspend fun getSession(key: SessionKey): SessionMeta? = sessions[key.raw]

    override suspend fun listSessions(): List<SessionMeta> = sessions.values.toList()

    override suspend fun appendMessage(key: SessionKey, message: SessionMessage) {
        transcripts.getOrPut(key.raw) { mutableListOf() }.add(message)
        sessions.computeIfPresent(key.raw) { _, meta ->
            meta.copy(
                messageCount = meta.messageCount + 1,
                lastMessageAt = message.timestamp
            )
        }
    }

    override suspend fun getHistory(key: SessionKey, limit: Int?): List<SessionMessage> {
        val messages = transcripts[key.raw] ?: emptyList()
        return if (limit != null) messages.takeLast(limit) else messages
    }

    override suspend fun deleteSession(key: SessionKey) {
        sessions.remove(key.raw)
        transcripts.remove(key.raw)
    }

    override suspend fun resetSession(key: SessionKey) {
        transcripts[key.raw]?.clear()
        sessions.computeIfPresent(key.raw) { _, meta ->
            meta.copy(messageCount = 0, totalTokens = 0, lastMessageAt = null)
        }
    }

    override suspend fun updateMeta(key: SessionKey, updater: (SessionMeta) -> SessionMeta) {
        sessions.computeIfPresent(key.raw) { _, meta -> updater(meta) }
    }
}
