# Core Modules Deep Audit

> Generated: 2026-03-04 | Last updated: 2026-03-05  
> Modules: `:protocol`, `:gateway-core`, `:agent-engine`, `:session-store`

---

## Changelog

| Date | Commits | What was fixed |
|------|---------|----------------|
| 2026-03-05 | `e4fbac7` | G-2: Dead WS session auto-unregistered on push failure |
| 2026-03-05 | `89979fa` | G-1: `ConfigWriter` now writes `agents.list` + full `tools` section; newline escaping fixed |
| 2026-03-05 | `89979fa` | G-5: `ConfigWriter.quote()` now escapes `\n` and `\r` |
| 2026-03-05 | `9f71e50` | G-6: `ConfigManager` now diffs config, builds `GatewayReloadPlan`; HOT vs HYBRID is meaningful |
| 2026-03-05 | `7974197` | G-4: WS frames now dispatched concurrently via `SupervisorJob` + `launch` per frame |
| 2026-03-05 | `7974197` | New: Handshake timeout (15s) via `withTimeoutOrNull` |
| 2026-03-05 | `053f3b5` | New: `UnauthorizedFloodGuard` — closes connection after 10 bad requests |
| 2026-03-05 | `053f3b5` | New: `AuthRateLimiter` — per-IP brute-force protection |
| 2026-03-05 | `053f3b5` | New: `MethodScopeRegistry` — default-deny method classification |
| 2026-03-05 | `29ed657` | New: `PresenceRegistry` — tracks connected clients via `StateFlow` |
| 2026-03-05 | `29ed657` | X-1 / A-4: Config-first secret resolver wired; `GatewayConfig.secrets` now feeds `ProviderFactory` |

---

## Scoring Legend

| Symbol | Meaning |
|--------|---------|
| ✅ | Solid — no action needed |
| ⚠️ | Warning — worth addressing |
| 🐛 | Bug — incorrect behaviour |
| 🔥 | Critical — must fix |

---

## 1. `:protocol`

### What it does
KMP multiplatform wire types. `Frame` sealed class (Request/Response/Event), DTO hierarchy (`AgentEvent`, `InboundEnvelope`, `TokenUsage`, etc.), and inline value-class identifiers (`SessionKey`, `AgentId`, `ModelId`).

### ✅ Strengths

- **Inline value classes for identifiers** — `SessionKey(val raw: String)` etc. gives compile-time type safety at zero runtime cost. No accidental string confusion across boundaries.
- **`Frame` discriminator design** — `type` field with sealed subclasses is clean and forward-compatible. The KDoc table mapping method names to frame types is excellent documentation.
- **`AgentEvent` hierarchy** — `TokenDelta`, `ToolCallStart`, `ToolCallResult`, `RunComplete`, `RunError` maps 1:1 to observable UI states. Clean.
- **KMP multiplatform** — `commonMain`/`commonTest` layout means wire types could be shared with a native iOS/Compose client later.
- **Test coverage** — `FrameTest`, `DtoTest`, `IdentifiersTest` all exist.

### ⚠️ Issues

| # | Severity | Location | Description |
|---|----------|----------|-------------|
| P-1 | ⚠️ | `AgentEvent.RunComplete` | `TokenUsage` is always the default (zero values). Nothing populates it from the Koog stream. Usage tracking is broken end-to-end. |
| P-2 | ⚠️ | `SessionKey` | `isSubagent` and `isCron` are referenced in `PromptContext` but their implementation isn't in protocol — verify they exist (likely string-prefix checks). If they're hardcoded string magic, centralise the convention. |
| P-3 | ⚠️ | All identifiers | No validation — `ModelId("")` or `SessionKey(" ")` are silently accepted. Add `init { require(raw.isNotBlank()) }` guards. |
| P-4 | ⚠️ | `InboundEnvelope` | No `messageId` field. The JS version carries a message ID for reply threading. This will be needed once Discord/Telegram channels are wired. |

---

## 2. `:gateway-core`

### What it does
Ktor/Netty WebSocket server, wire protocol handling, RPC routing, config management (HOCON, hot-reload, file watcher), auth, session registry, and first-run setup flow.

### ✅ Strengths

