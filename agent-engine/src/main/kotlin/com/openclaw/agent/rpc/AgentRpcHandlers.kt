package com.openclaw.agent.rpc

import com.openclaw.agent.isolation.AgentResolver
import com.openclaw.agent.prompt.ToolDescription
import com.openclaw.agent.runtime.RunManager
import com.openclaw.gateway.config.AgentsConfig
import com.openclaw.gateway.config.ToolsConfig
import com.openclaw.gateway.rpc.RpcRouter
import com.openclaw.gateway.ws.SessionRegistry
import com.openclaw.protocol.dto.AgentEvent
import com.openclaw.protocol.frames.Frame
import com.openclaw.protocol.identifiers.ChatType
import com.openclaw.protocol.identifiers.ModelId
import com.openclaw.protocol.identifiers.SessionKey
import com.openclaw.skills.SkillLoader
import com.openclaw.tools.ToolRegistryFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*
import java.util.concurrent.atomic.AtomicLong

private val logger = KotlinLogging.logger {}

private val json = Json { encodeDefaults = true }

/** Global event ID counter for unique Frame.Event IDs. */
private val eventSeq = AtomicLong(0)

/**
 * Register RPC handlers for agent run operations.
 *
 * Methods:
 * - `agent.run` — start an agent run for a session, streaming events via WebSocket
 * - `agent.stop` — stop an active agent run
 * - `agent.status` — get the status of an agent run
 */
fun registerAgentHandlers(
    router: RpcRouter,
    runManager: RunManager,
    agentResolver: AgentResolver,
    sessionRegistry: SessionRegistry? = null,
    agentsConfig: () -> AgentsConfig = { AgentsConfig() },
    toolRegistryFactory: ToolRegistryFactory? = null,
    toolsConfig: () -> ToolsConfig = { ToolsConfig() },
    skillLoader: SkillLoader? = null
) {
    // agent.run -- Start an agent run
    router.register("agent.run") { params, wsSessionId ->
        logger.debug { "Handling agent.run" }
        val key = params.requireString("key")
        val message = params.requireString("message")
        val modelIdStr = params["model"]?.jsonPrimitive?.contentOrNull

        val chatTypeStr = params["chatType"]?.jsonPrimitive?.contentOrNull
        val chatType = chatTypeStr?.let {
            try { ChatType.valueOf(it.uppercase()) } catch (_: IllegalArgumentException) { null }
        }

        val sessionKey = SessionKey(key)
        val resolved = agentResolver.resolve(agentsConfig(), sessionKey.agentId)

        val modelId = modelIdStr?.let { ModelId(it) } ?: resolved.modelId

        // Build per-agent tool registry using policy pipeline
        val currentToolsConfig = toolsConfig()
        val agentToolRegistry = toolRegistryFactory?.buildRegistry(
            currentToolsConfig,
            agentToolOverrides = resolved.toolOverrides
        ) ?: ai.koog.agents.core.tools.ToolRegistry.EMPTY

        // Extract tool descriptions for system prompt injection (Zone 1)
        val toolDescs = toolRegistryFactory?.buildDescriptions(
            currentToolsConfig,
            agentToolOverrides = resolved.toolOverrides
        )?.map { (name, desc) -> ToolDescription(name, desc) } ?: emptyList()

        // Build skills snapshot for this workspace
        val skillsXml = skillLoader?.let { loader ->
            val snapshot = loader.buildSnapshot(resolved.workspace.workspacePath)
            snapshot.promptXml
        } ?: ""

        runManager.startRun(
            sessionKey = sessionKey,
            message = message,
            workspace = resolved.workspace,
            modelId = modelId,
            chatType = chatType,
            toolRegistry = agentToolRegistry,
            skillsXml = skillsXml,
            toolDescriptions = toolDescs,
            onEvent = sessionRegistry?.let { registry ->
                { event: AgentEvent ->
                    val frameEvent = event.toFrameEvent(key)
                    registry.pushEvent(wsSessionId, frameEvent)
                }
            }
        )

        buildJsonObject {
            put("started", true)
            put("key", key)
            put("model", modelId.raw)
        }
    }

    // agent.stop -- Stop an active agent run
    router.register("agent.stop") { params, _ ->
        logger.debug { "Handling agent.stop" }
        val key = params.requireString("key")
        val sessionKey = SessionKey(key)

        runManager.stopRun(sessionKey)

        buildJsonObject {
            put("stopped", true)
            put("key", key)
        }
    }

    // agent.status -- Get status of an agent run
    router.register("agent.status") { params, _ ->
        logger.debug { "Handling agent.status" }
        val key = params.requireString("key")
        val sessionKey = SessionKey(key)

        val status = runManager.getStatus(sessionKey)

        buildJsonObject {
            put("key", key)
            put("isRunning", status.isRunning)
            put("elapsedMs", status.elapsedMs)
        }
    }
}

/**
 * Convert an [AgentEvent] to a [Frame.Event] for WebSocket delivery.
 */
private fun AgentEvent.toFrameEvent(sessionKey: String): Frame.Event {
    val seq = eventSeq.incrementAndGet()
    val (eventName, payload) = when (this) {
        is AgentEvent.TokenDelta -> "agent.token" to buildJsonObject {
            put("key", sessionKey)
            put("text", text)
        }
        is AgentEvent.ToolCallStart -> "agent.tool.start" to buildJsonObject {
            put("key", sessionKey)
            put("name", name)
            put("args", args)
        }
        is AgentEvent.ToolCallResult -> "agent.tool.result" to buildJsonObject {
            put("key", sessionKey)
            put("name", name)
            put("result", result)
            put("durationMs", durationMs)
        }
        is AgentEvent.RunComplete -> "agent.complete" to buildJsonObject {
            put("key", sessionKey)
            put("finalText", finalText)
            put("usage", json.encodeToJsonElement(usage))
        }
        is AgentEvent.RunError -> "agent.error" to buildJsonObject {
            put("key", sessionKey)
            put("message", message)
            code?.let { put("code", it) }
        }
    }
    return Frame.Event(id = "evt-$seq", event = eventName, payload = payload)
}

/**
 * Extract a required string parameter, throwing with a clear message if missing.
 */
private fun JsonObject.requireString(name: String): String =
    this[name]?.jsonPrimitive?.contentOrNull
        ?: throw IllegalArgumentException("Missing required parameter: '$name'")
