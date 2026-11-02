// Cross-repository contract test; intentionally not copied into the worker image.
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { DRIVER_PAYLOADS } from '../agent/driver-setup.mjs';

const contract = JSON.parse(readFileSync(new URL('../../backend/contracts/whatsapp-mvp/openapi.yaml', import.meta.url), 'utf8'));
const schemas = contract.components.schemas;

test('every driver command forwards exactly the payload accepted by the API', () => {
  for (const [name, fields] of Object.entries(DRIVER_PAYLOADS)) {
    const candidates = Object.values(schemas).filter(schema => schema.properties?.commandType?.enum?.length === 1
      && schema.properties.commandType.enum[0] === name);
    assert.equal(candidates.length, 1, name);
    const envelope = candidates[0];
    assert.notEqual(envelope['x-vanrides-availability'], 'FUTURE', name);
    const payload = schemas[envelope.properties.payload.$ref.split('/').at(-1)];
    assert.equal(payload.additionalProperties, false, name);
    assert.deepEqual(Object.keys(payload.properties ?? {}).sort(), Object.keys(fields).sort(), name);
    assert.deepEqual([...(payload.required ?? [])].sort(), Object.keys(fields).sort(), name);
    for (const field of Object.keys(fields))
      assert.equal(payload.properties[field].$ref, '#/components/schemas/ContextReference', `${name}.${field}`);
  }
});

test('context references preserve the closed producer-consumer representation', () => {
  const options = schemas.ContextOption;
  assert.equal(options.additionalProperties, false);
  assert.deepEqual(Object.keys(options.properties).sort(), ['kind', 'label', 'ref']);
  assert.deepEqual([...options.required].sort(), ['kind', 'label', 'ref']);
  for (const kind of new Set(Object.values(DRIVER_PAYLOADS).flatMap(Object.values)))
    assert.ok(options.properties.kind.enum.includes(kind), kind);
  assert.equal(schemas.ContextReference.minLength, 8);
  assert.equal(schemas.ContextReference.maxLength, 160);
  assert.equal(schemas.ContextReference.pattern, '^[A-Za-z0-9_-]+$');
});
