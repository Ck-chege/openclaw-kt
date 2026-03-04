package com.openclaw.tools.policy

/**
 * Tool groups allow referring to multiple tools by a single name
 * in allow/deny lists. Format: "group:runtime" expands to "exec", "process".
 *
 * Matches OpenClaw's ToolGroup definitions from the architecture tree.
 */
enum class ToolGroup(val tools: Set<String>) {
    RUNTIME(setOf("exec", "process")),
    FS(setOf("read", "write", "edit")),
    SESSIONS(setOf("sessions_list", "sessions_history", "sessions_send", "sessions_spawn", "session_status")),
    OPENCLAW(setOf("cron", "gateway", "agents_list", "session_status")),
    WEB(setOf("web_search", "web_fetch"));

    companion object {
        private const val GROUP_PREFIX = "group:"

        /**
         * If [name] is a group reference (e.g. "group:runtime"), expand to individual tool names.
         * Returns null if not a group reference.
         */
        fun expand(name: String): Set<String>? {
            if (!name.startsWith(GROUP_PREFIX)) return null
            val groupName = name.removePrefix(GROUP_PREFIX)
            return entries.find { it.name.equals(groupName, ignoreCase = true) }?.tools
        }

        /**
         * Expand a list that may contain both plain tool names and group references.
         * Group references are replaced with their constituent tools.
         * Plain tool names pass through unchanged.
         */
        fun expandAll(names: List<String>): Set<String> = buildSet {
            for (name in names) {
                val expanded = expand(name)
                if (expanded != null) {
                    addAll(expanded)
                } else {
                    add(name)
                }
            }
        }
    }
}
