import { pn, lid } from './config.mjs';
import { object, timestamp } from './strict-json.mjs';

const STATUSES = new Set(['SERVER_ACK', 'DELIVERY_ACK', 'READ', 'PLAYED', 'ERROR']);
const STATES = new Set(['open', 'connecting', 'close', 'refused']);
const id = value => typeof value === 'string' && /^[\x21-\x7e]{1,160}$/.test(value);
const text = value => typeof value === 'string' && value.trim().length > 0 && [...value].length <= 2000
  && ![...value].some(point => point === '\0' || point.codePointAt(0) >= 0xd800 && point.codePointAt(0) <= 0xdfff);

/** All policy drops are decided before API ingress can resolve or create an identity. */
export function filterEvent(envelope, config, now) {
  if (!Number.isSafeInteger(now) || now < config.start || now >= config.end || !object(envelope)
      || envelope.instance !== config.instance || !object(envelope.data)) return null;
  const occurred = timestamp(envelope.date_time);
  if (occurred === null || occurred < config.start || occurred >= config.end || occurred > now + 30_000) return null;
  const data = envelope.data;
  if (envelope.event === 'messages.update') {
    if (!id(data.keyId) || data.fromMe !== true || !STATUSES.has(data.status)) return null;
    return { lane: 'callback', kind: 'status', envelope: { event: 'messages.update', instance: config.instance,
      date_time: envelope.date_time, data: { keyId: data.keyId, fromMe: true, status: data.status } } };
  }
  if (envelope.event === 'connection.update') {
    if (!STATES.has(data.state)) return null;
    return { lane: 'callback', kind: 'connection', envelope: { event: 'connection.update', instance: config.instance,
      date_time: envelope.date_time, data: { state: data.state } } };
  }
  if (envelope.event !== 'messages.upsert' || !object(data.key) || data.key.fromMe !== false || !id(data.key.id)
      || !object(data.message)) return null;
  const key = data.key;
  const aliases = [key.remoteJid];
  if (Object.hasOwn(key, 'remoteJidAlt')) aliases.push(key.remoteJidAlt);
  if (aliases.some(alias => !pn(alias) && !lid(alias))) return null;
  const contact = config.contacts.find(candidate => aliases.every(alias => alias === candidate.pn || alias === candidate.lid));
  if (!contact) return null;
  // Accept only the two plain text layouts understood by Evolution2.3.7. Mixed and wrapped content fails closed.
  const message = data.message, keys = Object.keys(message);
  let content, messageType;
  if (keys.length === 1 && keys[0] === 'conversation' && text(message.conversation)) {
    content = message.conversation; messageType = 'conversation';
  } else if (keys.length === 1 && keys[0] === 'extendedTextMessage' && object(message.extendedTextMessage)
      && Object.keys(message.extendedTextMessage).every(field => ['text', 'contextInfo'].includes(field))
      && text(message.extendedTextMessage.text)) {
    content = message.extendedTextMessage.text; messageType = 'extendedTextMessage';
  } else return null;
  if (Object.hasOwn(data, 'messageType') && data.messageType !== messageType) return null;
  const cleanKey = { id: key.id, fromMe: false, remoteJid: key.remoteJid };
  if (Object.hasOwn(key, 'remoteJidAlt')) cleanKey.remoteJidAlt = key.remoteJidAlt;
  const cleanData = { key: cleanKey, message: { conversation: content } };
  if (Object.hasOwn(data, 'messageTimestamp')) {
    if (!Number.isSafeInteger(data.messageTimestamp) || data.messageTimestamp < 0) return null;
    const time = data.messageTimestamp * 1000;
    if (!Number.isSafeInteger(time) || time < config.start || time >= config.end || time > now + 30_000) return null;
    cleanData.messageTimestamp = data.messageTimestamp;
  }
  return { lane: 'human', kind: 'human', contact: contact.label, envelope: { event: 'messages.upsert',
    instance: config.instance, date_time: envelope.date_time, data: cleanData } };
}
