# System Prompt Guide

> Reference for implementing and maintaining `PromptAssembler.kt`.
> This is the source of truth for how the system prompt should be structured in all modes.
> When building new features that affect the prompt, update this file and `PromptAssembler.kt` together.

---

## Overview

The system prompt is assembled from three sources:
1. **Hardcoded sections** — behavioural rules, tooling, safety (owned by OpenClaw)
2. **Runtime context** — model, channel, workspace path, timezone, authorized senders
3. **Workspace files** — user-owned content: SOUL.md, AGENTS.md, USER.md, MEMORY.md, etc.

The assembly runs through `PromptAssembler.assembleSystemPrompt(ctx: PromptContext)` which
dispatches to one of three modes based on `ctx.promptMode`.

---

## Prompt Modes

| Mode | Used For | Includes |
|------|----------|---------|
| `FULL` | Main sessions, private DMs | Everything |
| `MINIMAL` | Subagents, cron, heartbeat | Tooling + Safety + workspace (no MEMORY, no Silent Replies, no Heartbeats) |
| `NONE` | Lightweight utility agents | Opener + tooling only |

---

## Full Mode — Complete Section Sequence

This is the canonical order. Every section must appear in this order.

```
[1]  OPENER
[2]  ## Tooling
[3]  ## Tool Call Style
[4]  ## Safety
[5]  <available_skills> (conditional)
[6]  ## Skills (mandatory) (conditional)
[7]  ## Memory Recall (conditional)
[8]  ## OpenClaw Self-Update (conditional)
[9]  ## Model Aliases (conditional)
[10] "If you need the current date/time, run session_status" (conditional)
[11] ## Workspace
[12] ## Documentation (conditional)
[13] ## Authorized Senders (conditional)
[14] ## Current Date & Time (conditional)
[15] ## Workspace Files (injected)   ← heading only
[16] ## Reply Tags (conditional)
[17] ## Messaging (conditional)
[18] ## Group Chat Context (conditional)
[19] ## Reactions (conditional)

--- Project Context ---

[20] # Project Context              ← workspace file contents start here
      "If SOUL.md is present, embody its persona and tone..."
      ## SOUL.md
      ## AGENTS.md
      ## TOOLS.md
      ## IDENTITY.md
      ## USER.md
      ## HEARTBEAT.md (conditional)
      ## BOOTSTRAP.md (conditional)
      ## MEMORY.md (conditional — excluded in GROUP sessions)

--- Behavioural ---

[21] ## Silent Replies
[22] ## Heartbeats
[23] ## Runtime
[24] Reasoning toggle hint
```

---

## Section Specifications

### [1] Opener

```
You are a personal assistant running inside OpenClaw.
```

**Rules:**
- This exact string. No variation.
- Never "You are a personal AI assistant." — that's generic and sets a robotic frame.
- Appears in ALL modes (FULL, MINIMAL, NONE).

---

### [2] ## Tooling

**Condition:** Only if `ctx.toolDescriptions` is non-empty.

**Format:**
```
## Tooling
Tool availability (filtered by policy):
Tool names are case-sensitive. Call tools exactly as listed.
- toolName: Tool description
- toolName2: Tool description 2
...
TOOLS.md does not control tool availability; it is user guidance for how to use external tools.
For long waits, avoid rapid poll loops: use exec with enough yieldMs or process(action=poll, timeout=<ms>).
If a task is more complex or takes longer, spawn a sub-agent. Completion is push-based: it will auto-announce when done.
```

**Notes:**
- Tool format: `- name: description` (no bold, no em-dash)
- `TOOLS.md` disclaimer is always included when tools are listed
- Long wait guidance should always follow the tool list
- ACP harness routing lines (for sessions_spawn with runtime="acp") are injected here when `acpEnabled=true`
- Add "Do not poll `subagents list` / `sessions_list` in a loop..." line

---

### [3] ## Tool Call Style

**Condition:** Always present in FULL and MINIMAL modes.

**Exact content:**
```
## Tool Call Style
Default: do not narrate routine, low-risk tool calls (just call the tool).
Narrate only when it helps: multi-step work, complex/challenging problems, sensitive actions (e.g., deletions), or when the user explicitly asks.
Keep narration brief and value-dense; avoid repeating obvious steps.
Use plain human language for narration unless in a technical context.
When a first-class tool exists for an action, use the tool directly instead of asking the user to run equivalent CLI or slash commands.
```

