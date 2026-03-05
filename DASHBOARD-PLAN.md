# kclaw Dashboard — Comprehensive Implementation Plan

> **Vision**: A best-in-class AI assistant command center that goes beyond OpenClaw's original LitElement dashboard — built with React 19, shadcn/ui, and modern UX patterns inspired by Claude.ai, ChatGPT, Perplexity, Cursor, and TypingMind.

---

## Current State Assessment

The `web-ui/` directory has a working foundation:

| Component | Status | Notes |
|-----------|--------|-------|
| React 19 + Vite 7 + TypeScript | Done | Solid base |
| Tailwind CSS 4 + shadcn/ui | Done | Radix primitives installed |
| GatewayClient (WebSocket RPC) | Done | Auto-reconnect, request/response, event handlers |
| Protocol types | Done | Mirrors Kotlin sealed classes |
| Zustand stores | Done | connection, chat, dashboard, theme |
| Chat: MessageBubble | Done | User/assistant bubbles with avatar |
| Chat: MarkdownContent | Done | react-markdown + remark-gfm (basic) |
| Chat: ToolCallBlock | Done | Collapsible tool call display |
| Chat: ChatInput | Done | Basic text input + send/stop |
| Sidebar | Done | Sessions/Config/Status tabs, collapsible |
| ConnectDialog | Done | Token-based auth dialog |
| ConnectionBanner | Done | Status indicator |
| ThemeToggle | Done | Light/dark switch |

**What's missing**: Routing, command palette, keyboard shortcuts, search, syntax highlighting, code copy, agent management, channel views, cron UI, logs viewer, usage analytics, execution approvals, settings panel, virtualized lists, skeleton loading, toast notifications, mobile responsiveness, accessibility, and more.

---

## Tech Stack (Confirmed)

