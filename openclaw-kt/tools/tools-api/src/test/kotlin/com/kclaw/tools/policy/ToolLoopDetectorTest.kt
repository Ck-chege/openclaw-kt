package com.kclaw.tools.policy

import com.kclaw.gateway.config.LoopDetectionConfig
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ToolLoopDetectorTest {

    private lateinit var detector: ToolLoopDetector

    @BeforeEach
    fun setup() {
        detector = ToolLoopDetector(LoopDetectionConfig(
            enabled = true,
            repeatWarning = 3,   // lower thresholds for testing
            repeatCritical = 5,
            circuitBreaker = 8
        ))
    }

    @Nested
    inner class GenericRepeat {

        @Test
        fun `no detection below warning threshold`() {
            repeat(2) {
                val result = detector.recordAndCheck("exec", """{"command":"ls"}""")
                result.severity shouldBe ToolLoopDetector.Severity.NONE
            }
        }

        @Test
        fun `warning at warning threshold`() {
            repeat(2) { detector.recordAndCheck("exec", """{"command":"ls"}""") }
            val result = detector.recordAndCheck("exec", """{"command":"ls"}""")
            result.severity shouldBe ToolLoopDetector.Severity.WARNING
            result.detector shouldBe "generic_repeat"
        }

        @Test
        fun `critical at critical threshold`() {
            repeat(4) { detector.recordAndCheck("exec", """{"command":"ls"}""") }
            val result = detector.recordAndCheck("exec", """{"command":"ls"}""")
            result.severity shouldBe ToolLoopDetector.Severity.CRITICAL
        }

        @Test
        fun `different params do not trigger repeat`() {
            repeat(4) { i ->
                val result = detector.recordAndCheck("exec", """{"command":"ls $i"}""")
                result.severity shouldBe ToolLoopDetector.Severity.NONE
            }
        }
    }

    @Nested
    inner class PollNoProgress {

        @Test
        fun `same params and outcome triggers no-progress`() {
            repeat(2) { detector.recordAndCheck("process", """{"action":"poll"}""", "output: running") }
            val result = detector.recordAndCheck("process", """{"action":"poll"}""", "output: running")
            result.severity shouldBe ToolLoopDetector.Severity.WARNING
            result.detector shouldBe "poll_no_progress"
        }

        @Test
        fun `same params but different outcomes do not trigger no-progress`() {
            repeat(4) { i ->
                val result = detector.recordAndCheck("process", """{"action":"poll"}""", "output: step $i")
                // Should trigger generic_repeat (same params) but NOT poll_no_progress
                if (result.severity != ToolLoopDetector.Severity.NONE) {
                    result.detector shouldBe "generic_repeat"
                }
            }
        }
    }

    @Nested
    inner class PingPong {

        @Test
        fun `alternating two tools triggers ping-pong`() {
            var result: ToolLoopDetector.LoopCheckResult
            repeat(2) {
                detector.recordAndCheck("read", """{"path":"a.txt"}""")
                detector.recordAndCheck("write", """{"path":"b.txt"}""")
            }
            // 5th call should trigger warning (alternations >= 3)
            result = detector.recordAndCheck("read", """{"path":"a.txt"}""")
            // At this point we have: read, write, read, write, read = 5 alternations >= repeatCritical=5
            result.severity shouldBe ToolLoopDetector.Severity.CRITICAL
            result.detector shouldBe "ping_pong"
        }
    }

    @Nested
    inner class CircuitBreaker {

        @Test
        fun `hard stop at circuit breaker threshold`() {
            repeat(7) { detector.recordAndCheck("exec", """{"command":"ls"}""") }
            val result = detector.recordAndCheck("exec", """{"command":"ls"}""")
            result.severity shouldBe ToolLoopDetector.Severity.CRITICAL
            result.detector shouldBe "circuit_breaker"
        }
    }

    @Nested
    inner class Reset {

        @Test
        fun `reset clears detection state`() {
            repeat(4) { detector.recordAndCheck("exec", """{"command":"ls"}""") }
            detector.reset()
            val result = detector.recordAndCheck("exec", """{"command":"ls"}""")
            result.severity shouldBe ToolLoopDetector.Severity.NONE
        }
    }

    @Nested
    inner class Disabled {

        @Test
        fun `disabled detector always returns NONE`() {
            val disabledDetector = ToolLoopDetector(LoopDetectionConfig(enabled = false))
            repeat(50) {
                val result = disabledDetector.recordAndCheck("exec", """{"command":"ls"}""")
                result.severity shouldBe ToolLoopDetector.Severity.NONE
            }
        }
    }
}
