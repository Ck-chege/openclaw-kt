package com.openclaw.gateway.config

import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path
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

        // Secrets section
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
    }

    private fun quote(value: String): String =
        "\"${value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")}\""
}
