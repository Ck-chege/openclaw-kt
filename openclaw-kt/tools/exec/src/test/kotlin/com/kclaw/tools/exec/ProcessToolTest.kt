package com.kclaw.tools.exec

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ProcessToolTest {

    private lateinit var bgTracker: BackgroundProcessTracker

    @BeforeEach
    fun setUp() {
        bgTracker = BackgroundProcessTracker()
        ProcessTool.tracker = bgTracker
    }

    @Nested
    inner class ListAction {

        @Test
        fun `list shows no processes when empty`() = runTest {
            val result = ProcessTool.execute(ProcessTool.Args(action = "list"))
            result shouldBe "No background processes"
        }

        @Test
        fun `list shows tracked process`() = runTest {
            val p = ProcessBuilder("/bin/sh", "-c", "sleep 30").start()
            val id = bgTracker.track("sleep 30", p)
            val result = ProcessTool.execute(ProcessTool.Args(action = "list"))
            result shouldContain id
            result shouldContain "running"
            result shouldContain "sleep 30"
            // Teardown
            bgTracker.kill(id)
            p.waitFor()
        }
    }

    @Nested
    inner class PollAction {

        @Test
        fun `poll running process`() = runTest {
            val p = ProcessBuilder("/bin/sh", "-c", "sleep 30").start()
            val id = bgTracker.track("sleep 30", p)
            val result = ProcessTool.execute(ProcessTool.Args(action = "poll", sessionId = id))
            result shouldContain "Session: $id"
            result shouldContain "running"
            // Teardown
            bgTracker.kill(id)
            p.waitFor()
        }

        @Test
        fun `poll completed process`() = runTest {
            val p = ProcessBuilder("/bin/sh", "-c", "echo done").start()
            val id = bgTracker.track("echo done", p)
            p.waitFor()
            Thread.sleep(500) // let collector threads flush
            val result = ProcessTool.execute(ProcessTool.Args(action = "poll", sessionId = id))
            result shouldContain "exited"
            result shouldContain "code: 0"
            result shouldContain "done"
        }

        @Test
        fun `poll unknown sessionId returns error`() = runTest {
            val result = ProcessTool.execute(
                ProcessTool.Args(action = "poll", sessionId = "bad_id")
            )
            result shouldContain "Error"
            result shouldContain "unknown session ID"
        }

        @Test
        fun `poll without sessionId returns error`() = runTest {
            val result = ProcessTool.execute(ProcessTool.Args(action = "poll"))
            result shouldContain "Error"
            result shouldContain "sessionId required"
        }
    }

    @Nested
    inner class LogAction {

        @Test
        fun `log returns output with default limit`() = runTest {
            val p = ProcessBuilder("/bin/sh", "-c", "echo line1; echo line2; echo line3").start()
            val id = bgTracker.track("echo lines", p)
            p.waitFor()
            Thread.sleep(500)
            val result = ProcessTool.execute(ProcessTool.Args(action = "log", sessionId = id))
            result shouldContain "line1"
            result shouldContain "line2"
            result shouldContain "line3"
        }

        @Test
        fun `log with offset and limit`() = runTest {
            val p = ProcessBuilder("/bin/sh", "-c", "echo a; echo b; echo c; echo d; echo e").start()
            val id = bgTracker.track("echo letters", p)
            p.waitFor()
            Thread.sleep(500)
            val result = ProcessTool.execute(
                ProcessTool.Args(action = "log", sessionId = id, offset = 1, limit = 2)
            )
            result shouldContain "b"
            result shouldContain "c"
        }

        @Test
        fun `log unknown sessionId returns error`() = runTest {
            val result = ProcessTool.execute(
                ProcessTool.Args(action = "log", sessionId = "nope")
            )
            result shouldContain "Error"
            result shouldContain "unknown session ID"
        }
    }

    @Nested
    inner class KillAction {

        @Test
        fun `kill running process`() = runTest {
            val p = ProcessBuilder("/bin/sh", "-c", "sleep 60").start()
            val id = bgTracker.track("sleep 60", p)
            val result = ProcessTool.execute(ProcessTool.Args(action = "kill", sessionId = id))
            result shouldBe "Killed process $id"
            p.waitFor()
        }

        @Test
        fun `kill unknown sessionId returns error`() = runTest {
            val result = ProcessTool.execute(
                ProcessTool.Args(action = "kill", sessionId = "bad")
            )
            result shouldContain "Error"
            result shouldContain "unknown session ID"
        }

        @Test
        fun `kill without sessionId returns error`() = runTest {
            val result = ProcessTool.execute(ProcessTool.Args(action = "kill"))
            result shouldContain "Error"
            result shouldContain "sessionId required"
        }
    }

    @Nested
    inner class ClearAction {

        @Test
        fun `clear removes finished session`() = runTest {
            val p = ProcessBuilder("/bin/sh", "-c", "echo bye").start()
            val id = bgTracker.track("echo bye", p)
            p.waitFor()
            val result = ProcessTool.execute(ProcessTool.Args(action = "clear", sessionId = id))
            result shouldBe "Cleared session $id"
            // Verify it's gone
            val listResult = ProcessTool.execute(ProcessTool.Args(action = "list"))
            listResult shouldBe "No background processes"
        }

        @Test
        fun `clear unknown sessionId returns error`() = runTest {
            val result = ProcessTool.execute(
                ProcessTool.Args(action = "clear", sessionId = "ghost")
            )
            result shouldContain "Error"
            result shouldContain "unknown session ID"
        }
    }

    @Nested
    inner class UnknownAction {

        @Test
        fun `unknown action returns error`() = runTest {
            val result = ProcessTool.execute(ProcessTool.Args(action = "explode"))
            result shouldContain "Error"
            result shouldContain "unknown action"
            result shouldContain "explode"
        }
    }

    @Nested
    inner class TrackerNotAvailable {

        @Test
        fun `returns error when tracker is null`() = runTest {
            ProcessTool.tracker = null
            val result = ProcessTool.execute(ProcessTool.Args(action = "list"))
            result shouldContain "Error"
            result shouldContain "process tracking not available"
        }
    }
}
