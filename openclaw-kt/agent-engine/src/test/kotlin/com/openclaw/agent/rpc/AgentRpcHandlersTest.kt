package com.openclaw.agent.rpc

import com.openclaw.agent.isolation.AgentResolver
import com.openclaw.agent.model.ModelRegistry
import com.openclaw.agent.runtime.AgentRuntime
import com.openclaw.agent.runtime.RunManager
import com.openclaw.gateway.auth.ClientRole
import com.openclaw.gateway.auth.WsSession
import com.openclaw.gateway.config.AgentsConfig
import com.openclaw.gateway.rpc.RpcRouter
import com.openclaw.protocol.identifiers.ModelId
import com.openclaw.protocol.identifiers.OperatorLevel
import com.openclaw.protocol.identifiers.SessionKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class AgentRpcHandlersTest {

    @TempDir
    lateinit var tempDir: Path

    private val testScope = CoroutineScope(SupervisorJob())

    private fun createRouter(): Triple<RpcRouter, RunManager, AgentResolver> {
        val registry = ModelRegistry { null }
        val runtime = AgentRuntime(registry)
        val runManager = RunManager(runtime, testScope)
        val agentResolver = AgentResolver()
        val router = RpcRouter()

        registerAgentHandlers(
            router = router,
            runManager = runManager,
            agentResolver = agentResolver,
            agentsConfig = { AgentsConfig() }
        )

        return Triple(router, runManager, agentResolver)
    }

    @Nested
    inner class Registration {

        @Test
        fun `all three handlers are registered`() = runTest {
            val (router) = createRouter()

            router.hasHandler("agent.run") shouldBe true
            router.hasHandler("agent.stop") shouldBe true
            router.hasHandler("agent.status") shouldBe true
        }
    }

    @Nested
    inner class AgentRun {

        @Test
        fun `agent run with valid params returns started response`() = runTest {
            val (router) = createRouter()

            val params = buildJsonObject {
                put("key", "test:main:*:main")
                put("message", "Hello")
            }

            val result = router.route("agent.run", params, "test-session")

            result["started"]?.jsonPrimitive?.boolean shouldBe true
            result["key"]?.jsonPrimitive?.content shouldBe "test:main:*:main"
            result["model"]?.jsonPrimitive?.content shouldNotBe null
        }

        @Test
        fun `agent run with model override uses specified model`() = runTest {
            val (router) = createRouter()

            val params = buildJsonObject {
                put("key", "test:main:*:main")
                put("message", "Hello")
                put("model", "openai:gpt-4o")
            }

            val result = router.route("agent.run", params, "test-session")

            result["started"]?.jsonPrimitive?.boolean shouldBe true
            result["model"]?.jsonPrimitive?.content shouldBe "openai:gpt-4o"
        }

        @Test
        fun `agent run missing key throws`() = runTest {
            val (router) = createRouter()

            val params = buildJsonObject {
                put("message", "Hello")
            }

            assertThrows<IllegalArgumentException> {
                router.route("agent.run", params, "test-session")
            }
        }

        @Test
        fun `agent run missing message throws`() = runTest {
            val (router) = createRouter()

            val params = buildJsonObject {
                put("key", "test:main:*:main")
            }

            assertThrows<IllegalArgumentException> {
                router.route("agent.run", params, "test-session")
            }
        }
    }

    @Nested
    inner class CallerContextResolution {

        @Test
        fun `caller context includes provider and cron scope from run`() {
            val context = buildCallerContext(
                authSession = null,
                sessionKey = SessionKey("worker:main:cron:job-1"),
                modelId = ModelId("openai:gpt-5.4")
            )

            context.provider shouldBe "openai"
            context.agentId shouldBe "worker"
            context.isCron shouldBe true
            context.isOwner shouldBe false
        }

        @Test
        fun `caller context marks authenticated admin scoped operators as owner`() {
            val authSession = WsSession(
                id = "operator-session",
                role = ClientRole.Operator(OperatorLevel.WRITE),
                scopes = setOf("chat", "tools", "admin"),
                isAuthenticated = true
            )

            val context = buildCallerContext(
                authSession = authSession,
                sessionKey = SessionKey("main:main:*:main"),
                modelId = ModelId("anthropic:claude-sonnet-4-6")
            )

            context.isOwner shouldBe true
            context.provider shouldBe "anthropic"
        }

        @Test
        fun `caller context marks authenticated admin level operators as owner`() {
            val authSession = WsSession(
                id = "operator-session",
                role = ClientRole.Operator(OperatorLevel.ADMIN),
                scopes = setOf("chat", "tools"),
                isAuthenticated = true
            )

            val context = buildCallerContext(
                authSession = authSession,
                sessionKey = SessionKey("main:main:*:main"),
                modelId = ModelId("anthropic:claude-sonnet-4-6")
            )

            context.isOwner shouldBe true
        }

        @Test
        fun `caller context does not treat open mode operators as owner`() {
            val authSession = WsSession(
                id = "open-mode-operator",
                role = ClientRole.Operator(OperatorLevel.WRITE),
                scopes = setOf("chat", "config", "agents", "tools", "admin"),
                isAuthenticated = false
            )

            val context = buildCallerContext(
                authSession = authSession,
                sessionKey = SessionKey("main:main:*:main"),
                modelId = ModelId("anthropic:claude-sonnet-4-6")
            )

            context.isOwner shouldBe false
        }

        @Test
        fun `caller context does not treat authenticated non admin operators as owner`() {
            val authSession = WsSession(
                id = "limited-operator",
                role = ClientRole.Operator(OperatorLevel.WRITE),
                scopes = setOf("chat", "tools"),
                isAuthenticated = true
            )

            val context = buildCallerContext(
                authSession = authSession,
                sessionKey = SessionKey("main:main:*:main"),
                modelId = ModelId("anthropic:claude-sonnet-4-6")
            )

            context.isOwner shouldBe false
        }
    }

    @Nested
    inner class AgentStop {

        @Test
        fun `agent stop returns stopped response`() = runTest {
            val (router) = createRouter()

            val params = buildJsonObject {
                put("key", "test:main:*:main")
            }

            val result = router.route("agent.stop", params, "test-session")

            result["stopped"]?.jsonPrimitive?.boolean shouldBe true
            result["key"]?.jsonPrimitive?.content shouldBe "test:main:*:main"
        }

        @Test
        fun `agent stop missing key throws`() = runTest {
            val (router) = createRouter()

            assertThrows<IllegalArgumentException> {
                router.route("agent.stop", buildJsonObject {}, "test-session")
            }
        }
    }

    @Nested
    inner class AgentStatus {

        @Test
        fun `agent status for unknown session returns not running`() = runTest {
            val (router) = createRouter()

            val params = buildJsonObject {
                put("key", "unknown:main:*:main")
            }

            val result = router.route("agent.status", params, "test-session")

            result["key"]?.jsonPrimitive?.content shouldBe "unknown:main:*:main"
            result["isRunning"]?.jsonPrimitive?.boolean shouldBe false
            result["elapsedMs"]?.jsonPrimitive?.long shouldBe 0L
        }

        @Test
        fun `agent status missing key throws`() = runTest {
            val (router) = createRouter()

            assertThrows<IllegalArgumentException> {
                router.route("agent.status", buildJsonObject {}, "test-session")
            }
        }
    }

    @AfterEach
    fun cleanup() {
        testScope.cancel()
    }
}
