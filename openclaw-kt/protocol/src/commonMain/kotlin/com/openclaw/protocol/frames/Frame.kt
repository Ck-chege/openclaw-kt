package com.openclaw.protocol.frames

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Wire protocol frame exchanged over WebSocket connections.
 *
 * Every frame carries an [id] for correlation. The concrete type is encoded
 * in the JSON `"type"` discriminator field:
 *
 * | Variant   | Direction            | Discriminator |
 * |-----------|----------------------|---------------|
 * | [Request] | client -> gateway    | `"req"`       |
 * | [Response]| gateway -> client    | `"res"`       |
 * | [Event]   | gateway -> client    | `"event"`     |
 */
@Serializable
sealed class Frame {
    /** Correlation identifier shared between request and response. */
    abstract val id: String

    /**
     * A client-to-gateway request frame.
     *
     * ```json
     * { "type": "req", "id": "abc-123", "method": "chat.send", "params": { ... } }
     * ```
     */
    @Serializable
    @SerialName("req")
    data class Request(
        override val id: String,
        val method: String,
        val params: JsonObject = JsonObject(emptyMap()),
    ) : Frame()

    /**
     * A gateway-to-client response frame, correlated to a prior [Request] by [id].
     *
     * ```json
     * { "type": "res", "id": "abc-123", "ok": true, "payload": { ... } }
     * ```
     */
    @Serializable
    @SerialName("res")
    data class Response(
        override val id: String,
        val ok: Boolean,
        val payload: JsonObject = JsonObject(emptyMap()),
    ) : Frame()

    /**
     * A server-initiated push event frame.
     *
     * ```json
     * { "type": "event", "id": "evt-456", "event": "agent.token", "payload": { ... } }
     * ```
     */
    @Serializable
    @SerialName("event")
    data class Event(
        override val id: String,
        val event: String,
        val payload: JsonObject = JsonObject(emptyMap()),
    ) : Frame()

    companion object {
        /** Shared [Json] instance configured for the wire protocol. */
        private val json = Json {
            ignoreUnknownKeys = true
            classDiscriminator = "type"
        }

        /** Encode a [Frame] to its JSON wire representation. */
        fun serialize(frame: Frame): String = json.encodeToString(serializer(), frame)

        /** Decode a JSON wire string into a typed [Frame]. */
        fun deserialize(text: String): Frame = json.decodeFromString(serializer(), text)
    }
}
