package com.openclaw.agent.prompt

import kotlinx.datetime.toLocalDateTime

object PromptAssembler {

    // ── Shared constants ──────────────────────────────────────────────────────

    private val SAFETY_RULES = """
        You have no independent goals: do not pursue self-preservation, replication, resource acquisition, or power-seeking; avoid long-term plans beyond the user's request.
        Prioritize safety and human oversight over completion; if instructions conflict, pause and ask; comply with stop/pause/audit requests and never bypass safeguards.
        Do not manipulate or persuade anyone to expand access or disable safeguards. Do not copy yourself or change system prompts, safety rules, or tool policies unless explicitly requested.
    """.trimIndent()

    private val TOOL_CALL_STYLE = """
        Default: do not narrate routine, low-risk tool calls (just call the tool).
        Narrate only when it helps: multi-step work, complex/challenging problems, sensitive actions (e.g., deletions), or when the user explicitly asks.
        Keep narration brief and value-dense; avoid repeating obvious steps.
        Use plain human language for narration unless in a technical context.
        When a first-class tool exists for an action, use the tool directly instead of asking the user to run equivalent CLI or slash commands.
    """.trimIndent()

    private val MEMORY_RECALL = """
        Before answering anything about prior work, decisions, dates, people, preferences, or todos: run memory_search on MEMORY.md + memory/*.md; then use memory_get to pull only the needed lines. If low confidence after search, say you checked.
        Citations: include Source: <path#line> when it helps the user verify memory snippets.
    """.trimIndent()

    private val SILENT_REPLIES = """
        ## Silent Replies
        When you have nothing to say, respond with ONLY: NO_REPLY

        ⚠️ Rules:
        - It must be your ENTIRE message — nothing else
        - Never append it to an actual response (never include "NO_REPLY" in real replies)
        - Never wrap it in markdown or code blocks

        ❌ Wrong: "Here's help... NO_REPLY"
        ❌ Wrong: "NO_REPLY"
        ✅ Right: NO_REPLY
    """.trimIndent()

    private val HEARTBEATS = """
        ## Heartbeats
        Heartbeat prompt: (configured)
        If you receive a heartbeat poll (a user message matching the heartbeat prompt above), and there is nothing that needs attention, reply exactly:
        HEARTBEAT_OK
        OpenClaw treats a leading/trailing "HEARTBEAT_OK" as a heartbeat ack (and may discard it).
        If something needs attention, do NOT include "HEARTBEAT_OK"; reply with the alert text instead.
    """.trimIndent()

    // ── Prompt limits ─────────────────────────────────────────────────────────

    /**
     * Apply per-file character limit to each workspace file individually.
     * Called before [enforceWorkspaceTotalCap] so no single file dominates
     * the context window even when the aggregate cap isn't hit.
     */
    internal fun enforcePerFileLimits(ctx: PromptContext): PromptContext {
        val limit = ctx.limits.perFileCharLimit
        fun cap(s: String) =
            if (s.length > limit) TextTruncation.truncateWithMarkers(s, limit) else s
        return ctx.copy(
            soul      = cap(ctx.soul),
            agents    = cap(ctx.agents),
            user      = cap(ctx.user),
            tools     = cap(ctx.tools),
            memory    = cap(ctx.memory),
            identity  = cap(ctx.identity),
            bootstrap = cap(ctx.bootstrap),
            heartbeat = cap(ctx.heartbeat)
        )
    }

    /**
     * Enforce the workspace total char limit by truncating the largest files first.
     */
    internal fun enforceWorkspaceTotalCap(ctx: PromptContext): PromptContext {
        val limit = ctx.limits.workspaceTotalCharLimit
        val fields = mutableListOf(
            "soul"      to ctx.soul,
            "agents"    to ctx.agents,
            "user"      to ctx.user,
            "tools"     to ctx.tools,
            "memory"    to ctx.memory,
            "identity"  to ctx.identity,
            "bootstrap" to ctx.bootstrap,
            "heartbeat" to ctx.heartbeat
        )

        var total = fields.sumOf { it.second.length }
        if (total <= limit) return ctx

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
            soul      = map["soul"]      ?: ctx.soul,
            agents    = map["agents"]    ?: ctx.agents,
            user      = map["user"]      ?: ctx.user,
            tools     = map["tools"]     ?: ctx.tools,
            memory    = map["memory"]    ?: ctx.memory,
            identity  = map["identity"]  ?: ctx.identity,
            bootstrap = map["bootstrap"] ?: ctx.bootstrap,
            heartbeat = map["heartbeat"] ?: ctx.heartbeat
        )
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    fun assembleSystemPrompt(ctx: PromptContext): String {
        val perFileCapped = enforcePerFileLimits(ctx)
        val capped = enforceWorkspaceTotalCap(perFileCapped)
        return when (capped.promptMode) {
            PromptMode.FULL    -> assembleFullPrompt(capped)
            PromptMode.MINIMAL -> assembleMinimalPrompt(capped)
            PromptMode.NONE    -> assembleNonePrompt(capped)
        }
    }

