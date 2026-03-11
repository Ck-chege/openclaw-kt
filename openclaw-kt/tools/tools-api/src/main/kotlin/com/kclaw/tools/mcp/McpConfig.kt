package com.kclaw.tools.mcp

import kotlinx.serialization.Serializable

/**
 * Transport mode for connecting to an MCP server.
 */
@Serializable
enum class McpTransport { STDIO, SSE }

/**
 * Configuration for a single MCP server connection.
 *
 * For STDIO transport: [command] and [args] are used to start the process.
 * For SSE transport: [url] is the HTTP endpoint.
 */
@Serializable
data class McpServerConfig(
    val name: String,
    val transport: McpTransport = McpTransport.STDIO,
    val command: String? = null,
    val args: List<String> = emptyList(),
    val url: String? = null,
    val env: Map<String, String> = emptyMap(),
    val enabled: Boolean = true
) {
    init {
        when (transport) {
            McpTransport.STDIO -> require(!command.isNullOrBlank()) {
                "MCP server '$name': STDIO transport requires 'command'"
            }
            McpTransport.SSE -> require(!url.isNullOrBlank()) {
                "MCP server '$name': SSE transport requires 'url'"
            }
        }
    }
}
