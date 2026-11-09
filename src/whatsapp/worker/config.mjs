import { constants, closeSync, fstatSync, openSync, readSync } from 'node:fs';
import { isAbsolute } from 'node:path';
import { Buffer } from 'node:buffer';
import { invalidConfig, WorkerError } from './errors.mjs';

export function isToken(value) {
  return typeof value === 'string' && /^[A-Za-z0-9_-]{43}$/.test(value)
    && Buffer.from(value, 'base64url').toString('base64url') === value;
}

function privateHost(value) {
  if (['localhost', '127.0.0.1', '[::1]', 'host.docker.internal'].includes(value)) return true;
  // Explicit single-label Docker DNS names, never public FQDNs or alternate integer IPv4 forms.
  if (/^[a-z][a-z0-9-]{0,62}$/.test(value)) return true;
  const parts = value.split('.');
  if (parts.length !== 4 || parts.some(part => !/^(0|[1-9][0-9]{0,2})$/.test(part) || Number(part) > 255)) return false;
  const [a, b] = parts.map(Number);
  return a === 10 || a === 172 && b >= 16 && b <= 31 || a === 192 && b === 168;
}

export function trustedOrigin(value, { localMode = false, allowedHttpHosts = [] } = {}) {
  try {
    if (typeof value !== 'string' || value.length > 2048 || !Array.isArray(allowedHttpHosts)
        || allowedHttpHosts.some(host => typeof host !== 'string' || !privateHost(host))) invalidConfig();
    const written = /^(https?):\/\/(\[[^\]]+\]|[^:/?#\\\s@]+)(?::([0-9]+))?\/?$/.exec(value);
    if (!written) invalidConfig();
    const url = new URL(value);
    if (url.username || url.password || url.search || url.hash || url.pathname !== '/'
        || written[3] !== undefined && Number(written[3]) < 1) invalidConfig();
    if (url.protocol !== 'https:' && !(url.protocol === 'http:' && localMode === true
        && privateHost(written[2]) && allowedHttpHosts.includes(written[2]))) invalidConfig();
    return url.origin;
  } catch { throw new WorkerError('CONFIG_INVALID'); }
}

export function privateSecret(file) {
  let fd;
  try {
    if (typeof file !== 'string' || !isAbsolute(file) || file.includes('\0') || typeof process.getuid !== 'function') invalidConfig();
    fd = openSync(file, constants.O_RDONLY | constants.O_NOFOLLOW | constants.O_NONBLOCK);
    const stat = fstatSync(fd);
    if (!stat.isFile() || stat.uid !== process.getuid() || (stat.mode & 0o777) !== 0o600 || stat.size < 1 || stat.size > 4097) invalidConfig();
    const bytes = Buffer.alloc(4098);
    const length = readSync(fd, bytes, 0, bytes.length, 0);
    if (length < 1 || length > 4097) invalidConfig();
    return new TextDecoder('utf-8', { fatal: true }).decode(bytes.subarray(0, length)).replace(/\r?\n$/, '');
  } catch { throw new WorkerError('CONFIG_INVALID'); }
  finally { if (fd !== undefined) { try { closeSync(fd); } catch { throw new WorkerError('CONFIG_INVALID'); } } }
}

/** Explicit lab/MVP files only; no secret discovery or plaintext env fallback. */
export function loadConfig(env = process.env) {
  try {
    const environment = env.WA070_ENVIRONMENT;
    if (!['wa070-local', 'vanrides-mvp-hml'].includes(environment)) invalidConfig();
    if (env.WA070_LOCAL_MODE !== undefined && !['true', 'false'].includes(env.WA070_LOCAL_MODE)) invalidConfig();
    const localMode = env.WA070_LOCAL_MODE === 'true';
    const allowedHttpHosts = env.WA070_HTTP_HOSTS ? env.WA070_HTTP_HOSTS.split(',') : [];
    // The published MVP never inherits the lab's cleartext exception.
    if (environment === 'vanrides-mvp-hml' && (localMode || allowedHttpHosts.length !== 0)) invalidConfig();
    const backendBaseUrl = trustedOrigin(env.WA070_BACKEND_BASE_URL, { localMode, allowedHttpHosts });
    const evolutionBaseUrl = trustedOrigin(env.WA070_EVOLUTION_BASE_URL, { localMode, allowedHttpHosts });
    if (environment === 'vanrides-mvp-hml'
        && (!backendBaseUrl.startsWith('https:') || !evolutionBaseUrl.startsWith('https:'))) invalidConfig();
    if (evolutionBaseUrl.startsWith('http:') && !['127.0.0.1', 'localhost', '[::1]'].includes(new URL(evolutionBaseUrl).hostname)) invalidConfig();
    if (!/^[A-Za-z0-9_-]{1,128}$/.test(env.WA070_INSTANCE ?? '')) invalidConfig();
    const host = env.WA070_HOST ?? '127.0.0.1';
    const portValue = env.WA070_PORT ?? '7080';
    if (!['127.0.0.1', '::1', '0.0.0.0'].includes(host) || !/^[1-9][0-9]{0,4}$/.test(portValue)
        || Number(portValue) > 65535) invalidConfig();
    const secrets = {};
    for (const [property, variable] of Object.entries({ conversationToken: 'CONVERSATION_TOKEN',
      deliveryToken: 'DELIVERY_TOKEN', controlToken: 'CONTROL_TOKEN', evolutionApiKey: 'EVOLUTION_API_KEY' })) {
      if (env[`WA070_${variable}`] !== undefined) invalidConfig();
      secrets[property] = privateSecret(env[`WA070_${variable}_FILE`]);
    }
    if (env.WA100_JOB_TOKEN !== undefined) invalidConfig();
    if (env.WA100_JOB_TOKEN_FILE !== undefined) {
      secrets.jobToken = privateSecret(env.WA100_JOB_TOKEN_FILE);
      if (!isToken(secrets.jobToken)) invalidConfig();
    }
    if (![secrets.conversationToken, secrets.deliveryToken, secrets.controlToken].every(isToken)
        || !/^[\x21-\x7e]{1,4096}$/.test(secrets.evolutionApiKey)
        || new Set(Object.values(secrets)).size !== Object.keys(secrets).length) invalidConfig();
    const config = { environment, backendBaseUrl, evolutionBaseUrl, instance: env.WA070_INSTANCE,
      host, port: Number(portValue), localMode, allowedHttpHosts: Object.freeze([...allowedHttpHosts]) };
    for (const [name, value] of Object.entries(secrets)) Object.defineProperty(config, name, { value, enumerable: false });
    return Object.freeze(config);
  } catch { throw new WorkerError('CONFIG_INVALID'); }
}
