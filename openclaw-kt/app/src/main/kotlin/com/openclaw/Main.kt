package com.openclaw

import com.openclaw.agent.isolation.AgentResolver
import com.openclaw.agent.model.ModelDefinition
import com.openclaw.agent.model.ModelRegistry
import com.openclaw.agent.model.readClaudeCliCredentials
import com.openclaw.agent.rpc.registerAgentHandlers
import com.openclaw.agent.rpc.registerSkillsHandlers
import com.openclaw.agent.runtime.AgentRuntime
import com.openclaw.agent.runtime.CompactionTrigger
import com.openclaw.agent.runtime.MemoryFlushRunner
import com.openclaw.agent.runtime.RunManager
import com.openclaw.agent.tools.NativeToolsProvider
import com.openclaw.gateway.AppPaths
import com.openclaw.gateway.GatewayServer
import com.openclaw.gateway.config.ConfigManager
import com.openclaw.gateway.config.ConfigWriter
import com.openclaw.gateway.rpc.ModelInfo
import com.openclaw.gateway.rpc.registerConfigHandlers
import com.openclaw.gateway.rpc.registerSessionHandlers
import com.openclaw.gateway.rpc.registerSetupHandlers
import com.openclaw.gateway.ws.SetupStatus
import com.openclaw.protocol.identifiers.SessionKey
import com.openclaw.session.compaction.CompactableSessionStore
import com.openclaw.session.compaction.CompactionConfig
import com.openclaw.session.compaction.CompactionEngine
import com.openclaw.session.compaction.CompactionResult
import com.openclaw.session.composite.CompositeSessionStore
import com.openclaw.session.jsonl.JsonlSessionProvider
import com.openclaw.session.sqlite.SqliteKoogPersistenceProvider
import ai.koog.agents.core.tools.ToolRegistry
import com.openclaw.tools.ToolRegistryFactory
import com.openclaw.tools.exec.BackgroundProcessTracker
import com.openclaw.tools.exec.ExecTool
import com.openclaw.tools.exec.ProcessTool
import com.openclaw.tools.filesystem.EditTool
import com.openclaw.tools.filesystem.ReadTool
import com.openclaw.tools.filesystem.WriteTool
import com.openclaw.tools.mcp.McpServerConfig
import com.openclaw.tools.mcp.McpToolsProvider
import com.openclaw.tools.mcp.McpTransport
import com.openclaw.tools.policy.ToolPolicyPipeline
import com.openclaw.tools.web.WebFetchTool
import com.openclaw.tools.web.WebSearchTool
import com.openclaw.tools.memory.MemorySearchTool
import com.openclaw.tools.memory.MemoryGetTool
import com.openclaw.memory.MemoryManager
import com.openclaw.memory.rpc.registerMemoryHandlers
import com.openclaw.agent.workspace.WorkspaceBootstrap
import com.openclaw.skills.SkillLoader
import com.openclaw.skills.SkillsWatcherService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

