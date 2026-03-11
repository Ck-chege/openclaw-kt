package com.kclaw.tools.mcp

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class McpToolsProviderTest {

    private object StubMcpToolA : SimpleTool<StubMcpToolA.Args>(
        argsSerializer = Args.serializer(),
        name = "mcp_a",
        description = "tool a"
    ) {
        @Serializable
        data class Args(val value: String = "")

        override suspend fun execute(args: Args): String = "ok"
    }

    private object StubMcpToolB : SimpleTool<StubMcpToolB.Args>(
        argsSerializer = Args.serializer(),
        name = "mcp_b",
        description = "tool b"
    ) {
        @Serializable
        data class Args(val value: String = "")

        override suspend fun execute(args: Args): String = "ok"
    }

    @Nested
    inner class DiscoverTools {

        @Test
        fun `returns empty list when no servers configured`() = runBlocking {
            val provider = McpToolsProvider(emptyList(), FakeRegistryFactory(), FakeProcessLauncher())
            provider.discoverTools().shouldBeEmpty()
        }

        @Test
        fun `returns empty list for disabled servers`() = runBlocking {
            val provider = McpToolsProvider(
                listOf(McpServerConfig(name = "test", command = "echo", enabled = false)),
                FakeRegistryFactory(),
                FakeProcessLauncher()
            )
            provider.discoverTools().shouldBeEmpty()
        }

        @Test
        fun `discovers tools from enabled stdio and sse servers`() = runBlocking {
            val factory = FakeRegistryFactory(
                stdioRegistry = ToolRegistry { tools(listOf(StubMcpToolA)) },
                sseRegistry = ToolRegistry { tools(listOf(StubMcpToolB)) }
            )
            val launcher = FakeProcessLauncher()
            val provider = McpToolsProvider(
                listOf(
                    McpServerConfig(name = "test-stdio", transport = McpTransport.STDIO, command = "echo"),
                    McpServerConfig(name = "test-sse", transport = McpTransport.SSE, url = "http://localhost:3000/sse")
                ),
                factory,
                launcher
            )

            val tools = provider.discoverTools()

            tools.map { it.name }.shouldContainExactly("mcp_a", "mcp_b")
            launcher.started shouldHaveSize 1
            factory.stdioCalls shouldHaveSize 1
            factory.sseCalls shouldHaveSize 1
        }

        @Test
        fun `deduplicates tools by name across servers`() = runBlocking {
            val registry = ToolRegistry { tools(listOf(StubMcpToolA)) }
            val provider = McpToolsProvider(
                listOf(
                    McpServerConfig(name = "stdio-1", transport = McpTransport.STDIO, command = "echo"),
                    McpServerConfig(name = "stdio-2", transport = McpTransport.STDIO, command = "echo")
                ),
                FakeRegistryFactory(stdioRegistry = registry),
                FakeProcessLauncher()
            )

            val tools = provider.discoverTools()

            tools.map { it.name }.shouldContainExactly("mcp_a")
        }

        @Test
        fun `continues when one server fails`() = runBlocking {
            val factory = FakeRegistryFactory(
                stdioFailureNames = setOf("broken"),
                stdioRegistry = ToolRegistry { tools(listOf(StubMcpToolA)) },
                sseRegistry = ToolRegistry { tools(listOf(StubMcpToolB)) }
            )
            val provider = McpToolsProvider(
                listOf(
                    McpServerConfig(name = "broken", transport = McpTransport.STDIO, command = "echo"),
                    McpServerConfig(name = "healthy", transport = McpTransport.SSE, url = "http://localhost:3000/sse")
                ),
                factory,
                FakeProcessLauncher()
            )

            val tools = provider.discoverTools()

            tools.map { it.name }.shouldContainExactly("mcp_b")
        }

        @Test
        fun `disconnect destroys launched stdio processes`() = runBlocking {
            val launcher = FakeProcessLauncher()
            val provider = McpToolsProvider(
                listOf(McpServerConfig(name = "test-stdio", transport = McpTransport.STDIO, command = "echo")),
                FakeRegistryFactory(stdioRegistry = ToolRegistry { tools(listOf(StubMcpToolA)) }),
                launcher
            )

            provider.discoverTools()
            val process = launcher.started.single()
            process.destroyed shouldBe false

            provider.disconnect()

            process.destroyed shouldBe true
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

private class FakeRegistryFactory(
    private val stdioRegistry: ToolRegistry = ToolRegistry.EMPTY,
    private val sseRegistry: ToolRegistry = ToolRegistry.EMPTY,
    private val stdioFailureNames: Set<String> = emptySet(),
    private val sseFailureNames: Set<String> = emptySet()
) : McpRegistryFactory {
    val stdioCalls = mutableListOf<String>()
    val sseCalls = mutableListOf<String>()

    override suspend fun fromStdio(config: McpServerConfig, process: Process): ToolRegistry {
        stdioCalls += config.name
        if (config.name in stdioFailureNames) error("boom")
        return stdioRegistry
    }

    override suspend fun fromSse(config: McpServerConfig): ToolRegistry {
        sseCalls += config.name
        if (config.name in sseFailureNames) error("boom")
        return sseRegistry
    }
}

private class FakeProcessLauncher : McpProcessLauncher {
    val started = mutableListOf<FakeProcess>()

    override fun start(config: McpServerConfig): Process {
        return FakeProcess().also { started += it }
    }
}

private class FakeProcess : Process() {
    var destroyed: Boolean = false

    override fun destroy() {
        destroyed = true
    }

    override fun destroyForcibly(): Process {
        destroyed = true
        return this
    }

    override fun isAlive(): Boolean = !destroyed

    override fun exitValue(): Int = 0

    override fun waitFor(): Int = 0

    override fun waitFor(timeout: Long, unit: java.util.concurrent.TimeUnit): Boolean = true

    override fun getInputStream(): java.io.InputStream = java.io.ByteArrayInputStream(ByteArray(0))

    override fun getErrorStream(): java.io.InputStream = java.io.ByteArrayInputStream(ByteArray(0))

    override fun getOutputStream(): java.io.OutputStream = java.io.ByteArrayOutputStream()
}
