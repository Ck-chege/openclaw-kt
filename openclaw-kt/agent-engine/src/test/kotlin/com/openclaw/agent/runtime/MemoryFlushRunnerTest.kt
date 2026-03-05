package com.openclaw.agent.runtime

import ai.koog.agents.core.tools.ToolRegistry
import com.openclaw.agent.model.ModelRegistry
import com.openclaw.protocol.identifiers.ModelId
import com.openclaw.protocol.identifiers.SessionKey
import com.openclaw.session.SessionMessage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MemoryFlushRunnerTest {

    private val testKey = SessionKey("test:main:*:main")

    private fun createRunner(): MemoryFlushRunner {
        val registry = ModelRegistry { null } // No API key — will fail on agent.run()
        return MemoryFlushRunner(registry, ToolRegistry.EMPTY)
    }

    @Nested
    inner class FlushPrompt {

        @Test
        fun `flush prompt contains today's date`() {
            val runner = createRunner()
            val prompt = runner.buildFlushPrompt()

            val today = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date.toString()

            prompt shouldContain "memory/$today.md"
        }

        @Test
        fun `flush prompt contains key instructions`() {
            val runner = createRunner()
            val prompt = runner.buildFlushPrompt()

            prompt shouldContain "Pre-compaction memory flush"
            prompt shouldContain "Store durable memories now"
            prompt shouldContain "APPEND new content only"
            prompt shouldContain "NO_REPLY"
        }

        @Test
        fun `flush prompt mentions creating memory directory`() {
            val runner = createRunner()
            val prompt = runner.buildFlushPrompt()

            prompt shouldContain "Create the memory/ directory if needed"
        }
    }

    @Nested
    inner class NoReplyDetection {

        @Test
        fun `detects NO_REPLY`() {
            val runner = createRunner()
            runner.isNoReply("NO_REPLY") shouldBe true
        }

        @Test
        fun `detects NO_REPLY case insensitive`() {
            val runner = createRunner()
            runner.isNoReply("no_reply") shouldBe true
            runner.isNoReply("No_Reply") shouldBe true
        }

        @Test
        fun `detects NO_REPLY with whitespace`() {
            val runner = createRunner()
            runner.isNoReply("  NO_REPLY  ") shouldBe true
            runner.isNoReply("\nNO_REPLY\n") shouldBe true
        }

        @Test
        fun `empty response is treated as no reply`() {
            val runner = createRunner()
            runner.isNoReply("") shouldBe true
            runner.isNoReply("   ") shouldBe true
        }

        @Test
        fun `actual content is not NO_REPLY`() {
            val runner = createRunner()
            runner.isNoReply("I saved the memories to file.") shouldBe false
            runner.isNoReply("NO_REPLY but also some content") shouldBe false
        }
    }

    @Nested
    inner class ContextSummary {

        @Test
        fun `empty history returns placeholder`() {
            val runner = createRunner()
            val summary = runner.buildContextSummary(emptyList())
            summary shouldBe "No conversation history."
        }

        @Test
        fun `includes user messages`() {
            val runner = createRunner()
            val history = listOf(
                SessionMessage.UserMessage(
                    id = "1", timestamp = Clock.System.now(),
                    senderId = "user", senderName = "User",
                    text = "Hello world"
                )
            )
            val summary = runner.buildContextSummary(history)
            summary shouldContain "User: Hello world"
        }

        @Test
        fun `includes assistant messages`() {
            val runner = createRunner()
            val history = listOf(
                SessionMessage.AssistantMessage(
                    id = "1", timestamp = Clock.System.now(),
                    text = "Hi there", model = "test"
                )
            )
            val summary = runner.buildContextSummary(history)
            summary shouldContain "Assistant: Hi there"
        }

        @Test
        fun `includes compaction summaries`() {
            val runner = createRunner()
            val history = listOf(
                SessionMessage.CompactionSummary(
                    id = "1", timestamp = Clock.System.now(),
                    summary = "Previous context", messagesCompacted = 5,
                    tokensBefore = 1000, tokensAfter = 200
                )
            )
            val summary = runner.buildContextSummary(history)
            summary shouldContain "Previous context"
        }

        @Test
        fun `limits to last 20 messages`() {
            val runner = createRunner()
            val history = (1..30).map { i ->
                SessionMessage.UserMessage(
                    id = "$i", timestamp = Clock.System.now(),
                    senderId = "user", senderName = "User",
                    text = "Message $i"
                )
            }
            val summary = runner.buildContextSummary(history)
            // Should not contain first 10 messages
            summary shouldContain "Message 11"
            summary shouldContain "Message 30"
        }
    }

    @Nested
    inner class RunFlush {

        @Test
        fun `returns Failed when model registry has no API key`() = runTest {
            val runner = createRunner()
            val history = listOf(
                SessionMessage.UserMessage(
                    id = "1", timestamp = Clock.System.now(),
                    senderId = "user", senderName = "User",
                    text = "Hello"
                )
            )

            val result = runner.runFlush(testKey, history)
            result.shouldBeInstanceOf<FlushResult.Failed>()
        }

        @Test
        fun `returns Failed with empty history gracefully`() = runTest {
            val runner = createRunner()
            val result = runner.runFlush(testKey, emptyList())
            // Will fail at agent.run() due to missing API key
            result.shouldBeInstanceOf<FlushResult.Failed>()
        }
    }
}
