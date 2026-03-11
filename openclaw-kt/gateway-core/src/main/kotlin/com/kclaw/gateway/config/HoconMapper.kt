package com.kclaw.gateway.config

import com.kclaw.skills.SkillEntryConfig
import com.kclaw.skills.SkillsConfig
import com.kclaw.skills.SkillsInstallConfig
import com.kclaw.skills.SkillsLoadConfig
import com.typesafe.config.Config
import com.typesafe.config.ConfigException

/**
 * Extension functions to map typesafe-config [Config] objects to the [GatewayConfig] data class hierarchy.
 *
 * Each mapper reads only the keys it owns and falls back to data class defaults for missing sections.
 * [ConfigException] is caught and wrapped into [ConfigError.InvalidValue] with the offending path.
 */

/**
 * Reads a value at [path] using [getter], returning [default] if the path is absent.
 * Wraps any [ConfigException] into [ConfigError.InvalidValue].
 */
private inline fun <T> Config.getOrDefault(
    path: String,
    default: T,
    getter: Config.(String) -> T
): T {
    if (!hasPath(path)) return default
    return try {
        getter(path)
    } catch (e: ConfigException) {
        throw ConfigError.InvalidValue(path, "valid value", e)
    }
}

fun Config.toGatewayConfig(): GatewayConfig = GatewayConfig(
    gateway = if (hasPath("gateway")) getConfig("gateway").toGatewaySettings() else GatewaySettings(),
    agents = if (hasPath("agents")) getConfig("agents").toAgentsConfig() else AgentsConfig(),
    skills = if (hasPath("skills")) getConfig("skills").toSkillsConfig() else SkillsConfig(),
    tools = if (hasPath("tools")) getConfig("tools").toToolsConfig() else ToolsConfig(),
    memory = if (hasPath("memory")) getConfig("memory").toMemoryConfig() else MemoryConfig(),
    cron = if (hasPath("cron")) getConfig("cron").toCronConfig() else CronConfig(),
    persistence = if (hasPath("persistence")) getConfig("persistence").toPersistenceConfig() else PersistenceConfig(),
    providers = if (hasPath("providers")) getConfig("providers").toProvidersConfig() else ProvidersConfig(),
    secrets = if (hasPath("secrets")) getConfig("secrets").toSecretsConfig() else SecretsConfig()
)

fun Config.toGatewaySettings(): GatewaySettings = GatewaySettings(
    port = getOrDefault("port", GatewaySettings().port) { getInt(it) },
    bind = getOrDefault("bind", GatewaySettings().bind) { getString(it) },
    auth = if (hasPath("auth")) getConfig("auth").toAuthConfig() else AuthConfig(),
    reload = if (hasPath("reload")) getConfig("reload").toReloadConfig() else ReloadConfig()
)

fun Config.toAuthConfig(): AuthConfig = AuthConfig(
    token = getOrDefault("token", AuthConfig().token) { getString(it) },
    password = getOrDefault("password", AuthConfig().password) { getString(it) }
)

fun Config.toReloadConfig(): ReloadConfig = ReloadConfig(
    mode = getOrDefault("mode", ReloadConfig().mode) { path ->
        val raw = getString(path)
        val parsed = ReloadMode.entries.find { it.name.equals(raw, ignoreCase = true) }
            ?: throw ConfigError.InvalidValue(
                "reload.mode",
                "one of ${ReloadMode.entries.joinToString { it.name }}"
            )
        parsed
    }
)

fun Config.toAgentsConfig(): AgentsConfig = AgentsConfig(
    defaults = if (hasPath("defaults")) getConfig("defaults").toAgentDefaults() else AgentDefaults(),
    list = if (hasPath("list")) getConfigList("list").map { it.toAgentEntry() } else emptyList()
)

fun Config.toAgentDefaults(): AgentDefaults = AgentDefaults(
    model = getOrDefault("model", AgentDefaults().model) { getString(it) },
    workspace = getOrDefault("workspace", AgentDefaults().workspace) { getString(it) },
    compaction = getOrDefault("compaction", AgentDefaults().compaction) { getString(it) },
    maxConcurrentRuns = getOrDefault("maxConcurrentRuns", AgentDefaults().maxConcurrentRuns) { getInt(it) }
)

