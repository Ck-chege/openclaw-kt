package com.openclaw.tools.policy

/**
 * Context about who is invoking a tool, used by the policy pipeline
 * for owner-only gating and provider-specific restrictions.
 */
data class CallerContext(
    /** Whether the caller is the gateway owner (operator). */
    val isOwner: Boolean = false,
    /** Whether this is a cron-triggered invocation. */
    val isCron: Boolean = false,
    /** The agent ID making the tool call. */
    val agentId: String = "default",
    /** The model provider (e.g. "anthropic", "openai") for provider-specific restrictions. */
    val provider: String? = null
)
