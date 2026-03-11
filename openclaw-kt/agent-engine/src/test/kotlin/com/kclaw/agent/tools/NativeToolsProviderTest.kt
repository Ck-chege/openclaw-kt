package com.kclaw.agent.tools

import com.kclaw.agent.model.ModelRegistry
import com.kclaw.agent.runtime.AgentRuntime
import com.kclaw.agent.runtime.RunManager
import com.kclaw.gateway.config.AgentsConfig
import com.kclaw.protocol.identifiers.SessionKey
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class NativeToolsProviderTest {

    private val testScope = CoroutineScope(SupervisorJob())

    @AfterEach
    fun cleanup() {
        testScope.cancel()
    }

    @Test
    fun `allTools returns all native tools`() {
        val store = FakeSessionStore()
        val registry = ModelRegistry { null }
        val runtime = AgentRuntime(registry)
        val runManager = RunManager(runtime, testScope)
        val sessionKey = SessionKey("main:main:*:main")

        val provider = NativeToolsProvider(
            sessionStore = store,
            runManager = runManager,
            agentsConfig = { AgentsConfig() },
            currentSessionKey = { sessionKey }
        )

        val tools = provider.allTools()
        tools shouldHaveSize 6

        val names = tools.map { it.name }.toSet()
        names shouldBe setOf(
            "sessions_list",
            "sessions_history",
            "sessions_send",
            "session_status",
            "agents_list",
            "create_skill"
        )
    }
}
