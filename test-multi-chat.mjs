#!/usr/bin/env node
import { WebSocket } from 'ws';
import { randomUUID } from 'crypto';

const ws = new WebSocket('ws://127.0.0.1:18789/');

const messages = [
  'What tools do you have available?',
  'List the files in the current directory.',
  'What is 2 + 2? Show your reasoning.',
  'Write a haiku about coding in Kotlin.'
];

let msgIdx = 0;
let authenticated = false;

function send(obj) { ws.send(JSON.stringify(obj)); }

function sendNextMessage() {
  if (msgIdx >= messages.length) {
    console.log('\n\n=== ALL MESSAGES SENT & COMPLETED ===');
    setTimeout(() => { ws.close(); process.exit(0); }, 2000);
    return;
  }
  const msg = messages[msgIdx];
  console.log('\n\n========================================');
  console.log(`>>> [${msgIdx + 1}/${messages.length}] SENDING: ${msg}`);
  console.log('========================================');
  send({
    type: 'req',
    id: randomUUID(),
    method: 'agent.run',
    params: { key: 'test:main:*:main', message: msg }
  });
}

ws.on('open', () => {
  send({
    type: 'req',
    id: randomUUID(),
    method: 'connect.hello',
    params: { token: 'test-token', role: 'operator' }
  });
});

ws.on('message', (raw) => {
  const data = JSON.parse(raw.toString());

  if (!authenticated) {
    console.log('[AUTH] OK');
    authenticated = true;
    sendNextMessage();
    return;
  }

  if (data.type === 'res' && data.payload?.started) {
    return;
  }

  const evt = data.event || data.type;
  switch (evt) {
    case 'agent.token':
      process.stdout.write(data.payload?.text || '');
      break;
    case 'agent.tool.start':
      console.log(`\n  [TOOL CALL] ${data.payload?.name}`);
      break;
    case 'agent.tool.result':
      const result = (data.payload?.result || '').substring(0, 300);
      console.log(`  [TOOL RESULT] ${data.payload?.name}: ${result}`);
      break;
    case 'agent.complete':
      console.log(`\n[COMPLETE] finalText: ${(data.payload?.finalText || '').substring(0, 500)}`);
      msgIdx++;
      setTimeout(sendNextMessage, 1000);
      break;
    case 'agent.error':
      console.log(`\n[ERROR] ${data.payload?.message}`);
      msgIdx++;
      setTimeout(sendNextMessage, 1000);
      break;
    default:
      console.log('[EVENT]', JSON.stringify(data).substring(0, 200));
  }
});

ws.on('error', (err) => console.error('WS Error:', err.message));
ws.on('close', () => { console.log('\nClosed.'); process.exit(0); });
setTimeout(() => { console.log('\nTimeout.'); ws.close(); process.exit(1); }, 120000);
