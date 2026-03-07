package com.openclaw.gateway.config

import kotlinx.serialization.Serializable

@Serializable
data class GatewayConfig(
    val gateway: GatewaySettings = GatewaySettings(),
    val agents: AgentsConfig = AgentsConfig(),
    val tools: ToolsConfig = ToolsConfig(),
    val memory: MemoryConfig = MemoryConfig(),
    val cron: CronConfig = CronConfig(),
    val secrets: SecretsConfig = SecretsConfig()
)

@Serializable
data class GatewaySettings(
    val port: Int = 18789,
    val bind: String = "127.0.0.1",
    val auth: AuthConfig = AuthConfig(),
    val reload: ReloadConfig = ReloadConfig()
)

@Serializable
data class AuthConfig(
    val token: String? = null,
    val password: String? = null
)

@Serializable
data class ReloadConfig(
    val mode: ReloadMode = ReloadMode.HYBRID
)

@Serializable
enum class ReloadMode { HYBRID, HOT, RESTART, OFF }

@Serializable
data class AgentsConfig(
    val defaults: AgentDefaults = AgentDefaults(),
    val list: List<AgentEntry> = emptyList()
)

@Serializable
data class AgentDefaults(
    val model: String = "anthropic:claude-sonnet-4",
    val workspace: String = "~/.openclawk/workspace",
    val compaction: String = "safeguard",
    val maxConcurrentRuns: Int = 4
)

@Serializable
data class AgentEntry(
    val id: String,
    val model: String? = null,
    val workspace: String? = null,
    val tools: AgentToolOverrides? = null,
    /** Explicit channel bindings. When non-empty, only messages from these channels route to this agent. */
    val channels: List<String> = emptyList()
)

@Serializable
data class ToolsConfig(
    val profile: String = "full",
    val allow: List<String> = emptyList(),
    val deny: List<String> = emptyList(),
    val byProvider: Map<String, ProviderToolConfig> = emptyMap(),
    val sandbox: SandboxToolConfig = SandboxToolConfig(),
    val loopDetection: LoopDetectionConfig = LoopDetectionConfig(),
    val mcp: List<McpServerEntry> = emptyList(),
    /** Filesystem access policy — mirrors TS tools.fs config. */
    val fs: FsToolConfig = FsToolConfig()
)

@Serializable
data class FsToolConfig(
    /**
     * When true, read/write/edit tools are restricted to the agent workspace directory.
     * When false (default), the agent can access any file on the machine — same as the
     * TS OpenClaw default behaviour.
     */
    val workspaceOnly: Boolean = false
)

@Serializable
data class McpServerEntry(
    val name: String,
    val transport: String = "stdio",
    val command: String? = null,
    val args: List<String> = emptyList(),
    val url: String? = null,
    val env: Map<String, String> = emptyMap(),
    val enabled: Boolean = true
)

@Serializable
data class ProviderToolConfig(
    val allow: List<String> = emptyList(),
    val deny: List<String> = emptyList()
)

@Serializable
data class SandboxToolConfig(
    val enabled: Boolean = false,
    val allow: List<String> = emptyList(),
    val deny: List<String> = emptyList()
)

@Serializable
data class LoopDetectionConfig(
    val enabled: Boolean = true,
    val repeatWarning: Int = 10,
    val repeatCritical: Int = 20,
    val circuitBreaker: Int = 30
)

@Serializable
data class AgentToolOverrides(
    val profile: String? = null,
    val allow: List<String> = emptyList(),
    val deny: List<String> = emptyList()
)

@Serializable
data class MemoryConfig(
    val enabled: Boolean = true,
    val provider: String = "openai",
    val model: String = "text-embedding-3-small",
    val ollamaBaseUrl: String = "http://localhost:11434",
    val chunkSize: Int = 400,
    val chunkOverlap: Int = 80
)

@Serializable
data class CronConfig(
    val enabled: Boolean = true
)

@Serializable
data class SecretsConfig(
    val anthropicApiKey: String? = null,
    val openaiApiKey: String? = null
)
