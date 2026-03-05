# Dashboard Implementation Tasks

> Track progress here. Check off subtasks as they are completed.
> Each subtask includes implementation AND its tests passing.

---

## Phase A: Shell & Foundation

### Epic 1: Foundation & Navigation Shell

#### Story 1.1: New Dependencies
- [x] Install react-router-dom, sonner, cmdk, date-fns, recharts, react-virtuoso, shiki
- [x] Verify `npm run build` succeeds with all new deps

#### Story 1.2: App Router
- [x] Create `src/router.tsx` with route config for all 10 views
- [x] Create placeholder pages: `ChatPage`, `AgentsPage`, `SessionsPage`, `ChannelsPage`, `ToolsPage`, `SkillsPage`, `CronPage`, `LogsPage`, `UsagePage`, `SettingsPage`
- [x] Wrap App in `BrowserRouter` + `Routes`
- [x] Implement 404 catch-all page
- [x] Default route redirects to `/chat`

#### Story 1.3: App Shell Layout
- [x] Create `AppShell.tsx` — sidebar + header + `<Outlet />`
- [x] Create `HeaderBar.tsx` — breadcrumb, search trigger, Cmd+K button, connection pill, theme toggle
- [x] Redesign `Sidebar.tsx` — vertical icon nav with tooltip labels
  - [x] Icon-only mode (48px wide) as default on desktop
  - [x] Expanded mode (240px) on hover or pin
  - [x] Active route highlighting
  - [x] Section grouping: primary (Chat, Agents, Sessions, Channels), secondary (Tools, Skills, Cron), tertiary (Logs, Usage), bottom (Settings)
- [x] Mobile: hamburger → full-screen overlay nav
- [x] Migrate existing `Layout.tsx` → new `AppShell.tsx` (preserve connection logic)

#### Story 1.4: Command Palette
- [x] Install and configure cmdk via shadcn `Command` component
- [x] Create `CommandPalette.tsx` with global Cmd+K handler
- [x] Sections: Navigation (all routes), Recent Sessions, Actions (new session, toggle theme, disconnect)
- [x] Fuzzy search across commands
- [x] Keyboard nav: arrow keys, enter, escape

#### Story 1.5: Toast Notifications
- [x] Add `<Toaster />` from sonner to App
- [x] Create `src/lib/notifications.ts` helper wrapping toast API
- [x] Wire toasts to: connection state changes, RPC errors, agent completion, session CRUD

#### Story 1.6: Keyboard Shortcuts
- [x] Create `src/hooks/useKeyboardShortcuts.ts`
- [x] Register: `Cmd+K` (palette), `Cmd+N` (new session), `Cmd+Shift+S` (toggle sidebar), `Cmd+,` (settings), `Escape` (close/stop), `/` (focus input)
- [ ] Create `KeyboardShortcutsDialog.tsx` — searchable list of all shortcuts

---

## Phase B: Enhanced Chat

### Epic 2: Chat Experience

#### Story 2.1: Syntax-Highlighted Code Blocks
- [x] Integrate Shiki with react-markdown via custom CodeBlock component
- [x] Auto-detect language from fenced code block labels
- [x] "Copy" button on each code block (top-right, appears on hover)
- [x] Language label badge (top-left)
- [x] Dark/light theme-aware Shiki themes (github-dark-default / github-light-default)
- [x] Memoize completed code blocks during streaming (lazy-loaded highlighter singleton)

#### Story 2.2: Streaming UX Improvements
- [x] Animated blinking cursor at end of streaming text (StreamingCursor component)
- [x] Debounce markdown re-parsing (StreamingContent splits completed blocks from tail)
- [x] Show elapsed time during streaming ("Thinking... 3.2s")
- [x] Smooth auto-scroll with "Jump to bottom" FAB when scrolled up (React Virtuoso followOutput)

#### Story 2.3: Virtualized Message List
- [x] Replace `MessageList.tsx` with React Virtuoso
- [x] Handle dynamic heights (markdown, code blocks, tool calls)
- [ ] Paginated history: load last 50 messages, "Load more" on scroll-up
- [x] Smooth follow-output with atBottom state tracking

#### Story 2.4: Thinking Blocks
- [ ] Handle `agent.thinking` events in chat-store
- [ ] Render thinking blocks as collapsible dimmed sections
- [ ] Global toggle: show/hide thinking (store in theme-store)

#### Story 2.5: Rich Tool Visualization
- [x] Redesign `ToolCallBlock` with typed tool icons (terminal, file, globe, wrench, search, pencil)
- [x] Animated status transition: spinner → checkmark/error with color
- [x] Styled args/result with monospace font
- [x] `exec`: terminal-styled output block (dark bg, green text)
- [x] `read`/`write`/`edit`: file path badge with icon
- [x] Group 3+ consecutive tool calls under "Used N tools" collapsible
- [x] Copy result button per tool call

#### Story 2.6: Message Actions
- [x] Hover action: Copy message (on all messages)
- [ ] Regenerate (assistant only), Edit (user only), Delete
- [ ] "Copy conversation" (full thread as markdown)
- [ ] Edit message → re-send (conversation branch)

