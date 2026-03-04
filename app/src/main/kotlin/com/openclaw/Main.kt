package com.openclaw

import com.openclaw.agent.isolation.AgentResolver
import com.openclaw.agent.model.ModelRegistry
import com.openclaw.agent.rpc.registerAgentHandlers
import com.openclaw.agent.runtime.AgentRuntime
import com.openclaw.agent.runtime.RunManager
import com.openclaw.agent.tools.NativeToolsProvider
import com.openclaw.gateway.GatewayServer
import com.openclaw.gateway.config.ConfigManager
import com.openclaw.gateway.rpc.registerConfigHandlers
import com.openclaw.gateway.rpc.registerSessionHandlers
import com.openclaw.protocol.identifiers.SessionKey
import com.openclaw.session.composite.CompositeSessionStore
import com.openclaw.session.jsonl.JsonlSessionProvider
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
import com.openclaw.agent.workspace.WorkspaceBootstrap
import com.openclaw.skills.SkillLoader
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

fun main() {
    logger.info { "OpenClaw-KT v0.1.0-SNAPSHOT" }

    val configPath = Path.of(
        System.getProperty("user.home"), ".openclawk", "openclaw.conf"
    )
    val configManager = ConfigManager(configPath)
    val config = configManager.load()

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    configManager.startFileWatcher(appScope)

    // Session store
    val sessionsDir = Path.of(System.getProperty("user.home"), ".openclawk", "sessions")
    val jsonlProvider = JsonlSessionProvider(sessionsDir)
    val sessionStore = CompositeSessionStore(jsonlProvider, checkpointProvider = null)
    logger.info { "Session store initialized (JSONL-only, Postgres checkpoints not configured)" }

    // Agent engine
    val modelRegistry = ModelRegistry()
    val agentRuntime = AgentRuntime(modelRegistry, sessionStore)
    val runManager = RunManager(agentRuntime, appScope)
    val agentResolver = AgentResolver()

    // Workspace bootstrap — seed templates on first run
    val workspacePath = Path.of(config.agents.defaults.workspace.replace("~", System.getProperty("user.home")))
    val workspaceBootstrap = WorkspaceBootstrap(workspacePath)
    val workspaceState = workspaceBootstrap.ensureWorkspace()
    logger.info { "Workspace state: ${workspaceState.phase}" }

    // Tools system
    val backgroundTracker = BackgroundProcessTracker()

    // Configure core tools with workspace and tracker
    ReadTool.workspacePath = workspacePath
    WriteTool.workspacePath = workspacePath
    EditTool.workspacePath = workspacePath
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
    val mcpTools = mcpProvider.discoverTools()

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

    val allTools = coreTools + nativeTools + mcpTools + webTools
    val toolPolicyPipeline = ToolPolicyPipeline()
    val toolRegistryFactory = ToolRegistryFactory(toolPolicyPipeline, allTools)
    logger.info { "Tool system initialized: ${allTools.size} tools (${coreTools.size} core, ${nativeTools.size} native, ${mcpTools.size} MCP, ${webTools.size} web)" }

    // Skills system
    val skillLoader = SkillLoader()
    logger.info { "Skills system initialized" }

    val gateway = GatewayServer(config)

    // Register RPC handlers
    registerConfigHandlers(gateway.router, configManager)
    registerAgentHandlers(
        router = gateway.router,
        runManager = runManager,
        agentResolver = agentResolver,
        sessionRegistry = gateway.sessionRegistry,
        agentsConfig = { configManager.config.value.agents },
        toolRegistryFactory = toolRegistryFactory,
        toolsConfig = { configManager.config.value.tools },
        skillLoader = skillLoader
    )
    registerSessionHandlers(gateway.router, sessionStore)

    gateway.start()

    logger.info { "Gateway running on ${config.gateway.bind}:${config.gateway.port}" }
    logger.info { "Health: http://${config.gateway.bind}:${config.gateway.port}/health" }
    logger.info { "WebSocket: ws://${config.gateway.bind}:${config.gateway.port}/" }
    logger.info { "Press Ctrl+C to stop" }

    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info { "Shutting down..." }
        configManager.stopFileWatcher()
        appScope.cancel()
        gateway.stop()
    })

    // Block the main thread so the server keeps running
    Thread.currentThread().join()
}
