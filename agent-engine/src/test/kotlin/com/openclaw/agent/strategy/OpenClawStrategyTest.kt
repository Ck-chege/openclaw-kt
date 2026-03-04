package com.openclaw.agent.strategy

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class OpenClawStrategyTest {

    @Nested
    inner class StrategyConstruction {

        @Test
        fun `strategy builds without error`() {
            val strategy = buildOpenClawStrategy()
            // If we get here, the strategy DSL compiled and executed successfully
        }

        @Test
        fun `strategy builds with custom compaction threshold`() {
            val strategy = buildOpenClawStrategy(compactionThreshold = 50)
            // If we get here, the strategy DSL compiled and executed successfully
        }

        @Test
        fun `strategy builds with high compaction threshold`() {
            val strategy = buildOpenClawStrategy(compactionThreshold = 500)
            // If we get here, the strategy DSL compiled and executed successfully
        }
    }
}
