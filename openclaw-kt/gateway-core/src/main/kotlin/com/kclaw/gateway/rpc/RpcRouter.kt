package com.kclaw.gateway.rpc

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonObject

private val logger = KotlinLogging.logger {}

/**
 * A handler for a single RPC method.
 */
fun interface RpcHandler {
    suspend fun handle(params: JsonObject, sessionId: String): JsonObject
}

/**
 * Routes incoming RPC method calls to their registered [RpcHandler]s.
 *
 * Handlers are registered by method name (e.g., "config.get", "chat.send").
 * Calling [route] with an unregistered method throws [IllegalArgumentException].
 */
class RpcRouter {
    private val handlers = mutableMapOf<String, RpcHandler>()

    /**
     * Register a handler for the given RPC method name.
     */
    fun register(method: String, handler: RpcHandler) {
        logger.debug { "Registering RPC handler for method: $method" }
        handlers[method] = handler
    }

    /**
     * Check whether a handler exists for the given method.
     */
    fun hasHandler(method: String): Boolean = method in handlers

    /**
     * Route an RPC call to its handler.
     *
     * @throws IllegalArgumentException if no handler is registered for [method].
     */
    suspend fun route(method: String, params: JsonObject, sessionId: String): JsonObject {
        val handler = handlers[method]
            ?: throw IllegalArgumentException("No handler registered for method: $method")
        logger.debug { "Routing RPC call: method=$method, sessionId=$sessionId" }
        return handler.handle(params, sessionId)
    }

    /**
     * Return the set of all registered method names.
     */
    fun registeredMethods(): Set<String> = handlers.keys.toSet()
}
