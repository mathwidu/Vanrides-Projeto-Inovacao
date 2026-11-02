import test from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { startProvider } from './070-worker-runtime.mjs';
import { jarDigest } from './070-worker-preflight.mjs';
import { localJson } from './070-worker-common.mjs';

const instance = 'wa070_synthetic_' + '0'.repeat(32);
const address = length => '0'.repeat(length) + '@s.whatsapp.net';

test('the passenger smoke checks the recipient of each message before inspecting its content', async () => {
  const key = 'local-test', driver = address(15), passenger = address(14);
  const provider = await startProvider(instance, driver, key, [passenger]);
  const send = number => localJson(provider.url, '/message/sendText/' + instance, { method: 'POST',
    headers: { apikey: key }, body: { number, text: 'Mensagem de teste.', linkPreview: false } });
  try {
    let inspected = 0;
    const expected = () => ({ outboxId: crypto.randomUUID(), address: passenger, matches: () => { inspected++; return true; } });
    provider.enqueue(expected());
    assert.equal((await send(driver)).status, 400);
    assert.equal(inspected, 0);
    provider.enqueue(expected());
    assert.equal((await send(passenger)).status, 201);
    assert.equal(inspected, 1);
    assert.throws(() => provider.enqueue({ ...expected(), address: address(13) }), { code: 'PROVIDER_EXPECTATION_OUTSIDE_COHORT' });
    assert.deepEqual(provider.counts(), { requests: 2, accepted: 1, pending: 0, failures: 1 });
  } finally { await provider.stop(); }
});

test('extra aliases for peer deliveries must be distinct and synthetic', async () => {
  for (const peers of [[address(14), address(14)], ['example@s.whatsapp.net'], [address(15)], [address(12)]]) {
    await assert.rejects(startProvider(instance, address(15), 'local-test', peers), { code: 'SYNTHETIC_PROVIDER_SCOPE_INVALID' });
  }
});

test('JAR verification covers the whole file across chunks and rejects symlinks', t => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'wa090-jar-'));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  const file = path.join(dir, 'test.jar');
  const contents = Buffer.alloc(600 * 1024 + 1, 'x');
  fs.writeFileSync(file, contents);
  assert.equal(jarDigest(file), crypto.createHash('sha256').update(contents).digest('hex'));
  const alias = path.join(dir, 'link.jar'); fs.symlinkSync(file, alias);
  assert.throws(() => jarDigest(alias));
});
