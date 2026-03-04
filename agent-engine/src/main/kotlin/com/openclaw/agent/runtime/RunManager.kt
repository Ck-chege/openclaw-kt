package com.openclaw.agent.runtime

import ai.koog.agents.core.tools.ToolRegistry
import com.openclaw.agent.prompt.ToolDescription
import com.openclaw.agent.workspace.WorkspaceLoader
import com.openclaw.protocol.identifiers.ChatType
import com.openclaw.protocol.dto.AgentEvent
import com.openclaw.protocol.dto.QueueAction
import com.openclaw.protocol.identifiers.ModelId
import com.openclaw.protocol.identifiers.SessionKey
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Manages agent runs with per-session serialization and timeout control.
 *
 * Only one run per session at a time — concurrent requests on the same session
 * wait for the mutex (serialized execution).
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
        onEvent: (suspend (AgentEvent) -> Unit)? = null
    ): Flow<AgentEvent> {
        val mutex = sessionMutexes.getOrPut(sessionKey.raw) { Mutex() }
        val queueChannel = Channel<QueueAction>(Channel.BUFFERED)

        val events = runtime.executeRun(sessionKey, message, workspace, modelId, channelName, chatType, toolRegistry, skillsXml, toolDescriptions)

        val job = scope.launch {
            mutex.withLock {
                logger.info { "Acquired mutex for session: ${sessionKey.raw}" }
                try {
                    withTimeoutOrNull(timeoutMs) {
                        events.collect { event ->
                            onEvent?.invoke(event)
                        }
                    } ?: run {
                        logger.warn { "Run timed out for session: ${sessionKey.raw}" }
                    }
                } finally {
                    activeRuns.remove(sessionKey.raw)
                    logger.info { "Released mutex for session: ${sessionKey.raw}" }
                }
            }
        }

        val runSession = RunSession(
            sessionKey = sessionKey,
            job = job,
            events = events,
            queueChannel = queueChannel,
            startTimeMs = System.currentTimeMillis()
        )
        activeRuns[sessionKey.raw] = runSession

        return events
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
            run.queueChannel.close()
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
     * Send a steer message to an active run's queue channel.
     * Returns true if the message was successfully enqueued, false if the session
     * is not active or the channel is closed.
     */
    suspend fun steer(sessionKey: SessionKey, message: String): Boolean {
        val run = activeRuns[sessionKey.raw] ?: return false
        if (!run.job.isActive) return false
        return try {
            val envelope = com.openclaw.protocol.dto.InboundEnvelope(
                channel = "internal",
                senderId = "system",
                senderName = "System",
                chatId = sessionKey.raw,
                chatType = com.openclaw.protocol.identifiers.ChatType.DM,
                text = message
            )
            run.queueChannel.send(QueueAction.Steer(envelope))
            true
        } catch (e: Exception) {
            logger.warn(e) { "Failed to steer session: ${sessionKey.raw}" }
            false
        }
    }
}
