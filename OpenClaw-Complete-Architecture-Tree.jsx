import { useState, useCallback } from "react";

const COLORS = {
  root: "#1B2A4A",
  gateway: "#2E75B6",
  agents: "#7F52FF",
  channels: "#2D8A4E",
  config: "#D4760A",
  tools: "#C0392B",
  memory: "#8E44AD",
  extensions: "#16A085",
  apps: "#E74C3C",
  ui: "#3498DB",
  cron: "#F39C12",
  routing: "#1ABC9C",
  security: "#E67E22",
  cli: "#9B59B6",
  infra: "#95A5A6",
};

const data = {
  name: "OpenClaw",
  desc: "Personal AI Assistant Platform — 196K★ GitHub, 10,729 commits, MIT License",
  color: COLORS.root,
  children: [
    {
      name: "🌐 Gateway (Control Plane)",
      desc: "Single WebSocket server process — ws://127.0.0.1:18789. Central hub that orchestrates ALL components.",
      color: COLORS.gateway,
      children: [
        {
          name: "server.impl.ts",
          desc: "startGatewayServer() — binds port, creates WS+HTTP server, loads plugins, starts cron, initializes channels",
          color: COLORS.gateway,
          children: [
            { name: "WebSocket Server", desc: "ws library — multiplexed JSON frame protocol for operators, nodes, web clients", color: COLORS.gateway },
            { name: "HTTP Surface", desc: "POST /tools/invoke, health checks, Control UI SPA serving, webhook ingress, Canvas/A2UI", color: COLORS.gateway },
            { name: "Hot Reload", desc: "chokidar watches openclaw.json — modes: hybrid (default), hot, restart, off", color: COLORS.gateway },
          ],
        },
        {
          name: "Protocol (Wire Format)",
          desc: "TypeBox-validated JSON frames with mandatory 'type' field. Three frame types.",
          color: COLORS.gateway,
          children: [
            { name: "Request Frame", desc: "{ type:'req', id, method, params } — client→gateway RPC calls", color: COLORS.gateway },
            { name: "Response Frame", desc: "{ type:'res', id, ok, payload } — gateway→client RPC results", color: COLORS.gateway },
            { name: "Event Frame", desc: "{ type:'event', id, event, payload } — gateway→client push notifications (chat, status, cron)", color: COLORS.gateway },
          ],
        },
        {
          name: "RPC Methods",
          desc: "Organized by domain into handler modules. Method naming: {domain}.{action}",
          color: COLORS.gateway,
          children: [
            { name: "agent.*", desc: "agent.run, agent.stop, agent.status — trigger/manage agent turns", color: COLORS.gateway },
            { name: "sessions.*", desc: "sessions.list, sessions.history, sessions.send, sessions.reset, sessions.delete", color: COLORS.gateway },
            { name: "config.*", desc: "config.get, config.set, config.apply — read/write openclaw.json fields", color: COLORS.gateway },
            { name: "channels.*", desc: "channels.status, channels.probe — health and connectivity checks", color: COLORS.gateway },
            { name: "cron.*", desc: "cron.list, cron.add, cron.update, cron.remove, cron.run — scheduler management", color: COLORS.gateway },
            { name: "nodes.*", desc: "node.invoke, node.list, node.pair — companion device management", color: COLORS.gateway },
            { name: "memory.*", desc: "memory.search, memory.index, memory.status — memory system control", color: COLORS.gateway },
            { name: "models.*", desc: "models.list, models.resolve — provider and model registry queries", color: COLORS.gateway },
          ],
        },
        {
          name: "Auth & Scopes",
          desc: "connect.hello handshake — clients identify as operator/node/webchat role",
          color: COLORS.security,
          children: [
            { name: "operator.admin", desc: "Full config access, system commands, gateway restart", color: COLORS.security },
            { name: "operator.write", desc: "Agent runs, session management, cron CRUD", color: COLORS.security },
            { name: "operator.read", desc: "Status queries, logs, read-only operations", color: COLORS.security },
            { name: "operator.approvals", desc: "Exec approval grant/deny for elevated tool calls", color: COLORS.security },
            { name: "Node Auth", desc: "Device pairing + JWT token — keypair-based authentication", color: COLORS.security },
          ],
        },
        {
          name: "Daemon Management",
          desc: "Gateway runs as background service — launchd (macOS), systemd (Linux), or interactive",
          color: COLORS.gateway,
          children: [
            { name: "daemon-install.ts", desc: "Installs LaunchAgent/systemd user service via 'openclaw onboard --install-daemon'", color: COLORS.gateway },
            { name: "Graceful Shutdown", desc: "SIGTERM handler — drains active sessions, closes WS connections, persists state", color: COLORS.gateway },
          ],
        },
      ],
    },
    {
      name: "🤖 Agent System",
      desc: "The AI brain. Processes messages through a multi-stage pipeline to produce replies.",
      color: COLORS.agents,
      children: [
        {
          name: "Agent Runtime",
          desc: "Built on @mariozechner/pi-coding-agent SDK (Pi Agent Core). Two execution paths.",
          color: COLORS.agents,
          children: [
            {
              name: "Embedded Runner (primary)",
              desc: "runEmbeddedPiAgent() — in-process engine for all standard providers",
              color: COLORS.agents,
              children: [
                { name: "run.ts", desc: "Outer loop — auth profile iteration, compaction retries, fallover across profiles", color: COLORS.agents },
                { name: "attempt.ts", desc: "Single attempt — session lock acquisition, model call, tool loop, streaming", color: COLORS.agents },
                { name: "compact.ts", desc: "compactEmbeddedPiSessionDirect — summarizes old messages when context overflows", color: COLORS.agents },
                { name: "system-prompt.ts", desc: "buildEmbeddedSystemPrompt → buildAgentSystemPrompt with all runtime params", color: COLORS.agents },
                { name: "model.ts", desc: "resolveModel() — looks up model definition from openclaw-models.json registry", color: COLORS.agents },
              ],
            },
            {
              name: "CLI Runner (secondary)",
              desc: "runCliAgent() — delegates to external CLI tools (Claude Code, Codex CLI, Gemini CLI)",
              color: COLORS.agents,
            },
          ],
        },
        {
          name: "Execution Pipeline (4 layers)",
          desc: "Message passes through 4 named function layers before reaching the model API",
          color: COLORS.agents,
          children: [
            { name: "1. agent-runner.ts", desc: "Entry — resolves agent, session, model. Manages global+per-session lanes.", color: COLORS.agents },
            { name: "2. agent-runner-execution.ts", desc: "Retry loop — handles failover, compaction, role-ordering conflicts, block-reply pipeline", color: COLORS.agents },
            { name: "3. run.ts", desc: "Auth profile iteration — tries profiles in priority order, marks failures, cooldowns", color: COLORS.agents },
            { name: "4. attempt.ts", desc: "Single model call — session init, prompt build, tool execution loop, streaming", color: COLORS.agents },
          ],
        },
        {
          name: "System Prompt Assembly",
          desc: "Custom prompt rebuilt every turn. ~9K-10K tokens before conversation starts.",
          color: COLORS.agents,
          children: [
            { name: "§ Tooling", desc: "Current tool list + short descriptions (auto-generated from tool registry)", color: COLORS.agents },
            { name: "§ Safety", desc: "Short guardrail reminder — no power-seeking, no bypassing oversight", color: COLORS.agents },
            { name: "§ Skills", desc: "Compact XML list (<available_skills>) — name, description, location path per skill", color: COLORS.agents },
            { name: "§ Workspace", desc: "Working directory path (agents.defaults.workspace)", color: COLORS.agents },
            { name: "§ Documentation", desc: "Local path to OpenClaw docs — tells model when to read them", color: COLORS.agents },
            { name: "§ Sandbox", desc: "When enabled: sandboxed runtime info, paths, elevated exec availability", color: COLORS.agents },
            { name: "§ Date/Time", desc: "Timezone only (no dynamic clock for cache stability). Use session_status for current time.", color: COLORS.agents },
            { name: "§ Messaging", desc: "Channel capabilities, reply tags, silent replies, authorized senders", color: COLORS.agents },
            { name: "§ Heartbeats", desc: "Proactive wake scheduling — model can request next check-in time", color: COLORS.agents },
            { name: "§ Runtime Info", desc: "Host OS, model name, channel name, node capabilities", color: COLORS.agents },
          ],
        },
        {
          name: "Bootstrap Files (injected every turn)",
          desc: "Workspace files auto-injected into context window. Keep concise — costs tokens!",
          color: COLORS.agents,
          children: [
            { name: "SOUL.md", desc: "Agent personality/character — second-person instructions defining identity", color: COLORS.agents },
            { name: "AGENTS.md", desc: "Operating rules — memory management, group chat behavior, heartbeat config", color: COLORS.agents },
            { name: "TOOLS.md", desc: "Agent-maintained tool capabilities reference", color: COLORS.agents },
            { name: "USER.md", desc: "User preferences and profile information", color: COLORS.agents },
            { name: "MEMORY.md", desc: "Curated long-term knowledge — grows over time, costs tokens every turn", color: COLORS.agents },
            { name: "memory/*.md", desc: "Daily memory files — NOT auto-injected. Accessed via memory_search/memory_get tools.", color: COLORS.agents },
          ],
        },
        {
          name: "Agent Isolation",
          desc: "Multiple agents run in single gateway. Each has own workspace, sessions, tools, model.",
          color: COLORS.agents,
          children: [
            { name: "agent-scope.ts", desc: "resolveSessionAgentIds() — maps session key + config → { defaultAgentId, sessionAgentId }", color: COLORS.agents },
            { name: "Bindings", desc: "Route inbound messages to agents by matching channel/accountId/chatType/peer", color: COLORS.agents },
            { name: "defaults + overrides", desc: "agents.defaults (baseline) → agents.list[] (per-agent overrides for model, workspace, tools)", color: COLORS.agents },
          ],
        },
        {
          name: "Subagents",
          desc: "sessions_spawn tool — spawn child agents with isolated or main session targeting",
          color: COLORS.agents,
          children: [
            { name: "Runtime modes", desc: "subagent (in-process) or acp (Agent Client Protocol)", color: COLORS.agents },
            { name: "Execution modes", desc: "run (single task) or session (persistent conversation)", color: COLORS.agents },
            { name: "Prompt mode", desc: "Subagents use 'minimal' prompt — omits heartbeats, memory recall, messaging sections", color: COLORS.agents },
          ],
        },
      ],
    },
    {
      name: "📡 Channels (22+ platforms)",
      desc: "Messaging adapters. Each handles auth, inbound parsing, access control, outbound formatting.",
      color: COLORS.channels,
      children: [
        {
          name: "Core Channels (src/)",
          desc: "Built into main codebase — src/telegram, src/discord, src/slack, etc.",
          color: COLORS.channels,
          children: [
            { name: "WhatsApp", desc: "Baileys library — QR login, multi-device, message history sync", color: COLORS.channels },
            { name: "Telegram", desc: "grammY framework — bot commands, inline queries, media groups, reactions", color: COLORS.channels },
            { name: "Discord", desc: "discord.js — slash commands, threads, voice (opus), presence, guild management", color: COLORS.channels },
            { name: "Slack", desc: "Bolt framework — app mentions, threads, blocks, reactions, file uploads", color: COLORS.channels },
            { name: "Signal", desc: "signal-cli bridge — encrypted messaging, group support", color: COLORS.channels },
            { name: "iMessage", desc: "Legacy imsg bridge + BlueBubbles (recommended) — requires macOS", color: COLORS.channels },
            { name: "Google Chat", desc: "Chat API — spaces, threads, cards", color: COLORS.channels },
            { name: "WebChat", desc: "Built-in browser chat UI — served by gateway HTTP surface", color: COLORS.channels },
            { name: "IRC", desc: "IRC protocol — KISS security principles", color: COLORS.channels },
          ],
        },
        {
          name: "Extension Channels",
          desc: "Plugin packages in extensions/ — loaded dynamically at gateway boot",
          color: COLORS.extensions,
          children: [
            { name: "Microsoft Teams", desc: "extensions/msteams — Bot Framework + Graph API", color: COLORS.extensions },
            { name: "Matrix", desc: "extensions/matrix — matrix-bot-sdk, E2E encryption", color: COLORS.extensions },
            { name: "Zalo", desc: "extensions/zalo — Vietnamese messaging platform (OA + Personal)", color: COLORS.extensions },
            { name: "Nostr", desc: "extensions/nostr — decentralized protocol", color: COLORS.extensions },
            { name: "Feishu/Lark", desc: "Community: @m1heng-clawd/feishu — Stream mode, AI cards, docs/wiki/bitable tools", color: COLORS.extensions },
            { name: "LINE / Mattermost / Nextcloud", desc: "Community plugins — various messaging platforms", color: COLORS.extensions },
            { name: "Twitch / Synology Chat / Tlon", desc: "Additional community channel adapters", color: COLORS.extensions },
          ],
        },
        {
          name: "Channel Infrastructure",
          desc: "Shared logic across all channels",
          color: COLORS.routing,
          children: [
            { name: "Envelope Normalization", desc: "channels/envelope.ts — platform-specific → standard InboundEnvelope", color: COLORS.routing },
            { name: "Access Control", desc: "routing/access-control.ts — allowFrom lists + DM pairing checks", color: COLORS.routing },
            { name: "DM Policy", desc: "pairing (default), allowlist, or open — per channel configurable", color: COLORS.routing },
            { name: "Outbound Formatting", desc: "channels/formatting.ts — standard → platform-specific (markdown, chunking, media)", color: COLORS.routing },
            { name: "Group Routing", desc: "Mention gating, reply tags, per-channel chunking limits", color: COLORS.routing },
          ],
        },
      ],
    },
    {
      name: "🔧 Tools System",
      desc: "Functions the agent can call. Two origins: Pi Agent Core codingTools + OpenClaw-native tools.",
      color: COLORS.tools,
      children: [
        {
          name: "Core Tools (Pi Agent Core)",
          desc: "Filesystem tools from @mariozechner/pi-coding-agent — some replaced by OpenClaw variants",
          color: COLORS.tools,
          children: [
            { name: "read", desc: "Read file contents — workspace-only guard when configured", color: COLORS.tools },
            { name: "write", desc: "Write file contents — sandboxed variant routes through sandboxFsBridge", color: COLORS.tools },
            { name: "edit", desc: "Patch file — surgical text replacement within files", color: COLORS.tools },
            { name: "apply_patch", desc: "Apply unified diff — only when allowWorkspaceWrites (sandbox access != 'ro')", color: COLORS.tools },
            { name: "exec", desc: "Shell execution — configurable timeout, background mode, yield support", color: COLORS.tools },
            { name: "process", desc: "Manage background exec sessions — list, poll, stop running jobs", color: COLORS.tools },
          ],
        },
        {
          name: "OpenClaw-Native Tools",
          desc: "createOpenClawTools() in src/agents/openclaw-tools.ts",
          color: COLORS.tools,
          children: [
            { name: "sessions_*", desc: "sessions_list, sessions_history, sessions_send, sessions_spawn — multi-session management", color: COLORS.tools },
            { name: "cron", desc: "Create/manage scheduled jobs — requires owner sender", color: COLORS.tools },
            { name: "gateway", desc: "Gateway control — status, restart. Requires owner sender.", color: COLORS.tools },
            { name: "image", desc: "Generate/describe images via model providers", color: COLORS.tools },
            { name: "tts", desc: "Text-to-speech via ElevenLabs — excluded when messageProvider=voice", color: COLORS.tools },
            { name: "memory_search", desc: "Hybrid search over workspace markdown + session transcripts", color: COLORS.tools },
            { name: "memory_get", desc: "Retrieve specific memory entries by path/date", color: COLORS.tools },
            { name: "agents_list", desc: "List available agents and their configurations", color: COLORS.tools },
            { name: "session_status", desc: "Current session info: timestamp, model, token usage, compaction count", color: COLORS.tools },
            { name: "whatsapp_login", desc: "Trigger WhatsApp QR pairing — requires owner sender", color: COLORS.tools },
          ],
        },
        {
          name: "Channel Plugin Tools",
          desc: "listChannelAgentTools() — channels contribute their own tools",
          color: COLORS.tools,
          children: [
            { name: "Discord actions", desc: "Send reactions, manage threads, pin messages", color: COLORS.tools },
            { name: "Slack actions", desc: "Post to channels, manage threads, add reactions", color: COLORS.tools },
          ],
        },
        {
          name: "Tool Policy Pipeline",
          desc: "applyToolPolicyPipeline() — cascading filters, each stage can only narrow (never expand)",
          color: COLORS.security,
          children: [
            { name: "1. Profile", desc: "tools.profile — base allowlist (full, safe, minimal, none)", color: COLORS.security },
            { name: "2. Global allow/deny", desc: "tools.allow + tools.deny — explicit global overrides", color: COLORS.security },
            { name: "3. Provider policy", desc: "tools.byProvider[key] — per-model restrictions", color: COLORS.security },
            { name: "4. Agent policy", desc: "agents.list[i].tools — per-agent overrides", color: COLORS.security },
            { name: "5. Sandbox policy", desc: "sandbox.tools.allow — when sandboxed, additional filtering", color: COLORS.security },
            { name: "6. Owner-only gate", desc: "cron, gateway, whatsapp_login require owner sender", color: COLORS.security },
            { name: "Tool Groups", desc: "group:runtime, group:fs, group:sessions, group:openclaw — expand to multiple tools", color: COLORS.security },
            { name: "Loop Detection", desc: "tools.loopDetection — configurable detectors to prevent infinite tool call loops", color: COLORS.security },
          ],
        },
        {
          name: "Sandboxing (Docker)",
          desc: "Optional Docker isolation for tool execution — configured per agent",
          color: COLORS.security,
          children: [
            { name: "sandbox.mode", desc: "off, non-main (group/channel sessions), all (every session)", color: COLORS.security },
            { name: "Container lifecycle", desc: "session (per-session), agent (per-agent, reused), shared (single global)", color: COLORS.security },
            { name: "Workspace access", desc: "none (container-local), ro (read-only mount), rw (read-write mount)", color: COLORS.security },
            { name: "Elevated exec", desc: "Host shell access from sandbox — /elevated on|off|ask|full", color: COLORS.security },
            { name: "Dockerfiles", desc: "Dockerfile.sandbox, Dockerfile.sandbox-browser, Dockerfile.sandbox-common", color: COLORS.security },
          ],
        },
      ],
    },
    {
      name: "📝 Session Management",
      desc: "Conversation persistence, history storage, state tracking across agent runs.",
      color: COLORS.config,
      children: [
        {
          name: "Session Keys",
          desc: "Hierarchical string identifiers encoding routing context: agent:channel:scope:peer",
          color: COLORS.config,
          children: [
            { name: "main scope", desc: "agent:main:*:main — single shared session across all channels", color: COLORS.config },
            { name: "per-peer", desc: "agent:main:*:dm:<peer> — separate session per user (channel-agnostic)", color: COLORS.config },
            { name: "per-channel-peer", desc: "agent:main:<channel>:dm:<peer> — per user per channel", color: COLORS.config },
            { name: "subagent", desc: "agent:parent:subagent:ID — parent session key embedded", color: COLORS.config },
            { name: "cron", desc: "agent:main:cron:ID — isolated cron job sessions", color: COLORS.config },
          ],
        },
        {
          name: "Storage",
          desc: "JSONL transcripts + JSON metadata store",
          color: COLORS.config,
          children: [
            { name: "sessions.json", desc: "Metadata store — maps session keys to entries (model, usage, last message, compaction count)", color: COLORS.config },
            { name: "*.jsonl", desc: "One file per session — one JSON object per line (role: user/assistant, content, tool calls)", color: COLORS.config },
            { name: "Write locks", desc: "session-write-lock.ts — prevents concurrent modifications, serialized writes per session", color: COLORS.config },
          ],
        },
        {
          name: "Compaction",
          desc: "When context exceeds ~200K token limit, oldest messages are summarized (~2K tokens)",
          color: COLORS.config,
          children: [
            { name: "Detection", desc: "isContextOverflowError, isLikelyContextOverflowError helpers", color: COLORS.config },
            { name: "Process", desc: "Dedicated summarization turn using same model → replace originals with summary", color: COLORS.config },
            { name: "Post-compaction", desc: "readPostCompactionContext — workspace snapshot injected for next turn", color: COLORS.config },
            { name: "Failure recovery", desc: "If compaction fails: new sessionId generated, transcript discarded, restart from scratch", color: COLORS.config },
          ],
        },
        {
          name: "Queue Modes",
          desc: "How new messages are handled when agent is already running",
          color: COLORS.config,
          children: [
            { name: "replace", desc: "Kill current run, restart with new message", color: COLORS.config },
            { name: "steer", desc: "Inject message into running context mid-turn", color: COLORS.config },
            { name: "ignore", desc: "Drop the new message while agent is busy", color: COLORS.config },
          ],
        },
      ],
    },
    {
      name: "🧠 Memory System",
      desc: "Hybrid search (vector similarity 70% + BM25 keyword 30%) over workspace and transcripts.",
      color: COLORS.memory,
      children: [
        {
          name: "MemoryIndexManager",
          desc: "Central class — manages indexing, search, provider lifecycle. src/memory/manager.ts",
          color: COLORS.memory,
          children: [
            { name: "Chunking", desc: "Markdown → ~400 token chunks with 80-token overlap", color: COLORS.memory },
            { name: "Embedding Cache", desc: "Content-hash keyed — prevents redundant API calls", color: COLORS.memory },
            { name: "sqlite-vec", desc: "Native vector search acceleration extension (when available)", color: COLORS.memory },
          ],
        },
        {
          name: "Search Pipeline",
          desc: "Query → embed → vector search → keyword search → merge & rerank → format",
          color: COLORS.memory,
          children: [
            { name: "Vector Search", desc: "Top-K by cosine similarity — 70% weight in final score", color: COLORS.memory },
            { name: "BM25 Keyword", desc: "Top-K by BM25 rank — 30% weight in final score", color: COLORS.memory },
            { name: "Hybrid Merge", desc: "hybrid.ts — combine scores with configurable weights", color: COLORS.memory },
            { name: "Snippet Formatting", desc: "Truncate to ~700 chars, include line ranges for context", color: COLORS.memory },
          ],
        },
        {
          name: "Embedding Providers",
          desc: "Pluggable provider system for generating embeddings",
          color: COLORS.memory,
          children: [
            { name: "OpenAI", desc: "text-embedding-3-small/large", color: COLORS.memory },
            { name: "Anthropic", desc: "Via Voyage AI embeddings", color: COLORS.memory },
            { name: "Ollama", desc: "Local embedding models (nomic-embed-text, etc.)", color: COLORS.memory },
            { name: "Mistral", desc: "mistral-embed model", color: COLORS.memory },
          ],
        },
        {
          name: "Memory Tools",
          desc: "Agent-accessible memory operations",
          color: COLORS.memory,
          children: [
            { name: "memory_search", desc: "Hybrid search query → returns ranked snippets", color: COLORS.memory },
            { name: "memory_get", desc: "Retrieve specific memory file by path", color: COLORS.memory },
            { name: "memory_add", desc: "Write to daily memory log (memory/YYYY-MM-DD.md)", color: COLORS.memory },
          ],
        },
      ],
    },
    {
      name: "⚙️ Configuration System",
      desc: "~/.openclaw/openclaw.json (JSON5) — Zod schema validation with plugin schema merging",
      color: COLORS.config,
      children: [
        {
          name: "Schema Pipeline",
          desc: "buildConfigSchema() — merges base + plugin + channel schemas at boot",
          color: COLORS.config,
          children: [
            { name: "zod-schema.ts", desc: "Core Zod schema — all gateway, agent, channel, tool fields (95-632 lines)", color: COLORS.config },
            { name: "Plugin schemas", desc: "Each plugin declares TypeBox schema — merged into global validation", color: COLORS.config },
            { name: "JSON Schema export", desc: "toJSONSchema() — converts Zod to draft-07 for IDE autocomplete", color: COLORS.config },
          ],
        },
        {
          name: "Key Config Sections",
          desc: "Top-level fields in openclaw.json",
          color: COLORS.config,
          children: [
            { name: "gateway.*", desc: "port, bind, auth, reload.mode, dashboard, logging", color: COLORS.config },
            { name: "agents.*", desc: "defaults (model, workspace, compaction), list[] (per-agent overrides)", color: COLORS.config },
            { name: "channels.*", desc: "Per-channel: token, dmPolicy, allowFrom, enabled, formatting options", color: COLORS.config },
            { name: "tools.*", desc: "profile, allow/deny lists, byProvider, fs.workspaceOnly, loopDetection, elevated", color: COLORS.config },
            { name: "memory.*", desc: "enabled, provider, embeddings config, search weights", color: COLORS.config },
            { name: "cron.*", desc: "enabled, store path, runLog limits (maxBytes, keepLines)", color: COLORS.config },
            { name: "plugins.*", desc: "entries (enable/disable), slots (memory provider), load.paths (custom dirs)", color: COLORS.config },
            { name: "bindings[]", desc: "Route messages to agents by matching channel/accountId/chatType/peer", color: COLORS.config },
          ],
        },
        {
          name: "Precedence",
          desc: "Environment variables > config file > system defaults",
          color: COLORS.config,
        },
        {
          name: "Paths",
          desc: "src/config/paths.ts — resolves all state/workspace/session directories",
          color: COLORS.config,
          children: [
            { name: "~/.openclaw/", desc: "State root (OPENCLAW_STATE_DIR override)", color: COLORS.config },
            { name: "~/.openclaw/openclaw.json", desc: "Config file (OPENCLAW_CONFIG_PATH override)", color: COLORS.config },
            { name: "~/.openclaw/workspace/", desc: "Default agent workspace", color: COLORS.config },
            { name: "~/.openclaw/sessions/", desc: "Session transcripts (JSONL files)", color: COLORS.config },
            { name: "~/.openclaw/cron/", desc: "jobs.json + runs/*.jsonl", color: COLORS.config },
          ],
        },
      ],
    },
    {
      name: "⏰ Cron Service",
      desc: "Built-in scheduler — time-triggered jobs, one-shot + recurring, isolation modes.",
      color: COLORS.cron,
      children: [
        { name: "CronJob record", desc: "id, schedule (cron expression + timezone), payload, sessionTarget, delivery config", color: COLORS.cron },
        { name: "staggerMs", desc: "Deterministic per-job offset (SHA-256 of ID) — avoids thundering herd", color: COLORS.cron },
        {
          name: "Execution modes",
          desc: "Two ways cron jobs run",
          color: COLORS.cron,
          children: [
            { name: "inject", desc: "System event injected into main session — agent handles it in normal flow", color: COLORS.cron },
            { name: "isolated", desc: "Full standalone agent turn — runCronIsolatedAgentTurn with own session", color: COLORS.cron },
          ],
        },
        { name: "Delivery", desc: "Channel delivery (telegram, discord, 'last') + webhook HTTP POST (SSRF-protected, 10s timeout)", color: COLORS.cron },
        { name: "Run Log", desc: "Per-job JSONL log — pruned at 2MB/2000 lines by default", color: COLORS.cron },
        { name: "Async Mutex", desc: "Mutations serialized through locked.ts — prevents race conditions in job store", color: COLORS.cron },
      ],
    },
    {
      name: "🧩 Skills System",
      desc: "Self-contained capability bundles — SKILL.md + optional scripts/deps. Lazy-loaded on demand.",
      color: COLORS.extensions,
      children: [
        {
          name: "Three-Stage Lifecycle",
          desc: "Load → inject → on-demand",
          color: COLORS.extensions,
          children: [
            { name: "1. Load time", desc: "Scan workspace/skills/ for SKILL.md files, check gating (requirements, bins, model caps)", color: COLORS.extensions },
            { name: "2. Prompt injection", desc: "Compact XML metadata only (~24 tokens per skill) in system prompt", color: COLORS.extensions },
            { name: "3. On-demand", desc: "Agent reads full SKILL.md via 'read' tool only when task matches", color: COLORS.extensions },
          ],
        },
        {
          name: "Skill Locations",
          desc: "Precedence: workspace/skills/ → ~/.openclaw/skills/ → bundled skills/",
          color: COLORS.extensions,
          children: [
            { name: "Bundled", desc: "skills/ directory in repo — voice-call, lobster, prose, etc.", color: COLORS.extensions },
            { name: "User-defined", desc: "workspace/skills/ — custom skills per agent", color: COLORS.extensions },
            { name: "ClawHub", desc: "clawhub.com — community skill registry for discovery and sharing", color: COLORS.extensions },
          ],
        },
        {
          name: "SKILL.md Format",
          desc: "Markdown with YAML frontmatter — name, description, requires (env, bins), dependencies",
          color: COLORS.extensions,
        },
      ],
    },
    {
      name: "🔌 Plugin/Extension System",
      desc: "npm-packaged extensions in extensions/ directory. Auto-discovered at gateway boot.",
      color: COLORS.extensions,
      children: [
        {
          name: "Plugin Loading",
          desc: "src/plugins/loader.ts — scan, validate, register",
          color: COLORS.extensions,
          children: [
            { name: "Discovery", desc: "Scan node_modules/@openclaw/* + plugins.load.paths for openclaw.* metadata", color: COLORS.extensions },
            { name: "Validation", desc: "Check openclaw.channel or openclaw.tool metadata in package.json", color: COLORS.extensions },
            { name: "Registration", desc: "Register into CHANNEL_REGISTRY, TOOL_REGISTRY, or MEMORY_REGISTRY", color: COLORS.extensions },
          ],
        },
        {
          name: "Bundled Extensions",
          desc: "Ship with OpenClaw in extensions/ — enabled via config",
          color: COLORS.extensions,
          children: [
            { name: "discord/", desc: "Discord channel plugin (discord.js)", color: COLORS.extensions },
            { name: "telegram/", desc: "Telegram channel plugin (grammY)", color: COLORS.extensions },
            { name: "slack/", desc: "Slack channel plugin (Bolt)", color: COLORS.extensions },
            { name: "signal/", desc: "Signal channel plugin (signal-cli)", color: COLORS.extensions },
            { name: "googlechat/", desc: "Google Chat channel plugin", color: COLORS.extensions },
            { name: "memory-core/", desc: "Core memory plugin — embeddings, indexing, search", color: COLORS.extensions },
            { name: "voice-call/", desc: "Voice calling via Twilio", color: COLORS.extensions },
            { name: "msteams/", desc: "Microsoft Teams channel", color: COLORS.extensions },
            { name: "matrix/", desc: "Matrix chat protocol", color: COLORS.extensions },
            { name: "zalo/ + zalouser/", desc: "Zalo messaging (Vietnam) — OA + Personal modes", color: COLORS.extensions },
            { name: "nostr/", desc: "Nostr decentralized protocol", color: COLORS.extensions },
          ],
        },
        {
          name: "Plugin Hooks",
          desc: "Lifecycle intercepts for deep pipeline integration",
          color: COLORS.extensions,
          children: [
            { name: "before_tool_call", desc: "Intercept/modify tool calls before execution", color: COLORS.extensions },
            { name: "after_tool_call", desc: "Process tool results after execution", color: COLORS.extensions },
            { name: "agent:bootstrap", desc: "Mutate/replace injected bootstrap files (e.g., swap SOUL.md)", color: COLORS.extensions },
            { name: "before_model_resolve", desc: "Dynamic model routing before API call", color: COLORS.extensions },
            { name: "agent_start / agent_end", desc: "Session lifecycle hooks", color: COLORS.extensions },
          ],
        },
      ],
    },
    {
      name: "💻 Companion Apps (Native)",
      desc: "apps/ directory — macOS (Swift), iOS (Swift), Android (Kotlin)",
      color: COLORS.apps,
      children: [
        {
          name: "macOS App",
          desc: "Menu bar control plane + Voice Wake + Talk Mode + WebChat + debug tools",
          color: COLORS.apps,
          children: [
            { name: "LaunchAgent", desc: "Auto-starts gateway on login, keeps running in background", color: COLORS.apps },
            { name: "Voice Wake", desc: "Always-on wake word detection via ElevenLabs", color: COLORS.apps },
            { name: "Talk Mode", desc: "Continuous voice overlay — speech-to-text → agent → text-to-speech", color: COLORS.apps },
            { name: "Canvas/A2UI", desc: "Agent-driven visual workspace rendered in native WebView", color: COLORS.apps },
            { name: "Node Host", desc: "Exposes system.run/notify + canvas/camera to gateway", color: COLORS.apps },
          ],
        },
        {
          name: "iOS App",
          desc: "Canvas, Voice Wake, Talk Mode, camera, screen recording, Bonjour + device pairing",
          color: COLORS.apps,
        },
        {
          name: "Android App",
          desc: "Connect tab, chat sessions, voice, Canvas, camera/screen recording, device commands",
          color: COLORS.apps,
          children: [
            { name: "Device Commands", desc: "notifications, location, SMS, photos, contacts, calendar, motion, app update", color: COLORS.apps },
          ],
        },
      ],
    },
    {
      name: "🖥 Control UI",
      desc: "ui/ directory — browser-based SPA built with LitElement (web components) + Vite",
      color: COLORS.ui,
      children: [
        { name: "Dashboard", desc: "Chat interface, session management, real-time status", color: COLORS.ui },
        { name: "Cron Manager", desc: "Create/edit/delete scheduled jobs, view run logs", color: COLORS.ui },
        { name: "Config Editor", desc: "Visual config editing with tag-aware settings filtering", color: COLORS.ui },
        { name: "Node Status", desc: "Connected companion devices, capabilities, pairing", color: COLORS.ui },
        { name: "Agent Manager", desc: "Multi-agent overview, workspace browsing, model selection", color: COLORS.ui },
        { name: "WebSocket Bridge", desc: "Same-origin WS bridge from browser → gateway using RPC protocol", color: COLORS.ui },
      ],
    },
    {
      name: "📋 CLI (openclaw)",
      desc: "Commander.js framework — primary operator interface. src/cli/ + src/commands/",
      color: COLORS.cli,
      children: [
        { name: "openclaw onboard", desc: "Setup wizard — auth, channels, workspace, skills, daemon install", color: COLORS.cli },
        { name: "openclaw gateway", desc: "start/stop/status/restart — manage gateway process", color: COLORS.cli },
        { name: "openclaw agent", desc: "--message, --to, --thinking, --model — send messages to agents", color: COLORS.cli },
        { name: "openclaw channels", desc: "status --probe — check channel health and connectivity", color: COLORS.cli },
        { name: "openclaw config", desc: "get/set/apply — read/write configuration fields", color: COLORS.cli },
        { name: "openclaw doctor", desc: "--fix — diagnose and auto-repair common issues, migrate legacy config", color: COLORS.cli },
        { name: "openclaw memory", desc: "index/search/status — manage memory system", color: COLORS.cli },
        { name: "openclaw pairing", desc: "approve/reject/list — manage DM pairing requests", color: COLORS.cli },
        { name: "openclaw update", desc: "--channel stable|beta|dev — switch release channels", color: COLORS.cli },
        { name: "openclaw security audit", desc: "--deep — comprehensive security scan of config and exposure", color: COLORS.cli },
        { name: "openclaw plugins", desc: "install/list — manage extension plugins", color: COLORS.cli },
      ],
    },
    {
      name: "📂 Commands & Directives",
      desc: "In-chat controls processed BEFORE reaching the AI model. /prefix or :inline format.",
      color: COLORS.cli,
      children: [
        {
          name: "Slash Commands (/)",
          desc: "Text commands + native platform commands (Discord slash, Telegram bot commands)",
          color: COLORS.cli,
          children: [
            { name: "/status", desc: "Session info, model, token usage, uptime", color: COLORS.cli },
            { name: "/stop", desc: "Abort current agent execution + clear queue", color: COLORS.cli },
            { name: "/new", desc: "Reset current session (clear history)", color: COLORS.cli },
            { name: "/model", desc: "Switch model for current session (with optional @profile lock)", color: COLORS.cli },
            { name: "/context", desc: "list/detail — inspect system prompt, bootstrap files, token usage per file", color: COLORS.cli },
            { name: "/help", desc: "List available commands for current channel", color: COLORS.cli },
            { name: "/elevated", desc: "on|off|ask|full — toggle host shell access from sandbox", color: COLORS.cli },
            { name: "/skill", desc: "Invoke specific skill with arguments", color: COLORS.cli },
            { name: "! <cmd>", desc: "Direct host shell execution (requires commands.bash=true)", color: COLORS.cli },
          ],
        },
        {
          name: "Directives (:)",
          desc: "Inline modifiers that change model behavior for current message/session",
          color: COLORS.cli,
          children: [
            { name: ":think", desc: "Set thinking level — off, minimal, low, medium, high, xhigh", color: COLORS.cli },
            { name: ":model", desc: "Override model for this message only", color: COLORS.cli },
            { name: ":verbose", desc: "on|full|off — show tool execution details", color: COLORS.cli },
            { name: ":raw", desc: "Send message without system prompt processing", color: COLORS.cli },
          ],
        },
      ],
    },
    {
      name: "🔐 Security",
      desc: "Defense in depth — loopback-only default, auth required, sandboxing, pairing.",
      color: COLORS.security,
      children: [
        { name: "Loopback Binding", desc: "Gateway binds 127.0.0.1 by default — never exposed to internet", color: COLORS.security },
        { name: "Token/Password Auth", desc: "Required for all WebSocket connections (shared token or password)", color: COLORS.security },
        { name: "DM Pairing", desc: "Unknown senders get pairing code — must be approved before processing", color: COLORS.security },
        { name: "Auth Profiles", desc: "auth-profiles.json — rotate API keys, OAuth tokens, manage cooldowns", color: COLORS.security },
        { name: "SSRF Protection", desc: "fetchWithSsrfGuard — prevents webhooks/cron from hitting internal services", color: COLORS.security },
        { name: "Secret Detection", desc: ".detect-secrets.cfg + pre-commit hooks — prevents credential leaks", color: COLORS.security },
        { name: "openclaw security audit", desc: "--deep — scans config for risky DM policies, exposed ports, weak auth", color: COLORS.security },
        { name: "Credential Redaction", desc: "CLI output redacts sensitive config values before printing", color: COLORS.security },
      ],
    },
    {
      name: "🏗 Infrastructure / Build",
      desc: "TypeScript project — pnpm workspace, tsdown bundler, Vitest testing, CI/CD",
      color: COLORS.infra,
      children: [
        { name: "Runtime", desc: "Node.js ≥22, TypeScript, Bun optional for dev", color: COLORS.infra },
        { name: "Build", desc: "tsdown → dist/ (production bundle), pnpm build", color: COLORS.infra },
        { name: "Workspace", desc: "pnpm-workspace.yaml — monorepo with packages/, extensions/, apps/, ui/", color: COLORS.infra },
        { name: "Testing", desc: "Vitest — unit, gateway, e2e, live, extensions configs. 5+ vitest config files.", color: COLORS.infra },
        { name: "CI/CD", desc: "GitHub Actions — ci.yml, labeler, stale issues, auto-response", color: COLORS.infra },
        { name: "Linting", desc: "oxlint + oxfmt + .swiftlint + .shellcheckrc + markdownlint", color: COLORS.infra },
        { name: "Deployment", desc: "Docker (Dockerfile), Fly.io (fly.toml), Render (render.yaml), Podman support", color: COLORS.infra },
        { name: "packages/", desc: "Shared packages — Swabble, plugin-sdk, shared types", color: COLORS.infra },
      ],
    },
  ],
};

