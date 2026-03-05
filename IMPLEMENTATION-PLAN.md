# Implementation Plan — Gateway Core Improvements

> Based on deep audit of openclaw-kt vs JS reference implementation.
> Research completed: 2026-03-05
> **Status: ✅ COMPLETE — all items implemented and tested (2026-03-05)**
> Commits: `e4fbac7` → `89979fa` → `053f3b5` → `9f71e50` → `7974197` → `29ed657` → `4515ed8`
> Tests: `./gradlew :gateway-core:test` → BUILD SUCCESSFUL

---

## Research Findings (What the Codebase Already Does Right)

Before listing gaps, the existing code already has:

- **`ProviderFactory(apiKeyResolver: (String) -> String?)`** — already injectable! Defaults to `System.getenv`. Just needs proper wiring from `GatewayConfig.secrets`.
- **`ModelRegistry(apiKeyResolver)`** — same pattern, already there.
- **`MutableStateFlow` in `ConfigManager`** — reactive config is already in place.
- **`OperatorLevel` enum** in protocol — `ADMIN`, `WRITE`, `READ`, `APPROVALS` already defined.
- **Ktor's `DefaultWebSocketServerSession` implements `CoroutineScope`** — `launch { }` is already available inside the `webSocket { }` block. No additional setup needed.
- **`SessionRegistry.unregister`** already exists — just not called on push failure.

---

## Changes by File

### 1. `SessionRegistry.kt` — Fix Dead Session Leak (G-2)

**File:** `gateway-core/src/main/kotlin/com/openclaw/gateway/ws/SessionRegistry.kt`

In `pushEvent`, on exception, call `unregister(sessionId)`:

```kotlin
} catch (e: Exception) {
    logger.warn(e) { "Failed to push event to session $sessionId — removing from registry" }
    sessions.remove(sessionId)   // ← ADD THIS
    return false
}
```

---

### 2. `ConfigWriter.kt` — Fix Lossy Round-Trip (G-1)

**File:** `gateway-core/src/main/kotlin/com/openclaw/gateway/config/ConfigWriter.kt`

**a) Fix `quote()` — add newline escaping:**
```kotlin
private fun quote(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")}\""
```

**b) Add missing `agents.list` section:**
```kotlin
if (ag.list.isNotEmpty()) {
    appendLine("    list = [")
    for (entry in ag.list) {
        appendLine("        {")
        appendLine("            id = ${quote(entry.id)}")
        entry.model?.let { appendLine("            model = ${quote(it)}") }
        entry.workspace?.let { appendLine("            workspace = ${quote(it)}") }
        appendLine("        }")
    }
    appendLine("    ]")
}
```

**c) Add `tools` section:**
```kotlin
val tools = config.tools
val toolsDefaults = defaults.tools
if (tools != toolsDefaults) {
    appendLine("tools {")
    if (tools.profile != toolsDefaults.profile) appendLine("    profile = ${quote(tools.profile)}")
    if (tools.allow.isNotEmpty()) appendLine("    allow = [${tools.allow.joinToString(", ") { quote(it) }}]")
    if (tools.deny.isNotEmpty()) appendLine("    deny = [${tools.deny.joinToString(", ") { quote(it) }}]")
    if (tools.sandbox != toolsDefaults.sandbox) {
        appendLine("    sandbox {")
        appendLine("        enabled = ${tools.sandbox.enabled}")
        if (tools.sandbox.allow.isNotEmpty()) appendLine("        allow = [${tools.sandbox.allow.joinToString(", ") { quote(it) }}]")
        if (tools.sandbox.deny.isNotEmpty()) appendLine("        deny = [${tools.sandbox.deny.joinToString(", ") { quote(it) }}]")
        appendLine("    }")
    }
    if (tools.mcp.isNotEmpty()) {
        appendLine("    mcp = [")
        for (mcp in tools.mcp) {
            appendLine("        { name = ${quote(mcp.name)}, transport = ${quote(mcp.transport)}, command = ${quote(mcp.command ?: "")}, enabled = ${mcp.enabled} }")
        }
        appendLine("    ]")
    }
    appendLine("}")
    appendLine()
}
```

---

### 3. `WebSocketHandler.kt` — Concurrent Frame Dispatch

**File:** `gateway-core/src/main/kotlin/com/openclaw/gateway/ws/WebSocketHandler.kt`

