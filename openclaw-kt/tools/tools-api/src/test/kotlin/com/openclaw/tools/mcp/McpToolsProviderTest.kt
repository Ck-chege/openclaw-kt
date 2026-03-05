package com.openclaw.tools.mcp

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class McpToolsProviderTest {

    @Nested
    inner class DiscoverTools {

        @Test
        fun `returns empty list when no servers configured`() {
            val provider = McpToolsProvider(emptyList())
            provider.discoverTools().shouldBeEmpty()
        }

        @Test
        fun `returns empty list for disabled servers`() {
            val provider = McpToolsProvider(listOf(
                McpServerConfig(name = "test", command = "echo", enabled = false)
            ))
            provider.discoverTools().shouldBeEmpty()
        }

        @Test
        fun `returns empty list for enabled servers (deferred implementation)`() {
            val provider = McpToolsProvider(listOf(
                McpServerConfig(name = "test-stdio", transport = McpTransport.STDIO, command = "echo"),
                McpServerConfig(name = "test-sse", transport = McpTransport.SSE, url = "http://localhost:3000/sse")
            ))
            // Currently returns empty as MCP client is not yet implemented
            provider.discoverTools().shouldBeEmpty()
        }
    }

    @Nested
    inner class McpServerConfigValidation {

        @Test
        fun `stdio transport requires command`() {
            try {
                McpServerConfig(name = "bad", transport = McpTransport.STDIO, command = null)
                throw AssertionError("Expected IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                assert(e.message!!.contains("command"))
            }
        }

        @Test
        fun `sse transport requires url`() {
            try {
                McpServerConfig(name = "bad", transport = McpTransport.SSE, url = null)
                throw AssertionError("Expected IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                assert(e.message!!.contains("url"))
            }
        }

        @Test
        fun `valid stdio config creates successfully`() {
            val config = McpServerConfig(
                name = "my-server",
                transport = McpTransport.STDIO,
                command = "/usr/local/bin/mcp-server",
                args = listOf("--port", "3000"),
                env = mapOf("API_KEY" to "test")
            )
            assert(config.name == "my-server")
            assert(config.args.size == 2)
        }

        @Test
        fun `valid sse config creates successfully`() {
            val config = McpServerConfig(
                name = "remote-mcp",
                transport = McpTransport.SSE,
                url = "http://localhost:3000/sse"
            )
            assert(config.name == "remote-mcp")
        }
    }
}
