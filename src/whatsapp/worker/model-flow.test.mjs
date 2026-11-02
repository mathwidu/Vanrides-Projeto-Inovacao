import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { randomBytes } from 'node:crypto';
import { once } from 'node:events';
import { createApiClient } from './http-client.mjs';
import { createInboxProcessor } from './process-inbox.mjs';
import { createWorkerServer } from './server.mjs';
import { loadModelConfig } from './model-config.mjs';
import { createOpenAIClassifier } from './model-client.mjs';
import { createModelDecision } from '../agent/model-policy.mjs';

const id = '00000000-0000-4000-8000-000000000001';
const help = { intent: 'HELP', attendance: null, day: null, direction: null, ambiguous: false };
const completed = proposal => ({ status: 'completed', output: [{ type: 'message', status: 'completed',
  role: 'assistant', content: [{ type: 'output_text', text: JSON.stringify(proposal) }] }] });
const json = value => new Response(JSON.stringify(value));
const modelConfig = loadModelConfig({ WA070_MODEL_MODE: 'openai', OPENAI_API_KEY: 'sk-FIXTURE_NOT_A_CREDENTIAL_1234567890' });

async function runScenario(t, { text = 'Como este serviço funciona?', provider, expectedStatus, expectedCode,
  expectedCalls = 1, contextOverride = {}, expectedCommand = 'RESPOND_TO_MESSAGE', expectedPayload }) {
  const token = randomBytes(32).toString('base64url'), control = randomBytes(32).toString('base64url');
  const lease = randomBytes(32).toString('base64url'), commands = [], acks = [], errors = [];
  const context = { inboxEventId: id, entityVersion: 5, actorState: 'PASSENGER', step: 'READY',
    registrationStep: 'READY', eventFreshness: 'CURRENT', options: [], allowedCommands: ['RESPOND_TO_MESSAGE'],
    serverTime: new Date().toISOString(), ...contextOverride };
  const backend = createServer(async (req, res) => {
    try {
      assert.equal(req.headers.authorization, `Bearer ${token}`);
      let body = ''; for await (const chunk of req) body += chunk;
      const parsed = body ? JSON.parse(body) : undefined;
      let data;
      if (req.url.endsWith('/claim')) data = { items: [{ inboxEventId: id, inboxLease: lease,
        leaseExpiresAt: new Date(Date.now() + 60000).toISOString(),
        message: { sourceMessageId: `event_${id}`, type: 'TEXT', text } }] };
      else {
        assert.equal(req.headers['x-inbox-lease'], lease);
        if (req.url.includes('/context?')) data = context;
        else if (req.url.endsWith('/commands')) {
          commands.push(parsed);
          assert.deepEqual(Object.keys(parsed).sort(), ['commandType', 'expectedVersion', 'inboxEventId', 'payload']);
          assert.equal(parsed.inboxEventId, id); assert.equal(parsed.expectedVersion, 5);
          assert.equal(parsed.commandType, expectedCommand);
          if (expectedPayload) assert.deepEqual(parsed.payload, expectedPayload);
          else assert.deepEqual(Object.keys(parsed.payload), ['responseCode']);
          // Producer contract: WaConversationPolicy only permits HELP for
          // literal help messages. CONTINUE renders the same stage prompt.
          if (parsed.payload.responseCode === 'HELP') assert.equal(text, 'AJUDA');
          data = { commandId: id, outcome: 'APPLIED' };
        } else {
          acks.push(parsed); data = { inboxEventId: id, state: parsed.outcome === 'PAUSED' ? 'PAUSED' : 'PROCESSED' };
        }
      }
      res.writeHead(200, { 'content-type': 'application/json' }); res.end(JSON.stringify(data));
    } catch (error) { errors.push(error); res.writeHead(500); res.end('{}'); }
  });
  backend.listen(0, '127.0.0.1'); await once(backend, 'listening');
  t.after(() => { backend.closeAllConnections(); backend.close(); });
  const api = createApiClient({ baseUrl: `http://127.0.0.1:${backend.address().port}`, token,
    localMode: true, allowedHttpHosts: ['127.0.0.1'] });
  let modelCalls = 0;
  const classify = createOpenAIClassifier({ config: modelConfig, sleep: async () => {}, fetchImpl: async (url, options) => {
    assert.equal(url, 'https://api.openai.com/v1/responses'); modelCalls++;
    const input = JSON.parse(JSON.parse(options.body).input[0].content[0].text);
    assert.deepEqual(Object.keys(input).sort(), ['actorState', 'registrationStep', 'text']);
    assert.equal(JSON.stringify(input).includes(id), false);
    return provider(modelCalls);
  } });
  const worker = createWorkerServer({ config: { controlToken: control },
    dispatchOne: async () => ({ status: 'IDLE', claimed: 0, submitted: 0, unknown: 0, failed: 0 }),
    processInbox: createInboxProcessor({ api, decide: createModelDecision({ classify }) }) });
  worker.listen(0, '127.0.0.1'); await once(worker, 'listening');
  t.after(() => { worker.closeAllConnections(); worker.close(); });
  const result = await fetch(`http://127.0.0.1:${worker.address().port}/v1/process-inbox`, {
    method: 'POST', headers: { authorization: `Bearer ${control}`, 'content-type': 'application/json' }, body: '{}',
  });
  assert.equal(result.status, 200);
  const summary = await result.json();
  assert.equal(summary.status, expectedStatus);
  assert.deepEqual(Object.keys(summary), ['status', 'claimed', 'processed', 'retried', 'paused']);
  assert.deepEqual(errors, []); assert.equal(modelCalls, expectedCalls);
  assert.equal(acks.length, 1);
  if (expectedCode || expectedPayload) {
    assert.equal(commands.length, 1);
    if (expectedCode) assert.equal(commands[0].payload.responseCode, expectedCode);
    if (expectedPayload) assert.deepEqual(commands[0].payload, expectedPayload);
    assert.equal(acks[0].outcome, 'COMPLETED');
  } else {
    assert.equal(commands.length, 0); assert.deepEqual(acks[0], { outcome: 'PAUSED', failureCode: 'INVALID_MODEL_OUTPUT' });
  }
}

