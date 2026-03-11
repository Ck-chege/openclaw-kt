package com.kclaw.agent.rpc

import com.kclaw.agent.isolation.AgentResolver
import com.kclaw.gateway.config.ConfigManager
import com.kclaw.gateway.config.ConfigWriter
import com.kclaw.gateway.config.GatewayConfig
import com.kclaw.gateway.config.isTruthyPath
import com.kclaw.gateway.rpc.RpcRouter
import com.kclaw.skills.SkillEntryConfig
import com.kclaw.skills.SkillLoader
import com.kclaw.skills.SkillStatusEntry
import com.kclaw.skills.SkillsWatcherService
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val skillsJson = Json { encodeDefaults = true }

fun registerSkillsHandlers(
    router: RpcRouter,
    agentResolver: AgentResolver,
    gatewayConfig: () -> GatewayConfig,
    skillLoader: SkillLoader,
    configManager: ConfigManager? = null,
    configWriter: ConfigWriter? = null,
    skillsWatcher: SkillsWatcherService? = null
) {
    fun resolveWorkspace(config: GatewayConfig, agentId: String?) =
        agentResolver.resolve(config.agents, agentId).workspace.workspacePath

    router.register("skills.list") { params, _ ->
        val config = gatewayConfig()
        val agentId = params["agentId"]?.jsonPrimitive?.contentOrNull
        val workspacePath = resolveWorkspace(config, agentId)
        skillsWatcher?.ensureWatching(workspacePath, config.skills)
        val report = skillLoader.buildStatus(
            workspacePath = workspacePath,
            config = config.skills,
            isConfigPathTruthy = { path -> config.isTruthyPath(path) }
        )
        buildJsonObject {
            put("workspacePath", report.workspacePath)
            putJsonArray("skills") {
                report.skills.forEach { skill ->
                    add(buildJsonObject {
                        put("name", skill.name)
                        put("skillKey", skill.skillKey)
                        put("description", skill.description)
                        put("source", skill.source.name.lowercase())
                        put("enabled", !skill.disabled)
                        put("eligible", skill.eligible)
                        put("always", skill.always)
                        put("filePath", skill.filePath)
                        put("baseDir", skill.baseDir)
                        put("blockedByAllowlist", skill.blockedByAllowlist)
                        skill.emoji?.let { put("emoji", it) }
                        skill.homepage?.let { put("homepage", it) }
                        putJsonObject("invocation") {
                            put("userInvocable", skill.invocation.userInvocable)
                            put("disableModelInvocation", skill.invocation.disableModelInvocation)
                            skill.invocation.commandName?.let { put("commandName", it) }
                            skill.invocation.commandDispatch?.let { put("commandDispatch", it.name.lowercase()) }
                            skill.invocation.commandTool?.let { put("commandTool", it) }
                            put("commandArgMode", skill.invocation.commandArgMode.name.lowercase())
                        }
                        putJsonObject("entry") {
                            skill.entry.enabled?.let { put("enabled", it) }
                            put("hasApiKey", skill.entry.hasApiKey)
                            putJsonObject("env") {
                                skill.entry.env.forEach { (key, value) -> put(key, value) }
                            }
                            putJsonObject("config") {
                                skill.entry.config.forEach { (key, value) -> put(key, value) }
                            }
                        }
                        putJsonObject("resources") {
                            putJsonArray("scripts") { skill.resources.scripts.forEach { add(JsonPrimitive(it)) } }
                            putJsonArray("references") { skill.resources.references.forEach { add(JsonPrimitive(it)) } }
                            putJsonArray("assets") { skill.resources.assets.forEach { add(JsonPrimitive(it)) } }
                        }
                        putJsonArray("requirements") {
                            skill.requirements.bins.forEach { value ->
                                add(buildJsonObject {
                                    put("type", "bin")
                                    put("value", value)
                                    put("passed", value !in skill.missing.bins)
                                })
                            }
                            skill.requirements.anyBins.forEach { value ->
                                add(buildJsonObject {
                                    put("type", "any_bin")
                                    put("value", value)
                                    put("passed", skill.missing.anyBins.isEmpty())
                                })
                            }
                            skill.requirements.env.forEach { value ->
                                add(buildJsonObject {
                                    put("type", "env")
                                    put("value", value)
                                    put("passed", value !in skill.missing.env)
                                })
                            }
                            skill.requirements.config.forEach { value ->
                                add(buildJsonObject {
                                    put("type", "config")
                                    put("value", value)
                                    put("passed", value !in skill.missing.config)
                                })
                            }
                            skill.requirements.os.forEach { value ->
                                add(buildJsonObject {
                                    put("type", "os")
                                    put("value", value)
                                    put("passed", skill.missing.os.isEmpty())
                                })
                            }
                        }
                    })
                }
            }
        }
    }

    router.register("skills.status") { params, _ ->
        val config = gatewayConfig()
        val agentId = params["agentId"]?.jsonPrimitive?.contentOrNull
        val workspacePath = resolveWorkspace(config, agentId)
        skillsWatcher?.ensureWatching(workspacePath, config.skills)
        val report = skillLoader.buildStatus(
            workspacePath = workspacePath,
            config = config.skills,
            isConfigPathTruthy = { path -> config.isTruthyPath(path) }
        )
        buildJsonObject {
            put("workspacePath", report.workspacePath)
            put("skills", skillsJson.encodeToJsonElement(ListSerializer(SkillStatusEntry.serializer()), report.skills))
        }
    }

    router.register("skills.refresh") { params, _ ->
        val config = gatewayConfig()
        val agentId = params["agentId"]?.jsonPrimitive?.contentOrNull
        val workspacePath = resolveWorkspace(config, agentId)
        skillsWatcher?.ensureWatching(workspacePath, config.skills)
        val version = skillLoader.refresh(workspacePath)
        buildJsonObject {
            put("workspacePath", workspacePath.toString())
            put("version", version)
        }
    }

    router.register("skills.update") { params, _ ->
        val activeConfigManager = configManager ?: error("skills.update requires ConfigManager")
        val activeConfigWriter = configWriter ?: error("skills.update requires ConfigWriter")
        val currentConfig = gatewayConfig()
        val skillKey = params["skillKey"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("Missing required parameter: 'skillKey'")
        val enabled = params["enabled"]?.jsonPrimitive?.booleanOrNull
        val apiKey = params["apiKey"]?.jsonPrimitive?.contentOrNull
        val clearApiKey = params["clearApiKey"]?.jsonPrimitive?.booleanOrNull == true
        val env = params["env"]?.jsonObject?.toStringMap()
        val configValues = params["config"]?.jsonObject?.toStringMap()

        val existing = currentConfig.skills.entries[skillKey] ?: SkillEntryConfig()
        val updatedEntry = existing.copy(
            enabled = enabled ?: existing.enabled,
            apiKey = when {
                clearApiKey -> null
                params.containsKey("apiKey") -> apiKey
                else -> existing.apiKey
            },
            env = env ?: existing.env,
            config = configValues ?: existing.config
        )
        val updatedEntries = currentConfig.skills.entries.toMutableMap().apply {
            if (updatedEntry == SkillEntryConfig()) remove(skillKey) else put(skillKey, updatedEntry)
        }
        val updatedConfig = currentConfig.copy(
            skills = currentConfig.skills.copy(entries = updatedEntries)
        )

        activeConfigWriter.write(updatedConfig)
        activeConfigManager.reload()

        val reloaded = activeConfigManager.config.value
        val agentId = params["agentId"]?.jsonPrimitive?.contentOrNull
        val workspacePath = resolveWorkspace(reloaded, agentId)
        skillsWatcher?.ensureWatching(workspacePath, reloaded.skills)
        val version = skillLoader.refresh(workspacePath)
        val report = skillLoader.buildStatus(
            workspacePath = workspacePath,
            config = reloaded.skills,
            isConfigPathTruthy = { path -> reloaded.isTruthyPath(path) }
        )
        val statusEntry = report.skills.find { it.skillKey == skillKey }

        buildJsonObject {
            put("updated", true)
            put("skillKey", skillKey)
            put("workspacePath", workspacePath.toString())
            put("version", version)
            statusEntry?.let {
                put("skill", skillsJson.encodeToJsonElement(SkillStatusEntry.serializer(), it))
            }
        }
    }
}

private fun Map<String, kotlinx.serialization.json.JsonElement>.toStringMap(): Map<String, String> =
    entries.associate { (key, value) -> key to value.jsonPrimitive.content }
