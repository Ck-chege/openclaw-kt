package com.openclaw.protocol.dto

import com.openclaw.protocol.identifiers.ChatType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class InboundEnvelope(
    val channel: String,
    val senderId: String,
    val senderName: String,
    val chatId: String,
    val chatType: ChatType,
    val text: String,
    val messageId: String? = null,
    val replyToId: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val raw: JsonObject? = null
)

@Serializable
data class Attachment(
    val type: AttachmentType,
    val url: String? = null,
    val data: ByteArray? = null,
    val mimeType: String? = null,
    val filename: String? = null,
    val size: Long? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Attachment) return false
        return type == other.type && url == other.url && mimeType == other.mimeType && filename == other.filename && size == other.size
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + (url?.hashCode() ?: 0)
        result = 31 * result + (mimeType?.hashCode() ?: 0)
        result = 31 * result + (filename?.hashCode() ?: 0)
        return result
    }
}

@Serializable
enum class AttachmentType {
    IMAGE, AUDIO, VIDEO, FILE, VOICE, STICKER
}
