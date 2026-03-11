package com.kclaw.session.postgres

import com.kclaw.protocol.identifiers.SessionKey
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.testcontainers.containers.PostgreSQLContainer

/** Parse JSON string to compare structures regardless of formatting. */
private fun parseJson(s: String): JsonElement = Json.parseToJsonElement(s)

class PostgresCheckpointProviderTest : FunSpec({

    val postgres = PostgreSQLContainer("postgres:16-alpine")

    lateinit var db: Database
    lateinit var provider: PostgresCheckpointProvider

    val key1 = SessionKey("agent-a:main:*:main")
    val key2 = SessionKey("agent-b:main:*:main")

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
        provider = PostgresCheckpointProvider(db)
        // Clean table between tests
        transaction(db) {
            CheckpointTable.deleteWhere { sessionKey neq "" }
        }
    }

    // --- SaveAndLoad ---

    context("saveAndLoad") {
        test("save then load returns same checkpoint") {
            val checkpoint = GraphCheckpoint(
                sessionKey = key1,
                graphNode = "llm_request",
                graphState = """{"turn":1}""",
                messageCount = 5
            )

            provider.saveCheckpoint(checkpoint)
            val loaded = provider.loadCheckpoint(key1)

            loaded shouldNotBe null
            loaded!!.sessionKey shouldBe key1
            loaded.graphNode shouldBe "llm_request"
            parseJson(loaded.graphState) shouldBe parseJson("""{"turn":1}""")
            loaded.messageCount shouldBe 5
        }

        test("load nonexistent key returns null") {
            val loaded = provider.loadCheckpoint(key1)
            loaded shouldBe null
        }

        test("save overwrites existing checkpoint (upsert)") {
            val original = GraphCheckpoint(key1, "node_a", """{"v":1}""", 3)
            provider.saveCheckpoint(original)

            val updated = GraphCheckpoint(key1, "node_b", """{"v":2}""", 7)
            provider.saveCheckpoint(updated)

            val loaded = provider.loadCheckpoint(key1)!!
            loaded.graphNode shouldBe "node_b"
            parseJson(loaded.graphState) shouldBe parseJson("""{"v":2}""")
            loaded.messageCount shouldBe 7
        }

        test("preserves graphNode, graphState, messageCount") {
            val checkpoint = GraphCheckpoint(
                sessionKey = key1,
                graphNode = "tool_execution",
                graphState = """{"tools":["exec","read"],"depth":3}""",
                messageCount = 42
            )

            provider.saveCheckpoint(checkpoint)
            val loaded = provider.loadCheckpoint(key1)!!

            loaded.graphNode shouldBe "tool_execution"
            parseJson(loaded.graphState) shouldBe parseJson("""{"tools":["exec","read"],"depth":3}""")
            loaded.messageCount shouldBe 42
        }

        test("updatedAt is set on save and updated on re-save") {
            val checkpoint = GraphCheckpoint(key1, "node_a", "{}", 1)
            provider.saveCheckpoint(checkpoint)

            val firstUpdatedAt = newSuspendedTransaction(db = db) {
                CheckpointTable.selectAll()
                    .where { CheckpointTable.sessionKey eq key1.raw }
                    .first()[CheckpointTable.updatedAt]
            }

            // Small delay to ensure timestamp differs
            kotlinx.coroutines.delay(50)

            val updated = GraphCheckpoint(key1, "node_b", "{}", 2)
            provider.saveCheckpoint(updated)

            val secondUpdatedAt = newSuspendedTransaction(db = db) {
                CheckpointTable.selectAll()
                    .where { CheckpointTable.sessionKey eq key1.raw }
                    .first()[CheckpointTable.updatedAt]
            }

            secondUpdatedAt shouldNotBe firstUpdatedAt
        }
    }

    // --- Delete ---

    context("delete") {
        test("delete removes checkpoint") {
            provider.saveCheckpoint(GraphCheckpoint(key1, "node", "{}", 1))
            provider.deleteCheckpoints(key1)
            provider.loadCheckpoint(key1) shouldBe null
        }

        test("delete nonexistent key is idempotent") {
            // Should not throw
            provider.deleteCheckpoints(key1)
            provider.loadCheckpoint(key1) shouldBe null
        }

        test("delete does not affect other sessions") {
            provider.saveCheckpoint(GraphCheckpoint(key1, "node_a", "{}", 1))
            provider.saveCheckpoint(GraphCheckpoint(key2, "node_b", "{}", 2))

            provider.deleteCheckpoints(key1)

            provider.loadCheckpoint(key1) shouldBe null
            provider.loadCheckpoint(key2) shouldNotBe null
            provider.loadCheckpoint(key2)!!.graphNode shouldBe "node_b"
        }
    }

    // --- MultipleKeys ---

    context("multipleKeys") {
        test("separate sessions have independent checkpoints") {
            provider.saveCheckpoint(GraphCheckpoint(key1, "node_a", """{"a":1}""", 10))
            provider.saveCheckpoint(GraphCheckpoint(key2, "node_b", """{"b":2}""", 20))

            val loaded1 = provider.loadCheckpoint(key1)!!
            val loaded2 = provider.loadCheckpoint(key2)!!

            loaded1.graphNode shouldBe "node_a"
            loaded1.messageCount shouldBe 10
            loaded2.graphNode shouldBe "node_b"
            loaded2.messageCount shouldBe 20
        }
    }

    // --- ConcurrentAccess ---

    context("concurrentAccess") {
        test("concurrent saves to different keys succeed") {
            runTest {
                val keys = (0 until 10).map { SessionKey("agent-$it:main:*:main") }
                val jobs = keys.map { key ->
                    async {
                        provider.saveCheckpoint(GraphCheckpoint(key, "node", "{}", 1))
                    }
                }
                jobs.awaitAll()

                keys.forEach { key ->
                    provider.loadCheckpoint(key) shouldNotBe null
                }
            }
        }

        test("concurrent saves to same key serialize correctly") {
            runTest {
                val jobs = (0 until 10).map { i ->
                    async {
                        provider.saveCheckpoint(GraphCheckpoint(key1, "node_$i", """{"i":$i}""", i))
                    }
                }
                jobs.awaitAll()

                // One of the writes should have won — just verify we can load a valid checkpoint
                val loaded = provider.loadCheckpoint(key1)
                loaded shouldNotBe null
                loaded!!.sessionKey shouldBe key1
            }
        }
    }
})
