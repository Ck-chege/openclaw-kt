package com.kclaw.gateway.rpc

import com.kclaw.protocol.identifiers.SessionKey
import com.kclaw.session.SessionMeta
import com.kclaw.session.SessionMessage
import com.kclaw.session.SessionStore
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory [SessionStore] for testing RPC handlers without file I/O.
 */
private class InMemorySessionStore : SessionStore {
    private val sessions = ConcurrentHashMap<String, SessionMeta>()
    private val transcripts = ConcurrentHashMap<String, MutableList<SessionMessage>>()

    override suspend fun createSession(key: SessionKey, agentId: String, channel: String?): SessionMeta {
        val meta = SessionMeta(
            key = key,
            agentId = agentId,
            channel = channel,
            createdAt = Clock.System.now(),
            fileId = UUID.randomUUID().toString()
        )
        sessions[key.raw] = meta
        transcripts[key.raw] = mutableListOf()
        return meta
    }

    override suspend fun getSession(key: SessionKey): SessionMeta? = sessions[key.raw]

    override suspend fun listSessions(): List<SessionMeta> = sessions.values.toList()

    override suspend fun appendMessage(key: SessionKey, message: SessionMessage) {
        transcripts.getOrPut(key.raw) { mutableListOf() }.add(message)
        sessions.computeIfPresent(key.raw) { _, meta ->
            meta.copy(
                messageCount = meta.messageCount + 1,
                lastMessageAt = message.timestamp
            )
        }
    }

    override suspend fun getHistory(key: SessionKey, limit: Int?): List<SessionMessage> {
        val messages = transcripts[key.raw] ?: emptyList()
        return if (limit != null) messages.takeLast(limit) else messages
    }

    override suspend fun deleteSession(key: SessionKey) {
        sessions.remove(key.raw)
        transcripts.remove(key.raw)
    }

    override suspend fun resetSession(key: SessionKey) {
        transcripts[key.raw]?.clear()
        sessions.computeIfPresent(key.raw) { _, meta ->
            meta.copy(messageCount = 0, totalTokens = 0, lastMessageAt = null)
        }
    }

    override suspend fun updateMeta(key: SessionKey, updater: (SessionMeta) -> SessionMeta) {
        sessions.computeIfPresent(key.raw) { _, meta -> updater(meta) }
    }
}

class SessionRpcHandlersTest {

    private lateinit var router: RpcRouter
    private lateinit var store: InMemorySessionStore

    @BeforeEach
    fun setup() {
        router = RpcRouter()
        store = InMemorySessionStore()
        registerSessionHandlers(router, store)
    }

    @Nested
    inner class SessionsList {

        @Test
        fun `sessions list returns empty when no sessions`() = runRpc {
            val result = router.route("sessions.list", JsonObject(emptyMap()), "test-session")
            val sessions = result["sessions"]!!.jsonArray
            sessions.size shouldBe 0
        }

        @Test
        fun `sessions list returns all created sessions`() = runRpc {
            store.createSession(SessionKey("agent1:main:*:main"), "agent1")
            store.createSession(SessionKey("agent2:telegram:*:dm:123"), "agent2", "telegram")

            val result = router.route("sessions.list", JsonObject(emptyMap()), "test-session")
            val sessions = result["sessions"]!!.jsonArray
            sessions.size shouldBe 2
        }
    }

    @Nested
    inner class SessionsGet {

        @Test
        fun `sessions get returns session metadata`() = runRpc {
            val key = SessionKey("main:main:*:main")
            store.createSession(key, "main")

            val params = buildJsonObject { put("key", key.raw) }
            val result = router.route("sessions.get", params, "test-session")

            val session = result["session"]!!.jsonObject
            session["agentId"]!!.jsonPrimitive.content shouldBe "main"
        }

        @Test
        fun `sessions get returns null for unknown key`() = runRpc {
            val params = buildJsonObject { put("key", "unknown:key:*:main") }
            val result = router.route("sessions.get", params, "test-session")

            result["session"] shouldBe JsonNull
            result["error"] shouldNotBe null
        }

        @Test
        fun `sessions get throws on missing key param`() = runRpc {
            try {
                router.route("sessions.get", JsonObject(emptyMap()), "test-session")
                throw AssertionError("Should have thrown")
            } catch (e: IllegalArgumentException) {
                e.message shouldBe "Missing required parameter: 'key'"
            }
        }
    }

