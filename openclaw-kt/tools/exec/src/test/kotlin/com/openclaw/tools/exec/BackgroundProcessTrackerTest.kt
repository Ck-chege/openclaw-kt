package com.openclaw.tools.exec

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class BackgroundProcessTrackerTest {

    private val tracker = BackgroundProcessTracker()

    @Nested
    inner class Track {

        @Test
        fun `track returns unique ID`() {
            val p1 = ProcessBuilder("/bin/sh", "-c", "echo one").start()
            val p2 = ProcessBuilder("/bin/sh", "-c", "echo two").start()
            val id1 = tracker.track("echo one", p1)
            val id2 = tracker.track("echo two", p2)
            id1 shouldNotBe id2
            id1.length shouldBe 8
            id2.length shouldBe 8
            p1.waitFor()
            p2.waitFor()
        }
    }

    @Nested
    inner class ListProcesses {

        @Test
        fun `list returns all tracked processes`() {
            val p1 = ProcessBuilder("/bin/sh", "-c", "echo a").start()
            val p2 = ProcessBuilder("/bin/sh", "-c", "echo b").start()
            tracker.track("echo a", p1)
            tracker.track("echo b", p2)
            tracker.list() shouldHaveSize 2
            p1.waitFor()
            p2.waitFor()
        }

        @Test
        fun `list returns empty when no processes tracked`() {
            tracker.list() shouldHaveSize 0
        }
    }

    @Nested
    inner class Poll {

        @Test
        fun `poll returns output from completed process`() {
            val p = ProcessBuilder("/bin/sh", "-c", "echo hello").start()
            val id = tracker.track("echo hello", p)
            p.waitFor()
            // Give collector threads a moment to flush
            Thread.sleep(500)
            val result = tracker.poll(id)
            result shouldNotBe null
            result!!.output shouldContain "hello"
            result.isAlive shouldBe false
            result.exitCode shouldBe 0
        }

        @Test
        fun `poll returns null for unknown ID`() {
            tracker.poll("nonexistent") shouldBe null
        }
    }

    @Nested
    inner class Kill {

        @Test
        fun `kill destroys a running process`() {
            val p = ProcessBuilder("/bin/sh", "-c", "sleep 60").start()
            val id = tracker.track("sleep 60", p)
            p.isAlive shouldBe true
            tracker.kill(id) shouldBe true
            // Wait for the process to actually terminate
            p.waitFor()
            p.isAlive shouldBe false
        }

        @Test
        fun `kill returns false for unknown ID`() {
            tracker.kill("nonexistent") shouldBe false
        }
    }

    @Nested
    inner class Clear {

        @Test
        fun `clear removes a tracked process`() {
            val p = ProcessBuilder("/bin/sh", "-c", "echo done").start()
            val id = tracker.track("echo done", p)
            p.waitFor()
            tracker.clear(id) shouldBe true
            tracker.list() shouldHaveSize 0
        }

        @Test
        fun `clear returns false for unknown ID`() {
            tracker.clear("nonexistent") shouldBe false
        }
    }

    @Nested
    inner class Cleanup {

        @Test
        fun `cleanup removes old completed processes`() {
            val p = ProcessBuilder("/bin/sh", "-c", "echo done").start()
            tracker.track("echo done", p)
            p.waitFor()
            // With maxAgeSeconds=0, any completed process should be removed
            tracker.cleanup(maxAgeSeconds = 0)
            tracker.list() shouldHaveSize 0
        }

        @Test
        fun `cleanup keeps running processes`() {
            val p = ProcessBuilder("/bin/sh", "-c", "sleep 60").start()
            val id = tracker.track("sleep 60", p)
            tracker.cleanup(maxAgeSeconds = 0)
            tracker.list() shouldHaveSize 1
            // Teardown
            tracker.kill(id)
            p.waitFor()
        }
    }
}