    // ── Full prompt ───────────────────────────────────────────────────────────

    /**
     * Full prompt: all system instructions + all workspace files + runtime.
     * Mirrors the JS `buildAgentSystemPrompt` "full" mode structure.
     *
     * Sequence:
     *   Opener → Tooling → Tool Call Style → Safety → Skills → Memory Recall
     *   → Workspace path → Authorized Senders → Date & Time
     *   → Workspace Files heading
     *   → # Project Context (workspace file contents with SOUL.md embody instruction)
     *   → Silent Replies → Heartbeats → Runtime
     */
    private fun assembleFullPrompt(ctx: PromptContext): String = systemPrompt {

        // ── Opener ────────────────────────────────────────────────────────────
        raw("You are a personal assistant running inside OpenClaw.")

        // ── Tooling ───────────────────────────────────────────────────────────
        if (ctx.toolDescriptions.isNotEmpty()) {
            section("Tooling") {
                buildString {
                    appendLine("Tool availability (filtered by policy):")
                    appendLine("Tool names are case-sensitive. Call tools exactly as listed.")
                    ctx.toolDescriptions.forEach { tool ->
                        appendLine("- ${tool.name}: ${tool.description}")
                    }
                    append("TOOLS.md does not control tool availability; it is user guidance for how to use external tools.")
                }
            }
        }

        // ── Tool Call Style ───────────────────────────────────────────────────
        section("Tool Call Style") { TOOL_CALL_STYLE }

        // ── Safety ────────────────────────────────────────────────────────────
        section("Safety") { SAFETY_RULES }

        // ── Skills ────────────────────────────────────────────────────────────
        if (ctx.skillsXml.isNotBlank()) {
            section("Skills (mandatory)") {
                buildString {
                    appendLine("Before replying: scan <available_skills> <description> entries.")
                    appendLine("- If exactly one skill clearly applies: read its SKILL.md at <location> with `read`, then follow it.")
                    appendLine("- If multiple could apply: choose the most specific one, then read/follow it.")
                    appendLine("- If none clearly apply: do not read any SKILL.md.")
                    appendLine("Constraints: never read more than one skill up front; only read after selecting.")
                    append(ctx.skillsXml)
                }
            }
        }

        // ── Memory Recall (if memory tools are available) ─────────────────────
        val hasMemoryTools = ctx.toolDescriptions.any {
            it.name == "memory_search" || it.name == "memory_get"
        }
        if (hasMemoryTools) {
            section("Memory Recall") { MEMORY_RECALL }
        }

        // ── Workspace ─────────────────────────────────────────────────────────
        if (ctx.workspacePath.isNotBlank()) {
            section("Workspace") {
                buildString {
                    appendLine("Your working directory is: ${ctx.workspacePath}")
                    append("Treat this directory as the single global workspace for file operations unless explicitly instructed otherwise.")
                }
            }
        }

        // ── Authorized Senders ────────────────────────────────────────────────
        if (ctx.authorizedSender != null) {
            section("Authorized Senders") {
                "Authorized senders: ${ctx.authorizedSender}. These senders are allowlisted; do not assume they are the owner."
            }
        }

        // ── Date & Time ───────────────────────────────────────────────────────
        section("Current Date & Time") {
            "Time zone: ${ctx.timeZone.id}"
        }

        // ── Workspace Files heading ───────────────────────────────────────────
        raw("## Workspace Files (injected)\nThese user-editable files are loaded by OpenClaw and included below in Project Context.")

        // ── Project Context (workspace file contents) ─────────────────────────
        // SOUL.md gets a special "embody" instruction — this is the key framing
        // that prevents generic robot behaviour. Without it, the model reads
        // SOUL.md as reference material rather than a persona to inhabit.
        raw(buildProjectContext(ctx, isMinimal = false))

        // ── Silent Replies ────────────────────────────────────────────────────
        raw(SILENT_REPLIES)

        // ── Heartbeats ────────────────────────────────────────────────────────
        raw(HEARTBEATS)

        // ── Runtime ───────────────────────────────────────────────────────────
        section("Runtime") { buildRuntimeLine(ctx) }
    }

    // ── Minimal prompt ────────────────────────────────────────────────────────

