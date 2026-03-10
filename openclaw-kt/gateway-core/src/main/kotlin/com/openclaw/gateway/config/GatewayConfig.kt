package com.openclaw.gateway.config

import com.openclaw.skills.SkillsConfig
import kotlinx.serialization.Serializable

@Serializable
data class GatewayConfig(
    val gateway: GatewaySettings = GatewaySettings(),
    val agents: AgentsConfig = AgentsConfig(),
    val skills: SkillsConfig = SkillsConfig(),
    val tools: ToolsConfig = ToolsConfig(),
    val memory: MemoryConfig = MemoryConfig(),
    val cron: CronConfig = CronConfig(),
    val persistence: PersistenceConfig = PersistenceConfig(),
    val providers: ProvidersConfig = ProvidersConfig(),
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
data class PersistenceConfig(
    val enabled: Boolean = true,
    val sqlite: SqlitePersistenceConfig = SqlitePersistenceConfig()
)

@Serializable
data class SqlitePersistenceConfig(
    val path: String = "~/.openclawk/checkpoints.sqlite"
)

/**
 * Per-provider configuration — base URL overrides and provider-specific settings.
 * API keys live in [SecretsConfig] or env vars.
 */
@Serializable
data class ProvidersConfig(
    /** Ollama local endpoint (default: http://127.0.0.1:11434) */
    val ollama: OllamaConfig = OllamaConfig(),
    /** vLLM local endpoint (default: http://127.0.0.1:8000/v1) */
    val vllm: VllmConfig = VllmConfig(),
    /** LiteLLM proxy endpoint (default: http://127.0.0.1:4000/v1) */
    val litellm: LitellmConfig = LitellmConfig(),
    /** Azure OpenAI — requires endpoint + deployment */
    val azure: AzureConfig = AzureConfig()
)

@Serializable data class OllamaConfig(val baseUrl: String = "http://127.0.0.1:11434")
@Serializable data class VllmConfig(val baseUrl: String = "http://127.0.0.1:8000/v1")
@Serializable data class LitellmConfig(val baseUrl: String = "http://127.0.0.1:4000/v1")

@Serializable
data class AzureConfig(
    /** e.g. https://my-resource.openai.azure.com */
    val endpoint: String? = null,
    /** Deployment name (= model alias in Azure) */
    val deployment: String? = null,
    /** Azure OpenAI API version */
    val apiVersion: String = "2024-12-01-preview"
)

@Serializable
data class SecretsConfig(
    // Anthropic
    val anthropicApiKey: String? = null,
    // OpenAI & Azure
    val openaiApiKey: String? = null,
    val azureApiKey: String? = null,
    // Google / Gemini
    val googleApiKey: String? = null,
    // OpenRouter
    val openrouterApiKey: String? = null,
    // Mistral
    val mistralApiKey: String? = null,
    // Groq
    val groqApiKey: String? = null,
    // DeepSeek
    val deepseekApiKey: String? = null,
    // xAI (Grok)
    val xaiApiKey: String? = null,
    // Together AI
    val togetherApiKey: String? = null,
    // NVIDIA NIM
    val nvidiaApiKey: String? = null,
    // Cerebras
    val cerebrasApiKey: String? = null,
    // Moonshot / Kimi
    val moonshotApiKey: String? = null,
    // vLLM (optional)
    val vllmApiKey: String? = null,
    // LiteLLM proxy (optional)
    val litellmApiKey: String? = null,
    // AWS Bedrock
    val awsAccessKeyId: String? = null,
    val awsSecretAccessKey: String? = null,
    val awsRegion: String? = null
)
