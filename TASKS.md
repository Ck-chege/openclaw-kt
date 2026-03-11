# OpenClaw-KT — Task Breakdown

> Epics → Stories → Subtasks | Checkboxes for tracking completion
> 15 Epics | 5 Phases | ~150 trackable items

---

## Phase 1: Core Foundation

### Epic 1: Project Bootstrap & Protocol Module
> **Dependencies:** None (starting point)
> **Outcome:** Buildable Gradle multimodule project with shared protocol types

- [x] **1.1 Initialize Gradle multimodule project**
  - [x] Create root `openclaw-kt/` with `build.gradle.kts`, `settings.gradle.kts`
  - [x] Set up `gradle/libs.versions.toml` version catalog (Kotlin 2.1.x, Ktor 3.1.x, Koog 0.6.x, kotlinx-serialization 1.7.x, kotlinx-coroutines 1.10.x, kotlinx-datetime 0.6.x)
  - [x] Configure submodule includes: protocol, gateway-core, agent-engine, session-store, app
  - [x] Add `.gitignore`, `.editorconfig`, root `README.md`
  - [x] Verify `./gradlew build` compiles empty project

- [x] **1.2 Implement `protocol/` module — Wire Protocol**
  - [x] Create KMP module structure (commonMain, jvmMain)
  - [x] Implement `Frame` sealed class (Request, Response, Event) with `@Serializable` and `@SerialName`
  - [x] Implement `Frame.serialize()` / `Frame.deserialize()` with kotlinx-serialization
  - [x] Write unit tests: round-trip serialization for all 3 frame types
  - [x] Write unit tests: malformed JSON → parse error (not crash)

- [x] **1.3 Implement `protocol/` module — Domain Identifiers**
  - [x] Implement `SessionKey` value class with component extraction (agentId, channel, peerId)
  - [x] Implement `AgentId`, `ModelId`, `TokenCount` value classes
  - [x] Implement `SessionScope` enum (MAIN, PER_PEER, PER_CHANNEL_PEER, SUBAGENT, CRON)
  - [x] Write unit tests: SessionKey parsing, validation, component extraction

- [x] **1.4 Implement `protocol/` module — Shared DTOs**
  - [x] Implement `InboundEnvelope` data class (channel, senderId, chatId, chatType, text, attachments)
  - [x] Implement `ChatType` enum (DM, GROUP, CHANNEL)
  - [x] Implement `StopReason` sealed class (ToolUse, EndTurn, Timeout, Error, Compaction)
  - [x] Implement `QueueAction` sealed class (NewMessage, Steer, Cancel)
  - [x] Implement `AgentEvent` sealed class (TokenDelta, ToolCallStart, ToolCallResult, RunComplete, RunError)
  - [x] Implement `ToolPermission` sealed interface (Allowed, Denied, RequiresApproval)

---

### Epic 2: Gateway Core
> **Dependencies:** Epic 1 (protocol module)
> **Outcome:** Running Ktor server accepting WebSocket connections with authenticated RPC routing

- [x] **2.1 Ktor server skeleton**
  - [x] Create `gateway-core/` module with Ktor dependencies (netty, websockets, content-negotiation, kotlinx-json)
  - [x] Implement `GatewayServer` class with `start()` / `stop()` lifecycle
  - [x] Configure `Application.module()` with WebSockets plugin (pingPeriod=15s, timeout=15s)
  - [x] Add HTTP health endpoint `GET /health`
  - [x] Write integration test: server starts, health returns 200

- [x] **2.2 WebSocket handler & frame routing**
  - [x] Implement WebSocket connection handler with `connect.hello` handshake validation
  - [x] Parse incoming text frames into `Frame.Request` using kotlinx-serialization
  - [x] Implement `RpcRouter` with method→handler registration
  - [x] Implement response framing: handler result → `Frame.Response`
  - [x] Implement event push: server→client `Frame.Event` broadcasting
  - [x] Write integration test: WS connect → handshake → RPC call → response

- [x] **2.3 Authentication & scopes**
  - [x] Implement `GatewayAuth` with token-based authentication from connect handshake
  - [x] Implement `ClientRole` sealed class (Operator, Node, WebChat)
  - [x] Implement `OperatorLevel` enum (ADMIN, WRITE, READ, APPROVALS)
  - [x] Implement scope checking on RPC method dispatch
  - [x] Write tests: valid auth → session created, invalid auth → disconnect

- [x] **2.4 Configuration system**
  - [x] Create `GatewayConfig` data class hierarchy (gateway, agents, channels, tools, memory, cron sections)
  - [x] Implement HOCON config loading from `~/.openclaw/openclaw.conf`
  - [x] Implement env var substitution (`${GATEWAY_TOKEN}`)
  - [x] Implement `ConfigManager` with `StateFlow<GatewayConfig>` for reactive updates
  - [x] Implement file watcher (Java WatchService) for hot-reload
  - [x] Write tests: valid config loads, invalid field → error with path, hot-reload detects changes

- [x] **2.5 RPC handler stubs**
  - [x] Register `config.get` / `config.set` handlers (backed by ConfigManager)
  - [x] Register `models.list` / `models.resolve` stubs
  - [x] Register `channels.status` stub
  - [x] Write integration test: full round-trip config.get via WebSocket

- [x] **2.6 First-run onboarding gate**
  - [x] Add `SecretsConfig` (anthropicApiKey, openaiApiKey) to `GatewayConfig` + `HoconMapper`
  - [x] Implement `ConfigWriter` for programmatic config updates (writes non-default HOCON)
  - [x] Add `markOnboardingComplete()` and `currentState()` to `WorkspaceBootstrap`
  - [x] Enrich `connect.hello` response with `setupRequired` + `onboardingPhase`
  - [x] Implement `setup.status` / `setup.configure` / `setup.complete` RPC handlers
  - [x] Wire `ModelRegistry` apiKeyResolver to check config secrets before env vars

