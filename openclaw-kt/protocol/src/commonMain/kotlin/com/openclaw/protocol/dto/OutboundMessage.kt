package com.openclaw.protocol.dto

import kotlinx.serialization.Serializable

@Serializable
data class OutboundMessage(
    val text: String,
    val replyToId: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val format: MessageFormat = MessageFormat.MARKDOWN
)

@Serializable
enum class MessageFormat {
    PLAIN, MARKDOWN, HTML
}

@Serializable
data class OutboundTarget(
    val channel: String,
    val chatId: String,
    val threadId: String? = null
)
