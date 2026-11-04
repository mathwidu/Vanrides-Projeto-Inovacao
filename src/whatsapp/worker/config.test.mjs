import assert from 'node:assert/strict';
import { Buffer } from 'node:buffer';
import { chmodSync, mkdtempSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { loadConfig, trustedOrigin } from './config.mjs';

function fixture(t) {
  const directory = mkdtempSync(join(tmpdir(), 'wa070-config-'));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const env = { WA070_ENVIRONMENT: 'wa070-local', WA070_LOCAL_MODE: 'true',
    WA070_HTTP_HOSTS: '127.0.0.1,localhost,backend,host.docker.internal',
    WA070_BACKEND_BASE_URL: 'http://127.0.0.1:18082', WA070_EVOLUTION_BASE_URL: 'http://127.0.0.1:18084',
    WA070_INSTANCE: 'synthetic_070' };
  const values = { CONVERSATION_TOKEN: Buffer.alloc(32, 1).toString('base64url'),
    DELIVERY_TOKEN: Buffer.alloc(32, 2).toString('base64url'), CONTROL_TOKEN: Buffer.alloc(32, 3).toString('base64url'),
    EVOLUTION_API_KEY: 'synthetic-evolution-api-key' };
  for (const [name, value] of Object.entries(values)) {
    const file = join(directory, name);
    writeFileSync(file, value + '\n', { mode: 0o600 });
    env[`WA070_${name}_FILE`] = file;
  }
  return { env, values, directory };
}

function invalid(action) {
  assert.throws(action, error => error.code === 'CONFIG_INVALID' && error.message === 'WA070_CONFIG_INVALID' && error.cause === undefined);
}

test('loads only explicit private files and keeps role secrets out of config serialization', t => {
  const { env, values } = fixture(t);
  const config = loadConfig(env);
  assert.equal(config.conversationToken, values.CONVERSATION_TOKEN);
  assert.equal(config.deliveryToken, values.DELIVERY_TOKEN);
  assert.equal(config.controlToken, values.CONTROL_TOKEN);
  assert.equal(config.evolutionApiKey, values.EVOLUTION_API_KEY);
  assert.equal(config.port, 7080);
  assert.equal(config.host, '127.0.0.1');
  assert.equal(Object.isFrozen(config), true);
  for (const value of Object.values(values)) assert.equal(JSON.stringify(config).includes(value), false);
});

test('rejects permissions other than 0600 and final symlinks', t => {
  const { env, directory } = fixture(t);
  const file = env.WA070_CONTROL_TOKEN_FILE;
  chmodSync(file, 0o644);
  invalid(() => loadConfig(env));
  chmodSync(file, 0o600);
  const link = join(directory, 'control-link');
  symlinkSync(file, link);
  invalid(() => loadConfig({ ...env, WA070_CONTROL_TOKEN_FILE: link }));
});

test('rejects absent, relative, oversized and noncanonical secret files', t => {
  const { env } = fixture(t);
  invalid(() => loadConfig({ ...env, WA070_CONTROL_TOKEN_FILE: undefined }));
  invalid(() => loadConfig({ ...env, WA070_CONTROL_TOKEN_FILE: 'relative-private-file' }));
  writeFileSync(env.WA070_CONTROL_TOKEN_FILE, 'x'.repeat(5000));
  invalid(() => loadConfig(env));
  writeFileSync(env.WA070_CONTROL_TOKEN_FILE, 'A'.repeat(42) + 'B');
  invalid(() => loadConfig(env));
});

test('rejects plaintext env fallback, duplicate principals and foreign environment', t => {
  const { env, values } = fixture(t);
  invalid(() => loadConfig({ ...env, WA070_DELIVERY_TOKEN: values.DELIVERY_TOKEN }));
  invalid(() => loadConfig({ ...env, WA070_ENVIRONMENT: 'production' }));
  invalid(() => loadConfig({ ...env, WA070_CONTROL_TOKEN_FILE: env.WA070_DELIVERY_TOKEN_FILE }));
  invalid(() => loadConfig({ ...env, WA070_EVOLUTION_API_KEY_FILE: env.WA070_DELIVERY_TOKEN_FILE }));
});

test('fails closed on bad instance, bind address, port or local mode', t => {
  const { env } = fixture(t);
  for (const patch of [{ WA070_INSTANCE: '../foreign' }, { WA070_HOST: '192.0.2.1' }, { WA070_PORT: '0' },
    { WA070_PORT: '65536' }, { WA070_LOCAL_MODE: 'yes' }, { WA070_LOCAL_MODE: undefined }]) invalid(() => loadConfig({ ...env, ...patch }));
});

test('permits HTTP only with explicit local mode and exact private host allowlist', () => {
  assert.equal(trustedOrigin('https://synthetic.example.test'), 'https://synthetic.example.test');
  assert.equal(trustedOrigin('http://backend:8080', { localMode: true, allowedHttpHosts: ['backend'] }), 'http://backend:8080');
  for (const url of ['http://backend:8080', 'http://127.0.0.1:8080']) invalid(() => trustedOrigin(url));
  invalid(() => trustedOrigin('http://other-backend:8080', { localMode: true, allowedHttpHosts: ['backend'] }));
  invalid(() => trustedOrigin('http://example.test', { localMode: true, allowedHttpHosts: ['example.test'] }));
  invalid(() => trustedOrigin('http://127.1', { localMode: true, allowedHttpHosts: ['127.0.0.1'] }));
  invalid(() => trustedOrigin('http://2130706433', { localMode: true, allowedHttpHosts: ['127.0.0.1'] }));
});

test('rejects URL paths, userinfo, query, fragment and Evolution HTTP Docker DNS', t => {
  for (const url of ['https://user:secret@example.test', 'https://@example.test', 'https://example.test/path',
    'https://example.test?', 'https://example.test#', 'https://example.test/?a=b', ' https://example.test']) invalid(() => trustedOrigin(url));
  const { env } = fixture(t);
  invalid(() => loadConfig({ ...env, WA070_EVOLUTION_BASE_URL: 'http://backend:8080' }));
});

test('attendance uses a separate optional 0600 SYSTEM_JOB credential and rejects plaintext or reused roles', t => {
  const { env, directory } = fixture(t);
  assert.equal(loadConfig(env).jobToken, undefined);
  const file = join(directory, 'job');
  const marker = Buffer.alloc(32, 17).toString('base64url');
  writeFileSync(file, marker + '\n', { mode: 0o600 });
  const config = loadConfig({ ...env, WA100_JOB_TOKEN_FILE: file });
  assert.equal(config.jobToken, marker); assert.ok(!JSON.stringify(config).includes(marker));
  invalid(() => loadConfig({ ...env, WA100_JOB_TOKEN: marker }));
  invalid(() => loadConfig({ ...env, WA100_JOB_TOKEN_FILE: env.WA070_CONVERSATION_TOKEN_FILE }));
});


test('MVP homologation accepts explicit HTTPS origins and keeps secrets private', t => {
  const { env, values } = fixture(t);
  const config = loadConfig({ ...env, WA070_ENVIRONMENT: 'vanrides-mvp-hml',
    WA070_LOCAL_MODE: 'false', WA070_HTTP_HOSTS: '',
    WA070_BACKEND_BASE_URL: 'https://vanrides-inovacao-api.matheusduarte.dev.br',
    WA070_EVOLUTION_BASE_URL: 'https://evolution.private.example.test',
    WA070_HOST: '0.0.0.0' });
  assert.equal(config.environment, 'vanrides-mvp-hml');
  assert.equal(config.localMode, false);
  assert.deepEqual(config.allowedHttpHosts, []);
  assert.equal(config.backendBaseUrl, 'https://vanrides-inovacao-api.matheusduarte.dev.br');
  assert.equal(config.evolutionBaseUrl, 'https://evolution.private.example.test');
  assert.equal(config.host, '0.0.0.0');
  for (const value of Object.values(values)) assert.equal(JSON.stringify(config).includes(value), false);
});

test('MVP homologation rejects the lab HTTP exceptions and malformed HTTPS origins', t => {
  const { env } = fixture(t);
  const online = { ...env, WA070_ENVIRONMENT: 'vanrides-mvp-hml',
    WA070_LOCAL_MODE: 'false', WA070_HTTP_HOSTS: '',
    WA070_BACKEND_BASE_URL: 'https://vanrides-inovacao-api.matheusduarte.dev.br',
    WA070_EVOLUTION_BASE_URL: 'https://evolution.private.example.test' };
  for (const patch of [
    { WA070_LOCAL_MODE: 'true' }, { WA070_HTTP_HOSTS: 'backend' },
    { WA070_BACKEND_BASE_URL: 'http://backend:8080' },
    { WA070_EVOLUTION_BASE_URL: 'http://127.0.0.1:8080' },
    { WA070_EVOLUTION_BASE_URL: 'http://evolution:8080' },
    { WA070_EVOLUTION_BASE_URL: 'https://evolution.private.example.test/path' },
    { WA070_BACKEND_BASE_URL: 'https://user:secret@example.test' },
  ]) invalid(() => loadConfig({ ...online, ...patch }));
  assert.equal(loadConfig({ ...online, WA070_LOCAL_MODE: undefined, WA070_HTTP_HOSTS: undefined }).environment,
    'vanrides-mvp-hml');
});

test('MVP homologation preserves non-root file modes and rejects plaintext or shared principals', t => {
  const { env, values } = fixture(t);
  const online = { ...env, WA070_ENVIRONMENT: 'vanrides-mvp-hml',
    WA070_LOCAL_MODE: 'false', WA070_HTTP_HOSTS: '',
    WA070_BACKEND_BASE_URL: 'https://vanrides-inovacao-api.matheusduarte.dev.br',
    WA070_EVOLUTION_BASE_URL: 'https://evolution.private.example.test' };
  for (const mode of [0o444, 0o400, 0o640]) {
    chmodSync(env.WA070_CONTROL_TOKEN_FILE, mode);
    invalid(() => loadConfig(online));
  }
  chmodSync(env.WA070_CONTROL_TOKEN_FILE, 0o600);
  assert.equal(loadConfig(online).controlToken, values.CONTROL_TOKEN);
  invalid(() => loadConfig({ ...online, WA070_CONTROL_TOKEN: values.CONTROL_TOKEN }));
  invalid(() => loadConfig({ ...online, WA070_DELIVERY_TOKEN_FILE: env.WA070_CONVERSATION_TOKEN_FILE }));
});
