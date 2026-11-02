import fs from 'node:fs';
import crypto from 'node:crypto';

export class SmokeFailure extends Error {
  constructor(code) { super(code); this.code = code; }
}
export const demand = (condition, code) => { if (!condition) throw new SmokeFailure(code); };
export const safeCode = (error, fallback) => error instanceof SmokeFailure ? error.code : fallback;
export const digest = value => crypto.createHash('sha256').update(value).digest('hex');
export const object = value => value !== null && typeof value === 'object' && !Array.isArray(value);
export const exact = (value, keys) => object(value) && Object.keys(value).sort().join('\0') === [...keys].sort().join('\0');
export const uuid = value => typeof value === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(value);
export const token = value => typeof value === 'string' && /^[A-Za-z0-9_-]{43}$/.test(value)
  && Buffer.from(value, 'base64url').toString('base64url') === value;
export const quote = value => "'" + String(value).replaceAll("'", "''") + "'";
export const privateWrite = (file, value) => fs.writeFileSync(file, value, { flag: 'wx', mode: 0o600 });

export function privateBytes(file) {
  let fd;
  try {
    fd = fs.openSync(file, fs.constants.O_RDONLY | fs.constants.O_NOFOLLOW | fs.constants.O_NONBLOCK);
    const stat = fs.fstatSync(fd);
    demand(stat.isFile() && stat.uid === process.getuid() && (stat.mode & 0o777) === 0o600
      && stat.size > 0 && stat.size <= 2 * 1024 * 1024, 'PRIVATE_INPUT_INVALID');
    const bytes = Buffer.alloc(stat.size + 1);
    let length = 0;
    while (length < bytes.length) {
      const read = fs.readSync(fd, bytes, length, bytes.length - length, length);
      if (!read) break;
      length += read;
    }
    demand(length > 0 && length < bytes.length, 'PRIVATE_INPUT_INVALID');
    return bytes.subarray(0, length);
  } catch { throw new SmokeFailure('PRIVATE_INPUT_INVALID'); }
  finally { if (fd !== undefined) fs.closeSync(fd); }
}

export function readPrivate(file) {
  try {
    const value = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(privateBytes(file)));
    demand(object(value), 'PRIVATE_INPUT_INVALID'); return value;
  } catch { throw new SmokeFailure('PRIVATE_INPUT_INVALID'); }
}

export function properties(bytes) {
  const result = new Map();
  for (const line of new TextDecoder('utf-8', { fatal: true }).decode(bytes).split(/\r?\n/)) {
    if (/^\s*(?:[#!]|$)/.test(line)) continue;
    const match = /^\s*([^\s=:]+)\s*[=:]\s*(.*?)\s*$/.exec(line);
    demand(match && /^[A-Za-z0-9_.-]+$/.test(match[1]) && !line.includes('\\') && !result.has(match[1])
      && !match[2].includes('${') && !match[1].startsWith('spring.config.'), 'LOCAL_CONFIG_INVALID');
    result.set(match[1], match[2]);
  }
  return result;
}

/** Loopback only, no redirect/retry, deadline covers the bounded response body. */
export async function localJson(base, route, { method = 'GET', body, headers = {}, timeout = 15000 } = {}) {
  demand(/^http:\/\/127\.0\.0\.1:[1-9][0-9]{0,4}$/.test(base) && new URL(base).port <= 65535,
    'LOCAL_HTTP_ORIGIN_INVALID');
  demand(typeof route === 'string' && /^\/[A-Za-z0-9/_-]+$/.test(route), 'LOCAL_HTTP_ROUTE_INVALID');
  const controller = new AbortController();
  let timer, response;
  try {
    return await Promise.race([(async () => {
      response = await fetch(base + route, { method, redirect: 'error', signal: controller.signal,
        headers: { ...headers, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
        ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
      const reader = response.body?.getReader();
      demand(reader, 'LOCAL_HTTP_RESPONSE_INVALID');
      const chunks = []; let size = 0;
      try {
        while (true) {
          const part = await reader.read();
          if (part.done) break;
          size += part.value.byteLength;
          demand(size <= 32768, 'LOCAL_HTTP_RESPONSE_TOO_LARGE'); chunks.push(part.value);
        }
        const data = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(Buffer.concat(chunks, size)));
        demand(object(data), 'LOCAL_HTTP_RESPONSE_INVALID');
        return { status: response.status, data };
      } finally { await reader.cancel().catch(() => {}); }
    })(), new Promise((_, reject) => {
      timer = setTimeout(() => { controller.abort(); reject(new SmokeFailure('LOCAL_HTTP_TIMEOUT')); }, timeout);
    })]);
  } catch (error) { throw error instanceof SmokeFailure ? error : new SmokeFailure('LOCAL_HTTP_FAILED'); }
  finally { clearTimeout(timer); controller.abort(); }
}

export function summary(value, kind, status) {
  const names = kind === 'process' ? ['claimed', 'processed', 'retried', 'paused']
    : ['claimed', 'submitted', 'unknown', 'failed'];
  demand(exact(value, ['status', ...names]) && value.status === status
    && names.every(name => value[name] === (name === 'claimed' && !['IDLE', 'BUSY'].includes(status)
      || name === 'processed' && status === 'PROCESSED' || name === 'submitted' && status === 'SUBMITTED'
      || name === 'unknown' && status === 'UNKNOWN' || name === 'failed' && status === 'FAILED' ? 1 : 0)),
  'CONTROL_SUMMARY_INVALID');
}
