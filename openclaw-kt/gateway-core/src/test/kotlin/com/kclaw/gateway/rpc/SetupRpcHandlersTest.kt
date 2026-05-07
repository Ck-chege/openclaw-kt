package com.kclaw.gateway.rpc

import com.kclaw.gateway.config.*
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

class SetupRpcHandlersTest {

    @TempDir
    lateinit var tempDir: Path

    private fun createConfigManager(config: GatewayConfig = GatewayConfig()): ConfigManager {
        val configPath = tempDir.resolve("kclaw.json")
        // Write a parseable config from the given config object
        ConfigWriter(configPath).write(config)
        val manager = ConfigManager(configPath)
        manager.load()
        return manager
    }

    private fun setupRouter(
        configManager: ConfigManager,
        phase: String = "BOOTSTRAP_SEEDED",
        apiKeyChecker: (String) -> Boolean = { false }
    ): RpcRouter {
        val router = RpcRouter()
        val configWriter = ConfigWriter(tempDir.resolve("kclaw.json"))
        registerSetupHandlers(
            router = router,
            configManager = configManager,
            configWriter = configWriter,
            setupStatusProvider = { phase },
            apiKeyChecker = apiKeyChecker
        )
        return router
    }

    @Nested
    inner class SetupStatus {

        @Test
        fun `fresh install returns setupRequired true`() = runTest {
            val cm = createConfigManager()
            val router = setupRouter(cm, phase = "BOOTSTRAP_SEEDED")

            val result = router.route("setup.status", JsonObject(emptyMap()), "test-session")
            result["setupRequired"]!!.jsonPrimitive.boolean shouldBe true
            result["onboardingPhase"]!!.jsonPrimitive.content shouldBe "BOOTSTRAP_SEEDED"
        }

        @Test
        fun `configured install returns setupRequired false even while bootstrap is pending`() = runTest {
            val config = GatewayConfig(secrets = SecretsConfig(anthropicApiKey = "sk-ant-123"))
            val cm = createConfigManager(config)
            val router = setupRouter(cm, phase = "BOOTSTRAP_SEEDED")

            val result = router.route("setup.status", JsonObject(emptyMap()), "test-session")
            result["setupRequired"]!!.jsonPrimitive.boolean shouldBe false
            result["onboardingPhase"]!!.jsonPrimitive.content shouldBe "BOOTSTRAP_SEEDED"
        }

        @Test
        fun `reports hasAnthropicKey from config`() = runTest {
            val config = GatewayConfig(secrets = SecretsConfig(anthropicApiKey = "sk-ant-123"))
            val cm = createConfigManager(config)
            val router = setupRouter(cm)

            val result = router.route("setup.status", JsonObject(emptyMap()), "test-session")
            result["hasAnthropicKey"]!!.jsonPrimitive.boolean shouldBe true
        }

        @Test
        fun `reports hasAnthropicKey from env var`() = runTest {
            val cm = createConfigManager()
            val router = setupRouter(cm, apiKeyChecker = { it == "ANTHROPIC_API_KEY" })

            val result = router.route("setup.status", JsonObject(emptyMap()), "test-session")
            result["hasAnthropicKey"]!!.jsonPrimitive.boolean shouldBe true
            result["hasOpenaiKey"]!!.jsonPrimitive.boolean shouldBe false
        }

        @Test
        fun `reports hasAuthToken when token configured`() = runTest {
            val config = GatewayConfig(
                gateway = GatewaySettings(auth = AuthConfig(token = "my-token"))
            )
            val cm = createConfigManager(config)
            val router = setupRouter(cm)

            val result = router.route("setup.status", JsonObject(emptyMap()), "test-session")
            result["hasAuthToken"]!!.jsonPrimitive.boolean shouldBe true
        }

        @Test
        fun `reports no auth token when not configured`() = runTest {
            val cm = createConfigManager()
            val router = setupRouter(cm)

            val result = router.route("setup.status", JsonObject(emptyMap()), "test-session")
            result["hasAuthToken"]!!.jsonPrimitive.boolean shouldBe false
        }

        @Test
        fun `reports default model`() = runTest {
            val cm = createConfigManager()
            val router = setupRouter(cm)

            val result = router.route("setup.status", JsonObject(emptyMap()), "test-session")
            result["defaultModel"]!!.jsonPrimitive.content shouldBe "anthropic:claude-sonnet-4"
        }
    }