Replace the sequential `for (message in wsSession.incoming)` loop body with concurrent dispatch using `SupervisorJob`. Since `DefaultWebSocketServerSession` is a `CoroutineScope`, `launch` is available directly.

**Before:**
```kotlin
for (message in wsSession.incoming) {
    // ... process synchronously
}
```

**After:**
```kotlin
// Connection-scoped supervisor: one failed request doesn't kill the connection
val connScope = wsSession + SupervisorJob(wsSession.coroutineContext.job)

for (message in wsSession.incoming) {
    val text = (message as? io.ktor.websocket.Frame.Text)?.readText() ?: continue
    connScope.launch {
        // All request handling runs here concurrently
        handleFrame(text, wsSession, session!!, unauthorizedFloodGuard)
    }
}
```

Also: add `withTimeoutOrNull` for the handshake:

```kotlin
// Handshake must arrive within 15 seconds
val firstMessage = withTimeoutOrNull(15_000L) { wsSession.incoming.receive() }
    ?: run {
        wsSession.close(CloseReason(CloseReason.Codes.GOING_AWAY, "handshake timeout"))
        return
    }
```

---

### 4. `UnauthorizedFloodGuard.kt` — New File

**File:** `gateway-core/src/main/kotlin/com/openclaw/gateway/ws/UnauthorizedFloodGuard.kt`

```kotlin
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
```

---

### 5. `AuthRateLimiter.kt` — New File

**File:** `gateway-core/src/main/kotlin/com/openclaw/gateway/auth/AuthRateLimiter.kt`

```kotlin
package com.openclaw.gateway.auth

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Per-IP rate limiter for auth failures.
 * Uses ConcurrentHashMap.compute for atomic updates without explicit locking.
 *
 * [maxAttempts] failures within [windowMs] milliseconds → [RateLimitResult.Blocked].
 * On success, call [reset] to clear the record for that IP.
 */
class AuthRateLimiter(
    private val maxAttempts: Int = 5,
    private val windowMs: Long = 60_000L
) {
    private data class Record(val count: AtomicInteger, val windowStart: Long)
    private val records = ConcurrentHashMap<String, Record>()

    sealed class RateLimitResult {
        data object Allowed : RateLimitResult()
        data class Blocked(val retryAfterMs: Long) : RateLimitResult()
    }

    fun check(ip: String): RateLimitResult {
        val now = System.currentTimeMillis()
        val record = records.compute(ip) { _, existing ->
            when {
                existing == null || (now - existing.windowStart) > windowMs ->
                    Record(AtomicInteger(1), now)
                else -> existing.also { it.count.incrementAndGet() }
            }
        }!!
        return if (record.count.get() > maxAttempts) {
            val retryAfterMs = windowMs - (now - record.windowStart)
            RateLimitResult.Blocked(retryAfterMs.coerceAtLeast(0L))
        } else {
            RateLimitResult.Allowed
        }
    }

    fun reset(ip: String) {
        records.remove(ip)
    }

    /** Clean up expired windows to prevent unbounded memory growth. */
    fun evictExpired() {
        val now = System.currentTimeMillis()
        records.entries.removeIf { (_, record) -> (now - record.windowStart) > windowMs }
    }
}
```

---

### 6. `MethodScopeRegistry.kt` — New File (Default-Deny Scope Enforcement)

**File:** `gateway-core/src/main/kotlin/com/openclaw/gateway/rpc/MethodScopeRegistry.kt`

