package com.kclaw.agent.runtime

import com.kclaw.agent.tools.FakeSessionStore
import com.kclaw.protocol.identifiers.SessionKey
import com.kclaw.session.SessionMessage
import com.kclaw.session.compaction.CompactableSessionStore
import com.kclaw.session.compaction.CompactionConfig
import com.kclaw.session.compaction.CompactionEngine
import com.kclaw.session.compaction.CompactionOutcome
import com.kclaw.session.compaction.CompactionResult
import com.kclaw.session.compaction.MessageSummarizer
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CompactionTriggerTest {

    private val testKey = SessionKey("test:main:*:main")
    private val now = Clock.System.now()

    private val mockSummarizer = MessageSummarizer { messages ->
        CompactionResult("Summary of ${messages.size} messages", 500)
    }

    private fun userMsg(text: String) = SessionMessage.UserMessage(
        id = java.util.UUID.randomUUID().toString(),
        timestamp = now,
        senderId = "user",
        senderName = "User",
        text = text
    )

    private fun assistantMsg(text: String) = SessionMessage.AssistantMessage(
        id = java.util.UUID.randomUUID().toString(),
        timestamp = now,
        text = text,
        model = "test"
    )

    /**
     * Creates a CompactableSessionStore backed by FakeSessionStore,
     * pre-populated with messages for compaction testing.
     */
    private suspend fun setupStore(
        messages: List<SessionMessage>
    ): FakeCompactableStore {
        val store = FakeCompactableStore()
        store.createSession(testKey, "test")
        for (msg in messages) {
            store.appendMessage(testKey, msg)
        }
        return store
    }

    @Nested
    inner class TriggerLogic {

        @Test
        fun `returns NotNeeded when token count below threshold`() = runTest {
            val store = setupStore(listOf(userMsg("Hello")))
            val config = CompactionConfig(maxContextTokens = 200_000) // threshold = 160k
            val engine = CompactionEngine(store, mockSummarizer, config)
            val trigger = CompactionTrigger(engine, null, store)

            val result = trigger.checkAndCompact(testKey)
            result shouldBe CompactionTriggerResult.NotNeeded
        }

        @Test
        fun `runs compaction when above threshold without flush runner`() = runTest {
            // Create enough messages to exceed threshold
            val longText = "x".repeat(4000) // ~1000 tokens each
            val messages = (1..200).map { userMsg(longText) } // ~200k tokens
            val store = setupStore(messages)
            // Low threshold so compaction triggers
            val config = CompactionConfig(maxContextTokens = 1000, triggerRatio = 0.20)
            val engine = CompactionEngine(store, mockSummarizer, config)
            val trigger = CompactionTrigger(engine, null, store)

            val result = trigger.checkAndCompact(testKey)
            val compacted = result.shouldBeInstanceOf<CompactionTriggerResult.Compacted>()
            compacted.flushed shouldBe false
        }

        @Test
        fun `runs flush then compact when flush runner provided`() = runTest {
            val longText = "x".repeat(4000)
            val messages = (1..200).map { userMsg(longText) }
            val store = setupStore(messages)
            val config = CompactionConfig(maxContextTokens = 1000, triggerRatio = 0.20)
            val engine = CompactionEngine(store, mockSummarizer, config)

            // Mock flush runner that always returns Flushed
            val flushRunner = FakeFlushRunner(FlushResult.Flushed("saved memories"))
            val trigger = CompactionTrigger(engine, flushRunner, store)

            val result = trigger.checkAndCompact(testKey)
            val compacted = result.shouldBeInstanceOf<CompactionTriggerResult.Compacted>()
            compacted.flushed shouldBe true
        }

        @Test
        fun `flushed is false when flush runner returns Skipped`() = runTest {
            val longText = "x".repeat(4000)
            val messages = (1..200).map { userMsg(longText) }
            val store = setupStore(messages)
            val config = CompactionConfig(maxContextTokens = 1000, triggerRatio = 0.20)
            val engine = CompactionEngine(store, mockSummarizer, config)

            val flushRunner = FakeFlushRunner(FlushResult.Skipped)
            val trigger = CompactionTrigger(engine, flushRunner, store)

            val result = trigger.checkAndCompact(testKey)
            val compacted = result.shouldBeInstanceOf<CompactionTriggerResult.Compacted>()
            compacted.flushed shouldBe false
        }

        @Test
        fun `proceeds with compaction even when flush fails`() = runTest {
            val longText = "x".repeat(4000)
            val messages = (1..200).map { userMsg(longText) }
            val store = setupStore(messages)
            val config = CompactionConfig(maxContextTokens = 1000, triggerRatio = 0.20)
            val engine = CompactionEngine(store, mockSummarizer, config)

            val flushRunner = FakeFlushRunner(FlushResult.Failed(RuntimeException("LLM down")))
            val trigger = CompactionTrigger(engine, flushRunner, store)

            val result = trigger.checkAndCompact(testKey)
            val compacted = result.shouldBeInstanceOf<CompactionTriggerResult.Compacted>()
            // Flush failed but compaction still ran
            compacted.flushed shouldBe false
        }
    }

    @Nested
    inner class EdgeCases {

        @Test
        fun `handles empty session history`() = runTest {
            val store = setupStore(emptyList())
            val config = CompactionConfig(maxContextTokens = 200_000)
            val engine = CompactionEngine(store, mockSummarizer, config)
            val trigger = CompactionTrigger(engine, null, store)

            val result = trigger.checkAndCompact(testKey)
            result shouldBe CompactionTriggerResult.NotNeeded
        }

        @Test
        fun `handles compaction engine returning Skipped`() = runTest {
            // Few messages but enough tokens to trigger, but too few non-summary messages
            val longText = "x".repeat(4000)
            val messages = listOf(userMsg(longText), assistantMsg(longText))
            val store = setupStore(messages)
            // Low threshold but high minMessagesToCompact
            val config = CompactionConfig(
                maxContextTokens = 1000,
                triggerRatio = 0.20,
                minMessagesToCompact = 100 // will cause Skipped
            )
            val engine = CompactionEngine(store, mockSummarizer, config)
            val trigger = CompactionTrigger(engine, null, store)

            val result = trigger.checkAndCompact(testKey)
            val compacted = result.shouldBeInstanceOf<CompactionTriggerResult.Compacted>()
            val outcome = compacted.outcome
            outcome.shouldBeInstanceOf<CompactionOutcome.Skipped>()
        }

        @Test
        fun `does not throw on any failure`() = runTest {
            // Use a store that will throw on getHistory
            val failingStore = object : FakeCompactableStore() {
                override suspend fun getHistory(key: SessionKey, limit: Int?): List<SessionMessage> {
                    throw RuntimeException("Database connection lost")
                }
            }
            failingStore.createSession(testKey, "test")

            val config = CompactionConfig(maxContextTokens = 1000)
            val engine = CompactionEngine(failingStore, mockSummarizer, config)
            val trigger = CompactionTrigger(engine, null, failingStore)

            val result = trigger.checkAndCompact(testKey)
            result.shouldBeInstanceOf<CompactionTriggerResult.FlushFailed>()
        }
    }
}