    @Nested
    inner class SetupConfigure {

        @Test
        fun `configure with token updates config`() = runTest {
            val cm = createConfigManager()
            val router = setupRouter(cm)

            val params = JsonObject(mapOf("token" to JsonPrimitive("new-token")))
            val result = router.route("setup.configure", params, "test-session")

            result["configured"]!!.jsonPrimitive.boolean shouldBe true
            result["tokenSet"]!!.jsonPrimitive.boolean shouldBe true
            cm.config.value.gateway.auth.token shouldBe "new-token"
        }

        @Test
        fun `configure with anthropic key updates secrets`() = runTest {
            val cm = createConfigManager()
            val router = setupRouter(cm)

            val params = JsonObject(mapOf("anthropicKey" to JsonPrimitive("sk-ant-new")))
            val result = router.route("setup.configure", params, "test-session")

            result["configured"]!!.jsonPrimitive.boolean shouldBe true
            result["anthropicKeySet"]!!.jsonPrimitive.boolean shouldBe true
            cm.config.value.secrets.anthropicApiKey shouldBe "sk-ant-new"
        }

        @Test
        fun `configure with model updates default model`() = runTest {
            val cm = createConfigManager()
            val router = setupRouter(cm)

            val params = JsonObject(mapOf("model" to JsonPrimitive("openai:gpt-4o")))
            val result = router.route("setup.configure", params, "test-session")

            result["modelSet"]!!.jsonPrimitive.content shouldBe "openai:gpt-4o"
            cm.config.value.agents.defaults.model shouldBe "openai:gpt-4o"
        }

        @Test
        fun `configure with multiple values sets all`() = runTest {
            val cm = createConfigManager()
            val router = setupRouter(cm)

            val params = JsonObject(mapOf(
                "token" to JsonPrimitive("my-token"),
                "anthropicKey" to JsonPrimitive("sk-ant-123"),
                "openaiKey" to JsonPrimitive("sk-456"),
                "model" to JsonPrimitive("openai:gpt-4o")
            ))
            router.route("setup.configure", params, "test-session")

            val config = cm.config.value
            config.gateway.auth.token shouldBe "my-token"
            config.secrets.anthropicApiKey shouldBe "sk-ant-123"
            config.secrets.openaiApiKey shouldBe "sk-456"
            config.agents.defaults.model shouldBe "openai:gpt-4o"
        }

        @Test
        fun `configure with no params throws`() = runTest {
            val cm = createConfigManager()
            val router = setupRouter(cm)

            val result = try {
                router.route("setup.configure", JsonObject(emptyMap()), "test-session")
                null
            } catch (e: IllegalArgumentException) {
                e
            }
            result shouldBe IllegalArgumentException("At least one of: token, anthropicKey, openaiKey, model must be provided")
        }

        @Test
        fun `configure preserves existing values when adding new ones`() = runTest {
            val initial = GatewayConfig(secrets = SecretsConfig(anthropicApiKey = "existing"))
            val cm = createConfigManager(initial)
            val router = setupRouter(cm)

            val params = JsonObject(mapOf("openaiKey" to JsonPrimitive("new-openai")))
            router.route("setup.configure", params, "test-session")

            val config = cm.config.value
            config.secrets.anthropicApiKey shouldBe "existing"
            config.secrets.openaiApiKey shouldBe "new-openai"
        }
    }

    @Nested
    inner class SetupComplete {

        @Test
        fun `complete succeeds when anthropic key configured`() = runTest {
            val config = GatewayConfig(secrets = SecretsConfig(anthropicApiKey = "sk-ant-123"))
            val cm = createConfigManager(config)
            val router = setupRouter(cm)

            val result = router.route("setup.complete", JsonObject(emptyMap()), "test-session")
            result["completed"]!!.jsonPrimitive.boolean shouldBe true
            result["onboardingPhase"]!!.jsonPrimitive.content shouldBe "BOOTSTRAP_SEEDED"
        }

        @Test
        fun `complete succeeds when openai key in env`() = runTest {
            val cm = createConfigManager()
            val router = setupRouter(
                cm,
                apiKeyChecker = { it == "OPENAI_API_KEY" }
            )

            val result = router.route("setup.complete", JsonObject(emptyMap()), "test-session")
            result["completed"]!!.jsonPrimitive.boolean shouldBe true
            result["onboardingPhase"]!!.jsonPrimitive.content shouldBe "BOOTSTRAP_SEEDED"
        }

        @Test
        fun `complete preserves completed onboarding phase when bootstrap is already done`() = runTest {
            val config = GatewayConfig(secrets = SecretsConfig(anthropicApiKey = "sk-ant-123"))
            val cm = createConfigManager(config)
            val router = setupRouter(cm, phase = "ONBOARDING_COMPLETE")

            val result = router.route("setup.complete", JsonObject(emptyMap()), "test-session")
            result["completed"]!!.jsonPrimitive.boolean shouldBe true
            result["onboardingPhase"]!!.jsonPrimitive.content shouldBe "ONBOARDING_COMPLETE"
        }

        @Test
        fun `complete fails when no API keys`() = runTest {
            val cm = createConfigManager()
            val router = setupRouter(cm)

            val result = try {
                router.route("setup.complete", JsonObject(emptyMap()), "test-session")
                null
            } catch (e: IllegalArgumentException) {
                e.message
            }
            result shouldBe "At least one API key (Anthropic or OpenAI) must be configured before completing setup"
        }

        @Test
        fun `complete leaves onboarding phase unchanged when no keys`() = runTest {
            val cm = createConfigManager()
            val router = setupRouter(cm)

            try {
                router.route("setup.complete", JsonObject(emptyMap()), "test-session")
            } catch (_: IllegalArgumentException) {
                // expected
            }
            val status = router.route("setup.status", JsonObject(emptyMap()), "test-session")
            status["onboardingPhase"]!!.jsonPrimitive.content shouldBe "BOOTSTRAP_SEEDED"
        }
    }
}
