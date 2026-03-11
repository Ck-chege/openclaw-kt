package com.kclaw.gateway

import com.kclaw.gateway.auth.GatewayAuth
import com.kclaw.gateway.config.GatewayConfig
import com.kclaw.gateway.rpc.RpcHandler
import com.kclaw.gateway.rpc.RpcRouter
import com.kclaw.gateway.ws.MAX_FRAME_BYTES
import com.kclaw.gateway.ws.SessionRegistry
import com.kclaw.gateway.ws.SetupStatus
import com.kclaw.gateway.ws.WebSocketHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

/** Config-first secret resolver: tries structured secrets, then falls back to env. */
fun buildSecretResolver(config: GatewayConfig): (String) -> String? = { envKey ->
    when (envKey) {
        "ANTHROPIC_API_KEY" -> config.secrets.anthropicApiKey?.takeIf { it.isNotBlank() }
        "OPENAI_API_KEY" -> config.secrets.openaiApiKey?.takeIf { it.isNotBlank() }
        else -> null
    } ?: System.getenv(envKey)
}

/**
 * The main gateway server entry point.
 *
 * Manages the Ktor/Netty embedded server lifecycle, WebSocket handling,
 * authentication, and RPC routing.
 */
class GatewayServer(
    private val config: GatewayConfig,
    setupStatusProvider: () -> SetupStatus = { SetupStatus(false, "ONBOARDING_COMPLETE") }
) {
    private val auth = GatewayAuth(config.gateway.auth.token, config.gateway.auth.password)
    val router = RpcRouter()
    val sessionRegistry = SessionRegistry()
    private val wsHandler = WebSocketHandler(auth, router, sessionRegistry, setupStatusProvider)
    private var server: EmbeddedServer<*, *>? = null

    /**
     * Register an RPC handler for the given method.
     */
    fun registerHandler(method: String, handler: RpcHandler) = router.register(method, handler)

    /**
     * Start the gateway server.
     */
    fun start() {
        logger.info { "Starting gateway server on ${config.gateway.bind}:${config.gateway.port}" }
        server = embeddedServer(Netty, port = config.gateway.port, host = config.gateway.bind) {
            configureApplication()
        }.start(wait = false)
        logger.info { "Gateway server started" }
    }

    /**
     * Stop the gateway server gracefully.
     */
    fun stop() {
        logger.info { "Stopping gateway server" }
        server?.stop(gracePeriodMillis = 1000, timeoutMillis = 5000)
        server = null
        logger.info { "Gateway server stopped" }
    }

    /**
     * Configure the Ktor application with WebSocket support, health endpoint,
     * and the main WebSocket route.
     */
    internal fun Application.configureApplication() {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            })
        }
        install(WebSockets) {
            pingPeriod = 15.seconds
            timeout = 15.seconds
            maxFrameSize = MAX_FRAME_BYTES
        }
        routing {
            get("/health") {
                call.respondText(
                    """{"status":"ok"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.OK
                )
            }
            webSocket("/") {
                wsHandler.handleSession(this)
            }
        }
    }
}