- [x] **2.7 Gateway security hardening (Phase 1)**
  > Implements core security mechanisms from JS reference gateway. See GATEWAY-LESSONS.md for full analysis.
  - [x] Fix `SessionRegistry.pushEvent` — auto-unregister dead WS sessions on send failure (was silently leaking)
  - [x] Fix `ConfigWriter.quote()` — escape `\n` / `\r` (broken HOCON on tokens with newlines)
  - [x] Fix `ConfigWriter.renderHocon` — add `agents.list` and full `tools` section rendering (config round-trips were lossy)
  - [x] Implement `UnauthorizedFloodGuard` — close WS connection after 10 consecutive unauthorized requests; suppress log spam (log every 100th)
  - [x] Implement `AuthRateLimiter` — per-IP failure tracking with configurable window; returns `retryAfterMs` on block; `ConcurrentHashMap.compute` for lock-free updates
  - [x] Implement `MethodScopeRegistry` — maps every known RPC method to required `OperatorLevel`; unknown methods default to ADMIN (default-deny); `write` implies `read`
  - [x] Implement `ConfigManager.diffConfigPaths()` — recursive structural diff of config objects; returns list of changed dotted paths
  - [x] Implement `GatewayReloadPlan` — classifies changed paths as hot-swap vs restart-required; `HOT` and `HYBRID` reload modes are now meaningfully distinct
  - [x] Implement concurrent WS frame dispatch — each incoming frame launched as child coroutine via `SupervisorJob` + `scope.launch`; abort/steer no longer blocked by running stream
  - [x] Implement handshake timeout — `withTimeoutOrNull(15_000L)` on connect handshake; idle connections closed automatically
  - [x] Implement `PresenceRegistry` — tracks connected client metadata (role, scopes, IP, connectedAt) in `MutableStateFlow<Map>`; atomic `update {}` operations; `snapshot()` for `hello-ok` response
  - [x] Implement config-first secret resolver — `buildSecretResolver()` checks `GatewayConfig.secrets` first, falls back to env vars; fixes API keys configured via UI being silently ignored
  - [x] Write tests for all of the above (AuthRateLimiterTest, MethodScopeRegistryTest, UnauthorizedFloodGuardTest, PresenceRegistryTest, SessionRegistryTest, ConfigManagerTest, WebSocketHandlerTest extensions)

---

### Epic 3: Agent Engine
> **Dependencies:** Epic 1 (protocol), Epic 2 (gateway-core)
> **Outcome:** Koog-powered agent that can receive messages, call an LLM, execute tools, and stream responses

- [x] **3.0 Agent engine bug fixes & facade (2026-03-05)**
  - [x] Fix `StreamBridge` double-fire — removed `ToolCallComplete` branch from `onLLMStreamingFrameReceived`; `onToolCallStarting` already handles starts correctly
  - [x] Wire `TokenUsage` from Koog — approximate output token counting via `StreamBridge.currentOutputTokens()` (chars ÷ 4); `completionTokens` + `totalTokens` now non-zero
  - [x] Implement `AgentEngine.kt` facade — `AgentEngine` class composes `ModelRegistry → AgentRuntime → RunManager → AgentResolver`; was previously an empty file
  - [x] Apply `perFileCharLimit` in `PromptAssembler` — `enforcePerFileLimits()` now called before `enforceWorkspaceTotalCap`; individual files capped at 20,000 chars
  - [x] Fix `RunManager.steer()` — cancel-and-log pattern; steer messages were previously enqueued into a channel nobody read
  - [x] Fix `JsonlSessionProvider` index race condition — global `indexMutex` (`withIndexWrite`) wraps all `loadIndex()`/`saveIndex()` pairs; concurrent sessions no longer overwrite each other's index entries
  - [x] Remove unused `SessionMetadataTable` — replaced with TODO for when a dedicated SQL metadata store becomes necessary

- [x] **3.1 Koog integration scaffold**
  - [x] Create `agent-engine/` module with Koog dependencies (koog-agents, prompt-executor-anthropic, prompt-executor-openai)
  - [x] Implement `ModelRegistry` — resolve ModelId to Koog PromptExecutor
  - [x] Configure at least 2 providers: Anthropic (Claude Sonnet 4.5) + OpenAI (GPT-4.1)
  - [x] Write test: ModelRegistry resolves known model, throws on unknown

- [x] **3.2 Workspace loader**
  - [x] Implement `WorkspaceLoader` class reading from configurable workspace path
  - [x] Implement `Path.readSoulMd()`, `readAgentsMd()`, `readUserMd()`, `readToolsMd()`, `readMemoryMd()` extension functions
  - [x] Handle missing files gracefully (return empty string, not crash)
  - [x] Write tests: reads existing files, returns empty for missing, respects workspace path

- [x] **3.2.1 Workspace bootstrap & template system**
  - [x] Implement `WorkspaceState` data class + `OnboardingPhase` enum (NEW, BOOTSTRAP_SEEDED, ONBOARDING_COMPLETE)
  - [x] Implement `WorkspaceBootstrap` state machine: fresh workspace seeding, legacy detection (SOUL.md >50 chars), BOOTSTRAP.md deletion → completion
  - [x] Create 8 workspace template files (BOOTSTRAP, SOUL, AGENTS, IDENTITY, USER, TOOLS, HEARTBEAT, MEMORY)
  - [x] Add `readIdentityMd()`, `readBootstrapMd()`, `readHeartbeatMd()` to WorkspaceLoader + 2MB file size guard
  - [x] Add `identity`, `bootstrap`, `heartbeat` fields to PromptContext
  - [x] Add BOOTSTRAP (first, conditional) and IDENTITY (after SOUL) sections to PromptAssembler
  - [x] Wire new workspace fields through AgentRuntime
  - [x] Call `WorkspaceBootstrap.ensureWorkspace()` at app startup in Main.kt

