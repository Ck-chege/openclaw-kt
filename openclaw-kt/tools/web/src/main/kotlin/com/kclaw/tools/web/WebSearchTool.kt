package com.kclaw.tools.web

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import com.kclaw.skills.SkillEnvironment
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Web search tool using the Brave Search API.
 *
 * Requires `BRAVE_API_KEY` environment variable to be set.
 * Free tier allows 2000 queries/month.
 *
 * Results are cached in-memory for 15 minutes (max 100 entries).
 */
object WebSearchTool : SimpleTool<WebSearchTool.Args>(
    argsSerializer = Args.serializer(),
    name = "web_search",
    description = "Search the web using Brave Search API. Returns a list of results with title, URL, description, and age. Use this to find current information, documentation, or answers to questions."
) {
    @Serializable
    data class Args(
        @property:LLMDescription("The search query")
        val query: String,
        @property:LLMDescription("Maximum number of results to return (1-10, default: 5)")
        val count: Int = 5,
        @property:LLMDescription("Country code filter (e.g. 'US', 'CA')")
        val country: String? = null,
        @property:LLMDescription("Freshness filter: 'day', 'week', or 'month'")
        val freshness: String? = null
    )

    private const val BRAVE_API_URL = "https://api.search.brave.com/res/v1/web/search"
    private const val CACHE_TTL_MS = 15 * 60 * 1000L // 15 minutes
    private const val MAX_CACHE_SIZE = 100

    /** Visible for testing — allows injecting a mock client. */
    internal var httpClientOverride: io.ktor.client.HttpClient? = null

    /** Visible for testing — allows overriding the API key lookup. */
    internal var apiKeyOverride: String? = null

    private val client get() = httpClientOverride ?: WebToolsHttpClient.instance

    private data class CacheEntry(val result: String, val timestamp: Long)
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    /**
     * Returns true if the Brave Search API key is configured.
     */
    fun isAvailable(): Boolean =
        getApiKey() != null

    private fun getApiKey(): String? =
        apiKeyOverride
            ?: SkillEnvironment.get("BRAVE_API_KEY")?.takeIf { it.isNotBlank() }
            ?: System.getenv("BRAVE_API_KEY")?.takeIf { it.isNotBlank() }

    override suspend fun execute(args: Args): String {
        val apiKey = getApiKey()
            ?: return "Error: BRAVE_API_KEY environment variable is not set. Web search is unavailable."

        if (args.query.isBlank()) {
            return "Error: Search query cannot be empty."
        }

        val effectiveCount = args.count.coerceIn(1, 10)
        val cacheKey = buildCacheKey(args.query, effectiveCount, args.country, args.freshness)

        // Check cache
        val cached = cache[cacheKey]
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
            logger.debug { "web_search cache hit for: ${args.query}" }
            return cached.result
        }

        return try {
            val response = client.get(BRAVE_API_URL) {
                header("X-Subscription-Token", apiKey)
                header("Accept", "application/json")
                parameter("q", args.query)
                parameter("count", effectiveCount)
                if (args.country != null) parameter("country", args.country)
                if (args.freshness != null) parameter("freshness", args.freshness)
            }

            if (!response.status.isSuccess()) {
                val body = response.bodyAsText()
                return "Error: Brave Search API returned HTTP ${response.status.value}: $body"
            }

            val body = response.bodyAsText()
            val result = parseResults(body, args.query)

            // Cache the result (evict oldest if over limit)
            if (cache.size >= MAX_CACHE_SIZE) {
                evictOldest()
            }
            cache[cacheKey] = CacheEntry(result, System.currentTimeMillis())

            result
        } catch (e: Exception) {
            logger.debug(e) { "web_search failed for: ${args.query}" }
            "Error searching web: ${e.message ?: e::class.simpleName}"
        }
    }

    internal fun parseResults(jsonBody: String, query: String): String {
        val json = Json { ignoreUnknownKeys = true }
        val root = json.parseToJsonElement(jsonBody).jsonObject

        val webResults = root["web"]?.jsonObject?.get("results")?.jsonArray
        if (webResults == null || webResults.isEmpty()) {
            return "No results found for: $query"
        }

        return buildString {
            appendLine("Search results for: $query")
            appendLine()
            for ((i, result) in webResults.withIndex()) {
                val obj = result.jsonObject
                val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "Untitled"
                val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: ""
                val description = obj["description"]?.jsonPrimitive?.contentOrNull ?: ""
                val age = obj["age"]?.jsonPrimitive?.contentOrNull

                appendLine("${i + 1}. $title")
                appendLine("   URL: $url")
                if (description.isNotBlank()) appendLine("   $description")
                if (age != null) appendLine("   Age: $age")
                appendLine()
            }
        }.trimEnd()
    }

    private fun buildCacheKey(query: String, count: Int, country: String?, freshness: String?): String =
        "$query|$count|${country ?: ""}|${freshness ?: ""}"

    private fun evictOldest() {
        val oldest = cache.entries.minByOrNull { it.value.timestamp } ?: return
        cache.remove(oldest.key)
    }

    /** Clear the search cache. Visible for testing. */
    internal fun clearCache() {
        cache.clear()
    }
}
