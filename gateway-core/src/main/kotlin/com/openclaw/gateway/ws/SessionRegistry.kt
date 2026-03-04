package com.openclaw.gateway.ws

import com.openclaw.protocol.frames.Frame
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Maps authenticated WebSocket session IDs to their active connections.
 *
 * Used by agent run handlers to push [Frame.Event] messages to connected clients.
 * Thread-safe via [ConcurrentHashMap].
 */
class SessionRegistry {
    private val sessions = ConcurrentHashMap<String, DefaultWebSocketServerSession>()

    /**
     * Register a WebSocket session after successful authentication.
     */
    fun register(sessionId: String, wsSession: DefaultWebSocketServerSession) {
        sessions[sessionId] = wsSession
        logger.debug { "Registered WebSocket session: $sessionId (total=${sessions.size})" }
    }

    /**
     * Unregister a WebSocket session on disconnect.
     */
    fun unregister(sessionId: String) {
        sessions.remove(sessionId)
        logger.debug { "Unregistered WebSocket session: $sessionId (total=${sessions.size})" }
    }

    /**
     * Push a [Frame.Event] to a specific connected client.
     *
     * Returns true if the event was sent, false if the session was not found.
     */
    suspend fun pushEvent(sessionId: String, event: Frame.Event): Boolean {
        val wsSession = sessions[sessionId]
        if (wsSession == null) {
            logger.warn { "Cannot push event to session $sessionId — not found in registry" }
            return false
        }
        return try {
            wsSession.send(io.ktor.websocket.Frame.Text(Frame.serialize(event)))
            true
        } catch (e: Exception) {
            logger.warn(e) { "Failed to push event to session $sessionId" }
            false
        }
    }

    /**
     * Check if a session is currently connected.
     */
    fun isConnected(sessionId: String): Boolean = sessions.containsKey(sessionId)

    /**
     * Number of currently connected sessions.
     */
    fun connectedCount(): Int = sessions.size
}
