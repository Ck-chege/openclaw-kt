package com.kclaw.tools.exec

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Information about a tracked background process (public read-only view).
 */
data class ProcessInfo(
    val id: String,
    val command: String,
    val isAlive: Boolean,
    val startedAt: Instant
)

/**
 * Result of polling a background process for its current state.
 */
data class ProcessPollResult(
    val output: String,
    val isAlive: Boolean,
    val exitCode: Int?
)

/**
 * Tracks background shell processes started via [ExecTool] with `background=true`.
 *
 * Each process gets a short unique session ID. Output from stdout and stderr is
 * captured into an in-memory buffer that can be polled or paged through.
 *
 * Thread-safe: uses [ConcurrentHashMap] for the process map and synchronises
 * buffer writes via the [StringBuilder] monitor (daemon collector threads).
 */
class BackgroundProcessTracker {

    internal data class TrackedProcess(
        val id: String,
        val command: String,
        val process: Process,
        val startedAt: Instant,
        val outputBuffer: StringBuilder = StringBuilder()
    ) {
        val isAlive: Boolean get() = process.isAlive
        val exitCode: Int? get() = if (!process.isAlive) process.exitValue() else null
    }

    private val processes = ConcurrentHashMap<String, TrackedProcess>()

    /**
     * Begin tracking [process] under a new session ID. Daemon threads are
     * started to collect stdout and stderr into the output buffer.
     *
     * @return the 8-character session ID
     */
    fun track(command: String, process: Process): String {
        val id = UUID.randomUUID().toString().take(8)
        val tp = TrackedProcess(
            id = id,
            command = command,
            process = process,
            startedAt = Clock.System.now()
        )
        processes[id] = tp

        // Daemon thread: collect stdout
        Thread {
            process.inputStream.bufferedReader().forEachLine { line ->
                synchronized(tp.outputBuffer) {
                    tp.outputBuffer.appendLine(line)
                }
            }
        }.apply { isDaemon = true; start() }

        // Daemon thread: collect stderr (prefixed)
        Thread {
            process.errorStream.bufferedReader().forEachLine { line ->
                synchronized(tp.outputBuffer) {
                    tp.outputBuffer.appendLine("[stderr] $line")
                }
            }
        }.apply { isDaemon = true; start() }

        return id
    }

    /**
     * List all tracked processes, sorted by start time (oldest first).
     */
    fun list(): List<ProcessInfo> = processes.values.map { tp ->
        ProcessInfo(tp.id, tp.command, tp.isAlive, tp.startedAt)
    }.sortedBy { it.startedAt }

    /**
     * Poll a process for its current output and status.
     *
     * @return null if [processId] is unknown
     */
    fun poll(processId: String): ProcessPollResult? {
        val tp = processes[processId] ?: return null
        val output = synchronized(tp.outputBuffer) { tp.outputBuffer.toString() }
        return ProcessPollResult(output, tp.isAlive, tp.exitCode)
    }

    /**
     * Retrieve a page of log lines from the process output buffer.
     *
     * @param offset number of lines to skip from the start
     * @param limit  maximum number of lines to return
     * @return null if [processId] is unknown
     */
    fun log(processId: String, offset: Int = 0, limit: Int = 200): String? {
        val tp = processes[processId] ?: return null
        val allLines = synchronized(tp.outputBuffer) { tp.outputBuffer.toString() }
        val lines = allLines.lines()
        return lines.drop(offset).take(limit).joinToString("\n")
    }

    /**
     * Forcibly kill a tracked process.
     *
     * @return false if [processId] is unknown
     */
    fun kill(processId: String): Boolean {
        val tp = processes[processId] ?: return false
        if (tp.isAlive) {
            tp.process.destroyForcibly()
        }
        return true
    }

    /**
     * Remove a process from tracking. Does **not** kill it first.
     *
     * @return false if [processId] is unknown
     */
    fun clear(processId: String): Boolean {
        return processes.remove(processId) != null
    }

    /**
     * Remove all completed processes older than [maxAgeSeconds].
     */
    fun cleanup(maxAgeSeconds: Long = 3600) {
        val now = Clock.System.now()
        processes.entries.removeIf { (_, tp) ->
            !tp.isAlive && (now - tp.startedAt).inWholeSeconds > maxAgeSeconds
        }
    }
}
