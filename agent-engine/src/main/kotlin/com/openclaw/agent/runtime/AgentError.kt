package com.openclaw.agent.runtime

import com.openclaw.protocol.identifiers.SessionKey

sealed class AgentError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class RunTimeout(val sessionKey: SessionKey, val timeoutMs: Long) :
        AgentError("Agent run timed out after ${timeoutMs}ms for session: ${sessionKey.raw}")

    class RunAlreadyActive(val sessionKey: SessionKey) :
        AgentError("A run is already active for session: ${sessionKey.raw}")

    class RunNotFound(val sessionKey: SessionKey) :
        AgentError("No active run found for session: ${sessionKey.raw}")

    class RunCancelled(val sessionKey: SessionKey) :
        AgentError("Run was cancelled for session: ${sessionKey.raw}")
}
