package com.openclaw.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import com.openclaw.gateway.config.AgentToolOverrides
import com.openclaw.gateway.config.ToolsConfig
import com.openclaw.tools.policy.CallerContext
import com.openclaw.tools.policy.ToolPolicyPipeline
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ToolRegistryFactoryTest {

    // Minimal stub tools for testing
    private object StubRead : SimpleTool<StubRead.Args>(
        argsSerializer = Args.serializer(), name = "read", description = "Read file"
    ) {
        @Serializable
        data class Args(val path: String = "")
        override suspend fun execute(args: Args): String = ""
    }

    private object StubWrite : SimpleTool<StubWrite.Args>(
        argsSerializer = Args.serializer(), name = "write", description = "Write file"
    ) {
        @Serializable
        data class Args(val path: String = "")
        override suspend fun execute(args: Args): String = ""
    }

    private object StubExec : SimpleTool<StubExec.Args>(
        argsSerializer = Args.serializer(), name = "exec", description = "Exec command"
    ) {
        @Serializable
        data class Args(val command: String = "")
        override suspend fun execute(args: Args): String = ""
    }

    private val allTools = listOf(StubRead, StubWrite, StubExec)
    private val factory = ToolRegistryFactory(ToolPolicyPipeline(), allTools)

    @Nested
    inner class BuildRegistry {

        @Test
        fun `builds registry with all tools when profile is full and no restrictions`() {
            val registry = factory.buildRegistry(ToolsConfig())
            registry.tools.map { it.name }.shouldContainExactlyInAnyOrder("read", "write", "exec")
        }

        @Test
        fun `filters out denied tools`() {
            val config = ToolsConfig(deny = listOf("exec"))
            val registry = factory.buildRegistry(config)
            registry.tools.map { it.name }.shouldContainExactlyInAnyOrder("read", "write")
        }

        @Test
        fun `empty result produces EMPTY registry`() {
            val config = ToolsConfig(profile = "none")
            val registry = factory.buildRegistry(config)
            registry shouldBe ToolRegistry.EMPTY
        }

        @Test
        fun `per-agent overrides narrow from global`() {
            val config = ToolsConfig()
            val overrides = AgentToolOverrides(allow = listOf("read"))
            val registry = factory.buildRegistry(config, agentToolOverrides = overrides)
            registry.tools.map { it.name } shouldBe listOf("read")
        }
    }
}
