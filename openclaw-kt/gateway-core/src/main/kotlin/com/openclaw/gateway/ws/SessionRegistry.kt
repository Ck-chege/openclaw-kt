package com.openclaw.gateway.ws

import com.openclaw.gateway.auth.WsSession
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
    private data class RegisteredSession(
        val wsSession: DefaultWebSocketServerSession,
        val authSession: WsSession
    )

    private val sessions = ConcurrentHashMap<String, RegisteredSession>()

    /**
     * Register a WebSocket session after successful authentication.
     */
    fun register(authSession: WsSession, wsSession: DefaultWebSocketServerSession) {
        sessions[authSession.id] = RegisteredSession(wsSession, authSession)
        logger.debug { "Registered WebSocket session: ${authSession.id} (total=${sessions.size})" }
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
        val registered = sessions[sessionId]
        if (registered == null) {
            logger.warn { "Cannot push event to session $sessionId — not found in registry" }
            return false
        }
        return try {
            registered.wsSession.send(io.ktor.websocket.Frame.Text(Frame.serialize(event)))
            true
        } catch (e: Exception) {
            logger.warn(e) { "Failed to push event to session $sessionId — removing from registry" }
            sessions.remove(sessionId)
            false
        }
    }

    /**
     * Check if a session is currently connected.
     */
    fun isConnected(sessionId: String): Boolean = sessions.containsKey(sessionId)

    /**
     * Return authenticated metadata for a connected session.
     */
    fun getAuthenticatedSession(sessionId: String): WsSession? = sessions[sessionId]?.authSession

    /**
     * Number of currently connected sessions.
     */
    fun connectedCount(): Int = sessions.size
}
