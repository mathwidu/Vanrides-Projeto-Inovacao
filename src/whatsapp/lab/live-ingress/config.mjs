import { constants, openSync, fstatSync, readSync, closeSync } from 'node:fs';
import { isAbsolute } from 'node:path';
import { strictJson, exact, timestamp } from './strict-json.mjs';
import { validateHmlConfig } from './hml-config.mjs';

export const pn = value => typeof value === 'string' && /^[0-9]{1,32}@s\.whatsapp\.net$/.test(value);
export const lid = value => typeof value === 'string' && /^[0-9]{1,32}@lid$/.test(value);
const invalid = () => { throw new Error('CONFIG_INVALID'); };
const token = value => typeof value === 'string' && /^[A-Za-z0-9_-]{43}$/.test(value)
  && Buffer.from(value, 'base64url').toString('base64url') === value;

function privateHost(value) {
  if (['localhost', '127.0.0.1', '[::1]', 'host.docker.internal'].includes(value)) return true;
  if (/^[a-z][a-z0-9-]{0,62}$/.test(value)) return true;
  const parts = value.split('.');
  if (parts.length !== 4 || parts.some(part => !/^(0|[1-9][0-9]{0,2})$/.test(part) || Number(part) > 255)) return false;
  const [a, b] = parts.map(Number);
  return a === 10 || a === 172 && b >= 16 && b <= 31 || a === 192 && b === 168;
}

export function validateConfig(raw) {
  try {
    if (Object.hasOwn(raw ?? {}, 'mode')) return validateHmlConfig(raw);
    if (!exact(raw, ['instance', 'webhookToken', 'channelToken', 'apiOrigin', 'startsAt', 'endsAt', 'contacts', 'listenHost', 'listenPort'])
        || typeof raw.instance !== 'string' || !/^[A-Za-z0-9_-]{1,80}$/.test(raw.instance)
        || !token(raw.webhookToken) || !token(raw.channelToken) || raw.webhookToken === raw.channelToken) invalid();
    const written = /^(https?):\/\/(\[[^\]]+\]|[^:/?#\\\s@]+)(?::([0-9]+))?$/.exec(raw.apiOrigin);
    if (!written || !privateHost(written[2]) || written[3] !== undefined && Number(written[3]) < 1) invalid();
    const url = new URL(raw.apiOrigin);
    if (url.origin !== raw.apiOrigin || url.pathname !== '/' || url.username || url.password || url.search || url.hash) invalid();
    const start = timestamp(raw.startsAt), end = timestamp(raw.endsAt);
    if (start === null || end === null || end <= start || end - start > 30 * 60_000) invalid();
    if (!['127.0.0.1', '::1', '0.0.0.0'].includes(raw.listenHost)
        || !Number.isInteger(raw.listenPort) || raw.listenPort < 1 || raw.listenPort > 65535
        || !Array.isArray(raw.contacts) || raw.contacts.length < 1 || raw.contacts.length > 2) invalid();
    const aliases = new Set(), labels = new Set();
    const contacts = raw.contacts.map(contact => {
      const keys = Object.hasOwn(contact ?? {}, 'lid') ? ['label', 'pn', 'lid'] : ['label', 'pn'];
      if (!exact(contact, keys) || !['A', 'B'].includes(contact.label) || labels.has(contact.label)
          || !pn(contact.pn) || keys.includes('lid') && !lid(contact.lid)) invalid();
      labels.add(contact.label);
      const copy = { label: contact.label, pn: contact.pn };
      if (keys.includes('lid')) copy.lid = contact.lid;
      for (const alias of [copy.pn, copy.lid].filter(value => value !== undefined)) {
        if (aliases.has(alias)) invalid();
        aliases.add(alias);
      }
      return Object.freeze(copy);
    });
    const config = { instance: raw.instance, apiOrigin: raw.apiOrigin, startsAt: raw.startsAt, endsAt: raw.endsAt,
      start, end, listenHost: raw.listenHost, listenPort: raw.listenPort };
    // Avoid accidental JSON serialization of the bearer or the private allowlist.
    Object.defineProperties(config, { webhookToken: { value: raw.webhookToken }, channelToken: { value: raw.channelToken },
      contacts: { value: Object.freeze(contacts) } });
    return Object.freeze(config);
  } catch { invalid(); }
}

/** No discovery, environment token fallback or reload. The caller supplies one private file. */
export function loadConfig(file) {
  let fd;
  try {
    if (typeof file !== 'string' || !isAbsolute(file) || file.includes('\0') || typeof process.getuid !== 'function') invalid();
    fd = openSync(file, constants.O_RDONLY | constants.O_NOFOLLOW | constants.O_NONBLOCK);
    const stat = fstatSync(fd);
    if (!stat.isFile() || stat.uid !== process.getuid() || (stat.mode & 0o777) !== 0o600 || stat.size < 1 || stat.size > 8192) invalid();
    const bytes = Buffer.alloc(8193); let count = 0;
    while (count < bytes.length) {
      const size = readSync(fd, bytes, count, bytes.length - count, null);
      if (!size) break;
      count += size;
    }
    if (count < 1 || count > 8192) invalid();
    return validateConfig(strictJson(bytes.subarray(0, count)));
  } catch { invalid(); }
  finally { if (fd !== undefined) closeSync(fd); }
}
