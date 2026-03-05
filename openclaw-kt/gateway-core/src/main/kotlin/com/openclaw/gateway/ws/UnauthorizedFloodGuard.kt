package com.openclaw.gateway.ws

/**
 * Tracks unauthorized request counts per connection.
 * After [closeAfter] consecutive unauthorized requests, signals the handler to close.
 * Suppresses log spam by only logging every [logEvery]th occurrence.
 *
 * Not thread-safe by design — each connection owns its own instance,
 * and all frame handling for a connection runs within a single coroutine scope.
 */
class UnauthorizedFloodGuard(
    val closeAfter: Int = 10,
    val logEvery: Int = 100
) {
    private var count = 0
    private var suppressedSinceLastLog = 0

    sealed class Decision {
        data class Close(val count: Int) : Decision()
        data class Log(val count: Int, val suppressed: Int) : Decision()
        data object Suppress : Decision()
    }

    fun register(): Decision {
        count++
        val shouldClose = count >= closeAfter
        val shouldLog = count == 1 || count % logEvery == 0 || shouldClose
        return when {
            shouldClose -> Decision.Close(count)
            shouldLog -> {
                val suppressed = suppressedSinceLastLog
                suppressedSinceLastLog = 0
                Decision.Log(count, suppressed)
            }
            else -> {
                suppressedSinceLastLog++
                Decision.Suppress
            }
        }
    }

    fun reset() {
        count = 0
        suppressedSinceLastLog = 0
    }
}
