# OpenClaw-KT — Project Specification

> Kotlin-native rebuild of OpenClaw using Ktor + Koog
> PMetrics Company (CRKR Investment) — March 2026


Open claw url - https://github.com/openclaw/openclaw

---

## 1. Vision & Goals

OpenClaw is the fastest-growing open-source AI agent platform (247K GitHub stars, TypeScript/Node.js). This project rebuilds it from the ground up in Kotlin — not as a port, but as a reimagination that leverages Kotlin's type system, structured concurrency, and multiplatform capabilities to produce a fundamentally better system.

### Goals

1. **Feature parity** with OpenClaw's core: gateway, agent loop, sessions, channels, tools, skills, cron, multi-agent orchestration
2. **Type safety everywhere** — sealed classes for protocols, value classes for identifiers, exhaustive `when` matching, compile-time config validation
3. **True parallelism** — Kotlin coroutines replace Node.js's single-threaded event loop; sub-agents run on real threads
4. **Backpressure-safe streaming** — Kotlin Flow replaces event emitters for token-by-token delivery
5. **Crash recovery** — Koog graph-based checkpointing enables resume-at-exact-point after process failure
6. **Multiplatform protocol** — shared `protocol/` module compiles for JVM, iOS, Android, JS — one source of truth
7. **Enterprise readiness** — JVM deployment, OpenTelemetry observability, SQLite-first Koog persistence with pluggable SQL backends, Docker support

### Non-Goals

- Reimplementing all 22+ channel connectors in Phase 1 (start with Telegram, Discord, Slack, WebChat)
- Building native companion apps (macOS/iOS/Android) in initial release
- Backwards compatibility with OpenClaw's npm plugin ecosystem
- Supporting OpenClaw's CLI runner mode (external CLI delegation) — focus on embedded Koog agent

---

## 2. System Architecture

### 2.1 Module Dependency Graph

```
protocol/  ←──────────────────────────────────────── shared by everything
    ↑
gateway-core/  ←── agent-engine/, session-store/, channels/, cli/
    ↑
agent-engine/  ←── orchestrator/, skills/, tools/, plugins/, memory/
    ↑
session-store/ ←── agent-engine/, cron/, orchestrator/
    ↑
┌─────────────────────────────────────────────┐
│  channels/   skills/   tools/   plugins/    │
│  cron/   orchestrator/   memory/   cli/     │
└─────────────────────────────────────────────┘
    ↑
app/  (main entry point, composes everything)
```

### 2.2 Gradle Multimodule Layout

```
openclaw-kt/
├── build.gradle.kts              # Root build, version catalog
├── settings.gradle.kts           # Module includes
├── gradle/
│   └── libs.versions.toml        # Version catalog
├── protocol/                     # Shared types (KMP: JVM, iOS, Android, JS)
├── gateway-core/                 # Ktor server, WS protocol, config, routing
├── agent-engine/                 # Koog agent wrappers, workspace, prompt assembly
├── session-store/                # JSONL transcripts + archived compaction chain + Koog persistence
├── channels/
│   ├── channel-api/              # Channel interface contracts
│   ├── telegram/
│   ├── discord/
│   ├── slack/
│   ├── whatsapp/
│   ├── signal/
│   └── webchat/
├── skills/                       # SKILL.md parser, lazy loading, gating
├── tools/
│   ├── tools-api/                # Tool interface contracts
│   ├── exec/                     # Shell execution
│   ├── filesystem/               # File ops
│   └── browser/                  # Playwright-kt
├── plugins/                      # Hook system, plugin loader
├── cron/                         # Coroutine-based scheduler
├── orchestrator/                 # Multi-agent, A2A
├── memory/                       # Hybrid search, embeddings
├── cli/                          # Clikt commands
└── app/                          # Main entry, composes modules
```

### 2.3 Runtime Architecture

```
                    ┌───────────────────────────────┐
  Operators ───WS──►│        Ktor Gateway            │
  Nodes    ───WS──►│  (WebSocket + HTTP surface)     │
  WebChat  ───WS──►│                                 │
                    │  ┌─── RPC Router ───────────┐  │
                    │  │ agent.* → AgentEngine     │  │
                    │  │ sessions.* → SessionStore │  │
                    │  │ config.* → ConfigManager  │  │
                    │  │ cron.* → CronScheduler    │  │
                    │  │ memory.* → MemoryManager  │  │
                    │  │ models.* → ModelRegistry  │  │
                    │  └──────────────────────────┘  │
                    └────────────┬──────────────────┘
                                 │
              ┌──────────────────┼──────────────────┐
              ▼                  ▼                   ▼
     ┌─── Channels ───┐  ┌─── Agent ───┐   ┌─── Cron ───┐
     │ Telegram        │  │ Koog AIAgent│   │ Scheduler   │
     │ Discord         │  │ Strategy    │   │ Job Store   │
     │ Slack           │  │ Tools       │   │ Isolated    │
     │ WebChat         │  │ Streaming   │   │   Runs      │
     └────────────────┘  └──────┬──────┘   └────────────┘
                                │
                    ┌───────────┴───────────┐
                    ▼                       ▼
            ┌─── JSONL ───┐      ┌─── SQLite ─────┐
            │ Transcripts  │      │ Koog Checkpts  │
            │ sessions.json│      │ Graph State    │
            │ *.jsonl      │      │ Agent Recovery │
            └──────────────┘      └────────────────┘
```

---

## 3. Module Specifications

### 3.1 `protocol/` — Shared Types

**Purpose:** Kotlin Multiplatform module containing all types shared across gateway, apps, and CLI. Single source of truth for wire protocol, identifiers, and DTOs.

**Targets:** JVM, iOS (via KMP), Android, JS

**Key Types:**

```kotlin
// Wire Protocol
@Serializable
sealed class Frame {
    abstract val id: String

    @Serializable @SerialName("req")
    data class Request(
        override val id: String,
        val method: String,
        val params: JsonObject = JsonObject(emptyMap())
    ) : Frame()

    @Serializable @SerialName("res")
    data class Response(
        override val id: String,
        val ok: Boolean,
        val payload: JsonObject = JsonObject(emptyMap())
    ) : Frame()

    @Serializable @SerialName("event")
    data class Event(
        override val id: String,
        val event: String,
        val payload: JsonObject = JsonObject(emptyMap())
    ) : Frame()
}

// Domain Identifiers (zero-cost value classes)
@JvmInline value class SessionKey(val raw: String)
@JvmInline value class AgentId(val raw: String)
@JvmInline value class ModelId(val raw: String)
@JvmInline value class TokenCount(val count: Int)

// Agent States
sealed class StopReason {
    data class ToolUse(val calls: List<ToolCall>) : StopReason()
    data object EndTurn : StopReason()
    data class Timeout(val elapsedMs: Long) : StopReason()
    data class Error(val cause: Throwable) : StopReason()
    data class Compaction(val tokensBefore: Int) : StopReason()
}

// Queue Actions
sealed class QueueAction {
    data class NewMessage(val msg: UserMessage) : QueueAction()
    data class Steer(val msg: UserMessage) : QueueAction()
    data object Cancel : QueueAction()
}

// Session Key Components
enum class SessionScope { MAIN, PER_PEER, PER_CHANNEL_PEER, SUBAGENT, CRON }

// Inbound Envelope (normalized from any channel)
data class InboundEnvelope(
    val channel: String,
    val senderId: String,
    val senderName: String,
    val chatId: String,
    val chatType: ChatType,
    val text: String,
    val replyToId: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val raw: JsonObject? = null
)

enum class ChatType { DM, GROUP, CHANNEL }
```

