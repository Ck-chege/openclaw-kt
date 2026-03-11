package com.kclaw.rpc

import ai.koog.agents.core.tools.SimpleTool
import com.kclaw.agent.isolation.AgentResolver
import com.kclaw.gateway.config.AgentDefaults
import com.kclaw.gateway.config.AgentEntry
import com.kclaw.gateway.config.AgentToolOverrides
import com.kclaw.gateway.config.AgentsConfig
import com.kclaw.gateway.config.GatewayConfig
import com.kclaw.gateway.config.ToolsConfig
import com.kclaw.gateway.rpc.RpcRouter
import com.kclaw.protocol.dto.TokenUsage
import com.kclaw.protocol.identifiers.SessionKey
import com.kclaw.session.SessionMessage
import com.kclaw.session.SessionMeta
import com.kclaw.session.SessionStore
import com.kclaw.tools.policy.ToolPolicyPipeline
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.writeText
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

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
            val tokensDelta = when (message) {
                is SessionMessage.AssistantMessage -> message.usage?.totalTokens?.toLong() ?: 0L
                else -> 0L
            }
            meta.copy(
                messageCount = meta.messageCount + 1,
                totalTokens = meta.totalTokens + tokensDelta,
                lastMessageAt = message.timestamp,
                modelId = (message as? SessionMessage.AssistantMessage)?.model ?: meta.modelId
            )
        }
    }

    override suspend fun getHistory(key: SessionKey, limit: Int?): List<SessionMessage> {
        val messages = transcripts[key.raw].orEmpty()
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

class OperatorRpcHandlersTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var router: RpcRouter
    private lateinit var store: InMemorySessionStore
    private lateinit var workspaceDir: Path
    private lateinit var logsFile: Path

    @BeforeEach
    fun setup() {
        router = RpcRouter()
        store = InMemorySessionStore()
        workspaceDir = tempDir.resolve("workspace")
        workspaceDir.toFile().mkdirs()
        workspaceDir.resolve("AGENTS.md").writeText("# Agents\n")
        workspaceDir.resolve("README.md").writeText("workspace file")
        logsFile = tempDir.resolve("agent-runtime.log")
        logsFile.writeText("INFO boot complete\nWARN something odd\nERROR something broke\n")
    }

    @Test
    fun `agents list returns live agent stats workspace files and filtered tools`() = runBlocking {
        val key = SessionKey("ops:main:*:main")
        store.createSession(key, "ops")
        store.updateMeta(key) { it.copy(totalTokens = 150, messageCount = 3) }

        registerHandlers(
            config = GatewayConfig(
                agents = AgentsConfig(
                    defaults = AgentDefaults(
                        model = "openai:gpt-4o",
                        workspace = workspaceDir.toString()
                    ),
                    list = listOf(
                        AgentEntry(
                            id = "ops",
                            model = "anthropic:claude-sonnet-4",
                            workspace = workspaceDir.toString(),
                            tools = AgentToolOverrides(profile = "full")
                        )
                    )
                ),
                tools = ToolsConfig(profile = "safe")
            ),
            runningSessions = setOf(key.raw)
        )

        val result = router.route("agents.list", JsonObject(emptyMap()), "test-session")
        val agents = result["agents"]!!.jsonArray
        val ops = agents.first { it.jsonObject["id"]!!.jsonPrimitive.content == "ops" }.jsonObject
        val defaultAgent = agents.first { it.jsonObject["id"]!!.jsonPrimitive.content == "default" }.jsonObject

        ops["isRunning"]!!.jsonPrimitive.boolean shouldBe true
        ops["sessionCount"]!!.jsonPrimitive.int shouldBe 1
        ops["totalTokens"]!!.jsonPrimitive.long shouldBe 150L
        ops["workspaceFiles"]!!.jsonArray.map { it.jsonPrimitive.content } shouldContain "AGENTS.md"
        ops["tools"]!!.jsonArray.map { it.jsonPrimitive.content }.shouldContainExactlyInAnyOrder("exec", "read")

        defaultAgent["tools"]!!.jsonArray.map { it.jsonPrimitive.content } shouldBe listOf("read")
    }

    @Test
    fun `workspace read returns file content and rejects traversal`() = runBlocking {
        registerHandlers()

        val ok = router.route(
            "workspace.read",
            buildJsonObject {
                put("agentId", "default")
                put("file", "AGENTS.md")
            },
            "test-session"
        )
        ok["content"]!!.jsonPrimitive.content shouldBe "# Agents\n"

        val error = assertThrows<IllegalArgumentException> {
            runBlocking {
                router.route(
                    "workspace.read",
                    buildJsonObject {
                        put("agentId", "default")
                        put("file", "../secret.txt")
                    },
                    "test-session"
                )
            }
        }
        error.message shouldBe "Workspace file path escapes the agent workspace"
    }

    @Test
    fun `tools catalog classifies elevated tools and applies policy`() = runBlocking {
        registerHandlers(config = GatewayConfig(
            agents = AgentsConfig(defaults = AgentDefaults(workspace = workspaceDir.toString())),
            tools = ToolsConfig(profile = "full")
        ))

        val result = router.route("tools.catalog", JsonObject(emptyMap()), "test-session")
        val tools = result["tools"]!!.jsonArray.associateBy { it.jsonObject["name"]!!.jsonPrimitive.content }

        tools["exec"]!!.jsonObject["group"]!!.jsonPrimitive.content shouldBe "exec"
        tools["exec"]!!.jsonObject["permission"]!!.jsonPrimitive.content shouldBe "elevated"
        tools["read"]!!.jsonObject["group"]!!.jsonPrimitive.content shouldBe "filesystem"
    }

    @Test
    fun `channels status reports configured and active channels`() = runBlocking {
        val key = SessionKey("ops:telegram:*:dm:123")
        val now = Clock.System.now()
        store.createSession(key, "ops", "telegram")
        store.appendMessage(key, SessionMessage.UserMessage(
            id = "u1",
            timestamp = now,
            senderId = "user",
            senderName = "User",
            text = "Ping",
            channel = "telegram"
        ))

        registerHandlers(config = GatewayConfig(
            agents = AgentsConfig(
                defaults = AgentDefaults(workspace = workspaceDir.toString()),
                list = listOf(
                    AgentEntry(id = "ops", workspace = workspaceDir.toString(), channels = listOf("telegram"))
                )
            )
        ))

        val result = router.route("channels.status", JsonObject(emptyMap()), "test-session")
        val channels = result["channels"]!!.jsonArray.associateBy { it.jsonObject["id"]!!.jsonPrimitive.content }

        channels["telegram"]!!.jsonObject["status"]!!.jsonPrimitive.content shouldBe "connected"
        channels["telegram"]!!.jsonObject["messageCount24h"]!!.jsonPrimitive.int shouldBe 1
        channels["webchat"]!!.jsonObject["status"]!!.jsonPrimitive.content shouldBe "connected"
    }

    @Test
    fun `usage summary aggregates token and message analytics`() = runBlocking {
        val key = SessionKey("default:main:*:main")
        val now = Clock.System.now()
        store.createSession(key, "default")
        store.appendMessage(key, SessionMessage.UserMessage(
            id = "u1",
            timestamp = now,
            senderId = "user",
            senderName = "User",
            text = "Hello"
        ))
        store.appendMessage(key, SessionMessage.AssistantMessage(
            id = "a1",
            timestamp = now.plus(1.seconds),
            text = "Hi",
            model = "openai:gpt-4o",
            usage = TokenUsage(promptTokens = 30, completionTokens = 20, totalTokens = 50)
        ))

        registerHandlers()

        val result = router.route(
            "usage.summary",
            buildJsonObject { put("range", "24h") },
            "test-session"
        )

        result["summary"]!!.jsonObject["totalTokens"]!!.jsonPrimitive.long shouldBe 50L
        result["summary"]!!.jsonObject["totalMessages"]!!.jsonPrimitive.int shouldBe 2
        result["summary"]!!.jsonObject["estimatedCost"]!!.jsonPrimitive.double shouldBe 0.0
        result["summary"]!!.jsonObject["avgResponseTime"]!!.jsonPrimitive.long shouldBe 1000L
        result["tokensByModel"]!!.jsonArray.first().jsonObject["name"]!!.jsonPrimitive.content shouldBe "openai:gpt-4o"
        result["topSessions"]!!.jsonArray.first().jsonObject["key"]!!.jsonPrimitive.content shouldBe key.raw
    }

    @Test
    fun `logs recent returns structured entries from trace file`() = runBlocking {
        registerHandlers()

        val result = router.route(
            "logs.recent",
            buildJsonObject { put("limit", 2) },
            "test-session"
        )

        val entries = result["entries"]!!.jsonArray
        entries.size shouldBe 2
        entries.first().jsonObject["source"]!!.jsonPrimitive.content shouldBe "agent-runtime"
        entries.last().jsonObject["level"]!!.jsonPrimitive.content shouldBe "ERROR"
    }

    @Test
    fun `status summarizes sessions agents and tools`() = runBlocking {
        val key = SessionKey("default:main:*:main")
        store.createSession(key, "default")

        registerHandlers(runningSessions = setOf(key.raw))

        val result = router.route("status", JsonObject(emptyMap()), "test-session")

        result["status"]!!.jsonPrimitive.content shouldBe "ok"
        result["agentCount"]!!.jsonPrimitive.int shouldBe 1
        result["sessionCount"]!!.jsonPrimitive.int shouldBe 1
        result["activeRuns"]!!.jsonPrimitive.int shouldBe 1
        result["toolCount"]!!.jsonPrimitive.int shouldBe 2
    }

    private fun registerHandlers(
        config: GatewayConfig = GatewayConfig(
            agents = AgentsConfig(
                defaults = AgentDefaults(
                    model = "openai:gpt-4o",
                    workspace = workspaceDir.toString()
                )
            ),
            tools = ToolsConfig(profile = "safe")
        ),
        runningSessions: Set<String> = emptySet()
    ) {
        registerOperatorHandlers(
            router = router,
            gatewayConfig = { config },
            agentResolver = AgentResolver(),
            sessionStore = store,
            allTools = { listOf(StubRead, StubExec) },
            toolPolicyPipeline = ToolPolicyPipeline(),
            isSessionRunning = { sessionKey -> sessionKey.raw in runningSessions },
            logsPath = logsFile
        )
    }

    private object StubRead : SimpleTool<StubRead.Args>(
        argsSerializer = Args.serializer(),
        name = "read",
        description = "Read file"
    ) {
        @Serializable
        data class Args(val path: String = "")

        override suspend fun execute(args: Args): String = ""
    }

    private object StubExec : SimpleTool<StubExec.Args>(
        argsSerializer = Args.serializer(),
        name = "exec",
        description = "Run a shell command"
    ) {
        @Serializable
        data class Args(val command: String = "")

        override suspend fun execute(args: Args): String = ""
    }
}
