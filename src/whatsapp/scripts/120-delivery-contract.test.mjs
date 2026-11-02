import fs from 'node:fs';
import test from 'node:test';
import assert from 'node:assert/strict';
import { submitEvolutionText } from '../transport/evolution-text.mjs';

const contract = JSON.parse(fs.readFileSync(new URL('../../backend/contracts/whatsapp-mvp/openapi.yaml', import.meta.url)));
const schemas = contract.components.schemas;

test('all adapter outcomes fit exactly one closed backend result variant; safe failure has no external ID', async () => {
  const now = Date.parse('2030-01-01T00:00:00Z');
  const reservation = { outboxId: '00000000-0000-4000-8000-000000000120', attemptToken: Buffer.alloc(32, 7).toString('base64url'),
    submitBefore: new Date(now + 10_000).toISOString(), serverTime: new Date(now).toISOString(),
    recipient: { addressType: 'JID', address: '000000000000000@s.whatsapp.net' },
    content: { type: 'TEXT', text: 'Resposta sintética.', referenceTime: new Date(now).toISOString() } };
  const variantRefs = schemas.OutboxResultRequest.oneOf;
  assert.equal(variantRefs.length, 4);
  const variants = variantRefs.map(ref => schemas[ref.$ref.split('/').at(-1)]);
  for (const outcome of ['NOT_SUBMITTED', 'SUBMITTED', 'UNKNOWN', 'FAILED']) {
    let posts = 0;
    const value = await submitEvolutionText({ baseUrl: 'https://synthetic.example.test', instance: 'wa120_synthetic', apiKey: 'synthetic',
      reservation, now: () => now, readConnection: async () => ({ status: outcome === 'NOT_SUBMITTED' ? 'CLOSED' : 'OPEN' }),
      fetchImpl: async () => { posts++; return outcome === 'SUBMITTED'
        ? new Response('{"key":{"id":"synthetic120"}}', { status: 201 })
        : new Response('{}', { status: outcome === 'FAILED' ? 401 : 400 }); } });
    assert.equal(value.outcome, outcome);
    const matches = variants.filter(schema => schema.properties.outcome.enum.includes(value.outcome));
    assert.equal(matches.length, 1);
    const schema = matches[0];
    assert.equal(schema.additionalProperties, false);
    assert.deepEqual(Object.keys(value).sort(), Object.keys(schema.properties).sort());
    assert.deepEqual(Object.keys(value).sort(), [...schema.required].sort());
    if (value.failureCode) assert.ok(schema.properties.failureCode.enum.includes(value.failureCode));
    assert.equal(posts, outcome === 'NOT_SUBMITTED' ? 0 : 1);
  }
  assert.deepEqual(schemas.OutboxNotSubmittedResult.properties.failureCode.enum, ['CHANNEL_UNAVAILABLE']);
  assert.equal(Object.hasOwn(schemas.OutboxNotSubmittedResult.properties, 'externalMessageId'), false);
});
