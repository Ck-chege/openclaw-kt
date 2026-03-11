package com.kclaw.agent.tools

import com.kclaw.protocol.identifiers.SessionKey
import com.kclaw.session.SessionMessage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SessionStatusToolTest {

    private lateinit var store: FakeSessionStore
    private val sessionKey = SessionKey("main:main:*:main")
    private val testInstant = Instant.parse("2026-01-15T10:30:00Z")

    @BeforeEach
    fun setup() {
        store = FakeSessionStore()
    }

    @Nested
    inner class Execute {

        @Test
        fun `returns current session metadata`() = runTest {
            store.createSession(sessionKey, "main")
            store.updateMeta(sessionKey) { it.copy(modelId = "anthropic:claude-sonnet-4-5", totalTokens = 1500) }
            store.appendMessage(sessionKey, SessionMessage.UserMessage(
                id = "msg-1",
                timestamp = testInstant,
                senderId = "user1",
                senderName = "Alice",
                text = "Hello"
            ))

            val tool = SessionStatusTool(store) { sessionKey }
            val result = tool.execute(SessionStatusTool.Args())

            result shouldContain "Session: main:main:*:main"
            result shouldContain "Agent: main"
            result shouldContain "Messages: 1"
            result shouldContain "Model: anthropic:claude-sonnet-4-5"
            result shouldContain "Total tokens: 1500"
            result shouldContain "Compactions: 0"
        }

        @Test
        fun `handles missing session gracefully`() = runTest {
            // Don't create the session -- it doesn't exist in the store
            val tool = SessionStatusTool(store) { sessionKey }
            val result = tool.execute(SessionStatusTool.Args())

            result shouldBe "No session metadata available"
        }

        @Test
        fun `shows default when model is not set`() = runTest {
            store.createSession(sessionKey, "main")

            val tool = SessionStatusTool(store) { sessionKey }
            val result = tool.execute(SessionStatusTool.Args())

            result shouldContain "Model: default"
        }
    }
}
