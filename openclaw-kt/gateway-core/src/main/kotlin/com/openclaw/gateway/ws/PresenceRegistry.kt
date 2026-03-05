package com.openclaw.gateway.ws

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class ClientPresence(
    val connId: String,
    val role: String,
    val scopes: Set<String>,
    val ip: String?,
    val platform: String?,
    val connectedAt: Instant = Clock.System.now()
)

/**
 * Tracks presence of all connected WebSocket clients.
 *
 * Backed by an immutable map in a [StateFlow] — any observer is notified on every
 * connect/disconnect without polling. [update] is atomic (lock-free CAS).
 */
class PresenceRegistry {
    private val _presence = MutableStateFlow<Map<String, ClientPresence>>(emptyMap())
    val presence: StateFlow<Map<String, ClientPresence>> = _presence.asStateFlow()

    fun register(connId: String, presence: ClientPresence) {
        _presence.update { it + (connId to presence) }
    }

    fun unregister(connId: String) {
        _presence.update { it - connId }
    }

    fun snapshot(): List<ClientPresence> = _presence.value.values.toList()
    fun count(): Int = _presence.value.size
}
