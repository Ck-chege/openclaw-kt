package com.openclaw.tools.policy

import com.openclaw.gateway.config.*
import com.openclaw.protocol.dto.ToolPermission
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ToolPolicyPipelineTest {

    private val pipeline = ToolPolicyPipeline()
    private val defaultConfig = ToolsConfig()

    @Nested
    inner class ProfileStage {

        @Test
        fun `full profile allows all tools`() {
            val result = pipeline.evaluate("exec", ToolsConfig(profile = "full"))
            result shouldBe ToolPermission.Allowed
        }

        @Test
        fun `safe profile denies exec`() {
            val result = pipeline.evaluate("exec", ToolsConfig(profile = "safe"))
            result.shouldBeInstanceOf<ToolPermission.Denied>()
        }

        @Test
        fun `safe profile denies process`() {
            val result = pipeline.evaluate("process", ToolsConfig(profile = "safe"))
            result.shouldBeInstanceOf<ToolPermission.Denied>()
        }

        @Test
        fun `safe profile allows read`() {
            val result = pipeline.evaluate("read", ToolsConfig(profile = "safe"))
            result shouldBe ToolPermission.Allowed
        }

        @Test
        fun `minimal profile allows only read-only tools`() {
            pipeline.evaluate("read", ToolsConfig(profile = "minimal")) shouldBe ToolPermission.Allowed
            pipeline.evaluate("sessions_list", ToolsConfig(profile = "minimal")) shouldBe ToolPermission.Allowed
            pipeline.evaluate("write", ToolsConfig(profile = "minimal")).shouldBeInstanceOf<ToolPermission.Denied>()
            pipeline.evaluate("exec", ToolsConfig(profile = "minimal")).shouldBeInstanceOf<ToolPermission.Denied>()
        }

        @Test
        fun `none profile denies all tools`() {
            pipeline.evaluate("read", ToolsConfig(profile = "none")).shouldBeInstanceOf<ToolPermission.Denied>()
            pipeline.evaluate("exec", ToolsConfig(profile = "none")).shouldBeInstanceOf<ToolPermission.Denied>()
        }
    }

    @Nested
    inner class GlobalAllowDeny {

        @Test
        fun `empty allow list does not restrict`() {
            val result = pipeline.evaluate("exec", ToolsConfig(allow = emptyList()))
            result shouldBe ToolPermission.Allowed
        }

        @Test
        fun `non-empty allow list restricts to only listed tools`() {
            val config = ToolsConfig(allow = listOf("read", "write"))
            pipeline.evaluate("read", config) shouldBe ToolPermission.Allowed
            pipeline.evaluate("exec", config).shouldBeInstanceOf<ToolPermission.Denied>()
        }

        @Test
        fun `deny list blocks specific tools`() {
            val config = ToolsConfig(deny = listOf("exec"))
            pipeline.evaluate("exec", config).shouldBeInstanceOf<ToolPermission.Denied>()
            pipeline.evaluate("read", config) shouldBe ToolPermission.Allowed
        }

        @Test
        fun `deny takes precedence over allow when tool in both`() {
            val config = ToolsConfig(allow = listOf("exec", "read"), deny = listOf("exec"))
            pipeline.evaluate("exec", config).shouldBeInstanceOf<ToolPermission.Denied>()
        }

        @Test
        fun `allow list with group reference expands correctly`() {
            val config = ToolsConfig(allow = listOf("group:fs"))
            pipeline.evaluate("read", config) shouldBe ToolPermission.Allowed
            pipeline.evaluate("write", config) shouldBe ToolPermission.Allowed
            pipeline.evaluate("exec", config).shouldBeInstanceOf<ToolPermission.Denied>()
        }
    }

    @Nested
    inner class ProviderRestrictions {

        @Test
        fun `no provider config passes through`() {
            val result = pipeline.evaluate(
                "exec", defaultConfig,
                callerContext = CallerContext(provider = "anthropic")
            )
            result shouldBe ToolPermission.Allowed
        }

        @Test
        fun `provider deny blocks tool for that provider`() {
            val config = ToolsConfig(
                byProvider = mapOf("openai" to ProviderToolConfig(deny = listOf("exec")))
            )
            val result = pipeline.evaluate(
                "exec", config,
                callerContext = CallerContext(provider = "openai")
            )
            result.shouldBeInstanceOf<ToolPermission.Denied>()
        }

        @Test
        fun `other providers unaffected by provider-specific deny`() {
            val config = ToolsConfig(
                byProvider = mapOf("openai" to ProviderToolConfig(deny = listOf("exec")))
            )
            val result = pipeline.evaluate(
                "exec", config,
                callerContext = CallerContext(provider = "anthropic")
            )
            result shouldBe ToolPermission.Allowed
        }
    }

    @Nested
    inner class AgentOverrides {

        @Test
        fun `null overrides passes through`() {
            val result = pipeline.evaluate("exec", defaultConfig, agentToolOverrides = null)
            result shouldBe ToolPermission.Allowed
        }

        @Test
        fun `agent deny blocks tool`() {
            val overrides = AgentToolOverrides(deny = listOf("exec"))
            val result = pipeline.evaluate("exec", defaultConfig, agentToolOverrides = overrides)
            result.shouldBeInstanceOf<ToolPermission.Denied>()
        }

        @Test
        fun `agent allow list narrows from global`() {
            val overrides = AgentToolOverrides(allow = listOf("read"))
            pipeline.evaluate("read", defaultConfig, agentToolOverrides = overrides) shouldBe ToolPermission.Allowed
            pipeline.evaluate("exec", defaultConfig, agentToolOverrides = overrides).shouldBeInstanceOf<ToolPermission.Denied>()
        }

        @Test
        fun `agent profile overrides global profile`() {
            val config = ToolsConfig(profile = "full")
            val overrides = AgentToolOverrides(profile = "safe")
            pipeline.evaluate("exec", config, agentToolOverrides = overrides).shouldBeInstanceOf<ToolPermission.Denied>()
            pipeline.evaluate("read", config, agentToolOverrides = overrides) shouldBe ToolPermission.Allowed
        }
    }

    @Nested
    inner class SandboxStage {

        @Test
        fun `disabled sandbox passes through`() {
            val config = ToolsConfig(sandbox = SandboxToolConfig(enabled = false, deny = listOf("exec")))
            pipeline.evaluate("exec", config) shouldBe ToolPermission.Allowed
        }

        @Test
        fun `enabled sandbox with deny blocks tools`() {
            val config = ToolsConfig(sandbox = SandboxToolConfig(enabled = true, deny = listOf("exec")))
            pipeline.evaluate("exec", config).shouldBeInstanceOf<ToolPermission.Denied>()
            pipeline.evaluate("read", config) shouldBe ToolPermission.Allowed
        }

        @Test
        fun `enabled sandbox with allow list restricts to allowed only`() {
            val config = ToolsConfig(sandbox = SandboxToolConfig(enabled = true, allow = listOf("read")))
            pipeline.evaluate("read", config) shouldBe ToolPermission.Allowed
            pipeline.evaluate("exec", config).shouldBeInstanceOf<ToolPermission.Denied>()
        }
    }

    @Nested
    inner class OwnerGate {

        @Test
        fun `cron tool allowed when caller is owner`() {
            val result = pipeline.evaluate("cron", defaultConfig, callerContext = CallerContext(isOwner = true))
            result shouldBe ToolPermission.Allowed
        }

        @Test
        fun `cron tool denied when caller is not owner`() {
            val result = pipeline.evaluate("cron", defaultConfig, callerContext = CallerContext(isOwner = false))
            result.shouldBeInstanceOf<ToolPermission.Denied>()
        }

        @Test
        fun `gateway tool denied for non-owner`() {
            pipeline.evaluate("gateway", defaultConfig, callerContext = CallerContext(isOwner = false))
                .shouldBeInstanceOf<ToolPermission.Denied>()
        }

        @Test
        fun `sessions_send denied for non-owner`() {
            pipeline.evaluate("sessions_send", defaultConfig, callerContext = CallerContext(isOwner = false))
                .shouldBeInstanceOf<ToolPermission.Denied>()
        }

        @Test
        fun `sessions_spawn denied for non-owner`() {
            pipeline.evaluate("sessions_spawn", defaultConfig, callerContext = CallerContext(isOwner = false))
                .shouldBeInstanceOf<ToolPermission.Denied>()
        }
    }

    @Nested
    inner class CascadeIntegration {

        @Test
        fun `full pipeline narrows through all stages`() {
            // Profile=full allows all, but global deny blocks exec,
            // and owner gate blocks cron for non-owner
            val config = ToolsConfig(deny = listOf("exec"))
            val ctx = CallerContext(isOwner = false)

            pipeline.evaluate("exec", config, callerContext = ctx).shouldBeInstanceOf<ToolPermission.Denied>()
            pipeline.evaluate("cron", config, callerContext = ctx).shouldBeInstanceOf<ToolPermission.Denied>()
            pipeline.evaluate("read", config, callerContext = ctx) shouldBe ToolPermission.Allowed
        }

        @Test
        fun `filterAllowed returns only permitted tools`() {
            val config = ToolsConfig(profile = "safe")
            val all = setOf("read", "write", "edit", "exec", "process")
            val allowed = pipeline.filterAllowed(all, config)
            allowed shouldBe setOf("read", "write", "edit")
        }
    }
}
