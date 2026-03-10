package com.openclaw.gateway.config

import io.github.oshai.kotlinlogging.KotlinLogging
import com.openclaw.skills.SkillEntryConfig
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.setPosixFilePermissions
import kotlin.io.path.writeText

private val logger = KotlinLogging.logger {}

/**
 * Writes a [GatewayConfig] as valid HOCON to the given file path.
 *
 * Only writes non-default values to keep the config file clean.
 * The output is a complete, parseable HOCON file that can be read back
 * by [ConfigManager].
 */
class ConfigWriter(private val configPath: Path) {

    /**
     * Write the given config to the config file, overwriting any existing content.
     * Only includes values that differ from [GatewayConfig] defaults.
     */
    fun write(config: GatewayConfig) {
        val hocon = renderHocon(config)
        configPath.writeText(hocon)
        // Restrict file permissions when secrets are present (owner read/write only)
        if (config.secrets != GatewayConfig().secrets) {
            try {
                configPath.setPosixFilePermissions(
                    setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
                )
            } catch (_: UnsupportedOperationException) {
                // Non-POSIX filesystem (Windows) — skip permission restriction
            }
        }
        logger.info { "Config written to $configPath" }
    }

    /**
     * Update specific fields in the current config without touching other values.
     * Reads the existing config, applies the [mutator], and writes back.
     */
    fun update(currentConfig: GatewayConfig, mutator: (GatewayConfig) -> GatewayConfig) {
        val updated = mutator(currentConfig)
        write(updated)
    }

