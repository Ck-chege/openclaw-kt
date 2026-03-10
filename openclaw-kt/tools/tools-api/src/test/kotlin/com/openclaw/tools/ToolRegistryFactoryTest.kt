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

    private object StubSessionsSend : SimpleTool<StubSessionsSend.Args>(
        argsSerializer = Args.serializer(), name = "sessions_send", description = "Send to session"
    ) {
        @Serializable
        data class Args(val key: String = "")
        override suspend fun execute(args: Args): String = ""
    }

    private val defaultCaller = CallerContext()
    private val allTools = listOf(StubRead, StubWrite, StubExec, StubSessionsSend)
    private val factory = ToolRegistryFactory(ToolPolicyPipeline(), allTools)

    @Test
    fun `builds registry with all tools when profile is full and no restrictions`() {
        val registry = factory.buildRegistry(ToolsConfig(), callerContext = defaultCaller)
        registry.tools.map { it.name }.shouldContainExactlyInAnyOrder("read", "write", "exec")
    }

    @Test
    fun `filters out denied tools`() {
        val config = ToolsConfig(deny = listOf("exec"))
        val registry = factory.buildRegistry(config, callerContext = defaultCaller)
        registry.tools.map { it.name }.shouldContainExactlyInAnyOrder("read", "write")
    }

    @Test
    fun `empty result produces EMPTY registry`() {
        val config = ToolsConfig(profile = "none")
        val registry = factory.buildRegistry(config, callerContext = defaultCaller)
        registry shouldBe ToolRegistry.EMPTY
    }

    @Test
    fun `per-agent overrides narrow from global`() {
        val config = ToolsConfig()
        val overrides = AgentToolOverrides(allow = listOf("read"))
        val registry = factory.buildRegistry(
            config,
            agentToolOverrides = overrides,
            callerContext = defaultCaller
        )
        registry.tools.map { it.name } shouldBe listOf("read")
    }

    @Test
    fun `provider-specific restrictions are applied from caller context`() {
        val config = ToolsConfig(
            byProvider = mapOf("openai" to com.openclaw.gateway.config.ProviderToolConfig(deny = listOf("exec")))
        )

        val registry = factory.buildRegistry(
            config,
            callerContext = CallerContext(provider = "openai")
        )

        registry.tools.map { it.name }.shouldContainExactlyInAnyOrder("read", "write")
    }

    @Test
    fun `owner-only tools are excluded for non-owner callers`() {
        val registry = factory.buildRegistry(ToolsConfig(), callerContext = CallerContext(isOwner = false))

        registry.tools.map { it.name }.shouldContainExactlyInAnyOrder("read", "write", "exec")
    }

    @Test
    fun `owner-only tools are included for owner callers`() {
        val registry = factory.buildRegistry(ToolsConfig(), callerContext = CallerContext(isOwner = true))

        registry.tools.map { it.name }
            .shouldContainExactlyInAnyOrder("read", "write", "exec", "sessions_send")
    }

    @Test
    fun `buildDescriptions excludes owner-only tools for non-owner callers`() {
        val descriptions = factory.buildDescriptions(ToolsConfig(), callerContext = CallerContext(isOwner = false))

        descriptions.map { it.first }.shouldContainExactlyInAnyOrder("read", "write", "exec")
    }

    @Test
    fun `buildDescriptions includes owner-only tools for owner callers`() {
        val descriptions = factory.buildDescriptions(ToolsConfig(), callerContext = CallerContext(isOwner = true))

        descriptions.map { it.first }.shouldContainExactlyInAnyOrder("read", "write", "exec", "sessions_send")
    }
}
