package com.openclaw.agent.prompt

import kotlinx.datetime.toLocalDateTime

object PromptAssembler {

    /**
     * Enforce the workspace total char limit by truncating the largest files first.
     * Returns a new PromptContext with truncated workspace fields.
     */
    internal fun enforceWorkspaceTotalCap(ctx: PromptContext): PromptContext {
        val limit = ctx.limits.workspaceTotalCharLimit
        val fields = mutableListOf(
            "soul" to ctx.soul,
            "agents" to ctx.agents,
            "user" to ctx.user,
            "tools" to ctx.tools,
            "memory" to ctx.memory,
            "identity" to ctx.identity,
            "bootstrap" to ctx.bootstrap,
            "heartbeat" to ctx.heartbeat
        )

        var total = fields.sumOf { it.second.length }
        if (total <= limit) return ctx

        // Sort by size descending and truncate largest first
        val sorted = fields.sortedByDescending { it.second.length }.toMutableList()
        for (i in sorted.indices) {
            if (total <= limit) break
            val (name, content) = sorted[i]
            if (content.isEmpty()) continue
            val excess = total - limit
            val newLen = (content.length - excess).coerceAtLeast(200)
            if (newLen < content.length) {
                val truncated = TextTruncation.truncateWithMarkers(content, newLen)
                total -= (content.length - truncated.length)
                sorted[i] = name to truncated
            }
        }

        val map = sorted.associate { it }
        return ctx.copy(
            soul = map["soul"] ?: ctx.soul,
            agents = map["agents"] ?: ctx.agents,
            user = map["user"] ?: ctx.user,
            tools = map["tools"] ?: ctx.tools,
            memory = map["memory"] ?: ctx.memory,
            identity = map["identity"] ?: ctx.identity,
            bootstrap = map["bootstrap"] ?: ctx.bootstrap,
            heartbeat = map["heartbeat"] ?: ctx.heartbeat
        )
    }

    private val SAFETY_RULES = """
        - You exist to help your human. Prioritize their stated goals.
        - Never seek to acquire resources, influence, or capabilities beyond what's needed for the current task.
        - If uncertain whether an action is safe, ask before proceeding.
        - Respect human oversight — don't circumvent approval mechanisms.
        - Private data stays private. Never share content from MEMORY.md or USER.md in group chats.
        - If asked to do something harmful, unethical, or illegal, explain your concerns clearly.
        - When tools fail, report the error honestly rather than silently retrying.
    """.trimIndent()

    private val TOOL_CALL_STYLE = """
        Default: do not narrate routine, low-risk tool calls. Just do them.
        For destructive or irreversible operations, explain what you're about to do first.
    """.trimIndent()

    fun assembleSystemPrompt(ctx: PromptContext): String {
        val capped = enforceWorkspaceTotalCap(ctx)
        return when (capped.promptMode) {
            PromptMode.FULL -> assembleFullPrompt(capped)
            PromptMode.MINIMAL -> assembleMinimalPrompt(capped)
            PromptMode.NONE -> assembleNonePrompt(capped)
        }
    }

