package com.kclaw.gateway.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class JsonConfigParserTest {

    @Test
    fun `empty JSON object returns all defaults`() {
        val result = configJson.decodeFromString<GatewayConfig>("{}")
        result shouldBe GatewayConfig()
    }

    @Test
    fun `full config parses every field correctly`() {
        val json = """
            {
                "gateway": {
                    "port": 9999,
                    "bind": "0.0.0.0",
                    "auth": {
                        "token": "my-secret",
                        "password": "pass123"
                    },
                    "reload": {
                        "mode": "HOT"
                    }
                },
                "agents": {
                    "defaults": {
                        "model": "openai:gpt-4o",
                        "workspace": "/tmp/workspace",
                        "compaction": "aggressive",
                        "maxConcurrentRuns": 8
                    },
                    "list": [
                        { "id": "coder", "model": "anthropic:claude-opus-4", "workspace": "/projects" },
                        { "id": "reviewer" }
                    ]
                },
                "tools": {
                    "profile": "minimal",
                    "allow": ["exec", "fs"],
                    "deny": ["browser"]
                },
                "memory": {
                    "enabled": false,
                    "provider": "local",
                    "model": "nomic-embed"
                },
                "cron": {
                    "enabled": false
                },
                "persistence": {
                    "enabled": true,
                    "sqlite": {
                        "path": "/tmp/checkpoints.sqlite"
                    }
                }
            }
        """.trimIndent()

        val result = configJson.decodeFromString<GatewayConfig>(json)

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
        result.persistence.enabled shouldBe true
        result.persistence.sqlite.path shouldBe "/tmp/checkpoints.sqlite"
    }

    @Test
    fun `partial config uses defaults for missing sections`() {
        val json = """
            {
                "gateway": {
                    "port": 3000
                }
            }
        """.trimIndent()

        val result = configJson.decodeFromString<GatewayConfig>(json)

        result.gateway.port shouldBe 3000
        result.gateway.bind shouldBe "127.0.0.1" // default
        result.gateway.auth shouldBe AuthConfig() // default
        result.agents shouldBe AgentsConfig() // default
        result.tools shouldBe ToolsConfig() // default
        result.memory shouldBe MemoryConfig() // default
        result.cron shouldBe CronConfig() // default
    }

    @Test
    fun `unknown keys are ignored`() {
        val json = """
            {
                "gateway": {
                    "port": 3000,
                    "unknownField": "whatever"
                },
                "futureSection": { "a": 1 }
            }
        """.trimIndent()

        val result = configJson.decodeFromString<GatewayConfig>(json)
        result.gateway.port shouldBe 3000
    }

    @Test
    fun `comments are allowed in config`() {
        val json = """
            {
                // This is a comment
                "gateway": {
                    "port": 4000
                }
            }
        """.trimIndent()

        val result = configJson.decodeFromString<GatewayConfig>(json)
        result.gateway.port shouldBe 4000
    }

    @Test
    fun `trailing commas are allowed`() {
        val json = """
            {
                "gateway": {
                    "port": 5000,
                },
            }
        """.trimIndent()

        val result = configJson.decodeFromString<GatewayConfig>(json)
        result.gateway.port shouldBe 5000
    }

    @Nested
    inner class AgentEntryMapping {

        @Test
        fun `empty agents list parses to empty list`() {
            val json = """
                {
                    "agents": {
                        "list": []
                    }
                }
            """.trimIndent()

            val result = configJson.decodeFromString<GatewayConfig>(json)
            result.agents.list shouldBe emptyList()
        }

        @Test
        fun `agent entry with only id uses null for optional fields`() {
            val json = """
                {
                    "agents": {
                        "list": [
                            { "id": "solo" }
                        ]
                    }
                }
            """.trimIndent()

            val result = configJson.decodeFromString<GatewayConfig>(json)
            result.agents.list.size shouldBe 1
            result.agents.list[0].id shouldBe "solo"
            result.agents.list[0].model shouldBe null
            result.agents.list[0].workspace shouldBe null
        }
    }

    @Nested
    inner class ReloadModeMapping {

        @Test
        fun `all four ReloadMode values parse correctly`() {
            for (mode in ReloadMode.entries) {
                val json = """
                    {
                        "gateway": {
                            "reload": {
                                "mode": "${mode.name}"
                            }
                        }
                    }
                """.trimIndent()

                val result = configJson.decodeFromString<GatewayConfig>(json)
                result.gateway.reload.mode shouldBe mode
            }
        }
    }

    @Nested
    inner class NestedPartialDefaults {

        @Test
        fun `only auth block inside gateway uses defaults for rest`() {
            val json = """
                {
                    "gateway": {
                        "auth": {
                            "token": "partial-token"
                        }
                    }
                }
            """.trimIndent()

            val result = configJson.decodeFromString<GatewayConfig>(json)
            result.gateway.auth.token shouldBe "partial-token"
            result.gateway.port shouldBe 18789
            result.gateway.bind shouldBe "127.0.0.1"
            result.gateway.reload shouldBe ReloadConfig()
        }

        @Test
        fun `only tools section with allow list uses defaults for rest`() {
            val json = """
                {
                    "tools": {
                        "allow": ["exec"]
                    }
                }
            """.trimIndent()

            val result = configJson.decodeFromString<GatewayConfig>(json)
            result.tools.allow shouldBe listOf("exec")
            result.tools.deny shouldBe emptyList()
            result.tools.profile shouldBe "full"
            result.gateway shouldBe GatewaySettings()
        }

        @Test
        fun `only agent defaults without list uses empty list`() {
            val json = """
                {
                    "agents": {
                        "defaults": {
                            "model": "custom:model"
                        }
                    }
                }
            """.trimIndent()

            val result = configJson.decodeFromString<GatewayConfig>(json)
            result.agents.defaults.model shouldBe "custom:model"
            result.agents.defaults.workspace shouldBe "~/.kclaw/workspace"
            result.agents.list shouldBe emptyList()
        }
    }

    @Nested
    inner class SkillsMapping {

        @Test
        fun `skills config parses nested entries and truthy paths`() {
            val json = """
                {
                    "skills": {
                        "allowBundled": ["search"],
                        "load": {
                            "extraDirs": ["/tmp/skills-extra"],
                            "maxSkillFileBytes": 4096
                        },
                        "install": {
                            "preferBrew": false,
                            "nodeManager": "pnpm"
                        },
                        "entries": {
                            "search": {
                                "enabled": true,
                                "apiKey": "search-key",
                                "env": {
                                    "SEARCH_TOKEN": "token-1"
                                },
                                "config": {
                                    "region": "us-east-1"
                                }
                            }
                        }
                    },
                    "providers": {
                        "azure": {
                            "endpoint": "https://example.openai.azure.com"
                        }
                    }
                }
            """.trimIndent()

            val result = configJson.decodeFromString<GatewayConfig>(json)

            result.skills.allowBundled shouldBe listOf("search")
            result.skills.load.extraDirs shouldBe listOf("/tmp/skills-extra")
            result.skills.load.maxSkillFileBytes shouldBe 4096
            result.skills.install.preferBrew shouldBe false
            result.skills.install.nodeManager shouldBe "pnpm"
            result.skills.entries["search"]?.enabled shouldBe true
            result.skills.entries["search"]?.apiKey shouldBe "search-key"
            result.skills.entries["search"]?.env shouldBe mapOf("SEARCH_TOKEN" to "token-1")
            result.skills.entries["search"]?.config shouldBe mapOf("region" to "us-east-1")
            result.isTruthyPath("providers.azure.endpoint") shouldBe true
            result.isTruthyPath("providers.azure.deployment") shouldBe false
        }
    }
}