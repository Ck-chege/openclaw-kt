package com.openclaw.gateway.ws

import com.openclaw.gateway.auth.GatewayAuth
import com.openclaw.gateway.auth.WsSession
import com.openclaw.gateway.rpc.RpcRouter
import com.openclaw.protocol.frames.Frame
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

private val logger = KotlinLogging.logger {}

/**
 * Setup status communicated to clients in the `connect.hello` response.
 * When [setupRequired] is true, the client should show the onboarding UI.
 */
data class SetupStatus(
    val setupRequired: Boolean,
    val onboardingPhase: String
)

/**
 * Handles WebSocket connections following the OpenClaw wire protocol.
 *
 * Connection lifecycle:
 * 1. Wait for the first frame, which must be a `connect.hello` request.
 * 2. Extract the token from params and authenticate via [auth].
 * 3. If authentication fails, send an error response and close.
 * 4. If authentication succeeds, send an OK response (including setup status).
 * 5. Enter the message loop: receive frames, route to RPC, send responses.
 * 6. Handle errors gracefully (log and close).
 */
class WebSocketHandler(
    private val auth: GatewayAuth,
    private val router: RpcRouter,
    private val sessionRegistry: SessionRegistry = SessionRegistry(),
    private val setupStatusProvider: () -> SetupStatus = { SetupStatus(false, "ONBOARDING_COMPLETE") }
) {

    suspend fun handleSession(wsSession: DefaultWebSocketServerSession) {
        var session: WsSession? = null

        try {
            // Step 1: Wait for the first frame (must be connect.hello)
            val firstMessage = wsSession.incoming.receive()
            val firstText = (firstMessage as? io.ktor.websocket.Frame.Text)?.readText()
                ?: run {
                    logger.warn { "First frame was not a text frame, closing connection" }
                    wsSession.close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, "Expected text frame"))
                    return
                }

            val firstFrame = try {
                Frame.deserialize(firstText)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to deserialize first frame" }
                wsSession.close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, "Invalid frame format"))
                return
            }

            // Step 2: Must be a connect.hello request
            if (firstFrame !is Frame.Request || firstFrame.method != "connect.hello") {
                val errorResponse = Frame.Response(
                    id = firstFrame.id,
                    ok = false,
                    payload = JsonObject(mapOf("error" to JsonPrimitive("First message must be connect.hello")))
                )
                wsSession.send(io.ktor.websocket.Frame.Text(Frame.serialize(errorResponse)))
                wsSession.close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, "Expected connect.hello"))
                return
            }

            // Step 3: Extract token and role, authenticate
            val providedToken = firstFrame.params["token"]?.jsonPrimitive?.contentOrNull
            val role = firstFrame.params["role"]?.jsonPrimitive?.contentOrNull

            session = auth.authenticate(providedToken, role)

            if (session == null) {
                // Step 3b: Auth failed
                val errorResponse = Frame.Response(
                    id = firstFrame.id,
                    ok = false,
                    payload = JsonObject(mapOf("error" to JsonPrimitive("Authentication failed")))
                )
                wsSession.send(io.ktor.websocket.Frame.Text(Frame.serialize(errorResponse)))
                wsSession.close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Authentication failed"))
                return
            }

            // Step 4: Auth succeeded, send OK with setup status
            val setupStatus = setupStatusProvider()
            val okResponse = Frame.Response(
                id = firstFrame.id,
                ok = true,
                payload = JsonObject(mapOf(
                    "sessionId" to JsonPrimitive(session.id),
                    "message" to JsonPrimitive("Connected"),
                    "setupRequired" to JsonPrimitive(setupStatus.setupRequired),
                    "onboardingPhase" to JsonPrimitive(setupStatus.onboardingPhase)
                ))
            )
            wsSession.send(io.ktor.websocket.Frame.Text(Frame.serialize(okResponse)))
            sessionRegistry.register(session.id, wsSession)
            logger.info { "WebSocket session ${session.id} connected" }

            // Step 5: Message loop
            for (message in wsSession.incoming) {
                val text = (message as? io.ktor.websocket.Frame.Text)?.readText() ?: continue

                val frame = try {
                    Frame.deserialize(text)
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to deserialize frame, ignoring" }
                    continue
                }

                when (frame) {
                    is Frame.Request -> {
                        val responsePayload = try {
                            router.route(frame.method, frame.params, session.id)
                        } catch (e: IllegalArgumentException) {
                            JsonObject(mapOf("error" to JsonPrimitive(e.message ?: "Unknown method")))
                        } catch (e: Exception) {
                            logger.error(e) { "Error handling RPC method: ${frame.method}" }
                            JsonObject(mapOf("error" to JsonPrimitive("Internal error: ${e.message}")))
                        }

                        val isOk = !responsePayload.containsKey("error")
                        val response = Frame.Response(
                            id = frame.id,
                            ok = isOk,
                            payload = responsePayload
                        )
                        wsSession.send(io.ktor.websocket.Frame.Text(Frame.serialize(response)))
                    }
                    is Frame.Response -> {
                        logger.debug { "Received unexpected response frame id=${frame.id}, ignoring" }
                    }
                    is Frame.Event -> {
                        logger.debug { "Received unexpected event frame id=${frame.id}, ignoring" }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "WebSocket session error (session=${session?.id ?: "pre-auth"})" }
        } finally {
            session?.let { sessionRegistry.unregister(it.id) }
            logger.info { "WebSocket session ${session?.id ?: "pre-auth"} disconnected" }
        }
    }
}