// ─── Component ───
function TreeNode({ node, depth = 0, path = "" }) {
  const [expanded, setExpanded] = useState(depth < 2);
  const hasChildren = node.children && node.children.length > 0;
  const id = path + node.name;

  return (
    <div style={{ marginLeft: depth === 0 ? 0 : 20 }}>
      <div
        onClick={() => hasChildren && setExpanded(!expanded)}
        style={{
          display: "flex",
          alignItems: "flex-start",
          gap: 8,
          padding: "6px 10px",
          marginBottom: 2,
          borderRadius: 6,
          cursor: hasChildren ? "pointer" : "default",
          background: expanded && hasChildren ? `${node.color}10` : "transparent",
          borderLeft: `3px solid ${node.color}`,
          transition: "all 0.15s ease",
        }}
        onMouseEnter={(e) => { e.currentTarget.style.background = `${node.color}18`; }}
        onMouseLeave={(e) => { e.currentTarget.style.background = expanded && hasChildren ? `${node.color}10` : "transparent"; }}
      >
        {hasChildren ? (
          <span style={{ color: node.color, fontWeight: 700, fontSize: 14, minWidth: 16, userSelect: "none", marginTop: 1 }}>
            {expanded ? "▾" : "▸"}
          </span>
        ) : (
          <span style={{ color: "#ccc", fontSize: 10, minWidth: 16, marginTop: 5 }}>●</span>
        )}
        <div style={{ flex: 1 }}>
          <span style={{ fontWeight: 600, fontSize: depth === 0 ? 16 : 13, color: node.color }}>
            {node.name}
          </span>
          {node.desc && (
            <div style={{ fontSize: 11.5, color: "#666", marginTop: 1, lineHeight: 1.4 }}>
              {node.desc}
            </div>
          )}
        </div>
        {hasChildren && (
          <span style={{ fontSize: 10, color: "#aaa", marginTop: 3, whiteSpace: "nowrap" }}>
            {node.children.length}
          </span>
        )}
      </div>
      {expanded && hasChildren && (
        <div>
          {node.children.map((child, i) => (
            <TreeNode key={i} node={child} depth={depth + 1} path={id + "/"} />
          ))}
        </div>
      )}
    </div>
  );
}