fun main() {
    logger.info { "OpenClaw-KT v0.1.0-SNAPSHOT" }

    // Ensure base directory and default config exist
    ensureDefaultConfig()

    val configPath = AppPaths.config
    val configManager = ConfigManager(configPath)
    val config = configManager.load()

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    configManager.startFileWatcher(appScope)

    val checkpointProvider = if (config.persistence.enabled) {
        val sqlitePath = resolveConfiguredPath(config.persistence.sqlite.path)
        try {
            SqliteKoogPersistenceProvider.connect(sqlitePath).also {
                logger.info {
                    "Koog persistence initialized (SQLite: $sqlitePath, recovery mode: message-history-only)"
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Koog persistence initialization failed, continuing without resumable checkpoints" }
            null
        }
    } else {
        logger.info { "Koog persistence disabled" }
        null
    }

    // Session store
    val sessionsDir = AppPaths.sessions
    val jsonlProvider = JsonlSessionProvider(sessionsDir)
    val sessionStore = CompositeSessionStore(
        jsonlProvider,
        checkpointProvider = null,
        checkpointCleaner = checkpointProvider
    )
    logger.info {
        "Session store initialized (JSONL transcripts${if (checkpointProvider != null) " + SQLite Koog checkpoints" else " only"})"
    }

    // Agent engine — resolve API keys from config secrets first, then env vars
    val modelRegistry = ModelRegistry(
        apiKeyResolver = { envVar ->
            val secrets = configManager.config.value.secrets
            when (envVar) {
                "ANTHROPIC_API_KEY" -> secrets.anthropicApiKey ?: System.getenv(envVar)
                "OPENAI_API_KEY" -> secrets.openaiApiKey ?: System.getenv(envVar)
                else -> System.getenv(envVar)
            }
        },
        providersConfig = config.providers
    )
    // ProviderFactory self-heals: compares cached key vs current on each getExecutor().
    // No explicit config-flow watcher needed here. AgentEngine wires one when used.

    // Compaction + pre-compaction memory flush
    val compactionTrigger = (sessionStore as? CompactableSessionStore)?.let { compactable ->
        val summarizer = { messages: List<com.openclaw.session.SessionMessage> ->
            // Simple extractive summarizer — concatenate message snippets.
            // A proper LLM-backed summarizer can replace this later.
            val summary = messages.joinToString("\n") { msg ->
                when (msg) {
                    is com.openclaw.session.SessionMessage.UserMessage -> "User: ${msg.text.take(200)}"
                    is com.openclaw.session.SessionMessage.AssistantMessage -> "Assistant: ${msg.text.take(200)}"
                    is com.openclaw.session.SessionMessage.CompactionSummary -> "[Summary]: ${msg.summary.take(200)}"
                    is com.openclaw.session.SessionMessage.ToolCall -> "[Tool: ${msg.toolName}]"
                    is com.openclaw.session.SessionMessage.ToolResult -> "[Result: ${msg.result.take(100)}]"
                    is com.openclaw.session.SessionMessage.SystemEvent -> ""
                }.take(300)
            }
            CompactionResult(summary, summary.length / 4)
        }
        val engine = CompactionEngine(compactable, { messages -> summarizer(messages) })
        val writeOnlyRegistry = ToolRegistry { tool(WriteTool) }
        val flushRunner = MemoryFlushRunner(modelRegistry, writeOnlyRegistry)
        CompactionTrigger(engine, flushRunner, sessionStore)
    }
    logger.info { "Compaction trigger: ${if (compactionTrigger != null) "enabled" else "disabled (session store not compactable)"}" }

    val traceOutputPath = AppPaths.traces.resolve("agent-runtime.log")
    val agentRuntime = AgentRuntime(
        modelRegistry,
        sessionStore,
        compactionTrigger,
        checkpointProvider,
        traceOutputPath
    )
    logger.info { "Koog tracing initialized (file: $traceOutputPath)" }
    val runManager = RunManager(agentRuntime, appScope)
    val agentResolver = AgentResolver()

    // Workspace bootstrap — seed templates on first run
    val workspacePath = resolveConfiguredPath(config.agents.defaults.workspace)
    val workspaceBootstrap = WorkspaceBootstrap(workspacePath)
    val workspaceState = workspaceBootstrap.ensureWorkspace()
    logger.info { "Workspace state: ${workspaceState.phase}" }

    // Memory system
    val memoryManager = if (config.memory.enabled) {
        try {
            MemoryManager(
                config = config.memory,
                workspacePath = workspacePath,
                apiKeyResolver = { envVar ->
                    val secrets = configManager.config.value.secrets
                    when (envVar) {
                        "OPENAI_API_KEY" -> secrets.openaiApiKey ?: System.getenv(envVar)
                        else -> System.getenv(envVar)
                    }
                }
            ).also { logger.info { "Memory system initialized (${it.config.provider})" } }
        } catch (e: Exception) {
            logger.warn(e) { "Memory system initialization failed, continuing without memory" }
            null
        }
    } else {
        logger.info { "Memory system disabled" }
        null
    }

    // Tools system
    val backgroundTracker = BackgroundProcessTracker()

    // Configure core tools with workspace and tracker
    val workspaceOnly = config.tools.fs.workspaceOnly
    ReadTool.workspacePath = workspacePath
    ReadTool.enforceBoundary = workspaceOnly
    WriteTool.workspacePath = workspacePath
    WriteTool.enforceBoundary = workspaceOnly
    EditTool.workspacePath = workspacePath
    EditTool.enforceBoundary = workspaceOnly
    ExecTool.workspacePath = workspacePath
    ExecTool.backgroundTracker = backgroundTracker
    ProcessTool.tracker = backgroundTracker

    val coreTools = listOf(ReadTool, WriteTool, EditTool, ExecTool, ProcessTool)

    // Native tools (session introspection)
    val nativeToolsProvider = NativeToolsProvider(
        sessionStore = sessionStore,
        runManager = runManager,
        agentsConfig = { configManager.config.value.agents },
        currentSessionKey = { SessionKey("main:main:*:main") }, // default; overridden per-run
        workspacePath = { workspacePath }
    )
    val nativeTools = nativeToolsProvider.allTools()

    // MCP tools (external tool providers)
    val mcpConfigs = config.tools.mcp.map { entry ->
        McpServerConfig(
            name = entry.name,
            transport = McpTransport.valueOf(entry.transport.uppercase()),
            command = entry.command,
            args = entry.args,
            url = entry.url,
            env = entry.env,
            enabled = entry.enabled
        )
    }
    val mcpProvider = McpToolsProvider(mcpConfigs)
    val mcpTools = runBlocking { mcpProvider.discoverTools() }
    Runtime.getRuntime().addShutdownHook(Thread { mcpProvider.disconnect() })

    // Web tools (search + fetch)
    val webTools = listOfNotNull(
        if (WebSearchTool.isAvailable()) WebSearchTool else null,
        WebFetchTool
    )
    if (WebSearchTool.isAvailable()) {
        logger.info { "Web search enabled (BRAVE_API_KEY configured)" }
    } else {
        logger.info { "Web search disabled (BRAVE_API_KEY not set)" }
    }

    // Memory tools
    MemorySearchTool.memoryManager = memoryManager
    MemoryGetTool.workspacePath = workspacePath
    val memoryTools = listOf(MemorySearchTool, MemoryGetTool)

    val allTools = coreTools + nativeTools + mcpTools + webTools + memoryTools
    val toolPolicyPipeline = ToolPolicyPipeline()
    val toolRegistryFactory = ToolRegistryFactory(toolPolicyPipeline, allTools)
    logger.info { "Tool system initialized: ${allTools.size} tools (${coreTools.size} core, ${nativeTools.size} native, ${mcpTools.size} MCP, ${webTools.size} web, ${memoryTools.size} memory)" }

    // Skills system
    val skillLoader = SkillLoader()
    val skillsWatcher = SkillsWatcherService(skillLoader, appScope)
    logger.info { "Skills system initialized" }

    // Config writer for setup handlers
    val configWriter = ConfigWriter(configPath)

    // Setup status provider — checks workspace phase
    val setupStatusProvider: () -> SetupStatus = {
        val phase = workspaceBootstrap.currentState().phase.name
        SetupStatus(
            setupRequired = phase != "ONBOARDING_COMPLETE",
            onboardingPhase = phase
        )
    }

    val gateway = GatewayServer(config, setupStatusProvider)

    // Register RPC handlers
    registerConfigHandlers(gateway.router, configManager) {
        buildModelCatalog(modelRegistry, configManager)
    }
    registerAgentHandlers(
        router = gateway.router,
        runManager = runManager,
        agentResolver = agentResolver,
        sessionRegistry = gateway.sessionRegistry,
        agentsConfig = { configManager.config.value.agents },
        toolRegistryFactory = toolRegistryFactory,
        toolsConfig = { configManager.config.value.tools },
        gatewayConfig = { configManager.config.value },
        skillLoader = skillLoader,
        skillsWatcher = skillsWatcher
    )
    registerSkillsHandlers(
        router = gateway.router,
        agentResolver = agentResolver,
        gatewayConfig = { configManager.config.value },
        skillLoader = skillLoader,
        configManager = configManager,
        configWriter = configWriter,
        skillsWatcher = skillsWatcher
    )
    registerSessionHandlers(gateway.router, sessionStore)
    registerMemoryHandlers(gateway.router, memoryManager)
    registerSetupHandlers(
        router = gateway.router,
        configManager = configManager,
        configWriter = configWriter,
        setupStatusProvider = { workspaceBootstrap.currentState().phase.name },
        markOnboardingComplete = { workspaceBootstrap.markOnboardingComplete() }
    )

    gateway.start()

    logger.info { "Gateway running on ${config.gateway.bind}:${config.gateway.port}" }
    logger.info { "Health: http://${config.gateway.bind}:${config.gateway.port}/health" }
    logger.info { "WebSocket: ws://${config.gateway.bind}:${config.gateway.port}/" }
    logger.info { "Press Ctrl+C to stop" }

    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info { "Shutting down..." }
        configManager.stopFileWatcher()
        skillsWatcher.stopAll()
        appScope.cancel()
        gateway.stop()
    })

    // Block the main thread so the server keeps running
    Thread.currentThread().join()
}

