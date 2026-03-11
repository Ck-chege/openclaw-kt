package com.kclaw.protocol

import com.kclaw.protocol.identifiers.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IdentifiersTest {

    // ── SessionKey: parsing ─────────────────────────────────────────────

    @Test
    fun sessionKey_parses_agentId() {
        val key = SessionKey("main:telegram:*:dm:6614828781")
        assertEquals("main", key.agentId)
    }

    @Test
    fun sessionKey_parses_channel() {
        val key = SessionKey("main:telegram:*:dm:6614828781")
        assertEquals("telegram", key.channel)
    }

    @Test
    fun sessionKey_parses_scope() {
        val key = SessionKey("main:telegram:*:dm:6614828781")
        assertEquals("*", key.scope)
    }

    @Test
    fun sessionKey_parses_peerId_for_dm() {
        val key = SessionKey("main:telegram:*:dm:6614828781")
        assertEquals("6614828781", key.peerId)
    }

    @Test
    fun sessionKey_parses_peerId_for_non_dm() {
        val key = SessionKey("main:discord:channel:14775920")
        assertEquals("14775920", key.peerId)
    }

    @Test
    fun sessionKey_segments() {
        val key = SessionKey("main:main:*:main")
        assertEquals(listOf("main", "main", "*", "main"), key.segments)
    }

    @Test
    fun sessionKey_isSubagent_true() {
        val key = SessionKey("trading:main:subagent:abc123")
        assertTrue(key.isSubagent)
    }

    @Test
    fun sessionKey_isSubagent_false() {
        val key = SessionKey("main:main:*:main")
        assertFalse(key.isSubagent)
    }

    @Test
    fun sessionKey_isCron_true() {
        val key = SessionKey("main:main:cron:9467cf95")
        assertTrue(key.isCron)
    }

    @Test
    fun sessionKey_isCron_false() {
        val key = SessionKey("main:main:*:main")
        assertFalse(key.isCron)
    }

    // ── SessionKey: factory methods ─────────────────────────────────────

    @Test
    fun sessionKey_main_default() {
        val key = SessionKey.main()
        assertEquals("main:main:*:main", key.raw)
    }

    @Test
    fun sessionKey_main_custom_agent() {
        val key = SessionKey.main("trading")
        assertEquals("trading:main:*:main", key.raw)
    }

    @Test
    fun sessionKey_dm_factory() {
        val key = SessionKey.dm("main", "telegram", "6614828781")
        assertEquals("main:telegram:*:dm:6614828781", key.raw)
        assertEquals("6614828781", key.peerId)
    }

    @Test
    fun sessionKey_cron_factory() {
        val key = SessionKey.cron("main", "9467cf95")
        assertEquals("main:main:cron:9467cf95", key.raw)
        assertTrue(key.isCron)
    }

    @Test
    fun sessionKey_subagent_factory() {
        val parent = SessionKey.main("trading")
        val key = SessionKey.subagent(parent, "abc123")
        assertEquals("trading:main:subagent:abc123", key.raw)
        assertTrue(key.isSubagent)
    }

    // ── SessionKey: invalid format ──────────────────────────────────────

    @Test
    fun sessionKey_rejects_no_colon() {
        assertFailsWith<IllegalArgumentException> {
            SessionKey("nocolon")
        }
    }

    @Test
    fun sessionKey_rejects_less_than_four_segments() {
        assertFailsWith<IllegalArgumentException> {
            SessionKey("a:b:c")
        }
    }

    @Test
    fun sessionKey_rejects_only_three_segments() {
        assertFailsWith<IllegalArgumentException> {
            SessionKey("main:main:only-three")
        }
    }

    // ── SessionKey: serialization round-trip ────────────────────────────

    @Test
    fun sessionKey_serialization_roundTrip() {
        val key = SessionKey("main:telegram:*:dm:6614828781")
        val jsonStr = Json.encodeToString(key)
        val decoded = Json.decodeFromString<SessionKey>(jsonStr)
        assertEquals(key, decoded)
    }

    // ── AgentId ─────────────────────────────────────────────────────────

    @Test
    fun agentId_rejects_blank() {
        assertFailsWith<IllegalArgumentException> {
            AgentId("")
        }
        assertFailsWith<IllegalArgumentException> {
            AgentId("   ")
        }
    }

    @Test
    fun agentId_toString() {
        assertEquals("main", AgentId("main").toString())
    }

    @Test
    fun agentId_serialization_roundTrip() {
        val id = AgentId("trading-bot")
        val jsonStr = Json.encodeToString(id)
        val decoded = Json.decodeFromString<AgentId>(jsonStr)
        assertEquals(id, decoded)
        assertEquals("trading-bot", decoded.raw)
    }

    // ── ModelId ─────────────────────────────────────────────────────────

    @Test
    fun modelId_provider_and_model_with_colon() {
        val id = ModelId("anthropic:claude-sonnet-4-5")
        assertEquals("anthropic", id.provider)
        assertEquals("claude-sonnet-4-5", id.model)
    }

    @Test
    fun modelId_provider_and_model_without_colon() {
        val id = ModelId("gpt-4o")
        assertEquals("gpt-4o", id.provider)
        assertEquals("gpt-4o", id.model)
    }

    @Test
    fun modelId_rejects_blank() {
        assertFailsWith<IllegalArgumentException> {
            ModelId("")
        }
    }

    @Test
    fun modelId_serialization_roundTrip() {
        val id = ModelId("anthropic:claude-sonnet-4-5")
        val jsonStr = Json.encodeToString(id)
        val decoded = Json.decodeFromString<ModelId>(jsonStr)
        assertEquals(id, decoded)
        assertEquals("anthropic", decoded.provider)
    }

    // ── TokenCount ──────────────────────────────────────────────────────

    @Test
    fun tokenCount_plus() {
        val a = TokenCount(100)
        val b = TokenCount(50)
        assertEquals(TokenCount(150), a + b)
    }

    @Test
    fun tokenCount_minus() {
        val a = TokenCount(100)
        val b = TokenCount(40)
        assertEquals(TokenCount(60), a - b)
    }

    @Test
    fun tokenCount_compareTo() {
        val small = TokenCount(10)
        val big = TokenCount(1000)
        assertTrue(small.compareTo(big) < 0)
        assertTrue(big.compareTo(small) > 0)
        assertEquals(0, small.compareTo(TokenCount(10)))
    }

    @Test
    fun tokenCount_exceedsLimit() {
        val count = TokenCount(150)
        assertTrue(count.exceedsLimit(TokenCount(100)))
        assertFalse(count.exceedsLimit(TokenCount(200)))
        assertFalse(count.exceedsLimit(TokenCount(150)))
    }

    @Test
    fun tokenCount_rejects_negative() {
        assertFailsWith<IllegalArgumentException> {
            TokenCount(-1)
        }
    }

    @Test
    fun tokenCount_allows_zero() {
        assertEquals(0, TokenCount(0).count)
    }

    @Test
    fun tokenCount_toString() {
        assertEquals("42 tokens", TokenCount(42).toString())
    }

    @Test
    fun tokenCount_serialization_roundTrip() {
        val tc = TokenCount(500)
        val jsonStr = Json.encodeToString(tc)
        val decoded = Json.decodeFromString<TokenCount>(jsonStr)
        assertEquals(tc, decoded)
    }

    // ── Enums: serialization round-trips ────────────────────────────────

    @Test
    fun sessionScope_serialization_roundTrip() {
        for (value in SessionScope.entries) {
            val jsonStr = Json.encodeToString(value)
            val decoded = Json.decodeFromString<SessionScope>(jsonStr)
            assertEquals(value, decoded)
        }
    }

    @Test
    fun chatType_serialization_roundTrip() {
        for (value in ChatType.entries) {
            val jsonStr = Json.encodeToString(value)
            val decoded = Json.decodeFromString<ChatType>(jsonStr)
            assertEquals(value, decoded)
        }
    }

    @Test
    fun operatorLevel_serialization_roundTrip() {
        for (value in OperatorLevel.entries) {
            val jsonStr = Json.encodeToString(value)
            val decoded = Json.decodeFromString<OperatorLevel>(jsonStr)
            assertEquals(value, decoded)
        }
    }

    @Test
    fun dmPolicy_serialization_roundTrip() {
        for (value in DmPolicy.entries) {
            val jsonStr = Json.encodeToString(value)
            val decoded = Json.decodeFromString<DmPolicy>(jsonStr)
            assertEquals(value, decoded)
        }
    }

    @Test
    fun queueMode_serialization_roundTrip() {
        for (value in QueueMode.entries) {
            val jsonStr = Json.encodeToString(value)
            val decoded = Json.decodeFromString<QueueMode>(jsonStr)
            assertEquals(value, decoded)
        }
    }
}
