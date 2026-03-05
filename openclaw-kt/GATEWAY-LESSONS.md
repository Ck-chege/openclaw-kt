# Gateway Lessons: JS → Kotlin

> Comparing the production JS gateway (`src/gateway/`) against `openclaw-kt/gateway-core/`  
> to surface concrete gaps, design patterns worth copying, and Kotlin-specific improvements.

---

## 1. Credential Resolution — The Biggest Gap

### What JS does

`credentials.ts` has a dedicated `resolveGatewayCredentialsFromValues()` that:
1. Reads `OPENCLAW_GATEWAY_TOKEN` from env
2. Reads `gateway.auth.token` from config
3. Picks between them based on a **configurable precedence** (`env-first` vs `config-first`)
4. Supports both local mode (gateway server) and remote mode (CLI client) with different precedence rules
5. Handles legacy env var names (`CLAWDBOT_GATEWAY_TOKEN`) with a flag

For **model API keys** the same pattern applies: secrets are always resolved via a `resolveSecretInputRef()` utility that checks the config object first, then env.

### What Kotlin does

`ProviderFactory` only calls `System.getenv("ANTHROPIC_API_KEY")`. The keys stored in `GatewayConfig.secrets.anthropicApiKey` (written by `setup.configure`) are never read.

### The Fix

Create a `SecretResolver` interface and inject it into `ProviderFactory`:

```kotlin
fun interface SecretResolver {
    fun resolve(configValue: String?, envKey: String): String?
}

// Implementation: config-first, env fallback
val secretResolver = SecretResolver { configValue, envKey ->
    configValue?.takeIf { it.isNotBlank() } ?: System.getenv(envKey)
}
```

Pass it to `ProviderFactory` on construction. Every secret lookup uses it.

### Lesson

> **Never hardcode `System.getenv()` inside a factory.** Inject a resolver so the source (config, env, vault) is swappable. This is the single biggest thing blocking the Kotlin gateway from working end-to-end.

---

## 2. Config Reload — Far More Than a File Watcher

### What JS does

`config-reload.ts` is a proper state machine:

```
File change → debounce(300ms) → readSnapshot() → diffConfigPaths() → buildGatewayReloadPlan() → hot or restart
```

Key pieces:

**`diffConfigPaths(prev, next)`** — walks both config objects recursively, returns the list of dotted paths that changed (`"gateway.auth.token"`, `"agents.0.model"`, etc.).

**`buildGatewayReloadPlan(changedPaths)`** — maps changed paths to a reload decision:
- Some paths → hot reload (just swap the in-memory config)
- Others (e.g. changing the bind port) → must restart the whole process

**State machine flags:**
```typescript
let running = false   // currently processing a reload
let pending = false   // change arrived while running, must re-run
let stopped = false   // watcher has been explicitly stopped
let restartQueued = false  // only queue one restart at a time
```

**Missing file retries** — if the file disappears during an atomic write (editor saves via tmp-rename), retries up to 2 times with 150ms delay before giving up.

### What Kotlin does

`ConfigManager.reload()` reads the file, parses it, and replaces `_config.value`. That's it. No diff, no plan, no retry, no distinction between HOT and HYBRID.

### The Fix

1. Port `diffConfigPaths` as a recursive `fun diffConfigPaths(prev: Any?, next: Any?, prefix: String = ""): List<String>` using reflection or kotlinx.serialization's JSON tree.
2. Create `GatewayReloadPlan` — a data class with `restartGateway: Boolean` + `restartReasons: List<String>`.
3. Implement `buildGatewayReloadPlan(changedPaths: List<String>)` that classifies each changed key.
4. Add the `running`/`pending`/`stopped` state flags to prevent concurrent reload races.
5. Add missing-file retry (2 attempts, 150ms apart) before treating deletion as intentional.

### Lesson

> **Reload is not a file watch.** It's a pipeline: detect → diff → classify → decide → act. Without the diff and plan steps, every config change looks the same and you can't distinguish "update an API key" from "change the port".

---

## 3. WS Connection — Sequential vs Concurrent Frame Handling

### What JS does

After handshake, every request frame is dispatched in its **own async context**:

```typescript
void (async () => {
  await handleGatewayRequest({ req, respond, client, ... });
})().catch((err) => {
  respond(false, undefined, errorShape(ErrorCodes.UNAVAILABLE, formatError(err)));
});
```

This means:
- A `chat.run` that streams for 30 seconds does **not** block subsequent requests on the same connection
- The client can send `chat.abort` while the agent is still running and it will be processed immediately
- Heartbeat pings still work during long operations

### What Kotlin does

`for (message in wsSession.incoming)` — sequential. One message processed at a time.

### The Fix

```kotlin
socket.on("message") { data ->
    scope.launch {
        handleRequest(data)
    }
}
```

