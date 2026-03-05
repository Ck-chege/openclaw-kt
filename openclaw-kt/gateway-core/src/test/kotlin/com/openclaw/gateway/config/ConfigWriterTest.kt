package com.openclaw.gateway.config

import com.typesafe.config.ConfigFactory
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readText

class ConfigWriterTest {

    @TempDir
    lateinit var tempDir: Path

    private fun writer(): ConfigWriter {
        val configPath = tempDir.resolve("openclaw.conf")
        return ConfigWriter(configPath)
    }

    private fun configPath(): Path = tempDir.resolve("openclaw.conf")

    @Nested
    inner class WriteDefaults {

        @Test
        fun `writing all-defaults produces empty config`() {
            writer().write(GatewayConfig())
            val content = configPath().readText()
            // All defaults — no sections written
            content.isBlank() shouldBe true
        }
    }

    @Nested
    inner class WriteNonDefaults {

        @Test
        fun `writes gateway port when non-default`() {
            val config = GatewayConfig(
                gateway = GatewaySettings(port = 9999)
            )
            writer().write(config)
            val content = configPath().readText()
            content shouldContain "port = 9999"
        }

        @Test
        fun `writes auth token`() {
            val config = GatewayConfig(
                gateway = GatewaySettings(auth = AuthConfig(token = "my-secret"))
            )
            writer().write(config)
            val content = configPath().readText()
            content shouldContain "token = \"my-secret\""
        }

        @Test
        fun `writes secrets section`() {
            val config = GatewayConfig(
                secrets = SecretsConfig(anthropicApiKey = "sk-ant-123")
            )
            writer().write(config)
            val content = configPath().readText()
            content shouldContain "secrets {"
            content shouldContain "anthropicApiKey = \"sk-ant-123\""
        }

        @Test
        fun `writes both API keys`() {
            val config = GatewayConfig(
                secrets = SecretsConfig(anthropicApiKey = "sk-ant-123", openaiApiKey = "sk-456")
            )
            writer().write(config)
            val content = configPath().readText()
            content shouldContain "anthropicApiKey = \"sk-ant-123\""
            content shouldContain "openaiApiKey = \"sk-456\""
        }

        @Test
        fun `writes model override`() {
            val config = GatewayConfig(
                agents = AgentsConfig(defaults = AgentDefaults(model = "openai:gpt-4o"))
            )
            writer().write(config)
            val content = configPath().readText()
            content shouldContain "model = \"openai:gpt-4o\""
        }

        @Test
        fun `omits null secrets`() {
            val config = GatewayConfig(
                secrets = SecretsConfig(anthropicApiKey = "sk-ant-123", openaiApiKey = null)
            )
            writer().write(config)
            val content = configPath().readText()
            content shouldContain "anthropicApiKey"
            content shouldNotContain "openaiApiKey"
        }
    }

    @Nested
    inner class RoundTrip {

        @Test
        fun `written config can be parsed back by typesafe config`() {
            val config = GatewayConfig(
                gateway = GatewaySettings(
                    port = 8080,
                    auth = AuthConfig(token = "test-token")
                ),
                secrets = SecretsConfig(anthropicApiKey = "sk-ant-test"),
                agents = AgentsConfig(defaults = AgentDefaults(model = "openai:gpt-4o"))
            )
            writer().write(config)

            val parsed = ConfigFactory.parseFile(configPath().toFile())
            val roundTripped = parsed.toGatewayConfig()

            roundTripped.gateway.port shouldBe 8080
            roundTripped.gateway.auth.token shouldBe "test-token"
            roundTripped.secrets.anthropicApiKey shouldBe "sk-ant-test"
            roundTripped.agents.defaults.model shouldBe "openai:gpt-4o"
        }

        @Test
        fun `overwriting existing file works`() {
            val w = writer()

            // First write
            w.write(GatewayConfig(secrets = SecretsConfig(anthropicApiKey = "key1")))
            val content1 = configPath().readText()
            content1 shouldContain "key1"

            // Overwrite
            w.write(GatewayConfig(secrets = SecretsConfig(anthropicApiKey = "key2")))
            val content2 = configPath().readText()
            content2 shouldContain "key2"
            content2 shouldNotContain "key1"
        }
    }