- [x] **3.3 System prompt assembly (v1 — workspace-only)**
  - [x] Implement `SystemPromptBuilder` DSL (`section()`, `xml()`)
  - [x] Implement `assembleSystemPrompt(context: PromptContext)` with initial workspace-file assembly
  - [x] Inject date/timezone, model name, channel name into runtime section
  - [x] Write tests: prompt contains all sections in order, runtime info is populated
  - _Note: Story 3.8 restructures this into the full two-zone architecture (system instructions → workspace files → runtime)_

- [x] **3.4 Koog strategy graph (agent loop)**
  - [x] Implement `openClawStrategy` using Koog's strategy DSL: start → LLM request → tool execution → loop → finish
  - [x] Wire compaction node: trigger when message count exceeds threshold
  - [x] Implement `AgentRuntime.executeRun()` returning `Flow<AgentEvent>`
  - [x] Handle StopReason.EndTurn → emit RunComplete, StopReason.ToolUse → execute tools and loop
  - [x] Write test: mock LLM returns text → RunComplete event emitted

- [x] **3.5 Agent run management**
  - [x] Implement per-session Mutex for serialized runs
  - [x] Implement `withTimeoutOrNull(600.seconds)` wrapping agent loop
  - [x] Implement QueueAction handling via `Channel<QueueAction>` (replace, steer, ignore)
  - [x] Wire `agent.run` / `agent.stop` / `agent.status` RPC handlers to AgentRuntime
  - [x] Write tests: concurrent runs serialize, timeout cancels, steer injects message

- [x] **3.6 Agent isolation (multi-agent)**
  - [x] Implement agent resolution from config: `agents.defaults` + `agents.list[]` overrides
  - [x] Implement binding resolution: match channel/accountId/chatType/peer → agent
  - [x] Each agent gets own workspace path, model, tool policy
  - [x] Write tests: binding matches correct agent, defaults apply when no override

- [x] **3.7 Session history integration**
  - [x] Wire SessionStore into AgentRuntime for conversation history loading + message persistence
  - [x] Auto-create sessions on first message
  - [x] Seed prior user/assistant messages into Koog prompt via PromptBuilder
  - [x] Persist user messages and assistant responses after each run

- [x] **3.8 System prompt restructure (two-zone architecture)**
  > Ref: SPEC.md §9 — System Prompt Architecture
  - [x] Restructure `PromptAssembler` into Zone 1 (system instructions) → Zone 2 (workspace files) → Zone 3 (runtime)
  - [x] Add identity line section ("You are a personal AI assistant.")
  - [x] Add auto-generated tool descriptions section from `ToolRegistry` via `ToolRegistryFactory.buildDescriptions()` (name + description)
  - [x] Add tool call style directive ("don't narrate routine tool calls")
  - [x] Add safety rules section (anti-power-seeking, human oversight, privacy boundary)
  - [x] Add authorized senders section (owner identity from config)
  - [x] Group all workspace files under single "Workspace" header: AGENTS → SOUL → TOOLS → IDENTITY → USER → HEARTBEAT → BOOTSTRAP → MEMORY
  - [x] Add `PromptMode` enum (FULL, MINIMAL, NONE) to `PromptContext`
  - [x] Add `SessionType` enum (MAIN, GROUP, SUBAGENT, CRON) to `PromptContext`
  - [x] Update `PromptContext` to accept tool descriptions list, safety text, authorized sender, prompt mode

- [x] **3.9 Rich template content**
  > Ref: SPEC.md §10 — Workspace Templates & Agent Behavior
  - [x] Rewrite SOUL.md: opinionated personality, self-modification awareness, continuity framing, self-update permission
  - [x] Rewrite AGENTS.md: session boot sequence, two-tier memory architecture, heartbeat behavior, memory maintenance, skill usage, self-improvement loop
  - [x] Rewrite BOOTSTRAP.md: full conversational onboarding ritual ("Hey. I just came online. Who am I?"), identity discovery, channel connection, BOOTSTRAP.md self-deletion
  - [x] Update IDENTITY.md: structured fields (name, creature, vibe, emoji, avatar), motivational framing
  - [x] Update USER.md: structured fields (name, pronouns, timezone, context section)
  - [x] Update TOOLS.md: clarify this is for local environment notes, not tool definitions
  - [x] Update MEMORY.md: clarify two-tier architecture, daily files vs curated wisdom

- [x] **3.10 Session-type prompt filtering**
  > Ref: SPEC.md §9.3, §9.5
  - [x] Add `SessionType` enum or field to `PromptContext` (MAIN, GROUP, SUBAGENT, CRON, HEARTBEAT)
  - [x] Implement MEMORY.md exclusion for group chat sessions (security boundary)
  - [x] Implement minimal workspace file set for subagent/cron sessions (AGENTS, TOOLS, SOUL, IDENTITY, USER only)
  - [x] Implement BOOTSTRAP.md conditional injection (only when file exists on disk)
  - [x] Wire session type from RPC handler through RunManager → AgentRuntime → PromptAssembler

- [x] **3.11 Pre-compaction memory flush**
  > Ref: SPEC.md §11.3
  - [x] Implement silent agentic turn injection before compaction trigger fires
  - [x] Inject system message: "Pre-compaction memory flush. Store durable memories now."
  - [x] Agent runs one tool-use turn to write `memory/YYYY-MM-DD.md`, then compaction proceeds
  - [x] Handle NO_REPLY response (nothing to store — skip)
  - [x] Wire into compaction trigger in session store

- [ ] **3.11.1 Compaction authority cleanup**
  > Ref: SPEC.md §3.4, §4
  - [ ] Remove Koog `nodeLLMCompressHistory(...)` from the primary runtime strategy path
  - [ ] Make `CompactionEngine` the sole authoritative transcript compaction policy
  - [ ] Keep Koog persistence for execution recovery only, not transcript compaction
  - [ ] Write regression tests: long tool loop does not invoke Koog prompt-history compression implicitly

- [x] **3.12 Prompt size limits & truncation**
  > Ref: SPEC.md §9.6
  - [x] Implement per-file truncation with head/tail markers (default 20,000 chars)
  - [x] Implement total workspace content cap (default 150,000 chars)
  - [x] Implement skills block cap (default 30,000 chars / 150 skills max)
  - [x] Make limits configurable via `PromptLimits` data class