/**
 * Ensures ~/.openclawk/ exists and seeds openclaw.conf if missing.
 * The generated file at ~/.openclawk/openclaw.conf is the single source of truth.
 */
private fun ensureDefaultConfig() {
    val baseDir = AppPaths.base
    if (!baseDir.toFile().exists()) {
        baseDir.toFile().mkdirs()
        logger.info { "Created base directory: $baseDir" }
    }

    val configFile = AppPaths.config
    if (!configFile.toFile().exists()) {
        configFile.toFile().writeText(DEFAULT_CONFIG)
        logger.info { "Generated default config: $configFile" }
    }
}

private fun buildModelCatalog(
    sourceRegistry: ModelRegistry,
    configManager: ConfigManager
): List<ModelInfo> {
    val currentConfig = configManager.config.value
    val readinessRegistry = ModelRegistry(
        apiKeyResolver = { envVar ->
            val secrets = currentConfig.secrets
            when (envVar) {
                "ANTHROPIC_API_KEY" -> secrets.anthropicApiKey ?: System.getenv(envVar)
                "OPENAI_API_KEY" -> secrets.openaiApiKey ?: System.getenv(envVar)
                "GEMINI_API_KEY" -> secrets.googleApiKey ?: System.getenv(envVar)
                "OPENROUTER_API_KEY" -> secrets.openrouterApiKey ?: System.getenv(envVar)
                "MISTRAL_API_KEY" -> secrets.mistralApiKey ?: System.getenv(envVar)
                "DEEPSEEK_API_KEY" -> secrets.deepseekApiKey ?: System.getenv(envVar)
                "GROQ_API_KEY" -> secrets.groqApiKey ?: System.getenv(envVar)
                "XAI_API_KEY" -> secrets.xaiApiKey ?: System.getenv(envVar)
                "TOGETHER_API_KEY" -> secrets.togetherApiKey ?: System.getenv(envVar)
                "NVIDIA_API_KEY" -> secrets.nvidiaApiKey ?: System.getenv(envVar)
                "CEREBRAS_API_KEY" -> secrets.cerebrasApiKey ?: System.getenv(envVar)
                "MOONSHOT_API_KEY" -> secrets.moonshotApiKey ?: System.getenv(envVar)
                "VLLM_API_KEY" -> secrets.vllmApiKey ?: System.getenv(envVar)
                "LITELLM_API_KEY" -> secrets.litellmApiKey ?: System.getenv(envVar)
                "AZURE_OPENAI_API_KEY" -> secrets.azureApiKey ?: System.getenv(envVar)
                else -> System.getenv(envVar)
            }
        },
        providersConfig = currentConfig.providers,
        claudeCliCredentialReader = { readClaudeCliCredentials() }
    )

    val definitions = sourceRegistry.list()
        .sortedWith(compareBy<ModelDefinition>({ it.provider }, { it.displayName }))

    definitions.forEach(readinessRegistry::register)

    return definitions.map { def ->
        val availability = readinessRegistry.availability(def)
        ModelInfo(
            id = def.id.raw,
            provider = def.provider,
            displayName = def.displayName,
            ready = availability.ready,
            unavailableReason = availability.unavailableReason
        )
    }
}

