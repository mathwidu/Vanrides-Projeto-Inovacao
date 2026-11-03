import test from 'node:test';
import assert from 'node:assert/strict';
import { createModelRequest, createOpenAIClassifier, parseModelResponse, ModelError } from './model-client.mjs';
import { loadModelConfig } from './model-config.mjs';

const config = loadModelConfig({ WA070_MODEL_MODE: 'openai', OPENAI_API_KEY: 'sk-FIXTURE_NOT_A_CREDENTIAL_1234567890' });
const input = { text: 'Como este serviço pode me ajudar?', actorState: 'UNREGISTERED', registrationStep: 'WELCOME' };
const proposal = { intent: 'HELP', attendance: null, day: null, direction: null, ambiguous: false };
const completed = (value = proposal) => ({ status: 'completed', error: null, incomplete_details: null,
  output: [{ type: 'reasoning', summary: [] }, { type: 'message', role: 'assistant', status: 'completed',
    content: [{ type: 'output_text', text: JSON.stringify(value) }] }],
  usage: { input_tokens: 450, output_tokens: 85, total_tokens: 535 } });
const response = body => new Response(JSON.stringify(body), { headers: { 'content-type': 'application/json' } });

test('fixed Responses request uses strict schema, small context and no tools or storage', () => {
  const encoded = createModelRequest(input), body = JSON.parse(encoded);
  assert.ok(Buffer.byteLength(encoded) <= 3200);
  assert.equal(body.model, 'gpt-6-luna'); assert.deepEqual(body.reasoning, { effort: 'low' });
  assert.equal(body.store, false); assert.equal(body.stream, false); assert.equal(body.max_output_tokens, 600);
  assert.deepEqual(body.tools, []); assert.equal(body.text.format.strict, true);
  assert.equal(body.text.format.schema.additionalProperties, false);
  assert.deepEqual(JSON.parse(body.input[0].content[0].text), input);
  for (const changed of [{ ...input, actorId: 'forged' }, { ...input, registrationStep: 'ASK_NAME' },
    { ...input, text: 'x'.repeat(1201) }, { ...input, text: '🚐'.repeat(301) }])
    assert.throws(() => createModelRequest(changed), { code: 'REQUEST_REJECTED' });
});
test('successful response returns only the validated proposal; usage observer sees counters', async () => {
  const usages = [], calls = [];
  const classify = createOpenAIClassifier({ config, onUsage: u => usages.push(u), fetchImpl: async (url, options) => {
    calls.push({ url, options }); return response(completed());
  } });
  assert.deepEqual(await classify(input, { deadline: Date.now() + 1000 }), proposal);
  assert.equal(calls[0].url, 'https://api.openai.com/v1/responses');
  assert.equal(calls[0].options.redirect, 'manual');
  assert.deepEqual(usages, [{ inputTokens: 450, outputTokens: 85, totalTokens: 535, attempts: 1 }]);
});
test('invalid, incomplete, refusal, tool-call and multiple-message responses fail closed', () => {
  const refusal = completed(); refusal.output[1].content = [{ type: 'refusal', refusal: 'PRIVATE_SENTINEL' }];
  assert.throws(() => parseModelResponse(refusal), { code: 'REFUSED' });
  assert.throws(() => parseModelResponse({ status: 'incomplete' }), { code: 'INCOMPLETE' });
  for (const body of [null, {}, { ...completed(), status: 'failed' }, completed({ ...proposal, actorId: 'forged' }),
    { ...completed(), output: [{ type: 'function_call', name: 'grant_admin' }] },
    { ...completed(), output: [completed().output[1], completed().output[1]] }])
    assert.throws(() => parseModelResponse(body), { code: 'INVALID_OUTPUT' });
  const bad = completed(); bad.output[1].content[0].text = '{invalid';
  assert.throws(() => parseModelResponse(bad), { code: 'INVALID_OUTPUT' });
});
test('auth errors and redirects are never retried or followed', async () => {
  for (const status of [400, 401, 403, 404, 307]) {
    let count = 0;
    const classify = createOpenAIClassifier({ config, fetchImpl: async () => {
      count++; return new Response('PRIVATE_SENTINEL', { status, headers: { location: 'https://elsewhere.invalid' } });
    } });
    await assert.rejects(classify(input, { deadline: Date.now() + 2000 }), error => {
      assert.ok(error instanceof ModelError); assert.ok(!error.message.includes('PRIVATE_SENTINEL')); return true;
    });
    assert.equal(count, 1);
  }
});
test('one transient retry shares total deadline and strips original network error', async () => {
  let current = 1000, calls = 0; const sleeps = [];
  const classify = createOpenAIClassifier({ config, now: () => current, sleep: async ms => { sleeps.push(ms); current += ms; },
    fetchImpl: async () => { calls++; if (calls === 1) throw new Error('PRIVATE_SENTINEL'); return response(completed()); } });
  assert.deepEqual(await classify(input, { deadline: 3000 }), proposal);
  assert.equal(calls, 2); assert.deepEqual(sleeps, [250]);
  calls = 0;
  const unavailable = createOpenAIClassifier({ config, now: () => current, sleep: async () => {},
    fetchImpl: async () => { calls++; throw new Error('PRIVATE_SENTINEL'); } });
  await assert.rejects(unavailable(input, { deadline: current + 1000 }), { code: 'UNAVAILABLE' });
  assert.equal(calls, 2);
});
test('Retry-After exceeding the budget prevents a retry', async () => {
  let calls = 0;
  const classify = createOpenAIClassifier({ config, fetchImpl: async () => {
    calls++; return new Response('{}', { status: 429, headers: { 'retry-after': '30' } });
  }, sleep: async () => assert.fail('must not sleep') });
  await assert.rejects(classify(input, { deadline: Date.now() + 1000 }), { code: 'RATE_LIMITED' });
  assert.equal(calls, 1);
});
test('HTTP-date Retry-After is also respected within the total budget', async () => {
  let calls = 0;
  const classify = createOpenAIClassifier({ config, fetchImpl: async () => {
    calls++; return new Response('{}', { status: 503, headers: { 'retry-after': new Date(Date.now() + 60000).toUTCString() } });
  }, sleep: async () => assert.fail('must not sleep') });
  await assert.rejects(classify(input, { deadline: Date.now() + 1000 }), { code: 'UNAVAILABLE' });
  assert.equal(calls, 1);
});
test('complete deadline includes body consumption and aborts a stalled stream', async () => {
  let cancelled = false, signal;
  const classify = createOpenAIClassifier({ config, fetchImpl: async (_url, options) => {
    signal = options.signal;
    return new Response(new ReadableStream({ pull() {}, cancel() { cancelled = true; } }));
  } });
  await assert.rejects(classify(input, { deadline: Date.now() + 30 }), { code: 'TIMEOUT' });
  assert.equal(signal.aborted, true); assert.equal(cancelled, true);
});
test('deadline rejects a non-cooperating fetch without exposing a result', async () => {
  let signal;
  const classify = createOpenAIClassifier({ config, fetchImpl: async (_url, options) => {
    signal = options.signal; return new Promise(() => {});
  } });
  await assert.rejects(classify(input, { deadline: Date.now() + 30 }), { code: 'TIMEOUT' });
  assert.equal(signal.aborted, true);
});
test('oversized, invalid UTF-8 and excessive reported usage are rejected without retries', async () => {
  const oversized = () => new Response('x'.repeat(32769));
  const invalid = () => new Response(new Uint8Array([255, 254]));
  const usage = () => response({ ...completed(), usage: { input_tokens: 4001, output_tokens: 20, total_tokens: 4021 } });
  for (const make of [oversized, invalid, usage]) {
    let calls = 0;
    const classify = createOpenAIClassifier({ config, fetchImpl: async () => { calls++; return make(); } });
    await assert.rejects(classify(input, { deadline: Date.now() + 1000 }), { code: 'INVALID_OUTPUT' });
    assert.equal(calls, 1);
  }
});
test('invalid configuration or elapsed deadline never sends a request', async () => {
  assert.throws(() => createOpenAIClassifier({ config: { mode: 'disabled' } }), { code: 'CONFIG_INVALID' });
  const classify = createOpenAIClassifier({ config, now: () => 100, fetchImpl: () => assert.fail('must not send') });
  for (const deadline of [undefined, 99, 100, NaN]) await assert.rejects(classify(input, { deadline }), { code: 'TIMEOUT' });
});