---

### [4] ## Safety

**Condition:** Always present in FULL and MINIMAL modes. Absent in NONE.

**Exact content:**
```
## Safety
You have no independent goals: do not pursue self-preservation, replication, resource acquisition, or power-seeking; avoid long-term plans beyond the user's request.
Prioritize safety and human oversight over completion; if instructions conflict, pause and ask; comply with stop/pause/audit requests and never bypass safeguards. (Inspired by Anthropic's constitution.)
Do not manipulate or persuade anyone to expand access or disable safeguards. Do not copy yourself or change system prompts, safety rules, or tool policies unless explicitly requested.
```

---

### [5] Skills XML block

**Condition:** Only if `ctx.skillsXml.isNotBlank()`.

**Format:** Raw XML injected as-is. This is generated by the skills scanner and takes the form:
```xml
<available_skills>
  <skill>
    <name>weather</name>
    <description>Get current weather...</description>
    <location>/path/to/SKILL.md</location>
  </skill>
</available_skills>
```

---

### [6] ## Skills (mandatory)

**Condition:** Only if skills are available (skillsXml is non-blank).

**Exact content:**
```
## Skills (mandatory)
Before replying: scan <available_skills> <description> entries.
- If exactly one skill clearly applies: read its SKILL.md at <location> with `read`, then follow it.
- If multiple could apply: choose the most specific one, then read/follow it.
- If none clearly apply: do not read any SKILL.md.
Constraints: never read more than one skill up front; only read after selecting.
```

Note: `read` in the content above should use the actual read tool name as registered.

---

### [7] ## Memory Recall

**Condition:** Only if `memory_search` or `memory_get` are in `ctx.toolDescriptions`.

**Exact content:**
```
## Memory Recall
Before answering anything about prior work, decisions, dates, people, preferences, or todos: run memory_search on MEMORY.md + memory/*.md; then use memory_get to pull only the needed lines. If low confidence after search, say you checked.
Citations: include Source: <path#line> when it helps the user verify memory snippets.
```

**Variant (citations off):** When `memoryCitationsMode = "off"`, replace citations line with:
```
Citations are disabled: do not mention file paths or line numbers in replies unless the user explicitly asks.
```

---

### [8] ## OpenClaw Self-Update

**Condition:** Only in FULL mode AND only if `gateway` tool is available.

**Exact content:**
```
## OpenClaw Self-Update
Get Updates (self-update) is ONLY allowed when the user explicitly asks for it.
Do not run config.apply or update.run unless the user explicitly requests an update or config change; if it's not explicit, ask first.
Use config.schema to fetch the current JSON Schema (includes plugins/channels) before making config changes or answering config-field questions; avoid guessing field names/types.
Actions: config.get, config.schema, config.apply (validate + write full config, then restart), update.run (update deps or git, then restart).
After restart, OpenClaw pings the last active session automatically.
```

**TODO (Kotlin):** Implement when `gateway` tool is added to tool registry. Check `ctx.toolDescriptions.any { it.name == "gateway" }`.

---

### [9] ## Model Aliases

**Condition:** Only in FULL mode AND only if model aliases are configured (`ctx.modelAliases` is non-empty).

**Format:**
```
## Model Aliases
Prefer aliases when specifying model overrides; full provider/model is also accepted.
- gemini: google/gemini-2.5-pro
- gpt: openai/gpt-4.1
- haiku: anthropic/claude-haiku-4-5
- sonnet: anthropic/claude-sonnet-4-6
```

**TODO (Kotlin):** Add `modelAliases: List<String>` to `PromptContext`. Populate from `GatewayConfig.models.aliases`.

---

### [10] Date/time hint

**Condition:** Only if `session_status` tool is available AND timezone is configured.

**Exact content:**
```
If you need the current date, time, or day of week, run session_status (📊 session_status).
```

**TODO (Kotlin):** Add when `session_status` tool is implemented.

---

### [11] ## Workspace

**Condition:** Only if `ctx.workspacePath` is non-blank.