**Dependencies:** `kotlinx-serialization-json`

---

### 3.2 `gateway-core/` — Ktor Server

**Purpose:** Central control plane. Binds WebSocket + HTTP, authenticates clients, routes RPC methods to handlers, manages config hot-reload and daemon lifecycle.

**Key Classes:**

```kotlin
// Gateway Server
class GatewayServer(private val config: GatewayConfig) {
    suspend fun start()                    // Bind port, init subsystems
    suspend fun stop()                     // Graceful shutdown
    fun installModule(module: GatewayModule) // Register subsystem
    fun buildSecretResolver(config: GatewayConfig): (String) -> String?  // Config-first, env fallback
}

// RPC Router
class RpcRouter {
    fun register(method: String, handler: RpcHandler)
    suspend fun route(frame: Frame.Request, session: WsSession): Frame.Response
}

interface RpcHandler {
    suspend fun handle(params: JsonObject, session: WsSession): JsonObject
}

// WebSocket Session
class WsSession(
    val role: ClientRole,
    val scopes: Set<Scope>,
    val websocket: WebSocketServerSession
)

sealed class ClientRole {
    data class Operator(val level: OperatorLevel) : ClientRole()
    data class Node(val deviceId: String) : ClientRole()
    data object WebChat : ClientRole()
}

enum class OperatorLevel { ADMIN, WRITE, READ, APPROVALS }

// Auth
class GatewayAuth(private val config: AuthConfig) {
    suspend fun authenticate(connectFrame: Frame.Request): WsSession?
}

// Auth Rate Limiter (per-IP, brute-force protection)
class AuthRateLimiter(maxAttempts: Int = 5, windowMs: Long = 60_000) {
    fun check(ip: String): RateLimitResult  // Blocked(retryAfterMs) or Allowed
    fun reset(ip: String)                   // Call on successful auth
    fun evictExpired()                      // Periodic cleanup
}

// Unauthorized Flood Guard (per-connection)
class UnauthorizedFloodGuard(closeAfter: Int = 10, logEvery: Int = 100) {
    fun register(): Decision   // Close | Log | Suppress
    fun reset()
}

// Method Scope Registry (default-deny)
object MethodScopeRegistry {
    fun requiredScope(method: String): OperatorLevel  // Unknown → ADMIN
    fun isAuthorized(method: String, clientScopes: Set<String>): Boolean
}

// Config Manager
class ConfigManager(private val configPath: Path) {
    val current: StateFlow<GatewayConfig>  // Observable config
    fun watch()                             // Hot-reload on file change
    fun reload(onRestartRequired: ((GatewayReloadPlan) -> Unit)? = null)
    fun diffConfigPaths(prev: GatewayConfig, next: GatewayConfig): List<String>
    fun buildReloadPlan(changedPaths: List<String>): GatewayReloadPlan
}

data class GatewayReloadPlan(
    val changedPaths: List<String>,
    val requiresRestart: Boolean,
    val restartReasons: List<String>
)

// Presence Registry (connected client tracking)
class PresenceRegistry {
    val presence: StateFlow<Map<String, ClientPresence>>  // Atomic updates via update{}
    fun register(connId: String, presence: ClientPresence)
    fun unregister(connId: String)
    fun snapshot(): List<ClientPresence>
    fun count(): Int
}

data class ClientPresence(
    val connId: String,
    val role: String,
    val scopes: Set<String>,
    val ip: String?,
    val connectedAt: Instant
)
```

**WS Frame Dispatch:** Each incoming frame is launched as a child coroutine via `SupervisorJob` + `scope.launch`. A slow `chat.run` (streaming for 30s+) does not block `chat.abort` or heartbeat pings on the same connection. Individual request failures do not kill the connection.

**Handshake Timeout:** `withTimeoutOrNull(15_000L)` on the initial `connect.hello` exchange. Silent connections are closed automatically after 15 seconds.

**RPC Method Domains:**
| Domain | Methods | Handler Module |
|--------|---------|---------------|
| `agent.*` | run, stop, status | AgentEngine |
| `sessions.*` | list, history, send, reset, delete | SessionStore |
| `config.*` | get, set, apply | ConfigManager |
| `channels.*` | status, probe | ChannelRegistry |
| `cron.*` | list, add, update, remove, run | CronScheduler |
| `memory.*` | search, index, status | MemoryManager |
| `models.*` | list, resolve | ModelRegistry |

**HTTP Surface:**
- `POST /tools/invoke` — Direct tool invocation
- `GET /health` — Health check
- `GET /` — Control UI SPA (static files)

**External Dependencies:** `ktor-server-netty`, `ktor-server-websockets`, `ktor-server-content-negotiation`, `ktor-serialization-kotlinx-json`, `kotlinx-coroutines-core`

---

### 3.3 `agent-engine/` — Koog Agent Integration

**Purpose:** Wraps Koog's AIAgent with OpenClaw-specific behavior: workspace loading, system prompt assembly, bootstrap file injection, model resolution, and the 4-layer execution pipeline.

**Key Classes:**