---

### Epic 4: Session Store & Persistence
> **Dependencies:** Epic 1 (protocol)
> **Outcome:** JSONL session transcripts + archival compaction chain + Koog runtime persistence working together

- [x] **4.1 JSONL session provider**
  - [x] Implement `JsonlSessionProvider` with `sessions.json` index file
  - [x] Implement `appendMessage()` — append one JSON line per message to `.jsonl` file
  - [x] Implement `getHistory()` — read and parse `.jsonl` file
  - [x] Implement `SessionMessage` sealed class (User, Assistant, ToolCall, ToolResult, System, CompactionSummary)
  - [x] Implement `SessionWriteLock` using Mutex per session key
  - [x] Write tests: append → read round-trip, concurrent writes serialize correctly

- [x] **4.2 Session lifecycle operations**
  - [x] Implement `createSession()`, `getSession()`, `listSessions()`, `deleteSession()`, `resetSession()`
  - [x] Implement session metadata tracking (model, usage, last message, compaction count)
  - [x] Wire `sessions.list` / `sessions.history` / `sessions.delete` / `sessions.reset` RPC handlers
  - [x] Write tests: full CRUD lifecycle

- [x] **4.3 Compaction v1 (in-place rewrite, now superseded)**
  - [x] Implement compaction trigger: detect when token count approaches limit (~200K)
  - [x] Implement compaction logic: take oldest N messages → LLM summarization → ~2K token summary
  - [x] Replace old messages with `CompactionSummary` in JSONL
  - [x] Update session metadata (compaction count, token savings)
  - [x] Write tests: compaction fires at threshold, summary replaces old messages

- [ ] **4.4 Compaction v2 (archival rotation + lineage)**
  > Ref: SPEC.md §3.4, §4, §11.3
  - [ ] Archive the previous active transcript instead of overwriting it in place
  - [ ] Rename archived transcripts with timestamped `.compacted.<ISO-8601-UTC>.jsonl` suffixes
  - [ ] Create a new active transcript file with a new UUID after every compaction
  - [ ] Persist archive lineage (`previousArchiveFileId`) so compacted transcripts form a retrievable chain
  - [ ] Change the keep-window budget from `triggerThreshold / 2` to `triggerThreshold / 4`
  - [ ] Exclude prior compaction summaries from the summarization source set
  - [ ] Extend history/retrieval APIs to optionally traverse archived transcript chain
  - [ ] Write integration tests: compact twice → archive chain remains intact and active transcript points to newest file

- [x] **4.5 Koog persistence backend (SQLite default)**
  - [x] Set up Exposed ORM with SQLite driver in `session-store/` module
  - [x] Implement Koog persistence storage provider for checkpoint payloads
  - [x] Persist Koog checkpoint state with stable `agentId = sessionKey.raw`
  - [x] Implement checkpoint cleanup on session reset/delete
  - [x] Write tests: save → load latest round-trip, filtered load, delete cleanup

- [x] **4.6 Composite session store**
  - [x] Keep JSONL as the source of truth for conversation history
  - [x] Keep Koog persistence as the source of truth for resumable execution state
  - [x] Route reset/delete cleanup through the checkpoint cleaner instead of custom recovery logic
  - [x] Write integration test: crash → recover execution from Koog persistence + replay JSONL history

---

## Phase 2: Plugin System & WebChat

> **Decision (2026-03-11):** Channel connectors (Telegram, Discord, Slack, etc.) are removed from
> core scope. All external channels will be implemented as plugins in the future. The plugin API
> is now the primary extension mechanism for channels, tools, skills, services, and hooks.
> WebChat (browser-based UI) is the only built-in surface for now — it's served directly from
> the Ktor gateway with no external dependencies.

### Epic 5: Plugin API
> **Dependencies:** Epic 1 (protocol), Epic 2 (gateway-core), Epic 3 (agent-engine)
> **Outcome:** A clean registration API that any plugin can use to extend the gateway with
> channels, tools, skills, services, hooks, HTTP routes, and gateway RPC methods. Channel
> connectors (Discord, Telegram, etc.) are future plugins built against this API.

- [ ] **5.1 Plugin API contracts**
  - [ ] Create `plugins/` module
  - [ ] Define `OpenClawPlugin` interface — `register(api: OpenClawPluginApi)`
  - [ ] Define `OpenClawPluginApi` with registration methods:
    - `registerChannel(connector: ChannelConnector)` — add a messaging surface
    - `registerTool(factory: PluginToolFactory)` — inject agent tools (per-session context-aware)
    - `registerSkillDir(path: Path)` — contribute a skills directory (merged with existing sources)
    - `registerService(service: PluginService)` — background start/stop with gateway lifecycle
    - `registerHook(event: PluginHookEvent, handler: PluginHookHandler)` — intercept agent lifecycle
    - `registerHttpRoute(path: String, handler: HttpRouteHandler)` — add Ktor HTTP endpoints
    - `registerGatewayMethod(method: String, handler: GatewayRpcHandler)` — add WS RPC methods
  - [ ] Define `PluginService` interface (`start(ctx)` / `stop(ctx)`)
  - [ ] Define `ChannelConnector` interface (`connect`, `disconnect`, `incomingMessages: Flow<InboundEnvelope>`, `sendMessage`, `capabilities`)
  - [ ] Define `PluginHookEvent` sealed class: `BeforeToolCall`, `AfterToolCall`, `AgentStart`, `AgentEnd`
  - [ ] Write tests: API surface compiles, types are correct