For Ktor WebSockets, launch each incoming frame as a child coroutine on the connection scope. Add a semaphore if you need to cap concurrent requests per connection.

### Lesson

> **WebSocket frames must be dispatched concurrently.** A streaming agent run and an abort request will arrive on the same connection. If you process them sequentially, `abort` will never be seen until the run finishes.

---

## 4. Auth — Five Things Kotlin Is Missing

### 4a. Rate Limiting on Auth Failures

JS has `AuthRateLimiter` — per-IP failure tracking on both HTTP and WS connections. Failed auth attempts are `recordFailure(ip, scope)`; success is `reset(ip, scope)`. When the threshold is hit, `retryAfterMs` is returned in the error response.

**Kotlin gap:** No rate limiting on auth. A bad actor can brute-force the gateway token.

### 4b. Flood Guard on Unauthorized Requests

`UnauthorizedFloodGuard` — after 10 unauthorized `req` frames on a live connection, the connection is forcibly closed. Log suppression (only logs every 100th) prevents log spam.

```kotlin
class UnauthorizedFloodGuard(val closeAfter: Int = 10, val logEvery: Int = 100) {
    private var count = 0
    fun registerUnauthorized(): Decision { ... }
    fun reset() { count = 0 }
}
```

**Kotlin gap:** No guard. A connected client with invalid scopes can spam the connection indefinitely.

### 4c. Tailscale Auth

JS supports `allowTailscale = true` in config, which:
1. Verifies the request comes through a Tailscale proxy (loopback remote + forwarded headers)
2. Calls `tailscaleWhois(ip)` to verify the user identity
3. Validates the whois identity matches the `Tailscale-User-Login` header

This lets users on a Tailnet connect without a gateway token.

**Kotlin gap:** Not present. Low priority but worth knowing it exists.

### 4d. Trusted Proxy Auth

JS supports a `trusted-proxy` auth mode where a reverse proxy injects a trusted `X-User-Id` header and the gateway trusts it. Supports allowlisted users, required headers, and source IP validation.

**Kotlin gap:** Not present.

### 4e. Device Identity — Node UUID Is Wrong

JS: The `device.id` in the connect params is **derived from the public key**: `deriveDeviceIdFromPublicKey(device.publicKey)`. The gateway verifies `derivedId === device.id`. A client cannot choose an arbitrary device ID — it's cryptographically bound to the key.

Additionally, the device signature is validated with:
- Timestamp skew check (±2 minutes)
- Nonce anti-replay (`connectNonce` is server-generated per connection)
- V2/V3 payload format detection

**Kotlin gap (G-3 in audit):** `deviceId = UUID.randomUUID()` is generated server-side per connection. This is not how it works — the client must present its own signed device identity, and the server derives the ID from the public key to verify it.

### Lesson

> Auth in the JS gateway is a layered security model: shared-secret OR device-token OR Tailscale OR trusted-proxy, each with its own guard. The Kotlin port has only the outermost layer (shared secret). The device identity system is the most important to get right — it's what makes operator role upgrades, scope grants, and pairing work correctly.

---

## 5. Protocol Negotiation

### What JS does

On the `connect` handshake, the client sends `minProtocol` and `maxProtocol`. The server checks:

```typescript
if (maxProtocol < PROTOCOL_VERSION || minProtocol > PROTOCOL_VERSION) {
    // reject with protocol-mismatch error
}
```

This means old clients can be gracefully rejected with a clear error rather than silently misbehaving.

### What Kotlin does

No protocol version check. Any client that sends a valid connect frame is accepted regardless of version.

### The Fix

Add `protocolVersion: Int = PROTOCOL_VERSION` to `GatewayServer`, validate `minProtocol..maxProtocol` range on handshake.

---

## 6. Scope System — Read/Write/Admin vs Binary Role

### What JS does

Fine-grained 5-scope model:

| Scope | Methods |
|-------|---------|
| `operator.read` | `health`, `sessions.list`, `models.list`, `config.get`, ... |
| `operator.write` | `chat.send`, `agent`, `wake`, `send`, ... |
| `operator.admin` | `config.apply`, `sessions.delete`, `cron.add`, ... |
| `operator.approvals` | `exec.approval.*` |
| `operator.pairing` | `node.pair.*`, `device.token.*` |

Rules:
- `admin` implies all other scopes (wildcard)
- `write` implies `read`
- Unknown methods default to `admin` (default-deny for unclassified)
- `node` role only has access to `node.*` methods

### What Kotlin does

`ClientRole` sealed class with `Operator` and `Node`. Operator has `read`, `write`, `admin` as a set of strings, but there's no `APPROVALS` or `PAIRING` scope, and there's no `isGatewayMethodClassified` default-deny safety net.

### The Fix