#### Story 2.7: Enhanced Chat Input
- [x] Auto-resizing textarea (max 6 lines → scroll)
- [ ] File attachment button + drag-and-drop zone
- [x] Model selector dropdown in input bar
- [ ] Slash command autocomplete (`/stop`, `/new`, `/model`, `/status`, `/help`)
- [x] Shift+Enter = newline, Enter = send
- [x] Suggested prompts for empty sessions (4 starter prompts)

#### Story 2.8: Artifacts / Split Pane
- [ ] Create `ArtifactPane.tsx` — resizable right pane
- [ ] Detect artifact content: HTML, SVG, Mermaid, large code blocks
- [ ] Render artifacts with live preview (HTML/SVG)
- [ ] Artifact tabs (multiple per conversation)
- [ ] Full-screen toggle
- [ ] Download artifact as file

---

## Phase C: Core Management Views

### Epic 3: Session Management

#### Story 3.1: Session Browser Page
- [x] Create `SessionsPage.tsx` at `/sessions`
- [x] Table view: Key, Agent, Channel, Last Message, Messages, Tokens, Created
- [x] Sortable columns
- [x] Filter bar: agent, channel, date range
- [x] Search by key or content
- [x] Bulk actions: delete, export

#### Story 3.2: Session Detail Panel
- [ ] Slide-in panel (or route `/sessions/:key`)
- [ ] Metadata card: key, agent, model, tokens, messages, compactions, timestamps
- [ ] Quick actions: open in chat, reset, delete, export (JSON/markdown)
- [ ] Visual timeline of messages with timestamps

#### Story 3.3: Sidebar Session Enhancements
- [x] Group sessions by date: Today, Yesterday, This Week, Older
- [x] Search/filter in sidebar
- [ ] Session rename (editable title)
- [ ] Pin favorites to top
- [ ] Last message preview under session name

### Epic 4: Agent Management

#### Story 4.1: Agent Overview Page
- [x] Create `AgentsPage.tsx` at `/agents`
- [x] Card grid of all configured agents
- [x] Per-agent card: name, model, status (idle/running), session count, tokens
- [x] Status badges with color coding
- [ ] Default agent indicator

#### Story 4.2: Agent Detail View
- [x] Route: `/agents/:id`
- [x] Workspace file browser (list all .md files)
- [x] Inline file viewer/editor with syntax highlighting
- [x] Model configuration panel: model, temperature, max tokens
- [x] Tool bindings display
- [x] Skill bindings display

#### Story 4.3: Agent Run Monitor
- [ ] Real-time running agents panel (can be on agents page or in header)
- [ ] Per-agent: session key, elapsed time, tokens, active tool
- [ ] Stop button per agent
- [ ] Run history (last N runs)

### Epic 5: Channel Management

#### Story 5.1: Channel Overview Page
- [x] Create `ChannelsPage.tsx` at `/channels`
- [x] Card grid: one card per channel
- [x] Status indicator: connected/disconnected/error
- [x] Platform icons and brand colors (Discord purple, Telegram blue, etc.)
- [x] Message count (24h), last activity

#### Story 5.2: Channel Detail
- [x] Configuration display (non-sensitive)
- [ ] Connection log / status history
- [ ] Recent messages through channel
- [ ] Test connection button

---

## Phase D: Operational Views

### Epic 6: Tools & Skills

#### Story 6.1: Tool Catalog Page
- [x] Create `ToolsPage.tsx` at `/tools`
- [x] Card grid grouped by category: Runtime, Filesystem, Exec, Web, Native, MCP
- [x] Per-tool: name, description, param schema, permission level
- [ ] Enable/disable status per agent
- [ ] "Try it" form for manual invocation

#### Story 6.2: Skill Manager Page
- [x] Create `SkillsPage.tsx` at `/skills`
- [x] List: name, description, trigger, gates, source
- [x] Precedence visualization
- [ ] Enable/disable toggle
- [x] Skill detail: rendered SKILL.md content
- [x] Gate status indicators

### Epic 7: Cron Scheduler

#### Story 7.1: Cron Dashboard
- [x] Create `CronPage.tsx` at `/cron`
- [x] Table: name, schedule (human-readable), next run, last run, status, agent
- [x] Create dialog: name, cron expression (with presets), message, target agent
- [x] Edit / delete existing jobs
- [x] Enable/disable toggle
- [x] "Run now" button

#### Story 7.2: Cron Run History
- [x] Expandable row showing run history per job
- [x] Per-run: timestamp, duration, status, output summary
- [ ] Date range filter

### Epic 8: Log Viewer

#### Story 8.1: Log Viewer Page
- [x] Create `LogsPage.tsx` at `/logs`
- [x] Real-time log stream via WebSocket events
- [x] Level-based color coding: DEBUG (gray), INFO (blue), WARN (yellow), ERROR (red)
- [x] Filter by: level, source module, search text
- [ ] Virtualized list (handle thousands of entries)
- [x] Auto-scroll (tail) toggle
- [x] Relative/absolute timestamp toggle
- [x] Copy log line, pause/resume, export as text

