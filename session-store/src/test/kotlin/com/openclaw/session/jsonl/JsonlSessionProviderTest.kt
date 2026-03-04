package com.openclaw.session.jsonl

import com.openclaw.protocol.dto.TokenUsage
import com.openclaw.protocol.identifiers.SessionKey
import com.openclaw.session.SessionMessage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files

class JsonlSessionProviderTest : FunSpec({

    lateinit var provider: JsonlSessionProvider

    val testKey = SessionKey("test-agent:main:*:main")
    val now = Clock.System.now()

    beforeEach {
        val tmpDir = Files.createTempDirectory("jsonl-test-")
        provider = JsonlSessionProvider(tmpDir)
    }

    test("createSession produces meta in index") {
        val meta = provider.createSession(testKey, agentId = "test-agent", channel = "main")

        meta.key shouldBe testKey
        meta.agentId shouldBe "test-agent"
        meta.channel shouldBe "main"
        meta.messageCount shouldBe 0
        meta.totalTokens shouldBe 0
        meta.fileId shouldNotBe ""

        // getSession returns the same data
        provider.getSession(testKey) shouldBe meta
    }

    test("createSession throws on duplicate key") {
        provider.createSession(testKey, agentId = "test-agent")

        shouldThrow<IllegalArgumentException> {
            provider.createSession(testKey, agentId = "test-agent")
        }
    }

    test("listSessions returns all sessions") {
        val key2 = SessionKey("other-agent:main:*:main")
        provider.createSession(testKey, agentId = "test-agent")
        provider.createSession(key2, agentId = "other-agent")

        provider.listSessions() shouldHaveSize 2
    }

    test("appendMessage and getHistory round-trip") {
        provider.createSession(testKey, agentId = "test-agent")

        val userMsg = SessionMessage.UserMessage(
            id = "u1", timestamp = now,
            senderId = "peer-1", senderName = "Alice", text = "Hello"
        )
        val assistantMsg = SessionMessage.AssistantMessage(
            id = "a1", timestamp = now,
            text = "Hi there!", model = "test-model",
            usage = TokenUsage(promptTokens = 50, completionTokens = 20, totalTokens = 70)
        )

        provider.appendMessage(testKey, userMsg)
        provider.appendMessage(testKey, assistantMsg)

        val history = provider.getHistory(testKey)
        history shouldHaveSize 2
        history[0] shouldBe userMsg
        history[1] shouldBe assistantMsg
    }

    test("appendMessage updates meta counters") {
        provider.createSession(testKey, agentId = "test-agent")

        val msg = SessionMessage.AssistantMessage(
            id = "a1", timestamp = now, text = "response",
            usage = TokenUsage(promptTokens = 100, completionTokens = 50, totalTokens = 150)
        )
        provider.appendMessage(testKey, msg)

        val meta = provider.getSession(testKey)!!
        meta.messageCount shouldBe 1
        meta.totalTokens shouldBe 150
        meta.lastMessageAt shouldBe now
    }

    test("appendMessage increments compaction count for CompactionSummary") {
        provider.createSession(testKey, agentId = "test-agent")

        val compaction = SessionMessage.CompactionSummary(
            id = "cs1", timestamp = now,
            summary = "summary", messagesCompacted = 5,
            tokensBefore = 2000, tokensAfter = 300
        )
        provider.appendMessage(testKey, compaction)

        val meta = provider.getSession(testKey)!!
        meta.compactionCount shouldBe 1
    }

    test("getHistory with limit returns tail") {
        provider.createSession(testKey, agentId = "test-agent")

        repeat(10) { i ->
            provider.appendMessage(testKey, SessionMessage.UserMessage(
                id = "u$i", timestamp = now,
                senderId = "p", senderName = "P", text = "msg-$i"
            ))
        }

        val tail = provider.getHistory(testKey, limit = 3)
        tail shouldHaveSize 3
        (tail[0] as SessionMessage.UserMessage).text shouldBe "msg-7"
        (tail[1] as SessionMessage.UserMessage).text shouldBe "msg-8"
        (tail[2] as SessionMessage.UserMessage).text shouldBe "msg-9"
    }

    test("getHistory for empty session returns empty list") {
        provider.createSession(testKey, agentId = "test-agent")
        provider.getHistory(testKey).shouldBeEmpty()
    }

    test("deleteSession removes meta and file") {
        provider.createSession(testKey, agentId = "test-agent")
        provider.appendMessage(testKey, SessionMessage.UserMessage(
            id = "u1", timestamp = now,
            senderId = "p", senderName = "P", text = "hi"
        ))

        provider.deleteSession(testKey)
        provider.getSession(testKey) shouldBe null
        provider.listSessions().shouldBeEmpty()
    }

    test("deleteSession throws for unknown key") {
        shouldThrow<IllegalStateException> {
            provider.deleteSession(testKey)
        }
    }

    test("resetSession clears history and counters") {
        provider.createSession(testKey, agentId = "test-agent")

        provider.appendMessage(testKey, SessionMessage.UserMessage(
            id = "u1", timestamp = now,
            senderId = "p", senderName = "P", text = "hi"
        ))
        provider.appendMessage(testKey, SessionMessage.AssistantMessage(
            id = "a1", timestamp = now, text = "hello",
            usage = TokenUsage(promptTokens = 10, completionTokens = 5, totalTokens = 15)
        ))

        provider.resetSession(testKey)

        provider.getHistory(testKey).shouldBeEmpty()
        val meta = provider.getSession(testKey)!!
        meta.messageCount shouldBe 0
        meta.totalTokens shouldBe 0
        meta.lastMessageAt shouldBe null
        meta.compactionCount shouldBe 0
        // Session entry still exists
        meta.key shouldBe testKey
    }

    test("updateMeta applies updater atomically") {
        provider.createSession(testKey, agentId = "test-agent")

        provider.updateMeta(testKey) { it.copy(modelId = "anthropic:claude-sonnet-4-5") }

        provider.getSession(testKey)!!.modelId shouldBe "anthropic:claude-sonnet-4-5"
    }

    test("appendMessage throws for unknown session") {
        shouldThrow<IllegalStateException> {
            provider.appendMessage(testKey, SessionMessage.UserMessage(
                id = "u1", timestamp = now,
                senderId = "p", senderName = "P", text = "hi"
            ))
        }
    }

    test("concurrent writes to the same session serialize correctly") {
        runTest {
            provider.createSession(testKey, agentId = "test-agent")

            val jobs = (0 until 50).map { i ->
                async {
                    provider.appendMessage(testKey, SessionMessage.UserMessage(
                        id = "u$i", timestamp = now,
                        senderId = "p", senderName = "P", text = "msg-$i"
                    ))
                }
            }
            jobs.awaitAll()

            val history = provider.getHistory(testKey)
            history shouldHaveSize 50

            val meta = provider.getSession(testKey)!!
            meta.messageCount shouldBe 50
        }
    }

    test("concurrent writes to different sessions do not interfere") {
        runTest {
            val keys = (0 until 5).map { SessionKey("agent-$it:main:*:main") }
            keys.forEach { provider.createSession(it, agentId = it.agentId) }

            val jobs = keys.flatMap { key ->
                (0 until 10).map { i ->
                    async {
                        provider.appendMessage(key, SessionMessage.UserMessage(
                            id = "u$i", timestamp = now,
                            senderId = "p", senderName = "P", text = "msg-$i"
                        ))
                    }
                }
            }
            jobs.awaitAll()

            keys.forEach { key ->
                provider.getHistory(key) shouldHaveSize 10
                provider.getSession(key)!!.messageCount shouldBe 10
            }
        }
    }

    test("ToolCall and ToolResult persist in transcript") {
        provider.createSession(testKey, agentId = "test-agent")

        val call = SessionMessage.ToolCall(
            id = "tc1", timestamp = now,
            toolName = "web_search",
            args = JsonObject(mapOf("q" to JsonPrimitive("Kotlin"))),
            requestId = "req-1"
        )
        val result = SessionMessage.ToolResult(
            id = "tr1", timestamp = now,
            toolName = "web_search",
            result = "Kotlin is a modern language",
            requestId = "req-1",
            durationMs = 120
        )

        provider.appendMessage(testKey, call)
        provider.appendMessage(testKey, result)

        val history = provider.getHistory(testKey)
        history shouldHaveSize 2
        history[0] shouldBe call
        history[1] shouldBe result
    }

    test("SystemEvent persists in transcript") {
        provider.createSession(testKey, agentId = "test-agent")

        val event = SessionMessage.SystemEvent(
            id = "se1", timestamp = now,
            event = "rate_limited",
            data = JsonObject(mapOf("retryAfter" to JsonPrimitive(5)))
        )

        provider.appendMessage(testKey, event)
        provider.getHistory(testKey).first() shouldBe event
    }
})
