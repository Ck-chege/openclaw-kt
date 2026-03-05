package com.openclaw.protocol

import com.openclaw.protocol.dto.*
import com.openclaw.protocol.identifiers.ChatType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DtoTest {

    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    // ------------------------------------------------------------------ //
    // InboundEnvelope
    // ------------------------------------------------------------------ //

    @Test
    fun inboundEnvelopeConstruction() {
        val envelope = InboundEnvelope(
            channel = "telegram",
            senderId = "user-1",
            senderName = "Alice",
            chatId = "chat-42",
            chatType = ChatType.GROUP,
            text = "Hello, world!"
        )

        assertEquals("telegram", envelope.channel)
        assertEquals("user-1", envelope.senderId)
        assertEquals("Alice", envelope.senderName)
        assertEquals("chat-42", envelope.chatId)
        assertEquals(ChatType.GROUP, envelope.chatType)
        assertEquals("Hello, world!", envelope.text)
    }

    @Test
    fun inboundEnvelopeDefaultValues() {
        val envelope = InboundEnvelope(
            channel = "discord",
            senderId = "u-2",
            senderName = "Bob",
            chatId = "c-1",
            chatType = ChatType.DM,
            text = "hi"
        )

        assertNull(envelope.replyToId)
        assertTrue(envelope.attachments.isEmpty())
        assertNull(envelope.raw)
    }

    @Test
    fun inboundEnvelopeRoundTrip() {
        val raw = buildJsonObject { put("update_id", 123) }
        val attachment = Attachment(
            type = AttachmentType.IMAGE,
            url = "https://example.com/photo.jpg",
            mimeType = "image/jpeg",
            filename = "photo.jpg",
            size = 1024L
        )
        val original = InboundEnvelope(
            channel = "telegram",
            senderId = "user-1",
            senderName = "Alice",
            chatId = "chat-42",
            chatType = ChatType.GROUP,
            text = "Look at this!",
            replyToId = "msg-99",
            attachments = listOf(attachment),
            raw = raw
        )

        val encoded = json.encodeToString(InboundEnvelope.serializer(), original)
        val decoded = json.decodeFromString(InboundEnvelope.serializer(), encoded)

        assertEquals(original, decoded)
        assertEquals(1, decoded.attachments.size)
        assertEquals(AttachmentType.IMAGE, decoded.attachments[0].type)
        assertEquals("photo.jpg", decoded.attachments[0].filename)
    }

    // ------------------------------------------------------------------ //
    // AgentEvent
    // ------------------------------------------------------------------ //

    @Test
    fun agentEventTokenDeltaRoundTrip() {
        val event: AgentEvent = AgentEvent.TokenDelta(text = "Hello")
        val encoded = json.encodeToString(AgentEvent.serializer(), event)
        val decoded = json.decodeFromString(AgentEvent.serializer(), encoded)

        assertIs<AgentEvent.TokenDelta>(decoded)
        assertEquals("Hello", decoded.text)
    }

    @Test
    fun agentEventToolCallStartRoundTrip() {
        val args = buildJsonObject { put("query", "weather") }
        val event: AgentEvent = AgentEvent.ToolCallStart(name = "search", args = args)
        val encoded = json.encodeToString(AgentEvent.serializer(), event)
        val decoded = json.decodeFromString(AgentEvent.serializer(), encoded)

        assertIs<AgentEvent.ToolCallStart>(decoded)
        assertEquals("search", decoded.name)
        assertEquals(args, decoded.args)
    }

    @Test
    fun agentEventToolCallResultRoundTrip() {
        val event: AgentEvent = AgentEvent.ToolCallResult(
            name = "search",
            result = "Sunny, 22C",
            durationMs = 350L
        )
        val encoded = json.encodeToString(AgentEvent.serializer(), event)
        val decoded = json.decodeFromString(AgentEvent.serializer(), encoded)

        assertIs<AgentEvent.ToolCallResult>(decoded)
        assertEquals("search", decoded.name)
        assertEquals("Sunny, 22C", decoded.result)
        assertEquals(350L, decoded.durationMs)
    }

    @Test
    fun agentEventRunCompleteRoundTrip() {
        val usage = TokenUsage(promptTokens = 100, completionTokens = 50, totalTokens = 150)
        val event: AgentEvent = AgentEvent.RunComplete(finalText = "Done!", usage = usage)
        val encoded = json.encodeToString(AgentEvent.serializer(), event)
        val decoded = json.decodeFromString(AgentEvent.serializer(), encoded)

        assertIs<AgentEvent.RunComplete>(decoded)
        assertEquals("Done!", decoded.finalText)
        assertEquals(100, decoded.usage.promptTokens)
        assertEquals(50, decoded.usage.completionTokens)
        assertEquals(150, decoded.usage.totalTokens)
    }

    @Test
    fun agentEventRunErrorRoundTrip() {
        val event: AgentEvent = AgentEvent.RunError(message = "Rate limited", code = "429")
        val encoded = json.encodeToString(AgentEvent.serializer(), event)
        val decoded = json.decodeFromString(AgentEvent.serializer(), encoded)

        assertIs<AgentEvent.RunError>(decoded)
        assertEquals("Rate limited", decoded.message)
        assertEquals("429", decoded.code)
    }

    @Test
    fun agentEventRunErrorDefaultCode() {
        val event = AgentEvent.RunError(message = "Unknown error")
        assertNull(event.code)
    }

    // ------------------------------------------------------------------ //
    // StopReason
    // ------------------------------------------------------------------ //

    @Test
    fun stopReasonToolUseRoundTrip() {
        val args = buildJsonObject { put("input", "test") }
        val call = ToolCallRequest(id = "tc-1", name = "eval", args = args)
        val reason: StopReason = StopReason.ToolUse(calls = listOf(call))

        val encoded = json.encodeToString(StopReason.serializer(), reason)
        val decoded = json.decodeFromString(StopReason.serializer(), encoded)

        assertIs<StopReason.ToolUse>(decoded)
        assertEquals(1, decoded.calls.size)
        assertEquals("tc-1", decoded.calls[0].id)
        assertEquals("eval", decoded.calls[0].name)
        assertEquals(args, decoded.calls[0].args)
    }

    @Test
    fun stopReasonEndTurnRoundTrip() {
        val reason: StopReason = StopReason.EndTurn
        val encoded = json.encodeToString(StopReason.serializer(), reason)
        val decoded = json.decodeFromString(StopReason.serializer(), encoded)

        assertIs<StopReason.EndTurn>(decoded)
    }

    @Test
    fun stopReasonTimeoutRoundTrip() {
        val reason: StopReason = StopReason.Timeout(elapsedMs = 30000L)
        val encoded = json.encodeToString(StopReason.serializer(), reason)
        val decoded = json.decodeFromString(StopReason.serializer(), encoded)

        assertIs<StopReason.Timeout>(decoded)
        assertEquals(30000L, decoded.elapsedMs)
    }

    @Test
    fun stopReasonErrorRoundTrip() {
        val reason: StopReason = StopReason.Error(message = "OOM", code = "MEMORY")
        val encoded = json.encodeToString(StopReason.serializer(), reason)
        val decoded = json.decodeFromString(StopReason.serializer(), encoded)

        assertIs<StopReason.Error>(decoded)
        assertEquals("OOM", decoded.message)
        assertEquals("MEMORY", decoded.code)
    }

    @Test
    fun stopReasonCompactionRoundTrip() {
        val reason: StopReason = StopReason.Compaction(tokensBefore = 8000, tokensAfter = 3000)
        val encoded = json.encodeToString(StopReason.serializer(), reason)
        val decoded = json.decodeFromString(StopReason.serializer(), encoded)

        assertIs<StopReason.Compaction>(decoded)
        assertEquals(8000, decoded.tokensBefore)
        assertEquals(3000, decoded.tokensAfter)
    }

    @Test
    fun stopReasonMaxIterationsRoundTrip() {
        val reason: StopReason = StopReason.MaxIterations
        val encoded = json.encodeToString(StopReason.serializer(), reason)
        val decoded = json.decodeFromString(StopReason.serializer(), encoded)

        assertIs<StopReason.MaxIterations>(decoded)
    }

    @Test
    fun stopReasonPolymorphicDiscrimination() {
        // Verify that different StopReason variants produce distinct JSON
        val endTurn = json.encodeToString(StopReason.serializer(), StopReason.EndTurn)
        val maxIter = json.encodeToString(StopReason.serializer(), StopReason.MaxIterations)
        val timeout = json.encodeToString(StopReason.serializer(), StopReason.Timeout(elapsedMs = 1000L))

        // All three should be different JSON strings
        assertTrue(endTurn != maxIter)
        assertTrue(endTurn != timeout)
        assertTrue(maxIter != timeout)

        // Each should deserialize back to the correct type
        assertIs<StopReason.EndTurn>(json.decodeFromString(StopReason.serializer(), endTurn))
        assertIs<StopReason.MaxIterations>(json.decodeFromString(StopReason.serializer(), maxIter))
        assertIs<StopReason.Timeout>(json.decodeFromString(StopReason.serializer(), timeout))
    }

    // ------------------------------------------------------------------ //
    // QueueAction
    // ------------------------------------------------------------------ //

    @Test
    fun queueActionNewMessageRoundTrip() {
        val envelope = InboundEnvelope(
            channel = "slack",
            senderId = "u-1",
            senderName = "Eve",
            chatId = "c-1",
            chatType = ChatType.CHANNEL,
            text = "deploy"
        )
        val action: QueueAction = QueueAction.NewMessage(envelope = envelope)
        val encoded = json.encodeToString(QueueAction.serializer(), action)
        val decoded = json.decodeFromString(QueueAction.serializer(), encoded)

        assertIs<QueueAction.NewMessage>(decoded)
        assertEquals("slack", decoded.envelope.channel)
        assertEquals("deploy", decoded.envelope.text)
    }

    @Test
    fun queueActionSteerRoundTrip() {
        val envelope = InboundEnvelope(
            channel = "discord",
            senderId = "u-2",
            senderName = "Frank",
            chatId = "c-2",
            chatType = ChatType.THREAD,
            text = "focus on tests"
        )
        val action: QueueAction = QueueAction.Steer(envelope = envelope)
        val encoded = json.encodeToString(QueueAction.serializer(), action)
        val decoded = json.decodeFromString(QueueAction.serializer(), encoded)

        assertIs<QueueAction.Steer>(decoded)
        assertEquals("focus on tests", decoded.envelope.text)
    }

    @Test
    fun queueActionCancelRoundTrip() {
        val action: QueueAction = QueueAction.Cancel
        val encoded = json.encodeToString(QueueAction.serializer(), action)
        val decoded = json.decodeFromString(QueueAction.serializer(), encoded)

        assertIs<QueueAction.Cancel>(decoded)
    }

    // ------------------------------------------------------------------ //
    // ToolPermission
    // ------------------------------------------------------------------ //

    @Test
    fun toolPermissionAllowedRoundTrip() {
        val perm: ToolPermission = ToolPermission.Allowed
        val encoded = json.encodeToString(ToolPermission.serializer(), perm)
        val decoded = json.decodeFromString(ToolPermission.serializer(), encoded)

        assertIs<ToolPermission.Allowed>(decoded)
    }

    @Test
    fun toolPermissionDeniedRoundTrip() {
        val perm: ToolPermission = ToolPermission.Denied(reason = "Not in allowlist")
        val encoded = json.encodeToString(ToolPermission.serializer(), perm)
        val decoded = json.decodeFromString(ToolPermission.serializer(), encoded)

        assertIs<ToolPermission.Denied>(decoded)
        assertEquals("Not in allowlist", decoded.reason)
    }

    @Test
    fun toolPermissionRequiresApprovalRoundTrip() {
        val perm: ToolPermission = ToolPermission.RequiresApproval(prompt = "Allow file write?")
        val encoded = json.encodeToString(ToolPermission.serializer(), perm)
        val decoded = json.decodeFromString(ToolPermission.serializer(), encoded)

        assertIs<ToolPermission.RequiresApproval>(decoded)
        assertEquals("Allow file write?", decoded.prompt)
    }

    // ------------------------------------------------------------------ //
    // OutboundMessage
    // ------------------------------------------------------------------ //

    @Test
    fun outboundMessageConstruction() {
        val msg = OutboundMessage(text = "Reply text")

        assertEquals("Reply text", msg.text)
        assertNull(msg.replyToId)
        assertTrue(msg.attachments.isEmpty())
        assertEquals(MessageFormat.MARKDOWN, msg.format)
    }

    @Test
    fun outboundMessageRoundTrip() {
        val attachment = Attachment(
            type = AttachmentType.FILE,
            url = "https://example.com/report.pdf",
            mimeType = "application/pdf",
            filename = "report.pdf",
            size = 2048L
        )
        val original = OutboundMessage(
            text = "Here is your report",
            replyToId = "msg-100",
            attachments = listOf(attachment),
            format = MessageFormat.HTML
        )

        val encoded = json.encodeToString(OutboundMessage.serializer(), original)
        val decoded = json.decodeFromString(OutboundMessage.serializer(), encoded)

        assertEquals(original, decoded)
        assertEquals(MessageFormat.HTML, decoded.format)
        assertEquals(1, decoded.attachments.size)
        assertEquals("report.pdf", decoded.attachments[0].filename)
    }

    @Test
    fun outboundTargetRoundTrip() {
        val original = OutboundTarget(
            channel = "telegram",
            chatId = "chat-42",
            threadId = "thread-7"
        )

        val encoded = json.encodeToString(OutboundTarget.serializer(), original)
        val decoded = json.decodeFromString(OutboundTarget.serializer(), encoded)

        assertEquals(original, decoded)
        assertEquals("thread-7", decoded.threadId)
    }

    @Test
    fun outboundTargetDefaultThreadId() {
        val target = OutboundTarget(channel = "slack", chatId = "c-1")
        assertNull(target.threadId)
    }

    // ------------------------------------------------------------------ //
    // TokenUsage defaults
    // ------------------------------------------------------------------ //

    @Test
    fun tokenUsageDefaults() {
        val usage = TokenUsage()
        assertEquals(0, usage.promptTokens)
        assertEquals(0, usage.completionTokens)
        assertEquals(0, usage.totalTokens)
    }
}