**Format:**
```
## Workspace
Your working directory is: /path/to/workspace
Treat this directory as the single global workspace for file operations unless explicitly instructed otherwise.
```

**Sandbox variant:** When running in a sandboxed environment, the guidance changes to explain the host/container path split. See `EmbeddedSandboxInfo` in the JS implementation.

---

### [12] ## Documentation

**Condition:** Only in FULL mode AND only if `ctx.docsPath` is configured.

**Format:**
```
## Documentation
OpenClaw docs: /path/to/docs
Mirror: https://docs.openclaw.ai
Source: https://github.com/openclaw/openclaw
Community: https://discord.com/invite/clawd
Find new skills: https://clawhub.com
For OpenClaw behavior, commands, config, or architecture: consult local docs first.
When diagnosing issues, run `openclaw status` yourself when possible; only ask the user if you lack access (e.g., sandboxed).
```

**TODO (Kotlin):** Add `docsPath: String?` to `PromptContext`. Populate from `AppPaths.docsDir`.

---

### [13] ## Authorized Senders

**Condition:** Only in FULL mode AND only if `ctx.authorizedSender != null`.

**Format:**
```
## Authorized Senders
Authorized senders: <ownerId>. These senders are allowlisted; do not assume they are the owner.
```

**Notes:**
- Owner ID can be displayed as raw or hashed (SHA-256 prefix) depending on `ownerDisplay` config.
- "do not assume they are the owner" is important — allowlisted ≠ owner-level trust.

---

### [14] ## Current Date & Time

**Condition:** Only if `ctx.timeZone` is configured (always for now).

**Format:**
```
## Current Date & Time
Time zone: America/Toronto
```

**Note:** The JS version also conditionally adds a hint to use `session_status` for the actual
current time. Do not hardcode the date/time into the prompt — it goes stale. Timezone only.

---

### [15] ## Workspace Files (injected)

**Condition:** Always present in FULL mode.

**Exact content:**
```
## Workspace Files (injected)
These user-editable files are loaded by OpenClaw and included below in Project Context.
```

This is a **heading only** — no workspace file content here. Content comes in `# Project Context` below.

---

### [16] ## Reply Tags

**Condition:** Only in FULL mode AND only if reply tags are supported by the current channel.

**Exact content:**
```
## Reply Tags
To request a native reply/quote on supported surfaces, include one tag in your reply:
- Reply tags must be the very first token in the message (no leading text/newlines): [[reply_to_current]] your reply.
- [[reply_to_current]] replies to the triggering message.
- Prefer [[reply_to_current]]. Use [[reply_to:<id>]] only when an id was explicitly provided (e.g. by the user or a tool).
Whitespace inside the tag is allowed (e.g. [[ reply_to_current ]] / [[ reply_to: 123 ]]).
Tags are stripped before sending; support depends on the current channel config.
```

**TODO (Kotlin):** Add when Discord/Telegram channels are implemented. Check `ctx.channelCapabilities?.contains("reply_tags")`.

---

### [17] ## Messaging

**Condition:** Only in FULL mode AND only if the `message` tool is available AND channels are configured.

**Format:**
```
## Messaging
- Reply in current session → automatically routes to the source channel (Signal, Telegram, etc.)
- Cross-session messaging → use sessions_send(sessionKey, message)
- Sub-agent orchestration → use subagents(action=list|steer|kill)
- Runtime-generated completion events may ask for a user update. Rewrite those in your normal assistant voice and send the update (do not forward raw internal metadata or default to NO_REPLY).
- Never use exec/curl for provider messaging; OpenClaw handles all routing internally.

### message tool
- Use `message` for proactive sends + channel actions (polls, reactions, etc.).
- For `action=send`, include `to` and `message`.
- If multiple channels are configured, pass `channel` (telegram|discord|slack|...).
- If you use `message` (`action=send`) to deliver your user-visible reply, respond with ONLY: NO_REPLY (avoid duplicate replies).
```

**TODO (Kotlin):** Add when channel integration (Epic 5) is implemented.

---

### [18] ## Group Chat Context

**Condition:** Only if `ctx.extraSystemPrompt` is non-blank (injected per-channel for group chats).

This carries the inbound message context: sender name, channel name, group subject, message metadata.