private val DEFAULT_CONFIG = """
# ──────────────────────────────────────────────────────────────────────
# OpenClaw-KT Configuration (HOCON format)
# ──────────────────────────────────────────────────────────────────────
# This is the single config file. Edit values below to customize.
# Changes are hot-reloaded (no restart needed unless reload.mode = OFF).
# Environment variables: use ${'$'}{ENV_VAR} syntax.
# All values shown are defaults — uncomment and change as needed.
# ──────────────────────────────────────────────────────────────────────

gateway {
    # port = 18789
    # bind = "127.0.0.1"

    auth {
        # token = "your-secret-token"
        # password = "your-password"
    }

    reload {
        # mode = "HYBRID"  # HYBRID | HOT | RESTART | OFF
    }
}

agents {
    defaults {
        # model = "anthropic:claude-sonnet-4"
        # workspace = "~/.openclawk/workspace"
        # compaction = "safeguard"
        # maxConcurrentRuns = 4
    }

    # list = [
    #     { id = "telegram", model = "anthropic:claude-haiku-4-5" }
    # ]
}

tools {
    # profile = "full"
    # allow = []
    # deny = []

    sandbox {
        # enabled = false
    }

    loopDetection {
        # enabled = true
        # repeatWarning = 10
        # repeatCritical = 20
        # circuitBreaker = 30
    }

    # mcp = [
    #     { name = "server", transport = "stdio", command = "/path/to/server" }
    # ]
}

secrets {
    # anthropicApiKey = "sk-ant-..."
    # openaiApiKey = "sk-..."
}

memory {
    # enabled = true
    # provider = "openai"
    # model = "text-embedding-3-small"
}

cron {
    # enabled = true
}

persistence {
    # enabled = true

    sqlite {
        # path = "~/.openclawk/checkpoints.sqlite"
    }
}
""".trimIndent()

private fun resolveConfiguredPath(configuredPath: String): Path =
    Path.of(configuredPath.replace("~", System.getProperty("user.home")))
