package com.kclaw.tools.exec

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

/**
 * Manages background exec sessions started by [ExecTool] with `background=true`.
 *
 * Supported actions:
 * - **list** — enumerate all tracked processes with status
 * - **poll** — get current output and status for a session
 * - **log**  — paginated log retrieval (offset + limit)
 * - **kill** — forcibly terminate a running process
 * - **clear** — remove a finished session from tracking
 */
object ProcessTool : SimpleTool<ProcessTool.Args>(
    argsSerializer = Args.serializer(),
    name = "process",
    description = "Manage background exec sessions: list running processes, poll for output, view logs, kill, or clear finished sessions."
) {
    @Serializable
    data class Args(
        @property:LLMDescription("Action: list, poll, log, kill, clear")
        val action: String,
        @property:LLMDescription("Session ID (required for poll, log, kill, clear)")
        val sessionId: String? = null,
        @property:LLMDescription("Start offset for log pagination")
        val offset: Int? = null,
        @property:LLMDescription("Max lines for log pagination (default: 200)")
        val limit: Int? = null,
        @property:LLMDescription("Milliseconds to wait for poll (max 120000)")
        val timeout: Int? = null
    )

    var tracker: BackgroundProcessTracker? = null

    override suspend fun execute(args: Args): String {
        val t = tracker ?: return "Error: process tracking not available"

        return when (args.action.lowercase()) {
            "list" -> {
                val procs = t.list()
                if (procs.isEmpty()) return "No background processes"
                procs.joinToString("\n") { p ->
                    "${p.id}  ${if (p.isAlive) "running" else "exited"}  ${p.command}"
                }
            }

            "poll" -> {
                val id = args.sessionId ?: return "Error: sessionId required for poll"
                val result = t.poll(id) ?: return "Error: unknown session ID: $id"
                // Optional wait: re-poll after sleeping
                if (result.isAlive && args.timeout != null) {
                    val waitMs = args.timeout.coerceIn(0, 120_000).toLong()
                    Thread.sleep(waitMs)
                    val updated = t.poll(id) ?: return "Error: session disappeared"
                    formatPollResult(id, updated)
                } else {
                    formatPollResult(id, result)
                }
            }

            "log" -> {
                val id = args.sessionId ?: return "Error: sessionId required for log"
                val logText = t.log(id, args.offset ?: 0, args.limit ?: 200)
                    ?: return "Error: unknown session ID: $id"
                if (logText.isBlank()) "No output yet" else logText
            }

            "kill" -> {
                val id = args.sessionId ?: return "Error: sessionId required for kill"
                if (t.kill(id)) "Killed process $id" else "Error: unknown session ID: $id"
            }

            "clear" -> {
                val id = args.sessionId ?: return "Error: sessionId required for clear"
                if (t.clear(id)) "Cleared session $id" else "Error: unknown session ID: $id"
            }

            else -> "Error: unknown action '${args.action}'. Valid: list, poll, log, kill, clear"
        }
    }

    private fun formatPollResult(id: String, result: ProcessPollResult): String = buildString {
        appendLine("Session: $id")
        appendLine("Status: ${if (result.isAlive) "running" else "exited (code: ${result.exitCode})"}")
        if (result.output.isNotBlank()) {
            appendLine("Output:")
            append(result.output)
        }
    }
}