function Stats() {
  const countNodes = (n) => 1 + (n.children || []).reduce((s, c) => s + countNodes(c), 0);
  const total = countNodes(data);
  const topLevel = data.children.length;
  return (
    <div style={{ display: "flex", gap: 16, flexWrap: "wrap", marginBottom: 16 }}>
      {[
        ["Total Nodes", total],
        ["Top-Level Systems", topLevel],
        ["GitHub Stars", "196K"],
        ["Commits", "10,729"],
        ["Channels", "22+"],
        ["Extensions", "11 bundled"],
        ["Runtime", "Node ≥22"],
        ["License", "MIT"],
      ].map(([label, value]) => (
        <div key={label} style={{ background: "#f8f9fa", borderRadius: 8, padding: "8px 14px", textAlign: "center" }}>
          <div style={{ fontSize: 18, fontWeight: 700, color: COLORS.root }}>{value}</div>
          <div style={{ fontSize: 10, color: "#888" }}>{label}</div>
        </div>
      ))}
    </div>
  );
}

export default function App() {
  const [expandAll, setExpandAll] = useState(false);
  const [key, setKey] = useState(0);

  return (
    <div style={{ fontFamily: "'Inter', -apple-system, sans-serif", maxWidth: 900, margin: "0 auto", padding: 16 }}>
      <div style={{ textAlign: "center", marginBottom: 20 }}>
        <h1 style={{ fontSize: 24, color: COLORS.root, margin: 0 }}>🦞 OpenClaw — Complete Architecture Tree</h1>
        <p style={{ color: "#666", fontSize: 13, margin: "4px 0" }}>
          Full project breakdown: every module, feature, and function relationship
        </p>
      </div>
      <Stats />
      <div style={{ display: "flex", gap: 8, marginBottom: 12 }}>
        <button
          onClick={() => { setExpandAll(true); setKey(k => k + 1); }}
          style={{ padding: "6px 14px", borderRadius: 6, border: "1px solid #ddd", background: "#f5f5f5", cursor: "pointer", fontSize: 12 }}
        >
          Expand All
        </button>
        <button
          onClick={() => { setExpandAll(false); setKey(k => k + 1); }}
          style={{ padding: "6px 14px", borderRadius: 6, border: "1px solid #ddd", background: "#f5f5f5", cursor: "pointer", fontSize: 12 }}
        >
          Collapse All
        </button>
      </div>
      <div key={key} style={{ border: "1px solid #e5e7eb", borderRadius: 10, padding: 12, background: "#fff" }}>
        <TreeNode node={{ ...data, children: data.children.map(c => expandAll ? deepExpand(c) : c) }} depth={0} />
      </div>
      <div style={{ textAlign: "center", marginTop: 12, fontSize: 11, color: "#aaa" }}>
        Click any node to expand/collapse • Numbers show child count • Based on github.com/openclaw/openclaw + DeepWiki analysis
      </div>
    </div>
  );
}

function deepExpand(node) { return node; }

