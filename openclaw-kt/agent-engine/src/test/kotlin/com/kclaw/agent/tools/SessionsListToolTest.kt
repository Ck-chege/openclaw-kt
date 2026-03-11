package com.kclaw.agent.tools

import com.kclaw.protocol.identifiers.SessionKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SessionsListToolTest {

    private lateinit var store: FakeSessionStore
    private lateinit var tool: SessionsListTool

    @BeforeEach
    fun setup() {
        store = FakeSessionStore()
        tool = SessionsListTool(store)
    }

    @Nested
    inner class Execute {

        @Test
        fun `returns formatted list of sessions`() = runTest {
            store.createSession(SessionKey("main:main:*:main"), "main")
            store.createSession(SessionKey("trading:telegram:*:dm:123"), "trading", "telegram")

            val result = tool.execute(SessionsListTool.Args())

            result shouldContain "main:main:*:main"
            result shouldContain "agent=main"
            result shouldContain "trading:telegram:*:dm:123"
            result shouldContain "agent=trading"
            result shouldContain "messages=0"
            result shouldContain "model=default"
            result shouldContain "compactions=0"
        }

        @Test
        fun `returns no sessions message when empty`() = runTest {
            val result = tool.execute(SessionsListTool.Args())
            result shouldBe "No active sessions"
        }

        @Test
        fun `includes model when set`() = runTest {
            val key = SessionKey("main:main:*:main")
            store.createSession(key, "main")
            store.updateMeta(key) { it.copy(modelId = "anthropic:claude-sonnet-4-5") }

            val result = tool.execute(SessionsListTool.Args())

            result shouldContain "model=anthropic:claude-sonnet-4-5"
        }
    }
}
