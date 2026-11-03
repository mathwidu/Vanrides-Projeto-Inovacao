import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, chmodSync, symlinkSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { loadModelConfig } from './model-config.mjs';

const key = 'sk-FIXTURE_NOT_A_CREDENTIAL_1234567890';
test('model is disabled by default even with a key or an unreadable secret file', () => {
  assert.deepEqual(loadModelConfig({ OPENAI_API_KEY: key, OPENAI_API_KEY_FILE: '/missing' }), { mode: 'disabled' });
});
test('explicit activation accepts one existing key and hides it from serialization', () => {
  const config = loadModelConfig({ WA070_MODEL_MODE: 'openai', OPENAI_API_KEY: key });
  assert.equal(config.apiKey, key);
  assert.deepEqual(JSON.parse(JSON.stringify(config)), { mode: 'openai', model: 'gpt-6-luna', reasoningEffort: 'low', maxOutputTokens: 600 });
  assert.equal(Object.isFrozen(config), true);
});
test('activation rejects missing, duplicated, malformed secrets and alternate endpoint', () => {
  for (const env of [{}, { OPENAI_API_KEY: '' }, { OPENAI_API_KEY: key + '\n' },
    { OPENAI_API_KEY: key, OPENAI_API_KEY_FILE: '/missing' }, { OPENAI_API_KEY: key, OPENAI_BASE_URL: 'https://elsewhere.invalid' }]) {
    assert.throws(() => loadModelConfig({ WA070_MODEL_MODE: 'openai', ...env }), { code: 'CONFIG_INVALID' });
  }
  assert.throws(() => loadModelConfig({ WA070_MODEL_MODE: 'automatic', OPENAI_API_KEY: key }), { code: 'CONFIG_INVALID' });
});
test('file mode enforces private regular owned file and never falls back', t => {
  const dir = mkdtempSync(join(tmpdir(), 'wa070-model-config-'));
  t.after(() => rmSync(dir, { force: true, recursive: true }));
  const file = join(dir, 'key'); writeFileSync(file, key + '\n', { mode: 0o600 });
  assert.equal(loadModelConfig({ WA070_MODEL_MODE: 'openai', OPENAI_API_KEY_FILE: file }).apiKey, key);
  chmodSync(file, 0o644);
  assert.throws(() => loadModelConfig({ WA070_MODEL_MODE: 'openai', OPENAI_API_KEY_FILE: file }), { code: 'CONFIG_INVALID' });
  chmodSync(file, 0o600); symlinkSync(file, join(dir, 'link'));
  assert.throws(() => loadModelConfig({ WA070_MODEL_MODE: 'openai', OPENAI_API_KEY_FILE: join(dir, 'link') }), { code: 'CONFIG_INVALID' });
});
