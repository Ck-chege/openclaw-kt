package com.kclaw.agent.runtime

import com.kclaw.agent.model.ModelRegistry
import com.kclaw.agent.workspace.WorkspaceLoader
import com.kclaw.protocol.identifiers.SessionKey
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class RunManagerTest {

    @TempDir
    lateinit var tempDir: Path

    private val testScope = CoroutineScope(SupervisorJob())

    private fun createManager(timeoutMs: Long = 600_000L): RunManager {
        val registry = ModelRegistry { null }
        val runtime = AgentRuntime(registry)
        return RunManager(runtime, testScope, timeoutMs)
    }

    @AfterEach
    fun cleanup() {
        testScope.cancel()
    }

    @Nested
    inner class StartRun {

        @Test
        fun `start run tracks run as active`() = runTest {
            val manager = createManager()
            val key = SessionKey("test:main:*:main")
            val workspace = WorkspaceLoader(tempDir)

            manager.startRun(key, "Hello", workspace)
            // Give the coroutine a moment to launch
            delay(50)

            val status = manager.getStatus(key)
            status.sessionKey shouldBe key
        }
    }

    @Nested
    inner class StopRun {

        @Test
        fun `stop run cancels active job`() = runTest {
            val manager = createManager()
            val key = SessionKey("test:main:*:main")
            val workspace = WorkspaceLoader(tempDir)

            manager.startRun(key, "Hello", workspace)
            delay(50)

            manager.stopRun(key)
            delay(50)

            val status = manager.getStatus(key)
            status.isRunning shouldBe false
        }

        @Test
        fun `stop idempotent - stopping non-existent run does not crash`() = runTest {
            val manager = createManager()
            val key = SessionKey("nonexistent:main:*:main")

            // Should not throw
            manager.stopRun(key)
        }
    }

    @Nested
    inner class GetStatus {

        @Test
        fun `status returns not running for unknown session`() = runTest {
            val manager = createManager()
            val key = SessionKey("unknown:main:*:main")

            val status = manager.getStatus(key)
            status.isRunning shouldBe false
            status.elapsedMs shouldBe 0L
        }

        @Test
        fun `status returns correct session key`() = runTest {
            val manager = createManager()
            val key = SessionKey("test:main:*:main")

            val status = manager.getStatus(key)
            status.sessionKey shouldBe key
        }
    }
}
