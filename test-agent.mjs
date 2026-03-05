// Test: connect → handshake → agent.run → collect events
const WS_URL = 'ws://127.0.0.1:18789/';
const TOKEN = 'test-token';

const ws = new WebSocket(WS_URL);
let reqId = 1;

function send(method, params = {}) {
  const id = `req-${reqId++}`;
  const frame = { type: 'req', id, method, params };
  console.log('→', method, JSON.stringify(params));
  ws.send(JSON.stringify(frame));
  return id;
}

ws.addEventListener('open', () => {
  console.log('Connected\n');
  send('connect.hello', { token: TOKEN, role: 'operator' });
});

ws.addEventListener('message', (event) => {
  const msg = JSON.parse(event.data);

  // Response to a request
  if (msg.type === 'res') {
    console.log(`← [res] id=${msg.id} ok=${msg.ok}`, JSON.stringify(msg.payload, null, 2), '\n');

    if (msg.id === 'req-1' && msg.ok) {
      console.log('Handshake OK — sending agent.run...\n');
      send('agent.run', {
        key: 'main:main:*:main',
        message: 'Say hello in one sentence.'
      });
    } else if (msg.id === 'req-2') {
      if (msg.ok) {
        console.log('agent.run accepted — waiting for events...\n');
      } else {
        console.log('agent.run failed:', msg.payload?.error, '\n');
        ws.close();
      }
    }
  }

  // Server-pushed events (streaming tokens, tool calls, completion)
  if (msg.type === 'event') {
    if (msg.event === 'agent.token') {
      process.stdout.write(msg.payload?.text || '');
    } else if (msg.event === 'agent.complete') {
      console.log('\n\n=== Agent run complete ===');
      console.log('Final text:', msg.payload?.finalText?.substring(0, 200));
      console.log('Usage:', JSON.stringify(msg.payload?.usage));
      ws.close();
    } else if (msg.event === 'agent.error') {
      console.log('\n=== Agent error ===', msg.payload?.message);
      ws.close();
    } else {
      console.log(`← [event] ${msg.event}`, JSON.stringify(msg.payload));
    }
  }
});

ws.addEventListener('error', (err) => {
  console.error('WS error:', err.message || err);
  process.exit(1);
});

ws.addEventListener('close', () => {
  console.log('Disconnected');
  process.exit(0);
});

setTimeout(() => {
  console.error('\nTimeout after 30s');
  ws.close();
  process.exit(1);
}, 30000);