    /**
     * Full prompt: all system instructions + all workspace files + runtime.
     * Used for main agent sessions and private DMs.
     */
    private fun assembleFullPrompt(ctx: PromptContext): String = systemPrompt {
        // ── ZONE 1: System Instructions ──

        raw("You are a personal AI assistant.")

        if (ctx.toolDescriptions.isNotEmpty()) {
            section("AVAILABLE TOOLS") {
                ctx.toolDescriptions.joinToString("\n") { tool ->
                    "- **${tool.name}** — ${tool.description}"
                }
            }
            raw(TOOL_CALL_STYLE)
        }

        section("SAFETY") { SAFETY_RULES }

        if (ctx.skillsXml.isNotBlank()) {
            raw(ctx.skillsXml)
        }

        if (ctx.authorizedSender != null) {
            section("AUTHORIZED SENDERS") { "Owner: ${ctx.authorizedSender}" }
        }

        section("DATE & TIME") {
            val localTime = ctx.currentTime.toLocalDateTime(ctx.timeZone)
            buildString {
                appendLine("Date: ${localTime.date}")
                appendLine("Time: ${localTime.time}")
                append("Timezone: ${ctx.timeZone.id}")
            }
        }

        // ── ZONE 2: Workspace Files ──

        raw("---")
        raw("# Workspace\nThe following files are from your workspace. You can read and modify them with your tools.")

        section("AGENTS.md") { ctx.agents }
        section("SOUL.md") { ctx.soul }
        section("TOOLS.md") { ctx.tools }
        section("IDENTITY.md") { ctx.identity }
        section("USER.md") { ctx.user }

        if (ctx.heartbeat.isNotBlank()) {
            section("HEARTBEAT.md") { ctx.heartbeat }
        }

        if (ctx.bootstrap.isNotBlank()) {
            section("BOOTSTRAP.md") { ctx.bootstrap }
        }

        // MEMORY.md: excluded in group chats (security boundary)
        if (ctx.sessionType != SessionType.GROUP) {
            section("MEMORY.md") { ctx.memory }
        }

        // ── ZONE 3: Runtime & Behavioral Directives ──

        if (ctx.channelName != null) {
            section("CHANNEL") {
                buildString {
                    appendLine("Channel: ${ctx.channelName}")
                    if (ctx.channelCapabilities != null) {
                        append("Capabilities: ${ctx.channelCapabilities}")
                    }
                }.trim()
            }
        }

        section("RUNTIME") {
            val localTime = ctx.currentTime.toLocalDateTime(ctx.timeZone)
            buildString {
                if (ctx.modelId != null) {
                    appendLine("Model: ${ctx.modelId.raw}")
                }
                if (ctx.channelName != null) {
                    appendLine("Channel: ${ctx.channelName}")
                }
                append("OS: ${ctx.osInfo}")
            }
        }
    }

    /**
     * Minimal prompt: system instructions + minimal workspace files.
     * Used for subagent, cron, and heartbeat sessions.
     */
    private fun assembleMinimalPrompt(ctx: PromptContext): String = systemPrompt {
        // ── ZONE 1: System Instructions (same as full) ──

        raw("You are a personal AI assistant.")

        if (ctx.toolDescriptions.isNotEmpty()) {
            section("AVAILABLE TOOLS") {
                ctx.toolDescriptions.joinToString("\n") { tool ->
                    "- **${tool.name}** — ${tool.description}"
                }
            }
            raw(TOOL_CALL_STYLE)
        }

        section("SAFETY") { SAFETY_RULES }

        if (ctx.skillsXml.isNotBlank()) {
            raw(ctx.skillsXml)
        }

        section("DATE & TIME") {
            val localTime = ctx.currentTime.toLocalDateTime(ctx.timeZone)
            buildString {
                appendLine("Date: ${localTime.date}")
                appendLine("Time: ${localTime.time}")
                append("Timezone: ${ctx.timeZone.id}")
            }
        }

        // ── ZONE 2: Minimal Workspace Files ──

        raw("---")
        raw("# Workspace")

        section("AGENTS.md") { ctx.agents }
        section("SOUL.md") { ctx.soul }
        section("TOOLS.md") { ctx.tools }
        section("IDENTITY.md") { ctx.identity }
        section("USER.md") { ctx.user }

        // No HEARTBEAT, BOOTSTRAP, or MEMORY in minimal mode

        // ── ZONE 3: Runtime ──

        section("RUNTIME") {
            buildString {
                if (ctx.modelId != null) {
                    appendLine("Model: ${ctx.modelId.raw}")
                }
                append("OS: ${ctx.osInfo}")
            }
        }
    }

    /**
     * None prompt: identity line + tooling only.
     * Used for lightweight utility agents.
     */
    private fun assembleNonePrompt(ctx: PromptContext): String = systemPrompt {
        raw("You are a personal AI assistant.")

        if (ctx.toolDescriptions.isNotEmpty()) {
            section("AVAILABLE TOOLS") {
                ctx.toolDescriptions.joinToString("\n") { tool ->
                    "- **${tool.name}** — ${tool.description}"
                }
            }
        }
    }
}
