package com.openclaw.session.composite

import com.openclaw.protocol.identifiers.SessionKey
import com.openclaw.session.SessionMessage
import com.openclaw.session.jsonl.JsonlSessionProvider
import com.openclaw.session.postgres.GraphCheckpoint
import com.openclaw.session.postgres.PostgresCheckpointProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.datetime.Clock
import java.nio.file.Files

class CompositeSessionStoreTest : FunSpec({

    val testKey = SessionKey("test-agent:main:*:main")
    val now = Clock.System.now()

    fun userMsg(id: String, text: String) = SessionMessage.UserMessage(
        id = id, timestamp = now,
        senderId = "peer-1", senderName = "Alice", text = text
    )

    // --- NormalMode (with mock Postgres that always fails → test JSONL independence) ---

    context("normalMode") {
        test("createSession writes to JSONL") {
            val tmpDir = Files.createTempDirectory("composite-test-")
            val jsonl = JsonlSessionProvider(tmpDir)
            val store = CompositeSessionStore(jsonl, null)

            val meta = store.createSession(testKey, agentId = "test-agent", channel = "main")
            meta.key shouldBe testKey
            meta.agentId shouldBe "test-agent"
        }

        test("appendMessage writes to JSONL") {
            val tmpDir = Files.createTempDirectory("composite-test-")
            val jsonl = JsonlSessionProvider(tmpDir)
            val store = CompositeSessionStore(jsonl, null)

            store.createSession(testKey, agentId = "test-agent")
            store.appendMessage(testKey, userMsg("u1", "hello"))

            val history = store.getHistory(testKey)
            history shouldHaveSize 1
            (history[0] as SessionMessage.UserMessage).text shouldBe "hello"
        }

        test("getHistory reads from JSONL") {
            val tmpDir = Files.createTempDirectory("composite-test-")
            val jsonl = JsonlSessionProvider(tmpDir)
            val store = CompositeSessionStore(jsonl, null)

            store.createSession(testKey, agentId = "test-agent")
            store.appendMessage(testKey, userMsg("u1", "first"))
            store.appendMessage(testKey, userMsg("u2", "second"))

            val history = store.getHistory(testKey)
            history shouldHaveSize 2
        }

        test("deleteSession removes from JSONL") {
            val tmpDir = Files.createTempDirectory("composite-test-")
            val jsonl = JsonlSessionProvider(tmpDir)
            val store = CompositeSessionStore(jsonl, null)

            store.createSession(testKey, agentId = "test-agent")
            store.appendMessage(testKey, userMsg("u1", "hello"))
            store.deleteSession(testKey)

            store.getSession(testKey) shouldBe null
        }

        test("saveCheckpoint with null provider is a no-op") {
            val tmpDir = Files.createTempDirectory("composite-test-")
            val jsonl = JsonlSessionProvider(tmpDir)
            val store = CompositeSessionStore(jsonl, null)

            // Should not throw
            store.saveCheckpoint(
                GraphCheckpoint(testKey, "node", "{}", 1)
            )
        }

        test("loadCheckpoint with null provider returns null") {
            val tmpDir = Files.createTempDirectory("composite-test-")
            val jsonl = JsonlSessionProvider(tmpDir)
            val store = CompositeSessionStore(jsonl, null)

            store.loadCheckpoint(testKey) shouldBe null
        }
    }

    // --- FallbackMode (using a provider that throws to simulate Postgres failure) ---

    context("fallbackMode") {
        test("appendMessage succeeds when Postgres unavailable") {
            val tmpDir = Files.createTempDirectory("composite-test-")
            val jsonl = JsonlSessionProvider(tmpDir)
            val failingProvider = FailingPostgresProvider()
            val store = CompositeSessionStore(jsonl, failingProvider)

            store.createSession(testKey, agentId = "test-agent")
            store.appendMessage(testKey, userMsg("u1", "hello"))

            store.getHistory(testKey) shouldHaveSize 1
        }

        test("isDegraded returns true after Postgres failure") {
            val tmpDir = Files.createTempDirectory("composite-test-")
            val jsonl = JsonlSessionProvider(tmpDir)
            val failingProvider = FailingPostgresProvider()
            val store = CompositeSessionStore(jsonl, failingProvider)

            store.isDegraded shouldBe false

            // Trigger a checkpoint save that fails
            store.saveCheckpoint(GraphCheckpoint(testKey, "node", "{}", 1))

            store.isDegraded shouldBe true
        }

        test("JSONL operations continue normally after degradation") {
            val tmpDir = Files.createTempDirectory("composite-test-")
            val jsonl = JsonlSessionProvider(tmpDir)
            val failingProvider = FailingPostgresProvider()
            val store = CompositeSessionStore(jsonl, failingProvider)

            store.createSession(testKey, agentId = "test-agent")

            // Trigger degradation
            store.saveCheckpoint(GraphCheckpoint(testKey, "node", "{}", 1))
            store.isDegraded shouldBe true

            // JSONL still works
            store.appendMessage(testKey, userMsg("u1", "hello"))
            store.appendMessage(testKey, userMsg("u2", "world"))
            store.getHistory(testKey) shouldHaveSize 2
            store.getSession(testKey)!!.messageCount shouldBe 2
        }

        test("deleteSession succeeds even with Postgres failure") {
            val tmpDir = Files.createTempDirectory("composite-test-")
            val jsonl = JsonlSessionProvider(tmpDir)
            val failingProvider = FailingPostgresProvider()
            val store = CompositeSessionStore(jsonl, failingProvider)

            store.createSession(testKey, agentId = "test-agent")
            store.deleteSession(testKey)

            store.getSession(testKey) shouldBe null
        }
    }

    // --- NullPostgres (no provider configured at all) ---

    context("nullPostgres") {
        test("works with null checkpoint provider") {
            val tmpDir = Files.createTempDirectory("composite-test-")
            val jsonl = JsonlSessionProvider(tmpDir)
            val store = CompositeSessionStore(jsonl, null)

            store.createSession(testKey, agentId = "test-agent")
            store.appendMessage(testKey, userMsg("u1", "hello"))
            store.getHistory(testKey) shouldHaveSize 1
        }

        test("isDegraded returns false (not degraded, just unconfigured)") {
            val tmpDir = Files.createTempDirectory("composite-test-")
            val jsonl = JsonlSessionProvider(tmpDir)
            val store = CompositeSessionStore(jsonl, null)

            store.isDegraded shouldBe false
        }

        test("loadCheckpoint returns null") {
            val tmpDir = Files.createTempDirectory("composite-test-")
            val jsonl = JsonlSessionProvider(tmpDir)
            val store = CompositeSessionStore(jsonl, null)

            store.loadCheckpoint(testKey) shouldBe null
        }
    }

    // --- RecoverFromCheckpoint ---

    context("recoverFromCheckpoint") {
        test("no checkpoint returns NoCheckpoint") {
            val tmpDir = Files.createTempDirectory("composite-test-")
            val jsonl = JsonlSessionProvider(tmpDir)
            val store = CompositeSessionStore(jsonl, null)

            val result = store.recoverFromCheckpoint(testKey)
            result.shouldBeInstanceOf<RecoveryResult.NoCheckpoint>()
        }
    }
})

/**
 * A [PostgresCheckpointProvider] subclass that always throws on checkpoint operations
 * to simulate Postgres being unavailable.
 */
private class FailingPostgresProvider : PostgresCheckpointProvider(null) {
    override suspend fun saveCheckpoint(checkpoint: GraphCheckpoint) {
        throw RuntimeException("Postgres is down")
    }

    override suspend fun loadCheckpoint(key: SessionKey): GraphCheckpoint? {
        throw RuntimeException("Postgres is down")
    }

    override suspend fun deleteCheckpoints(key: SessionKey) {
        throw RuntimeException("Postgres is down")
    }
}