---

## Phase E: Analytics & Configuration

### Epic 9: Usage Analytics

#### Story 9.1: Usage Dashboard
- [x] Create `UsagePage.tsx` at `/usage`
- [x] Summary cards: total tokens (24h/7d/30d), estimated cost, messages, avg response time
- [x] Line chart: token usage over time (Recharts)
- [x] Pie chart: breakdown by model
- [x] Bar chart: breakdown by agent
- [x] Table: top sessions by token usage

#### Story 9.2: Cost Estimation
- [ ] Per-model pricing config in settings
- [ ] Cost per session, per time period
- [ ] Budget alert thresholds (optional)

### Epic 10: Settings

#### Story 10.1: Settings Page Shell
- [x] Create `SettingsPage.tsx` at `/settings`
- [x] Tabbed layout: General, Models, Appearance, Shortcuts, Data, About

#### Story 10.2: General Tab
- [x] Gateway URL and token management
- [ ] Default agent / model selection
- [x] Auto-reconnect toggle

#### Story 10.3: Models Tab
- [x] Provider list (Anthropic, OpenAI, Google, Ollama)
- [ ] API key management (masked input, validation)
- [ ] Per-model: temperature, max tokens, pricing
- [ ] Test connection per provider

#### Story 10.4: Appearance Tab
- [x] Theme: Light / Dark / System
- [ ] Font size adjustment
- [ ] Code block theme selection
- [ ] Compact / comfortable density toggle

#### Story 10.5: Shortcuts Tab
- [x] List all shortcuts with current bindings
- [ ] Search shortcuts

#### Story 10.6: Data Tab
- [x] Export / import conversations
- [x] Clear local data
- [ ] Storage usage indicator

#### Story 10.7: About Tab
- [x] Version info
- [x] Health check results
- [x] Links: docs, GitHub, feedback

### Epic 11: Execution Approvals

#### Story 11.1: Approval Notifications
- [x] Handle `exec.approval.requested` events
- [x] Inline approval card in chat: tool name, args, [Approve] [Deny]
- [x] Toast notification + sidebar badge for pending approvals
- [x] Timeout countdown indicator

#### Story 11.2: Approval Queue
- [x] Approval queue view (accessible from sidebar or header badge)
- [ ] Pending approvals list: tool, args, agent, session, timestamp
- [ ] Batch approve/deny
- [ ] Past approvals history

---

## Phase F: Polish

### Epic 12: Mobile & Responsive

#### Story 12.1: Responsive Layout
- [x] Sidebar: hidden on mobile, hamburger reveal
- [x] Chat: full-width with bottom input
- [ ] Session list: bottom sheet on mobile
- [ ] Settings / detail views: full-screen on small viewports
- [ ] Test on 375px, 768px, 1024px, 1440px breakpoints

#### Story 12.2: Touch Optimizations
- [ ] 44px minimum tap targets
- [ ] Swipe right = sidebar reveal
- [ ] Swipe left on session = delete
- [ ] Long-press context menu on messages

### Epic 13: Accessibility

#### Story 13.1: Semantic HTML & ARIA
- [x] Landmark regions: nav, main, aside, header
- [ ] ARIA live regions for streaming messages
- [ ] Focus trap in modals, restore on close
- [x] Skip-to-content link
- [ ] Heading hierarchy audit

#### Story 13.2: Keyboard Accessibility
- [x] Tab-navigable for all interactive elements
- [x] Visible focus indicators
- [ ] Arrow key navigation in lists
- [x] Escape closes overlays

#### Story 13.3: Visual Accessibility
- [ ] Color contrast audit (4.5:1 minimum)
- [x] Status conveyed by icon + text, not just color
- [ ] `prefers-reduced-motion` support

### Epic 14: Performance

#### Story 14.1: Loading States
- [ ] Skeleton loaders: session list, chat history, agent cards, channel cards
- [ ] Shimmer animation
- [ ] Top-of-page progress bar (thin line)

#### Story 14.2: Optimistic Updates
- [x] Message send: show before server ack
- [x] Session delete: remove from list immediately
- [x] Settings toggle: apply immediately, rollback on error

#### Story 14.3: Code Splitting
- [x] `React.lazy` for all route pages
- [x] Lazy-load Shiki (first code block)
- [x] Lazy-load Recharts (`/usage` only)
- [ ] Lazy-load cmdk (first Cmd+K)

#### Story 14.4: Memoization
- [x] `React.memo` on MessageBubble, ToolCallBlock, SessionItem
- [x] Memoize completed markdown blocks during streaming
- [ ] Cache session list across navigations

---

## Summary

| Phase | Epics | Stories | Subtasks |
|-------|-------|---------|----------|
| A: Shell | 1 | 6 | ~25 |
| B: Chat | 1 | 8 | ~35 |
| C: Management | 3 | 8 | ~30 |
| D: Operations | 3 | 6 | ~25 |
| E: Analytics | 3 | 10 | ~30 |
| F: Polish | 3 | 7 | ~25 |
| **Total** | **14** | **45** | **~170** |

---

*Last updated: 2026-03-04*
