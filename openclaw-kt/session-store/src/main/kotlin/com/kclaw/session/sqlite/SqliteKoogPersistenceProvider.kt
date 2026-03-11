package com.kclaw.session.sqlite

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.feature.isTombstone
import ai.koog.agents.snapshot.providers.PersistenceStorageProvider
import ai.koog.agents.snapshot.providers.filters.AgentCheckpointPredicateFilter
import com.kclaw.session.SessionCheckpointCleaner
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.nio.file.Files
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

/**
 * Koog persistence provider backed by a local SQLite database.
 */
class SqliteKoogPersistenceProvider private constructor(
    private val db: Database,
    private val json: Json
) : PersistenceStorageProvider<AgentCheckpointPredicateFilter>, SessionCheckpointCleaner {

    override suspend fun getCheckpoints(
        agentId: String,
        filter: AgentCheckpointPredicateFilter?
    ): List<AgentCheckpointData> = newSuspendedTransaction(db = db) {
        KoogCheckpointTable.selectAll()
            .where { KoogCheckpointTable.agentId eq agentId }
            .orderBy(KoogCheckpointTable.createdAtEpochMs to SortOrder.DESC)
            .map(::toCheckpoint)
            .let { checkpoints ->
                filter?.let { predicate -> checkpoints.filter(predicate::check) } ?: checkpoints
            }
    }

    override suspend fun saveCheckpoint(agentId: String, agentCheckpointData: AgentCheckpointData) {
        newSuspendedTransaction(db = db) {
            KoogCheckpointTable.insert { row ->
                row[checkpointId] = agentCheckpointData.checkpointId
                row[KoogCheckpointTable.agentId] = agentId
                row[createdAtEpochMs] = agentCheckpointData.createdAt.toEpochMilliseconds()
                row[version] = agentCheckpointData.version
                row[nodePath] = agentCheckpointData.nodePath
                row[isTombstone] = agentCheckpointData.isTombstone()
                row[payload] = json.encodeToString(agentCheckpointData)
            }
        }
    }

    override suspend fun getLatestCheckpoint(
        agentId: String,
        filter: AgentCheckpointPredicateFilter?
    ): AgentCheckpointData? = newSuspendedTransaction(db = db) {
        KoogCheckpointTable.selectAll()
            .where { KoogCheckpointTable.agentId eq agentId }
            .orderBy(KoogCheckpointTable.createdAtEpochMs to SortOrder.DESC)
            .map(::toCheckpoint)
            .let { checkpoints ->
                filter?.let { predicate -> checkpoints.firstOrNull(predicate::check) }
                    ?: checkpoints.firstOrNull()
            }
    }

    override suspend fun deleteCheckpoints(agentId: String) {
        newSuspendedTransaction(db = db) {
            val deleted = KoogCheckpointTable.deleteWhere { KoogCheckpointTable.agentId eq agentId }
            logger.debug { "Deleted $deleted Koog checkpoints for agentId=$agentId" }
        }
    }

    private fun toCheckpoint(row: ResultRow): AgentCheckpointData =
        json.decodeFromString(row[KoogCheckpointTable.payload])

    companion object {
        private val checkpointJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun connect(path: Path): SqliteKoogPersistenceProvider {
            path.parent?.let { Files.createDirectories(it) }
            val db = Database.connect(
                url = "jdbc:sqlite:${path.toAbsolutePath()}",
                driver = "org.sqlite.JDBC"
            )
            SqliteCheckpointDatabase.migrate(db)
            return SqliteKoogPersistenceProvider(db, checkpointJson)
        }

        fun fromDatabase(db: Database, json: Json = checkpointJson): SqliteKoogPersistenceProvider {
            SqliteCheckpointDatabase.migrate(db)
            return SqliteKoogPersistenceProvider(db, json)
        }
    }
}

object SqliteCheckpointDatabase {
    fun migrate(db: Database) {
        org.jetbrains.exposed.sql.transactions.transaction(db) {
            SchemaUtils.create(KoogCheckpointTable)
        }
    }
}
