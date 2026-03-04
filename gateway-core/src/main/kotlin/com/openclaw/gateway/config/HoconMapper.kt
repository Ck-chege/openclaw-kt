package com.openclaw.gateway.config

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
    tools = if (hasPath("tools")) getConfig("tools").toToolsConfig() else ToolsConfig(),
    memory = if (hasPath("memory")) getConfig("memory").toMemoryConfig() else MemoryConfig(),
    cron = if (hasPath("cron")) getConfig("cron").toCronConfig() else CronConfig()
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
        tools = if (hasPath("tools")) getConfig("tools").toAgentToolOverrides() else null
    )
} catch (e: ConfigException.Missing) {
    throw ConfigError.InvalidValue("agents.list[].id", "required string (agent id)", e)
}

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
    model = getOrDefault("model", MemoryConfig().model) { getString(it) }
)

fun Config.toCronConfig(): CronConfig = CronConfig(
    enabled = getOrDefault("enabled", CronConfig().enabled) { getBoolean(it) }
)
