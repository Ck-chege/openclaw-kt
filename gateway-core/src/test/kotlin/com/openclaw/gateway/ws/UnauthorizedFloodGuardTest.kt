package com.openclaw.gateway.ws

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class UnauthorizedFloodGuardTest {

    @Test
    fun `first register returns Log`() {
        val guard = UnauthorizedFloodGuard(closeAfter = 10)
        val decision = guard.register()
        decision.shouldBeInstanceOf<UnauthorizedFloodGuard.Decision.Log>()
        decision.count shouldBe 1
        decision.suppressed shouldBe 0
    }

    @Test
    fun `second through eighth registers return Suppress`() {
        val guard = UnauthorizedFloodGuard(closeAfter = 10, logEvery = 100)
        guard.register() // 1 → Log

        for (i in 2..8) {
            val decision = guard.register()
            decision.shouldBeInstanceOf<UnauthorizedFloodGuard.Decision.Suppress>()
        }
    }

    @Test
    fun `close after configured threshold`() {
        val guard = UnauthorizedFloodGuard(closeAfter = 5)
        repeat(4) { guard.register() }
        val decision = guard.register() // 5th
        decision.shouldBeInstanceOf<UnauthorizedFloodGuard.Decision.Close>()
        decision.count shouldBe 5
    }

    @Test
    fun `logs every logEvery occurrences`() {
        val guard = UnauthorizedFloodGuard(closeAfter = 1000, logEvery = 5)
        guard.register() // 1 → Log
        guard.register() // 2 → Suppress
        guard.register() // 3 → Suppress
        guard.register() // 4 → Suppress
        val decision = guard.register() // 5 → Log (5 % 5 == 0)
        decision.shouldBeInstanceOf<UnauthorizedFloodGuard.Decision.Log>()
        decision.count shouldBe 5
        decision.suppressed shouldBe 3 // 2,3,4 were suppressed
    }

    @Test
    fun `reset clears the count`() {
        val guard = UnauthorizedFloodGuard(closeAfter = 10)
        repeat(5) { guard.register() }
        guard.reset()
        val decision = guard.register()
        decision.shouldBeInstanceOf<UnauthorizedFloodGuard.Decision.Log>()
        decision.count shouldBe 1
    }
}
