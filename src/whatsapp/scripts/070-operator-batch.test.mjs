import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { createOperatorBatch } from './070-operator-batch.mjs';

function fixture(mode = 'success') {
  const work = fs.mkdtempSync(path.join(os.tmpdir(), 'operator-batch-test-'));
  const java = path.join(work, 'java');
  fs.writeFileSync(java, `#!/usr/bin/env node
const fs = require('node:fs');
const request = process.argv.find(x => x.startsWith('--vanrides.wa.operator.request-file=')).split('=')[1];
const batch = JSON.parse(fs.readFileSync(request));
(async () => {
  for (let i = 0; i < batch.operations.length; i++) {
    if (batch.cancelFile && fs.existsSync(batch.cancelFile)) process.exit(1);
    if (process.env.MODE === 'failure' && i === 1) process.exit(9);
    const e = batch.operations[i];
    fs.writeFileSync(e.outputFile, JSON.stringify({status: process.env.MODE === 'prepared' ? 'PREPARED' : 'COMMITTED',
      operation: e.request.operation, batchId: e.request.batchId, environment: e.request.environment}), {mode: 0o600});
    if (process.env.MODE === 'cancel') await new Promise(r => setTimeout(r, 150));
  }
})().catch(() => process.exit(8));
`, { mode: 0o700 });
  let ordinal = 0;
  const run = createOperatorBatch({ java, work, batch: 'synthetic-batch',
    input: { jar: '/unused', configFile: '/unused', operator: { environment: 'wa070-local', operatorCredential: 'private' } },
    frozen: { env: { PATH: process.env.PATH, MODE: mode }, assertArtifacts() {}, assertContainer() {} },
    next: suffix => path.join(work, String(++ordinal).padStart(3, '0') + '-' + suffix) });
  return { work, run, close: () => fs.rmSync(work, { recursive: true, force: true }) };
}
const operations = [{ operation: 'create-test-cohort', fields: { synthetic: true } },
  { operation: 'provision-service-credential', fields: { principal: 'CHANNEL' } }];

test('batch returns each committed receipt and keeps inputs/results private', async () => {
  const f = fixture(), committed = [];
  try {
    const rows = await f.run(operations, { onCommitted: (_, i) => committed.push(i) });
    assert.deepEqual(committed, [0, 1]); assert.equal(rows.length, 2);
    for (const file of fs.readdirSync(f.work).filter(x => x !== 'java'))
      assert.equal(fs.statSync(path.join(f.work, file)).mode & 0o777, 0o600);
  } finally { f.close(); }
});
test('failed batch retains earlier committed receipts for cleanup and still rejects', async () => {
  const f = fixture('failure'), committed = [];
  try {
    await assert.rejects(f.run(operations, { onCommitted: (_, i) => committed.push(i) }), { code: 'OPERATOR_CLI_FAILED' });
    assert.deepEqual(committed, [0]);
  } finally { f.close(); }
});
test('zero process exit never turns PREPARED into success', async () => {
  const f = fixture('prepared'), committed = [];
  try {
    await assert.rejects(f.run(operations, { onCommitted: (_, i) => committed.push(i) }), { code: 'OPERATOR_RESULT_NOT_COMMITTED' });
    assert.deepEqual(committed, []);
  } finally { f.close(); }
});
test('cancellation waits for process exit, retains completed receipt and fences the next operation', async () => {
  const f = fixture('cancel'), controller = new AbortController(), committed = [];
  try {
    const result = assert.rejects(f.run(operations, { signal: controller.signal,
      onCommitted: (_, i) => committed.push(i) }), { code: 'SMOKE_INTERRUPTED' });
    let observed = false;
    for (let i = 0; i < 200; i++) {
      if (fs.readdirSync(f.work).some(x => x.endsWith('result.json'))) { observed = true; break; }
      await new Promise(resolve => setTimeout(resolve, 5));
    }
    controller.abort(); await result;
    assert.equal(observed, true); assert.deepEqual(committed, [0]);
    assert.equal(fs.readdirSync(f.work).filter(x => x.endsWith('result.json')).length, 1);
  } finally { f.close(); }
});
test('invalid or already-cancelled batches launch no process and create no inputs', async () => {
  const f = fixture(), controller = new AbortController(); controller.abort();
  try {
    await assert.rejects(f.run(operations, { signal: controller.signal }), { code: 'SMOKE_INTERRUPTED' });
    for (const invalid of [[], Array(17).fill(operations[0]), [{ operation: 'reset-test-data', fields: { batchId: 'other' } }]])
      await assert.rejects(f.run(invalid), { code: 'OPERATOR_BATCH_INVALID' });
    assert.deepEqual(fs.readdirSync(f.work), ['java']);
  } finally { f.close(); }
});