fun Config.toAgentEntry(): AgentEntry = try {
    AgentEntry(
        id = getString("id"),
        model = getOrDefault("model", null) { getString(it) },
        workspace = getOrDefault("workspace", null) { getString(it) },
        tools = if (hasPath("tools")) getConfig("tools").toAgentToolOverrides() else null,
        channels = getOrDefault("channels", emptyList()) { getStringList(it) }
    )
} catch (e: ConfigException.Missing) {
    throw ConfigError.InvalidValue("agents.list[].id", "required string (agent id)", e)
}

fun Config.toSkillsConfig(): SkillsConfig = SkillsConfig(
    allowBundled = getOrDefault("allowBundled", SkillsConfig().allowBundled) { getStringList(it) },
    load = if (hasPath("load")) getConfig("load").toSkillsLoadConfig() else SkillsLoadConfig(),
    install = if (hasPath("install")) getConfig("install").toSkillsInstallConfig() else SkillsInstallConfig(),
    entries = if (hasPath("entries")) getConfig("entries").root().keys.associateWith { key ->
        getConfig("entries").getConfig(key).toSkillEntryConfig()
    } else emptyMap()
)

fun Config.toSkillsLoadConfig(): SkillsLoadConfig = SkillsLoadConfig(
    extraDirs = getOrDefault("extraDirs", SkillsLoadConfig().extraDirs) { getStringList(it) },
    watch = getOrDefault("watch", SkillsLoadConfig().watch) { getBoolean(it) },
    watchDebounceMs = getOrDefault("watchDebounceMs", SkillsLoadConfig().watchDebounceMs) { getInt(it) },
    maxCandidatesPerRoot = getOrDefault("maxCandidatesPerRoot", SkillsLoadConfig().maxCandidatesPerRoot) { getInt(it) },
    maxSkillsLoadedPerSource = getOrDefault("maxSkillsLoadedPerSource", SkillsLoadConfig().maxSkillsLoadedPerSource) { getInt(it) },
    maxSkillsInPrompt = getOrDefault("maxSkillsInPrompt", SkillsLoadConfig().maxSkillsInPrompt) { getInt(it) },
    maxSkillsPromptChars = getOrDefault("maxSkillsPromptChars", SkillsLoadConfig().maxSkillsPromptChars) { getInt(it) },
    maxSkillFileBytes = getOrDefault("maxSkillFileBytes", SkillsLoadConfig().maxSkillFileBytes) { getInt(it) }
)

fun Config.toSkillsInstallConfig(): SkillsInstallConfig = SkillsInstallConfig(
    preferBrew = getOrDefault("preferBrew", SkillsInstallConfig().preferBrew) { getBoolean(it) },
    nodeManager = getOrDefault("nodeManager", SkillsInstallConfig().nodeManager) { getString(it) }
)

fun Config.toSkillEntryConfig(): SkillEntryConfig = SkillEntryConfig(
    enabled = getOrDefault("enabled", null) { getBoolean(it) },
    env = if (hasPath("env")) {
        getConfig("env").root().keys.associateWith { key -> getString("env.$key") }
    } else {
        emptyMap()
    },
    apiKey = getOrDefault("apiKey", null) { getString(it) },
    config = if (hasPath("config")) {
        getConfig("config").root().keys.associateWith { key -> getString("config.$key") }
    } else {
        emptyMap()
    }
)

fun Config.toToolsConfig(): ToolsConfig = ToolsConfig(
    profile = getOrDefault("profile", ToolsConfig().profile) { getString(it) },
    allow = getOrDefault("allow", ToolsConfig().allow) { getStringList(it) },
    deny = getOrDefault("deny", ToolsConfig().deny) { getStringList(it) },
    byProvider = if (hasPath("byProvider")) {
        getConfig("byProvider").root().keys.associateWith { key ->
            getConfig("byProvider").getConfig(key).toProviderToolConfig()
        }
    } else emptyMap(),
    sandbox = if (hasPath("sandbox")) getConfig("sandbox").toSandboxToolConfig() else SandboxToolConfig(),
    loopDetection = if (hasPath("loopDetection")) getConfig("loopDetection").toLoopDetectionConfig() else LoopDetectionConfig()
)

fun Config.toProviderToolConfig(): ProviderToolConfig = ProviderToolConfig(
    allow = getOrDefault("allow", ProviderToolConfig().allow) { getStringList(it) },
    deny = getOrDefault("deny", ProviderToolConfig().deny) { getStringList(it) }
)

