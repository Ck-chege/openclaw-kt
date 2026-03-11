package com.kclaw.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import com.kclaw.gateway.config.AgentToolOverrides
import com.kclaw.gateway.config.ToolsConfig
import com.kclaw.tools.policy.CallerContext
import com.kclaw.tools.policy.ToolPolicyPipeline
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

    private object StubCreateSkill : SimpleTool<StubCreateSkill.Args>(
        argsSerializer = Args.serializer(), name = "create_skill", description = "Default create skill"
    ) {
        @Serializable
        data class Args(val name: String = "")
        override suspend fun execute(args: Args): String = "default"
    }

    private object StubDynamicCreateSkill : SimpleTool<StubDynamicCreateSkill.Args>(
        argsSerializer = Args.serializer(), name = "create_skill", description = "Dynamic create skill"
    ) {
        @Serializable
        data class Args(val name: String = "")
        override suspend fun execute(args: Args): String = "dynamic"
    }

    private val defaultCaller = CallerContext()
    private val allTools = listOf(StubRead, StubWrite, StubExec, StubSessionsSend, StubCreateSkill)
    private val factory = ToolRegistryFactory(ToolPolicyPipeline(), allTools)

    @Test
    fun `builds registry with all tools when profile is full and no restrictions`() {
        val registry = factory.buildRegistry(ToolsConfig(), callerContext = defaultCaller)
        registry.tools.map { it.name }.shouldContainExactlyInAnyOrder("read", "write", "exec", "create_skill")
    }

    @Test
    fun `filters out denied tools`() {
        val config = ToolsConfig(deny = listOf("exec"))
        val registry = factory.buildRegistry(config, callerContext = defaultCaller)
        registry.tools.map { it.name }.shouldContainExactlyInAnyOrder("read", "write", "create_skill")
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
            byProvider = mapOf("openai" to com.kclaw.gateway.config.ProviderToolConfig(deny = listOf("exec")))
        )

        val registry = factory.buildRegistry(
            config,
            callerContext = CallerContext(provider = "openai")
        )

        registry.tools.map { it.name }.shouldContainExactlyInAnyOrder("read", "write", "create_skill")
    }

    @Test
    fun `owner-only tools are excluded for non-owner callers`() {
        val registry = factory.buildRegistry(ToolsConfig(), callerContext = CallerContext(isOwner = false))

        registry.tools.map { it.name }.shouldContainExactlyInAnyOrder("read", "write", "exec", "create_skill")
    }

    @Test
    fun `owner-only tools are included for owner callers`() {
        val registry = factory.buildRegistry(ToolsConfig(), callerContext = CallerContext(isOwner = true))

        registry.tools.map { it.name }
            .shouldContainExactlyInAnyOrder("read", "write", "exec", "sessions_send", "create_skill")
    }

    @Test
    fun `buildDescriptions excludes owner-only tools for non-owner callers`() {
        val descriptions = factory.buildDescriptions(ToolsConfig(), callerContext = CallerContext(isOwner = false))

        descriptions.map { it.first }.shouldContainExactlyInAnyOrder("read", "write", "exec", "create_skill")
    }

    @Test
    fun `buildDescriptions includes owner-only tools for owner callers`() {
        val descriptions = factory.buildDescriptions(ToolsConfig(), callerContext = CallerContext(isOwner = true))

        descriptions.map { it.first }.shouldContainExactlyInAnyOrder("read", "write", "exec", "sessions_send", "create_skill")
    }

    @Test
    fun `extra tools override same-named base tools`() {
        val registry = factory.buildRegistry(
            ToolsConfig(),
            callerContext = defaultCaller,
            extraTools = listOf(StubDynamicCreateSkill)
        )

        registry.tools.count { it.name == "create_skill" } shouldBe 1
        registry.tools.first { it.name == "create_skill" }.descriptor.description shouldBe "Dynamic create skill"
    }
}
