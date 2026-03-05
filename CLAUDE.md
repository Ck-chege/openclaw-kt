# CLAUDE.md

original OpenClaw url - https://github.com/openclaw/openclaw

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**kclaw** is the planning and implementation repository for rebuilding [OpenClaw](https://github.com/openclaw/openclaw) — an open-source personal AI assistant platform (originally TypeScript/Node.js) — as a Kotlin-native system using **Ktor** (server framework) and **Koog** (JetBrains' AI agent framework, v0.6.3+). This is **not a port** — it reimagines OpenClaw's architecture using Kotlin's strengths.

**Owner:** Isaac / PMetrics Company (CRKR Investment), Toronto, Ontario.

## Current State

The repository is in **active implementation** of Phase 1 (Core Foundation). Epic 1 (Protocol Module) is complete. Epic 2 (Gateway Core) is mostly complete (config hot-reload remaining). Session-store has partial implementation. The project structure is a Gradle multimodule Kotlin project (`openclaw-kt/`).

## Task Tracking

**TASKS.md** is the single source of truth for project progress. When any subtask or story is completed, **immediately update TASKS.md** by changing `- [ ]` to `- [x]` for that item. Never leave completed work unchecked. When a story's subtasks are all checked, check the story line too.

## Reference Documents

- `OpenClaw-Complete-Architecture-Tree.jsx` — Interactive React tree visualization of OpenClaw's full architecture (every module, protocol, tool, channel). This is the authoritative reference for what must be rebuilt.
- `OpenClaw-Ktor-Koog-Rebuild-Research.docx` — Feasibility study with component-by-component mapping from OpenClaw → Ktor + Koog, proposed module structure, implementation patterns, risk assessment, and a 20-week roadmap.
- `Kotlin-Best-Practices-OpenClaw-Agent-System.docx` — Kotlin idiom guide covering sealed classes for wire protocol/agent states, coroutines for structured concurrency, Flow for streaming, DSLs for config/prompts, Koog strategy graphs, type-safe tools, and multiplatform design.

## Target Architecture

Gradle multimodule project:
- `gateway-core/` — Ktor server, WebSocket wire protocol (sealed class frames: Req/Res/Event), config (HOCON), routing
- `agent-engine/` — Koog AIAgent wrappers, workspace loader, system prompt assembly DSL
- `session-store/` — JSONL persistence + Koog graph-based checkpointing, compaction
- `channels/` — Per-platform connectors (discord/, telegram/, slack/, whatsapp/, signal/)
- `skills/` — SKILL.md parser, lazy-loading skill injection, gating engine
- `tools/` — exec (ProcessBuilder), filesystem, browser (Playwright-kt)
- `plugins/` — Hook system via Koog AIAgentPipeline features
- `cron/` — Coroutine-based scheduler with kotlinx-datetime
- `orchestrator/` — Multi-agent spawning, steering, A2A protocol
- `app/` — Main application entry point

## Key Technical Decisions

- **Wire protocol:** Sealed classes (`Frame.Request`, `Frame.Response`, `Frame.Event`) with kotlinx-serialization — exhaustive `when` matching replaces TypeScript discriminated unions
- **Concurrency:** Kotlin coroutines with structured concurrency (`coroutineScope`, `supervisorScope`) replace Node.js single-threaded event loop. `Mutex` for per-session run serialization.
- **Agent loop:** Koog's graph-based `strategy` DSL (ReAct pattern built-in) replaces OpenClaw's 4-file imperative pipeline
- **Streaming:** Kotlin `Flow` with backpressure replaces event emitters
- **Config:** Type-safe Kotlin DSL (compile-time validation) replaces JSON5 + Zod runtime validation
- **Tools:** `Tool<TArgs, TResult>` with auto-generated JSON Schema from `@Serializable` data classes + `@LLMDescription` annotations
- **Session keys:** `value class SessionKey` for zero-cost type safety
- **Multiplatform:** Shared `protocol/` module compiled for JVM, iOS, Android, JS — single source of truth for all platform protocol definitions
- **Persistence:** Koog's graph-based checkpointing (Postgres provider) + JSONL compatibility layer

## Development Roadmap (Planned Phases)

1. **Core :** Ktor gateway, WebSocket protocol, Koog agent integration, workspace loader, JSONL sessions, config system
2. **Channels :** Telegram (TelegramBots SDK), Discord (JDA), Slack (Bolt SDK), channel routing
3. **Skills & Tools :** SKILL.md parser, shell exec tool, file ops, MCP integration via Koog
4. **Advanced:** Multi-agent orchestration, cron scheduler, plugin hooks, browser automation
5. **Production :** OpenTelemetry observability, security hardening, multiplatform companion apps

## Kotlin Patterns to Follow

- Use **sealed classes/interfaces** for all enum-like concepts carrying data (frames, stop reasons, tool results, channel events, queue actions, tool permissions)
- Use **value classes** for domain identifiers (SessionKey, AgentId, ModelId, TokenCount)
- Use **`supervisorScope`** for parallel sub-agents (independent failure isolation); **`coroutineScope`** for operations that must all succeed
- Use **`withTimeoutOrNull`** for agent timeouts (auto-cancels all children)
- Use **Kotlin delegation** for composable channel wrappers (e.g., rate-limited channels)
- Use **extension functions** on `Path` for workspace file loading
- Prefer **Koog's Feature pipeline** over custom event emitter patterns for hooks


