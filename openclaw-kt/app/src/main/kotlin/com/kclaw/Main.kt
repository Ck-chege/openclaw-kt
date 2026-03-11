package com.kclaw

import com.kclaw.agent.isolation.AgentResolver
import com.kclaw.agent.model.ModelDefinition
import com.kclaw.agent.model.ModelRegistry
import com.kclaw.agent.model.readClaudeCliCredentials
import com.kclaw.agent.rpc.registerAgentHandlers
import com.kclaw.agent.rpc.registerSkillsHandlers
import com.kclaw.agent.runtime.AgentRuntime
import com.kclaw.agent.runtime.CompactionTrigger
import com.kclaw.agent.runtime.MemoryFlushRunner
import com.kclaw.agent.runtime.RunManager
import com.kclaw.agent.tools.NativeToolsProvider
import com.kclaw.gateway.AppPaths
import com.kclaw.gateway.GatewayServer
import com.kclaw.gateway.config.ConfigManager
import com.kclaw.gateway.config.ConfigWriter
import com.kclaw.gateway.rpc.ModelInfo
import com.kclaw.gateway.rpc.registerConfigHandlers
import com.kclaw.gateway.rpc.registerSessionHandlers
import com.kclaw.gateway.rpc.registerSetupHandlers
import com.kclaw.gateway.ws.SetupStatus
import com.kclaw.protocol.identifiers.SessionKey
import com.kclaw.session.compaction.CompactableSessionStore
import com.kclaw.session.compaction.CompactionConfig
import com.kclaw.session.compaction.CompactionEngine
import com.kclaw.session.compaction.CompactionResult
import com.kclaw.session.composite.CompositeSessionStore
import com.kclaw.session.jsonl.JsonlSessionProvider
import com.kclaw.session.sqlite.SqliteKoogPersistenceProvider
import ai.koog.agents.core.tools.ToolRegistry
import com.kclaw.tools.ToolRegistryFactory
import com.kclaw.tools.exec.BackgroundProcessTracker
import com.kclaw.tools.exec.ExecTool
import com.kclaw.tools.exec.ProcessTool
import com.kclaw.tools.filesystem.EditTool
import com.kclaw.tools.filesystem.ReadTool
import com.kclaw.tools.filesystem.WriteTool
import com.kclaw.tools.mcp.McpServerConfig
import com.kclaw.tools.mcp.McpToolsProvider
import com.kclaw.tools.mcp.McpTransport
import com.kclaw.tools.policy.ToolPolicyPipeline
import com.kclaw.tools.web.WebFetchTool
import com.kclaw.tools.web.WebSearchTool
import com.kclaw.tools.memory.MemorySearchTool
import com.kclaw.tools.memory.MemoryGetTool
import com.kclaw.memory.MemoryManager
import com.kclaw.memory.rpc.registerMemoryHandlers
import com.kclaw.agent.workspace.WorkspaceBootstrap
import com.kclaw.skills.SkillLoader
import com.kclaw.skills.SkillsWatcherService
import com.kclaw.rpc.registerOperatorHandlers
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

fun main() {
    logger.info { "KClaw v0.1.0-SNAPSHOT" }

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
        val summarizer = { messages: List<com.kclaw.session.SessionMessage> ->
            // Simple extractive summarizer — concatenate message snippets.
            // A proper LLM-backed summarizer can replace this later.
            val summary = messages.joinToString("\n") { msg ->
                when (msg) {
                    is com.kclaw.session.SessionMessage.UserMessage -> "User: ${msg.text.take(200)}"
                    is com.kclaw.session.SessionMessage.AssistantMessage -> "Assistant: ${msg.text.take(200)}"
                    is com.kclaw.session.SessionMessage.CompactionSummary -> "[Summary]: ${msg.summary.take(200)}"
                    is com.kclaw.session.SessionMessage.ToolCall -> "[Tool: ${msg.toolName}]"
                    is com.kclaw.session.SessionMessage.ToolResult -> "[Result: ${msg.result.take(100)}]"
                    is com.kclaw.session.SessionMessage.SystemEvent -> ""
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

    // Skills system
    val skillLoader = SkillLoader()
    val skillsWatcher = SkillsWatcherService(skillLoader, appScope)
    logger.info { "Skills system initialized" }

    // Native tools (session introspection)
    val nativeToolsProvider = NativeToolsProvider(
        sessionStore = sessionStore,
        runManager = runManager,
        agentsConfig = { configManager.config.value.agents },
        currentSessionKey = { SessionKey("main:main:*:main") }, // default; overridden per-run
        workspacePath = { workspacePath },
        skillLoader = skillLoader
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
    registerOperatorHandlers(
        router = gateway.router,
        gatewayConfig = { configManager.config.value },
        agentResolver = agentResolver,
        sessionStore = sessionStore,
        allTools = { allTools },
        toolPolicyPipeline = toolPolicyPipeline,
        skillLoader = skillLoader,
        isSessionRunning = { key -> runManager.isRunActive(key) },
        logsPath = traceOutputPath
    )
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
 * Ensures ~/.kclaw/ exists and seeds kclaw.conf if missing.
 * The generated file at ~/.kclaw/kclaw.conf is the single source of truth.
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
# KClaw Configuration (HOCON format)
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
        # workspace = "~/.kclaw/workspace"
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
        # path = "~/.kclaw/checkpoints.sqlite"
    }
}
""".trimIndent()

private fun resolveConfiguredPath(configuredPath: String): Path =
    Path.of(configuredPath.replace("~", System.getProperty("user.home")))
