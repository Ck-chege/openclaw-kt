#!/usr/bin/env node
/**
 * Quick WebSocket test client for the OpenClaw gateway agent run.
 */

import { WebSocket } from 'ws';
import { randomUUID } from 'crypto';

const WS_URL = 'ws://127.0.0.1:18789/';
const AUTH_TOKEN = 'test-token';

const ws = new WebSocket(WS_URL);

function send(obj) {
  ws.send(JSON.stringify(obj));
}

ws.on('open', () => {
  // 1. Authenticate
  send({
    type: 'req',
    id: randomUUID(),
    method: 'connect.hello',
    params: { token: AUTH_TOKEN, role: 'operator' }
  });
});

let authenticated = false;
let runStarted = false;

ws.on('message', (raw) => {
  const data = JSON.parse(raw.toString());

  if (!authenticated) {
    console.log('[AUTH]', JSON.stringify(data, null, 2));
    if (!data.ok) {
      console.log('Authentication failed!');
      ws.close();
      return;
    }
    authenticated = true;

    // 2. Start agent run
    send({
      type: 'req',
      id: randomUUID(),
      method: 'agent.run',
      params: {
        key: 'test:main:*:main',
        message: 'Hello! Please respond with a short greeting.'
      }
    });
    return;
  }

  if (!runStarted && data.type === 'res') {
    console.log('\n[RUN STARTED]', JSON.stringify(data, null, 2));
    runStarted = true;
    console.log('\n--- Streaming events ---');
    return;
  }

  // 3. Handle streaming events
  const eventType = data.event || data.type || 'unknown';

  switch (eventType) {
    case 'agent.token':
      process.stdout.write(data.payload?.text || '');
      break;
    case 'agent.complete':
      console.log('\n\n[COMPLETE]', JSON.stringify(data, null, 2));
      ws.close();
      break;
    case 'agent.error':
      console.log('\n\n[ERROR]', JSON.stringify(data, null, 2));
      ws.close();
      break;
    case 'agent.tool.start':
      console.log(`\n[TOOL START] ${data.payload?.name}`);
      break;
    case 'agent.tool.result':
      console.log(`[TOOL RESULT] ${data.payload?.name}`);
      break;
    default:
      console.log('\n[EVENT]', JSON.stringify(data, null, 2));
  }
});

ws.on('error', (err) => {
  console.error('WebSocket error:', err.message);
});

ws.on('close', () => {
  console.log('\nConnection closed.');
  process.exit(0);
});

// Timeout after 60 seconds
setTimeout(() => {
  console.log('\nTimeout — closing.');
  ws.close();
  process.exit(1);
}, 60000);