1. Add `APPROVALS_SCOPE` and `PAIRING_SCOPE` to match the JS model.
2. Add a `classifiedMethods: Set<String>` that lists every known method.
3. If a method is not in the set and the client doesn't have `ADMIN_SCOPE`, reject it — default-deny for unclassified.
4. Enforce that `write` implies `read` in `authorizeOperatorScopesForMethod`.

---

## 7. Handshake Timer

### What JS does

Before the `connect` frame arrives, a timer is set:

```typescript
const handshakeTimer = setTimeout(() => {
  close(1000, "handshake timeout");
}, HANDSHAKE_TIMEOUT_MS);
```

`clearHandshakeTimer()` is called when `hello-ok` is sent. If the client connects but never sends `connect`, the socket is closed after the timeout.

### What Kotlin does

No timeout. A client that opens a WebSocket but never sends `connect` will hold the connection indefinitely.

### The Fix

```kotlin
val handshakeJob = scope.launch {
    delay(HANDSHAKE_TIMEOUT_MS)
    if (!handshakeComplete) close(1000, "handshake timeout")
}
// On successful connect:
handshakeJob.cancel()
```

---

## 8. Presence Tracking on Connect

### What JS does

On successful `hello-ok`, the server calls `upsertPresence(presenceKey, { ... })` with:
- `host`, `ip`, `version`, `platform`, `role`, `scopes`, `deviceId`, `mode`

`incrementPresenceVersion()` bumps a monotonic counter. All connected clients receive a `system-presence` event when presence changes. This is how the Control UI shows "2 clients connected".

The connect response also includes a full **gateway snapshot** (health state, presence list, state versions) so the client gets the full picture immediately without a round-trip.

### What Kotlin does

`SessionRegistry` maps session keys to WS sessions. No presence model, no snapshot on connect.

### The Fix

Add a `PresenceRegistry` alongside `SessionRegistry`:

```kotlin
data class ClientPresence(
    val connId: String,
    val host: String,
    val role: GatewayRole,
    val scopes: List<String>,
    val ip: String?,
    val platform: String?,
    val connectedAt: Instant
)
```

Include a `presence` list in the `hello-ok` response. Broadcast `system-presence` events on connect/disconnect.

---

## 9. Config Write Correctness (G-1 Fix Reference)

### What JS does

`config.apply` takes a base hash (SHA256 of current config bytes). If the hash doesn't match the current file, the write is rejected — preventing concurrent write conflicts.

`config.patch` applies a deep merge of just the changed keys rather than replacing the whole file.

Neither operation silently drops fields — the HOCON renderer is tested to round-trip every known config key.

### What Kotlin does

`ConfigWriter.renderHocon` is missing `tools`, `agents.list`, and `byProvider` sections. Config round-trips are lossy.

### The Fix

For each top-level config section, add a corresponding render block in `renderHocon`. Write tests that:
1. Create a config with every field populated
2. Render it to HOCON
3. Parse the HOCON back
4. Assert the parsed config equals the original

This is the contract the JS config tests enforce.

---

## 10. Origin Check for Browser Clients

### What JS does

For any connection with an `Origin` header (browser clients), the gateway checks:
1. Is the origin allowed by `gateway.controlUi.allowedOrigins`?
2. Is the origin a loopback address?
3. Host-header fallback (with security audit logging) if `dangerouslyAllowHostHeaderOriginFallback` is set

This prevents CSRF attacks where a malicious website opens a WebSocket to the local gateway.

### What Kotlin does

No origin check. Any page on any origin can connect to the gateway from a browser.

### The Fix

Add an `OriginChecker` called in `WebSocketHandler` before accepting the handshake:

```kotlin
fun checkBrowserOrigin(
    requestOrigin: String?,
    requestHost: String?,
    allowedOrigins: List<String>?,
    isLocalClient: Boolean
): OriginCheckResult
```

---

## Priority Order for Kotlin

| Priority | Lesson | Impact |
|----------|--------|--------|
| 1 | **Credential resolver** — config-first then env | Blocker: gateway can't use UI-configured keys |
| 2 | **Concurrent frame dispatch** — launch each req | Blocker: abort won't work during stream |
| 3 | **Config diff + reload plan** | Core: HOT vs HYBRID distinction is meaningless without it |
| 4 | **Handshake timer** | Security: indefinite connection holding |
| 5 | **Unauthorized flood guard** | Security: DoS vector on connected clients |
| 6 | **Auth rate limiter** | Security: brute-force protection |
| 7 | **Config writer round-trip test** | Correctness: data loss on every config save |
| 8 | **Presence tracking + snapshot on connect** | UX: Control UI can't show connected clients |
| 9 | **Protocol version negotiation** | Compatibility: forward/backward compatibility |
| 10 | **Origin check** | Security: CSRF for browser clients |
| 11 | **Scope system expansion** (approvals, pairing, default-deny) | Security: least-privilege model |
| 12 | **Device identity from public key** | Security: cryptographic device binding |
