package com.kclaw.session.sqlite

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.providers.filters.AgentCheckpointPredicateFilter
import ai.koog.prompt.message.Message
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.time.Instant

class SqliteKoogPersistenceProviderTest : FunSpec({

    fun checkpoint(
        id: String,
        agentId: String,
        version: Long,
        createdAt: String,
        tombstone: Boolean = false
    ): Pair<String, AgentCheckpointData> {
        val properties = if (tombstone) JsonObject(mapOf("tombstone" to JsonPrimitive(true))) else null
        return agentId to AgentCheckpointData(
            checkpointId = id,
            createdAt = Instant.parse(createdAt),
            nodePath = if (tombstone) "tombstone" else "node-$version",
            lastInput = null,
            lastOutput = JsonPrimitive("output-$id"),
            messageHistory = emptyList<Message>(),
            version = version,
            properties = properties
        )
    }

    test("save then load latest checkpoint") {
        val dbFile = Files.createTempFile("koog-checkpoints-", ".sqlite")
        try {
            val provider = SqliteKoogPersistenceProvider.connect(dbFile)
            val (agentId, first) = checkpoint("cp-1", "agent:main:*:main", 1, "2026-03-09T10:00:00Z")
            val (_, second) = checkpoint("cp-2", agentId, 2, "2026-03-09T10:05:00Z")

            provider.saveCheckpoint(agentId, first)
            provider.saveCheckpoint(agentId, second)

            val latest = provider.getLatestCheckpoint(agentId)
            latest shouldNotBe null
            latest!!.checkpointId shouldBe "cp-2"
            latest.version shouldBe 2
        } finally {
            dbFile.deleteIfExists()
        }
    }

    test("getCheckpoints returns newest first and honors filter") {
        val dbFile = Files.createTempFile("koog-checkpoints-", ".sqlite")
        try {
            val provider = SqliteKoogPersistenceProvider.connect(dbFile)
            val agentId = "agent:main:*:main"
            val checkpoints = listOf(
                checkpoint("cp-1", agentId, 1, "2026-03-09T10:00:00Z").second,
                checkpoint("cp-2", agentId, 2, "2026-03-09T10:05:00Z").second,
                checkpoint("cp-3", agentId, 3, "2026-03-09T10:10:00Z").second
            )

            checkpoints.forEach { provider.saveCheckpoint(agentId, it) }

            val all = provider.getCheckpoints(agentId)
            all shouldHaveSize 3
            all.map { it.checkpointId } shouldBe listOf("cp-3", "cp-2", "cp-1")

            val filtered = provider.getCheckpoints(agentId, object : AgentCheckpointPredicateFilter {
                override fun check(checkpointData: AgentCheckpointData): Boolean = checkpointData.version >= 2
            })
            filtered.map { it.checkpointId } shouldBe listOf("cp-3", "cp-2")
        } finally {
            dbFile.deleteIfExists()
        }
    }

    test("deleteCheckpoints removes all state for an agent") {
        val dbFile = Files.createTempFile("koog-checkpoints-", ".sqlite")
        try {
            val provider = SqliteKoogPersistenceProvider.connect(dbFile)
            val agentId = "agent:main:*:main"
            val otherAgentId = "other:main:*:main"

            provider.saveCheckpoint(agentId, checkpoint("cp-1", agentId, 1, "2026-03-09T10:00:00Z").second)
            provider.saveCheckpoint(otherAgentId, checkpoint("cp-2", otherAgentId, 1, "2026-03-09T10:05:00Z").second)

            provider.deleteCheckpoints(agentId)

            provider.getLatestCheckpoint(agentId).shouldBeNull()
            provider.getLatestCheckpoint(otherAgentId)?.checkpointId shouldBe "cp-2"
        } finally {
            dbFile.deleteIfExists()
        }
    }
})