fun Config.toSandboxToolConfig(): SandboxToolConfig = SandboxToolConfig(
    enabled = getOrDefault("enabled", SandboxToolConfig().enabled) { getBoolean(it) },
    allow = getOrDefault("allow", SandboxToolConfig().allow) { getStringList(it) },
    deny = getOrDefault("deny", SandboxToolConfig().deny) { getStringList(it) }
)

fun Config.toLoopDetectionConfig(): LoopDetectionConfig = LoopDetectionConfig(
    enabled = getOrDefault("enabled", LoopDetectionConfig().enabled) { getBoolean(it) },
    repeatWarning = getOrDefault("repeatWarning", LoopDetectionConfig().repeatWarning) { getInt(it) },
    repeatCritical = getOrDefault("repeatCritical", LoopDetectionConfig().repeatCritical) { getInt(it) },
    circuitBreaker = getOrDefault("circuitBreaker", LoopDetectionConfig().circuitBreaker) { getInt(it) }
)

fun Config.toAgentToolOverrides(): AgentToolOverrides = AgentToolOverrides(
    profile = getOrDefault("profile", null) { getString(it) },
    allow = getOrDefault("allow", AgentToolOverrides().allow) { getStringList(it) },
    deny = getOrDefault("deny", AgentToolOverrides().deny) { getStringList(it) }
)

fun Config.toMemoryConfig(): MemoryConfig = MemoryConfig(
    enabled = getOrDefault("enabled", MemoryConfig().enabled) { getBoolean(it) },
    provider = getOrDefault("provider", MemoryConfig().provider) { getString(it) },
    model = getOrDefault("model", MemoryConfig().model) { getString(it) },
    ollamaBaseUrl = getOrDefault("ollamaBaseUrl", MemoryConfig().ollamaBaseUrl) { getString(it) },
    chunkSize = getOrDefault("chunkSize", MemoryConfig().chunkSize) { getInt(it) },
    chunkOverlap = getOrDefault("chunkOverlap", MemoryConfig().chunkOverlap) { getInt(it) }
)

fun Config.toCronConfig(): CronConfig = CronConfig(
    enabled = getOrDefault("enabled", CronConfig().enabled) { getBoolean(it) }
)

fun Config.toPersistenceConfig(): PersistenceConfig = PersistenceConfig(
    enabled = getOrDefault("enabled", PersistenceConfig().enabled) { getBoolean(it) },
    sqlite = if (hasPath("sqlite")) getConfig("sqlite").toSqlitePersistenceConfig() else SqlitePersistenceConfig()
)

fun Config.toSqlitePersistenceConfig(): SqlitePersistenceConfig = SqlitePersistenceConfig(
    path = getOrDefault("path", SqlitePersistenceConfig().path) { getString(it) }
)

fun Config.toProvidersConfig(): ProvidersConfig = ProvidersConfig(
    ollama = if (hasPath("ollama")) getConfig("ollama").toOllamaConfig() else OllamaConfig(),
    vllm = if (hasPath("vllm")) getConfig("vllm").toVllmConfig() else VllmConfig(),
    litellm = if (hasPath("litellm")) getConfig("litellm").toLitellmConfig() else LitellmConfig(),
    azure = if (hasPath("azure")) getConfig("azure").toAzureConfig() else AzureConfig()
)

fun Config.toOllamaConfig(): OllamaConfig = OllamaConfig(
    baseUrl = getOrDefault("baseUrl", OllamaConfig().baseUrl) { getString(it) }
)

fun Config.toVllmConfig(): VllmConfig = VllmConfig(
    baseUrl = getOrDefault("baseUrl", VllmConfig().baseUrl) { getString(it) }
)

fun Config.toLitellmConfig(): LitellmConfig = LitellmConfig(
    baseUrl = getOrDefault("baseUrl", LitellmConfig().baseUrl) { getString(it) }
)

fun Config.toAzureConfig(): AzureConfig = AzureConfig(
    endpoint = getOrDefault("endpoint", AzureConfig().endpoint) { getString(it) },
    deployment = getOrDefault("deployment", AzureConfig().deployment) { getString(it) },
    apiVersion = getOrDefault("apiVersion", AzureConfig().apiVersion) { getString(it) }
)

fun Config.toSecretsConfig(): SecretsConfig = SecretsConfig(
    anthropicApiKey = getOrDefault("anthropicApiKey", null) { getString(it) },
    openaiApiKey = getOrDefault("openaiApiKey", null) { getString(it) }
)
