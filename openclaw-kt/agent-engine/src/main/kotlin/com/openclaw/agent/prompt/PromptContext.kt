package com.openclaw.agent.prompt

import com.openclaw.protocol.identifiers.ChatType
import com.openclaw.protocol.identifiers.ModelId
import com.openclaw.protocol.identifiers.SessionKey
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

/**
 * Describes a tool available to the agent, for injection into the system prompt.
 */
data class ToolDescription(
    val name: String,
    val description: String
)

/**
 * Controls how much of the system prompt is assembled.
 */
enum class PromptMode {
    /** All zones, all workspace files — main agent sessions. */
    FULL,
    /** Zone 1 (tooling, safety) + minimal workspace (AGENTS, TOOLS, SOUL, IDENTITY, USER). */
    MINIMAL,
    /** Identity line + tooling only — lightweight utility agents. */
    NONE
}

/**
 * Session type determines which workspace files are injected.
 */
enum class SessionType {
    /** Main/private DM session — all files including MEMORY.md. */
    MAIN,
    /** Group chat — all files except MEMORY.md (security boundary). */
    GROUP,
    /** Subagent session — minimal workspace files. */
    SUBAGENT,
    /** Cron/heartbeat session — minimal workspace files. */
    CRON
}

/**
 * Companion with derivation functions for session type and prompt mode.
 */
object PromptContextDefaults {

    /**
     * Derive session type from session key properties and optional chat type.
     * Priority: subagent > cron > group chat > main.
     */
    fun deriveSessionType(sessionKey: SessionKey, chatType: ChatType? = null): SessionType =
        when {
            sessionKey.isSubagent -> SessionType.SUBAGENT
            sessionKey.isCron -> SessionType.CRON
            chatType == ChatType.GROUP -> SessionType.GROUP
            else -> SessionType.MAIN
        }

    /**
     * Derive prompt mode from session type.
     * Subagent/cron get minimal prompts; main/group get full.
     */
    fun derivePromptMode(sessionType: SessionType): PromptMode =
        when (sessionType) {
            SessionType.SUBAGENT, SessionType.CRON -> PromptMode.MINIMAL
            else -> PromptMode.FULL
        }
}

data class PromptContext(
    // Workspace file contents
    val soul: String = "",
    val agents: String = "",
    val user: String = "",
    val tools: String = "",
    val memory: String = "",
    val identity: String = "",
    val bootstrap: String = "",
    val heartbeat: String = "",

    // System instruction inputs
    val toolDescriptions: List<ToolDescription> = emptyList(),
    val authorizedSender: String? = null,
    val workspacePath: String = "",

    // Skills
    val skillsXml: String = "",

    // Session context
    val promptMode: PromptMode = PromptMode.FULL,
    val sessionType: SessionType = SessionType.MAIN,
    val channelName: String? = null,
    val channelCapabilities: String? = null,

    // Limits
    val limits: PromptLimits = PromptLimits(),

    // Runtime
    val modelId: ModelId? = null,
    val currentTime: Instant,
    val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    val osInfo: String = System.getProperty("os.name") + " " + System.getProperty("os.version")
)
