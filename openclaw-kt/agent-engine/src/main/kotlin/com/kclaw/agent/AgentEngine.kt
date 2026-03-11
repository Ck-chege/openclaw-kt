package com.kclaw.agent

import ai.koog.agents.core.tools.ToolRegistry
import com.kclaw.agent.isolation.AgentResolver
import com.kclaw.agent.model.ModelRegistry
import com.kclaw.agent.prompt.ToolDescription
import com.kclaw.agent.runtime.AgentRuntime
import com.kclaw.agent.runtime.CompactionTrigger
import com.kclaw.agent.runtime.RunManager
import com.kclaw.agent.runtime.RunStatus
import com.kclaw.agent.workspace.WorkspaceLoader
import com.kclaw.gateway.config.GatewayConfig
import com.kclaw.gateway.config.SecretsConfig
import com.kclaw.protocol.dto.AgentEvent
import com.kclaw.protocol.identifiers.ChatType
import com.kclaw.protocol.identifiers.ModelId
import com.kclaw.protocol.identifiers.SessionKey
import com.kclaw.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

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
    configFlow: StateFlow<GatewayConfig>? = null,
    sessionStore: SessionStore? = null,
    compactionTrigger: CompactionTrigger? = null,
    scope: CoroutineScope,
    timeoutMs: Long = 600_000L
) {
    @Volatile
    private var currentSecrets: SecretsConfig? = configFlow?.value?.secrets

    val modelRegistry: ModelRegistry = ModelRegistry(
        apiKeyResolver = { envVar -> resolveConfigSecret(envVar) ?: apiKeyResolver(envVar) }
    )
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

    init {
        if (configFlow != null) {
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                var previous = normalizeSecrets(configFlow.value.secrets)
                configFlow.collect { current ->
                    currentSecrets = current.secrets
                    val next = normalizeSecrets(current.secrets)

                    if (previous.anthropicApiKey != next.anthropicApiKey) {
                        invalidateModelExecutors("anthropic")
                    }
                    if (previous.openaiApiKey != next.openaiApiKey) {
                        invalidateModelExecutors("openai")
                    }
                    previous = next
                }
            }
        }
    }

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
     * Invalidate cached provider executors.
     * Call when API key material changes (config reload, secret rotation).
     */
    fun invalidateModelExecutors(providerPrefix: String? = null) {
        modelRegistry.invalidateExecutorCache(providerPrefix)
    }

    /**
     * Steer an active run by cancelling it and allowing the caller to re-run
     * with the steer message appended to session history.
     *
     * Koog's strategy graph does not expose a mid-run injection point.
     * Cancel + re-run is the correct pattern for steering.
     */
    suspend fun steer(sessionKey: SessionKey, message: String): Boolean =
        runManager.steer(sessionKey, message)

    private fun resolveConfigSecret(envVar: String): String? =
        when (envVar) {
            "ANTHROPIC_API_KEY" -> currentSecrets?.anthropicApiKey?.takeIf { it.isNotBlank() }
            "OPENAI_API_KEY" -> currentSecrets?.openaiApiKey?.takeIf { it.isNotBlank() }
            else -> null
        }

    private fun normalizeSecrets(secrets: SecretsConfig): SecretsConfig =
        secrets.copy(
            anthropicApiKey = secrets.anthropicApiKey?.takeIf { it.isNotBlank() },
            openaiApiKey = secrets.openaiApiKey?.takeIf { it.isNotBlank() }
        )
}
