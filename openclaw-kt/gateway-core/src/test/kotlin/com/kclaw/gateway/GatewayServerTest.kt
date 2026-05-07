package com.kclaw.gateway

import com.kclaw.gateway.config.AuthConfig
import com.kclaw.gateway.config.GatewayConfig
import com.kclaw.gateway.config.GatewaySettings
import com.kclaw.gateway.config.SecretsConfig
import com.kclaw.gateway.ws.SetupStatus
import com.kclaw.protocol.frames.Frame
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class GatewayServerTest {

    private fun createTestServer(
        token: String? = null,
        password: String? = null,
        setupStatus: SetupStatus = SetupStatus(false, "ONBOARDING_COMPLETE")
    ): GatewayServer {
        val config = GatewayConfig(
            gateway = GatewaySettings(
                auth = AuthConfig(token = token, password = password)
            )
        )
        return GatewayServer(config, setupStatusProvider = { setupStatus })
    }

    @Test
    fun `health endpoint returns 200`() = testApplication {
        val gatewayServer = createTestServer()
        application {
            with(gatewayServer) { configureApplication() }
        }

        val response = client.get("/health")
        response.status shouldBe HttpStatusCode.OK
        response.bodyAsText() shouldContain "ok"
    }

    @Test
    fun `websocket handshake and RPC call works`() = testApplication {
        val gatewayServer = createTestServer()
        gatewayServer.registerHandler("echo.test") { params, _ ->
            buildJsonObject {
                put("echoed", params["msg"] ?: JsonPrimitive("empty"))
            }
        }
        application {
            with(gatewayServer) { configureApplication() }
        }

        val wsClient = createClient {
            install(ClientWebSockets)
        }

        wsClient.webSocket("/") {
            // Send connect.hello
            val helloFrame = Frame.Request(
                id = "hello-1",
                method = "connect.hello",
                params = JsonObject(mapOf("role" to JsonPrimitive("operator")))
            )
            send(io.ktor.websocket.Frame.Text(Frame.serialize(helloFrame)))

            // Receive handshake response
            val helloResp = (incoming.receive() as io.ktor.websocket.Frame.Text).readText()
            val parsedHello = Frame.deserialize(helloResp) as Frame.Response
            parsedHello.ok shouldBe true
            parsedHello.id shouldBe "hello-1"

            // Send RPC call
            val rpcFrame = Frame.Request(
                id = "rpc-1",
                method = "echo.test",
                params = JsonObject(mapOf("msg" to JsonPrimitive("hello world")))
            )
            send(io.ktor.websocket.Frame.Text(Frame.serialize(rpcFrame)))

            // Receive RPC response
            val rpcResp = (incoming.receive() as io.ktor.websocket.Frame.Text).readText()
            val parsedRpc = Frame.deserialize(rpcResp) as Frame.Response
            parsedRpc.ok shouldBe true
            parsedRpc.id shouldBe "rpc-1"
            parsedRpc.payload["echoed"] shouldBe JsonPrimitive("hello world")
        }
    }

    @Test
    fun `invalid auth sends error and closes connection`() = testApplication {
        val gatewayServer = createTestServer(token = "secret-token")
        application {
            with(gatewayServer) { configureApplication() }
        }

        val wsClient = createClient {
            install(ClientWebSockets)
        }

        wsClient.webSocket("/") {
            // Send connect.hello with wrong token
            val helloFrame = Frame.Request(
                id = "hello-2",
                method = "connect.hello",
                params = JsonObject(mapOf(
                    "token" to JsonPrimitive("wrong-token"),
                    "role" to JsonPrimitive("operator")
                ))
            )
            send(io.ktor.websocket.Frame.Text(Frame.serialize(helloFrame)))

            // Receive error response
            val resp = (incoming.receive() as io.ktor.websocket.Frame.Text).readText()
            val parsed = Frame.deserialize(resp) as Frame.Response
            parsed.ok shouldBe false
            parsed.id shouldBe "hello-2"
            (parsed.payload["error"] as JsonPrimitive).content shouldContain "Authentication failed"
        }
    }

    @Test
    fun `unknown RPC method returns error response`() = testApplication {
        val gatewayServer = createTestServer()
        application {
            with(gatewayServer) { configureApplication() }
        }

        val wsClient = createClient {
            install(ClientWebSockets)
        }

        wsClient.webSocket("/") {
            // Handshake
            val helloFrame = Frame.Request(
                id = "hello-3",
                method = "connect.hello",
                params = JsonObject(mapOf("role" to JsonPrimitive("operator")))
            )
            send(io.ktor.websocket.Frame.Text(Frame.serialize(helloFrame)))
            incoming.receive() // consume hello response

            // Send unknown method
            val unknownFrame = Frame.Request(
                id = "rpc-unknown",
                method = "nonexistent.method",
                params = JsonObject(emptyMap())
            )
            send(io.ktor.websocket.Frame.Text(Frame.serialize(unknownFrame)))

            // Should get error response
            val resp = (incoming.receive() as io.ktor.websocket.Frame.Text).readText()
            val parsed = Frame.deserialize(resp) as Frame.Response
            parsed.ok shouldBe false
            parsed.id shouldBe "rpc-unknown"
            (parsed.payload["error"] as JsonPrimitive).content shouldContain "No handler registered"
        }
    }

    @Test
    fun `webchat role is blocked from admin rpc methods`() = testApplication {
        val gatewayServer = createTestServer()
        application {
            with(gatewayServer) { configureApplication() }
        }

        val wsClient = createClient {
            install(ClientWebSockets)
        }

        wsClient.webSocket("/") {
            val helloFrame = Frame.Request(
                id = "hello-webchat",
                method = "connect.hello",
                params = JsonObject(mapOf("role" to JsonPrimitive("webchat")))
            )
            send(io.ktor.websocket.Frame.Text(Frame.serialize(helloFrame)))
            incoming.receive() // consume hello response

            val blocked = Frame.Request(
                id = "rpc-config",
                method = "config.get",
                params = JsonObject(emptyMap())
            )
            send(io.ktor.websocket.Frame.Text(Frame.serialize(blocked)))

            val resp = (incoming.receive() as io.ktor.websocket.Frame.Text).readText()
            val parsed = Frame.deserialize(resp) as Frame.Response
            parsed.ok shouldBe false
            parsed.id shouldBe "rpc-config"
            (parsed.payload["error"] as JsonPrimitive).content shouldContain "Unauthorized"
        }
    }

    @Test
    fun `unauthorized websocket RPC returns INVALID_REQUEST`() = testApplication {
        val gatewayServer = createTestServer()
        gatewayServer.registerHandler("config.apply") { _, _ ->
            buildJsonObject { put("applied", JsonPrimitive(true)) }
        }
        application {
            with(gatewayServer) { configureApplication() }
        }

        val wsClient = createClient {
            install(ClientWebSockets)
        }

        wsClient.webSocket("/") {
            val helloFrame = Frame.Request(
                id = "hello-webchat",
                method = "connect.hello",
                params = JsonObject(mapOf("role" to JsonPrimitive("webchat")))
            )
            send(io.ktor.websocket.Frame.Text(Frame.serialize(helloFrame)))
            incoming.receive() // consume hello response

            val unauthorizedFrame = Frame.Request(
                id = "rpc-denied",
                method = "config.apply",
                params = JsonObject(emptyMap())
            )
            send(io.ktor.websocket.Frame.Text(Frame.serialize(unauthorizedFrame)))

            val resp = (incoming.receive() as io.ktor.websocket.Frame.Text).readText()
            val parsed = Frame.deserialize(resp) as Frame.Response
            parsed.ok shouldBe false
            parsed.id shouldBe "rpc-denied"
            (parsed.payload["code"] as JsonPrimitive).content shouldBe "INVALID_REQUEST"
            (parsed.payload["error"] as JsonPrimitive).content shouldContain "Unauthorized RPC method"
        }
    }

    @Test
    fun `health RPC is allowed for non-operator roles`() = testApplication {
        val gatewayServer = createTestServer()
        gatewayServer.registerHandler("health") { _, _ ->
            buildJsonObject { put("status", JsonPrimitive("ok")) }
        }
        application {
            with(gatewayServer) { configureApplication() }
        }

        val wsClient = createClient {
            install(ClientWebSockets)
        }

        wsClient.webSocket("/") {
            val helloFrame = Frame.Request(
                id = "hello-health",
                method = "connect.hello",
                params = JsonObject(mapOf("role" to JsonPrimitive("webchat")))
            )
            send(io.ktor.websocket.Frame.Text(Frame.serialize(helloFrame)))
            incoming.receive() // consume hello response

            val healthFrame = Frame.Request(
                id = "rpc-health",
                method = "health",
                params = JsonObject(emptyMap())
            )
            send(io.ktor.websocket.Frame.Text(Frame.serialize(healthFrame)))

            val resp = (incoming.receive() as io.ktor.websocket.Frame.Text).readText()
            val parsed = Frame.deserialize(resp) as Frame.Response
            parsed.ok shouldBe true
            parsed.id shouldBe "rpc-health"
            parsed.payload["status"] shouldBe JsonPrimitive("ok")
        }
    }

    @Test
    fun `node role bypasses method scope authorization`() = testApplication {
        val gatewayServer = createTestServer()
        gatewayServer.registerHandler("config.apply") { _, _ ->
            buildJsonObject { put("applied", JsonPrimitive(true)) }
        }
        application {
            with(gatewayServer) { configureApplication() }
        }

        val wsClient = createClient {
            install(ClientWebSockets)
        }

        wsClient.webSocket("/") {
            val helloFrame = Frame.Request(
                id = "hello-node",
                method = "connect.hello",
                params = JsonObject(mapOf("role" to JsonPrimitive("node")))
            )
            send(io.ktor.websocket.Frame.Text(Frame.serialize(helloFrame)))
            incoming.receive() // consume hello response

            val privilegedFrame = Frame.Request(
                id = "rpc-node-bypass",
                method = "config.apply",
                params = JsonObject(emptyMap())
            )
            send(io.ktor.websocket.Frame.Text(Frame.serialize(privilegedFrame)))

            val resp = (incoming.receive() as io.ktor.websocket.Frame.Text).readText()
            val parsed = Frame.deserialize(resp) as Frame.Response
            parsed.ok shouldBe true
            parsed.id shouldBe "rpc-node-bypass"
            parsed.payload["applied"] shouldBe JsonPrimitive(true)
        }
    }

    @Nested
    inner class SetupRequired {

        @Test
        fun `connect hello includes setupRequired true on fresh install`() = testApplication {
            val gatewayServer = createTestServer(
                setupStatus = SetupStatus(true, "BOOTSTRAP_SEEDED")
            )
            application {
                with(gatewayServer) { configureApplication() }
            }

            val wsClient = createClient {
                install(ClientWebSockets)
            }

            wsClient.webSocket("/") {
                val helloFrame = Frame.Request(
                    id = "hello-setup",
                    method = "connect.hello",
                    params = JsonObject(mapOf("role" to JsonPrimitive("operator")))
                )
                send(io.ktor.websocket.Frame.Text(Frame.serialize(helloFrame)))

                val resp = (incoming.receive() as io.ktor.websocket.Frame.Text).readText()
                val parsed = Frame.deserialize(resp) as Frame.Response
                parsed.ok shouldBe true
                (parsed.payload["setupRequired"] as JsonPrimitive).boolean shouldBe true
                (parsed.payload["onboardingPhase"] as JsonPrimitive).content shouldBe "BOOTSTRAP_SEEDED"
            }
        }

        @Test
        fun `connect hello allows chat while bootstrap is still pending after config is ready`() = testApplication {
            val gatewayServer = createTestServer(
                setupStatus = SetupStatus(false, "BOOTSTRAP_SEEDED")
            )
            application {
                with(gatewayServer) { configureApplication() }
            }

            val wsClient = createClient {
                install(ClientWebSockets)
            }

            wsClient.webSocket("/") {
                val helloFrame = Frame.Request(
                    id = "hello-bootstrap-pending",
                    method = "connect.hello",
                    params = JsonObject(mapOf("role" to JsonPrimitive("operator")))
                )
                send(io.ktor.websocket.Frame.Text(Frame.serialize(helloFrame)))

                val resp = (incoming.receive() as io.ktor.websocket.Frame.Text).readText()
                val parsed = Frame.deserialize(resp) as Frame.Response
                parsed.ok shouldBe true
                (parsed.payload["setupRequired"] as JsonPrimitive).boolean shouldBe false
                (parsed.payload["onboardingPhase"] as JsonPrimitive).content shouldBe "BOOTSTRAP_SEEDED"
            }
        }

        @Test
        fun `connect hello includes setupRequired false after setup`() = testApplication {
            val gatewayServer = createTestServer(
                setupStatus = SetupStatus(false, "ONBOARDING_COMPLETE")
            )
            application {
                with(gatewayServer) { configureApplication() }
            }

            val wsClient = createClient {
                install(ClientWebSockets)
            }

            wsClient.webSocket("/") {
                val helloFrame = Frame.Request(
                    id = "hello-done",
                    method = "connect.hello",
                    params = JsonObject(mapOf("role" to JsonPrimitive("operator")))
                )
                send(io.ktor.websocket.Frame.Text(Frame.serialize(helloFrame)))

                val resp = (incoming.receive() as io.ktor.websocket.Frame.Text).readText()
                val parsed = Frame.deserialize(resp) as Frame.Response
                parsed.ok shouldBe true
                (parsed.payload["setupRequired"] as JsonPrimitive).boolean shouldBe false
                (parsed.payload["onboardingPhase"] as JsonPrimitive).content shouldBe "ONBOARDING_COMPLETE"
            }
        }
    }

    @Test
    fun `valid auth with token succeeds`() = testApplication {
        val gatewayServer = createTestServer(token = "my-secret")
        application {
            with(gatewayServer) { configureApplication() }
        }

        val wsClient = createClient {
            install(ClientWebSockets)
        }

        wsClient.webSocket("/") {
            val helloFrame = Frame.Request(
                id = "hello-auth",
                method = "connect.hello",
                params = JsonObject(mapOf(
                    "token" to JsonPrimitive("my-secret"),
                    "role" to JsonPrimitive("operator")
                ))
            )
            send(io.ktor.websocket.Frame.Text(Frame.serialize(helloFrame)))

            val resp = (incoming.receive() as io.ktor.websocket.Frame.Text).readText()
            val parsed = Frame.deserialize(resp) as Frame.Response
            parsed.ok shouldBe true
            parsed.id shouldBe "hello-auth"
        }
    }

    @Nested
    inner class SecretResolver {

        @Test
        fun `config key takes precedence over env var`() {
            val config = GatewayConfig(
                secrets = SecretsConfig(anthropicApiKey = "from-config")
            )
            val resolver = buildSecretResolver(config)
            resolver("ANTHROPIC_API_KEY") shouldBe "from-config"
        }

        @Test
        fun `blank config key falls back to env`() {
            val config = GatewayConfig(
                secrets = SecretsConfig(anthropicApiKey = "")
            )
            val resolver = buildSecretResolver(config)
            // Will fall back to System.getenv which may or may not be set;
            // at minimum it should not return the blank string
            val result = resolver("ANTHROPIC_API_KEY")
            // If env var is not set, result is null; if set, it's the env value
            // Either way, it should not be blank
            if (result != null) {
                (result.isNotBlank()) shouldBe true
            }
        }

        @Test
        fun `null config key falls back to env`() {
            val config = GatewayConfig(
                secrets = SecretsConfig(anthropicApiKey = null)
            )
            val resolver = buildSecretResolver(config)
            // Same as above — falls back to env
            val result = resolver("ANTHROPIC_API_KEY")
            if (result != null) {
                (result.isNotBlank()) shouldBe true
            }
        }

        @Test
        fun `unknown env key falls back to System getenv`() {
            val config = GatewayConfig()
            val resolver = buildSecretResolver(config)
            // PATH is almost always set
            val result = resolver("PATH")
            (result != null) shouldBe true
        }
    }
}
