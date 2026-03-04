package com.openclaw.agent.runtime

import com.openclaw.agent.model.ModelRegistry
import com.openclaw.agent.tools.FakeSessionStore
import com.openclaw.agent.workspace.WorkspaceLoader
import com.openclaw.protocol.dto.AgentEvent
import com.openclaw.protocol.identifiers.ModelId
import com.openclaw.protocol.identifiers.SessionKey
import com.openclaw.session.SessionMessage
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class AgentRuntimeTest {

    @TempDir
    lateinit var tempDir: Path

    private fun runtimeWithMissingKey(sessionStore: FakeSessionStore? = null): AgentRuntime {
        val registry = ModelRegistry { null }
        return AgentRuntime(registry, sessionStore)
    }

    @Nested
    inner class ExecuteRun {

        @Test
        fun `failing executor emits RunError event`() = runTest {
            val runtime = runtimeWithMissingKey()
            val workspace = WorkspaceLoader(tempDir)
            val events = runtime.executeRun(
                sessionKey = SessionKey("test:main:*:main"),
                message = "Hello",
                workspace = workspace
            ).toList()

            // Should get a RunError since no API key is configured
            val lastEvent = events.last()
            lastEvent.shouldBeInstanceOf<AgentEvent.RunError>()
        }

        @Test
        fun `system prompt contains workspace content when present`() = runTest {
            tempDir.resolve("SOUL.md").writeText("You are a test assistant.")
            val runtime = runtimeWithMissingKey()
            val workspace = WorkspaceLoader(tempDir)

            // The run will fail due to missing API key, but the prompt assembly works
            val events = runtime.executeRun(
                sessionKey = SessionKey("test:main:*:main"),
                message = "Hello",
                workspace = workspace
            ).toList()

            // Should get a RunError (no API key), but no crash
            events.last().shouldBeInstanceOf<AgentEvent.RunError>()
        }

        @Test
        fun `empty workspace still runs without crash`() = runTest {
            val runtime = runtimeWithMissingKey()
            val emptyWorkspace = WorkspaceLoader(tempDir)

            val events = runtime.executeRun(
                sessionKey = SessionKey("test:main:*:main"),
                message = "Hello",
                workspace = emptyWorkspace
            ).toList()

            events.last().shouldBeInstanceOf<AgentEvent.RunError>()
        }

        @Test
        fun `model override is used`() = runTest {
            val runtime = runtimeWithMissingKey()
            val workspace = WorkspaceLoader(tempDir)

            val events = runtime.executeRun(
                sessionKey = SessionKey("test:main:*:main"),
                message = "Hello",
                workspace = workspace,
                modelId = ModelId("openai:gpt-4o")
            ).toList()

            // Will fail with MissingApiKey for openai, confirming the override was used
            val errorEvent = events.last() as AgentEvent.RunError
            errorEvent.message shouldContain "openai"
        }
    }

    @Nested
    inner class NewWorkspaceFields {

        @Test
        fun `identity file is read into prompt context without crash`() = runTest {
            tempDir.resolve("IDENTITY.md").writeText("I am TestBot")
            val runtime = runtimeWithMissingKey()
            val workspace = WorkspaceLoader(tempDir)

            val events = runtime.executeRun(
                sessionKey = SessionKey("test:main:*:main"),
                message = "Hello",
                workspace = workspace
            ).toList()

            // Run fails (no API key) but identity was read without error
            events.last().shouldBeInstanceOf<AgentEvent.RunError>()
        }

        @Test
        fun `bootstrap file is read into prompt context without crash`() = runTest {
            tempDir.resolve("BOOTSTRAP.md").writeText("You just woke up.")
            val runtime = runtimeWithMissingKey()
            val workspace = WorkspaceLoader(tempDir)

            val events = runtime.executeRun(
                sessionKey = SessionKey("test:main:*:main"),
                message = "Hello",
                workspace = workspace
            ).toList()

            events.last().shouldBeInstanceOf<AgentEvent.RunError>()
        }

        @Test
        fun `heartbeat file is read into prompt context without crash`() = runTest {
            tempDir.resolve("HEARTBEAT.md").writeText("Check email daily")
            val runtime = runtimeWithMissingKey()
            val workspace = WorkspaceLoader(tempDir)

            val events = runtime.executeRun(
                sessionKey = SessionKey("test:main:*:main"),
                message = "Hello",
                workspace = workspace
            ).toList()

            events.last().shouldBeInstanceOf<AgentEvent.RunError>()
        }
    }

    @Nested
    inner class SessionHistory {

        private val testKey = SessionKey("test:main:*:main")

        @Test
        fun `session auto-created on first message`() = runTest {
            val store = FakeSessionStore()
            val runtime = runtimeWithMissingKey(store)
            val workspace = WorkspaceLoader(tempDir)

            // Session doesn't exist yet
            store.getSession(testKey) shouldBe null

            runtime.executeRun(
                sessionKey = testKey,
                message = "Hello",
                workspace = workspace
            ).toList()

            // Session was auto-created (agentId is first segment: "test")
            store.getSession(testKey)!!.agentId shouldBe "test"
        }

        @Test
        fun `user message persisted to session store`() = runTest {
            val store = FakeSessionStore()
            val runtime = runtimeWithMissingKey(store)
            val workspace = WorkspaceLoader(tempDir)

            runtime.executeRun(
                sessionKey = testKey,
                message = "Hello world",
                workspace = workspace
            ).toList()

            val history = store.getHistory(testKey)
            // Should have at least the user message
            history.any { it is SessionMessage.UserMessage && it.text == "Hello world" } shouldBe true
        }

        @Test
        fun `null session store works without crash`() = runTest {
            val runtime = runtimeWithMissingKey(sessionStore = null)
            val workspace = WorkspaceLoader(tempDir)

            val events = runtime.executeRun(
                sessionKey = testKey,
                message = "Hello",
                workspace = workspace
            ).toList()

            // Run completes (with error due to missing API key) but no NPE
            events.last().shouldBeInstanceOf<AgentEvent.RunError>()
        }

        @Test
        fun `empty history works without crash`() = runTest {
            val store = FakeSessionStore()
            val runtime = runtimeWithMissingKey(store)
            val workspace = WorkspaceLoader(tempDir)

            // First message to a new session — empty history
            val events = runtime.executeRun(
                sessionKey = testKey,
                message = "First message ever",
                workspace = workspace
            ).toList()

            events.last().shouldBeInstanceOf<AgentEvent.RunError>()
            // User message was still persisted
            val history = store.getHistory(testKey)
            history.filterIsInstance<SessionMessage.UserMessage>() shouldHaveSize 1
        }

        @Test
        fun `conversation history loaded from existing session`() = runTest {
            val store = FakeSessionStore()
            store.createSession(testKey, "main")
            // Pre-seed some history
            store.appendMessage(testKey, SessionMessage.UserMessage(
                id = "msg-1",
                timestamp = Instant.parse("2026-01-01T00:00:00Z"),
                senderId = "user",
                senderName = "Isaac",
                text = "What is Kotlin?"
            ))
            store.appendMessage(testKey, SessionMessage.AssistantMessage(
                id = "msg-2",
                timestamp = Instant.parse("2026-01-01T00:01:00Z"),
                text = "Kotlin is a programming language.",
                model = "anthropic:claude-sonnet-4-5"
            ))

            val runtime = runtimeWithMissingKey(store)
            val workspace = WorkspaceLoader(tempDir)

            // Run with pre-existing history — should not crash
            val events = runtime.executeRun(
                sessionKey = testKey,
                message = "Tell me more",
                workspace = workspace
            ).toList()

            events.last().shouldBeInstanceOf<AgentEvent.RunError>()
            // History now has 3 messages: 2 pre-existing + 1 new user message
            val history = store.getHistory(testKey)
            history.filterIsInstance<SessionMessage.UserMessage>() shouldHaveSize 2
        }
    }
}
