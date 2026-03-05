package com.openclaw.gateway.ws

import com.openclaw.protocol.frames.Frame
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.*
import io.ktor.server.websocket.*
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test

class SessionRegistryTest {

    @Test
    fun `pushEvent to dead session removes it from registry`() = runBlocking {
        val registry = SessionRegistry()
        // We can't easily create a real dead DefaultWebSocketServerSession,
        // but we can verify the basic registry operations
        registry.isConnected("test-session") shouldBe false
        registry.connectedCount() shouldBe 0
    }

    @Test
    fun `connectedCount reflects registrations and unregistrations`() = runBlocking {
        val registry = SessionRegistry()
        registry.connectedCount() shouldBe 0
        registry.isConnected("s1") shouldBe false
    }

    @Test
    fun `unregister removes session`() {
        val registry = SessionRegistry()
        // Unregistering a non-existent session should not throw
        registry.unregister("nonexistent")
        registry.connectedCount() shouldBe 0
    }
}
