package com.openclaw.session.composite

import com.openclaw.protocol.identifiers.SessionKey
import com.openclaw.session.SessionMessage
import com.openclaw.session.compaction.CompactionConfig
import com.openclaw.session.compaction.CompactionEngine
import com.openclaw.session.compaction.CompactionOutcome
import com.openclaw.session.compaction.CompactionResult
import com.openclaw.session.compaction.MessageSummarizer
import com.openclaw.session.jsonl.JsonlSessionProvider
import com.openclaw.session.postgres.CheckpointTable
import com.openclaw.session.postgres.GraphCheckpoint
import com.openclaw.session.postgres.PostgresCheckpointProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import org.testcontainers.containers.PostgreSQLContainer
import java.nio.file.Files

class CompositeSessionStoreIntegrationTest : FunSpec({

    val postgres = PostgreSQLContainer("postgres:16-alpine")
    lateinit var db: Database

    val testKey = SessionKey("test-agent:main:*:main")
    val now = Clock.System.now()

    fun userMsg(id: String, text: String) = SessionMessage.UserMessage(
        id = id, timestamp = now,
        senderId = "peer-1", senderName = "Alice", text = text
    )

    val mockSummarizer = MessageSummarizer { messages ->
        CompactionResult("Summary of ${messages.size} messages", 500)
    }

    beforeSpec {
        postgres.start()
        db = Database.connect(
            url = postgres.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = postgres.username,
            password = postgres.password
        )
        transaction(db) {
            SchemaUtils.create(CheckpointTable)
        }
    }

    afterSpec {
        postgres.stop()
    }

    beforeEach {
        transaction(db) {
            CheckpointTable.deleteWhere { sessionKey neq "" }
        }
    }

    // --- CrashRecovery ---

    context("crashRecovery") {
        test("checkpoint + JSONL state reports recovery needed") {
            val tmpDir = Files.createTempDirectory("composite-int-")
            val jsonl = JsonlSessionProvider(tmpDir)
            val checkpointProvider = PostgresCheckpointProvider(db)
            val store = CompositeSessionStore(jsonl, checkpointProvider)

            store.createSession(testKey, agentId = "test-agent")
            store.appendMessage(testKey, userMsg("u1", "hello"))

            // Checkpoint says 5 messages, but JSONL only has 1 → recovery needed
            store.saveCheckpoint(GraphCheckpoint(testKey, "node", "{}", 5))

            val result = store.recoverFromCheckpoint(testKey)
            result.shouldBeInstanceOf<RecoveryResult.Recovered>()
            result.checkpointMessageCount shouldBe 5
            result.jsonlMessageCount shouldBe 1
        }

        test("no checkpoint returns NoCheckpoint") {
            val tmpDir = Files.createTempDirectory("composite-int-")
            val jsonl = JsonlSessionProvider(tmpDir)
            val checkpointProvider = PostgresCheckpointProvider(db)
            val store = CompositeSessionStore(jsonl, checkpointProvider)

            store.createSession(testKey, agentId = "test-agent")
            val result = store.recoverFromCheckpoint(testKey)
            result.shouldBeInstanceOf<RecoveryResult.NoCheckpoint>()
        }

        test("already current returns AlreadyCurrent") {
            val tmpDir = Files.createTempDirectory("composite-int-")
            val jsonl = JsonlSessionProvider(tmpDir)
            val checkpointProvider = PostgresCheckpointProvider(db)
            val store = CompositeSessionStore(jsonl, checkpointProvider)

            store.createSession(testKey, agentId = "test-agent")
            repeat(5) { i ->
                store.appendMessage(testKey, userMsg("u$i", "msg $i"))
            }

            // Checkpoint at 3 messages, but JSONL has 5 → already current
            store.saveCheckpoint(GraphCheckpoint(testKey, "node", "{}", 3))

            val result = store.recoverFromCheckpoint(testKey)
            result.shouldBeInstanceOf<RecoveryResult.AlreadyCurrent>()
            result.jsonlMessageCount shouldBe 5
            result.checkpointMessageCount shouldBe 3
        }
    }

    // --- EndToEnd ---

    context("endToEnd") {
        test("full lifecycle: create → append → compact → checkpoint → delete") {
            val tmpDir = Files.createTempDirectory("composite-int-")
            val jsonl = JsonlSessionProvider(tmpDir)
            val checkpointProvider = PostgresCheckpointProvider(db)
            val store = CompositeSessionStore(jsonl, checkpointProvider)

            // Create
            store.createSession(testKey, agentId = "test-agent")

            // Append messages
            repeat(8) { i ->
                store.appendMessage(testKey, userMsg("u$i", "x".repeat(200)))
            }
            store.getHistory(testKey) shouldHaveSize 8

            // Compact (using low threshold to trigger)
            val config = CompactionConfig(maxContextTokens = 1000, triggerRatio = 0.20)
            val engine = CompactionEngine(store, mockSummarizer, config)
            val outcome = engine.compact(testKey)
            outcome.shouldBeInstanceOf<CompactionOutcome.Success>()

            val historyAfterCompaction = store.getHistory(testKey)
            historyAfterCompaction.first().shouldBeInstanceOf<SessionMessage.CompactionSummary>()

            // Checkpoint
            store.saveCheckpoint(
                GraphCheckpoint(testKey, "post_compaction", "{}", historyAfterCompaction.size)
            )
            val checkpoint = store.loadCheckpoint(testKey)
            checkpoint shouldNotBe null
            checkpoint!!.graphNode shouldBe "post_compaction"

            // Delete
            store.deleteSession(testKey)
            store.getSession(testKey) shouldBe null
            store.loadCheckpoint(testKey) shouldBe null
        }
    }
})
