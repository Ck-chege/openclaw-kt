package com.kclaw.gateway.rpc

import com.kclaw.gateway.config.ConfigManager
import com.kclaw.gateway.config.ConfigWriter
import com.kclaw.gateway.config.GatewayConfig
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ConfigRpcHandlersTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var router: RpcRouter
    private lateinit var configManager: ConfigManager

    @BeforeEach
    fun setup() {
        val configPath = tempDir.resolve("kclaw.conf")
        ConfigWriter(configPath).write(GatewayConfig())
        configManager = ConfigManager(configPath)
        configManager.load()
        router = RpcRouter()
    }

    @Test
    fun `models list returns ready models separately from supported catalog`() = runTest {
        registerConfigHandlers(
            router = router,
            configManager = configManager,
            modelLister = {
                listOf(
                    ModelInfo(
                        id = "openai:gpt-5.4",
                        provider = "openai",
                        displayName = "GPT-5.4",
                        ready = true
                    ),
                    ModelInfo(
                        id = "anthropic:claude-sonnet-4-6",
                        provider = "anthropic",
                        displayName = "Claude Sonnet 4.6",
                        ready = false,
                        unavailableReason = "Missing ANTHROPIC_API_KEY"
                    )
                )
            }
        )

        val result = router.route("models.list", JsonObject(emptyMap()), "test-session")

        val ready = result["models"]!!.jsonArray
        val supported = result["supportedModels"]!!.jsonArray

        ready.size shouldBe 1
        supported.size shouldBe 2
        ready[0].jsonObject["id"]!!.jsonPrimitive.content shouldBe "openai:gpt-5.4"
        ready[0].jsonObject["ready"]!!.jsonPrimitive.boolean shouldBe true
        supported[1].jsonObject["id"]!!.jsonPrimitive.content shouldBe "anthropic:claude-sonnet-4-6"
        supported[1].jsonObject["ready"]!!.jsonPrimitive.boolean shouldBe false
        supported[1].jsonObject["unavailableReason"]!!.jsonPrimitive.content shouldBe "Missing ANTHROPIC_API_KEY"
    }
}
