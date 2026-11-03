import assert from 'node:assert/strict';
import { Buffer } from 'node:buffer';
import test from 'node:test';
import { submitEvolutionLocation } from './evolution-location.mjs';
import { submitEvolutionText } from './evolution-text.mjs';

const NOW = Date.parse('2030-01-01T12:00:00Z');
const TOKEN = Buffer.alloc(32, 9).toString('base64url');
const capturedAt = new Date(NOW - 1000).toISOString();
const pin = () => ({ outboxId: '00000000-0000-4000-8000-000000000130', attemptToken: TOKEN,
  submitBefore: new Date(NOW + 5000).toISOString(), serverTime: new Date(NOW).toISOString(),
  recipient: { addressType: 'JID', address: '000000000001@s.whatsapp.net' },
  content: { type: 'STATIC_PIN', latitude: 0, longitude: 0.001, capturedAt, label: 'Localização atual do veículo' } });
const options = overrides => ({ reservation: pin(), baseUrl: 'https://synthetic.example.test',
  instance: 'synthetic_130', apiKey: 'synthetic-key', now: () => NOW,
  readConnection: async () => ({ status: 'OPEN' }), ...overrides });
const accepted = () => new Response(JSON.stringify({ key: { id: 'SYNTHETIC_PIN_130' } }), { status: 201 });
const result = (outcome, failureCode) => ({ attemptToken: TOKEN, outcome, failureCode });

test('sends the exact static Evolution 2.3.7 pin with capture time and preserves LID/zero coordinates', async () => {
  for (const [addressType, address] of [['JID', '000000000001@s.whatsapp.net'], ['LID', '000000000001@lid']]) {
    const reservation = pin(); reservation.recipient = { addressType, address };
    let posts = 0;
    assert.deepEqual(await submitEvolutionLocation(options({ reservation, fetchImpl: async (url, request) => {
      posts++;
      assert.equal(url, 'https://synthetic.example.test/message/sendLocation/synthetic_130');
      assert.equal(request.redirect, 'manual'); assert.equal(request.headers.apikey, 'synthetic-key');
      assert.deepEqual(JSON.parse(request.body), { number: address, latitude: 0, longitude: 0.001,
        name: 'Localização atual do veículo', address: `Capturada em ${capturedAt}` });
      return accepted();
    } })), { attemptToken: TOKEN, outcome: 'SUBMITTED', externalMessageId: 'SYNTHETIC_PIN_130' });
    assert.equal(posts, 1);
  }
});

test('rejects stale, future, invalid or enriched pins before any HTTP', async () => {
  for (const mutate of [r => { r.submitBefore = new Date(NOW + 19001).toISOString(); },
    r => { r.content.capturedAt = new Date(NOW + 1).toISOString(); },
    r => { r.content.capturedAt = new Date(NOW - 20000).toISOString(); },
    r => { r.content.latitude = 90.01; }, r => { r.content.longitude = -180.01; },
    r => { r.content.latitude = '0'; }, r => { r.content.longitude = NaN; },
    r => { r.content.label = 'Ponto de outro passageiro'; }, r => { r.content.live = true; },
    r => { r.content.capturedAt = '2030-02-30T12:00:00Z'; }]) {
    const reservation = pin(); mutate(reservation); let calls = 0;
    assert.deepEqual(await submitEvolutionLocation(options({ reservation,
      readConnection: async () => { calls++; return { status: 'OPEN' }; },
      fetchImpl: async () => { calls++; return accepted(); } })), result('FAILED', 'REJECTED'));
    assert.equal(calls, 0);
  }
});

test('rechecks the deadline after connection lookup and never submits a pin aged 20 seconds', async () => {
  let current = NOW, posts = 0;
  const reservation = pin(); reservation.submitBefore = new Date(NOW + 19000).toISOString();
  assert.deepEqual(await submitEvolutionLocation(options({ reservation, now: () => current,
    readConnection: async () => { current += 19000; return { status: 'OPEN' }; },
    fetchImpl: async () => { posts++; return accepted(); } })), result('NOT_SUBMITTED', 'CHANNEL_UNAVAILABLE'));
  assert.equal(posts, 0);
});

test('pin HTTP 400, network uncertainty and timeouts preserve UNKNOWN without retries', async () => {
  for (const fetchImpl of [async () => new Response('PRIVATE', { status: 400 }), () => { throw Error('PRIVATE'); }]) {
    let posts = 0;
    assert.deepEqual(await submitEvolutionLocation(options({ fetchImpl: (...args) => { posts++; return fetchImpl(...args); } })),
      result('UNKNOWN', 'CHANNEL_UNAVAILABLE'));
    assert.equal(posts, 1);
  }
  const reservation = pin(); reservation.submitBefore = new Date(NOW + 10).toISOString();
  let posts = 0;
  assert.deepEqual(await submitEvolutionLocation(options({ reservation, fetchImpl: () => { posts++; return new Promise(() => {}); } })),
    result('UNKNOWN', 'TIMEOUT'));
  assert.equal(posts, 1);
});

test('keeps coordinates and provider data out of receipts and logs; text-only adapter refuses pins', async t => {
  const logs = [];
  for (const method of ['log', 'error', 'warn', 'info', 'debug']) t.mock.method(console, method, (...args) => logs.push(args));
  const response = await submitEvolutionLocation(options({ fetchImpl: () => { throw Error('PRIVATE_COORDINATES'); } }));
  assert.deepEqual(Object.keys(response).sort(), ['attemptToken', 'failureCode', 'outcome']);
  assert.deepEqual(logs, []);
  assert.deepEqual(await submitEvolutionText(options({ fetchImpl: () => assert.fail('Text consumer cannot submit a pin') })),
    result('FAILED', 'REJECTED'));
});