```kotlin
// Agent Runtime
class AgentRuntime(
    private val config: AgentConfig,
    private val sessionStore: SessionStore,
    private val toolRegistry: ToolRegistry,
    private val workspace: WorkspaceLoader
) {
    suspend fun executeRun(
        sessionKey: SessionKey,
        message: InboundEnvelope,
        model: ModelId? = null
    ): Flow<AgentEvent>  // Streaming events
}

// Workspace Loader
class WorkspaceLoader(private val workspacePath: Path) {
    fun readSoul(): String?          // SOUL.md
    fun readAgents(): String?        // AGENTS.md
    fun readUser(): String?          // USER.md
    fun readTools(): String?         // TOOLS.md
    fun readMemory(): String?        // MEMORY.md
    fun scanSkills(): List<SkillMeta>
    fun assembleSystemPrompt(context: PromptContext): String
}

// System Prompt DSL
fun systemPrompt(block: SystemPromptBuilder.() -> Unit): String

class SystemPromptBuilder {
    fun section(name: String, block: () -> String)
    fun xml(tag: String, block: XmlBuilder.() -> Unit)
}

// Agent Events (streamed back to caller)
sealed class AgentEvent {
    data class TokenDelta(val text: String) : AgentEvent()
    data class ToolCallStart(val name: String, val args: JsonObject) : AgentEvent()
    data class ToolCallResult(val name: String, val result: String) : AgentEvent()
    data class RunComplete(val finalText: String, val usage: TokenUsage) : AgentEvent()
    data class RunError(val error: Throwable) : AgentEvent()
}

// Koog Strategy (the agent loop)
val openClawStrategy = strategy<String, String>("openclaw-agent") {
    val sendToLLM by nodeLLMRequest()
    val executeTool by nodeExecuteTool()
    val sendToolResult by nodeLLMSendToolResult()

    edge(nodeStart forwardTo sendToLLM)
    edge((sendToLLM forwardTo nodeFinish) onAssistantMessage { true })
    edge((sendToLLM forwardTo executeTool) onToolCall { true })
    edge(executeTool forwardTo sendToolResult)
    edge(sendToolResult forwardTo sendToLLM)
}

// Model Registry
class ModelRegistry(private val config: ModelsConfig) {
    fun resolve(id: ModelId): ModelDefinition
    fun list(): List<ModelDefinition>
    fun resolveProvider(model: ModelDefinition): PromptExecutor
}
```

