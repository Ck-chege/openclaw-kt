package com.openclaw.gateway.auth

import com.openclaw.protocol.identifiers.OperatorLevel
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class GatewayAuthTest {

    @Test
    fun `valid token authenticates operator`() {
        val auth = GatewayAuth(token = "secret-token", password = null)
        val session = auth.authenticate("secret-token", "operator")

        session.shouldNotBeNull()
        session.role.shouldBeInstanceOf<ClientRole.Operator>()
        (session.role as ClientRole.Operator).level shouldBe OperatorLevel.WRITE
    }

    @Test
    fun `invalid token returns null`() {
        val auth = GatewayAuth(token = "secret-token", password = null)
        val session = auth.authenticate("wrong-token", "operator")

        session.shouldBeNull()
    }

    @Test
    fun `null token with no configured auth allows connection`() {
        val auth = GatewayAuth(token = null, password = null)
        val session = auth.authenticate(null, "operator")

        session.shouldNotBeNull()
        session.role.shouldBeInstanceOf<ClientRole.Operator>()
    }

    @Test
    fun `webchat role assigns correct scopes`() {
        val auth = GatewayAuth(token = null, password = null)
        val session = auth.authenticate(null, "webchat")

        session.shouldNotBeNull()
        session.role.shouldBeInstanceOf<ClientRole.WebChat>()
        session.scopes shouldBe setOf("chat")
    }

    @Test
    fun `node role authenticates with valid token`() {
        val auth = GatewayAuth(token = "tok", password = null)
        val session = auth.authenticate("tok", "node")

        session.shouldNotBeNull()
        session.role.shouldBeInstanceOf<ClientRole.Node>()
        session.scopes shouldBe setOf("chat", "agents")
    }

    @Test
    fun `unknown role returns null`() {
        val auth = GatewayAuth(token = null, password = null)
        val session = auth.authenticate(null, "superadmin")

        session.shouldBeNull()
    }

    @Test
    fun `null role defaults to operator`() {
        val auth = GatewayAuth(token = null, password = null)
        val session = auth.authenticate(null, null)

        session.shouldNotBeNull()
        session.role.shouldBeInstanceOf<ClientRole.Operator>()
    }

    @Test
    fun `password auth works when token is null`() {
        val auth = GatewayAuth(token = null, password = "my-password")
        val successSession = auth.authenticate("my-password", "operator")
        val failSession = auth.authenticate("wrong", "operator")

        successSession.shouldNotBeNull()
        failSession.shouldBeNull()
    }

    @Test
    fun `operator gets full scopes`() {
        val auth = GatewayAuth(token = null, password = null)
        val session = auth.authenticate(null, "operator")

        session.shouldNotBeNull()
        session.scopes shouldBe setOf("chat", "config", "agents", "tools", "admin")
    }
}
