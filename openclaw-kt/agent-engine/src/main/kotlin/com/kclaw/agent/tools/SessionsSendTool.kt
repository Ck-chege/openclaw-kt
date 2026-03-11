package com.kclaw.agent.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import com.kclaw.agent.runtime.RunManager
import com.kclaw.protocol.identifiers.SessionKey
import kotlinx.serialization.Serializable

/**
 * Sends a steer message to another active session.
 *
 * Uses RunManager to check if the target session has an active run
 * and injects the message into that run's queue channel.
 */
class SessionsSendTool(
    private val runManager: RunManager
) : SimpleTool<SessionsSendTool.Args>(
    argsSerializer = Args.serializer(),
    name = "sessions_send",
    description = "Send a message to another active session. The target session must have an active run."
) {
    @Serializable
    data class Args(
        @property:LLMDescription("The target session key")
        val key: String,
        @property:LLMDescription("The message to send")
        val message: String
    )

    override suspend fun execute(args: Args): String {
        val sessionKey = SessionKey(args.key)
        val sent = runManager.steer(sessionKey, args.message)
        return if (sent) {
            "Message sent to session ${args.key}"
        } else {
            "Error: session ${args.key} is not active"
        }
    }
}
