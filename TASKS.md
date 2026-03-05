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

---

### Epic 3: Agent Engine
> **Dependencies:** Epic 1 (protocol), Epic 2 (gateway-core)
> **Outcome:** Koog-powered agent that can receive messages, call an LLM, execute tools, and stream responses

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

- [x] **3.12 Prompt size limits & truncation**
  > Ref: SPEC.md §9.6
  - [x] Implement per-file truncation with head/tail markers (default 20,000 chars)
  - [x] Implement total workspace content cap (default 150,000 chars)
  - [x] Implement skills block cap (default 30,000 chars / 150 skills max)
  - [x] Make limits configurable via `PromptLimits` data class

---

### Epic 4: Session Store & Persistence
> **Dependencies:** Epic 1 (protocol)
> **Outcome:** JSONL session transcripts + Postgres checkpoints working together

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

- [x] **4.3 Compaction**
  - [x] Implement compaction trigger: detect when token count approaches limit (~200K)
  - [x] Implement compaction logic: take oldest N messages → LLM summarization → ~2K token summary
  - [x] Replace old messages with `CompactionSummary` in JSONL
  - [x] Update session metadata (compaction count, token savings)
  - [x] Write tests: compaction fires at threshold, summary replaces old messages

- [x] **4.4 Postgres checkpoint provider**
  - [x] Set up Exposed ORM with PostgreSQL driver in `session-store/` module
  - [x] Create `checkpoints` table (session_key, graph_node, graph_state JSONB, updated_at)
  - [x] Create `session_metadata` table (session_key, agent_id, channel, model_id, timestamps, counts)
  - [x] Implement `PostgresCheckpointProvider.saveCheckpoint()` / `loadCheckpoint()` / `deleteCheckpoints()`
  - [x] Write tests (with testcontainers-postgres): save → load round-trip, delete cleans up

- [x] **4.5 Composite session store**
  - [x] Implement `CompositeSessionStore` combining JSONL + Postgres providers
  - [x] Writes go to both layers; reads prefer JSONL for history, Postgres for checkpoint state
  - [x] Implement fallback: if Postgres unavailable, degrade to JSONL-only mode
  - [x] Write integration test: crash → recover from checkpoint + replay JSONL

---

## Phase 2: Channels

### Epic 5: Channel Infrastructure
> **Dependencies:** Epic 1 (protocol), Epic 2 (gateway-core)
> **Outcome:** Channel interface, registry, access control, and outbound formatting ready for connectors

- [ ] **5.1 Channel interface & registry**
  - [ ] Create `channels/channel-api/` module
  - [ ] Implement `ChannelConnector` interface (connect, disconnect, incomingMessages Flow, sendMessage, probe)
  - [ ] Implement `ChannelCapabilities` data class (threads, reactions, edits, media, maxLength, markdown)
  - [ ] Implement `ChannelRegistry` with registration, lookup, and `probeAll()`
  - [ ] Wire `channels.status` / `channels.probe` RPC handlers

- [ ] **5.2 Access control & DM policy**
  - [ ] Implement `AccessControl` class with `checkAccess(envelope): AccessDecision`
  - [ ] Implement `DmPolicy` enum (PAIRING, ALLOWLIST, OPEN)
  - [ ] Implement pairing code generation and approval flow
  - [ ] Implement `allowFrom` list matching per channel config
  - [ ] Write tests: allowed user passes, unknown user gets pairing, denied user blocked

- [ ] **5.3 Outbound formatting**
  - [ ] Implement `OutboundFormatter` interface with `format()` and `chunk()`
  - [ ] Implement markdown-to-platform conversion base (handles code blocks, bold, italic, links)
  - [ ] Implement message chunking for platforms with length limits
  - [ ] Write tests: long message → correct chunks, markdown converts properly

- [ ] **5.4 Envelope normalization pipeline**
  - [ ] Implement `InboundEnvelope` construction from raw channel messages
  - [ ] Implement attachment normalization (images, files, voice → common Attachment type)
  - [ ] Implement group routing logic (mention gating, reply tags)

---

### Epic 6: Platform Connectors
> **Dependencies:** Epic 5 (channel-api)
> **Outcome:** Working Telegram, Discord, Slack, and WebChat connectors

- [ ] **6.1 Telegram connector**
  - [ ] Create `channels/telegram/` module with vendeli/telegram-bot dependency
  - [ ] Implement `TelegramConnector : ChannelConnector`
  - [ ] Handle inbound: text messages, media groups, bot commands, inline queries
  - [ ] Handle outbound: send text, images, files with Telegram markdown formatting
  - [ ] Implement `TelegramFormatter` (Telegram MarkdownV2 escaping)
  - [ ] Write integration test with mock Telegram API

- [ ] **6.2 Discord connector**
  - [ ] Create `channels/discord/` module with JDA dependency
  - [ ] Implement `DiscordConnector : ChannelConnector`
  - [ ] Handle inbound: messages, slash commands, threads, reactions
  - [ ] Handle outbound: send text, embeds, files with Discord markdown
  - [ ] Implement guild management (per-guild config, user allowlists)
  - [ ] Implement `DiscordFormatter` (Discord markdown, 2000-char chunks)
  - [ ] Write integration test with mock Discord gateway

- [ ] **6.3 Slack connector**
  - [ ] Create `channels/slack/` module with slack-api-client dependency
  - [ ] Implement `SlackConnector : ChannelConnector`
  - [ ] Handle inbound: app mentions, DMs, threads, file uploads
  - [ ] Handle outbound: send text, blocks, files with Slack mrkdwn
  - [ ] Implement `SlackFormatter` (Slack mrkdwn syntax)
  - [ ] Write integration test with mock Slack events API

