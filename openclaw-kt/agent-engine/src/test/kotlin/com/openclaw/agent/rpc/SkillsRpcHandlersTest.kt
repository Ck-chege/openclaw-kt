package com.openclaw.agent.rpc

import com.openclaw.agent.isolation.AgentResolver
import com.openclaw.gateway.config.AgentDefaults
import com.openclaw.gateway.config.AgentsConfig
import com.openclaw.gateway.config.ConfigManager
import com.openclaw.gateway.config.ConfigWriter
import com.openclaw.gateway.config.GatewayConfig
import com.openclaw.gateway.rpc.RpcRouter
import com.openclaw.skills.SkillEntryConfig
import com.openclaw.skills.SkillLoader
import com.openclaw.skills.SkillsConfig
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

class SkillsRpcHandlersTest {

    @TempDir
    lateinit var tempDir: Path

    private fun createSkill(name: String, description: String = "Skill $name") {
        val skillsDir = tempDir.resolve("skills").createDirectories()
        val skillDir = skillsDir.resolve(name).createDirectories()
        skillDir.resolve("SKILL.md").writeText(
            """
            |---
            |name: $name
            |description: $description
            |---
            |Use $name carefully.
            """.trimMargin()
        )
    }

    private fun createRouter(skillsConfig: SkillsConfig = SkillsConfig()): Pair<RpcRouter, ConfigManager> {
        val configPath = tempDir.resolve("openclaw.conf")
        val writer = ConfigWriter(configPath)
        val manager = ConfigManager(configPath)
        writer.write(
            GatewayConfig(
                agents = AgentsConfig(defaults = AgentDefaults(workspace = tempDir.toString())),
                skills = skillsConfig
            )
        )
        manager.load()
        val router = RpcRouter()
        registerSkillsHandlers(
            router = router,
            agentResolver = AgentResolver(),
            gatewayConfig = { manager.config.value },
            skillLoader = SkillLoader(),
            configManager = manager,
            configWriter = writer
        )
        return router to manager
    }

    @Nested
    inner class Registration {

        @Test
        fun `skills handlers are registered`() {
            val (router, _) = createRouter()

            router.hasHandler("skills.list") shouldBe true
            router.hasHandler("skills.status") shouldBe true
            router.hasHandler("skills.refresh") shouldBe true
        }
    }

    @Nested
    inner class SkillsList {

        @Test
        fun `skills list returns UI payload`() = runTest {
            createSkill("search", "Search docs")
            val (router, _) = createRouter(
                SkillsConfig(entries = mapOf("search" to SkillEntryConfig(enabled = false)))
            )

            val result = router.route("skills.list", buildJsonObject {}, "test-session")
            val skills = result["skills"]!!.jsonArray
            val search = skills.first { it.jsonObject["name"]!!.jsonPrimitive.content == "search" }.jsonObject

            result["workspacePath"]!!.jsonPrimitive.content shouldBe tempDir.toString()
            search["skillKey"]!!.jsonPrimitive.content shouldBe "search"
            search["source"]!!.jsonPrimitive.content shouldBe "workspace"
            search["enabled"]!!.jsonPrimitive.boolean shouldBe false
            search["eligible"]!!.jsonPrimitive.boolean shouldBe false
            search["filePath"]!!.jsonPrimitive.content shouldBe tempDir.resolve("skills/search/SKILL.md").toRealPath().toString()
        }
    }

    @Nested
    inner class SkillsStatus {

        @Test
        fun `skills status returns full status payload`() = runTest {
            createSkill("review", "Review changes")
            val (router, _) = createRouter()

            val result = router.route("skills.status", buildJsonObject {}, "test-session")
            val skills = result["skills"]!!.jsonArray
            val review = skills.first { it.jsonObject["name"]!!.jsonPrimitive.content == "review" }.jsonObject

            review["disabled"]!!.jsonPrimitive.boolean shouldBe false
            review["eligible"]!!.jsonPrimitive.boolean shouldBe true
            review["homepage"]?.jsonPrimitive?.contentOrNull shouldBe null
        }
    }

    @Nested
    inner class SkillsRefresh {

        @Test
        fun `skills refresh bumps version`() = runTest {
            val (router, _) = createRouter()

            val first = router.route("skills.refresh", buildJsonObject {}, "test-session")
            val second = router.route("skills.refresh", buildJsonObject {}, "test-session")

            first["version"]!!.jsonPrimitive.int shouldBe 1
            second["version"]!!.jsonPrimitive.int shouldBe 2
        }
    }

    @Nested
    inner class SkillsUpdate {

        @Test
        fun `skills update persists entry overrides and reloads config`() = runTest {
            createSkill("search", "Search docs")
            val (router, manager) = createRouter()

            val result = router.route(
                "skills.update",
                buildJsonObject {
                    put("skillKey", JsonPrimitive("search"))
                    put("enabled", JsonPrimitive(false))
                    put("apiKey", JsonPrimitive("search-api-key"))
                },
                "test-session"
            )

            result["updated"]!!.jsonPrimitive.boolean shouldBe true
            result["skillKey"]!!.jsonPrimitive.content shouldBe "search"
            manager.config.value.skills.entries["search"]?.enabled shouldBe false
            manager.config.value.skills.entries["search"]?.apiKey shouldBe "search-api-key"
            tempDir.resolve("openclaw.conf").readText().contains("skills {") shouldBe true
        }
    }
}
