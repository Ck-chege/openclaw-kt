package com.kclaw.protocol

import com.kclaw.protocol.frames.Frame
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FrameTest {

    // ------------------------------------------------------------------ //
    // Round-trip serialization
    // ------------------------------------------------------------------ //

    @Test
    fun requestRoundTrip() {
        val params = buildJsonObject { put("text", "hello") }
        val original = Frame.Request(id = "r-1", method = "chat.send", params = params)

        val json = Frame.serialize(original)
        val restored = Frame.deserialize(json)

        assertIs<Frame.Request>(restored)
        assertEquals(original, restored)
    }

    @Test
    fun responseRoundTrip() {
        val payload = buildJsonObject { put("messageId", "m-42") }
        val original = Frame.Response(id = "r-1", ok = true, payload = payload)

        val json = Frame.serialize(original)
        val restored = Frame.deserialize(json)

        assertIs<Frame.Response>(restored)
        assertEquals(original, restored)
    }

    @Test
    fun eventRoundTrip() {
        val payload = buildJsonObject { put("token", "Hi") }
        val original = Frame.Event(id = "e-1", event = "agent.token", payload = payload)

        val json = Frame.serialize(original)
        val restored = Frame.deserialize(json)

        assertIs<Frame.Event>(restored)
        assertEquals(original, restored)
    }

    // ------------------------------------------------------------------ //
    // Type discriminator in JSON output
    // ------------------------------------------------------------------ //

    @Test
    fun requestJsonContainsTypeReq() {
        val frame = Frame.Request(id = "1", method = "ping")
        val jsonStr = Frame.serialize(frame)
        val tree = Json.parseToJsonElement(jsonStr) as JsonObject

        assertEquals(JsonPrimitive("req"), tree["type"])
    }

    @Test
    fun responseJsonContainsTypeRes() {
        val frame = Frame.Response(id = "2", ok = false)
        val jsonStr = Frame.serialize(frame)
        val tree = Json.parseToJsonElement(jsonStr) as JsonObject

        assertEquals(JsonPrimitive("res"), tree["type"])
    }

    @Test
    fun eventJsonContainsTypeEvent() {
        val frame = Frame.Event(id = "3", event = "status.update")
        val jsonStr = Frame.serialize(frame)
        val tree = Json.parseToJsonElement(jsonStr) as JsonObject

        assertEquals(JsonPrimitive("event"), tree["type"])
    }

    // ------------------------------------------------------------------ //
    // Malformed JSON
    // ------------------------------------------------------------------ //

    @Test
    fun deserializeMalformedJsonThrowsException() {
        assertFailsWith<Exception> {
            Frame.deserialize("not json at all")
        }
    }

    @Test
    fun deserializeIncompleteJsonThrowsException() {
        assertFailsWith<Exception> {
            Frame.deserialize("""{ "type": "req" }""")
        }
    }

    @Test
    fun deserializeUnknownTypeThrowsException() {
        assertFailsWith<Exception> {
            Frame.deserialize("""{ "type": "unknown", "id": "1" }""")
        }
    }

    // ------------------------------------------------------------------ //
    // Unknown keys are ignored
    // ------------------------------------------------------------------ //

    @Test
    fun unknownKeysAreIgnoredForRequest() {
        val input = """{ "type": "req", "id": "u-1", "method": "ping", "extra": 42 }"""
        val frame = Frame.deserialize(input)

        assertIs<Frame.Request>(frame)
        assertEquals("u-1", frame.id)
        assertEquals("ping", frame.method)
    }

    @Test
    fun unknownKeysAreIgnoredForResponse() {
        val input = """{ "type": "res", "id": "u-2", "ok": true, "debug": "info" }"""
        val frame = Frame.deserialize(input)

        assertIs<Frame.Response>(frame)
        assertEquals("u-2", frame.id)
        assertTrue(frame.ok)
    }

    @Test
    fun unknownKeysAreIgnoredForEvent() {
        val input = """{ "type": "event", "id": "u-3", "event": "tick", "trace": [1,2] }"""
        val frame = Frame.deserialize(input)

        assertIs<Frame.Event>(frame)
        assertEquals("u-3", frame.id)
        assertEquals("tick", frame.event)
    }

    // ------------------------------------------------------------------ //
    // Default params / payload
    // ------------------------------------------------------------------ //

    @Test
    fun requestDefaultParamsIsEmptyJsonObject() {
        val frame = Frame.Request(id = "d-1", method = "noop")
        assertEquals(JsonObject(emptyMap()), frame.params)

        // Also verify it round-trips through JSON
        val restored = Frame.deserialize(Frame.serialize(frame))
        assertIs<Frame.Request>(restored)
        assertEquals(JsonObject(emptyMap()), restored.params)
    }

    @Test
    fun responseDefaultPayloadIsEmptyJsonObject() {
        val frame = Frame.Response(id = "d-2", ok = true)
        assertEquals(JsonObject(emptyMap()), frame.payload)

        val restored = Frame.deserialize(Frame.serialize(frame))
        assertIs<Frame.Response>(restored)
        assertEquals(JsonObject(emptyMap()), restored.payload)
    }

    @Test
    fun eventDefaultPayloadIsEmptyJsonObject() {
        val frame = Frame.Event(id = "d-3", event = "heartbeat")
        assertEquals(JsonObject(emptyMap()), frame.payload)

        val restored = Frame.deserialize(Frame.serialize(frame))
        assertIs<Frame.Event>(restored)
        assertEquals(JsonObject(emptyMap()), restored.payload)
    }
}