test('HTTP control → real worker/client → local API: classified help is an enumerated response', t => runScenario(t, {
  provider: () => json(completed(help)), expectedStatus: 'PROCESSED', expectedCode: 'CONTINUE',
}));
test('HTTP flow: natural support request stays informational and uses the producer-permitted prompt', t => runScenario(t, {
  text: 'Preciso conversar com alguém da equipe', provider: () => json(completed({ ...help, intent: 'SUPPORT' })),
  expectedStatus: 'PROCESSED', expectedCode: 'CONTINUE',
}));
test('HTTP flow: partial model output pauses without a command', t => runScenario(t, {
  provider: () => json({ status: 'incomplete' }), expectedStatus: 'PAUSED',
}));
test('HTTP flow: refusal pauses without a command', t => runScenario(t, {
  provider: () => json({ status: 'completed', output: [{ type: 'message', role: 'assistant', status: 'completed',
    content: [{ type: 'refusal', refusal: 'SYNTHETIC_REFUSAL' }] }] }), expectedStatus: 'PAUSED',
}));
test('HTTP flow: forged command/actor fields cannot cross the policy boundary', t => runScenario(t, {
  provider: () => json(completed({ ...help, actorId: 'OTHER_ACTOR', commandType: 'ACCEPT_NOTICE' })), expectedStatus: 'PAUSED',
}));
test('HTTP flow: authentication failure pauses and is not retried', t => runScenario(t, {
  provider: () => new Response('{}', { status: 401 }), expectedStatus: 'PAUSED',
}));
test('HTTP flow: one transient retry still emits only one command', t => runScenario(t, {
  provider: count => count === 1 ? new Response('{}', { status: 503 }) : json(completed(help)),
  expectedStatus: 'PROCESSED', expectedCode: 'CONTINUE', expectedCalls: 2,
}));
test('HTTP flow: AJUDA works with zero model requests', t => runScenario(t, {
  text: 'AJUDA', provider: () => assert.fail('model must not run'), expectedStatus: 'PROCESSED', expectedCode: 'HELP', expectedCalls: 0,
}));
test('HTTP flow: text with numeric private data is withheld from the provider', t => runScenario(t, {
  text: 'Preciso falar sobre meu código 123456', provider: () => assert.fail('model must not run'),
  expectedStatus: 'PROCESSED', expectedCode: 'CLARIFY', expectedCalls: 0,
}));

test('HTTP flow: unpaired driver advances a private draft without a model request', t => runScenario(t, {
  text: '06:00', provider: () => assert.fail('model must not receive driver draft values'),
  expectedStatus: 'PROCESSED', expectedCalls: 0, expectedCommand: 'ADVANCE_DRIVER_SETUP', expectedPayload: {},
  contextOverride: { actorState: 'DRIVER', step: 'PAIRING', registrationStep: 'PAIRING',
    allowedCommands: ['RESPOND_TO_MESSAGE', 'ADVANCE_DRIVER_SETUP'] },
}));
test('HTTP flow: routine confirmation forwards only the two API references', t => runScenario(t, {
  text: 'CONFIRMAR EXEMPLOX', provider: () => assert.fail('model must not authorize confirmation'),
  expectedStatus: 'PROCESSED', expectedCalls: 0, expectedCommand: 'CONFIGURE_ROUTINE',
  expectedPayload: { routineRef: 'routine_synthetic080', confirmationRef: 'confirmation_synthetic080' },
  contextOverride: { actorState: 'DRIVER', step: 'PAIRING', registrationStep: 'PAIRING',
    allowedCommands: ['RESPOND_TO_MESSAGE', 'CONFIGURE_ROUTINE'],
    options: [{ kind: 'ROUTINE', ref: 'routine_synthetic080', label: 'Rotina selecionada' },
      { kind: 'CONFIRMATION', ref: 'confirmation_synthetic080', label: 'Confirmação vigente' }] },
}));
test('HTTP flow: an ambiguous routine target pauses without choosing a target or calling the model', t => runScenario(t, {
  text: 'CONFIRMAR EXEMPLOX', provider: () => assert.fail('ambiguous API context must not reach the model'),
  expectedStatus: 'PAUSED', expectedCalls: 0,
  contextOverride: { actorState: 'DRIVER', step: 'PAIRING', registrationStep: 'PAIRING',
    allowedCommands: ['RESPOND_TO_MESSAGE', 'CONFIGURE_ROUTINE'],
    options: [{ kind: 'ROUTINE', ref: 'routine_synthetic080', label: 'Rotina selecionada' },
      { kind: 'ROUTINE', ref: 'routine_other080', label: 'Outra rotina' },
      { kind: 'CONFIRMATION', ref: 'confirmation_synthetic080', label: 'Confirmação vigente' }] },
}));