- **`ConfigManager`** — Reactive `StateFlow<GatewayConfig>`, 300ms debounced file watcher, respects `ReloadMode`, fail-fast on startup, warn-and-retain on hot reload. Very solid.
- **`HoconMapper`** — `getOrDefault` inline helper avoids repetition, section-by-section delegation is readable and safe.
- **`ConfigError` sealed hierarchy** — `FileParseError` vs `InvalidValue` gives structured reporting. Wraps `ConfigException` without losing context.
- **`GatewayAuth`** — Clean open mode (no config = allow all). Sealed `ClientRole` with scoped permissions. Role string → scoped permissions mapping is clear.
- **`WebSocketHandler`** — Well-documented 5-step lifecycle. Proper `CloseReason` codes on each failure path.
- **`SessionRegistry`** — `ConcurrentHashMap`, correct `suspend fun pushEvent`.
- **`RpcRouter`** — `fun interface RpcHandler` is idiomatic. Easy to test.
- **`AppPaths`** — Single source of truth for filesystem layout. No hardcoded paths anywhere else.

### ⚠️ Issues

| # | Severity | Location | Description |
|---|----------|----------|-------------|
| G-1 | ~~🔥~~ ✅ **FIXED** | `ConfigWriter.renderHocon` | ~~`tools`, `agents.list`, and `byProvider` are never written.~~ Fixed in `89979fa` — `agents.list` and full `tools` section now rendered. |
| G-2 | ~~🐛~~ ✅ **FIXED** | `SessionRegistry.pushEvent` | ~~Dead session not removed on failure.~~ Fixed in `e4fbac7` — `sessions.remove(sessionId)` called on exception. |
| G-3 | 🐛 | `GatewayAuth` — `Node` role | `deviceId = UUID.randomUUID()` is generated fresh per connection. A node device loses its identity on every reconnect. The deviceId should come from the client's `connect.hello` params. Requires device identity protocol (cryptographic binding). |
| G-4 | ~~⚠️~~ ✅ **FIXED** | `WebSocketHandler` message loop | ~~Sequential frame processing.~~ Fixed in `7974197` — each request now runs in a child coroutine via `SupervisorJob` + `launch`. |
| G-5 | ~~⚠️~~ ✅ **FIXED** | `ConfigWriter.quote()` | ~~Doesn't escape newlines.~~ Fixed in `89979fa` — `\n` and `\r` now escaped. |
| G-6 | ~~⚠️~~ ✅ **FIXED** | `ReloadMode.HYBRID` | ~~HYBRID and HOT are identical.~~ Fixed in `9f71e50` — `diffConfigPaths` + `GatewayReloadPlan` implemented; restart-required paths trigger `onRestartRequired` callback. |
| G-7 | ⚠️ | `ConfigRpcHandlers` — `channels.status` | Always returns `connected = 0`. Stub — needs wiring once channel modules are integrated. |
| G-8 | ⚠️ | `ConfigManager.startFileWatcher` | Watcher lifetime implicitly tied to caller scope. `stopFileWatcher()` must be called on teardown — not enforced. |

---

## 3. `:agent-engine`

### What it does
Agent lifecycle: model registry, prompt assembly, Koog strategy graph, streaming bridge, run management, compaction trigger, and memory flush runner.

### ✅ Strengths

- **`AgentRuntime`** — Documented 10-step pipeline. Clean separation of concerns: persist input → assemble prompt → run → persist output → compact.
- **`RetryingExecutor`** — Full exponential backoff with jitter, `parseRetryAfter` header parsing (handles "try again in 10.4s"), `isRetryable` categorises 429/5xx/overloaded correctly. Wraps streaming too.
- **`PromptAssembler`** — FULL/MINIMAL/NONE modes, group chat MEMORY.md exclusion (security boundary), workspace total cap enforcement with largest-first truncation.
- **`SystemPromptBuilder`** — Elegant DSL (`section`, `xml`, `raw`) that keeps the assembler readable.
- **`TextTruncation`** — Head+tail with char-count marker. Better than naive tail truncation.
- **`buildOpenClawStrategy`** — Koog DSL graph is clean. Inline `nodeLLMCompressHistory` handles in-graph context compression at `compactionThreshold`.
- **`CompactionTrigger`** — check→flush→compact in a single place, never throws, returns sealed `CompactionTriggerResult`.
- **`RunManager`** — Per-session `Mutex` serialises concurrent run requests. Timeout controlled by `withTimeoutOrNull`. `steer()` enqueues via `QueueAction`.

### ⚠️ Issues

