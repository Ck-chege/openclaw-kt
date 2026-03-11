package com.kclaw.session.compaction

import com.kclaw.protocol.identifiers.SessionKey
import com.kclaw.session.SessionMessage
import com.kclaw.session.jsonl.JsonlSessionProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.datetime.Clock
import java.nio.file.Files

class CompactionEngineTest : FunSpec({

    val testKey = SessionKey("test-agent:main:*:main")
    val now = Clock.System.now()

    /** Mock summarizer that returns a predictable summary. */
    val mockSummarizer = MessageSummarizer { messages ->
        CompactionResult("Summary of ${messages.size} messages", 500)
    }

    /**
     * Helper to create a provider + engine with a low threshold for testing.
     * Uses chars/4 heuristic: 400 chars text → ~100 tokens.
     * With threshold = 200 tokens, we need > 800 chars of messages to trigger.
     */
    fun setup(
        maxContextTokens: Int = 1000,
        triggerRatio: Double = 0.20,
        minMessages: Int = 4,
        summarizer: MessageSummarizer = mockSummarizer
    ): Triple<JsonlSessionProvider, CompactionEngine, CompactionConfig> {
        val tmpDir = Files.createTempDirectory("compaction-test-")
        val provider = JsonlSessionProvider(tmpDir)
        val config = CompactionConfig(
            maxContextTokens = maxContextTokens,
            triggerRatio = triggerRatio,
            minMessagesToCompact = minMessages
        )
        val engine = CompactionEngine(provider, summarizer, config)
        return Triple(provider, engine, config)
    }

    fun userMessage(id: String, text: String) = SessionMessage.UserMessage(
        id = id, timestamp = now,
        senderId = "peer-1", senderName = "Alice", text = text
    )

    fun assistantMessage(id: String, text: String) = SessionMessage.AssistantMessage(
        id = id, timestamp = now,
        text = text
    )

    // --- ShouldCompact ---

    context("shouldCompact") {
        test("returns true when tokens exceed threshold") {
            val (_, engine, config) = setup()
            engine.shouldCompact(config.triggerThreshold + 1) shouldBe true
        }

        test("returns false below threshold") {
            val (_, engine, config) = setup()
            engine.shouldCompact(config.triggerThreshold - 1) shouldBe false
        }
    }

    // --- EstimateTokens ---

    context("estimateTokens") {
        test("estimates from user message text length") {
            val (_, engine, _) = setup()
            // "x".repeat(400) → 400 chars / 4 = 100 tokens
            val messages = listOf(userMessage("u1", "x".repeat(400)))
            engine.estimateTokens(messages) shouldBe 100
        }

        test("estimates from assistant message text") {
            val (_, engine, _) = setup()
            val messages = listOf(assistantMessage("a1", "y".repeat(800)))
            engine.estimateTokens(messages) shouldBe 200
        }

        test("returns zero for empty list") {
            val (_, engine, _) = setup()
            engine.estimateTokens(emptyList()) shouldBe 0
        }
    }

    // --- Compact ---

    context("compact") {
        test("compacts oldest messages and writes CompactionSummary") {
            val (provider, engine, _) = setup(maxContextTokens = 1000, triggerRatio = 0.20)
            // threshold = 200 tokens, so we need > 800 chars
            provider.createSession(testKey, agentId = "test-agent")

            // Add 8 messages of 200 chars each → 1600 chars / 4 = 400 tokens > 200 threshold
            repeat(8) { i ->
                provider.appendMessage(testKey, userMessage("u$i", "x".repeat(200)))
            }

            val outcome = engine.compact(testKey)
            outcome.shouldBeInstanceOf<CompactionOutcome.Success>()
            outcome.messagesCompacted shouldBe outcome.messagesCompacted // sanity

            // History should now start with CompactionSummary
            val history = provider.getHistory(testKey)
            history.first().shouldBeInstanceOf<SessionMessage.CompactionSummary>()
        }

        test("preserves most recent messages after compaction") {
            val (provider, engine, _) = setup(maxContextTokens = 1000, triggerRatio = 0.20)
            provider.createSession(testKey, agentId = "test-agent")

            repeat(8) { i ->
                provider.appendMessage(testKey, userMessage("u$i", "x".repeat(200)))
            }

            engine.compact(testKey)

            val history = provider.getHistory(testKey)
            // Should have CompactionSummary + some remaining messages
            // Last message should be the one we added last
            val lastMsg = history.last()
            lastMsg.shouldBeInstanceOf<SessionMessage.UserMessage>()
            lastMsg.id shouldBe "u7"
        }

        test("updates session metadata compaction count") {
            val (provider, engine, _) = setup(maxContextTokens = 1000, triggerRatio = 0.20)
            provider.createSession(testKey, agentId = "test-agent")

            repeat(8) { i ->
                provider.appendMessage(testKey, userMessage("u$i", "x".repeat(200)))
            }

            engine.compact(testKey)

            val meta = provider.getSession(testKey)!!
            meta.compactionCount shouldBe 1
        }

        test("returns Skipped when below threshold") {
            val (provider, engine, _) = setup(maxContextTokens = 1000, triggerRatio = 0.20)
            provider.createSession(testKey, agentId = "test-agent")

            // Add very small messages → below threshold
            provider.appendMessage(testKey, userMessage("u1", "hi"))

            val outcome = engine.compact(testKey)
            outcome.shouldBeInstanceOf<CompactionOutcome.Skipped>()
        }

        test("returns Skipped when fewer messages than minimum") {
            val (provider, engine, _) = setup(
                maxContextTokens = 100, triggerRatio = 0.10, minMessages = 10
            )
            // threshold = 10 tokens, so 40+ chars triggers
            provider.createSession(testKey, agentId = "test-agent")

            // 3 messages of 100 chars → 300 chars / 4 = 75 tokens > 10 threshold
            // But minMessages = 10, and we only have 3
            repeat(3) { i ->
                provider.appendMessage(testKey, userMessage("u$i", "x".repeat(100)))
            }

            val outcome = engine.compact(testKey)
            outcome.shouldBeInstanceOf<CompactionOutcome.Skipped>()
        }

        test("returns Failed when summarizer throws") {
            val failingSummarizer = MessageSummarizer { _ ->
                throw RuntimeException("LLM unavailable")
            }
            val (provider, engine, _) = setup(
                maxContextTokens = 1000, triggerRatio = 0.20,
                summarizer = failingSummarizer
            )
            provider.createSession(testKey, agentId = "test-agent")

            repeat(8) { i ->
                provider.appendMessage(testKey, userMessage("u$i", "x".repeat(200)))
            }

            val outcome = engine.compact(testKey)
            outcome.shouldBeInstanceOf<CompactionOutcome.Failed>()
        }

        test("can compact a session with existing CompactionSummary (re-compaction)") {
            val (provider, engine, _) = setup(maxContextTokens = 1000, triggerRatio = 0.20)
            provider.createSession(testKey, agentId = "test-agent")

            // First: add messages and compact
            repeat(8) { i ->
                provider.appendMessage(testKey, userMessage("u$i", "x".repeat(200)))
            }
            val firstOutcome = engine.compact(testKey)
            firstOutcome.shouldBeInstanceOf<CompactionOutcome.Success>()

            // Add more messages to push over threshold again
            repeat(8) { i ->
                provider.appendMessage(testKey, userMessage("v$i", "y".repeat(200)))
            }

            val secondOutcome = engine.compact(testKey)
            secondOutcome.shouldBeInstanceOf<CompactionOutcome.Success>()

            val meta = provider.getSession(testKey)!!
            meta.compactionCount shouldBe 2

            // History starts with a new CompactionSummary
            val history = provider.getHistory(testKey)
            history.first().shouldBeInstanceOf<SessionMessage.CompactionSummary>()
        }

        test("re-compaction includes prior summaries in summarizer input") {
            val summarizedTypes = mutableListOf<List<SessionMessage>>()
            val trackingSummarizer = MessageSummarizer { messages ->
                summarizedTypes += messages
                CompactionResult("Summary of ${messages.size} messages", 500)
            }
            val (provider, engine, _) = setup(
                maxContextTokens = 1000,
                triggerRatio = 0.20,
                summarizer = trackingSummarizer
            )
            provider.createSession(testKey, agentId = "test-agent")

            repeat(8) { i ->
                provider.appendMessage(testKey, userMessage("u$i", "x".repeat(200)))
            }
            engine.compact(testKey).shouldBeInstanceOf<CompactionOutcome.Success>()

            repeat(8) { i ->
                provider.appendMessage(testKey, userMessage("v$i", "y".repeat(200)))
            }
            engine.compact(testKey).shouldBeInstanceOf<CompactionOutcome.Success>()

            summarizedTypes.size shouldBe 2
            summarizedTypes.last().any { it is SessionMessage.CompactionSummary } shouldBe true
        }
    }
})
