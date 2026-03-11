package com.kclaw.rpc

import ai.koog.agents.core.tools.Tool
import com.kclaw.agent.isolation.AgentResolver
import com.kclaw.gateway.config.AgentEntry
import com.kclaw.gateway.config.GatewayConfig
import com.kclaw.gateway.config.isTruthyPath
import com.kclaw.gateway.rpc.RpcRouter
import com.kclaw.protocol.identifiers.SessionKey
import com.kclaw.session.SessionMessage
import com.kclaw.session.SessionMeta
import com.kclaw.session.SessionStore
import com.kclaw.skills.SkillLoader
import com.kclaw.tools.policy.CallerContext
import com.kclaw.tools.policy.DangerousTools
import com.kclaw.tools.policy.ToolPolicyPipeline
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.io.path.fileSize
import kotlin.io.path.isDirectory
import kotlin.io.path.isReadable
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

private val json = Json { encodeDefaults = true }

fun registerOperatorHandlers(
    router: RpcRouter,
    gatewayConfig: () -> GatewayConfig,
    agentResolver: AgentResolver,
    sessionStore: SessionStore,
    allTools: () -> List<Tool<*, *>>,
    toolPolicyPipeline: ToolPolicyPipeline,
    skillLoader: SkillLoader? = null,
    isSessionRunning: (SessionKey) -> Boolean = { false },
    logsPath: Path? = null
) {
    router.register("status") { _, _ ->
        val config = gatewayConfig()
        val sessions = sessionStore.listSessions()
        val activeRuns = sessions.count { isSessionRunning(it.key) }
        val configuredAgents = configuredAgentEntries(config)

        buildJsonObject {
            put("status", JsonPrimitive("ok"))
            put("agentCount", JsonPrimitive(configuredAgents.size))
            put("sessionCount", JsonPrimitive(sessions.size))
            put("activeRuns", JsonPrimitive(activeRuns))
            put("toolCount", JsonPrimitive(allTools().map { it.name }.distinct().size))
            put("toolsProfile", JsonPrimitive(config.tools.profile))
            put("memoryEnabled", JsonPrimitive(config.memory.enabled))
            put("cronEnabled", JsonPrimitive(config.cron.enabled))
        }
    }

    router.register("agents.list") { _, _ ->
        val config = gatewayConfig()
        val sessionStats = sessionStore.listSessions().groupBy { it.agentId }
        val agents = configuredAgentEntries(config).map { entry ->
            val resolved = agentResolver.resolve(config.agents, entry.id)
            val sessions = sessionStats[entry.id].orEmpty()
            val skills = skillLoader?.buildStatus(
                workspacePath = resolved.workspace.workspacePath,
                config = config.skills,
                isConfigPathTruthy = { path -> config.isTruthyPath(path) }
            )?.skills
                ?.filter { it.eligible && !it.disabled }
                ?.map { it.name }
                ?.sorted()
                ?: emptyList()

            AgentInfo(
                id = entry.id,
                model = resolved.modelId.raw,
                isRunning = sessions.any { isSessionRunning(it.key) },
                sessionCount = sessions.size,
                totalTokens = sessions.sumOf(SessionMeta::totalTokens),
                tools = filterToolsForAgent(
                    allTools = allTools(),
                    toolPolicyPipeline = toolPolicyPipeline,
                    config = config,
                    agentId = entry.id,
                    provider = resolved.modelId.provider,
                    overrides = entry.tools
                ),
                skills = skills,
                workspaceFiles = listWorkspaceFiles(resolved.workspace.workspacePath)
            )
        }.sortedBy { if (it.id == DEFAULT_AGENT_ID) "" else it.id }

        buildJsonObject {
            put("agents", json.encodeToJsonElement(agents))
        }
    }

    router.register("channels.status") { _, _ ->
        val config = gatewayConfig()
        val channels = buildChannelStatuses(config, sessionStore)

        buildJsonObject {
            put("channels", json.encodeToJsonElement(channels))
            put("connected", JsonPrimitive(channels.count { it.status == "connected" }))
        }
    }

    router.register("workspace.read") { params, _ ->
        val config = gatewayConfig()
        val agentId = params["agentId"]?.jsonPrimitive?.contentOrNull
        val file = params.requireString("file")
        val resolved = agentResolver.resolve(config.agents, agentId ?: DEFAULT_AGENT_ID)
        val workspacePath = resolved.workspace.workspacePath.normalize()
        val target = resolveWorkspaceFile(workspacePath, file)

        if (!target.isRegularFile()) {
            throw IllegalArgumentException("Workspace file not found: '$file'")
        }
        if (!target.isReadable()) {
            throw IllegalArgumentException("Workspace file is not readable: '$file'")
        }
        if (target.fileSize() > MAX_WORKSPACE_READ_BYTES) {
            throw IllegalArgumentException("Workspace file exceeds ${MAX_WORKSPACE_READ_BYTES / 1024}KB limit: '$file'")
        }

        buildJsonObject {
            put("agentId", JsonPrimitive(agentId ?: DEFAULT_AGENT_ID))
            put("file", JsonPrimitive(file))
            put("content", JsonPrimitive(target.readText()))
        }
    }

    router.register("tools.catalog") { _, _ ->
        val config = gatewayConfig()
        val tools = allTools()
            .distinctBy { it.name }
            .filter { tool ->
                toolPolicyPipeline.evaluate(
                    toolName = tool.name,
                    toolsConfig = config.tools,
                    callerContext = CallerContext(isOwner = true, agentId = DEFAULT_AGENT_ID)
                ).isAllowed()
            }
            .map { tool ->
                ToolInfo(
                    name = tool.name,
                    description = tool.descriptor.description,
                    group = classifyToolGroup(tool.name),
                    permission = if (tool.name in DangerousTools.OWNER_ONLY || tool.name in DangerousTools.APPROVAL_REQUIRED) {
                        "elevated"
                    } else {
                        "standard"
                    }
                )
            }
            .sortedWith(compareBy<ToolInfo>({ it.group }, { it.name }))

        buildJsonObject {
            put("profile", JsonPrimitive(config.tools.profile))
            put("tools", json.encodeToJsonElement(tools))
        }
    }

    router.register("usage.summary") { params, _ ->
        val range = parseRange(params["range"]?.jsonPrimitive?.contentOrNull)
        val cutoff = Instant.now().minus(range.lookback)
        val sessions = sessionStore.listSessions()

        val timeline = linkedMapOf<Long, TimeBucket>()
        var totalTokens = 0L
        var totalMessages = 0
        var responseTimeTotalMs = 0L
        var responseSamples = 0
        val tokensByModel = linkedMapOf<String, Long>()
        val tokensByAgent = linkedMapOf<String, Long>()
        val sessionsByKey = linkedMapOf<String, SessionUsage>()

        initialiseBuckets(range).forEach { bucket ->
            timeline[bucket.start.toEpochMilli()] = TimeBucket(bucket.label)
        }

        for (session in sessions) {
            val history = sessionStore.getHistory(session.key)
            var pendingUserAt: Instant? = null
            var sessionTokens = 0L
            var sessionMessages = 0

            for (message in history) {
                val messageInstant = Instant.parse(message.timestamp.toString())
                if (messageInstant.isBefore(cutoff)) continue

                totalMessages += 1
                sessionMessages += 1

                when (message) {
                    is SessionMessage.UserMessage -> pendingUserAt = messageInstant
                    is SessionMessage.AssistantMessage -> {
                        val usage = message.usage
                        val promptTokens = usage?.promptTokens?.toLong() ?: 0L
                        val completionTokens = usage?.completionTokens?.toLong() ?: 0L
                        val messageTokens = usage?.totalTokens?.toLong() ?: promptTokens + completionTokens
                        val modelName = message.model ?: session.modelId ?: "unknown"

                        totalTokens += messageTokens
                        sessionTokens += messageTokens
                        tokensByModel[modelName] = (tokensByModel[modelName] ?: 0L) + messageTokens
                        tokensByAgent[session.agentId] = (tokensByAgent[session.agentId] ?: 0L) + messageTokens

                        timeline[bucketStartFor(messageInstant, range)]?.apply {
                            input += promptTokens
                            output += completionTokens
                        }

                        pendingUserAt?.let { userAt ->
                            val delta = ChronoUnit.MILLIS.between(userAt, messageInstant).coerceAtLeast(0L)
                            responseTimeTotalMs += delta
                            responseSamples += 1
                            pendingUserAt = null
                        }
                    }
                    else -> Unit
                }
            }

            if (sessionTokens > 0L || sessionMessages > 0) {
                sessionsByKey[session.key.raw] = SessionUsage(
                    key = session.key.raw,
                    tokens = sessionTokens,
                    messages = sessionMessages
                )
            }
        }

        val payload = UsageSummary(
            summary = UsageSummaryInfo(
                totalTokens = totalTokens,
                estimatedCost = 0.0,
                totalMessages = totalMessages,
                avgResponseTime = if (responseSamples == 0) 0 else responseTimeTotalMs / responseSamples
            ),
            tokensByTime = timeline.values.toList(),
            tokensByModel = tokensByModel.entries
                .sortedByDescending { it.value }
                .map { NamedUsage(name = it.key, tokens = it.value) },
            tokensByAgent = tokensByAgent.entries
                .sortedByDescending { it.value }
                .map { AgentUsage(agent = it.key, tokens = it.value) },
            topSessions = sessionsByKey.values
                .sortedWith(compareByDescending<SessionUsage> { it.tokens }.thenByDescending { it.messages })
                .take(10)
        )

        buildJsonObject {
            put("range", JsonPrimitive(range.name))
            put("summary", json.encodeToJsonElement(payload.summary))
            put("tokensByTime", json.encodeToJsonElement(payload.tokensByTime))
            put("tokensByModel", json.encodeToJsonElement(payload.tokensByModel))
            put("tokensByAgent", json.encodeToJsonElement(payload.tokensByAgent))
            put("topSessions", json.encodeToJsonElement(payload.topSessions))
        }
    }

    router.register("logs.recent") { params, _ ->
        val limit = params["limit"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.coerceIn(1, 500) ?: 200
        val entries = readRecentLogs(logsPath, limit)

        buildJsonObject {
            put("entries", json.encodeToJsonElement(entries))
        }
    }
}

private const val DEFAULT_AGENT_ID = "default"
private const val MAX_WORKSPACE_READ_BYTES = 256 * 1024L

private fun configuredAgentEntries(config: GatewayConfig): List<AgentEntry> =
    buildList {
        add(config.agents.list.firstOrNull { it.id == DEFAULT_AGENT_ID } ?: AgentEntry(id = DEFAULT_AGENT_ID))
        addAll(config.agents.list.filterNot { it.id == DEFAULT_AGENT_ID })
    }

private fun filterToolsForAgent(
    allTools: List<Tool<*, *>>,
    toolPolicyPipeline: ToolPolicyPipeline,
    config: GatewayConfig,
    agentId: String,
    provider: String,
    overrides: com.kclaw.gateway.config.AgentToolOverrides?
): List<String> {
    val toolNames = allTools.map { it.name }.toSet()
    return toolPolicyPipeline.filterAllowed(
        allToolNames = toolNames,
        toolsConfig = config.tools,
        agentToolOverrides = overrides,
        callerContext = CallerContext(
            isOwner = true,
            agentId = agentId,
            provider = provider
        )
    ).sorted()
}

private fun listWorkspaceFiles(workspacePath: Path): List<String> {
    val normalized = workspacePath.normalize()
    if (!Files.exists(normalized) || !normalized.isDirectory()) return emptyList()

    return Files.list(normalized).use { stream ->
        stream
            .filter { Files.isRegularFile(it) && Files.isReadable(it) }
            .map { normalized.relativize(it).toString() }
            .sorted()
            .limit(50)
            .toList()
    }
}

private fun resolveWorkspaceFile(workspacePath: Path, file: String): Path {
    val relative = Path.of(file)
    if (relative.isAbsolute) {
        throw IllegalArgumentException("Workspace file path must be relative")
    }

    val target = workspacePath.resolve(relative).normalize()
    if (!target.startsWith(workspacePath)) {
        throw IllegalArgumentException("Workspace file path escapes the agent workspace")
    }
    return target
}

private fun classifyToolGroup(toolName: String): String =
    when {
        toolName in setOf("exec", "process") -> "exec"
        toolName in setOf("read", "write", "edit", "apply_patch") -> "filesystem"
        toolName.startsWith("web_") -> "web"
        toolName.startsWith("sessions_") || toolName == "session_status" -> "sessions"
        toolName.startsWith("memory_") -> "runtime"
        else -> "native"
    }

private suspend fun buildChannelStatuses(
    config: GatewayConfig,
    sessionStore: SessionStore
): List<ChannelInfo> {
    val now = Instant.now()
    val last24h = now.minus(24, ChronoUnit.HOURS)
    val sessions = sessionStore.listSessions()
    val configuredChannels = config.agents.list.flatMap { it.channels }.toSet()
    val observedChannels = sessions.mapNotNull { it.channel }.toSet()
    val channelIds = (configuredChannels + observedChannels + setOf("webchat")).sorted()

    return channelIds.map { channelId ->
        val channelSessions = sessions.filter { it.channel == channelId }
        var messageCount24h = 0

        for (session in channelSessions) {
            val history = sessionStore.getHistory(session.key)
            messageCount24h += history.count { message ->
                Instant.parse(message.timestamp.toString()).isAfter(last24h)
            }
        }

        val lastActivity = channelSessions.mapNotNull { it.lastMessageAt }
            .maxByOrNull { it.toEpochMilliseconds() }

        val configured = channelId == "webchat" || channelId in configuredChannels || channelSessions.isNotEmpty()
        val status = when {
            channelId == "webchat" -> "connected"
            messageCount24h > 0 -> "connected"
            configured -> "disconnected"
            else -> "unconfigured"
        }

        ChannelInfo(
            id = channelId,
            status = status,
            lastActivity = lastActivity?.toString(),
            messageCount24h = messageCount24h,
            config = buildMap {
                val boundAgents = config.agents.list
                    .filter { channelId in it.channels || (channelId == "webchat" && it.channels.isEmpty()) }
                    .map { it.id }
                    .sorted()
                if (boundAgents.isNotEmpty()) {
                    put("boundAgents", boundAgents)
                }
            }.takeIf { it.isNotEmpty() }
        )
    }
}

private fun parseRange(value: String?): UsageRange =
    when (value?.lowercase(Locale.US)) {
        "24h" -> UsageRange("24h", 24, 24, ChronoUnit.HOURS, ChronoUnit.HOURS, DateTimeFormatter.ofPattern("HH:mm"))
        "30d" -> UsageRange("30d", 30, 30, ChronoUnit.DAYS, ChronoUnit.DAYS, DateTimeFormatter.ofPattern("MMM d"))
        else -> UsageRange("7d", 7, 7, ChronoUnit.DAYS, ChronoUnit.DAYS, DateTimeFormatter.ofPattern("MMM d"))
    }

private fun readRecentLogs(logsPath: Path?, limit: Int): List<LogEntry> {
    val path = logsPath?.normalize() ?: return emptyList()
    if (!Files.exists(path) || !path.isRegularFile() || !path.isReadable()) return emptyList()

    val timestamp = Instant.ofEpochMilli(Files.getLastModifiedTime(path).toMillis()).toString()
    val lines = path.readText()
        .lineSequence()
        .filter { it.isNotBlank() }
        .toList()
        .takeLast(limit)

    return lines.mapIndexed { index, line ->
        LogEntry(
            id = "${path.fileName}-$index",
            timestamp = timestamp,
            level = classifyLogLevel(line),
            source = "agent-runtime",
            message = line.trim()
        )
    }
}

private fun classifyLogLevel(line: String): String {
    val lower = line.lowercase(Locale.US)
    return when {
        "error" in lower || "exception" in lower -> "ERROR"
        "warn" in lower -> "WARN"
        "debug" in lower -> "DEBUG"
        else -> "INFO"
    }
}

private fun initialiseBuckets(range: UsageRange): List<BucketSeed> {
    val now = ZonedDateTime.now(ZoneOffset.UTC)
    val current = truncate(now, range.bucketUnit)
    return (range.bucketCount - 1 downTo 0).map { index ->
        val start = current.minus(index.toLong(), range.bucketUnit)
        BucketSeed(
            start = start.toInstant(),
            label = start.format(range.labelFormatter)
        )
    }
}

private fun bucketStartFor(timestamp: Instant, range: UsageRange): Long =
    truncate(ZonedDateTime.ofInstant(timestamp, ZoneOffset.UTC), range.bucketUnit)
        .toInstant()
        .toEpochMilli()

private fun truncate(time: ZonedDateTime, unit: ChronoUnit): ZonedDateTime =
    when (unit) {
        ChronoUnit.HOURS -> time.truncatedTo(ChronoUnit.HOURS)
        ChronoUnit.DAYS -> time.truncatedTo(ChronoUnit.DAYS)
        else -> time
    }

private fun JsonObject.requireString(name: String): String =
    this[name]?.jsonPrimitive?.contentOrNull
        ?: throw IllegalArgumentException("Missing required parameter: '$name'")

private fun com.kclaw.protocol.dto.ToolPermission.isAllowed(): Boolean =
    this is com.kclaw.protocol.dto.ToolPermission.Allowed

@Serializable
private data class AgentInfo(
    val id: String,
    val model: String,
    val isRunning: Boolean,
    val sessionCount: Int,
    val totalTokens: Long,
    val tools: List<String>,
    val skills: List<String>,
    val workspaceFiles: List<String>
)

@Serializable
private data class ToolInfo(
    val name: String,
    val description: String,
    val group: String,
    val permission: String
)

@Serializable
private data class ChannelInfo(
    val id: String,
    val status: String,
    val lastActivity: String? = null,
    val messageCount24h: Int = 0,
    val config: Map<String, List<String>>? = null
)

@Serializable
private data class LogEntry(
    val id: String,
    val timestamp: String,
    val level: String,
    val source: String,
    val message: String
)

private data class UsageRange(
    val name: String,
    val bucketCount: Int,
    val lookbackUnitCount: Long,
    val lookbackUnit: ChronoUnit,
    val bucketUnit: ChronoUnit,
    val labelFormatter: DateTimeFormatter
) {
    val lookback = java.time.Duration.of(lookbackUnitCount, lookbackUnit)
}

private data class BucketSeed(
    val start: Instant,
    val label: String
)

@Serializable
private data class UsageSummary(
    val summary: UsageSummaryInfo,
    val tokensByTime: List<TimeBucket>,
    val tokensByModel: List<NamedUsage>,
    val tokensByAgent: List<AgentUsage>,
    val topSessions: List<SessionUsage>
)

@Serializable
private data class UsageSummaryInfo(
    val totalTokens: Long,
    val estimatedCost: Double,
    val totalMessages: Int,
    val avgResponseTime: Long
)

@Serializable
private data class TimeBucket(
    val time: String,
    var input: Long = 0,
    var output: Long = 0
)

@Serializable
private data class NamedUsage(
    val name: String,
    val tokens: Long
)

@Serializable
private data class AgentUsage(
    val agent: String,
    val tokens: Long
)

@Serializable
private data class SessionUsage(
    val key: String,
    val tokens: Long,
    val messages: Int
)
