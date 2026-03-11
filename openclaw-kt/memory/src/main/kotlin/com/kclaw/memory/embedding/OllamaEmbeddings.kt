package com.kclaw.memory.embedding

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

private val logger = KotlinLogging.logger {}

/**
 * Ollama embedding provider.
 * Posts to `${baseUrl}/api/embed` with `{ model, input }`.
 */
class OllamaEmbeddings(
    private val model: String = "nomic-embed-text",
    private val baseUrl: String = "http://localhost:11434"
) : EmbeddingProvider {

    override val dimensions: Int = when {
        model.contains("nomic-embed-text") -> 768
        model.contains("mxbai-embed-large") -> 1024
        model.contains("all-minilm") -> 384
        else -> 768
    }

    override val providerName: String = "ollama"

    /** Visible for testing — allows injecting a mock client. */
    internal var httpClientOverride: HttpClient? = null

    private val client get() = httpClientOverride ?: EmbeddingHttpClient.instance

    override suspend fun embed(texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()

        val requestBody = buildJsonObject {
            put("model", model)
            put("input", JsonArray(texts.map { JsonPrimitive(it) }))
        }

        val response = try {
            client.post("$baseUrl/api/embed") {
                contentType(ContentType.Application.Json)
                setBody(requestBody.toString())
            }
        } catch (e: Exception) {
            throw IllegalStateException("Failed to connect to Ollama at $baseUrl: ${e.message}", e)
        }

        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            throw IllegalStateException("Ollama API error (HTTP ${response.status.value}): $body")
        }

        val body = response.bodyAsText()
        val root = Json.parseToJsonElement(body).jsonObject
        val embeddings = root["embeddings"]?.jsonArray
            ?: throw IllegalStateException("Missing 'embeddings' field in Ollama response")

        return embeddings.map { embeddingArray ->
            val arr = embeddingArray.jsonArray
            FloatArray(arr.size) { i -> arr[i].jsonPrimitive.float }
        }
    }
}
