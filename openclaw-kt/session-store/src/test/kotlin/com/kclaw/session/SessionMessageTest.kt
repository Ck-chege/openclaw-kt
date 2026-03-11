package com.kclaw.session

import com.kclaw.protocol.dto.TokenUsage
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class SessionMessageTest : FunSpec({

    val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
    }
    val now = Clock.System.now()

    test("UserMessage round-trip serialization") {
        val msg: SessionMessage = SessionMessage.UserMessage(
            id = "u1",
            timestamp = now,
            senderId = "peer-42",
            senderName = "Alice",
            text = "Hello, agent!",
            channel = "telegram"
        )
        val encoded = json.encodeToString(SessionMessage.serializer(), msg)
        val decoded = json.decodeFromString(SessionMessage.serializer(), encoded)
        decoded shouldBe msg
    }

    test("AssistantMessage round-trip serialization") {
        val msg: SessionMessage = SessionMessage.AssistantMessage(
            id = "a1",
            timestamp = now,
            text = "Hi there!",
            model = "anthropic:claude-sonnet-4-5",
            usage = TokenUsage(promptTokens = 100, completionTokens = 50, totalTokens = 150)
        )
        val encoded = json.encodeToString(SessionMessage.serializer(), msg)
        val decoded = json.decodeFromString(SessionMessage.serializer(), encoded)
        decoded shouldBe msg
    }

    test("AssistantMessage without optional fields round-trips") {
        val msg: SessionMessage = SessionMessage.AssistantMessage(
            id = "a2",
            timestamp = now,
            text = "bare response"
        )
        val encoded = json.encodeToString(SessionMessage.serializer(), msg)
        val decoded = json.decodeFromString(SessionMessage.serializer(), encoded)
        decoded shouldBe msg
    }

    test("ToolCall round-trip serialization") {
        val args = JsonObject(mapOf("query" to JsonPrimitive("weather in Nairobi")))
        val msg: SessionMessage = SessionMessage.ToolCall(
            id = "tc1",
            timestamp = now,
            toolName = "web_search",
            args = args,
            requestId = "req-001"
        )
        val encoded = json.encodeToString(SessionMessage.serializer(), msg)
        val decoded = json.decodeFromString(SessionMessage.serializer(), encoded)
        decoded shouldBe msg
    }

    test("ToolResult round-trip serialization") {
        val msg: SessionMessage = SessionMessage.ToolResult(
            id = "tr1",
            timestamp = now,
            toolName = "web_search",
            result = """{"temp": 22}""",
            requestId = "req-001",
            durationMs = 340
        )
        val encoded = json.encodeToString(SessionMessage.serializer(), msg)
        val decoded = json.decodeFromString(SessionMessage.serializer(), encoded)
        decoded shouldBe msg
    }

    test("SystemEvent round-trip serialization") {
        val data = JsonObject(mapOf("reason" to JsonPrimitive("timeout")))
        val msg: SessionMessage = SessionMessage.SystemEvent(
            id = "se1",
            timestamp = now,
            event = "session_paused",
            data = data
        )
        val encoded = json.encodeToString(SessionMessage.serializer(), msg)
        val decoded = json.decodeFromString(SessionMessage.serializer(), encoded)
        decoded shouldBe msg
    }

    test("SystemEvent with null data round-trips") {
        val msg: SessionMessage = SessionMessage.SystemEvent(
            id = "se2",
            timestamp = now,
            event = "heartbeat"
        )
        val encoded = json.encodeToString(SessionMessage.serializer(), msg)
        val decoded = json.decodeFromString(SessionMessage.serializer(), encoded)
        decoded shouldBe msg
    }

    test("CompactionSummary round-trip serialization") {
        val msg: SessionMessage = SessionMessage.CompactionSummary(
            id = "cs1",
            timestamp = now,
            summary = "User asked about weather. Agent used web_search tool.",
            messagesCompacted = 12,
            tokensBefore = 4000,
            tokensAfter = 500
        )
        val encoded = json.encodeToString(SessionMessage.serializer(), msg)
        val decoded = json.decodeFromString(SessionMessage.serializer(), encoded)
        decoded shouldBe msg
    }

    test("SessionArchiveRecord round-trip serialization") {
        val record = SessionArchiveRecord(
            sessionKey = com.kclaw.protocol.identifiers.SessionKey("main:main:*:main"),
            archivedFileId = "archive-1",
            archivedPath = "/tmp/archive-1.compacted.2026-03-09T14-59-48.909Z.jsonl",
            previousArchiveFileId = "archive-0",
            compactedAt = now,
            messagesCompacted = 12,
            tokensBefore = 4000,
            tokensAfter = 500
        )
        val encoded = json.encodeToString(record)
        val decoded = json.decodeFromString<SessionArchiveRecord>(encoded)
        decoded shouldBe record
    }

    test("type discriminator is present in JSON output") {
        val msg: SessionMessage = SessionMessage.UserMessage(
            id = "u2",
            timestamp = now,
            senderId = "peer-1",
            senderName = "Bob",
            text = "Test"
        )
        val encoded = json.encodeToString(SessionMessage.serializer(), msg)
        encoded.contains(""""type":"user"""") shouldBe true
    }

    test("ignoreUnknownKeys allows forward compatibility") {
        // Simulate a future field being present in JSON
        val raw = """{"type":"user","id":"u3","timestamp":"${now}","senderId":"x","senderName":"Y","text":"hi","futureField":true}"""
        val decoded = json.decodeFromString(SessionMessage.serializer(), raw)
        (decoded as SessionMessage.UserMessage).text shouldBe "hi"
    }
})
