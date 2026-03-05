package com.openclaw.agent.runtime

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.streaming.StreamFrame
import com.openclaw.agent.model.ModelRegistry
import com.openclaw.agent.prompt.PromptAssembler
import com.openclaw.agent.prompt.PromptContext
import com.openclaw.agent.prompt.PromptContextDefaults
import com.openclaw.agent.prompt.ToolDescription
import com.openclaw.protocol.identifiers.ChatType
import com.openclaw.agent.strategy.StreamBridge
import com.openclaw.agent.strategy.buildOpenClawStrategy
import com.openclaw.agent.workspace.WorkspaceLoader
import com.openclaw.protocol.dto.AgentEvent
import com.openclaw.protocol.dto.TokenUsage
import com.openclaw.protocol.identifiers.ModelId
import com.openclaw.protocol.identifiers.SessionKey
import com.openclaw.session.SessionMessage
import com.openclaw.session.SessionStore
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Executes agent runs by assembling prompts, creating Koog agents,
 * and bridging events to a Flow<AgentEvent>.
 */
class AgentRuntime(
    private val modelRegistry: ModelRegistry,
    private val sessionStore: SessionStore? = null,
    private val compactionTrigger: CompactionTrigger? = null
) {
    /**
     * Execute an agent run. Returns a cold Flow — the run starts on collection.
     *
     * @param sessionKey Session identifier for this run
     * @param message User message to process
     * @param workspace Workspace loader for reading prompt files
     * @param modelId Optional model override; defaults to the workspace/config default
     * @param channelName Optional channel context
     */
    fun executeRun(
        sessionKey: SessionKey,
        message: String,
        workspace: WorkspaceLoader,
        modelId: ModelId? = null,
        channelName: String? = null,
        chatType: ChatType? = null,
        toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
        skillsXml: String = "",
        toolDescriptions: List<ToolDescription> = emptyList()
    ): Flow<AgentEvent> = channelFlow {
        val effectiveModelId = modelId ?: ModelId("anthropic:claude-sonnet-4")
        val bridge = StreamBridge()

        try {
            logger.info { "Starting agent run: session=${sessionKey.raw}, model=${effectiveModelId.raw}" }

            // 1. Ensure session exists and load conversation history
            val history = loadSessionHistory(sessionKey, channelName)

            // 2. Persist user message before running agent
            persistUserMessage(sessionKey, message, channelName)

            // 3. Assemble system prompt
            val sessionType = PromptContextDefaults.deriveSessionType(sessionKey, chatType)
            val promptMode = PromptContextDefaults.derivePromptMode(sessionType)

            val promptContext = PromptContext(
                soul = workspace.readSoulMd(),
                agents = workspace.readAgentsMd(),
                user = workspace.readUserMd(),
                tools = workspace.readToolsMd(),
                memory = workspace.readMemoryMd(),
                identity = workspace.readIdentityMd(),
                bootstrap = workspace.readBootstrapMd(),
                heartbeat = workspace.readHeartbeatMd(),
                toolDescriptions = toolDescriptions,
                skillsXml = skillsXml,
                workspacePath = workspace.workspacePath.toString(),
                promptMode = promptMode,
                sessionType = sessionType,
                channelName = channelName,
                modelId = effectiveModelId,
                currentTime = kotlinx.datetime.Instant.fromEpochMilliseconds(
                    java.lang.System.currentTimeMillis()
                )
            )
            val systemPrompt = PromptAssembler.assembleSystemPrompt(promptContext)

            // 4. Resolve executor and model
            val executor = modelRegistry.resolveExecutor(effectiveModelId)
            val definition = modelRegistry.resolve(effectiveModelId)
            val koogModel = modelRegistry.resolveKoogModel(definition)

            // 5. Build strategy and agent config with conversation history
            val strategy = buildOpenClawStrategy()
            val agentConfig = AIAgentConfig(
                prompt = prompt("openclaw-${sessionKey.raw}") {
                    system(systemPrompt)
                    // Seed prior conversation turns so the LLM has context
                    for (msg in history) {
                        when (msg) {
                            is SessionMessage.UserMessage -> user(msg.text)
                            is SessionMessage.AssistantMessage -> assistant(msg.text)
                            is SessionMessage.CompactionSummary ->
                                assistant("[Context summary]\n${msg.summary}")
                            is SessionMessage.ToolCall,
                            is SessionMessage.ToolResult,
                            is SessionMessage.SystemEvent -> { /* skip — Koog doesn't model these */ }
                        }
                    }
                },
                model = koogModel,
                maxAgentIterations = 50
            )

            // 6. Create AIAgent with event handlers
            val agent = AIAgent(
                promptExecutor = executor,
                agentConfig = agentConfig,
                strategy = strategy,
                toolRegistry = toolRegistry
            ) {
                handleEvents {
                    onLLMStreamingFrameReceived { ctx ->
                        when (val frame = ctx.streamFrame) {
                            is StreamFrame.TextDelta -> bridge.onTokenDelta(frame.text)
                            // ToolCallComplete means the LLM finished streaming the tool call spec.
                            // Do NOT emit ToolCallStart here — onToolCallStarting handles that
                            // correctly with full args. Emitting here would double-fire ToolCallStart.
                            else -> {}
                        }
                    }

                    onToolCallStarting { ctx ->
                        bridge.onToolCallStart(
                            ctx.toolName,
                            ctx.toolArgs
                        )
                    }

                    onToolCallCompleted { ctx ->
                        bridge.onToolCallResult(
                            ctx.toolName,
                            ctx.toolResult.toString(),
                            0L
                        )
                    }

                    onAgentExecutionFailed { ctx ->
                        bridge.onRunError(
                            ctx.throwable.message ?: "Unknown agent error",
                            "AGENT_ERROR"
                        )
                    }
                }
            }

            // 7. Launch bridge collector — forward events to channelFlow
            val bridgeJob = launch {
                bridge.events.onEach { event -> send(event) }.collect()
            }

            // 8. Run agent
            val result = agent.run(message)

            // 9. Persist assistant response
            persistAssistantMessage(sessionKey, result, effectiveModelId)

            // 9.5 Check compaction — run silent memory flush if needed
            compactionTrigger?.let { trigger ->
                val compactionResult = trigger.checkAndCompact(sessionKey)
                when (compactionResult) {
                    is CompactionTriggerResult.Compacted ->
                        logger.info { "Compaction completed: flushed=${compactionResult.flushed}, outcome=${compactionResult.outcome}" }
                    is CompactionTriggerResult.FlushFailed ->
                        logger.warn { "Compaction trigger failed: ${compactionResult.cause.message}" }
                    CompactionTriggerResult.NotNeeded -> {}
                }
            }

            // 10. Emit run complete
            val outputTokens = bridge.currentOutputTokens()
            val completeEvent = AgentEvent.RunComplete(
                finalText = result,
                usage = TokenUsage(
                    promptTokens = 0,        // Koog doesn't expose input tokens from agent.run()
                    completionTokens = outputTokens,
                    totalTokens = outputTokens
                )
            )
            send(completeEvent)

            bridgeJob.cancel()

        } catch (e: Exception) {
            logger.error(e) { "Agent run failed: session=${sessionKey.raw}" }
            send(AgentEvent.RunError(e.message ?: "Unknown error", "RUN_ERROR"))
        }
    }

    /**
     * Ensure session exists and load conversation history.
     * Returns empty list if no session store is configured.
     */
    private suspend fun loadSessionHistory(
        sessionKey: SessionKey,
        channelName: String?
    ): List<SessionMessage> {
        val store = sessionStore ?: return emptyList()
        try {
            // Auto-create session if it doesn't exist
            if (store.getSession(sessionKey) == null) {
                store.createSession(sessionKey, sessionKey.agentId, channelName)
                logger.info { "Auto-created session: ${sessionKey.raw}" }
                return emptyList()
            }
            val history = store.getHistory(sessionKey)
            logger.debug { "Loaded ${history.size} history messages for session: ${sessionKey.raw}" }
            return history
        } catch (e: Exception) {
            logger.warn(e) { "Failed to load session history: ${sessionKey.raw}" }
            return emptyList()
        }
    }

    /**
     * Persist user message to session store.
     */
    private suspend fun persistUserMessage(
        sessionKey: SessionKey,
        text: String,
        channelName: String?
    ) {
        val store = sessionStore ?: return
        try {
            store.appendMessage(
                sessionKey,
                SessionMessage.UserMessage(
                    id = UUID.randomUUID().toString(),
                    timestamp = Clock.System.now(),
                    senderId = "user",
                    senderName = "User",
                    text = text,
                    channel = channelName
                )
            )
        } catch (e: Exception) {
            logger.warn(e) { "Failed to persist user message: ${sessionKey.raw}" }
        }
    }

    /**
     * Persist assistant response to session store.
     */
    private suspend fun persistAssistantMessage(
        sessionKey: SessionKey,
        text: String,
        modelId: ModelId
    ) {
        val store = sessionStore ?: return
        try {
            store.appendMessage(
                sessionKey,
                SessionMessage.AssistantMessage(
                    id = UUID.randomUUID().toString(),
                    timestamp = Clock.System.now(),
                    text = text,
                    model = modelId.raw
                )
            )
        } catch (e: Exception) {
            logger.warn(e) { "Failed to persist assistant message: ${sessionKey.raw}" }
        }
    }
}
