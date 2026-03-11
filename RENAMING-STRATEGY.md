# Renaming Strategy: openclaw → kclaw

> **Status:** Strategy only — no changes made yet.
> **Decision date:** 2026-03-11
> **Scope:** Rename all openclaw/OpenClaw references inside the Kotlin project to kclaw/KClaw.
> **TS project:** NEVER touched. Read-only reference only.

---

## Summary of Changes

| Category | From | To |
|---|---|---|
| Root project name | `openclaw-kt` | `kclaw` |
| Gradle group | `com.openclaw` | `com.kclaw` |
| Package names | `com.openclaw.*` | `com.kclaw.*` |
| Config dir | `~/.openclawk/` | `~/.kclaw/` |
| Config file | `openclaw.conf` | `kclaw.conf` |
| Class name | `OpenClawStrategy` | `KClawStrategy` |
| File name | `OpenClawStrategy.kt` | `KClawStrategy.kt` |
| Root folder | `openclaw-kt/` | `kclaw/` |
| GitHub repo | `openclaw-kt` | `kclaw` |
| `BASE_DIR_NAME` constant | `".openclawk"` | `".kclaw"` |

---

## 1. Gradle Build Files

### `settings.gradle.kts`
```
rootProject.name = "openclaw-kt"  →  rootProject.name = "kclaw"
```

### `build.gradle.kts` (root)
```
group = "com.openclaw"  →  group = "com.kclaw"
```

### `openclaw-kt/app/build.gradle.kts`
- Any `com.openclaw` main class references → `com.kclaw`

---

## 2. Kotlin Package Declarations

Every `.kt` file starts with `package com.openclaw.*` — all must be updated.

**Files by module (source + test):**

### `protocol/`
- `com.openclaw.protocol.*` → `com.kclaw.protocol.*`
- Files: `Frame.kt`, `OutboundMessage.kt`, `AgentEvent.kt`, `StopReason.kt`,
  `ToolPermission.kt`, `InboundEnvelope.kt`, `QueueAction.kt`,
  `SessionKey.kt`, `Enums.kt`, `Identifiers.kt` + all test files

### `gateway-core/`
- `com.openclaw.gateway.*` → `com.kclaw.gateway.*`
- Files: `GatewayServer.kt`, `Gateway.kt`, `AppPaths.kt`, `GatewayConfig.kt`,
  `ConfigManager.kt`, `ConfigWriter.kt`, `HoconMapper.kt`, `ConfigError.kt`,
  `ConfigPathEval.kt`, `GatewayAuth.kt`, `AuthRateLimiter.kt`, `RpcRouter.kt`,
  `SessionRpcHandlers.kt`, `ConfigRpcHandlers.kt`, `SetupRpcHandlers.kt`,
  `MethodScopeRegistry.kt`, `SessionRegistry.kt`, `WebSocketHandler.kt`,
  `UnauthorizedFloodGuard.kt`, `PresenceRegistry.kt`, `WebSocketLimits.kt`
  + all test files

### `agent-engine/`
- `com.openclaw.agent.*` → `com.kclaw.agent.*`
- Files: `AgentEngine.kt`, all model/tools/runtime/strategy/prompt/workspace/rpc/isolation files
- **Special:** `OpenClawStrategy.kt` → rename file to `KClawStrategy.kt`
  and class `OpenClawStrategy` → `KClawStrategy`

### `session-store/`
- `com.openclaw.session.*` → `com.kclaw.session.*`
- Files: All session, compaction, jsonl, sqlite, postgres, composite files + tests

### `skills/`
- `com.openclaw.skills.*` → `com.kclaw.skills.*`
- Files: All skill files including new `BundledSkillsResolver.kt` + tests

### `tools/tools-api/`
- `com.openclaw.tools.*` → `com.kclaw.tools.*`

### `tools/exec/`
- `com.openclaw.tools.exec.*` → `com.kclaw.tools.exec.*`

### `tools/filesystem/`
- `com.openclaw.tools.filesystem.*` → `com.kclaw.tools.filesystem.*`

### `tools/web/`
- `com.openclaw.tools.web.*` → `com.kclaw.tools.web.*`

### `tools/memory-tools/`
- `com.openclaw.tools.memory.*` → `com.kclaw.tools.memory.*`

### `memory/`
- `com.openclaw.memory.*` → `com.kclaw.memory.*`

### `plugins/`
- `com.openclaw.plugins.*` → `com.kclaw.plugins.*`

### `channels/` (stubs — will be removed in future cleanup)
- `com.openclaw.channels.*` → `com.kclaw.channels.*`

### `cron/`
- `com.openclaw.cron.*` → `com.kclaw.cron.*`

### `orchestrator/`
- `com.openclaw.orchestrator.*` → `com.kclaw.orchestrator.*`

