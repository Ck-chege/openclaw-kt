package com.kclaw.memory.embedding

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

private val logger = KotlinLogging.logger {}

/**
 * OpenAI-compatible embedding provider.
 * Posts to `${baseUrl}/embeddings` with `{ model, input }`.
 */
class OpenAIEmbeddings(
    private val apiKey: String,
    private val model: String = "text-embedding-3-small",
    private val baseUrl: String = "https://api.openai.com/v1"
) : EmbeddingProvider {

    override val dimensions: Int = when {
        model.contains("text-embedding-3-small") -> 1536
        model.contains("text-embedding-3-large") -> 3072
        model.contains("text-embedding-ada") -> 1536
        else -> 1536
    }

    override val providerName: String = "openai"

    /** Visible for testing — allows injecting a mock client. */
    internal var httpClientOverride: HttpClient? = null

    private val client get() = httpClientOverride ?: EmbeddingHttpClient.instance

    override suspend fun embed(texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()

        val requestBody = buildJsonObject {
            put("model", model)
            put("input", JsonArray(texts.map { JsonPrimitive(it) }))
        }

        val response = client.post("$baseUrl/embeddings") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $apiKey")
            setBody(requestBody.toString())
        }

        if (response.status.value == 401) {
            throw IllegalStateException("OpenAI API authentication failed: invalid API key")
        }
        if (response.status.value == 429) {
            throw IllegalStateException("OpenAI API rate limit exceeded")
        }
        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            throw IllegalStateException("OpenAI API error (HTTP ${response.status.value}): $body")
        }

        val body = response.bodyAsText()
        val root = Json.parseToJsonElement(body).jsonObject
        val data = root["data"]?.jsonArray
            ?: throw IllegalStateException("Missing 'data' field in OpenAI response")

        return data.map { entry ->
            val embedding = entry.jsonObject["embedding"]?.jsonArray
                ?: throw IllegalStateException("Missing 'embedding' field in response data")
            FloatArray(embedding.size) { i -> embedding[i].jsonPrimitive.float }
        }
    }
}