| # | Severity | Location | Description |
|---|----------|----------|-------------|
| A-1 | 🔥 | `AgentEngine.kt` | **File is empty.** This is the main entry point of the module. The runtime, run manager, and model registry have no wiring point. The module cannot be instantiated by `:app` without this file. |
| A-2 | 🐛 | `StreamBridge` / strategy bridge | From the strategy bridge: `onLLMStreamingFrameReceived` emits `onToolCallStart` on `ToolCallComplete` events. `onToolCallStarting` already handles starts — this double-fires `ToolCallStart` events to the client on every completed call. |
| A-3 | 🐛 | `RunManager.startRun` + `AgentRuntime` | `queueChannel` receives `QueueAction.Steer` messages via `steer()`, but **nothing reads from `queueChannel`** in `executeRun`. Steer messages are enqueued and silently discarded. The steer API is non-functional. |
| A-4 | 🐛 | `ProviderFactory` | Only reads API keys from **env vars**. `GatewayConfig.secrets.anthropicApiKey` (set via `setup.configure`) is never passed to the factory. Users who configure keys via the UI rather than env vars will get `MissingApiKey` errors. |
| A-5 | ⚠️ | `PromptAssembler` | `PromptLimits.perFileCharLimit` (20,000 chars) is defined but **never applied**. `enforceWorkspaceTotalCap` only enforces the aggregate limit. A single 80KB SOUL.md won't be trimmed until it blows the total cap. |
| A-6 | ⚠️ | `TokenUsage` — `RunComplete` | Emitted with `TokenUsage()` (all zeros). Never populated from Koog's execution response. Token tracking, cost estimation, and compaction token budgeting are all affected. |
| A-7 | ⚠️ | `MemoryFlushRunner` | Creates a full `AIAgent` with `buildOpenClawStrategy()` for a flush that should only write a file. The strategy includes `nodeLLMCompressHistory`, which fires on 100+ messages — a flush agent will never hit that, but it's unnecessary complexity. A simple `executor.execute(prompt, model, tools)` call would suffice. |
| A-8 | ⚠️ | `BindingMatcher.matchByChannel` | Channel matching is `it.id == channel` — an agent named `discord` matches a Discord message. This is fragile. Comment notes "Future: add explicit channel bindings." Until that's done, unintended channel→agent matches are possible if agent IDs collide with channel names. |
| A-9 | ⚠️ | `AgentResolver.resolveForChannel` | Calls `bindingMatcher.match` twice — once in `matchByChannel` (unused result), once in `resolve(config, entry.id)`. Minor but wasteful. |

---

## 4. `:session-store`

### What it does
Session persistence layer: JSONL files (primary), Postgres checkpoints (degradable), `CompactionEngine` with pluggable `MessageSummarizer`, `CompositeSessionStore` dual-layer, and `SessionWriteLock`.

### ✅ Strengths