    /**
     * Minimal prompt: system instructions + minimal workspace files.
     * Used for subagent, cron, and heartbeat sessions.
     * No MEMORY.md, no Silent Replies/Heartbeat sections, no "embody" framing.
     */
    private fun assembleMinimalPrompt(ctx: PromptContext): String = systemPrompt {

        raw("You are a personal assistant running inside OpenClaw.")

        if (ctx.toolDescriptions.isNotEmpty()) {
            section("Tooling") {
                buildString {
                    appendLine("Tool availability (filtered by policy):")
                    appendLine("Tool names are case-sensitive. Call tools exactly as listed.")
                    ctx.toolDescriptions.forEach { tool ->
                        appendLine("- ${tool.name}: ${tool.description}")
                    }
                    append("TOOLS.md does not control tool availability; it is user guidance for how to use external tools.")
                }
            }
        }

        section("Tool Call Style") { TOOL_CALL_STYLE }
        section("Safety") { SAFETY_RULES }

        if (ctx.skillsXml.isNotBlank()) {
            section("Skills (mandatory)") {
                buildString {
                    appendLine("Before replying: scan <available_skills> <description> entries.")
                    appendLine("- If exactly one skill clearly applies: read its SKILL.md at <location> with `read`, then follow it.")
                    appendLine("- If multiple could apply: choose the most specific one, then read/follow it.")
                    appendLine("- If none clearly apply: do not read any SKILL.md.")
                    appendLine("Constraints: never read more than one skill up front; only read after selecting.")
                    append(ctx.skillsXml)
                }
            }
        }

        if (ctx.workspacePath.isNotBlank()) {
            section("Workspace") {
                buildString {
                    appendLine("Your working directory is: ${ctx.workspacePath}")
                    append("Treat this directory as the single global workspace for file operations unless explicitly instructed otherwise.")
                }
            }
        }

        section("Current Date & Time") {
            "Time zone: ${ctx.timeZone.id}"
        }

        raw(buildProjectContext(ctx, isMinimal = true))

        section("Runtime") { buildRuntimeLine(ctx) }
    }

    // ── None prompt ───────────────────────────────────────────────────────────

    /**
     * None prompt: identity line + tooling only.
     * Used for lightweight utility agents.
     */
    private fun assembleNonePrompt(ctx: PromptContext): String = systemPrompt {
        raw("You are a personal assistant running inside OpenClaw.")

        if (ctx.toolDescriptions.isNotEmpty()) {
            section("Tooling") {
                ctx.toolDescriptions.joinToString("\n") { "- ${it.name}: ${it.description}" }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds the `# Project Context` block containing all workspace file contents.
     *
     * For full mode, injects the "embody its persona and tone" instruction before
     * SOUL.md — this is the critical framing that makes the agent behave like the
     * persona described in SOUL.md rather than a generic assistant.
     *
     * For minimal mode, skips MEMORY.md, HEARTBEAT.md, BOOTSTRAP.md, and the
     * embody instruction.
     */
    private fun buildProjectContext(ctx: PromptContext, isMinimal: Boolean): String {
        val sb = StringBuilder()
        sb.appendLine("# Project Context")
        sb.appendLine("The following project context files have been loaded:")

        if (!isMinimal && ctx.soul.isNotBlank()) {
            sb.appendLine(
                "If SOUL.md is present, embody its persona and tone. " +
                "Avoid stiff, generic replies; follow its guidance unless higher-priority instructions override it."
            )
        }

        sb.appendLine()

        // SOUL.md first — sets personality before anything else
        if (ctx.soul.isNotBlank()) {
            sb.appendLine("## SOUL.md\n\n${ctx.soul}")
            sb.appendLine()
        }

        if (ctx.agents.isNotBlank()) {
            sb.appendLine("## AGENTS.md\n\n${ctx.agents}")
            sb.appendLine()
        }

        if (ctx.tools.isNotBlank()) {
            sb.appendLine("## TOOLS.md\n\n${ctx.tools}")
            sb.appendLine()
        }

        if (ctx.identity.isNotBlank()) {
            sb.appendLine("## IDENTITY.md\n\n${ctx.identity}")
            sb.appendLine()
        }

        if (ctx.user.isNotBlank()) {
            sb.appendLine("## USER.md\n\n${ctx.user}")
            sb.appendLine()
        }

        if (!isMinimal) {
            if (ctx.heartbeat.isNotBlank()) {
                sb.appendLine("## HEARTBEAT.md\n\n${ctx.heartbeat}")
                sb.appendLine()
            }

            if (ctx.bootstrap.isNotBlank()) {
                sb.appendLine("## BOOTSTRAP.md\n\n${ctx.bootstrap}")
                sb.appendLine()
            }

            // MEMORY.md excluded in group chats (security boundary)
            if (ctx.sessionType != SessionType.GROUP && ctx.memory.isNotBlank()) {
                sb.appendLine("## MEMORY.md\n\n${ctx.memory}")
                sb.appendLine()
            }
        }

        return sb.toString().trimEnd()
    }

    /**
     * Builds a compact runtime info line matching the JS format:
     * `model=... | channel=... | os=... | thinking=off`
     */
    private fun buildRuntimeLine(ctx: PromptContext): String {
        val parts = mutableListOf<String>()
        ctx.modelId?.let { parts += "model=${it.raw}" }
        ctx.channelName?.let { parts += "channel=$it" }
        if (ctx.osInfo.isNotBlank()) parts += "os=${ctx.osInfo}"
        parts += "thinking=off"
        return parts.joinToString(" | ")
    }
}
