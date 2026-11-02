import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const script = fileURLToPath(new URL('./050-transport-smoke.mjs', import.meta.url));
const repository = path.resolve(fileURLToPath(new URL('../../backend/', import.meta.url)));
const sha = 'a'.repeat(40);
const sentinel = 'PRIVATE_CONTENT_MUST_NEVER_REACH_OUTPUT';
const token = Buffer.alloc(32, 9).toString('base64url');

function fixture() {
  const home = fs.mkdtempSync(path.join(os.tmpdir(), 'wa050-preflight-'));
  const base = path.join(home, '.local/share/vanrides/local/wa050-backend-' + sha.slice(0, 8));
  fs.mkdirSync(base, { recursive: true, mode: 0o700 });
  const state = { repository, source_sha: sha, jar_sha256: 'b'.repeat(64), api_pid: 123456789,
    api_base_url: 'http://127.0.0.1:18082', container: 'vanrides-wa050-aaaaaaaa-postgres', db_port: 51221 };
  const operator = { environment: 'wa050-local', operatorCredential: token };
  const config = [
    'spring.datasource.url=jdbc:postgresql://127.0.0.1:51221/vanrides_wa050',
    'spring.datasource.username=vanrides_wa050', 'spring.datasource.password=' + token,
    'server.address=127.0.0.1', 'server.port=18082', 'vanrides.wa.environment=wa050-local',
    'vanrides.wa.enabled=true', 'vanrides.wa.test-cohort-only=true',
  ].join('\n') + '\n';
  const write = (name, value, mode = 0o600) => {
    const file = path.join(base, name);
    fs.writeFileSync(file, typeof value === 'string' ? value : JSON.stringify(value), { mode });
    fs.chmodSync(file, mode);
  };
  write('state.json', state); write('operator-private.json', operator); write('application-wa050.properties', config);
  return { home, base, state, operator, config, write };
}

const scenarios = [
  ['missing-state', 'PRIVATE_INPUT_INVALID', f => fs.unlinkSync(path.join(f.base, 'state.json'))],
  ['missing-operator', 'PRIVATE_INPUT_INVALID', f => fs.unlinkSync(path.join(f.base, 'operator-private.json'))],
  ['missing-config', 'PRIVATE_INPUT_INVALID', f => fs.unlinkSync(path.join(f.base, 'application-wa050.properties'))],
  ['malformed-state', 'PRIVATE_INPUT_INVALID', f => f.write('state.json', '{"private":"' + sentinel + token)],
  ['malformed-operator', 'PRIVATE_INPUT_INVALID', f => f.write('operator-private.json', '{"private":"' + sentinel + token)],
  ['state-array', 'PRIVATE_INPUT_INVALID', f => f.write('state.json', JSON.stringify([sentinel, token]))],
  ['state-mode', 'PRIVATE_INPUT_INVALID', f => f.write('state.json', { ...f.state, private: sentinel }, 0o644)],
  ['operator-mode', 'PRIVATE_INPUT_INVALID', f => f.write('operator-private.json', { ...f.operator, private: sentinel }, 0o644)],
  ['config-mode', 'PRIVATE_INPUT_INVALID', f => f.write('application-wa050.properties', f.config + '# ' + sentinel, 0o644)],
  ['state-symlink', 'PRIVATE_INPUT_INVALID', f => {
    fs.unlinkSync(path.join(f.base, 'state.json'));
    const target = path.join(f.home, 'private-' + sentinel);
    fs.writeFileSync(target, sentinel + token, { mode: 0o600 }); fs.symlinkSync(target, path.join(f.base, 'state.json'));
  }],
  ['foreign-repository', 'RUNTIME_STATE_INVALID', f => f.write('state.json', { ...f.state, repository: '/' + sentinel + '/' + token })],
  ['foreign-sha', 'RUNTIME_STATE_INVALID', f => f.write('state.json', { ...f.state, source_sha: sentinel + token })],
  ['external-api-url', 'RUNTIME_STATE_INVALID', f => f.write('state.json', { ...f.state, api_base_url: 'https://' + sentinel + '.invalid/' + token })],
  ['foreign-container', 'RUNTIME_STATE_INVALID', f => f.write('state.json', { ...f.state, container: sentinel + token })],
  ['foreign-database-port', 'RUNTIME_STATE_INVALID', f => f.write('state.json', { ...f.state, db_port: 5432, private: sentinel + token })],
  ['invalid-api-pid', 'RUNTIME_STATE_INVALID', f => f.write('state.json', { ...f.state, api_pid: sentinel + token })],
  ['foreign-operator-environment', 'OPERATOR_INPUT_INVALID', f => f.write('operator-private.json', { ...f.operator, environment: sentinel })],
  ['invalid-operator-token', 'OPERATOR_INPUT_INVALID', f => f.write('operator-private.json', { ...f.operator, operatorCredential: sentinel + token })],
  ['operator-extra-fields', 'OPERATOR_INPUT_INVALID', f => f.write('operator-private.json', { ...f.operator, private: sentinel + token })],
  ['malformed-config', 'LOCAL_CONFIG_INVALID', f => f.write('application-wa050.properties', f.config + sentinel + token)],
  ['remote-database-config', 'LOCAL_CONFIG_INVALID', f => f.write('application-wa050.properties', f.config.replace('127.0.0.1:51221', sentinel + '.invalid:51221') + '# ' + token)],
  ['wildcard-api-config', 'LOCAL_CONFIG_INVALID', f => f.write('application-wa050.properties', f.config.replace('server.address=127.0.0.1', 'server.address=0.0.0.0') + '# ' + sentinel)],
  ['wrong-runtime-directory', 'RUNTIME_SCOPE_INVALID', f => { f.directory = path.join(f.home, sentinel + token); }],
  ['extra-argument', 'EXPECTED_SOURCE_REQUIRED', f => { f.extra = [sentinel + token]; }],
];

for (const [name, code, mutate] of scenarios) {
  test('preflight sanitizes ' + name + ' before any runtime action', () => {
    const f = fixture();
    try {
      mutate(f);
      const result = spawnSync(process.execPath, [script, f.directory ?? f.base, sha, ...(f.extra ?? [])], {
        env: { HOME: f.home }, encoding: 'utf8', timeout: 5000, maxBuffer: 128 * 1024,
      });
      assert.equal(result.error, undefined);
      assert.equal(result.status, 1);
      assert.equal(result.stdout, '');
      assert.deepEqual(JSON.parse(result.stderr), { status: 'FAIL', code });
      assert.equal((result.stdout + result.stderr).includes(sentinel), false);
      assert.equal((result.stdout + result.stderr).includes(token), false);
      assert.equal((result.stdout + result.stderr).includes(f.home), false);
      assert.equal(fs.readdirSync(f.base).some(entry => entry.startsWith('smoke-')), false);
    } finally { fs.rmSync(f.home, { recursive: true, force: true }); }
  });
}

test('missing arguments produce only a closed error', () => {
  const result = spawnSync(process.execPath, [script], { env: {}, encoding: 'utf8', timeout: 5000 });
  assert.equal(result.status, 1); assert.equal(result.stdout, '');
  assert.deepEqual(JSON.parse(result.stderr), { status: 'FAIL', code: 'EXPECTED_SOURCE_REQUIRED' });
});
