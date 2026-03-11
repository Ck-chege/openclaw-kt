# KClaw

Kotlin-native rebuild of [OpenClaw](https://github.com/openclaw/openclaw) — an open-source personal AI assistant platform. Built with **Ktor** (server) and **Koog** (JetBrains AI agent framework).

This is not a port — it reimagines KClaw's architecture using Kotlin's strengths: sealed classes for the wire protocol, coroutines for concurrency, Flow for streaming, and type-safe DSLs for configuration.

## Requirements

- **JDK 17+** (21 recommended)
- **Gradle 8.x** (wrapper included)

## Build

```bash
./gradlew build
```

## Run

```bash
./gradlew :app:run
```

## Configuration

Config file location: `~/.kclaw/kclaw.conf` (HOCON format).

> Runtime data now lives under `.kclaw`.

### Environment Variables

| Variable | Description |
|---|---|
| `ANTHROPIC_API_KEY` | Anthropic API key for Claude models |
| `OPENAI_API_KEY` | OpenAI API key (used for embeddings by default) |

These can also be set in the config file under `secrets {}`.

## Endpoints

| Endpoint | Description |
|---|---|
| `ws://127.0.0.1:18789/ws` | WebSocket (wire protocol) |
| `http://127.0.0.1:18789/health` | Health check |

Port and bind address are configurable in `kclaw.conf`:

```hocon
gateway {
    port = 18789
    bind = "127.0.0.1"
}
```

## Project Structure

```
kclaw/
  app/             # Main entry point
  gateway-core/    # Ktor server, WebSocket wire protocol, config
  agent-engine/    # Koog AI agent wrappers, workspace loader, prompt assembly
  session-store/   # JSONL persistence + Postgres checkpointing
  protocol/        # Multiplatform wire protocol (sealed class frames)
  channels/        # Platform connectors (discord, telegram, slack, etc.)
  skills/          # SKILL.md parser, skill injection
  tools/           # Shell exec, filesystem, browser automation
  plugins/         # Hook system via Koog pipeline
  cron/            # Coroutine-based scheduler
  orchestrator/    # Multi-agent spawning, A2A protocol
```

## License

See [LICENSE](LICENSE) for details.