- [ ] **5.2 Plugin host**
  - [ ] Implement `PluginHost` — collects registered channels, tools, skills, services, hooks
  - [ ] `PluginHost.install(plugin: OpenClawPlugin)` — calls `plugin.register(api)`, accumulates registrations
  - [ ] `PluginHost.startAll()` — start all registered services in parallel (SupervisorScope)
  - [ ] `PluginHost.stopAll()` — graceful shutdown of all services
  - [ ] Expose registered skill dirs to `SkillLoader` (feeds into `pluginSkillsPaths`)
  - [ ] Expose registered tools to `NativeToolsProvider` / `ToolPolicyPipeline`
  - [ ] Expose registered channels to gateway routing layer
  - [ ] Wire into `Main.kt` at startup
  - [ ] Write tests: install two plugins → both services start, both tools registered

- [ ] **5.3 Hook dispatch**
  - [ ] Implement `PluginHookDispatcher` — fan-out hook events to registered handlers
  - [ ] Wire `BeforeToolCall` / `AfterToolCall` hooks into `AgentRuntime` tool execution path
  - [ ] Wire `AgentStart` / `AgentEnd` hooks into `RunManager`
  - [ ] Handler errors are caught and logged (never crash the agent turn)
  - [ ] Write tests: hook fires, multiple handlers all called, error in one doesn't block others

- [ ] **5.4 Inbound envelope & access control**
  - [ ] Define `InboundEnvelope` as the normalized message contract for all channel connectors
  - [ ] Implement `AccessControl` — `checkAccess(envelope): AccessDecision` (ALLOW / DENY / PAIR)
  - [ ] Implement `DmPolicy` enum (PAIRING, ALLOWLIST, OPEN) per-channel-connector config
  - [ ] Implement `allowFrom` list matching
  - [ ] Write tests: allowed sender passes, unknown sender blocked or paired

---

### Epic 6: WebChat
> **Dependencies:** Epic 5 (plugin API), Epic 3 (agent-engine)
> **Outcome:** Browser-based chat UI served directly from the Ktor gateway. The only built-in
> channel — all other connectors are future plugins.

- [ ] **6.1 WebChat connector (plugin)**
  - [ ] Implement `WebChatPlugin : OpenClawPlugin` — self-registers via `api.registerChannel()`
  - [ ] Implement `WebChatConnector : ChannelConnector`
  - [ ] Browser connects via WebSocket to Ktor gateway
  - [ ] Inbound: browser sends text messages, received as `InboundEnvelope`
  - [ ] Outbound: agent token deltas streamed to browser in real time
  - [ ] Serve static placeholder HTML/JS SPA from Ktor static routes
  - [ ] Write integration test: browser WS → agent → streaming response back

- [ ] **6.2 WebChat UI (minimal)**
  - [ ] Single-page chat interface (plain HTML + JS, no framework dependency)
  - [ ] Text input, send button, streaming response display
  - [ ] Session ID management (persisted in localStorage)
  - [ ] Bundled as static resources in `app/` module resources

---

## Phase 3: Skills & Tools

### Epic 7: Skills System
> **Dependencies:** Epic 3 (agent-engine, workspace loader)
> **Outcome:** SKILL.md parsing, lazy-load injection, gating — skills appear in agent prompts

- [x] **7.1 SKILL.md parser**
  - [x] Add `kaml` YAML dependency to `skills/` module
  - [x] Implement YAML frontmatter extraction from markdown files
  - [x] Parse into `SkillMeta` (name, description, path, requires, dependencies)
  - [x] Handle malformed frontmatter gracefully (skip skill, log warning)
  - [x] Write tests: valid skill parses, missing fields → defaults, bad YAML → skip

- [x] **7.2 Skill scanner & gating**
  - [x] Implement `SkillScanner` — scan directories in precedence order: workspace/skills/ → ~/.openclaw/skills/ → bundled
  - [x] Implement gating: check required binaries exist (`which`), env vars set, model capabilities match
  - [x] Return eligible skills only (gating failures logged, not thrown)
  - [x] Write tests: binary check passes/fails, precedence ordering correct

- [x] **7.3 Skill injection & lazy loading**
  - [x] Implement `SkillLoader.buildPromptXml()` — compact XML (~24 tokens per skill)
  - [x] Implement `SkillLoader.buildSnapshot()` — cached at session start, reused across turns
  - [x] Implement `loadFull()` — read full SKILL.md content when agent requests it
  - [x] Integrate into `WorkspaceLoader.assembleSystemPrompt()` at the skills section
  - [x] Write tests: XML output is compact, snapshot caches correctly, full load returns content

- [x] **7.4 Skills source parity & precedence fix**
  > Fixes gaps vs TS reference found during audit (2026-03-11)

  - [ ] **Fix precedence reversal** — `SkillScanner.scanDetailedSources()` uses a `seen` set (first-wins), but
    `defaultSources()` adds WORKSPACE last, so workspace skills lose to bundled on name conflicts.
    Reverse the deduplication logic: scan all sources, deduplicate giving priority to the last
    (highest-precedence) occurrence — matching TS's `Map` overwrite behaviour.
  - [ ] **Fix `always` flag bypass** — `SkillGate.evaluate()` runs all bin/env/os checks even on
    `always: true` skills, which can block skills that should always be included. Add an early
    `if (skill.always) return SkillGateResult.Eligible` before any checks, matching TS
    `evaluateRuntimeEligibility` behaviour.
  - [ ] **Add missing source dirs** — `defaultSources()` is missing two TS sources:
    - `~/.agents/skills/` → `SkillSource.AGENTS_PERSONAL` (between USER and WORKSPACE)
    - `{workspace}/.agents/skills/` → `SkillSource.AGENTS_PROJECT` (between AGENTS_PERSONAL and WORKSPACE)
    Add both enum values and wire into `defaultSources()` in correct precedence position.
  - [ ] **Add `bundledSkillsDir` auto-resolution** — KT currently takes `bundledSkillsPath: Path?` as a raw
    parameter with no fallback. Implement `BundledSkillsResolver.resolve(): Path?` that mirrors TS
    `resolveBundledSkillsDir()`:
    1. `OPENCLAW_BUNDLED_SKILLS_DIR` env var override
    2. `<executableDir>/skills/` (sibling to JAR/binary)
    3. Walk class-path roots looking for a `skills/` dir containing `SKILL.md` subdirs
    Wire into `Main.kt` startup so bundled skills are auto-discovered without explicit path config.
  - [ ] Write/update tests for all four fixes:
    - Workspace skill overrides same-named bundled skill
    - `always: true` skill with missing binary is still eligible
    - `~/.agents/skills/` and `{workspace}/.agents/skills/` appear in source list at correct precedence
    - `BundledSkillsResolver` returns path when env var set, falls back to sibling dir