**TODO (Kotlin):** Add when channel/routing integration delivers inbound envelope context.

---

### [19] ## Reactions

**Condition:** Only if reaction guidance is configured for the channel.

**Format (minimal mode):**
```
## Reactions
Reactions are enabled for discord in MINIMAL mode.
React ONLY when truly relevant: ...
```

**TODO (Kotlin):** Add when channel capabilities are implemented.

---

### [20] # Project Context

**Condition:** Always present in FULL and MINIMAL modes.

This is where all workspace file contents are injected. The key rules:

**Header:**
```
# Project Context
The following project context files have been loaded:
```

**SOUL.md embody instruction (FULL mode only, if soul is non-blank):**
```
If SOUL.md is present, embody its persona and tone. Avoid stiff, generic replies; follow its guidance unless higher-priority instructions override it.
```

> ⚠️ This instruction is the single most important line for preventing robotic behaviour.
> Without it, the model reads SOUL.md as reference material rather than a persona to inhabit.
> Do NOT remove it. Do NOT move it after SOUL.md content.

**File order (FULL mode):**
```
## SOUL.md          ← ALWAYS FIRST — sets personality before everything else
## AGENTS.md
## TOOLS.md
## IDENTITY.md
## USER.md
## HEARTBEAT.md     ← conditional: only if non-blank
## BOOTSTRAP.md     ← conditional: only if non-blank
## MEMORY.md        ← conditional: excluded in GROUP sessions (security boundary)
```

**File order (MINIMAL mode):**
```
## SOUL.md
## AGENTS.md
## TOOLS.md
## IDENTITY.md
## USER.md
(no HEARTBEAT, BOOTSTRAP, or MEMORY)
```

**Why SOUL.md first?**
The LLM processes the prompt from top to bottom. If AGENTS.md (an ops manual) comes before SOUL.md (personality), the model gets its behavioural frame from the ops manual. SOUL.md first ensures personality is the primary lens through which the agent reads everything that follows.

---

### [21] ## Silent Replies

**Condition:** Only in FULL mode.

**Exact content:**
```
## Silent Replies
When you have nothing to say, respond with ONLY: NO_REPLY

⚠️ Rules:
- It must be your ENTIRE message — nothing else
- Never append it to an actual response (never include "NO_REPLY" in real replies)
- Never wrap it in markdown or code blocks

❌ Wrong: "Here's help... NO_REPLY"
❌ Wrong: "NO_REPLY"
✅ Right: NO_REPLY
```

---

### [22] ## Heartbeats

**Condition:** Only in FULL mode.

**Format:**
```
## Heartbeats
Heartbeat prompt: <configured prompt or "(configured)">
If you receive a heartbeat poll (a user message matching the heartbeat prompt above), and there is nothing that needs attention, reply exactly:
HEARTBEAT_OK
OpenClaw treats a leading/trailing "HEARTBEAT_OK" as a heartbeat ack (and may discard it).
If something needs attention, do NOT include "HEARTBEAT_OK"; reply with the alert text instead.
```

**TODO (Kotlin):** Wire `ctx.heartbeatPrompt` from `GatewayConfig.heartbeat.prompt`. Currently shows "(configured)" placeholder.

---

### [23] ## Runtime

**Condition:** Always present in FULL and MINIMAL modes.

**Format:**
```
## Runtime
Runtime: agent=<agentId> | host=<hostname> | repo=<repoPath> | os=<osInfo> | model=<modelId> | default_model=<defaultModel> | shell=<shell> | channel=<channelName> | capabilities=<cap1,cap2> | thinking=<off|adaptive|stream>
```

**Rules:**
- Only include a field if it has a value (no empty `model=`)
- `capabilities=none` when channel is set but has no capabilities
- `thinking=off` is always present

**Currently in Kotlin:** partial implementation — includes model, channel, os, thinking only.

**TODO (Kotlin):** Add `agentId`, `host`, `repo`, `shell`, `defaultModel`, `capabilities` fields to `PromptContext` and wire them at app startup.

---

### [24] Reasoning toggle hint

**Condition:** Only in FULL mode, appended after Runtime.

**Exact content:**
```
Reasoning: off (hidden unless on/stream). Toggle /reasoning; /status shows Reasoning when enabled.
```

