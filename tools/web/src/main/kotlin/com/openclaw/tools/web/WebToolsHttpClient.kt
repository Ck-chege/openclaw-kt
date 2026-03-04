package com.openclaw.tools.web

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Shared Ktor HttpClient for all web tools.
 * Lazy-initialized singleton with browser-like defaults.
 */
object WebToolsHttpClient {

    private const val USER_AGENT =
        "Mozilla/5.0 (compatible; OpenClaw/1.0; +https://github.com/openclaw)"

    const val CONNECT_TIMEOUT_MS = 30_000L
    const val REQUEST_TIMEOUT_MS = 30_000L
    const val MAX_REDIRECTS = 3

    val instance: HttpClient by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }

            install(HttpTimeout) {
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
            }

            install(HttpRedirect) {
                checkHttpMethod = false
                allowHttpsDowngrade = false
            }

            install(UserAgent) {
                agent = USER_AGENT
            }

            engine {
                maxConnectionsCount = 20
                endpoint {
                    maxConnectionsPerRoute = 5
                }
            }

            followRedirects = true
            expectSuccess = false
        }
    }
}
