package com.kclaw.tools.policy

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ToolGroupTest {

    @Nested
    inner class Expand {

        @Test
        fun `group runtime expands to exec and process`() {
            ToolGroup.expand("group:runtime") shouldBe setOf("exec", "process")
        }

        @Test
        fun `group fs expands to read, write, edit`() {
            ToolGroup.expand("group:fs") shouldBe setOf("read", "write", "edit")
        }

        @Test
        fun `group sessions expands to session tools`() {
            val expanded = ToolGroup.expand("group:sessions")!!
            expanded shouldBe setOf("sessions_list", "sessions_history", "sessions_send", "sessions_spawn", "session_status")
        }

        @Test
        fun `group kclaw expands to kclaw tools`() {
            val expanded = ToolGroup.expand("group:kclaw")!!
            expanded shouldBe setOf("cron", "gateway", "agents_list", "session_status")
        }

        @Test
        fun `legacy group openclaw alias still expands`() {
            val expanded = ToolGroup.expand("group:openclaw")!!
            expanded shouldBe setOf("cron", "gateway", "agents_list", "session_status")
        }

        @Test
        fun `plain tool name returns null`() {
            ToolGroup.expand("exec").shouldBeNull()
        }

        @Test
        fun `unknown group returns null`() {
            ToolGroup.expand("group:unknown").shouldBeNull()
        }
    }

    @Nested
    inner class ExpandAll {

        @Test
        fun `expands mixed groups and plain names`() {
            val result = ToolGroup.expandAll(listOf("group:runtime", "read", "custom_tool"))
            result.shouldContainExactlyInAnyOrder("exec", "process", "read", "custom_tool")
        }

        @Test
        fun `empty list returns empty set`() {
            ToolGroup.expandAll(emptyList()) shouldBe emptySet()
        }

        @Test
        fun `case insensitive group name matching`() {
            val result = ToolGroup.expand("group:RUNTIME")
            result shouldBe setOf("exec", "process")
        }
    }
}