```kotlin
package com.openclaw.gateway.rpc

import com.openclaw.protocol.identifiers.OperatorLevel

/**
 * Maps every known gateway method to its required [OperatorLevel].
 * Methods not in this registry require [OperatorLevel.ADMIN] (default-deny).
 *
 * WRITE implies READ (callers with WRITE can call READ-scoped methods).
 * ADMIN implies everything.
 */
object MethodScopeRegistry {

    private val registry: Map<String, OperatorLevel> = buildMap {
        // READ-scoped methods
        listOf(
            "health", "status", "config.get", "models.list",
            "channels.status", "sessions.list", "sessions.get", "sessions.history",
            "setup.status", "agents.list"
        ).forEach { put(it, OperatorLevel.READ) }

        // WRITE-scoped methods
        listOf(
            "chat.send", "chat.abort", "sessions.send",
            "wake", "agent.run", "setup.configure"
        ).forEach { put(it, OperatorLevel.WRITE) }

        // APPROVALS-scoped methods
        listOf(
            "exec.approval.request", "exec.approval.waitDecision", "exec.approval.resolve"
        ).forEach { put(it, OperatorLevel.APPROVALS) }

        // ADMIN-scoped methods (explicit — same as default-deny, but self-documenting)
        listOf(
            "config.apply", "config.patch",
            "sessions.delete", "sessions.reset", "sessions.compact",
            "cron.add", "cron.update", "cron.remove", "cron.run",
            "agents.create", "agents.update", "agents.delete",
            "setup.complete"
        ).forEach { put(it, OperatorLevel.ADMIN) }
    }

    /** Required scope for [method]. Unknown methods require ADMIN (default-deny). */
    fun requiredScope(method: String): OperatorLevel =
        registry[method] ?: OperatorLevel.ADMIN

    /**
     * Returns true if [clientScopes] grants permission for [method].
     *
     * ADMIN implies all.
     * WRITE implies READ.
     */
    fun isAuthorized(method: String, clientScopes: Set<String>): Boolean {
        if (clientScopes.contains("admin") || clientScopes.contains(OperatorLevel.ADMIN.name.lowercase())) return true
        val required = requiredScope(method)
        return when (required) {
            OperatorLevel.READ -> clientScopes.any { it in setOf("read", "write") }
            OperatorLevel.WRITE -> clientScopes.contains("write")
            OperatorLevel.APPROVALS -> clientScopes.contains("approvals")
            OperatorLevel.ADMIN -> false
        }
    }
}
```

---

### 7. `ConfigManager.kt` — Config Diff + Reload Plan

**File:** `gateway-core/src/main/kotlin/com/openclaw/gateway/config/ConfigManager.kt`

Add `diffConfigPaths` + `GatewayReloadPlan` + plumbed `reload()`:

```kotlin
/** Returns dotted paths that changed between two configs. */
fun diffConfigPaths(prev: GatewayConfig, next: GatewayConfig): List<String> {
    val paths = mutableListOf<String>()
    if (prev.gateway.port != next.gateway.port)   paths += "gateway.port"
    if (prev.gateway.bind != next.gateway.bind)   paths += "gateway.bind"
    if (prev.gateway.auth != next.gateway.auth)   paths += "gateway.auth"
    if (prev.gateway.reload != next.gateway.reload) paths += "gateway.reload"
    if (prev.agents != next.agents)               paths += "agents"
    if (prev.secrets != next.secrets)             paths += "secrets"
    if (prev.tools != next.tools)                 paths += "tools"
    if (prev.memory != next.memory)               paths += "memory"
    if (prev.cron != next.cron)                   paths += "cron"
    return paths
}

data class GatewayReloadPlan(
    val changedPaths: List<String>,
    val requiresRestart: Boolean,
    val restartReasons: List<String>
)

/** Paths that require a full gateway restart when changed. */
private val RESTART_REQUIRED_PATHS = setOf("gateway.port", "gateway.bind", "gateway.auth")

fun buildReloadPlan(changedPaths: List<String>): GatewayReloadPlan {
    val restartReasons = changedPaths.filter { it in RESTART_REQUIRED_PATHS }
    return GatewayReloadPlan(
        changedPaths = changedPaths,
        requiresRestart = restartReasons.isNotEmpty(),
        restartReasons = restartReasons
    )
}
```

Update `reload()` to use the plan:

```kotlin
fun reload(onRestartRequired: ((GatewayReloadPlan) -> Unit)? = null) {
    val previous = _config.value
    try {
        val reloaded = parseConfigFile()
        val diff = diffConfigPaths(previous, reloaded)
        if (diff.isEmpty()) {
            logger.debug { "Config reload: no changes detected" }
            return
        }
        val plan = buildReloadPlan(diff)
        logger.info { "Config changed: ${diff.joinToString()}" }
        if (plan.requiresRestart) {
            logger.warn { "Config change requires restart: ${plan.restartReasons.joinToString()}" }
            onRestartRequired?.invoke(plan)
            if (onRestartRequired == null) {
                // Still apply the config — caller handles restart externally
                _config.value = reloaded
            }
        } else {
            _config.value = reloaded
            logger.info { "Config hot-reloaded successfully" }
        }
    } catch (e: ConfigError) {
        logger.warn(e) { "Failed to reload config — keeping previous configuration" }
    }
}
```

