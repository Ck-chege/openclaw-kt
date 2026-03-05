package com.openclaw.agent.runtime

import com.openclaw.agent.model.ModelRegistry
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AgentRuntimeBridgeJobTest {

    private fun runtimeWithMissingKey(): AgentRuntime {
        val registry = ModelRegistry { null }
        return AgentRuntime(registry)
    }

    @Test
    fun `joinBridgeJobOrCancel waits for completed bridge job`() = runTest {
        val runtime = runtimeWithMissingKey()
        val bridgeJob = launch { }

        runtime.joinBridgeJobOrCancel(bridgeJob)

        bridgeJob.isCompleted shouldBe true
        bridgeJob.isCancelled shouldBe false
    }

    @Test
    fun `joinBridgeJobOrCancel cancels bridge job after timeout`() = runTest {
        val runtime = runtimeWithMissingKey()
        val bridgeJob = launch { awaitCancellation() }

        runtime.joinBridgeJobOrCancel(bridgeJob)

        bridgeJob.isCancelled shouldBe true
    }
}
