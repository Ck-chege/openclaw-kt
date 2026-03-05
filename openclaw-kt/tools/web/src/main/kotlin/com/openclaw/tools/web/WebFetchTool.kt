package com.openclaw.tools.web

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

/**
 * Fetches a URL and extracts readable content.
 *
 * - HTML pages are converted to markdown via [ContentExtractor]
 * - JSON responses are pretty-printed
 * - Plain text is returned as-is
 * - Content is truncated to [Args.maxChars]
 */
object WebFetchTool : SimpleTool<WebFetchTool.Args>(
    argsSerializer = Args.serializer(),
    name = "web_fetch",
    description = "Fetch a web page or API endpoint and extract its content as readable text. Returns the page content converted to markdown (for HTML) or pretty-printed (for JSON). Use this to read documentation, articles, or API responses."
) {
    @Serializable
    data class Args(
        @property:LLMDescription("The HTTP or HTTPS URL to fetch")
        val url: String,
        @property:LLMDescription("Maximum characters to return (default: 50000)")
        val maxChars: Int = 50_000,
        @property:LLMDescription("Extract mode: 'markdown' (default) or 'text'")
        val extractMode: String = "markdown"
    )

    internal const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024 // 2MB

    private val prettyJson = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /** Visible for testing — allows injecting a mock client. */
    internal var httpClientOverride: io.ktor.client.HttpClient? = null

    private val client get() = httpClientOverride ?: WebToolsHttpClient.instance

    override suspend fun execute(args: Args): String {
        // Validate URL scheme
        val url = args.url.trim()
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return "Error: Only HTTP and HTTPS URLs are supported. Got: $url"
        }

        val effectiveMaxChars = args.maxChars.coerceIn(100, 200_000)

        return try {
            val response = client.get(url)

            if (!response.status.isSuccess()) {
                return "Error: HTTP ${response.status.value} ${response.status.description} for $url"
            }

            val contentType = response.contentType()
            val bodyBytes = response.readRawBytes()

            if (bodyBytes.size > MAX_RESPONSE_BYTES) {
                return "Error: Response too large (${bodyBytes.size} bytes, max ${MAX_RESPONSE_BYTES})"
            }

            val body = bodyBytes.decodeToString()

            val content = when {
                contentType?.match(ContentType.Application.Json) == true ||
                    contentType?.toString()?.contains("json", ignoreCase = true) == true -> {
                    formatJson(body)
                }
                contentType?.match(ContentType.Text.Html) == true ||
                    contentType?.toString()?.contains("html", ignoreCase = true) == true ||
                    body.trimStart().startsWith("<!") ||
                    body.trimStart().startsWith("<html", ignoreCase = true) -> {
                    extractHtml(body, url, args.extractMode)
                }
                else -> body
            }

            val title = if (body.contains("<title", ignoreCase = true)) {
                ContentExtractor.extractTitle(body)
            } else null

            val result = buildString {
                appendLine("--- Web Fetch Result ---")
                appendLine("URL: $url")
                if (title != null) appendLine("Title: $title")
                appendLine("Content-Type: ${contentType ?: "unknown"}")
                appendLine("---")
                appendLine()
                append(content)
            }

            truncate(result, effectiveMaxChars)
        } catch (e: Exception) {
            logger.debug(e) { "web_fetch failed for $url" }
            "Error fetching $url: ${e.message ?: e::class.simpleName}"
        }
    }

    private fun extractHtml(html: String, baseUrl: String, mode: String): String {
        return if (mode == "text") {
            org.jsoup.Jsoup.parse(html, baseUrl).text()
        } else {
            ContentExtractor.extract(html, baseUrl)
        }
    }

    private fun formatJson(json: String): String {
        return try {
            val element = prettyJson.parseToJsonElement(json)
            prettyJson.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), element)
        } catch (_: Exception) {
            json // Return raw if not valid JSON
        }
    }

    internal fun truncate(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        return text.substring(0, maxChars) + "\n\n[... truncated at $maxChars characters ...]"
    }
}
