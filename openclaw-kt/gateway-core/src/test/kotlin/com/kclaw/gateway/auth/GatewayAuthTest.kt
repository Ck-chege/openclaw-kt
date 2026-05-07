package com.kclaw.gateway.auth

import com.kclaw.protocol.identifiers.OperatorLevel
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class GatewayAuthTest {

    @Test
    fun `valid token authenticates operator`() {
        val auth = GatewayAuth(token = "secret-token", password = null)
        val session = auth.authenticate("secret-token", ClientRole.Operator(OperatorLevel.WRITE))

        session.shouldNotBeNull()
        val operator = session.role.shouldBeInstanceOf<ClientRole.Operator>()
        operator.level shouldBe OperatorLevel.WRITE
        session.isAuthenticated shouldBe true
    }

    @Test
    fun `invalid token returns null`() {
        val auth = GatewayAuth(token = "secret-token", password = null)
        val session = auth.authenticate("wrong-token", ClientRole.Operator(OperatorLevel.WRITE))

        session.shouldBeNull()
    }

    @Test
    fun `null token with no configured auth allows connection`() {
        val auth = GatewayAuth(token = null, password = null)
        val session = auth.authenticate(null, ClientRole.Operator(OperatorLevel.WRITE))

        session.shouldNotBeNull()
        session.role.shouldBeInstanceOf<ClientRole.Operator>()
        session.isAuthenticated shouldBe false
    }

    @Test
    fun `webchat role assigns correct scopes`() {
        val auth = GatewayAuth(token = null, password = null)
        val session = auth.authenticate(null, ClientRole.WebChat)

        session.shouldNotBeNull()
        session.role.shouldBeInstanceOf<ClientRole.WebChat>()
        session.scopes shouldBe setOf("chat")
        session.isAuthenticated shouldBe false
    }

    @Test
    fun `node role authenticates with valid token`() {
        val auth = GatewayAuth(token = "tok", password = null)
        val session = auth.authenticate("tok", ClientRole.Node("node-device"))

        session.shouldNotBeNull()
        session.role.shouldBeInstanceOf<ClientRole.Node>()
        session.scopes shouldBe setOf("chat", "agents")
        session.isAuthenticated shouldBe true
    }

    @Test
    fun `node role uses provided deviceId`() {
        val role = ClientRole.fromWire("node", deviceId = "my-device-123")

        role.shouldNotBeNull()
        val node = role.shouldBeInstanceOf<ClientRole.Node>()
        node.deviceId shouldBe "my-device-123"
    }

    @Test
    fun `node role generates deviceId when not provided`() {
        val role = ClientRole.fromWire("node")

        role.shouldNotBeNull()
        val node = role.shouldBeInstanceOf<ClientRole.Node>()
        node.deviceId.shouldNotBeNull()
        node.deviceId.length shouldBe 36 // UUID format
    }

    @Test
    fun `unknown role returns null`() {
        val role = ClientRole.fromWire("superadmin")

        role.shouldBeNull()
    }

    @Test
    fun `null role defaults to operator`() {
        val role = ClientRole.fromWire(null)

        role.shouldNotBeNull()
        role.shouldBeInstanceOf<ClientRole.Operator>()
    }

    @Test
    fun `password auth works when token is null`() {
        val auth = GatewayAuth(token = null, password = "my-password")
        val successSession = auth.authenticate("my-password", ClientRole.Operator(OperatorLevel.WRITE))
        val failSession = auth.authenticate("wrong", ClientRole.Operator(OperatorLevel.WRITE))

        successSession.shouldNotBeNull()
        successSession.isAuthenticated shouldBe true
        failSession.shouldBeNull()
    }

    @Test
    fun `operator gets full scopes`() {
        val auth = GatewayAuth(token = null, password = null)
        val session = auth.authenticate(null, ClientRole.Operator(OperatorLevel.WRITE))

        session.shouldNotBeNull()
        session.scopes shouldBe setOf("chat", "config", "agents", "tools", "admin")
        session.isAuthenticated shouldBe false
    }
}
