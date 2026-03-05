package com.openclaw.tools.mcp

import ai.koog.agents.core.tools.Tool
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Discovers and connects to MCP (Model Context Protocol) servers, exposing their
 * tools as Koog [Tool] instances that can be included in the agent's ToolRegistry.
 *
 * Currently a placeholder — actual MCP client implementation (stdio process
 * management, SSE/HTTP client, JSON-RPC framing) is deferred. When Koog adds
 * native MCP support (`McpToolRegistryProvider`), this class will delegate to it.
 */
class McpToolsProvider(
    private val servers: List<McpServerConfig>
) {
    /**
     * Connect to all enabled MCP servers and return their tools.
     *
     * Currently returns an empty list with a log message per configured server.
     * The actual MCP client will be implemented when Koog adds MCP support or
     * as a standalone JSON-RPC/stdio/SSE client.
     */
    fun discoverTools(): List<Tool<*, *>> {
        val enabled = servers.filter { it.enabled }
        if (enabled.isEmpty()) {
            logger.debug { "No MCP servers configured" }
            return emptyList()
        }

        enabled.forEach { config ->
            logger.info { "MCP server '${config.name}' configured (${config.transport}) — connection deferred" }
        }

        // TODO: Implement actual MCP client connections
        // For STDIO: start process via ProcessBuilder, communicate via JSON-RPC over stdin/stdout
        // For SSE: connect via Ktor HTTP client to the SSE endpoint
        // Parse tool listings, wrap each MCP tool as a Koog SimpleTool adapter
        return emptyList()
    }

    /**
     * Disconnect from all active MCP server connections.
     */
    fun disconnect() {
        // No-op until connections are implemented
        logger.debug { "MCP disconnect (no active connections)" }
    }
}
