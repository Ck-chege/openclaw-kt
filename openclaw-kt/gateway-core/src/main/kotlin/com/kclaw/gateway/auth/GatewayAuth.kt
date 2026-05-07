@file:OptIn(ExperimentalUuidApi::class)

package com.kclaw.gateway.auth

import com.kclaw.protocol.identifiers.OperatorLevel
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID
import kotlin.text.Charsets.UTF_8
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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

    companion object {
        fun fromWire(role: String?, deviceId: String? = null): ClientRole? =
            when (role?.lowercase()) {
                "operator" -> Operator(level = OperatorLevel.WRITE)
                "node" -> Node(deviceId = deviceId ?: UUID.randomUUID().toString())
                "webchat" -> WebChat
                null -> Operator(level = OperatorLevel.WRITE)
                else -> null
            }
    }
}

/**
 * Describes an authenticated WebSocket session.
 */
data class WsSession(
    val id: String,
    val role: ClientRole,
    val scopes: Set<String>,
    val isAuthenticated: Boolean
)

fun  ClientRole.toScope() = when (this) {
    is ClientRole.Operator -> setOf("chat", "config", "agents", "tools", "admin")
    is ClientRole.Node -> setOf("chat", "agents")
    is ClientRole.WebChat -> setOf("chat")
}

/**
 * Handles authentication during the WebSocket connect handshake.
 *
 * If neither [token] nor [password] is configured, all connections are allowed
 * (open mode). Otherwise, [providedToken] must match the configured token to
 * authenticate.
 */
class GatewayAuth(private val token: String?, private val password: String?) {
    private fun constantTimeEquals(expected: String, provided: String?): Boolean {
        if (provided == null) return false
        val expectedBytes = expected.toByteArray(UTF_8)
        val providedBytes = provided.toByteArray(UTF_8)
        var diff = expectedBytes.size xor providedBytes.size
        val maxSize = maxOf(expectedBytes.size, providedBytes.size)
        for (i in 0 until maxSize) {
            val left = if (i < expectedBytes.size) expectedBytes[i].toInt() else 0
            val right = if (i < providedBytes.size) providedBytes[i].toInt() else 0
            diff = diff or (left xor right)
        }
        return diff == 0
    }

    /**
     * Authenticate a connecting client.
     *
     * @param providedToken The token sent by the client in the connect.hello frame.
     * @param clientRole The already-parsed client role.
     * @return A [WsSession] on success, or `null` if authentication fails.
     */
    fun authenticate(providedToken: String?, clientRole: ClientRole): WsSession? {
        // If no auth is configured, allow all connections
        val authRequired = token != null || password != null

        if (authRequired) {
            val valid = when {
                token != null -> constantTimeEquals(token, providedToken)
                password != null -> constantTimeEquals(password, providedToken)
                else -> false
            }
            if (!valid) {
                logger.warn { "Authentication failed for role=$clientRole" }
                return null
            }
        }


        val session = WsSession(
            id = Uuid.random().toString(),
            role = clientRole,
            scopes = clientRole.toScope(),
            isAuthenticated = authRequired
        )
        logger.info { "Authenticated session ${session.id} with role=$clientRole" }
        return session
    }
}
