package com.openclaw.gateway.rpc

import com.openclaw.protocol.identifiers.OperatorLevel

/**
 * Maps every known gateway method to its required [OperatorLevel].
 * Methods not in this registry require [OperatorLevel.ADMIN] (default-deny).
 *
 * WRITE implies READ (callers with WRITE can call READ-scoped methods).
 * ADMIN implies everything.
 */
object MethodScopeRegistry {

    private val registry: Map<String, OperatorLevel> = buildMap {
        // READ-scoped methods
        listOf(
            "health", "status", "config.get", "models.list",
            "channels.status", "sessions.list", "sessions.get", "sessions.history",
            "setup.status", "agents.list", "skills.list", "skills.status"
        ).forEach { put(it, OperatorLevel.READ) }

        // WRITE-scoped methods
        listOf(
            "chat.send", "chat.abort", "sessions.send",
            "wake", "agent.run", "setup.configure", "skills.refresh", "skills.update"
        ).forEach { put(it, OperatorLevel.WRITE) }

        // APPROVALS-scoped methods
        listOf(
            "exec.approval.request", "exec.approval.waitDecision", "exec.approval.resolve"
        ).forEach { put(it, OperatorLevel.APPROVALS) }

        // ADMIN-scoped methods (explicit — same as default-deny, but self-documenting)
        listOf(
            "config.apply", "config.patch",
            "sessions.delete", "sessions.reset", "sessions.compact",
            "cron.add", "cron.update", "cron.remove", "cron.run",
            "agents.create", "agents.update", "agents.delete",
            "setup.complete"
        ).forEach { put(it, OperatorLevel.ADMIN) }
    }

    /** Required scope for [method]. Unknown methods require ADMIN (default-deny). */
    fun requiredScope(method: String): OperatorLevel =
        registry[method] ?: OperatorLevel.ADMIN

    /** Methods accessible with the "chat" scope (WebChat clients). */
    private val chatScopeMethods = setOf(
        "health", "status", "chat.send", "chat.abort",
        "sessions.list", "sessions.get", "sessions.history",
        "models.list"
    )

    /**
     * Returns true if [clientScopes] grants permission for [method].
     *
     * ADMIN implies all.
     * WRITE implies READ.
     * "chat" scope grants access to a limited set of chat-related methods (WebChat clients).
     */
    fun isAuthorized(method: String, clientScopes: Set<String>): Boolean {
        if (clientScopes.contains("admin") || clientScopes.contains(OperatorLevel.ADMIN.name.lowercase())) return true

        // "chat" scope: allow a curated subset of methods for WebChat clients
        if (clientScopes.contains("chat") && method in chatScopeMethods) return true

        val required = requiredScope(method)
        return when (required) {
            OperatorLevel.READ -> clientScopes.any { it in setOf("read", "write") }
            OperatorLevel.WRITE -> clientScopes.contains("write")
            OperatorLevel.APPROVALS -> clientScopes.contains("approvals")
            OperatorLevel.ADMIN -> false
        }
    }
}
