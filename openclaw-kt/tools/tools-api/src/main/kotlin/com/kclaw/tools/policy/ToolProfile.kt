package com.kclaw.tools.policy

/**
 * Predefined tool profiles controlling which tools are available.
 * Matches KClaw's tools.profile configuration.
 *
 * Each profile defines a base allowlist. The policy pipeline narrows
 * from this starting point — later stages can only remove tools, never add.
 */
enum class ToolProfile(val allowedTools: Set<String>) {
    /** All tools available — no restrictions from profile stage. */
    FULL(emptySet()), // empty means "allow all"

    /** Safe tools only — excludes exec/process (shell execution). */
    SAFE(setOf(
        "read", "write", "edit",
        "sessions_list", "sessions_history", "session_status",
        "agents_list", "memory_search", "memory_get",
        "web_search", "web_fetch"
    )),

    /** Read-only tools — no writes, no execution. */
    MINIMAL(setOf(
        "read",
        "sessions_list", "sessions_history", "session_status",
        "agents_list", "memory_search", "memory_get"
    )),

    /** No tools available. */
    NONE(emptySet());

    /** Whether this profile allows all tools (FULL). FULL uses empty set as "allow all" sentinel. */
    val allowsAll: Boolean get() = this == FULL

    companion object {
        fun fromString(value: String): ToolProfile =
            entries.find { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException(
                    "Unknown tool profile '$value'. Valid profiles: ${entries.joinToString { it.name.lowercase() }}"
                )
    }
}
