package com.openclaw.gateway.config

import com.typesafe.config.ConfigFactory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class HoconMapperTest {

    @Test
    fun `empty config returns all defaults`() {
        val config = ConfigFactory.empty()
        val result = config.toGatewayConfig()

        result shouldBe GatewayConfig()
    }

    @Test
    fun `full config parses every field correctly`() {
        val hocon = """
            gateway {
                port = 9999
                bind = "0.0.0.0"
                auth {
                    token = "my-secret"
                    password = "pass123"
                }
                reload {
                    mode = "HOT"
                }
            }
            agents {
                defaults {
                    model = "openai:gpt-4o"
                    workspace = "/tmp/workspace"
                    compaction = "aggressive"
                    maxConcurrentRuns = 8
                }
                list = [
                    { id = "coder", model = "anthropic:claude-opus-4", workspace = "/projects" },
                    { id = "reviewer" }
                ]
            }
            tools {
                profile = "minimal"
                allow = ["exec", "fs"]
                deny = ["browser"]
            }
            memory {
                enabled = false
                provider = "local"
                model = "nomic-embed"
            }
            cron {
                enabled = false
            }
        """.trimIndent()

        val config = ConfigFactory.parseString(hocon)
        val result = config.toGatewayConfig()

        result.gateway.port shouldBe 9999
        result.gateway.bind shouldBe "0.0.0.0"
        result.gateway.auth.token shouldBe "my-secret"
        result.gateway.auth.password shouldBe "pass123"
        result.gateway.reload.mode shouldBe ReloadMode.HOT

        result.agents.defaults.model shouldBe "openai:gpt-4o"
        result.agents.defaults.workspace shouldBe "/tmp/workspace"
        result.agents.defaults.compaction shouldBe "aggressive"
        result.agents.defaults.maxConcurrentRuns shouldBe 8
        result.agents.list.size shouldBe 2
        result.agents.list[0].id shouldBe "coder"
        result.agents.list[0].model shouldBe "anthropic:claude-opus-4"
        result.agents.list[0].workspace shouldBe "/projects"
        result.agents.list[1].id shouldBe "reviewer"
        result.agents.list[1].model shouldBe null

        result.tools.profile shouldBe "minimal"
        result.tools.allow shouldBe listOf("exec", "fs")
        result.tools.deny shouldBe listOf("browser")

        result.memory.enabled shouldBe false
        result.memory.provider shouldBe "local"
        result.memory.model shouldBe "nomic-embed"

        result.cron.enabled shouldBe false
    }

    @Test
    fun `partial config uses defaults for missing sections`() {
        val hocon = """
            gateway {
                port = 3000
            }
        """.trimIndent()

        val config = ConfigFactory.parseString(hocon)
        val result = config.toGatewayConfig()

        result.gateway.port shouldBe 3000
        result.gateway.bind shouldBe "127.0.0.1" // default
        result.gateway.auth shouldBe AuthConfig() // default
        result.agents shouldBe AgentsConfig() // default
        result.tools shouldBe ToolsConfig() // default
        result.memory shouldBe MemoryConfig() // default
        result.cron shouldBe CronConfig() // default
    }

    @Test
    fun `wrong type throws InvalidValue with config path`() {
        val hocon = """
            gateway {
                port = "not-a-number"
            }
        """.trimIndent()

        val config = ConfigFactory.parseString(hocon)
        val error = shouldThrow<ConfigError.InvalidValue> {
            config.toGatewayConfig()
        }
        error.configPath shouldBe "port"
        error.expected shouldBe "valid value"
    }

    @Test
    fun `invalid enum throws InvalidValue listing valid options`() {
        val hocon = """
            gateway {
                reload {
                    mode = "INVALID_MODE"
                }
            }
        """.trimIndent()

        val config = ConfigFactory.parseString(hocon)
        val error = shouldThrow<ConfigError.InvalidValue> {
            config.toGatewayConfig()
        }
        error.configPath shouldBe "reload.mode"
        error.expected shouldContain "HYBRID"
        error.expected shouldContain "HOT"
        error.expected shouldContain "RESTART"
        error.expected shouldContain "OFF"
    }

    @Test
    fun `env var substitution works with resolveWith`() {
        val hocon = """
            gateway {
                auth {
                    token = ${"$"}{GATEWAY_TOKEN}
                }
            }
        """.trimIndent()

        val fallback = ConfigFactory.parseMap(mapOf("GATEWAY_TOKEN" to "env-secret-123"))
        val config = ConfigFactory.parseString(hocon).resolveWith(fallback)
        val result = config.toGatewayConfig()

        result.gateway.auth.token shouldBe "env-secret-123"
    }

    @Test
    fun `optional env var resolves to null when unset`() {
        val hocon = """
            gateway {
                auth {
                    token = ${"$"}{?UNSET_VAR}
                }
            }
        """.trimIndent()

        val config = ConfigFactory.parseString(hocon).resolve()
        val result = config.toGatewayConfig()

        result.gateway.auth.token shouldBe null
    }

    @Nested
    inner class AgentEntryMapping {

        @Test
        fun `agent entry without required id throws InvalidValue`() {
            val hocon = """
                agents {
                    list = [
                        { model = "openai:gpt-4o" }
                    ]
                }
            """.trimIndent()

            val config = ConfigFactory.parseString(hocon)
            val error = shouldThrow<ConfigError.InvalidValue> {
                config.toGatewayConfig()
            }
            error.configPath shouldBe "agents.list[].id"
            error.expected shouldContain "required"
        }

        @Test
        fun `empty agents list parses to empty list`() {
            val hocon = """
                agents {
                    list = []
                }
            """.trimIndent()

            val config = ConfigFactory.parseString(hocon)
            val result = config.toGatewayConfig()

            result.agents.list shouldBe emptyList()
        }

        @Test
        fun `agent entry with only id uses null for optional fields`() {
            val hocon = """
                agents {
                    list = [
                        { id = "solo" }
                    ]
                }
            """.trimIndent()

            val config = ConfigFactory.parseString(hocon)
            val result = config.toGatewayConfig()

            result.agents.list.size shouldBe 1
            result.agents.list[0].id shouldBe "solo"
            result.agents.list[0].model shouldBe null
            result.agents.list[0].workspace shouldBe null
        }
    }

    @Nested
    inner class ReloadModeMapping {

        @Test
        fun `case-insensitive enum parsing accepts lowercase`() {
            val hocon = """
                gateway {
                    reload {
                        mode = "hot"
                    }
                }
            """.trimIndent()

            val config = ConfigFactory.parseString(hocon)
            val result = config.toGatewayConfig()

            result.gateway.reload.mode shouldBe ReloadMode.HOT
        }

        @Test
        fun `case-insensitive enum parsing accepts mixed case`() {
            val hocon = """
                gateway {
                    reload {
                        mode = "Hybrid"
                    }
                }
            """.trimIndent()

            val config = ConfigFactory.parseString(hocon)
            val result = config.toGatewayConfig()

            result.gateway.reload.mode shouldBe ReloadMode.HYBRID
        }

        @Test
        fun `all four ReloadMode values parse correctly`() {
            for (mode in ReloadMode.entries) {
                val hocon = """
                    gateway {
                        reload {
                            mode = "${mode.name}"
                        }
                    }
                """.trimIndent()

                val config = ConfigFactory.parseString(hocon)
                val result = config.toGatewayConfig()

                result.gateway.reload.mode shouldBe mode
            }
        }
    }

    @Nested
    inner class TypeErrors {

        @Test
        fun `boolean field with wrong type throws InvalidValue`() {
            // Note: HOCON treats "yes"/"no"/"on"/"off" as valid booleans,
            // so we use an unambiguously non-boolean string
            val hocon = """
                memory {
                    enabled = "not-a-boolean"
                }
            """.trimIndent()

            val config = ConfigFactory.parseString(hocon)
            val error = shouldThrow<ConfigError.InvalidValue> {
                config.toGatewayConfig()
            }
            error.configPath shouldBe "enabled"
        }

        @Test
        fun `integer field with boolean throws InvalidValue`() {
            val hocon = """
                agents {
                    defaults {
                        maxConcurrentRuns = true
                    }
                }
            """.trimIndent()

            val config = ConfigFactory.parseString(hocon)
            val error = shouldThrow<ConfigError.InvalidValue> {
                config.toGatewayConfig()
            }
            error.configPath shouldBe "maxConcurrentRuns"
        }
    }

    @Nested
    inner class NestedPartialDefaults {

        @Test
        fun `only auth block inside gateway uses defaults for rest`() {
            val hocon = """
                gateway {
                    auth {
                        token = "partial-token"
                    }
                }
            """.trimIndent()

            val config = ConfigFactory.parseString(hocon)
            val result = config.toGatewayConfig()

            result.gateway.auth.token shouldBe "partial-token"
            result.gateway.port shouldBe 18789
            result.gateway.bind shouldBe "127.0.0.1"
            result.gateway.reload shouldBe ReloadConfig()
        }

        @Test
        fun `only tools section with allow list uses defaults for rest`() {
            val hocon = """
                tools {
                    allow = ["exec"]
                }
            """.trimIndent()

            val config = ConfigFactory.parseString(hocon)
            val result = config.toGatewayConfig()

            result.tools.allow shouldBe listOf("exec")
            result.tools.deny shouldBe emptyList()
            result.tools.profile shouldBe "full"
            result.gateway shouldBe GatewaySettings()
        }

        @Test
        fun `only agent defaults without list uses empty list`() {
            val hocon = """
                agents {
                    defaults {
                        model = "custom:model"
                    }
                }
            """.trimIndent()

            val config = ConfigFactory.parseString(hocon)
            val result = config.toGatewayConfig()

            result.agents.defaults.model shouldBe "custom:model"
            result.agents.defaults.workspace shouldBe "~/.openclawk/workspace"
            result.agents.list shouldBe emptyList()
        }
    }

    @Nested
    inner class EnvVarSubstitution {

        @Test
        fun `multiple env vars in one config resolve correctly`() {
            val hocon = """
                gateway {
                    auth {
                        token = ${"$"}{AUTH_TOKEN}
                        password = ${"$"}{AUTH_PASSWORD}
                    }
                }
            """.trimIndent()

            val fallback = ConfigFactory.parseMap(mapOf(
                "AUTH_TOKEN" to "tok-123",
                "AUTH_PASSWORD" to "pw-456"
            ))
            val config = ConfigFactory.parseString(hocon).resolveWith(fallback)
            val result = config.toGatewayConfig()

            result.gateway.auth.token shouldBe "tok-123"
            result.gateway.auth.password shouldBe "pw-456"
        }
    }
}
