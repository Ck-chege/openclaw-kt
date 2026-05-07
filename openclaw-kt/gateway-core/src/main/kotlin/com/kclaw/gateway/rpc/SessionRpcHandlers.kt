package com.kclaw.gateway.rpc

import com.kclaw.protocol.identifiers.SessionKey
import com.kclaw.session.SessionMeta
import com.kclaw.session.SessionMessage
import com.kclaw.session.SessionStore
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*

private val logger = KotlinLogging.logger {}

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Register RPC handlers for session lifecycle operations.
 *
 * Methods:
 * - `sessions.list` — list all sessions
 * - `sessions.get` — get metadata for a session by key
 * - `sessions.ensure` — create a session if it does not already exist
 * - `sessions.history` — get message history for a session
 * - `sessions.delete` — permanently delete a session
 * - `sessions.reset` — clear transcript, keep session entry
 */
fun registerSessionHandlers(router: RpcRouter, sessionStore: SessionStore) {

    // sessions.list -- Return metadata for all known sessions
    router.register("sessions.list") { _, _ ->
        logger.debug { "Handling sessions.list" }
        val sessions = sessionStore.listSessions()
        buildJsonObject {
            put("sessions", json.encodeToJsonElement<List<SessionMeta>>(sessions))
        }
    }

    // sessions.get -- Return metadata for a single session
    router.register("sessions.get") { params, _ ->
        logger.debug { "Handling sessions.get" }
        val key = params.requireString("key")
        val meta = sessionStore.getSession(SessionKey(key))
        buildJsonObject {
            if (meta != null) {
                put("session", json.encodeToJsonElement<SessionMeta>(meta))
            } else {
                put("session", JsonNull)
                put("error", JsonPrimitive("Session not found: $key"))
            }
        }
    }

    // sessions.ensure -- Idempotently create a session for a key
    router.register("sessions.ensure") { params, _ ->
        logger.debug { "Handling sessions.ensure" }
        val key = SessionKey(params.requireString("key"))
        val existing = sessionStore.getSession(key)
        val session = existing ?: sessionStore.createSession(key, key.agentId, key.channel)
        buildJsonObject {
            put("created", JsonPrimitive(existing == null))
            put("session", json.encodeToJsonElement<SessionMeta>(session))
        }
    }

    // sessions.history -- Return message history for a session
    router.register("sessions.history") { params, _ ->
        logger.debug { "Handling sessions.history" }
        val key = SessionKey(params.requireString("key"))
        val limit = params["limit"]?.jsonPrimitive?.intOrNull
        val messages = if (sessionStore.getSession(key) == null) {
            emptyList()
        } else {
            sessionStore.getHistory(key, limit)
        }
        buildJsonObject {
            put("messages", json.encodeToJsonElement<List<SessionMessage>>(messages))
            put("count", JsonPrimitive(messages.size))
        }
    }

    // sessions.delete -- Permanently delete a session and its transcript
    router.register("sessions.delete") { params, _ ->
        logger.debug { "Handling sessions.delete" }
        val key = params.requireString("key")
        sessionStore.deleteSession(SessionKey(key))
        buildJsonObject {
            put("deleted", JsonPrimitive(true))
            put("key", JsonPrimitive(key))
        }
    }

    // sessions.reset -- Clear transcript and reset counters, keep session entry
    router.register("sessions.reset") { params, _ ->
        logger.debug { "Handling sessions.reset" }
        val key = params.requireString("key")
        sessionStore.resetSession(SessionKey(key))
        buildJsonObject {
            put("reset", JsonPrimitive(true))
            put("key", JsonPrimitive(key))
        }
    }
}

/**
 * Extract a required string parameter, throwing with a clear message if missing.
 */
private fun JsonObject.requireString(name: String): String =
    this[name]?.jsonPrimitive?.contentOrNull
        ?: throw IllegalArgumentException("Missing required parameter: '$name'")
