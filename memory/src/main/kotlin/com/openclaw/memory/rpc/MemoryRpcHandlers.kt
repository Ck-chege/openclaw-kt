package com.openclaw.memory.rpc

import com.openclaw.gateway.rpc.RpcRouter
import com.openclaw.memory.MemoryManager
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*

private val logger = KotlinLogging.logger {}

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Register RPC handlers for memory operations.
 *
 * Methods:
 * - `memory.status` — get memory system status
 * - `memory.index` — trigger indexing of workspace files
 * - `memory.search` — search indexed memory
 */
fun registerMemoryHandlers(router: RpcRouter, memoryManager: MemoryManager?) {

    router.register("memory.status") { _, _ ->
        logger.debug { "Handling memory.status" }
        if (memoryManager == null) {
            buildJsonObject {
                put("enabled", false)
            }
        } else {
            val status = memoryManager.status()
            json.encodeToJsonElement(status).jsonObject
        }
    }

    router.register("memory.index") { _, _ ->
        logger.debug { "Handling memory.index" }
        if (memoryManager == null) {
            buildJsonObject {
                put("error", "Memory system is not enabled")
            }
        } else {
            val stats = memoryManager.index()
            json.encodeToJsonElement(stats).jsonObject
        }
    }

    router.register("memory.search") { params, _ ->
        logger.debug { "Handling memory.search" }
        if (memoryManager == null) {
            buildJsonObject {
                put("error", "Memory system is not enabled")
                put("results", JsonArray(emptyList()))
            }
        } else {
            val query = params.requireString("query")
            val limit = params["limit"]?.jsonPrimitive?.intOrNull ?: 5
            val results = memoryManager.search(query, limit)
            buildJsonObject {
                put("results", json.encodeToJsonElement(results))
                put("count", results.size)
            }
        }
    }
}

private fun JsonObject.requireString(name: String): String =
    this[name]?.jsonPrimitive?.contentOrNull
        ?: throw IllegalArgumentException("Missing required parameter: '$name'")