| Layer | Choice | Version |
|-------|--------|---------|
| Framework | React (SPA, served by Ktor) | 19.2 |
| Build | Vite | 7.3 |
| Language | TypeScript | 5.9 |
| Styling | Tailwind CSS + shadcn/ui | 4.2 |
| State | Zustand | 5.0 |
| Icons | Lucide React | 0.576 |
| Markdown | react-markdown + remark-gfm | 10.1 |
| Routing | React Router | 7.x (new) |
| Syntax Highlighting | Shiki (via rehype-shiki) | New |
| Virtualization | React Virtuoso | New |
| Command Palette | cmdk (via shadcn/ui) | New |
| Toasts | Sonner | New |
| Charts | Recharts | New |
| Date Utils | date-fns | New |

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    App Shell (Layout)                        │
│  ┌─────────┐  ┌────────────────────────────────────────┐    │
│  │         │  │         Header Bar                     │    │
│  │         │  │  [breadcrumb] [search] [cmd+k] [user]  │    │
│  │  Side   │  ├────────────────────────────────────────┤    │
│  │  bar    │  │                                        │    │
│  │         │  │         Main Content Area               │    │
│  │  Nav    │  │                                        │    │
│  │  Icons  │  │  Routed views:                         │    │
│  │         │  │  - /chat          (Chat + Artifacts)   │    │
│  │  +      │  │  - /agents        (Agent Manager)      │    │
│  │         │  │  - /sessions      (Session Browser)    │    │
│  │  Quick  │  │  - /channels      (Channel Status)     │    │
│  │  Access │  │  - /tools         (Tool Catalog)       │    │
│  │         │  │  - /skills        (Skill Manager)      │    │
│  │         │  │  - /cron          (Scheduler)          │    │
│  │         │  │  - /logs          (Log Viewer)         │    │
│  │         │  │  - /usage         (Analytics)          │    │
│  │         │  │  - /settings      (Configuration)      │    │
│  └─────────┘  └────────────────────────────────────────┘    │
│  [Command Palette overlay - Cmd+K]                          │
│  [Toast notifications - bottom right]                       │
└─────────────────────────────────────────────────────────────┘
```

---

## Epics & Task Breakdown

### Epic 1: Foundation & Navigation Shell
**Goal**: Replace the current single-view layout with a proper routed app shell.

#### 1.1 Install new dependencies
- [ ] Add react-router-dom v7, sonner, cmdk, date-fns, recharts, react-virtuoso, shiki/rehype-shiki
- [ ] Verify all packages resolve and `npm run build` succeeds

#### 1.2 App Router setup
- [ ] Create `src/router.tsx` with route definitions for all views
- [ ] Create placeholder page components for each route (`/chat`, `/agents`, `/sessions`, `/channels`, `/tools`, `/skills`, `/cron`, `/logs`, `/usage`, `/settings`)
- [ ] Wrap `<App>` with `<BrowserRouter>` and `<Routes>`
- [ ] Implement 404 catch-all route with a helpful "Page not found" view

#### 1.3 Redesign the App Shell layout
- [ ] Create `AppShell.tsx` — the outer frame with sidebar + header + content area
- [ ] Create `HeaderBar.tsx` — breadcrumb trail, global search trigger, Cmd+K button, connection status pill, theme toggle, user menu
- [ ] Redesign `Sidebar.tsx` — vertical icon nav (collapsed by default on desktop), with tooltip labels on hover:
  - Chat (primary — top)
  - Agents
  - Sessions
  - Channels
  - Tools & Skills (grouped)
  - Cron
  - Logs
  - Usage
  - Separator
  - Settings (bottom)
- [ ] Support sidebar expand on hover or pin toggle
- [ ] Active route highlighting in sidebar
- [ ] Mobile: hamburger menu → full-screen overlay nav

#### 1.4 Command Palette (Cmd+K)
- [ ] Install and configure `cmdk` via shadcn/ui `Command` component
- [ ] Register global `Cmd+K` / `Ctrl+K` keyboard shortcut
- [ ] Sections: Navigation, Recent Sessions, Quick Actions (new session, toggle theme, disconnect)
- [ ] Fuzzy search across all registered commands
- [ ] Keyboard navigation (arrow keys, enter, escape)

#### 1.5 Toast notification system
- [ ] Install and configure `sonner` (Toaster provider in App)
- [ ] Create `useNotifications` hook wrapping sonner's toast API
- [ ] Wire up: connection state changes, RPC errors, agent completion, session actions
- [ ] Toast variants: success, error, warning, info

#### 1.6 Global keyboard shortcuts
- [ ] Create `useKeyboardShortcuts` hook
- [ ] Register shortcuts:
  - `Cmd+K` — Command palette
  - `Cmd+N` — New session
  - `Cmd+Shift+S` — Toggle sidebar
  - `Cmd+,` — Settings
  - `Escape` — Close modals / stop generation
  - `/` — Focus chat input (when not in input)
- [ ] Create keyboard shortcut help modal (accessible from settings or `?` key)

---

### Epic 2: Enhanced Chat Experience
**Goal**: Upgrade the chat view from basic to world-class — streaming, syntax highlighting, artifacts, and rich tool visualization.

#### 2.1 Syntax-highlighted code blocks
- [ ] Integrate Shiki for code block rendering inside markdown
- [ ] Support language detection from fenced code block labels
- [ ] Add "Copy" button to every code block (top-right corner)
- [ ] Add language label badge (top-left corner)
- [ ] Dark/light theme-aware Shiki themes
- [ ] Handle streaming code blocks (don't re-highlight on every token — memoize completed blocks)

#### 2.2 Improved streaming UX
- [ ] Animate cursor/caret at end of streaming text
- [ ] Debounce markdown re-parsing during streaming (parse only completed blocks, render tail as plain text)
- [ ] Show elapsed time during streaming ("Thinking... 3.2s")
- [ ] Smooth auto-scroll to bottom during streaming (with "scroll to bottom" FAB if user scrolled up)

#### 2.3 Virtualized message list
- [ ] Replace current `MessageList` with React Virtuoso's `Virtuoso` component
- [ ] Handle dynamic message heights (markdown, code blocks, tool calls)
- [ ] Implement "load more" on scroll-up for long sessions (paginated history fetch)
- [ ] Maintain scroll position when new messages prepend (history load)

#### 2.4 Thinking/reasoning blocks
- [ ] Add support for `agent.thinking` event from backend
- [ ] Render thinking blocks as collapsible, dimmed sections above the response
- [ ] Toggle to show/hide thinking globally (per-session preference)

#### 2.5 Rich tool execution visualization
- [ ] Redesign `ToolCallBlock` as a card with:
  - Tool icon (wrench for generic, custom per tool type: terminal for exec, file for fs, globe for web)
  - Status: spinner → checkmark/error with animation
  - Duration badge
  - Expandable args/result with syntax highlighting
- [ ] For `web_search` tool: render results as link cards with favicons
- [ ] For `exec` tool: render output in terminal-styled block (monospace, dark bg)
- [ ] For `read`/`write`/`edit` tools: show file path as a badge with file icon
- [ ] Group consecutive tool calls under a single "Used N tools" collapsible when there are 3+

#### 2.6 Message actions
- [ ] Hover menu on each message: Copy, Regenerate (assistant only), Edit (user only), Delete
- [ ] Copy as markdown (full conversation or single message)
- [ ] Message edit → re-send with option to branch conversation

#### 2.7 Enhanced chat input
- [ ] Auto-resizing textarea (grows to max 6 lines, then scrolls)
- [ ] File attachment button (drag-and-drop support)
- [ ] Model selector dropdown in input bar
- [ ] Slash command autocomplete (`/stop`, `/new`, `/model`, `/status`, `/help`)
- [ ] Shift+Enter for newline, Enter to send
- [ ] Character/token count indicator
- [ ] Suggested prompts for empty sessions (first-run experience)

#### 2.8 Artifacts / Split pane (Claude.ai inspired)
- [ ] Detect artifact-worthy content in responses: HTML, SVG, Mermaid diagrams, large code blocks
- [ ] Render in a resizable right pane (split view)
- [ ] Artifact tabs (multiple artifacts in one conversation)
- [ ] Live preview for HTML/SVG artifacts
- [ ] Full-screen toggle for artifacts
- [ ] Download artifact as file

---

### Epic 3: Session Management
**Goal**: Full session lifecycle management with rich metadata and search.

#### 3.1 Session browser page (`/sessions`)
- [ ] Table/list view of all sessions with columns: Key, Agent, Channel, Last Message, Message Count, Tokens, Created
- [ ] Sortable columns (click header to sort)
- [ ] Filter bar: by agent, by channel, by date range
- [ ] Search sessions by key or content
- [ ] Bulk actions: delete selected, export selected

#### 3.2 Session detail panel
- [ ] Click a session → slide-in panel (or navigate to `/sessions/:key`)
- [ ] Session metadata card: key, agent, model, tokens used, message count, compaction count, created/updated timestamps
- [ ] Quick actions: open in chat, reset, delete, export as JSON/markdown
- [ ] Session timeline: visual timeline of messages with timestamps

#### 3.3 Session sidebar enhancements
- [ ] Group sessions by date: Today, Yesterday, This Week, Older
- [ ] Session search/filter in sidebar
- [ ] Session rename (editable title — store in session meta)
- [ ] Pin favorite sessions to top
- [ ] Swipe to delete (mobile)
- [ ] Show last message preview under session name

---

### Epic 4: Agent Management
**Goal**: Visualize and manage the multi-agent system.

#### 4.1 Agent overview page (`/agents`)
- [ ] Card grid showing all configured agents
- [ ] Per-agent card: name, model, status (idle/running), active session count, total token usage
- [ ] Agent status badges: `idle`, `running`, `error`
- [ ] "Default agent" indicator

#### 4.2 Agent detail view
- [ ] Workspace files browser (SOUL.md, AGENTS.md, IDENTITY.md, USER.md, TOOLS.md, HEARTBEAT.md, MEMORY.md, BOOTSTRAP.md)
- [ ] Inline file editor with syntax highlighting (for .md workspace files)
- [ ] Model configuration: select model, set temperature, max tokens
- [ ] Tool bindings: which tools are available to this agent
- [ ] Skill bindings: which skills are active

#### 4.3 Agent run monitor
- [ ] Real-time view of currently running agents
- [ ] Per-agent: current session key, elapsed time, token consumption, active tool
- [ ] Stop button per agent
- [ ] Historical run log (last N runs with duration, tokens, status)

#### 4.4 Multi-agent orchestration view (future-ready)
- [ ] Visual graph/flowchart of agent-to-agent communication
- [ ] Show spawned sub-agents as child nodes
- [ ] Message passing visualization (arrows between agents)
- [ ] Placeholder UI for Phase 4 orchestrator features

---

### Epic 5: Channel Management
**Goal**: Monitor and configure messaging platform integrations.

#### 5.1 Channel overview page (`/channels`)
- [ ] Card grid: one card per configured channel (Telegram, Discord, Slack, WebChat, etc.)
- [ ] Per-channel: status indicator (connected/disconnected/error), message count (24h), last activity
- [ ] Channel-specific icons and brand colors

#### 5.2 Channel detail view
- [ ] Configuration display (non-sensitive fields)
- [ ] Connection status with last-connected timestamp
- [ ] Recent messages through this channel
- [ ] Test connection button
- [ ] Link to external platform (e.g., "Open in Discord")

#### 5.3 WebChat channel (built-in)
- [ ] The main `/chat` view IS the WebChat channel
- [ ] Show "webchat" badge in session info when chatting through the dashboard
- [ ] Route replies back through WebChat deterministically

---

### Epic 6: Tools & Skills
**Goal**: Browse, configure, and test tools and skills.

#### 6.1 Tool catalog page (`/tools`)
- [ ] Card grid of all registered tools grouped by category: Runtime, Filesystem, Exec, Web, Native, MCP
- [ ] Per-tool card: name, description, parameter schema, permission level
- [ ] Tool status: enabled/disabled per agent
- [ ] "Try it" button — manual tool invocation form (for debugging)

#### 6.2 Skill manager page (`/skills`)
- [ ] List of all loaded skills with metadata: name, description, trigger, gates, source (workspace/user/bundled)
- [ ] Skill precedence visualization (which source wins)
- [ ] Skill enable/disable toggle
- [ ] Skill detail: full SKILL.md content rendered as markdown
- [ ] Gate status indicators (binary check, env var, capability)

---

### Epic 7: Cron Scheduler
**Goal**: Visual cron job management.

#### 7.1 Cron dashboard page (`/cron`)
- [ ] Table of all cron jobs: name, schedule (human-readable), next run, last run, status, agent
- [ ] Create new cron job dialog: name, cron expression (with helper/preset picker), message, target agent
- [ ] Edit/delete existing jobs
- [ ] Enable/disable toggle per job
- [ ] Manual "Run now" button

#### 7.2 Cron run history
- [ ] Expandable row or detail panel showing run history per job
- [ ] Per-run: timestamp, duration, status (success/error), output summary
- [ ] Filter by date range

---

### Epic 8: Log Viewer
**Goal**: Real-time system log viewing with filtering.

#### 8.1 Log viewer page (`/logs`)
- [ ] Real-time log stream (subscribe to `log.*` events via WebSocket)
- [ ] Log levels: DEBUG, INFO, WARN, ERROR with color coding
- [ ] Filter by level, source module, search text
- [ ] Virtualized log list (can handle thousands of entries)
- [ ] Auto-scroll toggle (tail mode)
- [ ] Timestamp display (relative and absolute toggle)
- [ ] Copy log line
- [ ] Pause/resume stream
- [ ] Export logs as text file

---

### Epic 9: Usage Analytics
**Goal**: Token usage tracking and cost visualization.

#### 9.1 Usage dashboard page (`/usage`)
- [ ] Summary cards: total tokens (24h/7d/30d), estimated cost, messages sent, average response time
- [ ] Line chart: token usage over time (hourly/daily granularity)
- [ ] Breakdown by model (pie chart or stacked bar)
- [ ] Breakdown by agent
- [ ] Breakdown by channel
- [ ] Table: top sessions by token usage

#### 9.2 Cost estimation
- [ ] Per-model pricing configuration (stored in settings)
- [ ] Estimated cost per session and per time period
- [ ] Budget alerts (optional — warn when daily/monthly cost exceeds threshold)

---

### Epic 10: Settings & Configuration
**Goal**: Comprehensive settings panel.

#### 10.1 Settings page (`/settings`)
- [ ] Tabbed layout: General, Models, Appearance, Keyboard Shortcuts, Data, About

#### 10.2 General settings tab
- [ ] Gateway connection URL and token management
- [ ] Default agent selection
- [ ] Default model selection
- [ ] Auto-reconnect toggle
- [ ] Session auto-title toggle

#### 10.3 Models settings tab
- [ ] List of configured model providers (Anthropic, OpenAI, Google, Ollama)
- [ ] Per-provider: API key management (masked input, validation indicator)
- [ ] Per-model: temperature, max tokens, pricing
- [ ] Test connection button per provider

#### 10.4 Appearance settings tab
- [ ] Theme: Light / Dark / System (three-way toggle)
- [ ] Font size adjustment (chat messages)
- [ ] Code block theme selection
- [ ] Compact/comfortable message density toggle
- [ ] Sidebar position (left/right)

#### 10.5 Keyboard shortcuts tab
- [ ] Full list of all shortcuts with current bindings
- [ ] Customizable bindings (future)
- [ ] Search shortcuts

#### 10.6 Data settings tab
- [ ] Export all conversations (JSON)
- [ ] Import conversations
- [ ] Clear all local data
- [ ] Storage usage indicator (if using IndexedDB)

#### 10.7 About tab
- [ ] Version info (kclaw version, protocol version)
- [ ] System health check results
- [ ] Links: documentation, GitHub, feedback

---

### Epic 11: Execution Approvals
**Goal**: Human-in-the-loop tool execution approval workflow.

#### 11.1 Approval notification system
- [ ] When `exec.approval.requested` event arrives, show a toast + badge on sidebar
- [ ] Inline approval card in chat: "Agent wants to run `exec` with args: {...}. [Approve] [Deny]"
- [ ] Approval timeout indicator (countdown)

#### 11.2 Approval queue page
- [ ] List of pending approvals (separate from chat view)
- [ ] Per-approval: tool name, args preview, requesting agent, session, timestamp
- [ ] Batch approve/deny
- [ ] History of past approvals

---

### Epic 12: Mobile & Responsive
**Goal**: Fully usable on tablets and phones.

#### 12.1 Responsive layout
- [ ] Sidebar: hidden by default on mobile, revealed via hamburger menu
- [ ] Chat: full-width with bottom-anchored input
- [ ] Session list: bottom sheet on mobile
- [ ] Settings: full-screen on mobile
- [ ] All modals: full-screen on small viewports

#### 12.2 Touch optimizations
- [ ] Minimum 44px tap targets on all interactive elements
- [ ] Swipe right to reveal sidebar
- [ ] Swipe left on session to delete
- [ ] Pull-to-refresh on session list
- [ ] Long-press for context menu on messages

---

### Epic 13: Accessibility
**Goal**: WCAG 2.1 AA compliance.

#### 13.1 Semantic HTML & ARIA
- [ ] Proper landmark regions (nav, main, aside, header)
- [ ] ARIA live regions for streaming messages (announce new content to screen readers)
- [ ] Focus management: trap focus in modals, restore on close
- [ ] Skip-to-content link
- [ ] Proper heading hierarchy (h1-h6)
- [ ] All images/icons have aria-labels or alt text

#### 13.2 Keyboard accessibility
- [ ] All interactive elements reachable via Tab
- [ ] Focus visible indicators on all focusable elements
- [ ] Escape closes all overlays
- [ ] Arrow keys navigate lists (sessions, tools, etc.)

#### 13.3 Visual accessibility
- [ ] Color contrast minimum 4.5:1 for all text (verify with tooling)
- [ ] Don't rely solely on color to convey status (use icons + text)
- [ ] Support prefers-reduced-motion

---

### Epic 14: Performance
**Goal**: Smooth experience even with long conversations and many sessions.

#### 14.1 Loading states
- [ ] Skeleton loaders for: session list, chat history, agent cards, channel cards, settings
- [ ] Shimmer animation on skeletons
- [ ] Page-level loading indicator (thin progress bar at top)

#### 14.2 Optimistic updates
- [ ] Send message: show immediately in chat before server confirmation
- [ ] Delete session: remove from list immediately, restore on error
- [ ] Toggle settings: apply immediately, rollback on error

#### 14.3 Code splitting & lazy loading
- [ ] Lazy-load all route pages with `React.lazy` + Suspense
- [ ] Lazy-load Shiki (heavy) — only import when first code block appears
- [ ] Lazy-load Recharts — only import on `/usage` page
- [ ] Lazy-load cmdk — only import on first Cmd+K press

#### 14.4 Memoization
- [ ] `React.memo` on MessageBubble, ToolCallBlock, SessionItem
- [ ] Memoize completed markdown blocks during streaming (only re-render the last block)
- [ ] Cache session list between route navigations

---

## Implementation Priority / Suggested Order

| Phase | Epics | Rationale |
|-------|-------|-----------|
| **Phase A: Shell** | 1 (Foundation) | Routing and navigation must come first |
| **Phase B: Chat** | 2 (Chat), 14.1-14.2 (Loading/Optimistic) | The primary user experience |
| **Phase C: Management** | 3 (Sessions), 4 (Agents), 5 (Channels) | Core admin functionality |
| **Phase D: Operations** | 6 (Tools/Skills), 7 (Cron), 8 (Logs) | Operational views |
| **Phase E: Analytics** | 9 (Usage), 10 (Settings), 11 (Approvals) | Analytics and configuration |
| **Phase F: Polish** | 12 (Mobile), 13 (Accessibility), 14.3-14.4 (Perf) | Production polish |

---

## Design Principles

1. **Operator-first** — This is an admin command center, not a consumer chat app. Prioritize information density, keyboard shortcuts, and power-user features.
2. **Real-time by default** — Everything subscribes to WebSocket events. No polling. Data arrives the instant it changes.
3. **Progressive disclosure** — Show the essential info upfront; let users expand for details (tool calls, logs, configs).
4. **Consistent patterns** — Every list is searchable + filterable. Every card is expandable. Every action has undo (where possible).
5. **Dark mode native** — Dark theme is the default. Light mode is fully supported but dark is primary.

---

## Backend RPC Methods Required

The dashboard requires these RPC methods from the Ktor gateway (some exist, some need to be added):

| Method | Status | Used By |
|--------|--------|---------|
| `connect.hello` | Exists | Connection |
| `sessions.list` | Exists | Sessions, Sidebar |
| `sessions.history` | Exists | Chat |
| `sessions.delete` | Exists | Sessions |
| `sessions.reset` | Exists | Sessions |
| `agent.run` | Exists | Chat |
| `agent.stop` | Exists | Chat |
| `agent.status` | Exists | Agents |
| `config.get` | Exists | Settings |
| `config.set` | Needs impl | Settings |
| `models.list` | Exists | Status, Settings |
| `agents.list` | Needs impl | Agents page |
| `agents.detail` | Needs impl | Agent detail |
| `channels.list` | Needs impl | Channels page |
| `channels.status` | Needs impl | Channels page |
| `tools.catalog` | Needs impl | Tools page |
| `skills.list` | Needs impl | Skills page |
| `cron.list` | Needs impl | Cron page |
| `cron.create` | Needs impl | Cron page |
| `cron.update` | Needs impl | Cron page |
| `cron.delete` | Needs impl | Cron page |
| `cron.runs` | Needs impl | Cron page |
| `logs.subscribe` | Needs impl | Logs page |
| `usage.summary` | Needs impl | Usage page |
| `usage.timeseries` | Needs impl | Usage page |
| `exec.approval.list` | Needs impl | Approvals |
| `exec.approval.resolve` | Needs impl | Approvals |
| `workspace.files` | Needs impl | Agent detail |
| `workspace.read` | Needs impl | Agent detail |
| `workspace.write` | Needs impl | Agent detail |

---

*Last updated: 2026-03-04*
