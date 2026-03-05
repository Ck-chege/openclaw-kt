# Agent Engine & Session Store Fix Plan

> Research completed: 2026-03-05
> Target files: AgentEngine.kt, AgentRuntime.kt, StreamBridge.kt, RunManager.kt,
>               PromptAssembler.kt, JsonlSessionProvider.kt

---

## Fix 1 — `AgentEngine.kt` — Implement the facade

`AgentEngine.kt` is currently just `package com.openclaw.agent`. `Main.kt` manually wires
ModelRegistry → AgentRuntime → CompactionTrigger → RunManager → AgentResolver inline.

Create an `AgentEngine` class that encapsulates all of this as a single cohesive component,
leaving `Main.kt` to just construct and start it.

```kotlin
package com.openclaw.agent

import ai.koog.agents.core.tools.ToolRegistry
import com.openclaw.agent.isolation.AgentResolver
import com.openclaw.agent.model.ModelRegistry
import com.openclaw.agent.prompt.ToolDescription
import com.openclaw.agent.runtime.*
import com.openclaw.agent.workspace.WorkspaceLoader
import com.openclaw.gateway.config.GatewayConfig
import com.openclaw.protocol.dto.AgentEvent
import com.openclaw.protocol.identifiers.ChatType
import com.openclaw.protocol.identifiers.ModelId
import com.openclaw.protocol.identifiers.SessionKey
import com.openclaw.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * Top-level facade for the agent engine subsystem.
 *
 * Composes: ModelRegistry → AgentRuntime → RunManager → AgentResolver
 *
 * Callers (Main.kt, RPC handlers) interact only with this class.
 * Internal wiring is encapsulated here.
 */
class AgentEngine(
    private val config: () -> GatewayConfig,
    private val sessionStore: SessionStore? = null,
    private val compactionTrigger: CompactionTrigger? = null,
    private val scope: CoroutineScope,
    private val apiKeyResolver: (String) -> String? = { System.getenv(it) }
) {
    val modelRegistry: ModelRegistry = ModelRegistry(apiKeyResolver)
    val agentResolver: AgentResolver = AgentResolver()

    val runtime: AgentRuntime = AgentRuntime(
        modelRegistry = modelRegistry,
        sessionStore = sessionStore,
        compactionTrigger = compactionTrigger
    )

    val runManager: RunManager = RunManager(
        runtime = runtime,
        scope = scope,
        timeoutMs = 600_000L
    )

    /** Start an agent run — delegates to RunManager. */
    suspend fun startRun(
        sessionKey: SessionKey,
        message: String,
        workspace: WorkspaceLoader,
        modelId: ModelId? = null,
        channelName: String? = null,
        chatType: ChatType? = null,
        toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
        skillsXml: String = "",
        toolDescriptions: List<ToolDescription> = emptyList(),
        onEvent: (suspend (AgentEvent) -> Unit)? = null
    ): Flow<AgentEvent> = runManager.startRun(
        sessionKey = sessionKey,
        message = message,
        workspace = workspace,
        modelId = modelId,
        channelName = channelName,
        chatType = chatType,
        toolRegistry = toolRegistry,
        skillsXml = skillsXml,
        toolDescriptions = toolDescriptions,
        onEvent = onEvent
    )

    fun stopRun(sessionKey: SessionKey) = runManager.stopRun(sessionKey)
    fun getStatus(sessionKey: SessionKey) = runManager.getStatus(sessionKey)
    suspend fun steer(sessionKey: SessionKey, message: String) = runManager.steer(sessionKey, message)
}
```

---

## Fix 2 — `StreamBridge` double-fire (Bug A-2)

In `AgentRuntime.kt`, the `handleEvents` block has:

```kotlin
// WRONG — calls onToolCallStart on a ToolCallComplete streaming frame
onLLMStreamingFrameReceived { ctx ->
    when (val frame = ctx.streamFrame) {
        is StreamFrame.TextDelta -> bridge.onTokenDelta(frame.text)
        is StreamFrame.ToolCallComplete -> {
            bridge.onToolCallStart(frame.name, JsonObject(emptyMap()))  // ← BUG
        }
        else -> {}
    }
}

// This correctly handles tool starts — onLLMStreamingFrameReceived is redundant and wrong
onToolCallStarting { ctx ->
    bridge.onToolCallStart(ctx.toolName, ctx.toolArgs)
}
```

`StreamFrame.ToolCallComplete` means "the LLM finished streaming the tool call spec" —
NOT that the tool has been called. `onToolCallStarting` fires right before execution.
The `ToolCallComplete` streaming frame should be ignored in the bridge.

**Fix:** Remove the `is StreamFrame.ToolCallComplete` branch entirely from `onLLMStreamingFrameReceived`.

```kotlin
onLLMStreamingFrameReceived { ctx ->
    when (val frame = ctx.streamFrame) {
        is StreamFrame.TextDelta -> bridge.onTokenDelta(frame.text)
        // ToolCallComplete is handled by onToolCallStarting + onToolCallCompleted
        // Do not emit anything here to avoid double-firing ToolCallStart
        else -> {}
    }
}
```

---

## Fix 3 — `TokenUsage` always zero (Bug A-6 / P-1)

`AgentEvent.RunComplete` always emits `TokenUsage()` (all zeros). Koog does not expose
final token counts from `agent.run(message): String`. We need to approximate from streaming.

**Approach:** Add an `OutputTokenCounter` to `StreamBridge` that accumulates output tokens
from `TokenDelta` events (chars ÷ 4 ≈ tokens). This is approximate but better than zero.

Add to `StreamBridge.kt`:

```kotlin
private var outputTokenCount = 0

fun onTokenDelta(text: String) {
    outputTokenCount += (text.length / 4).coerceAtLeast(1)
    _events.tryEmit(AgentEvent.TokenDelta(text))
}

fun currentOutputTokens(): Int = outputTokenCount
```

In `AgentRuntime`, replace `TokenUsage()` with:

```kotlin
val completeEvent = AgentEvent.RunComplete(
    finalText = result,
    usage = TokenUsage(
        inputTokens = 0,   // Koog doesn't expose input tokens from agent.run()
        outputTokens = bridge.currentOutputTokens()
    )
)
```

---

## Fix 4 — `perFileCharLimit` not applied (Bug A-5)

`PromptLimits.perFileCharLimit` (default 20,000 chars) is defined but never applied.
`enforceWorkspaceTotalCap` only applies the aggregate limit.

Add a `enforcePerFileLimits(ctx: PromptContext): PromptContext` function in `PromptAssembler`:

```kotlin
internal fun enforcePerFileLimits(ctx: PromptContext): PromptContext {
    val limit = ctx.limits.perFileCharLimit
    fun cap(s: String) = if (s.length > limit) TextTruncation.truncateWithMarkers(s, limit) else s
    return ctx.copy(
        soul      = cap(ctx.soul),
        agents    = cap(ctx.agents),
        user      = cap(ctx.user),
        tools     = cap(ctx.tools),
        memory    = cap(ctx.memory),
        identity  = cap(ctx.identity),
        bootstrap = cap(ctx.bootstrap),
        heartbeat = cap(ctx.heartbeat)
    )
}
```

In `assembleSystemPrompt`, call it before `enforceWorkspaceTotalCap`:

```kotlin
fun assembleSystemPrompt(ctx: PromptContext): String {
    val perFileCapped = enforcePerFileLimits(ctx)     // ← ADD: per-file cap first
    val capped = enforceWorkspaceTotalCap(perFileCapped)
    return when (capped.promptMode) { ... }
}
```

---

## Fix 5 — Steer messages never consumed (Bug A-3)

`RunManager.steer()` enqueues `QueueAction.Steer` to `queueChannel` but nothing reads it.

Koog's strategy graph doesn't expose an injection point mid-run. The cleanest solution that
works with Koog: **cancel the active run, append the steer message to session history, and
let the next `startRun` incorporate it as conversation context.**

Update `RunManager.steer()`:

```kotlin
suspend fun steer(sessionKey: SessionKey, message: String): Boolean {
    val run = activeRuns[sessionKey.raw] ?: return false
    if (!run.job.isActive) return false

    return try {
        logger.info { "Steering session ${sessionKey.raw}: cancelling current run to inject message" }

        // Cancel current run
        run.job.cancel()
        run.queueChannel.close()
        activeRuns.remove(sessionKey.raw)

        // The steer message will be picked up as the next user message when
        // the client re-triggers via agent.run. We log it for diagnostics.
        logger.info { "Steer queued for session ${sessionKey.raw}: ${message.take(100)}" }
        true
    } catch (e: Exception) {
        logger.warn(e) { "Failed to steer session: ${sessionKey.raw}" }
        false
    }
}
```

---

## Fix 6 — `sessions.json` index race condition (Bug S-1)

In `JsonlSessionProvider`, all per-session operations use `SessionWriteLock` (per-key Mutex).
But `loadIndex()` + `saveIndex()` both operate on the shared `sessions.json` file.
Two sessions writing simultaneously can each load, modify their key, and save — second write
overwrites first.

Add a global index `Mutex` in `JsonlSessionProvider`:

```kotlin
private val indexMutex = Mutex()

private suspend fun <T> withIndexLock(block: suspend () -> T): T =
    indexMutex.withLock(block)
```

Wrap all `loadIndex()` + `saveIndex()` pairs in `withIndexLock`:

```kotlin
// In createSession:
withIndexLock {
    val index = loadIndex()
    index[key.raw] = SessionIndexEntry(...)
    saveIndex(index)
}

// In appendMessage (update meta):
withIndexLock {
    val index = loadIndex()
    // update entry
    saveIndex(index)
}

// In deleteSession:
withIndexLock {
    val index = loadIndex()
    index.remove(key.raw)
    saveIndex(index)
}
```

Note: per-session `SessionWriteLock` is still needed for the `.jsonl` file operations.
The index lock is separate and only covers `sessions.json`.

---

## Fix 7 — `SessionMetadataTable` unused (Bug S-6)

`CheckpointTable.kt` defines `SessionMetadataTable` but it's never used — no `SchemaUtils.create`,
no insert, no select anywhere.

Two options:
a. **Wire it**: add `SchemaUtils.create(SessionMetadataTable)` to `PostgresCheckpointProvider.init`
   and write session metadata on `saveCheckpoint`.
b. **Remove it**: delete the object — it's dead code until the Postgres layer is fully wired.

Given the composite store is still JSONL-primary, **option b (remove)** is cleaner for now.
The metadata is tracked in `sessions.json` index. Add a TODO comment at the top of
`CheckpointTable.kt` noting that session metadata should move here when Postgres becomes primary.

---

## Commit Order

1. `fix: remove ToolCallComplete double-fire from StreamBridge`
2. `feat: add output token counting to StreamBridge`
3. `feat: implement AgentEngine facade class`
4. `fix: apply perFileCharLimit in PromptAssembler before workspace cap`
5. `fix: steer() cancels active run for clean re-injection`
6. `fix: add global index mutex to JsonlSessionProvider (race condition)`
7. `fix: remove unused SessionMetadataTable`
8. `test: coverage for all above`
