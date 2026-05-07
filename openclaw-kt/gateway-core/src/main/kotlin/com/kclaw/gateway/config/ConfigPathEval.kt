package com.kclaw.gateway.config

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json

private val pathEvalJson = Json { encodeDefaults = true }

fun GatewayConfig.isTruthyPath(path: String): Boolean {
    if (path.isBlank()) return false
    val root = pathEvalJson.encodeToJsonElement(GatewayConfig.serializer(), this)
    val resolved = resolveJsonPath(root, path) ?: return false
    return resolved.isTruthy()
}

private fun resolveJsonPath(root: JsonElement, path: String): JsonElement? {
    var current: JsonElement = root
    for (segment in path.split('.').filter { it.isNotBlank() }) {
        current = (current as? JsonObject)?.get(segment) ?: return null
    }
    return current
}

private fun JsonElement.isTruthy(): Boolean = when (this) {
    JsonNull -> false
    is JsonObject -> isNotEmpty()
    is JsonArray -> isNotEmpty()
    is JsonPrimitive -> jsonPrimitive.isTruthy()
}

private fun JsonPrimitive.isTruthy(): Boolean {
    booleanOrNull?.let { return it }
    doubleOrNull?.let { return it != 0.0 }
    return contentOrNull?.isNotBlank() == true
}