### `cli/`
- `com.openclaw.cli.*` → `com.kclaw.cli.*`

### `app/`
- `com.openclaw.*` → `com.kclaw.*`
- Files: `Main.kt`, `OperatorRpcHandlers.kt` + tests

---

## 3. Runtime Path Constants

### `AppPaths.kt`
```kotlin
const val BASE_DIR_NAME = ".openclawk"  →  ".kclaw"
val config = base.resolve("openclaw.conf")  →  base.resolve("kclaw.conf")
// Comments: "~/.openclawk/" → "~/.kclaw/"
```

### `GatewayConfig.kt`
```kotlin
val workspace = "~/.openclawk/workspace"  →  "~/.kclaw/workspace"
val path = "~/.openclawk/checkpoints.sqlite"  →  "~/.kclaw/checkpoints.sqlite"
```

### `SkillScanner.kt` (USER source path)
```kotlin
Path.of(System.getProperty("user.home"), ".openclawk", "skills")
→  Path.of(System.getProperty("user.home"), ".kclaw", "skills")
```

### Any other hardcoded `~/.openclawk` strings in source or tests
- `GatewayConfig.kt`, `ConfigWriterTest.kt`, `ConfigManagerTest.kt`,
  `HoconMapperTest.kt`, `SkillScannerTest.kt`, `SkillLoaderTest.kt`,
  `WorkspaceBootstrap.kt`, `WorkspaceBootstrapTest.kt`, `AgentsListToolTest.kt`,
  `SkillsRpcHandlersTest.kt`, `Main.kt`

---

## 4. Class / File Renames

| Current file | New file | Current class | New class |
|---|---|---|---|
| `OpenClawStrategy.kt` | `KClawStrategy.kt` | `OpenClawStrategy` | `KClawStrategy` |
| `OpenClawStrategyTest.kt` | `KClawStrategyTest.kt` | `OpenClawStrategyTest` | `KClawStrategyTest` |

All usages of `OpenClawStrategy` in `AgentRuntime.kt`, `MemoryFlushRunner.kt`, and any other references must be updated to `KClawStrategy`.

---

## 5. Documentation Files

### `SPEC.md`
- Title: `OpenClaw-KT` → `KClaw`
- All body references to `OpenClaw`, `openclaw-kt`, `openclaw`

### `TASKS.md`
- Epic headers, dependency notes, body text referencing `OpenClaw`/`openclaw`

### `README.md`
- Full rename throughout

### `AUDIT-CORE.md`, `AGENT-ENGINE-PLAN.md`, `GATEWAY-LESSONS.md`,
  `CLAUDE.md`, `PROMPT-GUIDE.md`, `IMPLEMENTATION-PLAN.md`, `DASHBOARD-PLAN.md`
- All `openclaw`/`OpenClaw`/`openclaw-kt` references updated

---

## 6. Root Directory Rename

The physical folder `openclaw-kt/` can optionally be renamed to `kclaw/` at the filesystem level.
This is cosmetic — Gradle uses `rootProject.name` not the folder name — but keeps things consistent.

**Note:** This must be done before or after running the code changes, not during.
Recommend: rename the GitHub repo first, update git remote URL, then rename the local folder.

---

## 7. Build Output / `.gitignore`

Build artifacts in `build/` dirs contain `com.openclaw` in test result XML filenames.
These are generated files — they auto-update on next build. No manual action needed.

---

## 8. GitHub

1. Rename repo on GitHub: `Settings → Repository name → kclaw`
2. Update local git remote:
   ```
   git remote set-url origin https://github.com/Ck-chege/kclaw
   ```

---

## Execution Plan (when ready)

1. Rename GitHub repo → update local remote URL
2. Run Claude Code with `--permission-mode bypassPermissions` in `openclaw-kt/` to:
   - Rename root project in `settings.gradle.kts`
   - Update group in `build.gradle.kts`
   - Rename `OpenClawStrategy.kt` → `KClawStrategy.kt` (+ test file)
   - Find-replace all `com.openclaw` → `com.kclaw` in all `.kt`/`.kts` files
   - Find-replace all `.openclawk` → `.kclaw` in all source files
   - Find-replace all `openclaw.conf` → `kclaw.conf` in all source files
   - Update all doc files
3. Run `./gradlew build` to verify — should compile and all tests pass
4. Commit: `chore: rename openclaw → kclaw`
5. Push
6. Optionally rename the root folder `openclaw-kt/` → `kclaw/` locally

**Estimated effort:** Single Claude Code run, ~20 min.

---

## What Does NOT Change

- The TS reference project (`openclaw/` in workspace) — **read-only, never touched**
- Gradle module functional names (`skills`, `tools`, `memory`, etc.) — these are capability names, not brand names
- External dependency names (Koog, Ktor, Kotest, etc.)
- Git history
