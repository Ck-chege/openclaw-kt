package com.openclaw.agent

import ai.koog.agents.core.tools.ToolRegistry
import com.openclaw.agent.isolation.AgentResolver
import com.openclaw.agent.model.ModelRegistry
import com.openclaw.agent.prompt.ToolDescription
import com.openclaw.agent.runtime.AgentRuntime
import com.openclaw.agent.runtime.CompactionTrigger
import com.openclaw.agent.runtime.RunManager
import com.openclaw.agent.runtime.RunStatus
import com.openclaw.agent.workspace.WorkspaceLoader
import com.openclaw.protocol.dto.AgentEvent
import com.openclaw.protocol.identifiers.ChatType
import com.openclaw.protocol.identifiers.ModelId
import com.openclaw.protocol.identifiers.SessionKey
import com.openclaw.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * Top-level facade for the agent engine subsystem.
 *
 * Composes:
 *   ModelRegistry → AgentRuntime → RunManager → AgentResolver
 *
 * This class is the single wiring point for the agent engine. Callers
 * (Main.kt, RPC handlers) interact only with [AgentEngine] rather than
 * assembling the pipeline manually.
 *
 * @param apiKeyResolver Config-first secret resolver — checks config secrets
 *   before falling back to env vars. Injected from [GatewayServer.buildSecretResolver].
 * @param sessionStore Optional session persistence layer. Null = stateless mode.
 * @param compactionTrigger Optional compaction trigger. Null = no compaction.
 * @param scope Coroutine scope for background run management.
 * @param timeoutMs Per-run timeout. Defaults to 10 minutes.
 */
class AgentEngine(
    apiKeyResolver: (String) -> String? = { System.getenv(it) },
    sessionStore: SessionStore? = null,
    compactionTrigger: CompactionTrigger? = null,
    scope: CoroutineScope,
    timeoutMs: Long = 600_000L
) {
    val modelRegistry: ModelRegistry = ModelRegistry(apiKeyResolver)
    val agentResolver: AgentResolver = AgentResolver()

    val runtime: AgentRuntime = AgentRuntime(
        modelRegistry = modelRegistry,
        sessionStore = sessionStore,
        compactionTrigger = compactionTrigger
    )

    val runManager: RunManager = RunManager(
        runtime = runtime,
        scope = scope,
        timeoutMs = timeoutMs
    )

    /**
     * Start an agent run — delegates to [RunManager.startRun].
     * Returns a cold [Flow] of [AgentEvent]s; the run starts on collection.
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
    ): Flow<AgentEvent> = runManager.startRun(
        sessionKey = sessionKey,
        message = message,
        workspace = workspace,
        modelId = modelId,
        channelName = channelName,
        chatType = chatType,
        toolRegistry = toolRegistry,
        skillsXml = skillsXml,
        toolDescriptions = toolDescriptions,
        onEvent = onEvent
    )

    /** Stop an active run by cancelling its coroutine. Idempotent. */
    fun stopRun(sessionKey: SessionKey) = runManager.stopRun(sessionKey)

    /** Returns current run status for a session. */
    fun getStatus(sessionKey: SessionKey): RunStatus = runManager.getStatus(sessionKey)

    /** Returns true if a run is currently active for the given session. */
    fun isRunActive(sessionKey: SessionKey): Boolean = runManager.isRunActive(sessionKey)

    /**
     * Steer an active run by cancelling it and allowing the caller to re-run
     * with the steer message appended to session history.
     *
     * Koog's strategy graph does not expose a mid-run injection point.
     * Cancel + re-run is the correct pattern for steering.
     */
    suspend fun steer(sessionKey: SessionKey, message: String): Boolean =
        runManager.steer(sessionKey, message)
}
