#!/usr/bin/env python3
"""Quick WebSocket test client for the KClaw gateway agent run."""

import asyncio
import json
import uuid
import websockets

WS_URL = "ws://127.0.0.1:18789/"
AUTH_TOKEN = "test-token"

async def main():
    async with websockets.connect(WS_URL) as ws:
        # 1. Authenticate
        hello = {
            "type": "req",
            "id": str(uuid.uuid4()),
            "method": "connect.hello",
            "params": {"token": AUTH_TOKEN, "role": "operator"}
        }
        await ws.send(json.dumps(hello))
        resp = json.loads(await ws.recv())
        print(f"[AUTH] {json.dumps(resp, indent=2)}")

        if not resp.get("ok"):
            print("Authentication failed!")
            return

        # 2. Start agent run
        run_req = {
            "type": "req",
            "id": str(uuid.uuid4()),
            "method": "agent.run",
            "params": {
                "key": "test:main:*:main",
                "message": "Hello! Please respond with a short greeting."
            }
        }
        await ws.send(json.dumps(run_req))
        resp = json.loads(await ws.recv())
        print(f"\n[RUN STARTED] {json.dumps(resp, indent=2)}")

        # 3. Listen for streaming events
        print("\n--- Streaming events ---")
        try:
            async for msg in ws:
                data = json.loads(msg)
                event_type = data.get("event", data.get("type", "unknown"))

                if event_type == "agent.token":
                    # Print token deltas inline
                    text = data.get("payload", {}).get("text", "")
                    print(text, end="", flush=True)
                elif event_type == "agent.complete":
                    print(f"\n\n[COMPLETE] {json.dumps(data, indent=2)}")
                    break
                elif event_type == "agent.error":
                    print(f"\n\n[ERROR] {json.dumps(data, indent=2)}")
                    break
                elif event_type == "agent.tool.start":
                    print(f"\n[TOOL START] {data.get('payload', {}).get('name', '?')}")
                elif event_type == "agent.tool.result":
                    print(f"[TOOL RESULT] {data.get('payload', {}).get('name', '?')}")
                else:
                    print(f"\n[EVENT] {json.dumps(data, indent=2)}")
        except asyncio.TimeoutError:
            print("\nTimeout waiting for events")
        except websockets.exceptions.ConnectionClosed:
            print("\nConnection closed")

if __name__ == "__main__":
    asyncio.run(main())