/**
 * Fake [MemoryFlushRunner] that returns a predetermined result.
 */
private class FakeFlushRunner(
    private val result: FlushResult
) : MemoryFlushRunner(
    modelRegistry = com.kclaw.agent.model.ModelRegistry { null },
    toolRegistry = ai.koog.agents.core.tools.ToolRegistry.EMPTY
) {
    override suspend fun runFlush(
        sessionKey: SessionKey,
        history: List<SessionMessage>
    ): FlushResult = result
}

/**
 * Fake [CompactableSessionStore] for testing CompactionTrigger.
 * Extends FakeSessionStore with compaction support.
 */
open class FakeCompactableStore : CompactableSessionStore {
    private val sessions = java.util.concurrent.ConcurrentHashMap<String, com.kclaw.session.SessionMeta>()
    private val transcripts = java.util.concurrent.ConcurrentHashMap<String, MutableList<SessionMessage>>()

    override suspend fun createSession(key: SessionKey, agentId: String, channel: String?): com.kclaw.session.SessionMeta {
        val meta = com.kclaw.session.SessionMeta(
            key = key, agentId = agentId, channel = channel,
            createdAt = kotlinx.datetime.Instant.parse("2026-01-01T00:00:00Z"),
            fileId = java.util.UUID.randomUUID().toString()
        )
        sessions[key.raw] = meta
        transcripts[key.raw] = mutableListOf()
        return meta
    }

    override suspend fun getSession(key: SessionKey) = sessions[key.raw]
    override suspend fun listSessions() = sessions.values.toList()
    override suspend fun appendMessage(key: SessionKey, message: SessionMessage) {
        transcripts.getOrPut(key.raw) { mutableListOf() }.add(message)
    }
    override suspend fun getHistory(key: SessionKey, limit: Int?): List<SessionMessage> {
        val msgs = transcripts[key.raw] ?: emptyList()
        return if (limit != null) msgs.takeLast(limit) else msgs
    }
    override suspend fun deleteSession(key: SessionKey) {
        sessions.remove(key.raw); transcripts.remove(key.raw)
    }
    override suspend fun resetSession(key: SessionKey) {
        transcripts[key.raw]?.clear()
    }
    override suspend fun updateMeta(key: SessionKey, updater: (com.kclaw.session.SessionMeta) -> com.kclaw.session.SessionMeta) {
        sessions.computeIfPresent(key.raw) { _, meta -> updater(meta) }
    }

    override suspend fun replaceWithCompaction(
        key: SessionKey,
        compactedCount: Int,
        summary: SessionMessage.CompactionSummary,
        remaining: List<SessionMessage>
    ) {
        val newTranscript = mutableListOf<SessionMessage>()
        newTranscript.add(summary)
        newTranscript.addAll(remaining)
        transcripts[key.raw] = newTranscript
    }
}
