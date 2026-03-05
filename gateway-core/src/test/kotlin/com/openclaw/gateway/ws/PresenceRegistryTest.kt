package com.openclaw.gateway.ws

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PresenceRegistryTest {

    @Test
    fun `register and snapshot returns presence`() {
        val registry = PresenceRegistry()
        val presence = ClientPresence(
            connId = "conn-1",
            role = "operator",
            scopes = setOf("admin"),
            ip = "127.0.0.1",
            platform = "web"
        )
        registry.register("conn-1", presence)
        registry.count() shouldBe 1
        registry.snapshot().first().connId shouldBe "conn-1"
    }

    @Test
    fun `unregister removes presence`() {
        val registry = PresenceRegistry()
        val presence = ClientPresence(
            connId = "conn-2",
            role = "operator",
            scopes = setOf("read"),
            ip = null,
            platform = null
        )
        registry.register("conn-2", presence)
        registry.count() shouldBe 1

        registry.unregister("conn-2")
        registry.count() shouldBe 0
        registry.snapshot() shouldBe emptyList()
    }

    @Test
    fun `presence StateFlow reflects current state`() {
        val registry = PresenceRegistry()
        registry.presence.value shouldBe emptyMap()

        val p = ClientPresence("c1", "op", setOf("write"), "10.0.0.1", "cli")
        registry.register("c1", p)
        registry.presence.value.size shouldBe 1
        registry.presence.value["c1"]?.role shouldBe "op"
    }
}
