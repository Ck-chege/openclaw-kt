package com.openclaw.protocol.dto

import kotlinx.serialization.Serializable

@Serializable
sealed class QueueAction {
    @Serializable
    data class NewMessage(val envelope: InboundEnvelope) : QueueAction()

    @Serializable
    data class Steer(val envelope: InboundEnvelope) : QueueAction()

    @Serializable
    data object Cancel : QueueAction()
}
