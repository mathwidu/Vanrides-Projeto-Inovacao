import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const script = fileURLToPath(new URL('./040-foundation-smoke.mjs', import.meta.url));
const sha = 'a'.repeat(40);
const sentinel = 'PRIVATE_CONTENT_MUST_NEVER_REACH_STDERR';

for (const scenario of ['invalid-state-json', 'invalid-operator-json', 'missing-input']) {
  test('preflight sanitizes ' + scenario, () => {
    const home = fs.mkdtempSync(path.join(os.tmpdir(), 'wa040-preflight-'));
    try {
      const base = path.join(home, '.local/share/vanrides/local/wa040-backend-' + sha.slice(0, 8));
      fs.mkdirSync(base, { recursive: true, mode: 0o700 });
      if (scenario !== 'missing-input') {
        const state = scenario === 'invalid-state-json' ? '{"secret":"' + sentinel : JSON.stringify({ source_sha: sha });
        fs.writeFileSync(path.join(base, 'state.json'), state, { mode: 0o600 });
      }
      if (scenario === 'invalid-operator-json') {
        fs.writeFileSync(path.join(base, 'operator-private.json'), '{"secret":"' + sentinel, { mode: 0o600 });
      }
      const result = spawnSync(process.execPath, [script, base, sha], { env: { HOME: home }, encoding: 'utf8', timeout: 5000 });
      assert.equal(result.status, 1);
      assert.equal(result.stdout, '');
      assert.deepEqual(JSON.parse(result.stderr), { status: 'FAIL', code: 'PRIVATE_INPUT_INVALID' });
      assert.equal(result.stderr.includes(sentinel), false);
      assert.equal(result.stderr.includes(home), false);
    } finally {
      fs.rmSync(home, { recursive: true, force: true });
    }
  });
}
