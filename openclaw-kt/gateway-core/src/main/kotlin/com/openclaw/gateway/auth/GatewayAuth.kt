package com.openclaw.gateway.auth

import com.openclaw.protocol.identifiers.OperatorLevel
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Represents the role of a connected client.
 */
sealed class ClientRole {
    /** An operator interacting directly via CLI or web UI. */
    data class Operator(val level: OperatorLevel) : ClientRole()

    /** A remote node (device) connecting as part of a distributed setup. */
    data class Node(val deviceId: String) : ClientRole()

    /** An anonymous web chat participant. */
    data object WebChat : ClientRole()
}

/**
 * Describes an authenticated WebSocket session.
 */
data class WsSession(
    val id: String,
    val role: ClientRole,
    val scopes: Set<String>
)

/**
 * Handles authentication during the WebSocket connect handshake.
 *
 * If neither [token] nor [password] is configured, all connections are allowed
 * (open mode). Otherwise, [providedToken] must match the configured token to
 * authenticate.
 */
class GatewayAuth(private val token: String?, private val password: String?) {

    /**
     * Authenticate a connecting client.
     *
     * @param providedToken The token sent by the client in the connect.hello frame.
     * @param role The requested role string: "operator", "node", or "webchat".
     * @return A [WsSession] on success, or `null` if authentication fails.
     */
    fun authenticate(providedToken: String?, role: String?, deviceId: String? = null): WsSession? {
        // If no auth is configured, allow all connections
        val authRequired = token != null || password != null

        if (authRequired) {
            val valid = when {
                token != null -> providedToken == token
                password != null -> providedToken == password
                else -> false
            }
            if (!valid) {
                logger.warn { "Authentication failed for role=$role" }
                return null
            }
        }

        val clientRole = when (role?.lowercase()) {
            "operator" -> ClientRole.Operator(level = OperatorLevel.WRITE)
            "node" -> ClientRole.Node(deviceId = deviceId ?: UUID.randomUUID().toString())
            "webchat" -> ClientRole.WebChat
            null -> ClientRole.Operator(level = OperatorLevel.WRITE)
            else -> {
                logger.warn { "Unknown role requested: $role" }
                return null
            }
        }

        val scopes = when (clientRole) {
            is ClientRole.Operator -> setOf("chat", "config", "agents", "tools", "admin")
            is ClientRole.Node -> setOf("chat", "agents")
            is ClientRole.WebChat -> setOf("chat")
        }

        val session = WsSession(
            id = UUID.randomUUID().toString(),
            role = clientRole,
            scopes = scopes
        )
        logger.info { "Authenticated session ${session.id} with role=$role" }
        return session
    }
}