- [ ] **6.4 WebChat connector**
  - [ ] Implement `WebChatConnector` served directly from Ktor HTTP surface
  - [ ] WebSocket-based: browser connects, sends messages, receives streaming responses
  - [ ] Serve static SPA files from gateway (placeholder HTML for now)
  - [ ] Write integration test: browser WS → agent → streaming response back

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

- [x] **8.5 MCP tools integration**
  - [x] Implement MCP server connection config and provider (placeholder — actual client deferred until Koog adds native MCP support)
  - [x] Support stdio and SSE transport mode configuration
  - [x] Transform MCP tools into Koog Tool interface (adapter pattern ready)
  - [x] Write tests: provider handles configs, returns empty when no servers, validates enabled/disabled

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
> **Dependencies:** Epic 3 (agent-engine), Epic 4 (session-store), Epic 5 (channels)
> **Outcome:** Time-triggered agent runs with channel delivery

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
  - [ ] Implement channel delivery — send cron results to specified channel (or "last" used channel)
  - [ ] Implement webhook delivery — HTTP POST with SSRF protection (block internal IPs, 10s timeout)
  - [ ] Wire `cron.list` / `cron.add` / `cron.update` / `cron.remove` / `cron.run` RPC handlers
  - [ ] Implement `cron` agent tool (owner-only gate)
  - [ ] Write integration test: add cron job → fires → delivers to channel

---

### Epic 11: Plugin System
> **Dependencies:** Epic 3 (agent-engine)
> **Outcome:** Plugins can intercept agent lifecycle via Koog Features

- [ ] **11.1 Plugin loader**
  - [ ] Create `plugins/` module
  - [ ] Implement `PluginDescriptor` data class (name, type, version, config)
  - [ ] Implement `PluginLoader.discover()` — scan configured paths for plugin packages
  - [ ] Implement `PluginLoader.load()` — instantiate plugin, validate descriptor
  - [ ] Write tests: discover finds plugins, invalid plugin skipped with warning

- [ ] **11.2 Koog Feature hooks**
  - [ ] Implement `Plugin` interface with `install(pipeline: AIAgentPipeline)`
  - [ ] Map hook points to Koog Features:
    - `beforeToolCall` → EventHandler.onToolCall
    - `afterToolCall` → EventHandler.onToolResult
    - `agentStart` / `agentEnd` → lifecycle Feature
    - `beforeModelResolve` → ModelRouter Feature
    - `agentBootstrap` → custom Feature that mutates bootstrap files
  - [ ] Write tests: hook fires at correct point, multiple plugins compose

- [ ] **11.3 Built-in plugins**
  - [ ] Implement `AuditLogPlugin` — log all tool calls and model requests
  - [ ] Implement `SessionMemoryPlugin` — auto-update MEMORY.md after sessions
  - [ ] Write tests: audit log records events, session memory updates file

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
  - [ ] Configure Koog's built-in Tracing feature (Langfuse or W&B Weave)
  - [ ] Write test: trace exported with correct parent-child span relationships

- [ ] **13.3 Metrics & health**
  - [ ] Expose Prometheus-compatible metrics endpoint
  - [ ] Track: active sessions, agent runs/min, tool calls/min, model latency, error rate
  - [ ] Implement `GET /health` with detailed subsystem checks (Postgres, channels, model providers)

---

### Epic 14: Security Hardening
> **Dependencies:** Epic 2 (gateway-core), Epic 8 (tools)
> **Outcome:** Production-grade security across all layers

- [ ] **14.1 Network & auth hardening**
  - [ ] Enforce loopback binding by default (`127.0.0.1`)
  - [ ] Implement token rotation support in auth profiles
  - [ ] Implement rate limiting per client role (Ktor RateLimit plugin)
  - [ ] Implement graceful shutdown: SIGTERM → drain active sessions → close WS → persist state

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
  - [ ] Write `docker-compose.yml` with gateway + postgres services

- [ ] **15.4 Documentation**
  - [ ] Write `docs/architecture.md` — system overview for contributors
  - [ ] Write `docs/configuration.md` — full config reference with examples
  - [ ] Write `docs/channels.md` — per-channel setup guides
  - [ ] Write `docs/skills.md` — how to create and register custom skills
  - [ ] Write `docs/deployment.md` — local, Docker, and cloud deployment guides

---

## Summary

| Phase | Epics | Stories | Subtasks |
|-------|-------|---------|----------|
| **1. Core** | 4 | 25 | ~100 |
| **2. Channels** | 2 | 8 | 32 |
| **3. Skills & Tools** | 2 | 8 | 28 |
| **4. Advanced** | 4 | 12 | 36 |
| **5. Production** | 3 | 10 | 28 |
| **Total** | **15** | **63** | **~225** |

### Dependency Chain

```
Epic 1 (Protocol) ──→ Epic 2 (Gateway) ──→ Epic 3 (Agent Engine) ──→ Epic 5 (Channel Infra)
                                      └──→ Epic 4 (Sessions)     └──→ Epic 6 (Connectors)
                                                                  └──→ Epic 7 (Skills)
                                                                  └──→ Epic 8 (Tools)
                                                                  └──→ Epic 9 (Orchestrator)
                                                                  └──→ Epic 10 (Cron)
                                                                  └──→ Epic 11 (Plugins)
                                                                  └──→ Epic 12 (Memory)
                                                                  └──→ Epic 13-15 (Production)
```
