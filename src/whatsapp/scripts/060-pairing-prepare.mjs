#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { prepareTls, origin } from './060-pairing-tls.mjs';
import { properties, privateBytes, privateWrite, demand, safeCode } from './070-worker-common.mjs';

export function prepare(directory, source) {
  process.umask(0o077);
  const config = properties(privateBytes(source));
  demand(config.get('vanrides.wa.environment') === 'wa070-local'
    && config.get('server.address') === '127.0.0.1' && config.get('server.port') === '18083'
    && config.get('spring.datasource.url') === 'jdbc:postgresql://127.0.0.1:51222/vanrides_wa070'
    && config.get('vanrides.wa.test-cohort-only') === 'true', 'LAB_CONFIG_SCOPE_INVALID');
  const tls = prepareTls(directory);
  config.set('vanrides.wa.public-base-url', origin);
  const configFile = path.join(directory, 'application-wa060.properties');
  privateWrite(configFile, [...config].map(([key, value]) => key + '=' + value).join('\n') + '\n');
  const manifest = { schemaVersion: 1, origin, upstream: 'http://127.0.0.1:18083', publicCa: tls.publicCa,
    configFile, tlsHashes: tls.hashes, expiresAfterDays: 7, externalActions: false };
  privateWrite(path.join(directory, 'prepare.json'), JSON.stringify(manifest, null, 2) + '\n');
  return manifest;
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const [directory, source, ...extra] = process.argv.slice(2);
    demand(extra.length === 0, 'LAB_ARGUMENTS_INVALID');
    console.log(JSON.stringify({ status: 'PREPARED', ...prepare(directory, source) }));
  } catch (error) { console.error(JSON.stringify({ status: 'FAIL', code: safeCode(error, 'LAB_PREPARATION_FAILED') })); process.exitCode = 1; }
}