    @Nested
    inner class SessionsHistory {

        @Test
        fun `sessions history returns messages`() = runRpc {
            val key = SessionKey("main:main:*:main")
            store.createSession(key, "main")
            store.appendMessage(key, SessionMessage.UserMessage(
                id = "msg-1",
                timestamp = Clock.System.now(),
                senderId = "user1",
                senderName = "Alice",
                text = "Hello"
            ))
            store.appendMessage(key, SessionMessage.AssistantMessage(
                id = "msg-2",
                timestamp = Clock.System.now(),
                text = "Hi there!"
            ))

            val params = buildJsonObject { put("key", key.raw) }
            val result = router.route("sessions.history", params, "test-session")

            result["count"]!!.jsonPrimitive.int shouldBe 2
            result["messages"]!!.jsonArray.size shouldBe 2
        }

        @Test
        fun `sessions history respects limit parameter`() = runRpc {
            val key = SessionKey("main:main:*:main")
            store.createSession(key, "main")
            repeat(5) { i ->
                store.appendMessage(key, SessionMessage.UserMessage(
                    id = "msg-$i",
                    timestamp = Clock.System.now(),
                    senderId = "user1",
                    senderName = "Alice",
                    text = "Message $i"
                ))
            }

            val params = buildJsonObject {
                put("key", key.raw)
                put("limit", 2)
            }
            val result = router.route("sessions.history", params, "test-session")

            result["count"]!!.jsonPrimitive.int shouldBe 2
        }

        @Test
        fun `sessions history returns empty for new session`() = runRpc {
            val key = SessionKey("main:main:*:main")
            store.createSession(key, "main")

            val params = buildJsonObject { put("key", key.raw) }
            val result = router.route("sessions.history", params, "test-session")

            result["count"]!!.jsonPrimitive.int shouldBe 0
            result["messages"]!!.jsonArray.size shouldBe 0
        }
    }

    @Nested
    inner class SessionsDelete {

        @Test
        fun `sessions delete removes session`() = runRpc {
            val key = SessionKey("main:main:*:main")
            store.createSession(key, "main")

            val params = buildJsonObject { put("key", key.raw) }
            val result = router.route("sessions.delete", params, "test-session")

            result["deleted"]!!.jsonPrimitive.boolean shouldBe true
            store.getSession(key) shouldBe null
        }

        @Test
        fun `sessions delete on nonexistent key does not throw`() = runRpc {
            val params = buildJsonObject { put("key", "gone:key:*:main") }
            val result = router.route("sessions.delete", params, "test-session")

            result["deleted"]!!.jsonPrimitive.boolean shouldBe true
        }
    }

    @Nested
    inner class SessionsReset {

        @Test
        fun `sessions reset clears transcript but keeps session`() = runRpc {
            val key = SessionKey("main:main:*:main")
            store.createSession(key, "main")
            store.appendMessage(key, SessionMessage.UserMessage(
                id = "msg-1",
                timestamp = Clock.System.now(),
                senderId = "user1",
                senderName = "Alice",
                text = "Hello"
            ))

            val params = buildJsonObject { put("key", key.raw) }
            router.route("sessions.reset", params, "test-session")

            // Session still exists
            store.getSession(key) shouldNotBe null
            // But transcript is empty
            store.getHistory(key).size shouldBe 0
            // And counters reset
            store.getSession(key)!!.messageCount shouldBe 0
        }
    }

    @Nested
    inner class HandlerRegistration {

        @Test
        fun `all five session handlers are registered`() {
            router.hasHandler("sessions.list") shouldBe true
            router.hasHandler("sessions.get") shouldBe true
            router.hasHandler("sessions.history") shouldBe true
            router.hasHandler("sessions.delete") shouldBe true
            router.hasHandler("sessions.reset") shouldBe true
        }
    }
}

/** Helper to run suspend RPC calls in tests. */
private fun runRpc(block: suspend () -> Unit) = kotlinx.coroutines.test.runTest { block() }
