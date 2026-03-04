package com.openclaw.session.compaction

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CompactionConfigTest : FunSpec({

    test("default trigger threshold is 160000 (80% of 200K)") {
        val config = CompactionConfig()
        config.triggerThreshold shouldBe 160_000
    }

    test("custom ratio calculates correctly") {
        val config = CompactionConfig(maxContextTokens = 100_000, triggerRatio = 0.75)
        config.triggerThreshold shouldBe 75_000
    }

    test("custom maxContextTokens calculates correctly") {
        val config = CompactionConfig(maxContextTokens = 128_000, triggerRatio = 0.80)
        config.triggerThreshold shouldBe 102_400
    }
})
