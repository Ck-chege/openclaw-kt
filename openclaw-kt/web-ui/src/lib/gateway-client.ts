import type { Frame, FrameResponse, FrameEvent } from '@/types/protocol';

type EventHandler = (payload: Record<string, unknown>) => void;
type ConnectionHandler = (state: 'connecting' | 'connected' | 'disconnected', detail?: string) => void;

interface PendingRequest {
  resolve: (payload: Record<string, unknown>) => void;
  reject: (error: Error) => void;
  timeout: ReturnType<typeof setTimeout>;
}

export class GatewayClient {
  private ws: WebSocket | null = null;
  private pending = new Map<string, PendingRequest>();
  private eventHandlers = new Map<string, Set<EventHandler>>();
  private connectionHandlers = new Set<ConnectionHandler>();
  private reconnectAttempt = 0;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private _wsSessionId: string | null = null;
  private _setupRequired = false;
  private _onboardingPhase: string | null = null;
  private _url: string;
  private _token?: string;
  private _shouldReconnect = false;
  private _lastDisconnectReason: string | null = null;
  private _role: 'operator' | 'webchat' | 'node' = 'operator';

  constructor(url: string, token?: string, role: 'operator' | 'webchat' | 'node' = 'operator') {
    this._url = url;
    this._token = token;
    this._role = role;
  }

  get wsSessionId() {
    return this._wsSessionId;
  }

  get setupRequired() {
    return this._setupRequired;
  }

  get onboardingPhase() {
    return this._onboardingPhase;
  }

  get isConnected() {
    return this.ws?.readyState === WebSocket.OPEN && this._wsSessionId !== null;
  }

  connect(): void {
    this._shouldReconnect = true;
    this.doConnect();
  }

  disconnect(): void {
    this._shouldReconnect = false;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    if (this.ws) {
      this.ws.close(1000, 'Client disconnect');
      this.ws = null;
    }
    this._wsSessionId = null;
    this.notifyConnection('disconnected');
  }

  async request(method: string, params: Record<string, unknown> = {}): Promise<Record<string, unknown>> {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      throw new Error('Not connected');
    }

    const id = crypto.randomUUID();
    const frame: Frame = { type: 'req', id, method, params };

    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        this.pending.delete(id);
        reject(new Error(`RPC timeout: ${method}`));
      }, 30000);

      this.pending.set(id, { resolve, reject, timeout });
      this.ws!.send(JSON.stringify(frame));
    });
  }

  on(event: string, handler: EventHandler): void {
    if (!this.eventHandlers.has(event)) {
      this.eventHandlers.set(event, new Set());
    }
    this.eventHandlers.get(event)!.add(handler);
  }

  off(event: string, handler: EventHandler): void {
    this.eventHandlers.get(event)?.delete(handler);
  }

  onConnection(handler: ConnectionHandler): () => void {
    this.connectionHandlers.add(handler);
    return () => this.connectionHandlers.delete(handler);
  }

  get lastDisconnectReason() {
    return this._lastDisconnectReason;
  }

  private doConnect(): void {
    this.notifyConnection('connecting');

    try {
      this.ws = new WebSocket(this._url);
    } catch {
      this.scheduleReconnect();
      return;
    }

    this.ws.onopen = () => {
      this.reconnectAttempt = 0;
      // Send connect.hello
      const helloId = crypto.randomUUID();
      const hello: Frame = {
        type: 'req',
        id: helloId,
        method: 'connect.hello',
        params: {
          ...(this._token ? { token: this._token } : {}),
          role: this._role,
        },
      };

      const timeout = setTimeout(() => {
        this.pending.delete(helloId);
        this.ws?.close();
      }, 10000);

      this.pending.set(helloId, {
        resolve: (payload) => {
          this._wsSessionId = payload.sessionId as string;
          this._setupRequired = (payload.setupRequired as boolean) ?? false;
          this._onboardingPhase = (payload.onboardingPhase as string) ?? null;
          this._lastDisconnectReason = null;
          this.notifyConnection('connected');
        },
        reject: (err) => {
          this._lastDisconnectReason = err.message;
          this.ws?.close(1008, err.message);
        },
        timeout,
      });

      this.ws!.send(JSON.stringify(hello));
    };

    this.ws.onmessage = (ev) => {
      try {
        const frame = JSON.parse(ev.data as string) as Frame;
        this.handleFrame(frame);
      } catch {
        // ignore malformed frames
      }
    };

    this.ws.onclose = (ev) => {
      this._wsSessionId = null;
      this._setupRequired = false;
      this._onboardingPhase = null;
      const reason = ev.reason || 'Connection closed';
      this._lastDisconnectReason = reason;
      this.rejectAllPending(reason);
      this.notifyConnection('disconnected', reason);
      if (this._shouldReconnect) {
        this.scheduleReconnect();
      }
    };

    this.ws.onerror = () => {
      // onclose will fire after this
    };
  }

  private handleFrame(frame: Frame): void {
    if (frame.type === 'res') {
      const res = frame as FrameResponse;
      const pending = this.pending.get(res.id);
      if (pending) {
        clearTimeout(pending.timeout);
        this.pending.delete(res.id);
        if (res.ok) {
          pending.resolve(res.payload);
        } else {
          const message = (res.payload.error as string) || 'RPC error';
          const code = (res.payload.code as string) || undefined;
          const decorated = code ? `${message} [${code}]` : message;
          pending.reject(new Error(decorated));
        }
      }
    } else if (frame.type === 'event') {
      const evt = frame as FrameEvent;
      const handlers = this.eventHandlers.get(evt.event);
      if (handlers) {
        for (const handler of handlers) {
          try {
            handler(evt.payload);
          } catch {
            // don't let handler errors break the loop
          }
        }
      }
    }
  }

  private scheduleReconnect(): void {
    if (!this._shouldReconnect) return;
    const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempt), 30000);
    this.reconnectAttempt++;
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.doConnect();
    }, delay);
  }

  private rejectAllPending(message: string): void {
    for (const [id, pending] of this.pending) {
      clearTimeout(pending.timeout);
      pending.reject(new Error(message));
      this.pending.delete(id);
    }
  }

  private notifyConnection(state: 'connecting' | 'connected' | 'disconnected', detail?: string): void {
    for (const handler of this.connectionHandlers) {
      try {
        handler(state, detail);
      } catch {
        // ignore
      }
    }
  }
}
