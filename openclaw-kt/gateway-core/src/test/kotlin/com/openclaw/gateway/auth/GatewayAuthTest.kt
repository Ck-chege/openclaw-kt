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
        val operator = session.role.shouldBeInstanceOf<ClientRole.Operator>()
        operator.level shouldBe OperatorLevel.WRITE
        session.isAuthenticated shouldBe true
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
        session.isAuthenticated shouldBe false
    }

    @Test
    fun `webchat role assigns correct scopes`() {
        val auth = GatewayAuth(token = null, password = null)
        val session = auth.authenticate(null, "webchat")

        session.shouldNotBeNull()
        session.role.shouldBeInstanceOf<ClientRole.WebChat>()
        session.scopes shouldBe setOf("chat")
        session.isAuthenticated shouldBe false
    }

    @Test
    fun `node role authenticates with valid token`() {
        val auth = GatewayAuth(token = "tok", password = null)
        val session = auth.authenticate("tok", "node")

        session.shouldNotBeNull()
        session.role.shouldBeInstanceOf<ClientRole.Node>()
        session.scopes shouldBe setOf("chat", "agents")
        session.isAuthenticated shouldBe true
    }

    @Test
    fun `node role uses provided deviceId`() {
        val auth = GatewayAuth(token = null, password = null)
        val session = auth.authenticate(null, "node", deviceId = "my-device-123")

        session.shouldNotBeNull()
        val node = session.role.shouldBeInstanceOf<ClientRole.Node>()
        node.deviceId shouldBe "my-device-123"
        session.isAuthenticated shouldBe false
    }

    @Test
    fun `node role generates deviceId when not provided`() {
        val auth = GatewayAuth(token = null, password = null)
        val session = auth.authenticate(null, "node")

        session.shouldNotBeNull()
        val node = session.role.shouldBeInstanceOf<ClientRole.Node>()
        node.deviceId.shouldNotBeNull()
        node.deviceId.length shouldBe 36 // UUID format
        session.isAuthenticated shouldBe false
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
        session.isAuthenticated shouldBe false
    }

    @Test
    fun `password auth works when token is null`() {
        val auth = GatewayAuth(token = null, password = "my-password")
        val successSession = auth.authenticate("my-password", "operator")
        val failSession = auth.authenticate("wrong", "operator")

        successSession.shouldNotBeNull()
        successSession.isAuthenticated shouldBe true
        failSession.shouldBeNull()
    }

    @Test
    fun `operator gets full scopes`() {
        val auth = GatewayAuth(token = null, password = null)
        val session = auth.authenticate(null, "operator")

        session.shouldNotBeNull()
        session.scopes shouldBe setOf("chat", "config", "agents", "tools", "admin")
        session.isAuthenticated shouldBe false
    }
}
