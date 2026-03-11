package com.kclaw.agent.strategy

import com.kclaw.protocol.dto.AgentEvent
import com.kclaw.protocol.dto.TokenUsage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class StreamBridgeTest {

    @Nested
    inner class EventEmission {

        @Test
        fun `onTokenDelta emits TokenDelta event`() = runTest {
            val bridge = StreamBridge()
            val job = launch {
                val event = bridge.events.first()
                event.shouldBeInstanceOf<AgentEvent.TokenDelta>()
                (event as AgentEvent.TokenDelta).text shouldBe "hello"
            }
            yield() // let collector subscribe before emitting
            bridge.onTokenDelta("hello")
            job.join()
        }

        @Test
        fun `onToolCallStart emits ToolCallStart event`() = runTest {
            val bridge = StreamBridge()
            val args = buildJsonObject { put("query", "test") }
            val job = launch {
                val event = bridge.events.first()
                event.shouldBeInstanceOf<AgentEvent.ToolCallStart>()
                val tcEvent = event as AgentEvent.ToolCallStart
                tcEvent.name shouldBe "search"
                tcEvent.args shouldBe args
            }
            yield()
            bridge.onToolCallStart("search", args)
            job.join()
        }

        @Test
        fun `onToolCallResult emits ToolCallResult event`() = runTest {
            val bridge = StreamBridge()
            val job = launch {
                val event = bridge.events.first()
                event.shouldBeInstanceOf<AgentEvent.ToolCallResult>()
                val tcEvent = event as AgentEvent.ToolCallResult
                tcEvent.name shouldBe "search"
                tcEvent.result shouldBe "found it"
                tcEvent.durationMs shouldBe 42L
            }
            yield()
            bridge.onToolCallResult("search", "found it", 42L)
            job.join()
        }

        @Test
        fun `onRunComplete emits RunComplete event`() = runTest {
            val bridge = StreamBridge()
            val usage = TokenUsage(100, 50, 150)
            val job = launch {
                val event = bridge.events.first()
                event.shouldBeInstanceOf<AgentEvent.RunComplete>()
                val rcEvent = event as AgentEvent.RunComplete
                rcEvent.finalText shouldBe "Done"
                rcEvent.usage shouldBe usage
            }
            yield()
            bridge.onRunComplete("Done", usage)
            job.join()
        }

        @Test
        fun `onRunError emits RunError event`() = runTest {
            val bridge = StreamBridge()
            val job = launch {
                val event = bridge.events.first()
                event.shouldBeInstanceOf<AgentEvent.RunError>()
                val reEvent = event as AgentEvent.RunError
                reEvent.message shouldBe "Something failed"
                reEvent.code shouldBe "ERR_500"
            }
            yield()
            bridge.onRunError("Something failed", "ERR_500")
            job.join()
        }
    }

    @Nested
    inner class MultipleEmissions {

        @Test
        fun `multiple rapid emissions all received`() = runTest {
            val bridge = StreamBridge()
            val job = launch {
                val events = bridge.events.take(3).toList()
                events.size shouldBe 3
                events[0].shouldBeInstanceOf<AgentEvent.TokenDelta>()
                events[1].shouldBeInstanceOf<AgentEvent.TokenDelta>()
                events[2].shouldBeInstanceOf<AgentEvent.RunComplete>()
            }
            yield()
            bridge.onTokenDelta("hello ")
            bridge.onTokenDelta("world")
            bridge.onRunComplete("hello world", TokenUsage())
            job.join()
        }
    }
}