---

### 8. `PresenceRegistry.kt` — New File

**File:** `gateway-core/src/main/kotlin/com/openclaw/gateway/ws/PresenceRegistry.kt`

```kotlin
package com.openclaw.gateway.ws

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class ClientPresence(
    val connId: String,
    val role: String,
    val scopes: Set<String>,
    val ip: String?,
    val platform: String?,
    val connectedAt: Instant = Clock.System.now()
)

/**
 * Tracks presence of all connected WebSocket clients.
 *
 * Backed by an immutable map in a [StateFlow] — any observer is notified on every
 * connect/disconnect without polling. [update] is atomic (lock-free CAS).
 */
class PresenceRegistry {
    private val _presence = MutableStateFlow<Map<String, ClientPresence>>(emptyMap())
    val presence: StateFlow<Map<String, ClientPresence>> = _presence.asStateFlow()

    fun register(connId: String, presence: ClientPresence) {
        _presence.update { it + (connId to presence) }
    }

    fun unregister(connId: String) {
        _presence.update { it - connId }
    }

    fun snapshot(): List<ClientPresence> = _presence.value.values.toList()
    fun count(): Int = _presence.value.size
}
```

---

### 9. `GatewayServer.kt` — Wire Secret Resolver

**File:** `gateway-core/src/main/kotlin/com/openclaw/gateway/GatewayServer.kt`

The `ModelRegistry` and `ProviderFactory` both already accept `apiKeyResolver: (String) -> String?`.
Wire `GatewayConfig.secrets` as the config-first resolver in `GatewayServer`:

```kotlin
// In GatewayServer constructor or a factory function — expose for agent-engine wiring:
fun buildSecretResolver(config: GatewayConfig): (String) -> String? = { envKey ->
    // Config-first: try the structured secrets object first, then fall back to env
    when (envKey) {
        "ANTHROPIC_API_KEY" -> config.secrets.anthropicApiKey?.takeIf { it.isNotBlank() }
        "OPENAI_API_KEY" -> config.secrets.openaiApiKey?.takeIf { it.isNotBlank() }
        else -> null
    } ?: System.getenv(envKey)
}
```

Pass this to `ModelRegistry(apiKeyResolver = buildSecretResolver(config))` at app startup.

---

## Test Coverage Required

For each change, corresponding tests:

| Change | Test file | What to test |
|--------|-----------|--------------|
| SessionRegistry dead-session cleanup | `SessionRegistryTest.kt` | Push to dead session → unregistered |
| ConfigWriter round-trip | `ConfigWriterTest.kt` (extend existing) | agents.list + tools survive write/parse |
| ConfigWriter newline escape | `ConfigWriterTest.kt` | Token with `\n` round-trips correctly |
| UnauthorizedFloodGuard | `UnauthorizedFloodGuardTest.kt` | Close after 10, suppress then log pattern |
| AuthRateLimiter | `AuthRateLimiterTest.kt` | Block after N failures, reset clears |
| MethodScopeRegistry | `MethodScopeRegistryTest.kt` | read/write/admin/approvals matrix |
| ConfigManager diff | `ConfigManagerTest.kt` (extend) | Only changed paths reported; no-op on identical |
| Secret resolver | `GatewayServerTest.kt` | Config key takes precedence over env var |
| Concurrent dispatch | `GatewayServerTest.kt` | Two simultaneous RPC calls both get responses |
| Handshake timeout | `WebSocketHandlerTest.kt` | Silent connect → closed after 15s |

---

## Commit Order

1. `fix: unregister dead WS session on push failure` (SessionRegistry)
2. `fix: escape newlines in ConfigWriter.quote()`
3. `feat: write agents.list and tools sections in ConfigWriter`
4. `feat: add UnauthorizedFloodGuard`
5. `feat: add AuthRateLimiter`
6. `feat: add MethodScopeRegistry with default-deny`
7. `feat: add config diff and GatewayReloadPlan to ConfigManager`
8. `feat: concurrent WS frame dispatch with SupervisorJob`
9. `feat: handshake timeout via withTimeoutOrNull`
10. `feat: add PresenceRegistry`
11. `feat: wire config-first secret resolver`
12. `test: coverage for all above`
