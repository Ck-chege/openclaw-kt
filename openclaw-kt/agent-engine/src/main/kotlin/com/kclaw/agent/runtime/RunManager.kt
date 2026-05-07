package com.kclaw.agent.runtime

import ai.koog.agents.core.tools.ToolRegistry
import com.kclaw.agent.prompt.ToolDescription
import com.kclaw.agent.workspace.WorkspaceLoader
import com.kclaw.protocol.identifiers.ChatType
import com.kclaw.protocol.dto.AgentEvent
import com.kclaw.protocol.identifiers.ModelId
import com.kclaw.protocol.identifiers.SessionKey
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Manages agent runs with per-session serialization and timeout control.
 *
 * Only one run per session at a time — concurrent requests on the same session
 * wait for the mutex (serialized execution).
 *
 * Events are delivered exclusively via the [onEvent] callback. The returned [Job]
 * provides a handle for tracking and cancellation; callers must NOT collect any
 * separate flow, as each collection of [AgentRuntime.executeRun] starts a new agent run.
 */
class RunManager(
    private val runtime: AgentRuntime,
    private val scope: CoroutineScope,
    private val timeoutMs: Long = 600_000L
) {
    private val sessionMutexes = ConcurrentHashMap<String, Mutex>()
    private val activeRuns = ConcurrentHashMap<String, RunSession>()

    /**
     * Start a new agent run for the given session.
     * Acquires per-session mutex to serialize concurrent run requests.
     *
     * Returns the [Job] backing the run — use it to track completion or cancel early.
     * Events are delivered via [onEvent]; do not collect the cold flow separately.
     *
     * @param onEvent Optional callback invoked for each [AgentEvent] emitted during the run.
     *                Used to forward events to WebSocket clients.
     */
    suspend fun startRun(
        sessionKey: SessionKey,
        message: String,
        workspace: WorkspaceLoader,
        modelId: ModelId? = null,
        channelName: String? = null,
        chatType: ChatType? = null,
        toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
        skillsXml: String = "",
        toolDescriptions: List<ToolDescription> = emptyList(),
        prepareRun: (() -> (() -> Unit))? = null,
        onEvent: (suspend (AgentEvent) -> Unit)? = null
    ): Job {
        val mutex = sessionMutexes.getOrPut(sessionKey.raw) { Mutex() }
        val events = runtime.executeRun(
            sessionKey, message, workspace, modelId, channelName, chatType,
            toolRegistry, skillsXml, toolDescriptions
        )

        // Register a placeholder immediately so isRunActive() returns true as soon
        // as startRun() is called — before the coroutine has a chance to launch.
        val placeholder = RunSession(
            sessionKey = sessionKey,
            job = Job(),
            startTimeMs = System.currentTimeMillis()
        )
        activeRuns[sessionKey.raw] = placeholder

        val job = scope.launch {
            mutex.withLock {
                logger.info { "Acquired mutex for session: ${sessionKey.raw}" }
                val cleanup = prepareRun?.invoke()
                try {
                    withTimeoutOrNull(timeoutMs) {
                        // Single consumer of the cold flow — onEvent drives all delivery.
                        events.collect { event ->
                            onEvent?.invoke(event)
                        }
                    } ?: run {
                        logger.warn { "Run timed out for session: ${sessionKey.raw}" }
                    }
                } finally {
                    cleanup?.invoke()
                    activeRuns.remove(sessionKey.raw)
                    logger.info { "Released mutex for session: ${sessionKey.raw}" }
                }
            }
        }

        // Replace placeholder with actual job so stopRun/steer can cancel it.
        activeRuns[sessionKey.raw] = RunSession(
            sessionKey = sessionKey,
            job = job,
            startTimeMs = placeholder.startTimeMs
        )

        return job
    }

    /**
     * Stop an active run for the given session by cancelling its job.
     * Idempotent — stopping a non-existent run does nothing.
     */
    fun stopRun(sessionKey: SessionKey) {
        val run = activeRuns.remove(sessionKey.raw)
        if (run != null) {
            logger.info { "Stopping run for session: ${sessionKey.raw}" }
            run.job.cancel()
        } else {
            logger.debug { "No active run to stop for session: ${sessionKey.raw}" }
        }
    }

    /**
     * Get the status of a run for the given session.
     */
    fun getStatus(sessionKey: SessionKey): RunStatus {
        val run = activeRuns[sessionKey.raw]
        return if (run != null) {
            RunStatus(
                sessionKey = sessionKey,
                isRunning = run.job.isActive,
                elapsedMs = System.currentTimeMillis() - run.startTimeMs
            )
        } else {
            RunStatus(
                sessionKey = sessionKey,
                isRunning = false,
                elapsedMs = 0L
            )
        }
    }

    /**
     * Check if a run is active for the given session.
     */
    fun isRunActive(sessionKey: SessionKey): Boolean =
        activeRuns[sessionKey.raw]?.job?.isActive == true

    /**
     * Steer an active run by cancelling it so the caller can re-run with the
     * steer message incorporated into session history.
     *
     * Koog's strategy graph doesn't expose a mid-run injection point, so
     * cancel + re-run is the correct pattern. The steer message should be
     * persisted by the caller (e.g. as a SessionMessage.SystemEvent) before
     * the next agent.run call so it appears in conversation history.
     *
     * Returns true if a run was active and cancelled, false if no run was found.
     */
    suspend fun steer(sessionKey: SessionKey, message: String): Boolean {
        val run = activeRuns[sessionKey.raw] ?: return false
        if (!run.job.isActive) return false
        return try {
            logger.info { "Steering session ${sessionKey.raw}: cancelling run to inject message (${message.take(80)}...)" }
            run.job.cancel()
            activeRuns.remove(sessionKey.raw)
            true
        } catch (e: Exception) {
            logger.warn(e) { "Failed to steer session: ${sessionKey.raw}" }
            false
        }
    }
}