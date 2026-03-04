package com.openclaw.gateway

import com.openclaw.gateway.config.AuthConfig
import com.openclaw.gateway.config.GatewayConfig
import com.openclaw.gateway.config.GatewaySettings
import com.openclaw.protocol.frames.Frame
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
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test

class GatewayServerTest {

    private fun createTestServer(
        token: String? = null,
        password: String? = null
    ): GatewayServer {
        val config = GatewayConfig(
            gateway = GatewaySettings(
                auth = AuthConfig(token = token, password = password)
            )
        )
        return GatewayServer(config)
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
}
