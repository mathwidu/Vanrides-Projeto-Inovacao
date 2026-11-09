import { exact, timestamp } from './strict-json.mjs';

export const HML_MODE = 'HML_V1';
export const isHml = config => config?.mode === HML_MODE;
const invalid = () => { throw new Error('CONFIG_INVALID'); };
const token = value => typeof value === 'string' && /^[A-Za-z0-9_-]{43}$/.test(value)
  && Buffer.from(value, 'base64url').toString('base64url') === value;

/** A private operator document, never an HTTP enrollment/renewal endpoint. */
export function validateHmlConfig(raw) {
  try {
    const dynamic = raw?.admissionMode === 'API';
    if (!exact(raw, ['mode', 'environment', 'instance', 'webhookToken', 'channelToken', 'apiOrigin',
      'startsAt', 'endsAt', 'contacts', 'limitsPerMinute', 'listenHost', 'listenPort',
      ...(Object.hasOwn(raw ?? {}, 'admissionMode') ? ['admissionMode'] : [])])
      || Object.hasOwn(raw ?? {}, 'admissionMode') && !dynamic
      || raw.mode !== HML_MODE || raw.environment !== 'vanrides-mvp-hml'
      || typeof raw.instance !== 'string' || !/^[A-Za-z0-9_-]{1,80}$/.test(raw.instance)
      || !token(raw.webhookToken) || !token(raw.channelToken) || raw.webhookToken === raw.channelToken) invalid();
    if (typeof raw.apiOrigin !== 'string' || raw.apiOrigin.length > 2048
      || !/^https:\/\/[^/?#\\\s@]+$/.test(raw.apiOrigin)) invalid();
    const url = new URL(raw.apiOrigin);
    if (url.protocol !== 'https:' || url.origin !== raw.apiOrigin || !url.hostname || url.port === '0'
      || url.username || url.password || url.search || url.hash || url.pathname !== '/') invalid();
    const start = timestamp(raw.startsAt), end = timestamp(raw.endsAt);
    if (start === null || end === null || end <= start || end - start > 30 * 24 * 60 * 60_000) invalid();
    if (!['127.0.0.1', '::1', '0.0.0.0'].includes(raw.listenHost)
      || !Number.isInteger(raw.listenPort) || raw.listenPort < 1 || raw.listenPort > 65535
      || !Array.isArray(raw.contacts) || raw.contacts.length < (dynamic ? 0 : 1) || raw.contacts.length > 4) invalid();
    const labels = new Set(), aliases = new Set();
    const contacts = raw.contacts.map(contact => {
      const keys = Object.hasOwn(contact ?? {}, 'lid') ? ['label', 'approved', 'pn', 'lid'] : ['label', 'approved', 'pn'];
      if (!exact(contact, keys) || !['A', 'B', 'C', 'D'].includes(contact.label)
        || labels.has(contact.label) || contact.approved !== true
        || typeof contact.pn !== 'string' || !/^[0-9]{1,32}@s\.whatsapp\.net$/.test(contact.pn)
        || keys.includes('lid') && (typeof contact.lid !== 'string' || !/^[0-9]{1,32}@lid$/.test(contact.lid))) invalid();
      labels.add(contact.label);
      const copy = { label: contact.label, approved: true, pn: contact.pn };
      if (keys.includes('lid')) copy.lid = contact.lid;
      for (const alias of [copy.pn, copy.lid].filter(value => value !== undefined)) {
        if (aliases.has(alias)) invalid();
        aliases.add(alias);
      }
      return Object.freeze(copy);
    });
    const limits = raw.limitsPerMinute;
    const caps = { human: 240, perContact: 120, status: 1200, connection: 120 };
    if (!exact(limits, Object.keys(caps)) || Object.entries(caps).some(([key, cap]) =>
      !Number.isSafeInteger(limits[key]) || limits[key] < 1 || limits[key] > cap)
      || limits.perContact > limits.human) invalid();
    const config = { ...(dynamic ? { admissionMode: 'API' } : {}), mode: HML_MODE, environment: raw.environment, instance: raw.instance,
      apiOrigin: raw.apiOrigin, startsAt: raw.startsAt, endsAt: raw.endsAt, start, end,
      listenHost: raw.listenHost, listenPort: raw.listenPort,
      limitsPerMinute: Object.freeze({ ...limits }) };
    Object.defineProperties(config, { webhookToken: { value: raw.webhookToken },
      channelToken: { value: raw.channelToken }, contacts: { value: Object.freeze(contacts) } });
    return Object.freeze(config);
  } catch { invalid(); }
}