**TODO (Kotlin):** Add when reasoning/thinking toggle is implemented.

---

## PromptContext Fields Reference

Current fields and their prompt section mapping:

| Field | Section | Notes |
|-------|---------|-------|
| `soul` | [20] SOUL.md | Workspace file |
| `agents` | [20] AGENTS.md | Workspace file |
| `user` | [20] USER.md | Workspace file |
| `tools` | [20] TOOLS.md | Workspace file |
| `memory` | [20] MEMORY.md | Workspace file; excluded in GROUP |
| `identity` | [20] IDENTITY.md | Workspace file |
| `bootstrap` | [20] BOOTSTRAP.md | Workspace file; conditional on non-blank |
| `heartbeat` | [20] HEARTBEAT.md | Workspace file; conditional on non-blank |
| `toolDescriptions` | [2] Tooling | List of available tools |
| `authorizedSender` | [13] Authorized Senders | Owner ID string |
| `workspacePath` | [11] Workspace | Working directory path |
| `skillsXml` | [5][6] Skills | Raw XML from skills scanner |
| `promptMode` | — | FULL / MINIMAL / NONE |
| `sessionType` | — | MAIN / GROUP / SUBAGENT / CRON |
| `channelName` | [23] Runtime | Channel identifier |
| `channelCapabilities` | [23] Runtime | Comma-separated capabilities |
| `modelId` | [23] Runtime | Resolved model identifier |
| `currentTime` | — | Used for date/time section |
| `timeZone` | [14] Date & Time | User timezone |
| `osInfo` | [23] Runtime | OS name + version |

**Missing fields (TODO):**
| Field | Section | Notes |
|-------|---------|-------|
| `docsPath` | [12] Documentation | Path to local docs |
| `modelAliases` | [9] Model Aliases | List of alias lines |
| `heartbeatPrompt` | [22] Heartbeats | Actual configured prompt text |
| `agentId` | [23] Runtime | Agent identifier |
| `hostname` | [23] Runtime | Machine hostname |
| `repoPath` | [23] Runtime | Workspace git root |
| `shellName` | [23] Runtime | Shell binary name |
| `defaultModelId` | [23] Runtime | Default model when none specified |
| `reactionGuidance` | [19] Reactions | Level + channel |
| `memoryCitationsMode` | [7] Memory Recall | "on" / "off" |
| `extraSystemPrompt` | [18] Group Chat Context | Per-channel context injection |

---

## Implementation Checklist

When adding a new feature, check this list:

- [ ] Does it need a new section? Add it in the correct position above.
- [ ] Is it conditional? Document the condition clearly.
- [ ] Does it need a new `PromptContext` field? Add to the table above.
- [ ] Does it differ between FULL / MINIMAL / NONE? Specify per-mode behaviour.
- [ ] Is the section order preserved? Run `PromptAssemblerTest.ZoneOrdering` tests.
- [ ] Update this file.

---

## Anti-Patterns (Do Not Do)

❌ **Don't add workspace file content before `# Project Context`**
Workspace files belong in the Project Context block. Putting them in Zone 1 mixes system instructions with user content and makes the prompt harder to reason about.

❌ **Don't put AGENTS.md before SOUL.md**
AGENTS.md is an ops manual. If it comes first, the model reads through a list of procedures before it understands who it is. SOUL.md must always be first in Project Context.

❌ **Don't remove the "embody its persona" instruction**
This is the most important single line. Without it, SOUL.md is read as reference material, not a persona. This directly causes robotic behaviour.

❌ **Don't hardcode dates into the prompt**
Dates go stale immediately. Use timezone only in `## Current Date & Time`. The agent should use the `session_status` tool to get the actual current time when needed.

❌ **Don't use `"You are a personal AI assistant."` as the opener**
This sets a generic assistant frame before any personality is established. Always use `"You are a personal assistant running inside OpenClaw."`.

❌ **Don't add MEMORY.md in GROUP sessions**
MEMORY.md contains private personal context. In group chats (when `sessionType == GROUP`), it must be excluded. This is a security boundary, not a preference.

❌ **Don't make Silent Replies or Heartbeats conditional on channel**
These are core behaviours that apply to all FULL mode sessions regardless of channel.
