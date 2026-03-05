// Wire protocol types mirroring Kotlin sealed classes

export interface FrameRequest {
  type: 'req';
  id: string;
  method: string;
  params: Record<string, unknown>;
}

export interface FrameResponse {
  type: 'res';
  id: string;
  ok: boolean;
  payload: Record<string, unknown>;
}

export interface FrameEvent {
  type: 'event';
  id: string;
  event: string;
  payload: Record<string, unknown>;
}

export type Frame = FrameRequest | FrameResponse | FrameEvent;

// RPC response payloads

export interface HelloPayload {
  sessionId: string;
  message: string;
  setupRequired?: boolean;
  onboardingPhase?: string;
}

export interface AgentRunPayload {
  started: boolean;
  key: string;
  model: string;
}

export interface AgentStopPayload {
  stopped: boolean;
  key: string;
}

export interface AgentStatusPayload {
  key: string;
  isRunning: boolean;
  elapsedMs: number;
}

// Setup payloads

export interface SetupStatusPayload {
  setupRequired: boolean;
  onboardingPhase: string;
  hasAnthropicKey: boolean;
  hasOpenaiKey: boolean;
  hasAuthToken: boolean;
  defaultModel: string;
}

export interface SetupConfigureParams {
  anthropicKey?: string;
  openaiKey?: string;
  token?: string;
  model?: string;
}

// Event payloads

export interface TokenEventPayload {
  key: string;
  text: string;
}

export interface ToolStartPayload {
  key: string;
  name: string;
  args: Record<string, unknown>;
}

export interface ToolResultPayload {
  key: string;
  name: string;
  result: string;
  durationMs: number;
}

export interface CompletePayload {
  key: string;
  finalText: string;
  usage: TokenUsage;
}

export interface ErrorPayload {
  key: string;
  message: string;
  code?: string;
}

export interface TokenUsage {
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
}

// Session types

export interface SessionMeta {
  key: string;
  agentId: string;
  channel?: string;
  modelId?: string;
  createdAt: string;
  lastMessageAt?: string;
  messageCount: number;
  compactionCount: number;
  totalTokens: number;
  fileId: string;
}

export interface SessionMessage {
  type: 'user' | 'assistant' | 'tool_call' | 'tool_result' | 'system_event' | 'compaction_summary';
  id: string;
  timestamp: string;
  // User message fields
  senderId?: string;
  senderName?: string;
  text?: string;
  channel?: string;
  // Assistant message fields
  model?: string;
  usage?: TokenUsage;
  // Tool call fields
  toolName?: string;
  args?: Record<string, unknown>;
  requestId?: string;
  // Tool result fields
  result?: string;
  durationMs?: number;
  // System event fields
  event?: string;
  data?: Record<string, unknown>;
  // Compaction fields
  summary?: string;
  messagesCompacted?: number;
  tokensBefore?: number;
  tokensAfter?: number;
}

// Chat UI types

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant' | 'approval';
  content: string;
  timestamp: string;
  isStreaming?: boolean;
  model?: string;
  usage?: TokenUsage;
  toolCalls?: ToolCallInfo[];
  approval?: ApprovalRequest;
}

export interface ApprovalRequest {
  requestId: string;
  toolName: string;
  args: Record<string, unknown>;
  sessionKey: string;
  agentId?: string;
  status: 'pending' | 'approved' | 'denied' | 'expired';
  timeoutMs?: number;
  requestedAt: string;
}

export interface ToolCallInfo {
  name: string;
  args: Record<string, unknown>;
  result?: string;
  durationMs?: number;
  status: 'running' | 'complete' | 'error';
}