    internal fun renderHocon(config: GatewayConfig): String = buildString {
        val defaults = GatewayConfig()

        // Gateway section
        val gw = config.gateway
        val gwDefaults = defaults.gateway
        if (gw != gwDefaults) {
            appendLine("gateway {")
            if (gw.port != gwDefaults.port) appendLine("    port = ${gw.port}")
            if (gw.bind != gwDefaults.bind) appendLine("    bind = ${quote(gw.bind)}")
            if (gw.auth != gwDefaults.auth) {
                appendLine("    auth {")
                gw.auth.token?.let { appendLine("        token = ${quote(it)}") }
                gw.auth.password?.let { appendLine("        password = ${quote(it)}") }
                appendLine("    }")
            }
            if (gw.reload != gwDefaults.reload) {
                appendLine("    reload {")
                appendLine("        mode = ${quote(gw.reload.mode.name)}")
                appendLine("    }")
            }
            appendLine("}")
            appendLine()
        }

        // Agents section
        val ag = config.agents
        val agDefaults = defaults.agents
        if (ag != agDefaults) {
            appendLine("agents {")
            if (ag.defaults != agDefaults.defaults) {
                appendLine("    defaults {")
                val d = ag.defaults
                val dd = agDefaults.defaults
                if (d.model != dd.model) appendLine("        model = ${quote(d.model)}")
                if (d.workspace != dd.workspace) appendLine("        workspace = ${quote(d.workspace)}")
                if (d.compaction != dd.compaction) appendLine("        compaction = ${quote(d.compaction)}")
                if (d.maxConcurrentRuns != dd.maxConcurrentRuns) appendLine("        maxConcurrentRuns = ${d.maxConcurrentRuns}")
                appendLine("    }")
            }
            if (ag.list.isNotEmpty()) {
                appendLine("    list = [")
                for (entry in ag.list) {
                    appendLine("        {")
                    appendLine("            id = ${quote(entry.id)}")
                    entry.model?.let { appendLine("            model = ${quote(it)}") }
                    entry.workspace?.let { appendLine("            workspace = ${quote(it)}") }
                    if (entry.channels.isNotEmpty()) appendLine("            channels = [${entry.channels.joinToString(", ") { quote(it) }}]")
                    appendLine("        }")
                }
                appendLine("    ]")
            }
            appendLine("}")
            appendLine()
        }

        // Tools section
        val tools = config.tools
        val toolsDefaults = defaults.tools
        if (tools != toolsDefaults) {
            appendLine("tools {")
            if (tools.profile != toolsDefaults.profile) appendLine("    profile = ${quote(tools.profile)}")
            if (tools.allow.isNotEmpty()) appendLine("    allow = [${tools.allow.joinToString(", ") { quote(it) }}]")
            if (tools.deny.isNotEmpty()) appendLine("    deny = [${tools.deny.joinToString(", ") { quote(it) }}]")
            if (tools.sandbox != toolsDefaults.sandbox) {
                appendLine("    sandbox {")
                appendLine("        enabled = ${tools.sandbox.enabled}")
                if (tools.sandbox.allow.isNotEmpty()) appendLine("        allow = [${tools.sandbox.allow.joinToString(", ") { quote(it) }}]")
                if (tools.sandbox.deny.isNotEmpty()) appendLine("        deny = [${tools.sandbox.deny.joinToString(", ") { quote(it) }}]")
                appendLine("    }")
            }
            if (tools.mcp.isNotEmpty()) {
                appendLine("    mcp = [")
                for (mcp in tools.mcp) {
                    appendLine("        { name = ${quote(mcp.name)}, transport = ${quote(mcp.transport)}, command = ${quote(mcp.command ?: "")}, enabled = ${mcp.enabled} }")
                }
                appendLine("    ]")
            }
            appendLine("}")
            appendLine()
        }

        // Skills section
        val skills = config.skills
        val skillsDefaults = defaults.skills
        if (skills != skillsDefaults) {
            appendLine("skills {")
            if (skills.allowBundled.isNotEmpty()) {
                appendLine("    allowBundled = [${skills.allowBundled.joinToString(", ") { quote(it) }}]")
            }
            if (skills.load != skillsDefaults.load) {
                appendLine("    load {")
                if (skills.load.extraDirs.isNotEmpty()) {
                    appendLine("        extraDirs = [${skills.load.extraDirs.joinToString(", ") { quote(it) }}]")
                }
                if (skills.load.watch != skillsDefaults.load.watch) appendLine("        watch = ${skills.load.watch}")
                if (skills.load.watchDebounceMs != skillsDefaults.load.watchDebounceMs) appendLine("        watchDebounceMs = ${skills.load.watchDebounceMs}")
                if (skills.load.maxCandidatesPerRoot != skillsDefaults.load.maxCandidatesPerRoot) appendLine("        maxCandidatesPerRoot = ${skills.load.maxCandidatesPerRoot}")
                if (skills.load.maxSkillsLoadedPerSource != skillsDefaults.load.maxSkillsLoadedPerSource) appendLine("        maxSkillsLoadedPerSource = ${skills.load.maxSkillsLoadedPerSource}")
                if (skills.load.maxSkillsInPrompt != skillsDefaults.load.maxSkillsInPrompt) appendLine("        maxSkillsInPrompt = ${skills.load.maxSkillsInPrompt}")
                if (skills.load.maxSkillsPromptChars != skillsDefaults.load.maxSkillsPromptChars) appendLine("        maxSkillsPromptChars = ${skills.load.maxSkillsPromptChars}")
                if (skills.load.maxSkillFileBytes != skillsDefaults.load.maxSkillFileBytes) appendLine("        maxSkillFileBytes = ${skills.load.maxSkillFileBytes}")
                appendLine("    }")
            }
            if (skills.install != skillsDefaults.install) {
                appendLine("    install {")
                if (skills.install.preferBrew != skillsDefaults.install.preferBrew) appendLine("        preferBrew = ${skills.install.preferBrew}")
                if (skills.install.nodeManager != skillsDefaults.install.nodeManager) appendLine("        nodeManager = ${quote(skills.install.nodeManager)}")
                appendLine("    }")
            }
            if (skills.entries.isNotEmpty()) {
                appendLine("    entries {")
                skills.entries.toSortedMap().forEach { (name, entry) ->
                    if (entry == SkillEntryConfig()) return@forEach
                    appendLine("        ${quote(name)} {")
                    entry.enabled?.let { appendLine("            enabled = $it") }
                    entry.apiKey?.let { appendLine("            apiKey = ${quote(it)}") }
                    if (entry.env.isNotEmpty()) {
                        appendLine("            env {")
                        entry.env.toSortedMap().forEach { (key, value) ->
                            appendLine("                ${quote(key)} = ${quote(value)}")
                        }
                        appendLine("            }")
                    }
                    if (entry.config.isNotEmpty()) {
                        appendLine("            config {")
                        entry.config.toSortedMap().forEach { (key, value) ->
                            appendLine("                ${quote(key)} = ${quote(value)}")
                        }
                        appendLine("            }")
                    }
                    appendLine("        }")
                }
                appendLine("    }")
            }
            appendLine("}")
            appendLine()
        }

        // Secrets section — keys are written to the config file (matches JS behavior).
        // The config file should have restrictive permissions (600) to limit exposure.
        val sec = config.secrets
        if (sec != defaults.secrets) {
            appendLine("secrets {")
            sec.anthropicApiKey?.let { appendLine("    anthropicApiKey = ${quote(it)}") }
            sec.openaiApiKey?.let { appendLine("    openaiApiKey = ${quote(it)}") }
            appendLine("}")
            appendLine()
        }

        // Memory section
        val mem = config.memory
        if (mem != defaults.memory) {
            appendLine("memory {")
            if (mem.enabled != defaults.memory.enabled) appendLine("    enabled = ${mem.enabled}")
            if (mem.provider != defaults.memory.provider) appendLine("    provider = ${quote(mem.provider)}")
            if (mem.model != defaults.memory.model) appendLine("    model = ${quote(mem.model)}")
            appendLine("}")
            appendLine()
        }

        // Cron section
        val cron = config.cron
        if (cron != defaults.cron) {
            appendLine("cron {")
            appendLine("    enabled = ${cron.enabled}")
            appendLine("}")
            appendLine()
        }

        // Persistence section
        val persistence = config.persistence
        if (persistence != defaults.persistence) {
            appendLine("persistence {")
            if (persistence.enabled != defaults.persistence.enabled) {
                appendLine("    enabled = ${persistence.enabled}")
            }
            if (persistence.sqlite != defaults.persistence.sqlite) {
                appendLine("    sqlite {")
                if (persistence.sqlite.path != defaults.persistence.sqlite.path) {
                    appendLine("        path = ${quote(persistence.sqlite.path)}")
                }
                appendLine("    }")
            }
            appendLine("}")
            appendLine()
        }
    }

    private fun quote(value: String): String =
        "\"${value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")}\""
}
