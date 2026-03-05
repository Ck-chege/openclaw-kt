package com.openclaw.tools.policy

/**
 * Tool classification constants matching OpenClaw's dangerous-tools.ts.
 *
 * Owner-only tools require the caller to be the gateway owner.
 * Approval-required tools may need explicit user approval before execution.
 */
object DangerousTools {
    /**
     * Tools restricted to the gateway owner (operator).
     * Non-owners cannot invoke these even if the profile allows them.
     * Matches OpenClaw's gateway HTTP deny list.
     */
    val OWNER_ONLY: Set<String> = setOf(
        "sessions_spawn",
        "sessions_send",
        "cron",
        "gateway"
    )

    /**
     * Tools that may require explicit user approval before execution.
     * The policy pipeline can return RequiresApproval for these.
     */
    val APPROVAL_REQUIRED: Set<String> = setOf(
        "exec",
        "process",
        "write",
        "edit",
        "sessions_spawn",
        "sessions_send",
        "gateway",
        "apply_patch"
    )
}