    @Nested
    inner class Update {

        @Test
        fun `update applies mutator and writes`() {
            val w = writer()
            val initial = GatewayConfig(
                secrets = SecretsConfig(anthropicApiKey = "old-key")
            )
            w.write(initial)

            w.update(initial) { it.copy(secrets = it.secrets.copy(openaiApiKey = "new-openai")) }

            val parsed = ConfigFactory.parseFile(configPath().toFile()).toGatewayConfig()
            parsed.secrets.anthropicApiKey shouldBe "old-key"
            parsed.secrets.openaiApiKey shouldBe "new-openai"
        }
    }

    @Nested
    inner class AgentsListAndTools {

        @Test
        fun `writes and parses agents list round-trip`() {
            val config = GatewayConfig(
                agents = AgentsConfig(
                    defaults = AgentDefaults(model = "openai:gpt-4o"),
                    list = listOf(
                        AgentEntry(id = "agent-1", model = "anthropic:claude-sonnet-4"),
                        AgentEntry(id = "agent-2", workspace = "/tmp/workspace")
                    )
                )
            )
            writer().write(config)
            val content = configPath().readText()
            content shouldContain "list = ["
            content shouldContain "id = \"agent-1\""
            content shouldContain "id = \"agent-2\""

            val parsed = ConfigFactory.parseFile(configPath().toFile()).toGatewayConfig()
            parsed.agents.list.size shouldBe 2
            parsed.agents.list[0].id shouldBe "agent-1"
            parsed.agents.list[0].model shouldBe "anthropic:claude-sonnet-4"
            parsed.agents.list[1].id shouldBe "agent-2"
            parsed.agents.list[1].workspace shouldBe "/tmp/workspace"
        }

        @Test
        fun `writes tools section with sandbox and allow-deny`() {
            val config = GatewayConfig(
                tools = ToolsConfig(
                    profile = "minimal",
                    allow = listOf("bash", "read"),
                    deny = listOf("write"),
                    sandbox = SandboxToolConfig(enabled = true, allow = listOf("/tmp"))
                )
            )
            writer().write(config)
            val content = configPath().readText()
            content shouldContain "tools {"
            content shouldContain "profile = \"minimal\""
            content shouldContain "allow = [\"bash\", \"read\"]"
            content shouldContain "deny = [\"write\"]"
            content shouldContain "sandbox {"
            content shouldContain "enabled = true"

            val parsed = ConfigFactory.parseFile(configPath().toFile()).toGatewayConfig()
            parsed.tools.profile shouldBe "minimal"
            parsed.tools.allow shouldBe listOf("bash", "read")
            parsed.tools.deny shouldBe listOf("write")
            parsed.tools.sandbox.enabled shouldBe true
            parsed.tools.sandbox.allow shouldBe listOf("/tmp")
        }
    }

    @Nested
    inner class Escaping {

        @Test
        fun `newlines in values are escaped and round-trip correctly`() {
            val config = GatewayConfig(
                gateway = GatewaySettings(auth = AuthConfig(token = "line1\nline2\rline3"))
            )
            writer().write(config)
            val content = configPath().readText()
            content shouldContain "\\n"
            content shouldContain "\\r"

            val parsed = ConfigFactory.parseFile(configPath().toFile()).toGatewayConfig()
            parsed.gateway.auth.token shouldBe "line1\nline2\rline3"
        }

        @Test
        fun `quotes in values are escaped`() {
            val config = GatewayConfig(
                gateway = GatewaySettings(auth = AuthConfig(token = "has\"quote"))
            )
            writer().write(config)
            val content = configPath().readText()
            content shouldContain "has\\\"quote"

            // Should still parse
            val parsed = ConfigFactory.parseFile(configPath().toFile()).toGatewayConfig()
            parsed.gateway.auth.token shouldBe "has\"quote"
        }

        @Test
        fun `backslashes in values are escaped`() {
            val config = GatewayConfig(
                gateway = GatewaySettings(auth = AuthConfig(token = "has\\backslash"))
            )
            writer().write(config)

            val parsed = ConfigFactory.parseFile(configPath().toFile()).toGatewayConfig()
            parsed.gateway.auth.token shouldBe "has\\backslash"
        }
    }
}