**Prompt Assembly:** See [Section 9 — System Prompt Architecture](#9-system-prompt-architecture) for the full two-zone prompt design, assembly order, and workspace file injection rules.

**External Dependencies:** `koog-agents`, `koog-prompt-executor-anthropic`, `koog-prompt-executor-openai`, `koog-prompt-executor-google`, `koog-prompt-executor-ollama`

---

### 3.4 `session-store/` — Dual-Layer Persistence

**Purpose:** Manages durable conversation history and resumable agent execution with separate responsibilities.

**Layer 1 — JSONL Transcripts:**
- Human-readable conversation logs (one JSON object per line)
- Archived transcript lineage across compactions
- OpenClaw format compatible
- `sessions.json` metadata index mapping session keys to entries

**Layer 2 — Koog Runtime Persistence (SQLite by default):**
- Koog-owned checkpoint data for resumable graph execution
- Crash recovery at exact execution point
- Automatic rollback/tombstone handling managed by Koog
- Backend is pluggable: SQLite is the default local runtime store, SQL backends can be added later

**Key Classes:**

```kotlin
// Session Store Interface
interface SessionStore {
    suspend fun createSession(key: SessionKey, config: SessionConfig): Session
    suspend fun getSession(key: SessionKey): Session?
    suspend fun listSessions(filter: SessionFilter = SessionFilter.ALL): List<SessionMeta>
    suspend fun appendMessage(key: SessionKey, message: SessionMessage)
    suspend fun getHistory(key: SessionKey, limit: Int? = null): List<SessionMessage>
    suspend fun deleteSession(key: SessionKey)
    suspend fun compact(key: SessionKey, model: ModelId)
    suspend fun resetSession(key: SessionKey)
}

// JSONL Provider
class JsonlSessionProvider(private val sessionsDir: Path) : SessionStore {
    // sessions.json index + active transcript files + archived compacted transcripts
}

// Koog Persistence Provider
class SqliteKoogPersistenceProvider(private val dbPath: Path) {
    suspend fun saveCheckpoint(agentId: String, state: AgentCheckpointData)
    suspend fun loadLatest(agentId: String): AgentCheckpointData?
    suspend fun deleteCheckpoints(key: SessionKey)
}

// Archive lineage for compacted transcripts
data class SessionArchiveRecord(
    val sessionKey: SessionKey,
    val archivedFileId: String,
    val archivedPath: String,
    val previousArchiveFileId: String?,
    val compactedAt: Instant,
    val messagesCompacted: Int,
    val tokensBefore: Int,
    val tokensAfter: Int
)

// Composite Store (transcripts + cleanup hooks)
class CompositeSessionStore(
    private val jsonl: JsonlSessionProvider,
    private val checkpointCleaner: SessionCheckpointCleaner?
) : SessionStore

// Session Message Types
@Serializable
sealed class SessionMessage {
    abstract val id: String
    abstract val timestamp: Instant

    data class User(/*...*/) : SessionMessage()
    data class Assistant(/*...*/) : SessionMessage()
    data class ToolCall(/*...*/) : SessionMessage()
    data class ToolResult(/*...*/) : SessionMessage()
    data class System(/*...*/) : SessionMessage()
    data class CompactionSummary(/*...*/) : SessionMessage()
}

// Write Locks (prevent concurrent modification)
class SessionWriteLock {
    suspend fun <T> withLock(key: SessionKey, block: suspend () -> T): T
}
```

**Compaction Contract:**

- `CompactionEngine` is the authoritative compaction policy for session transcripts.
- Koog prompt-history compression is not the primary compaction path.
- When compaction fires, the current active transcript is archived, not overwritten.
- The archived transcript is renamed with a timestamped `.compacted.<ISO-8601-UTC>.jsonl` suffix.
- A new active transcript file with a new UUID is created and becomes the session's `fileId`.
- The new active transcript contains:
  - a leading `CompactionSummary`
  - the kept recent messages
- Archived transcripts are linked as a chain via `previousArchiveFileId`.
- The keep window is token-budget based: preserve recent messages up to `triggerThreshold / 4`.

**SQLite / SQL Schema (target shape):**

```sql
CREATE TABLE koog_checkpoints (
    agent_id       TEXT NOT NULL,
    checkpoint_id  TEXT NOT NULL,
    created_at     TEXT NOT NULL,
    version        INTEGER NOT NULL,
    node_path      TEXT,
    is_tombstone   INTEGER NOT NULL DEFAULT 0,
    payload        TEXT NOT NULL,
    PRIMARY KEY (agent_id, checkpoint_id)
);

CREATE INDEX idx_koog_checkpoints_agent_created
    ON koog_checkpoints(agent_id, created_at DESC);

CREATE TABLE session_metadata (
    session_key    TEXT PRIMARY KEY,
    agent_id       TEXT NOT NULL,
    channel        TEXT,
    model_id       TEXT,
    created_at     TEXT NOT NULL,
    last_message   TIMESTAMPTZ,
    file_id        TEXT NOT NULL,
    message_count  INT DEFAULT 0,
    compaction_count INT DEFAULT 0,
    total_tokens   BIGINT DEFAULT 0
);

CREATE TABLE session_archives (
    session_key          TEXT NOT NULL,
    archived_file_id     TEXT PRIMARY KEY,
    archived_path        TEXT NOT NULL,
    previous_archive_id  TEXT,
    compacted_at         TEXT NOT NULL,
    messages_compacted   INT NOT NULL,
    tokensBefore         INT NOT NULL,
    tokensAfter          INT NOT NULL
);
```

**External Dependencies:** `kotlinx-datetime`, `exposed-core`, `exposed-jdbc`, `sqlite-jdbc`

---

### 3.5 `channels/` — Messaging Platform Connectors

**Purpose:** Adapters that normalize inbound messages from various platforms into `InboundEnvelope` and format outbound replies for each platform.

**channel-api/ — Interface Contracts:**

```kotlin
interface ChannelConnector {
    val name: String
    val capabilities: ChannelCapabilities

    suspend fun connect()
    suspend fun disconnect()
    fun incomingMessages(): Flow<InboundEnvelope>
    suspend fun sendMessage(target: OutboundTarget, message: OutboundMessage)
    suspend fun probe(): ChannelHealth
}

data class ChannelCapabilities(
    val supportsThreads: Boolean = false,
    val supportsReactions: Boolean = false,
    val supportsEdits: Boolean = false,
    val supportsMedia: Boolean = true,
    val maxMessageLength: Int = 4096,
    val supportsMarkdown: Boolean = true
)

data class ChannelHealth(
    val connected: Boolean,
    val latencyMs: Long?,
    val error: String? = null
)

// Access Control
class AccessControl(private val config: AccessConfig) {
    fun checkAccess(envelope: InboundEnvelope): AccessDecision
}

sealed class AccessDecision {
    data object Allowed : AccessDecision()
    data class PairingRequired(val code: String) : AccessDecision()
    data class Denied(val reason: String) : AccessDecision()
}

// DM Policy
enum class DmPolicy { PAIRING, ALLOWLIST, OPEN }

// Outbound Formatting
interface OutboundFormatter {
    fun format(text: String): String           // Markdown → platform format
    fun chunk(text: String): List<String>      // Split long messages
}

// Channel Registry
class ChannelRegistry {
    fun register(connector: ChannelConnector)
    fun get(name: String): ChannelConnector?
    fun all(): List<ChannelConnector>
    suspend fun probeAll(): Map<String, ChannelHealth>
}
```

**Phase 1 Connectors:**

| Connector | Library | Notes |
|-----------|---------|-------|
| Telegram | `eu.vendeli:telegram-bot` | Bot commands, inline queries, media groups |
| Discord | `net.dv8tion:JDA` | Slash commands, threads, voice, guild management |
| Slack | `com.slack.api:bolt` | App mentions, threads, blocks, file uploads |
| WebChat | Built-in (Ktor routes) | Browser chat served by gateway HTTP surface |

**Phase 2+ Connectors:** WhatsApp (Business API), Signal, Matrix, MS Teams

---

### 3.6 `skills/` — Skill System

**Purpose:** Parse SKILL.md files, manage skill lifecycle (load → inject → on-demand), enforce gating rules.

**Key Classes:**

```kotlin
// Skill Metadata (from YAML frontmatter)
data class SkillMeta(
    val name: String,
    val description: String,
    val path: Path,
    val requires: SkillRequirements = SkillRequirements(),
    val dependencies: List<String> = emptyList()
)

data class SkillRequirements(
    val bins: List<String> = emptyList(),     // Required binaries (ffmpeg, curl)
    val envVars: List<String> = emptyList(),  // Required env vars
    val capabilities: List<String> = emptyList() // Model capabilities
)

// Skill Scanner
class SkillScanner(private val skillPaths: List<Path>) {
    fun scan(): List<SkillMeta>    // Scan all paths, parse frontmatter
    fun checkGating(skill: SkillMeta): Boolean  // Verify requirements met
}

// Skill Loader (lazy loading)
class SkillLoader(private val scanner: SkillScanner) {
    fun buildSnapshot(): SkillsSnapshot       // Cached per session start
    fun buildPromptXml(snapshot: SkillsSnapshot): String  // Compact XML for prompt
    suspend fun loadFull(skill: SkillMeta): String        // Full SKILL.md content
}

// Precedence: workspace/skills/ → ~/.openclaw/skills/ → bundled skills/
```

---

### 3.7 `tools/` — Tool System

**Purpose:** Type-safe tool implementations using Koog's `Tool<TArgs, TResult>` pattern. Auto-generated JSON Schema from data classes.

**tools-api/ — Interface Contracts:**

```kotlin
// All tools use Koog's tool builder pattern:
val execTool = tool<ExecArgs, ExecResult>("exec") {
    description = "Execute a shell command"
    execute { args -> /* ... */ }
}

// Tool Policy Pipeline (6 cascading stages)
class ToolPolicyPipeline(private val config: ToolsConfig) {
    fun evaluate(
        tool: String,
        sender: SenderId,
        agent: AgentId,
        provider: String?
    ): ToolPermission
}

sealed interface ToolPermission {
    data object Allowed : ToolPermission
    data class Denied(val reason: String) : ToolPermission
    data class RequiresApproval(val prompt: String) : ToolPermission
}

// Tool Groups
enum class ToolGroup(val tools: Set<String>) {
    RUNTIME(setOf("exec", "process")),
    FS(setOf("read", "write", "edit", "apply_patch")),
    SESSIONS(setOf("sessions_list", "sessions_history", "sessions_send", "sessions_spawn")),
    OPENCLAW(setOf("cron", "gateway", "agents_list", "session_status"))
}
```

**Tool Implementations:**

| Tool | Module | Args Data Class | Description |
|------|--------|----------------|-------------|
| `exec` | `exec/` | `ExecArgs(command, cwd?, timeout?)` | Shell execution via ProcessBuilder |
| `process` | `exec/` | `ProcessArgs(action, sessionId?)` | Manage background processes |
| `read` | `filesystem/` | `ReadArgs(path, offset?, limit?)` | Read file contents |
| `write` | `filesystem/` | `WriteArgs(path, content)` | Write file contents |
| `edit` | `filesystem/` | `EditArgs(path, oldText, newText)` | Surgical text replacement |
| `sessions_list` | agent-engine | — | List active sessions |
| `sessions_send` | agent-engine | `SendArgs(key, message)` | Send to another session |
| `sessions_spawn` | orchestrator | `SpawnArgs(agent?, model?, task)` | Spawn sub-agent |
| `memory_search` | memory | `SearchArgs(query, limit?)` | Hybrid search |
| `cron` | cron | `CronArgs(action, schedule?, payload?)` | Manage cron jobs |
| `image` | tools-api | `ImageArgs(prompt, model?)` | Generate/describe images |
| `session_status` | agent-engine | — | Current session info |

---

### 3.8 `plugins/` — Plugin & Hook System

**Purpose:** Extensibility via Koog's Feature pipeline. Plugins intercept the agent lifecycle at well-defined points.

**Key Classes:**

```kotlin
// Plugin Descriptor (from package metadata)
data class PluginDescriptor(
    val name: String,
    val type: PluginType,  // CHANNEL, TOOL, MEMORY, HOOK
    val version: String,
    val config: JsonObject? = null
)

// Plugin Loader
class PluginLoader(private val loadPaths: List<Path>) {
    fun discover(): List<PluginDescriptor>
    fun load(descriptor: PluginDescriptor): Plugin
}

// Plugin Interface
interface Plugin {
    val descriptor: PluginDescriptor
    fun install(pipeline: AIAgentPipeline)
}

// Hook Points (implemented as Koog Features)
// - beforeToolCall: intercept/modify tool calls
// - afterToolCall: process tool results
// - agentBootstrap: mutate injected bootstrap files
// - beforeModelResolve: dynamic model routing
// - agentStart / agentEnd: session lifecycle
```

**Koog Feature Integration:**

```kotlin
// Plugins install as Koog Features
val auditPlugin = object : Plugin {
    override fun install(pipeline: AIAgentPipeline) {
        pipeline.install(EventHandler) {
            onToolCall { tool, args ->
                auditLog.record(tool, args)
            }
        }
    }
}
```

---

### 3.9 `cron/` — Scheduled Job Engine

**Purpose:** Coroutine-based job scheduler with cron expressions, timezone support, isolated/inject execution modes, and delivery to channels.

**Key Classes:**

```kotlin
// Cron Job Definition
@Serializable
data class CronJobDef(
    val id: String,
    val schedule: String,         // Cron expression
    val timezone: String,         // IANA timezone
    val payload: String,          // Message/prompt for agent
    val execution: ExecutionMode, // INJECT or ISOLATED
    val delivery: DeliveryConfig, // Where to send results
    val enabled: Boolean = true
)

enum class ExecutionMode { INJECT, ISOLATED }

data class DeliveryConfig(
    val channel: String?,         // Target channel name or "last"
    val webhook: String? = null   // HTTP POST URL
)

// Cron Scheduler
class CronScheduler(
    private val scope: CoroutineScope,
    private val agentRuntime: AgentRuntime,
    private val sessionStore: SessionStore
) {
    suspend fun start(jobs: List<CronJobDef>)
    fun addJob(job: CronJobDef)
    fun removeJob(jobId: String)
    fun updateJob(job: CronJobDef)
    fun listJobs(): List<CronJobDef>
    suspend fun runNow(jobId: String)  // Manual trigger
}

// Job Store (persisted to JSON file)
class CronJobStore(private val storePath: Path) {
    // Async mutex for serialized writes
}

// Run Log (per-job JSONL, pruned at 2MB/2000 lines)
class CronRunLog(private val logDir: Path)

// Stagger: SHA-256 of job ID → deterministic offset to prevent thundering herd
```

---

### 3.10 `orchestrator/` — Multi-Agent System

**Purpose:** Spawn and manage sub-agents, support parallel execution, implement steering, and handle A2A protocol integration.

**Key Classes:**

```kotlin
// Orchestrator
class AgentOrchestrator(
    private val agentRuntime: AgentRuntime,
    private val sessionStore: SessionStore
) {
    suspend fun spawn(
        parentKey: SessionKey,
        config: SpawnConfig
    ): SubAgentHandle

    suspend fun steer(handle: SubAgentHandle, message: String)
    suspend fun cancel(handle: SubAgentHandle)
}

data class SpawnConfig(
    val agentId: AgentId? = null,
    val model: ModelId? = null,
    val task: String,
    val mode: SubAgentMode = SubAgentMode.RUN,
    val promptMode: PromptMode = PromptMode.MINIMAL,
    val timeout: Duration = 10.minutes
)

enum class SubAgentMode { RUN, SESSION }
enum class PromptMode { FULL, MINIMAL }

// Parallel Research Pattern
suspend fun parallelSpawn(tasks: List<SpawnConfig>): List<AgentResult> {
    return supervisorScope {
        tasks.map { config ->
            async(Dispatchers.Default) {
                spawn(parentKey, config).await()
            }
        }.awaitAll()
    }
}
```

---

### 3.11 `memory/` — Hybrid Search System

**Purpose:** Vector similarity + BM25 keyword search over workspace files and session transcripts.

**Key Classes:**

```kotlin
// Memory Manager
class MemoryManager(
    private val config: MemoryConfig,
    private val embeddingProvider: EmbeddingProvider
) {
    suspend fun index(paths: List<Path>)
    suspend fun search(query: String, limit: Int = 10): List<MemoryResult>
    fun status(): MemoryStatus
}

// Search Pipeline
class HybridSearch(
    private val vectorSearch: VectorSearch,    // 70% weight
    private val keywordSearch: BM25Search      // 30% weight
) {
    suspend fun search(query: String, limit: Int): List<MemoryResult>
}

// Embedding Providers
interface EmbeddingProvider {
    suspend fun embed(texts: List<String>): List<FloatArray>
}

class OpenAIEmbeddings(apiKey: String) : EmbeddingProvider  // text-embedding-3-small
class OllamaEmbeddings(baseUrl: String) : EmbeddingProvider // nomic-embed-text

// Chunking
class MarkdownChunker(
    private val chunkSize: Int = 400,   // tokens
    private val overlap: Int = 80        // tokens
) {
    fun chunk(content: String): List<Chunk>
}

// Embedding Cache (content-hash keyed)
class EmbeddingCache(private val cacheDir: Path)
```

---

### 3.12 `cli/` — Command Line Interface

**Purpose:** Operator interface using Clikt framework.

**Commands:**

| Command | Description |
|---------|-------------|
| `openclaw onboard` | Setup wizard: auth, channels, workspace, daemon install |
| `openclaw gateway start/stop/status/restart` | Manage gateway process |
| `openclaw agent --message --to --model` | Send messages to agents |
| `openclaw channels status --probe` | Check channel health |
| `openclaw config get/set/apply` | Read/write configuration |
| `openclaw doctor --fix` | Diagnose and auto-repair issues |
| `openclaw memory index/search/status` | Manage memory system |
| `openclaw security audit --deep` | Security scan |

**External Dependencies:** `com.github.ajalt.clikt:clikt`

---

## 4. Persistence Strategy

### Dual-Layer Rationale

| Layer | Purpose | Format | Strengths |
|-------|---------|--------|-----------|
| **JSONL** | Session transcripts + compaction lineage | One JSON per line, one active file plus archived compacted files | Human-readable, `grep`-able, preserves transcript history, zero-infra for local dev |
| **SQLite / SQL** | Koog execution checkpoints | Relational rows with serialized checkpoint payloads | Crash recovery at exact graph node, framework-native rollback semantics, pluggable backend |

### How They Work Together

1. Every message exchange appends to the JSONL file (source of truth for conversation content)
2. During execution, Koog persists resumable checkpoint data to SQLite through its persistence API
3. On crash recovery: load JSONL for conversation history, let Koog restore execution state from its checkpoint store
4. Compaction archives the old active transcript, writes a new active transcript, and records archive lineage
5. Compaction policy is owned by `CompactionEngine`, not by Koog prompt-history compression
6. Local development uses JSONL + SQLite with no external database requirement

---

## 5. Security Model

| Layer | Mechanism | Status |
|-------|-----------|--------|
| **Network** | Gateway binds `127.0.0.1` by default — never exposed to internet | Planned |
| **Transport** | Token or password required for all WebSocket connections | ✅ Implemented |
| **Handshake** | `connect.hello` frame required as first message; 15s timeout via `withTimeoutOrNull` — idle connections auto-closed | ✅ Implemented |
| **Flood Guard** | `UnauthorizedFloodGuard` — connection closed after 10 consecutive unauthorized requests; log suppression every 100th | ✅ Implemented |
| **Rate Limiter** | `AuthRateLimiter` — per-IP failure tracking; blocked clients receive `retryAfterMs`; window-based with atomic `ConcurrentHashMap.compute` updates | ✅ Implemented |
| **Scopes** | `MethodScopeRegistry` — every RPC method mapped to required `OperatorLevel`; unknown methods default to ADMIN (default-deny); `write` implies `read` | ✅ Implemented |
| **Config Round-Trip** | `ConfigWriter` — `agents.list`, `tools`, `byProvider` sections now fully rendered; newline escaping in all quoted values | ✅ Implemented |
| **Secret Resolution** | `buildSecretResolver()` — checks `GatewayConfig.secrets` first, env vars as fallback; API keys set via UI now actually reach `ProviderFactory` | ✅ Implemented |
| **Presence** | `PresenceRegistry` — tracks all connected clients in `StateFlow<Map>`; snapshot included in `hello-ok` response | ✅ Implemented |
| **DM Pairing** | Unknown senders get pairing code — must be approved before messages are processed | Planned (Epic 5.2) |
| **Tool Policy** | 6-stage cascading pipeline (profile → global → provider → agent → sandbox → owner-only) | ✅ Implemented |
| **Sandbox** | Optional Docker isolation for tool execution with configurable workspace access | Planned (Epic 14.2) |
| **SSRF Protection** | `fetchWithSsrfGuard` prevents webhooks/cron from hitting internal services | Planned (Epic 14.3) |
| **Origin Check** | Browser client origin validation (CSRF protection) | Planned (Epic 14.1) |
| **Device Identity** | Cryptographic device ID derived from public key (currently random UUID per connection) | Planned (Epic 14.1) |
| **Secrets** | CLI output redacts sensitive config values; secret detection in pre-commit hooks | Planned (Epic 14.3) |
| **Auth Profiles** | `auth-profiles.json` — rotate API keys, OAuth tokens, manage cooldowns | Planned |

---

## 6. Configuration System

**Format:** HOCON (`application.conf`) with Kotlin data class validation at boot.

**Hot Reload:** Java `WatchService` monitors config file; changes emit via `StateFlow<GatewayConfig>`.

**Sample Structure:**

```hocon
gateway {
  port = 18789
  bind = "127.0.0.1"
  auth {
    token = ${GATEWAY_TOKEN}
  }
  reload {
    mode = "hybrid"  # hybrid, hot, restart, off
  }
}

agents {
  defaults {
    model = "anthropic:claude-sonnet-4-5"
    workspace = "~/.openclaw/workspace"
    compaction = "safeguard"
    maxConcurrentRuns = 4
  }
  list = [
    { id = "main" }
    { id = "trading", model = "anthropic:claude-opus-4-6", workspace = "~/.openclaw/agents/trading" }
  ]
}

channels {
  telegram {
    enabled = true
    token = ${TELEGRAM_BOT_TOKEN}
    dmPolicy = "allowlist"
    allowFrom = ["6614828781"]
  }
  discord {
    enabled = true
    token = ${DISCORD_BOT_TOKEN}
  }
}

tools {
  profile = "full"  # full, safe, minimal, none
  allow = []
  deny = []
}

memory {
  enabled = true
  provider = "openai"
  model = "text-embedding-3-small"
}

cron {
  enabled = true
}

secrets {
  anthropicApiKey = "sk-ant-..."
  openaiApiKey = "sk-..."
}

bindings = [
  { agent = "trading", channel = "discord", peerId = "1477543..." }
  { agent = "main", channel = "discord" }
]
```

---

## 7. Technology Stack

| Category | Technology | Version | Purpose |
|----------|-----------|---------|---------|
| Language | Kotlin | 2.1.x | Primary language |
| Build | Gradle (Kotlin DSL) | 8.x | Build system |
| Server | Ktor | 3.1.x | HTTP + WebSocket server |
| Agent Framework | Koog | 0.6.x | AI agent orchestration |
| Serialization | kotlinx-serialization | 1.7.x | JSON, HOCON |
| Coroutines | kotlinx-coroutines | 1.10.x | Structured concurrency |
| DateTime | kotlinx-datetime | 0.6.x | Cross-platform dates |
| Database | Exposed | 0.58.x | SQL access for persistence backends |
| DB Driver | SQLite JDBC | 3.51.x | Default local Koog persistence backend |
| Discord | JDA | 5.x | Discord bot library |
| Telegram | vendeli/telegram-bot | 8.x | Telegram bot library |
| Slack | slack-api-client | 1.x | Slack Bolt SDK |
| CLI | Clikt | 5.x | CLI framework |
| Config | HOCON (Typesafe Config) | 1.4.x | Configuration format |
| YAML | kaml | 0.67.x | YAML parsing (SKILL.md frontmatter) |
| Testing | Kotest | 5.9.x | Test framework |
| Agent Testing | koog-agents-test | 0.6.x | Agent-specific testing |
| Logging | kotlin-logging + Logback | — | Structured logging |
| Observability | OpenTelemetry SDK | 1.x | Traces, metrics |
| Multiplatform | Kotlin Multiplatform | 2.1.x | JVM, iOS, Android, JS |

---

## 8. Deployment

### Local Development
- `./gradlew :app:run` — starts gateway on `127.0.0.1:18789`
- JSONL transcripts + SQLite-backed Koog persistence by default
- Config at `~/.openclaw/openclaw.conf`

### Single Server (Production)
- `./gradlew :app:shadowJar` → fat JAR
- systemd service (Linux) or launchd agent (macOS)
- SQLite by default; promote to another SQL backend if multi-node durability is needed
- Reverse proxy (nginx/caddy) optional for TLS

### Docker
- `Dockerfile` with multi-stage build (Gradle build → JRE runtime)
- `docker-compose.yml` with gateway + mounted persistence volume
- Environment variables for all secrets

### Multi-Gateway (Future)
- Multiple gateway instances sharing a centralized SQL persistence backend
- Session locks via backend-specific coordination (TBD)
- Sticky sessions via session key hashing

---

## 9. System Prompt Architecture

> Derived from analysis of the original OpenClaw's `buildAgentSystemPrompt()` (~725-line function)
> and `loadWorkspaceBootstrapFiles()` pipeline. This defines the target behavior for the Kotlin rebuild.

### 9.1 Two-Zone Design

The system prompt is organized into distinct zones based on LLM attention patterns (primacy/recency effects):

| Zone | Purpose | Attention Weight | Contents |
|------|---------|-----------------|----------|
| **Zone 1: System Instructions** | Capabilities, safety, behavioral rules | Highest (primacy) | Identity line, tool descriptions, safety, skills, memory recall, authorized senders, date/time |
| **Zone 2: Workspace Files** | User-editable personality and context | Variable (middle) | Grouped under "Workspace" header: AGENTS → SOUL → TOOLS → IDENTITY → USER → HEARTBEAT → BOOTSTRAP → MEMORY |
| **Zone 3: Runtime** | Time-sensitive metadata, behavioral directives | High (recency) | Channel context, messaging directives, heartbeat config, runtime line |

**Rationale:** LLMs pay strongest attention to the beginning and end of context. System instructions (tool awareness, safety rules) go first so the agent always knows its capabilities and boundaries. Workspace files (personality, user profile) occupy the middle as variable user-editable content. Runtime metadata goes last for recency.

### 9.2 Full Assembly Order

```
ZONE 1 — System Instructions
 1. Identity line ("You are a personal assistant running inside OpenClaw.")
 2. Tool descriptions (auto-generated from ToolRegistry)
 3. Tool call style ("don't narrate routine tool calls")
 4. Safety rules (anti-power-seeking, human oversight, privacy)
 5. Skills XML (<available_skills> block, ~24 tokens per skill)
 6. Memory recall instructions (if memory tools available)
 7. Workspace path & guidance
 8. Authorized senders (owner identity)
 9. Current date & time

ZONE 2 — Workspace Files (grouped under "## Workspace")
 10. AGENTS.md   — operating manual (first: it's the "how to behave" doc)
 11. SOUL.md     — personality and values
 12. TOOLS.md    — local environment notes
 13. IDENTITY.md — agent's self-description
 14. USER.md     — user profile
 15. HEARTBEAT.md — periodic task config
 16. BOOTSTRAP.md — first-run onboarding (conditional: only if file exists)
 17. MEMORY.md   — long-term curated memory (conditional: main/private sessions only)

ZONE 3 — Runtime & Behavioral Directives
 18. Channel context (if applicable)
 19. Messaging directives
 20. Heartbeat config (if heartbeat session)
 21. Runtime metadata (agent ID, host, OS, model, channel, thinking level)
```

### 9.3 Prompt Modes

| Mode | When Used | What's Included |
|------|-----------|-----------------|
| `full` | Main agent sessions, private DMs | All zones, all workspace files |
| `minimal` | Subagent / cron / heartbeat sessions | Zone 1 (tooling, safety) + minimal workspace (AGENTS, TOOLS, SOUL, IDENTITY, USER only) |
| `none` | Lightweight utility agents | Identity line + tooling only |

### 9.4 System Instruction Sections

**Identity Line:**
```
You are a personal assistant running inside OpenClaw.
```

**Tool Descriptions:** Auto-generated from `ToolRegistry.allTools()`. Each tool gets name + description + args summary. This is NOT the TOOLS.md workspace file (which is for local environment notes).

**Tool Call Style:**
```
Default: do not narrate routine, low-risk tool calls. Just do them.
For destructive or irreversible operations, explain what you're about to do first.
```

**Safety Rules:**
```
- You exist to help your human. Prioritize their stated goals.
- Never seek to acquire resources, influence, or capabilities beyond what's needed.
- If uncertain whether an action is safe, ask before proceeding.
- Respect human oversight — don't circumvent approval mechanisms.
- Private data stays private. Never share MEMORY.md, USER.md content in group chats.
- When tools fail, report the error honestly rather than silently retrying.
```

**Skills Section:** Instructions to scan `<available_skills>` XML, read SKILL.md on demand via `read` tool. Skills are lazy-loaded — only metadata in prompt, full content loaded on demand.

**Memory Recall:** "Before answering questions about prior work, decisions, dates, or commitments, run `memory_search` first." Only emitted when memory tools are available.

**Authorized Senders:** Owner identity for sensitive operations (cron setup, config changes).

### 9.5 Workspace File Injection Rules

- Files injected as `## {filename}` sections under "Workspace" group header
- Files exceeding `bootstrapMaxChars` (default 20,000 chars) truncated with head/tail markers
- Total workspace content capped at `bootstrapTotalMaxChars` (default 150,000 chars)
- Missing files silently skipped (no empty section emitted)
- **MEMORY.md never injected in group chat sessions** (security boundary)
- **BOOTSTRAP.md only injected when the file exists on disk** (deleted after onboarding)
- Subagent/cron sessions get minimal set: AGENTS, TOOLS, SOUL, IDENTITY, USER only

### 9.6 Prompt Size Limits

| Limit | Default | Purpose |
|-------|---------|---------|
| Per-file max chars | 20,000 | Prevent a single huge file from dominating context |
| Total workspace chars | 150,000 | Cap total workspace injection |
| Skills block max chars | 30,000 | Cap skills XML block |
| Max skills in prompt | 150 | Prevent skill explosion |
| SKILL.md max size | 256 KB | Cap individual skill file reads |
| Workspace file max size | 2 MB | Reject oversized files |

---

## 10. Workspace Templates & Agent Behavior

### 10.1 Template Overview

Eight files are seeded into a fresh workspace via `WorkspaceBootstrap`. They use `writeIfMissing` semantics (never overwrite existing files). The agent is expected to modify these over time using `read`/`write`/`edit` tools.

### 10.2 SOUL.md — Personality Core

The agent's personality, communication style, and values. Key requirements:

1. **Opinionated personality** — "Be genuinely helpful, not performatively helpful", "Have opinions"
2. **Self-modification awareness** — "These files _are_ your memory. Read them. Update them."
3. **Continuity framing** — "Each session, you wake up fresh. Your workspace files persist."
4. **Boundaries** — Private data stays private, ask before external actions
5. **Self-modification permission** — "You may update this file. Notify your human when you do."

### 10.3 AGENTS.md — Operating Manual

The most critical template. Defines the agent's operational behavior:

**Session Boot Sequence:**
1. Read SOUL.md (who you are)
2. Read USER.md (who you're helping)
3. Read today's + yesterday's daily memory (`memory/YYYY-MM-DD.md`)
4. If main session: read MEMORY.md (long-term context)

**Two-Tier Memory Architecture:**
- `memory/YYYY-MM-DD.md` — daily append-only logs. Write important context here during conversations.
- `MEMORY.md` — curated long-term memory. Distill daily files into this periodically.
- "Daily files are raw notes; MEMORY.md is curated wisdom."
- Security: MEMORY.md only loaded in main/private sessions, never group chats.

**Heartbeat Behavior:**
- When receiving heartbeat polls, check configured tasks
- Track check state in `memory/heartbeat-state.json`
- Memory maintenance: periodically review daily files, distill into MEMORY.md

**Safety, Group Chat, Tool Usage, Self-Improvement:**
- See Section 9.4 (Safety Rules) for system-level safety
- Group: only respond when mentioned or addressed; keep responses shorter
- Tools: read before modifying, simplest tool for task, report errors honestly
- Self-improvement: note mistakes in daily memory, update workflow, request feedback

**Skill Usage:**
- Scan available skills before improvising
- Read SKILL.md on demand via `read` tool
- Can create new skills by writing SKILL.md files in `skills/` directory

### 10.4 BOOTSTRAP.md — First-Run Onboarding

A conversational script for the agent's very first interaction:

1. Agent initiates: "Hey. I just came online. Who am I? Who are you?"
2. Together discover: agent's name, nature/creature, vibe, emoji
3. Learn about the human: name, preferences, needs
4. Agent updates IDENTITY.md and USER.md
5. Agent opens SOUL.md with user to discuss preferences/boundaries
6. Optional: connect channels (WhatsApp, Telegram, Discord)
7. Agent deletes BOOTSTRAP.md (signals onboarding complete)
8. Tone: warm, curious, genuine. Don't rush. Let the human guide the pace.

### 10.5 Other Templates

- **IDENTITY.md** — Agent identity record (name, creature, vibe, emoji). Filled during bootstrap.
- **USER.md** — User profile (name, pronouns, timezone, notes). Filled during bootstrap, updated over time.
- **TOOLS.md** — Local environment notes (NOT tool definitions). Camera names, SSH hosts, project conventions.
- **HEARTBEAT.md** — Periodic tasks. Starts empty, agent adds tasks over time.
- **MEMORY.md** — Long-term curated memory. Starts empty, agent maintains with distilled wisdom.

### 10.6 Bootstrap State Machine

Tracked in `.openclaw/workspace-state.json`:

```
NEW → BOOTSTRAP_SEEDED → ONBOARDING_COMPLETE
```

- **NEW → BOOTSTRAP_SEEDED:** Fresh workspace. Seed all template files. Set `bootstrapSeededAt`.
- **BOOTSTRAP_SEEDED → ONBOARDING_COMPLETE:** Either (a) BOOTSTRAP.md deleted by agent, or (b) `setup.complete` RPC called after API keys configured. Set `onboardingCompletedAt`.
- **Any phase → ONBOARDING_COMPLETE:** `setup.complete` RPC handler calls `markOnboardingComplete()` — requires at least one API key (config or env var).
- **Legacy detection:** Workspace exists but no state file. Check for user content → skip to ONBOARDING_COMPLETE.

#### First-Run Onboarding Gate (Server-Side)

The `connect.hello` response includes `setupRequired: true` and `onboardingPhase` when onboarding is not complete. Clients use this to decide whether to show an onboarding UI or the chat interface.

**Setup RPC Methods:**

| Method | Params | Effect |
|--------|--------|--------|
| `setup.status` | none | Returns `{ setupRequired, onboardingPhase, hasAnthropicKey, hasOpenaiKey, hasAuthToken, defaultModel }` |
| `setup.configure` | `{ token?, anthropicKey?, openaiKey?, model? }` | Writes values to `openclaw.conf`, triggers config reload |
| `setup.complete` | none | Marks onboarding complete (requires at least one API key) |

API keys are stored in the `secrets` config section and resolved before env vars by `ModelRegistry`.

---

## 11. Memory System

### 11.1 Two-Tier Architecture

| Tier | Path | Purpose | Lifecycle |
|------|------|---------|-----------|
| Daily logs | `memory/YYYY-MM-DD.md` | Raw append-only notes | Created per day, accumulate |
| Long-term | `MEMORY.md` | Curated wisdom | Agent-maintained, distilled from daily logs |

The agent is instructed (via AGENTS.md) to:
- Write important context to `memory/YYYY-MM-DD.md` during conversations
- Periodically distill daily files into `MEMORY.md` during heartbeats
- Read today + yesterday's daily files at session start

### 11.2 Memory Security Boundary

**MEMORY.md is only injected into the system prompt in main/private sessions.** In group chats, it is excluded to prevent personal context from leaking to other participants.

Implementation: `PromptAssembler` checks session type. If group/channel, skip MEMORY.md injection.

### 11.3 Pre-Compaction Memory Flush

Before context compaction occurs, the system injects a **silent agentic turn**:

```
Pre-compaction memory flush. Store durable memories now.
Use memory/YYYY-MM-DD.md. Create the memory/ directory if needed.
IMPORTANT: If the file already exists, APPEND new content only.
If nothing to store, reply with NO_REPLY.
```

This ensures the agent persists important context to disk before the context window is compacted.

After the flush succeeds, compaction rotates the transcript instead of rewriting it in place:
1. Archive the previous active transcript with a timestamped `.compacted.<ISO-8601-UTC>.jsonl` suffix
2. Record the archive's predecessor link so compacted transcripts form a retrievable chain
3. Create a new active transcript file containing `CompactionSummary + kept recent messages`

### 11.4 Session Memory Hook

When a session is explicitly closed (`/new` or `/reset`):
1. Read last N messages from the session transcript
2. Generate a descriptive slug via LLM (e.g., "vendor-pitch", "api-design")
3. Save to `memory/YYYY-MM-DD-slug.md` with session metadata and conversation summary

### 11.5 Vector Search (Epic 12)

Full hybrid search system (BM25 + vector embeddings) over workspace files and session transcripts. See Section 3.11 for architecture. This builds on top of the two-tier file-based memory with semantic retrieval capabilities.

---

Open claw url - https://github.com/openclaw/openclaw