---

### Epic 8: Tools System
> **Dependencies:** Epic 3 (agent-engine), Epic 4 (session-store)
> **Outcome:** exec, filesystem, session tools registered in Koog ToolRegistry with policy enforcement

- [x] **8.1 Tool policy pipeline**
  - [x] Implement `ToolPolicyPipeline` with 6 cascading stages
  - [x] Stage 1: Profile base allowlist (full, safe, minimal, none)
  - [x] Stage 2: Global allow/deny lists
  - [x] Stage 3: Per-provider restrictions (`tools.byProvider`)
  - [x] Stage 4: Per-agent overrides (`agents.list[i].tools`)
  - [x] Stage 5: Sandbox restrictions
  - [x] Stage 6: Owner-only gate (cron, gateway tools)
  - [x] Implement `ToolGroup` expansion (group:runtime → exec,process; group:fs → read,write,edit)
  - [x] Write tests: each stage narrows correctly, cascading works, groups expand

- [x] **8.2 Shell execution tool (`exec`)**
  - [x] Create `tools/exec/` module
  - [x] Implement `ExecArgs` data class with `@LLMDescription` annotations
  - [x] Implement `exec` tool using ProcessBuilder with configurable timeout
  - [x] Implement background execution mode with session tracking
  - [x] Implement `process` tool for managing background jobs (list, poll, stop)
  - [x] Write tests: command runs, timeout fires, background job tracked

- [x] **8.3 Filesystem tools (read, write, edit)**
  - [x] Create `tools/filesystem/` module
  - [x] Implement `read` tool — read file contents with optional offset/limit
  - [x] Implement `write` tool — write file contents (workspace-only guard)
  - [x] Implement `edit` tool — surgical text replacement (old_string → new_string)
  - [x] Implement workspace boundary checking (prevent reads outside workspace when configured)
  - [x] Write tests: read existing file, write creates/overwrites, edit replaces, boundary guard blocks

- [x] **8.4 OpenClaw-native tools**
  - [x] Implement `sessions_list` tool — list active sessions
  - [x] Implement `sessions_history` tool — get session conversation history
  - [x] Implement `sessions_send` tool — send message to another session
  - [x] Implement `session_status` tool — current session info (timestamp, model, tokens, compaction count)
  - [x] Implement `agents_list` tool — list configured agents
  - [x] Register all tools in Koog ToolRegistry

- [ ] **8.5 MCP tools integration**
  - [x] Implement MCP server connection config and provider
  - [x] Support stdio and SSE transport mode configuration
  - [ ] Replace placeholder provider with Koog-native MCP integration via `McpToolRegistryProvider`
  - [ ] Wire stdio transport using spawned process handles and SSE transport using Koog defaults
  - [ ] Surface discovered MCP tools into the main ToolRegistry at startup
  - [ ] Manage MCP connection/process cleanup on shutdown
  - [ ] Write tests: provider handles configs, validates enabled/disabled, and returns tools from injected Koog registry factories

- [x] **8.6 Web tools (web_search, web_fetch)**
  - [x] Create `tools/web/` module with Jsoup + Ktor client dependencies
  - [x] Implement `ContentExtractor` — Jsoup HTML-to-markdown converter (strips nav/footer/script, converts headings/links/lists/code)
  - [x] Implement `WebToolsHttpClient` — shared Ktor CIO HttpClient singleton with browser UA, 30s timeout
  - [x] Implement `web_fetch` tool — HTTP GET + content extraction (HTML→markdown, JSON→pretty-print, plain text passthrough, truncation)
  - [x] Implement `web_search` tool — Brave Search API integration with in-memory cache (15min TTL, 100 entries)
  - [x] Add `WEB` group to `ToolGroup`, add `web_search`/`web_fetch` to `SAFE` profile
  - [x] Wire into `Main.kt` with conditional `WebSearchTool.isAvailable()` check
  - [x] Write tests: ContentExtractorTest, WebFetchToolTest, WebSearchToolTest (all using Ktor MockEngine)

---

## Phase 4: Advanced Features

### Epic 9: Multi-Agent Orchestration
> **Dependencies:** Epic 3 (agent-engine), Epic 4 (session-store)
> **Outcome:** Sub-agent spawning, parallel execution, steering, session isolation

- [ ] **9.1 Sub-agent spawning**
  - [ ] Implement `AgentOrchestrator.spawn()` — create child agent in coroutine scope
  - [ ] Implement `SubAgentHandle` with `await()`, `steer()`, `cancel()`
  - [ ] Implement session key derivation for subagents: `agent:parent:subagent:ID`
  - [ ] Implement minimal prompt mode (stripped of personality, user profile, formatting)
  - [ ] Write tests: spawn completes, result returned, session key correct

- [ ] **9.2 Parallel execution**
  - [ ] Implement `parallelSpawn()` using `supervisorScope` + `async`
  - [ ] Independent failure: one sub-agent failing doesn't kill others
  - [ ] Implement `sessions_spawn` tool (args: agent, model, task, mode, timeout)
  - [ ] Write tests: 3 parallel spawns complete, one failure returns partial results

- [ ] **9.3 Steering**
  - [ ] Implement mid-run message injection via `Channel<QueueAction>.send(Steer(msg))`
  - [ ] Agent loop checks for steer messages between tool executions
  - [ ] Implement via `sessions_send` tool targeting a running session
  - [ ] Write test: send steer message during tool loop, agent incorporates it

---

