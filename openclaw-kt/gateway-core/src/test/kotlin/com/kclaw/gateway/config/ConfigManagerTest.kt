package com.kclaw.gateway.config

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.writeText

class ConfigManagerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `missing file returns defaults`() {
        val nonExistent = tempDir.resolve("does-not-exist.json")
        val manager = ConfigManager(nonExistent)

        val config = manager.load()

        config shouldBe GatewayConfig()
    }

    @Test
    fun `valid file parses correctly`() {
        val configFile = tempDir.resolve("kclaw.json").createFile()
        configFile.writeText("""
            {
                "gateway": {
                    "port": 7777,
                    "bind": "0.0.0.0",
                    "auth": {
                        "token": "test-token"
                    }
                },
                "agents": {
                    "defaults": {
                        "model": "openai:gpt-4o"
                    }
                }
            }
        """.trimIndent())

        val manager = ConfigManager(configFile)
        val config = manager.load()

        config.gateway.port shouldBe 7777
        config.gateway.bind shouldBe "0.0.0.0"
        config.gateway.auth.token shouldBe "test-token"
        config.agents.defaults.model shouldBe "openai:gpt-4o"
    }

    @Test
    fun `invalid JSON syntax throws FileParseError`() {
        val configFile = tempDir.resolve("bad.json").createFile()
        configFile.writeText("this is not { valid json >>>")

        val manager = ConfigManager(configFile)

        shouldThrow<ConfigError.FileParseError> {
            manager.load()
        }
    }

    @Test
    fun `reload updates StateFlow with new values`() {
        val configFile = tempDir.resolve("kclaw.json").createFile()
        configFile.writeText("""
            {
                "gateway": { "port": 8000 }
            }
        """.trimIndent())

        val manager = ConfigManager(configFile)
        manager.load()
        manager.config.value.gateway.port shouldBe 8000

        // Modify the config file
        configFile.writeText("""
            {
                "gateway": { "port": 9000 }
            }
        """.trimIndent())

        manager.reload()
        manager.config.value.gateway.port shouldBe 9000
    }

    @Test
    fun `reload on parse error retains previous config`() {
        val configFile = tempDir.resolve("kclaw.json").createFile()
        configFile.writeText("""
            {
                "gateway": { "port": 5555 }
            }
        """.trimIndent())

        val manager = ConfigManager(configFile)
        manager.load()
        manager.config.value.gateway.port shouldBe 5555

        // Break the config file
        configFile.writeText("broken { syntax >>>")

        manager.reload()
        // Previous config should be retained
        manager.config.value.gateway.port shouldBe 5555
    }

    @Nested
    inner class LoadEdgeCases {

        @Test
        fun `load updates StateFlow value`() {
            val configFile = tempDir.resolve("kclaw.json").createFile()
            configFile.writeText("""
                {
                    "gateway": { "port": 4444 }
                }
            """.trimIndent())

            val manager = ConfigManager(configFile)
            // StateFlow starts with defaults
            manager.config.value shouldBe GatewayConfig()

            manager.load()
            // StateFlow now reflects loaded config
            manager.config.value.gateway.port shouldBe 4444
        }

        @Test
        fun `load with invalid JSON throws FileParseError`() {
            val configFile = tempDir.resolve("kclaw.json").createFile()
            configFile.writeText("""
                {
                    "gateway": {
                        "port": "not-a-number"
                    }
                }
            """.trimIndent())

            val manager = ConfigManager(configFile)

            shouldThrow<ConfigError.FileParseError> {
                manager.load()
            }
        }

        @Test
        fun `empty file returns defaults`() {
            val configFile = tempDir.resolve("kclaw.json").createFile()
            configFile.writeText("")

            val manager = ConfigManager(configFile)
            val config = manager.load()

            config shouldBe GatewayConfig()
        }

        @Test
        fun `load return value matches StateFlow value`() {
            val configFile = tempDir.resolve("kclaw.json").createFile()
            configFile.writeText("""
                {
                    "gateway": { "bind": "0.0.0.0" }
                }
            """.trimIndent())

            val manager = ConfigManager(configFile)
            val returned = manager.load()

            returned shouldBe manager.config.value
        }
    }

    @Nested
    inner class ReloadEdgeCases {

        @Test
        fun `reload with invalid JSON retains previous config`() {
            val configFile = tempDir.resolve("kclaw.json").createFile()
            configFile.writeText("""
                {
                    "gateway": { "port": 6666 }
                }
            """.trimIndent())

            val manager = ConfigManager(configFile)
            manager.load()
            manager.config.value.gateway.port shouldBe 6666

            // Write invalid JSON
            configFile.writeText("""{ "gateway": { "port": ??? } }""")

            manager.reload()
            // Previous config retained
            manager.config.value.gateway.port shouldBe 6666
        }

        @Test
        fun `reload with deleted file retains defaults gracefully`() {
            val configFile = tempDir.resolve("kclaw.json").createFile()
            configFile.writeText("""
                {
                    "gateway": { "port": 3333 }
                }
            """.trimIndent())

            val manager = ConfigManager(configFile)
            manager.load()
            manager.config.value.gateway.port shouldBe 3333

            // Delete the file
            configFile.toFile().delete()

            // Reload should succeed with defaults (file gone = defaults)
            manager.reload()
            manager.config.value shouldBe GatewayConfig()
        }

        @Test
        fun `StateFlow collectors are notified on reload`() = runBlocking {
            val configFile = tempDir.resolve("kclaw.json").createFile()
            configFile.writeText("""
                { "gateway": { "port": 1111 } }
            """.trimIndent())

            val manager = ConfigManager(configFile)
            manager.load()

            // Collect the current value via Flow
            val current = manager.config.first()
            current.gateway.port shouldBe 1111

            configFile.writeText("""
                { "gateway": { "port": 2222 } }
            """.trimIndent())

            manager.reload()
            val updated = manager.config.first()
            updated.gateway.port shouldBe 2222
        }
    }

    @Nested
    inner class FileWatcher {

        @Test
        fun `startFileWatcher skips when mode is OFF`() {
            val configFile = tempDir.resolve("kclaw.json").createFile()
            configFile.writeText("""
                {
                    "gateway": {
                        "reload": { "mode": "OFF" }
                    }
                }
            """.trimIndent())

            val manager = ConfigManager(configFile)
            manager.load()

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                // Should not throw, just skip
                shouldNotThrowAny {
                    manager.startFileWatcher(scope)
                }
                // Stopping an unstarted watcher is safe
                manager.stopFileWatcher()
            } finally {
                scope.cancel()
            }
        }

        @Test
        fun `startFileWatcher skips when mode is RESTART`() {
            val configFile = tempDir.resolve("kclaw.json").createFile()
            configFile.writeText("""
                {
                    "gateway": {
                        "reload": { "mode": "RESTART" }
                    }
                }
            """.trimIndent())

            val manager = ConfigManager(configFile)
            manager.load()

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                shouldNotThrowAny {
                    manager.startFileWatcher(scope)
                }
                manager.stopFileWatcher()
            } finally {
                scope.cancel()
            }
        }

        @Test
        fun `stopFileWatcher is idempotent`() {
            val nonExistent = tempDir.resolve("nope.json")
            val manager = ConfigManager(nonExistent)
            manager.load()

            // Call stop multiple times without starting — should not throw
            shouldNotThrowAny {
                manager.stopFileWatcher()
                manager.stopFileWatcher()
                manager.stopFileWatcher()
            }
        }

        @Test
        fun `file watcher detects modification and triggers reload`() = runBlocking {
            val configFile = tempDir.resolve("kclaw.json").createFile()
            configFile.writeText("""
                { "gateway": { "port": 10000 } }
            """.trimIndent())

            val manager = ConfigManager(configFile)
            manager.load()
            manager.config.value.gateway.port shouldBe 10000

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                manager.startFileWatcher(scope)

                // Modify the file
                configFile.writeText("""
                    { "gateway": { "port": 20000 } }
                """.trimIndent())

                // Wait for the watcher to pick up the change (debounce is 300ms)
                withTimeout(5000) {
                    // Poll until the config changes
                    while (manager.config.value.gateway.port != 20000) {
                        kotlinx.coroutines.delay(100)
                    }
                }

                manager.config.value.gateway.port shouldBe 20000
            } finally {
                manager.stopFileWatcher()
                scope.cancel()
            }
        }

        @Test
        fun `file watcher start and stop lifecycle works cleanly`() {
            val configFile = tempDir.resolve("kclaw.json").createFile()
            configFile.writeText("{}")

            val manager = ConfigManager(configFile)
            manager.load()

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                shouldNotThrowAny {
                    manager.startFileWatcher(scope)
                    manager.stopFileWatcher()
                }
            } finally {
                scope.cancel()
            }
        }
    }

    @Nested
    inner class ConfigDiff {

        @Test
        fun `identical configs produce no diff`() {
            val config = GatewayConfig()
            diffConfigPaths(config, config) shouldBe emptyList()
        }

        @Test
        fun `different port shows gateway port path`() {
            val a = GatewayConfig()
            val b = a.copy(gateway = a.gateway.copy(port = 9999))
            val diff = diffConfigPaths(a, b)
            diff shouldBe listOf("gateway.port")
        }

        @Test
        fun `multiple changes reported`() {
            val a = GatewayConfig()
            val b = a.copy(
                gateway = a.gateway.copy(port = 9999, bind = "0.0.0.0"),
                agents = a.agents.copy(defaults = a.agents.defaults.copy(model = "new-model"))
            )
            val diff = diffConfigPaths(a, b)
            diff shouldBe listOf("gateway.port", "gateway.bind", "agents")
        }

        @Test
        fun `reload plan marks restart for port change`() {
            val plan = buildReloadPlan(listOf("gateway.port", "agents"))
            plan.requiresRestart shouldBe true
            plan.restartReasons shouldBe listOf("gateway.port")
        }

        @Test
        fun `reload plan does not require restart for agents-only change`() {
            val plan = buildReloadPlan(listOf("agents"))
            plan.requiresRestart shouldBe false
            plan.restartReasons shouldBe emptyList()
        }
    }

    @Nested
    inner class ReloadWithDiff {

        @Test
        fun `reload skips update when config is unchanged`() {
            val configFile = tempDir.resolve("kclaw.json").createFile()
            configFile.writeText("""
                { "gateway": { "port": 7000 } }
            """.trimIndent())

            val manager = ConfigManager(configFile)
            manager.load()
            manager.config.value.gateway.port shouldBe 7000

            // Reload same file — no change
            manager.reload()
            manager.config.value.gateway.port shouldBe 7000
        }

        @Test
        fun `reload calls onRestartRequired for port change`() {
            val configFile = tempDir.resolve("kclaw.json").createFile()
            configFile.writeText("""
                { "gateway": { "port": 7000 } }
            """.trimIndent())

            val manager = ConfigManager(configFile)
            manager.load()

            configFile.writeText("""
                { "gateway": { "port": 8000 } }
            """.trimIndent())

            var capturedPlan: GatewayReloadPlan? = null
            manager.reload { plan -> capturedPlan = plan }

            capturedPlan shouldNotBe null
            capturedPlan!!.requiresRestart shouldBe true
            capturedPlan!!.restartReasons shouldBe listOf("gateway.port")
        }
    }
}