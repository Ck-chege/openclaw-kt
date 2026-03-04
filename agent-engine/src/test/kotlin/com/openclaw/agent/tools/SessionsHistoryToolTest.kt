package com.openclaw.agent.tools

import com.openclaw.protocol.identifiers.SessionKey
import com.openclaw.session.SessionMessage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SessionsHistoryToolTest {

    private lateinit var store: FakeSessionStore
    private lateinit var tool: SessionsHistoryTool

    private val testInstant = Instant.parse("2026-01-15T10:30:00Z")

    @BeforeEach
    fun setup() {
        store = FakeSessionStore()
        tool = SessionsHistoryTool(store)
    }

    @Nested
    inner class Execute {

        @Test
        fun `returns formatted message history`() = runTest {
            val key = SessionKey("main:main:*:main")
            store.createSession(key, "main")
            store.appendMessage(key, SessionMessage.UserMessage(
                id = "msg-1",
                timestamp = testInstant,
                senderId = "user1",
                senderName = "Alice",
                text = "Hello agent"
            ))
            store.appendMessage(key, SessionMessage.AssistantMessage(
                id = "msg-2",
                timestamp = testInstant,
                text = "Hello! How can I help?"
            ))

            val result = tool.execute(SessionsHistoryTool.Args(key = key.raw))

            result shouldContain "USER (Alice): Hello agent"
            result shouldContain "ASSISTANT: Hello! How can I help?"
        }

        @Test
        fun `respects limit parameter`() = runTest {
            val key = SessionKey("main:main:*:main")
            store.createSession(key, "main")
            repeat(10) { i ->
                store.appendMessage(key, SessionMessage.UserMessage(
                    id = "msg-$i",
                    timestamp = testInstant,
                    senderId = "user1",
                    senderName = "Alice",
                    text = "Message $i"
                ))
            }

            val result = tool.execute(SessionsHistoryTool.Args(key = key.raw, limit = 3))

            // Should contain last 3 messages (7, 8, 9) but not earlier ones
            result shouldContain "Message 7"
            result shouldContain "Message 8"
            result shouldContain "Message 9"
            result shouldNotContain "Message 0"
            result shouldNotContain "Message 6"
        }

        @Test
        fun `returns error for unknown session key`() = runTest {
            val result = tool.execute(SessionsHistoryTool.Args(key = "unknown:main:*:main"))
            result shouldBe "No messages found for session: unknown:main:*:main"
        }

        @Test
        fun `formats tool call messages`() = runTest {
            val key = SessionKey("main:main:*:main")
            store.createSession(key, "main")
            store.appendMessage(key, SessionMessage.ToolCall(
                id = "tc-1",
                timestamp = testInstant,
                toolName = "read",
                args = buildJsonObject { put("path", "/tmp/test.txt") },
                requestId = "req-1"
            ))
            store.appendMessage(key, SessionMessage.ToolResult(
                id = "tr-1",
                timestamp = testInstant,
                toolName = "read",
                result = "file contents here",
                requestId = "req-1",
                durationMs = 42
            ))

            val result = tool.execute(SessionsHistoryTool.Args(key = key.raw))

            result shouldContain "TOOL_CALL: read"
            result shouldContain "TOOL_RESULT: read -> file contents here (42ms)"
        }
    }
}
