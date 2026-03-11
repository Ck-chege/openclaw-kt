package com.kclaw.tools.mcp

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.defaultStdioTransport
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Discovers and connects to MCP (Model Context Protocol) servers, exposing their
 * tools as Koog [Tool] instances that can be included in the agent's ToolRegistry.
 */
class McpToolsProvider(
    private val servers: List<McpServerConfig>,
    private val registryFactory: McpRegistryFactory = KoogMcpRegistryFactory(),
    private val processLauncher: McpProcessLauncher = DefaultMcpProcessLauncher
) {
    private val managedProcesses = mutableListOf<Process>()

    /**
     * Connect to all enabled MCP servers and return their tools.
     */
    suspend fun discoverTools(): List<Tool<*, *>> {
        val enabled = servers.filter { it.enabled }
        if (enabled.isEmpty()) {
            logger.debug { "No MCP servers configured" }
            return emptyList()
        }

        val discovered = mutableListOf<Tool<*, *>>()
        enabled.forEach { config ->
            try {
                val registry = when (config.transport) {
                    McpTransport.STDIO -> {
                        val process = processLauncher.start(config)
                        managedProcesses += process
                        registryFactory.fromStdio(config, process)
                    }

                    McpTransport.SSE -> registryFactory.fromSse(config)
                }
                discovered += registry.tools
                logger.info {
                    "MCP server '${config.name}' connected via ${config.transport} with ${registry.tools.size} tool(s)"
                }
            } catch (e: Exception) {
                logger.warn(e) {
                    "Failed to connect MCP server '${config.name}' via ${config.transport}; continuing without it"
                }
            }
        }

        return discovered.distinctBy { it.name }
    }

    /**
     * Disconnect from all active MCP server connections.
     */
    fun disconnect() {
        managedProcesses.forEach { process ->
            if (process.isAlive) {
                process.destroy()
            }
        }
        managedProcesses.clear()
        logger.debug { "MCP disconnect complete" }
    }
}

fun interface McpProcessLauncher {
    fun start(config: McpServerConfig): Process
}

private object DefaultMcpProcessLauncher : McpProcessLauncher {
    override fun start(config: McpServerConfig): Process {
        require(config.transport == McpTransport.STDIO) {
            "Process launcher can only start STDIO MCP servers"
        }
        val command = buildList {
            add(config.command!!)
            addAll(config.args)
        }
        return ProcessBuilder(command)
            .apply { environment().putAll(config.env) }
            .start()
    }
}

interface McpRegistryFactory {
    suspend fun fromStdio(config: McpServerConfig, process: Process): ToolRegistry
    suspend fun fromSse(config: McpServerConfig): ToolRegistry
}

private class KoogMcpRegistryFactory : McpRegistryFactory {
    override suspend fun fromStdio(config: McpServerConfig, process: Process): ToolRegistry {
        val transport = with(McpToolRegistryProvider) { defaultStdioTransport(process) }
        return McpToolRegistryProvider.fromTransport(
            transport = transport,
            name = config.name
        )
    }

    override suspend fun fromSse(config: McpServerConfig): ToolRegistry {
        val transport = McpToolRegistryProvider.defaultSseTransport(config.url!!)
        return McpToolRegistryProvider.fromTransport(
            transport = transport,
            name = config.name
        )
    }
}
