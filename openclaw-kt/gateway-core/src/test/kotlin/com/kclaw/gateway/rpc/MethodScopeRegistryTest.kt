package com.kclaw.gateway.rpc

import com.kclaw.protocol.identifiers.OperatorLevel
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MethodScopeRegistryTest {

    @Nested
    inner class RequiredScope {

        @Test
        fun `read methods require READ`() {
            MethodScopeRegistry.requiredScope("health") shouldBe OperatorLevel.READ
            MethodScopeRegistry.requiredScope("sessions.list") shouldBe OperatorLevel.READ
            MethodScopeRegistry.requiredScope("skills.list") shouldBe OperatorLevel.READ
            MethodScopeRegistry.requiredScope("workspace.read") shouldBe OperatorLevel.READ
            MethodScopeRegistry.requiredScope("tools.catalog") shouldBe OperatorLevel.READ
            MethodScopeRegistry.requiredScope("usage.summary") shouldBe OperatorLevel.READ
            MethodScopeRegistry.requiredScope("logs.recent") shouldBe OperatorLevel.READ
        }

        @Test
        fun `write methods require WRITE`() {
            MethodScopeRegistry.requiredScope("chat.send") shouldBe OperatorLevel.WRITE
            MethodScopeRegistry.requiredScope("agent.run") shouldBe OperatorLevel.WRITE
            MethodScopeRegistry.requiredScope("skills.refresh") shouldBe OperatorLevel.WRITE
            MethodScopeRegistry.requiredScope("skills.update") shouldBe OperatorLevel.WRITE
        }

        @Test
        fun `approval methods require APPROVALS`() {
            MethodScopeRegistry.requiredScope("exec.approval.request") shouldBe OperatorLevel.APPROVALS
        }

        @Test
        fun `admin methods require ADMIN`() {
            MethodScopeRegistry.requiredScope("config.apply") shouldBe OperatorLevel.ADMIN
            MethodScopeRegistry.requiredScope("sessions.delete") shouldBe OperatorLevel.ADMIN
        }

        @Test
        fun `unknown methods default to ADMIN`() {
            MethodScopeRegistry.requiredScope("totally.unknown.method") shouldBe OperatorLevel.ADMIN
        }
    }

    @Nested
    inner class IsAuthorized {

        @Test
        fun `admin scope can access everything`() {
            MethodScopeRegistry.isAuthorized("health", setOf("admin")) shouldBe true
            MethodScopeRegistry.isAuthorized("config.apply", setOf("admin")) shouldBe true
            MethodScopeRegistry.isAuthorized("chat.send", setOf("admin")) shouldBe true
        }

        @Test
        fun `read scope can access read methods`() {
            MethodScopeRegistry.isAuthorized("health", setOf("read")) shouldBe true
            MethodScopeRegistry.isAuthorized("sessions.list", setOf("read")) shouldBe true
            MethodScopeRegistry.isAuthorized("workspace.read", setOf("read")) shouldBe true
            MethodScopeRegistry.isAuthorized("logs.recent", setOf("read")) shouldBe true
        }

        @Test
        fun `read scope cannot access write methods`() {
            MethodScopeRegistry.isAuthorized("chat.send", setOf("read")) shouldBe false
        }

        @Test
        fun `write scope implies read`() {
            MethodScopeRegistry.isAuthorized("health", setOf("write")) shouldBe true
            MethodScopeRegistry.isAuthorized("chat.send", setOf("write")) shouldBe true
        }

        @Test
        fun `write scope cannot access admin methods`() {
            MethodScopeRegistry.isAuthorized("config.apply", setOf("write")) shouldBe false
        }

        @Test
        fun `approvals scope can access approval methods`() {
            MethodScopeRegistry.isAuthorized("exec.approval.resolve", setOf("approvals")) shouldBe true
        }

        @Test
        fun `approvals scope cannot access write methods`() {
            MethodScopeRegistry.isAuthorized("chat.send", setOf("approvals")) shouldBe false
        }

        @Test
        fun `empty scopes deny everything`() {
            MethodScopeRegistry.isAuthorized("health", emptySet()) shouldBe false
        }
    }
}