### Epic 10: Cron Scheduler
> **Dependencies:** Epic 3 (agent-engine), Epic 4 (session-store), Epic 5 (plugin API)
> **Outcome:** Time-triggered agent runs with optional delivery via registered channel plugins

- [ ] **10.1 Cron core scheduler**
  - [ ] Create `cron/` module with kotlinx-datetime dependency
  - [ ] Implement cron expression parser (or use quartz-scheduler library)
  - [ ] Implement `CronScheduler` — coroutine-based scheduling with `delay()` to next fire time
  - [ ] Implement SHA-256 stagger offset per job (prevent thundering herd)
  - [ ] Write tests: cron expression parses, next fire time correct, stagger distributes

- [ ] **10.2 Job store**
  - [ ] Implement `CronJobStore` backed by JSON file (`~/.openclaw/cron/jobs.json`)
  - [ ] Implement async Mutex for serialized writes (prevent race conditions)
  - [ ] CRUD operations: add, remove, update, list
  - [ ] Write tests: add job persists, remove deletes, concurrent updates serialize

- [ ] **10.3 Execution modes**
  - [ ] Implement `INJECT` mode — inject system event into main session
  - [ ] Implement `ISOLATED` mode — full standalone agent turn with own session key (`agent:main:cron:ID`)
  - [ ] Implement run log (per-job JSONL, pruned at 2MB/2000 lines)
  - [ ] Write tests: inject adds to main session, isolated creates new session

- [ ] **10.4 Delivery & RPC**
  - [ ] Implement channel delivery — send cron results via registered channel plugin (if any connected)
  - [ ] Implement webhook delivery — HTTP POST with SSRF protection (block internal IPs, 10s timeout)
  - [ ] Wire `cron.list` / `cron.add` / `cron.update` / `cron.remove` / `cron.run` RPC handlers
  - [ ] Implement `cron` agent tool (owner-only gate)
  - [ ] Write integration test: add cron job → fires → delivers to channel

---

### Epic 11: Built-in Plugins
> **Dependencies:** Epic 5 (plugin API)
> **Outcome:** First-party plugins that ship with the core distribution, demonstrating the plugin
> API and providing useful default capabilities. These are the reference implementation for
> third-party plugin authors.
> _Note: The plugin host, API contracts, and hook dispatch were moved to Epic 5._

- [ ] **11.1 Audit log plugin**
  - [ ] Implement `AuditLogPlugin : OpenClawPlugin`
  - [ ] On `BeforeToolCall` hook: log tool name, args, session key
  - [ ] On `AfterToolCall` hook: log result summary, duration
  - [ ] Write JSONL audit log to `~/.openclawk/audit/YYYY-MM-DD.jsonl`
  - [ ] Write tests: audit log contains expected entries after tool call

- [ ] **11.2 Session memory plugin**
  - [ ] Implement `SessionMemoryPlugin : OpenClawPlugin`
  - [ ] On `AgentEnd` hook: prompt agent to flush memories to `memory/YYYY-MM-DD.md`
  - [ ] Skip if session was too short (< N turns) or already flushed in this session
  - [ ] Write tests: memory file updated after session, short sessions skipped

- [ ] **11.3 Example channel plugin (Discord)**
  - [ ] Implement `DiscordPlugin : OpenClawPlugin` as a reference channel plugin
  - [ ] Registers `DiscordConnector` via `api.registerChannel()`
  - [ ] Ships `discord/` skills directory via `api.registerSkillDir()`
  - [ ] Handle inbound: messages, slash commands, threads, reactions
  - [ ] Handle outbound: send text, embeds with Discord markdown
  - [ ] Write integration test with mock Discord gateway
  - [ ] _Serves as the canonical example for implementing other channel plugins_

---

### Epic 12: Memory System
> **Dependencies:** Epic 3 (agent-engine)
> **Outcome:** Hybrid vector + keyword search over workspace and transcripts

- [x] **12.1 Embedding providers**
  - [x] Create `memory/` module
  - [x] Implement `EmbeddingProvider` interface
  - [x] Implement `OpenAIEmbeddings` (text-embedding-3-small)
  - [x] Implement `OllamaEmbeddings` (nomic-embed-text, local)
  - [x] Implement content-hash `EmbeddingCache` (prevent redundant API calls)
  - [x] Write tests: embeddings returned, cache hit avoids API call

- [x] **12.2 Chunking & indexing**
  - [x] Implement `MarkdownChunker` — ~400 token chunks with 80-token overlap
  - [x] Implement `MemoryIndexManager.index()` — scan workspace markdown + session transcripts
  - [x] Store chunks with embeddings for vector search
  - [x] Write tests: document chunks correctly, overlap works, index builds

- [x] **12.3 Hybrid search**
  - [x] Implement `VectorSearch` — top-K by cosine similarity
  - [x] Implement `BM25Search` — top-K by BM25 keyword ranking
  - [x] Implement `HybridSearch` — merge scores (70% vector, 30% BM25)
  - [x] Implement snippet formatting (~700 chars, include line ranges)
  - [x] Write tests: search returns relevant results, hybrid outperforms individual

- [x] **12.4 Memory tools & RPC**
  - [x] Implement `memory_search` agent tool — query → ranked snippets
  - [x] Implement `memory_get` agent tool — retrieve specific file by path
  - [x] Wire `memory.search` / `memory.index` / `memory.status` RPC handlers
  - [x] Write integration test: index workspace → search returns relevant content

---

## Phase 5: Production Readiness

### Epic 13: Observability & Monitoring
> **Dependencies:** Epic 3 (agent-engine), Epic 2 (gateway-core)
> **Outcome:** OpenTelemetry traces, metrics, structured logging throughout the system

- [ ] **13.1 Structured logging**
  - [ ] Add kotlin-logging + Logback across all modules
  - [ ] Implement structured JSON log format with correlation IDs (session key, run ID)
  - [ ] Add log levels: agent turns (INFO), tool calls (DEBUG), frame routing (TRACE)
  - [ ] Write tests: log output contains expected fields