- **`SessionStore` interface** — Clean contract, all ops are `suspend`. `updateMeta` atomic updater pattern is good.
- **`SessionWriteLock`** — Per-key coroutine `Mutex` via `ConcurrentHashMap`. Correct and minimal.
- **`JsonlSessionProvider`** — Atomic compaction via `ATOMIC_MOVE` (won't corrupt on crash). `writeLock` on all mutations.
- **`CompactionEngine`** — Token budget keeps recent messages, handles CompactionSummary-only prefix, checks minimum message count. Robust.
- **`MessageSummarizer` as `fun interface`** — Pluggable: real LLM in prod, mock in tests. Clean.
- **`CompositeSessionStore`** — JSONL primary always writes; Postgres is opportunistic. `isDegraded` flag prevents log spam. Graceful degradation is correct.
- **`PostgresCheckpointProvider`** — Exposed `newSuspendedTransaction`, upsert logic, proper `deleteWhere`.
- **Test coverage** — `CompactionEngineTest`, `JsonlSessionProviderTest`, `CompositeSessionStoreTest`, integration test with Testcontainers/Postgres. Excellent.

### ⚠️ Issues

| # | Severity | Location | Description |
|---|----------|----------|-------------|
| S-1 | 🔥 | `JsonlSessionProvider` — `loadIndex`/`saveIndex` | **Race condition across concurrent sessions.** `SessionWriteLock` is per-session, but `sessions.json` is a single shared file. Two different sessions writing simultaneously both call `loadIndex()`, both modify their own key, and both call `saveIndex()` — the second write silently overwrites the first's entry. Add a global index `Mutex` separate from per-session locks. |
| S-2 | 🐛 | `JsonlSessionProvider.appendMessage` | Calls `loadIndex()` + `saveIndex()` on every single message. For active sessions this is a full file read+write per message. An in-memory cache of `SessionMeta` would reduce this to a write-through on change only. |
| S-3 | ⚠️ | `PostgresCheckpointProvider.saveCheckpoint` | Manual `selectAll + count > 0` then `update/insert`. Exposed 0.50+ has `upsert` / `insertOrUpdate`. Replace with a single upsert to avoid TOCTOU in concurrent scenarios. |
| S-4 | ⚠️ | `CompositeSessionStore.appendMessage` | Never triggers a checkpoint write to Postgres. Checkpoints only happen when `saveCheckpoint()` is explicitly called from outside. A crash between explicit checkpoint calls loses progress. Consider checkpointing every N messages. |
| S-5 | ⚠️ | `RecoveryResult.Recovered` | Detects that JSONL is behind the checkpoint but **does nothing to actually recover**. No mechanism exists to replay or backfill the missing messages. This is detection-only. |
| S-6 | ⚠️ | `SessionMetadataTable` | Defined in `CheckpointTable.kt` but **never used anywhere** — no `SchemaUtils.create`, no insert, no select. Either wire it up or remove it to avoid confusion. |
| S-7 | ⚠️ | `JsonlSessionProvider` | `sessionsDir / "sessions.json"` uses `Path / String` operator extension. Confirm this is imported correctly (`kotlin.io.path.div`) — it's not in stdlib prior to Kotlin 1.5.0 and some builds miss it. |

---

## Cross-Module Issues

| # | Severity | Description |
|---|----------|-------------|
| X-1 | ~~🔥~~ ✅ **FIXED** | **API key plumbing.** ~~`ProviderFactory` only reads env vars.~~ Fixed in `29ed657` — `buildSecretResolver()` checks `GatewayConfig.secrets` first, then env. Wired at app startup. |
| X-2 | ⚠️ | **No README.** Zero docs. Add `README.md` with build instructions and quickstart. |
| X-3 | ⚠️ | **`AgentEngine.kt` is empty.** `:app` wiring exists but the main entry file needs implementation. |
| X-4 | ⚠️ | **Channel event bus missing.** No bridge between `SessionRegistry` (WS clients) and channel modules. Integration layer still needed. |
| X-5 | ⚠️ | **`.openclawk` vs `.openclaw`** — confirm the extra `k` is intentional vs a typo. JS uses `.openclaw`. |

---

## Priority Fix List

### ✅ Completed (2026-03-05)
| Issue | Fix | Commit |
|-------|-----|--------|
| X-1 / A-4: Config secrets → ProviderFactory | `buildSecretResolver()` wired at startup | `29ed657` |
| G-1: ConfigWriter lossy round-trip | `agents.list` + `tools` sections added | `89979fa` |
| G-2: Dead session leak | `sessions.remove()` on push failure | `e4fbac7` |
| G-4: Sequential WS dispatch | `SupervisorJob` + `launch` per frame | `7974197` |
| G-5: Newline escaping | `\n`/`\r` in `quote()` | `89979fa` |
| G-6: HYBRID = HOT | `diffConfigPaths` + `GatewayReloadPlan` | `9f71e50` |
| New: Handshake timeout | `withTimeoutOrNull(15_000)` | `7974197` |
| New: Flood guard | `UnauthorizedFloodGuard` | `053f3b5` |
| New: Rate limiter | `AuthRateLimiter` | `053f3b5` |
| New: Default-deny scopes | `MethodScopeRegistry` | `053f3b5` |
| New: Presence tracking | `PresenceRegistry` | `29ed657` |

### 🔲 Remaining (open)
| Priority | Issue | Module | Est. Effort |
|----------|-------|--------|-------------|
| 1 | **A-1**: Implement `AgentEngine.kt` wiring | agent-engine | Medium |
| 2 | **S-1**: Fix `sessions.json` index race condition | session-store | Small |
| 3 | **A-3**: Wire steer message consumption in `AgentRuntime` | agent-engine | Medium |
| 4 | **A-2**: Fix double-fire `ToolCallStart` in `StreamBridge` | agent-engine | Trivial |
| 5 | **A-6 / P-1**: Wire `TokenUsage` from Koog response | agent-engine | Small |
| 6 | **A-5**: Apply `perFileCharLimit` in `PromptAssembler` | agent-engine | Small |
| 7 | **S-6**: Remove or wire `SessionMetadataTable` | session-store | Trivial |
| 8 | **S-2**: Cache session index in-memory | session-store | Small |
| 9 | **G-3**: Device identity from public key | gateway-core | Large |
| 10 | **X-2**: Write README.md | root | Small |
