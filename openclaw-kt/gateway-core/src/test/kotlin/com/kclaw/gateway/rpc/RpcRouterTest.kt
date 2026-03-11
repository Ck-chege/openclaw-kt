package com.kclaw.gateway.rpc

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test

class RpcRouterTest {

    @Test
    fun `register and route a handler`() = runTest {
        val router = RpcRouter()
        router.register("test.echo") { params, sessionId ->
            buildJsonObject {
                put("echo", params["input"] ?: JsonPrimitive("none"))
                put("session", JsonPrimitive(sessionId))
            }
        }

        val params = JsonObject(mapOf("input" to JsonPrimitive("hello")))
        val result = router.route("test.echo", params, "session-1")

        result["echo"] shouldBe JsonPrimitive("hello")
        result["session"] shouldBe JsonPrimitive("session-1")
    }

    @Test
    fun `hasHandler returns true for registered methods`() {
        val router = RpcRouter()
        router.register("foo.bar") { _, _ -> JsonObject(emptyMap()) }

        router.hasHandler("foo.bar") shouldBe true
        router.hasHandler("foo.baz") shouldBe false
    }

    @Test
    fun `route throws for unknown method`() = runTest {
        val router = RpcRouter()

        shouldThrow<IllegalArgumentException> {
            router.route("unknown.method", JsonObject(emptyMap()), "session-1")
        }
    }

    @Test
    fun `registeredMethods returns all registered method names`() {
        val router = RpcRouter()
        router.register("a.b") { _, _ -> JsonObject(emptyMap()) }
        router.register("c.d") { _, _ -> JsonObject(emptyMap()) }

        router.registeredMethods() shouldContainExactlyInAnyOrder setOf("a.b", "c.d")
    }

    @Test
    fun `registering same method replaces old handler`() = runTest {
        val router = RpcRouter()
        router.register("test.value") { _, _ ->
            JsonObject(mapOf("v" to JsonPrimitive("old")))
        }
        router.register("test.value") { _, _ ->
            JsonObject(mapOf("v" to JsonPrimitive("new")))
        }

        val result = router.route("test.value", JsonObject(emptyMap()), "s1")
        result["v"] shouldBe JsonPrimitive("new")
    }
}