- [ ] **13.2 OpenTelemetry integration**
  - [ ] Add OpenTelemetry SDK dependencies
  - [ ] Instrument agent runs: span per run, child spans per tool call and model request
  - [ ] Instrument gateway: span per RPC method, WS connection lifecycle
  - [x] Configure Koog's built-in Tracing feature in `AgentRuntime` with native Koog trace output
  - [ ] Export Koog trace output into OpenTelemetry / Langfuse / Weave
  - [ ] Write test: trace exported with correct parent-child span relationships

- [ ] **13.3 Metrics & health**
  - [ ] Expose Prometheus-compatible metrics endpoint
  - [ ] Track: active sessions, agent runs/min, tool calls/min, model latency, error rate
  - [ ] Implement `GET /health` with detailed subsystem checks (persistence backend, channels, model providers)

---

### Epic 14: Security Hardening
> **Dependencies:** Epic 2 (gateway-core), Epic 8 (tools)
> **Outcome:** Production-grade security across all layers

- [ ] **14.1 Network & auth hardening**
  - [ ] Enforce loopback binding by default (`127.0.0.1`)
  - [ ] Implement token rotation support in auth profiles
  - [x] Implement per-IP auth rate limiting (`AuthRateLimiter` — done in Story 2.7)
  - [x] Implement unauthorized request flood guard (`UnauthorizedFloodGuard` — done in Story 2.7)
  - [x] Implement WS handshake timeout (done in Story 2.7)
  - [ ] Implement graceful shutdown: SIGTERM → drain active sessions → close WS → persist state
  - [ ] Implement origin check for browser clients (CSRF protection — block cross-origin WS from non-allowlisted domains)
  - [ ] Implement device identity from public key (cryptographic binding — `deriveDeviceIdFromPublicKey`; currently uses random UUID per connection)

- [ ] **14.2 Tool sandboxing**
  - [ ] Implement workspace boundary enforcement for filesystem tools
  - [ ] Implement command blocklist for exec tool (dangerous patterns)
  - [ ] Document Docker sandbox integration (container lifecycle: session/agent/shared modes)
  - [ ] Write tests: boundary violations blocked, dangerous commands rejected

- [ ] **14.3 SSRF protection & secret handling**
  - [ ] Implement `fetchWithSsrfGuard` — block internal IPs, private ranges in webhook URLs
  - [ ] Implement credential redaction in CLI output and logs
  - [ ] Implement `openclaw security audit` command (scan config for risky settings)
  - [ ] Write tests: internal URLs blocked, secrets redacted in output

---

### Epic 15: CLI & Documentation
> **Dependencies:** All previous epics
> **Outcome:** Full CLI interface and documentation for operators

- [ ] **15.1 CLI commands**
  - [ ] Create `cli/` module with Clikt dependency
  - [ ] Implement `openclaw gateway start/stop/status/restart`
  - [ ] Implement `openclaw agent --message --to --model`
  - [ ] Implement `openclaw config get/set`
  - [ ] Implement `openclaw channels status --probe`
  - [ ] Implement `openclaw memory index/search/status`
  - [ ] Implement `openclaw doctor --fix`
  - [ ] Write tests: each command parses args correctly, outputs expected format

- [ ] **15.2 Onboarding wizard**
  - [ ] Implement `openclaw onboard` — interactive setup
  - [ ] Auth profile configuration (API keys for Anthropic, OpenAI, etc.)
  - [ ] Channel setup (Telegram token, Discord token, etc.)
  - [ ] Workspace initialization (create SOUL.md template, AGENTS.md, etc.)
  - [ ] Daemon installation (launchd plist on macOS, systemd unit on Linux)

- [ ] **15.3 App entry point**
  - [ ] Create `app/` module composing all subsystems
  - [ ] Implement `main()` — load config → init gateway → start channels → start cron → bind RPC handlers
  - [ ] Implement `./gradlew :app:run` for development
  - [ ] Implement `./gradlew :app:shadowJar` for production fat JAR
  - [ ] Write Dockerfile with multi-stage build (Gradle → JRE runtime)
  - [ ] Write `docker-compose.yml` with gateway + mounted persistence volume

- [ ] **15.4 Documentation**
  - [ ] Write `docs/architecture.md` — system overview for contributors
  - [ ] Write `docs/configuration.md` — full config reference with examples
  - [ ] Write `docs/channels.md` — per-channel setup guides
  - [ ] Write `docs/skills.md` — how to create and register custom skills
  - [ ] Write `docs/deployment.md` — local, Docker, and cloud deployment guides

---

## Summary

| Phase | Epics | Focus |
|-------|-------|-------|
| **1. Core** | 1–4 | Protocol, Gateway, Agent Engine, Sessions ✅ |
| **2. Plugin System & WebChat** | 5–6 | Plugin API, WebChat UI |
| **3. Skills & Tools** | 7–8 | Skills, Tools ✅ (mostly) |
| **4. Advanced** | 9–12 | Orchestration, Cron, Built-in Plugins, Memory ✅ |
| **5. Production** | 13–15 | Observability, Security, CLI |

> Channel connectors (Discord, Telegram, Slack, etc.) are out of core scope.
> They will be implemented as separate plugins using the Epic 5 plugin API.

### Dependency Chain

```
Epic 1 (Protocol) ──→ Epic 2 (Gateway) ──→ Epic 3 (Agent Engine) ──→ Epic 5 (Plugin API)
                                      └──→ Epic 4 (Sessions)     └──→ Epic 6 (WebChat Plugin)
                                                                  └──→ Epic 7 (Skills) ✅
                                                                  └──→ Epic 8 (Tools) ✅
                                                                  └──→ Epic 9 (Orchestrator)
                                                                  └──→ Epic 10 (Cron)
                                                                  └──→ Epic 11 (Built-in Plugins)
                                                                  └──→ Epic 12 (Memory) ✅
                                                                  └──→ Epic 13-15 (Production)
```
