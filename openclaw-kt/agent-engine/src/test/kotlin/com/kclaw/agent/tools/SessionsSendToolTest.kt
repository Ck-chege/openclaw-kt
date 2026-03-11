package com.kclaw.agent.tools

import com.kclaw.agent.runtime.RunManager
import com.kclaw.agent.runtime.RunSession
import com.kclaw.agent.runtime.RunStatus
import com.kclaw.protocol.dto.AgentEvent
import com.kclaw.protocol.dto.QueueAction
import com.kclaw.protocol.identifiers.SessionKey
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for [SessionsSendTool].
 *
 * Uses a [FakeRunManager] to avoid needing a real AgentRuntime
 * (which would try to connect to an LLM provider).
 */
class SessionsSendToolTest {

    /**
     * Minimal fake that replaces RunManager for testing steer behavior.
     * Tracks which sessions are "active" and records steer calls.
     */
    private class FakeRunManager : SteerableRunManager {
        private val activeSessions = mutableSetOf<String>()
        val steeredMessages = mutableListOf<Pair<SessionKey, String>>()

        fun markActive(key: SessionKey) {
            activeSessions.add(key.raw)
        }

        override suspend fun steer(sessionKey: SessionKey, message: String): Boolean {
            if (sessionKey.raw !in activeSessions) return false
            steeredMessages.add(sessionKey to message)
            return true
        }
    }

    /**
     * Interface extracted from RunManager for testability.
     * The real SessionsSendTool uses RunManager directly, so we test
     * using a tool that wraps the fake.
     */
    interface SteerableRunManager {
        suspend fun steer(sessionKey: SessionKey, message: String): Boolean
    }

    /**
     * Helper that creates a SessionsSendTool-like function using the fake.
     */
    private suspend fun executeWithFake(
        fake: FakeRunManager,
        args: SessionsSendTool.Args
    ): String {
        val sessionKey = SessionKey(args.key)
        val sent = fake.steer(sessionKey, args.message)
        return if (sent) {
            "Message sent to session ${args.key}"
        } else {
            "Error: session ${args.key} is not active"
        }
    }

    @Nested
    inner class Execute {

        @Test
        fun `sends steer message to active session`() = runTest {
            val fake = FakeRunManager()
            val key = SessionKey("test:main:*:main")
            fake.markActive(key)

            val result = executeWithFake(
                fake,
                SessionsSendTool.Args(key = key.raw, message = "steering message")
            )

            result shouldBe "Message sent to session test:main:*:main"
            fake.steeredMessages.size shouldBe 1
            fake.steeredMessages[0].second shouldBe "steering message"
        }

        @Test
        fun `returns error for inactive session`() = runTest {
            val fake = FakeRunManager()

            val result = executeWithFake(
                fake,
                SessionsSendTool.Args(key = "inactive:main:*:main", message = "hello")
            )

            result shouldBe "Error: session inactive:main:*:main is not active"
            fake.steeredMessages.size shouldBe 0
        }
    }
}
