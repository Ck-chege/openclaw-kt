package com.openclaw.tools.exec

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import com.openclaw.tools.WorkspaceBoundary
import kotlinx.serialization.Serializable
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Shell execution tool — runs a command via `/bin/sh -c` (or `cmd /c` on Windows),
 * captures combined stdout/stderr, and returns the output with an exit code.
 *
 * Supports foreground (blocking with timeout) and background modes. In background
 * mode the command is tracked via [BackgroundProcessTracker] and a session ID is
 * returned for later polling with [ProcessTool].
 *
 * Working-directory arguments are validated against [WorkspaceBoundary] to prevent
 * the LLM from executing commands in arbitrary filesystem locations.
 */
object ExecTool : SimpleTool<ExecTool.Args>(
    argsSerializer = Args.serializer(),
    name = "exec",
    description = "Execute a shell command and return stdout/stderr. Use for running scripts, installing packages, or any command-line operation."
) {
    @Serializable
    data class Args(
        @property:LLMDescription("The shell command to execute")
        val command: String,
        @property:LLMDescription("Working directory (defaults to workspace root)")
        val cwd: String? = null,
        @property:LLMDescription("Timeout in seconds (default: 1800, max: 3600)")
        val timeout: Int? = null,
        @property:LLMDescription("Run in background and return session ID for polling")
        val background: Boolean = false
    )

    var workspacePath: Path = Path.of(".")
    var backgroundTracker: BackgroundProcessTracker? = null

    private const val DEFAULT_TIMEOUT = 1800
    private const val MAX_TIMEOUT = 3600
    internal const val MAX_OUTPUT_BYTES = 100_000

    override suspend fun execute(args: Args): String {
        if (args.command.isBlank()) return "Error: command cannot be empty"

        // Resolve working directory — must stay within workspace boundary
        val workDir = if (args.cwd != null) {
            try {
                WorkspaceBoundary.resolve(args.cwd, workspacePath).toFile()
            } catch (e: WorkspaceBoundary.WorkspaceBoundaryViolation) {
                return "Error: ${e.message}"
            }
        } else {
            workspacePath.toFile()
        }

        val effectiveTimeout = (args.timeout ?: DEFAULT_TIMEOUT).coerceIn(1, MAX_TIMEOUT)

        // Build the OS process
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val processBuilder = if (isWindows) {
            ProcessBuilder("cmd", "/c", args.command)
        } else {
            ProcessBuilder("/bin/sh", "-c", args.command)
        }
        processBuilder.directory(workDir)
        processBuilder.redirectErrorStream(true)

        val process = try {
            processBuilder.start()
        } catch (e: Exception) {
            return "Error starting process: ${e.message}"
        }

        // ---- Background mode ----
        if (args.background) {
            val tracker = backgroundTracker
                ?: return "Error: background execution not available"
            val sessionId = tracker.track(args.command, process)
            return "Background process started: sessionId=$sessionId, command=${args.command}"
        }

        // ---- Foreground mode: wait with timeout, capture output ----
        val completed = process.waitFor(effectiveTimeout.toLong(), TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            return "Error: command timed out after ${effectiveTimeout}s"
        }

        val output = process.inputStream.bufferedReader().readText()
        val truncated = truncateMiddle(output, MAX_OUTPUT_BYTES)
        val exitCode = process.exitValue()

        return buildString {
            append(truncated)
            if (truncated.isNotEmpty() && !truncated.endsWith("\n")) append("\n")
            append("Exit code: $exitCode")
        }
    }

    /**
     * Truncate a string to [maxBytes] characters by keeping the first and last
     * halves and inserting an omission notice in the middle.
     */
    internal fun truncateMiddle(text: String, maxBytes: Int): String {
        if (text.length <= maxBytes) return text
        val half = maxBytes / 2
        val head = text.substring(0, half)
        val tail = text.substring(text.length - half)
        val omitted = text.length - maxBytes
        return "$head\n\n[... $omitted characters omitted ...]\n\n$tail"
    }
}
