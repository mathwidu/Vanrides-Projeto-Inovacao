import { pn, lid } from './config.mjs';
import { object, timestamp } from './strict-json.mjs';
import { isHml } from './hml-config.mjs';

const STATUSES = new Set(['SERVER_ACK', 'DELIVERY_ACK', 'READ', 'PLAYED', 'ERROR']);
const STATES = new Set(['open', 'connecting', 'close', 'refused']);
const id = value => typeof value === 'string' && /^[\x21-\x7e]{1,160}$/.test(value);
const text = value => typeof value === 'string' && value.trim().length > 0 && [...value].length <= 2000
  && ![...value].some(point => point === '\0' || point.codePointAt(0) >= 0xd800 && point.codePointAt(0) <= 0xdfff);

/** All policy drops are decided before API ingress can resolve or create an identity. */
function evaluateEvent(envelope, config, now, onDrop) {
  const drop = code => { onDrop?.(code); return null; };
  if (!Number.isSafeInteger(now) || now < config.start || now >= config.end) return drop('HML_DROP_WINDOW');
  if (!object(envelope)) return drop('HML_DROP_ENVELOPE_SHAPE');
  if (envelope.instance !== config.instance) return drop('HML_DROP_INSTANCE');
  if (!object(envelope.data)) return drop('HML_DROP_DATA_SHAPE');
  const occurred = timestamp(envelope.date_time);
  if (occurred === null || occurred < config.start || occurred >= config.end || occurred > now + 30_000)
    return drop('HML_DROP_EVENT_TIMESTAMP');
  const data = envelope.data;
  if (envelope.event === 'messages.update') {
    if (data.fromMe !== true) return drop('HML_DROP_DIRECTION');
    if (!id(data.keyId) || !STATUSES.has(data.status)) return drop('HML_DROP_CALLBACK_SHAPE');
    return { lane: 'callback', kind: 'status', envelope: { event: 'messages.update', instance: config.instance,
      date_time: envelope.date_time, data: { keyId: data.keyId, fromMe: true, status: data.status } } };
  }
  if (envelope.event === 'connection.update') {
    if (!STATES.has(data.state)) return drop('HML_DROP_CALLBACK_SHAPE');
    return { lane: 'callback', kind: 'connection', envelope: { event: 'connection.update', instance: config.instance,
      date_time: envelope.date_time, data: { state: data.state } } };
  }
  if (envelope.event !== 'messages.upsert') return drop('HML_DROP_EVENT_KIND');
  if (!object(data.key)) return drop('HML_DROP_MESSAGE_SHAPE');
  if (data.key.fromMe !== false) return drop('HML_DROP_DIRECTION');
  if (!id(data.key.id) || !object(data.message)) return drop('HML_DROP_MESSAGE_SHAPE');
  const key = data.key;
  const aliases = [key.remoteJid];
  if (Object.hasOwn(key, 'remoteJidAlt')) aliases.push(key.remoteJidAlt);
  if (aliases.some(alias => !pn(alias) && !lid(alias))) return drop('HML_DROP_ALIAS_FORMAT');
  const contact = config.contacts.find(candidate => aliases.every(alias => alias === candidate.pn || alias === candidate.lid));
  if (!contact) {
    if (aliases.some(alias => lid(alias) && !config.contacts.some(candidate => candidate.lid === alias)))
      return drop('HML_DROP_LID_UNCONFIRMED');
    if (aliases.some(alias => config.contacts.some(candidate => alias === candidate.pn || alias === candidate.lid)))
      return drop('HML_DROP_ALIAS_CONFLICT');
    return drop('HML_DROP_CONTACT_NOT_ADMITTED');
  }
  // Accept only the two plain text layouts understood by Evolution2.3.7. Mixed and wrapped content fails closed.
  const message = data.message, keys = Object.keys(message);
  let content, messageType, cleanMessage;
  if (keys.length === 1 && keys[0] === 'conversation' && text(message.conversation)) {
    content = message.conversation; messageType = 'conversation';
  } else if (keys.length === 1 && keys[0] === 'extendedTextMessage' && object(message.extendedTextMessage)
      && Object.keys(message.extendedTextMessage).every(field => ['text', 'contextInfo'].includes(field))
      && text(message.extendedTextMessage.text)) {
    content = message.extendedTextMessage.text; messageType = 'extendedTextMessage';
  } else if (isHml(config) && keys.length === 1 && keys[0] === 'locationMessage' && object(message.locationMessage)) {
    const location = message.locationMessage;
    if (!Number.isFinite(location.degreesLatitude) || Math.abs(location.degreesLatitude) > 90
      || !Number.isFinite(location.degreesLongitude) || Math.abs(location.degreesLongitude) > 180
      || Object.hasOwn(location, 'isLive') && location.isLive !== false) return drop('HML_DROP_LOCATION_SHAPE');
    messageType = 'locationMessage';
    cleanMessage = { locationMessage: { degreesLatitude: location.degreesLatitude,
      degreesLongitude: location.degreesLongitude, isLive: false } };
  } else return drop('HML_DROP_MESSAGE_SHAPE');
  if (Object.hasOwn(data, 'messageType') && data.messageType !== messageType) return drop('HML_DROP_MESSAGE_TYPE');
  const cleanKey = { id: key.id, fromMe: false, remoteJid: key.remoteJid };
  if (Object.hasOwn(key, 'remoteJidAlt')) cleanKey.remoteJidAlt = key.remoteJidAlt;
  const cleanData = { key: cleanKey, message: cleanMessage ?? { conversation: content } };
  if (isHml(config)) {
    // Same precedence as WaEvolutionNormalizer; quoted bodies/identity metadata never pass.
    const reply = [data.contextInfo?.stanzaId, message.extendedTextMessage?.contextInfo?.stanzaId,
      message.locationMessage?.contextInfo?.stanzaId].find(id);
    if (reply !== undefined) cleanData.contextInfo = { stanzaId: reply };
  }
  if (Object.hasOwn(data, 'messageTimestamp')) {
    if (!Number.isSafeInteger(data.messageTimestamp) || data.messageTimestamp < 0) return drop('HML_DROP_MESSAGE_TIMESTAMP');
    const time = data.messageTimestamp * 1000;
    if (!Number.isSafeInteger(time) || time < config.start || time >= config.end || time > now + 30_000)
      return drop('HML_DROP_MESSAGE_TIMESTAMP');
    cleanData.messageTimestamp = data.messageTimestamp;
  }
  return { lane: 'human', kind: 'human', contact: contact.label, envelope: { event: 'messages.upsert',
    instance: config.instance, date_time: envelope.date_time, data: cleanData } };
}

export function filterEvent(envelope, config, now) {
  return evaluateEvent(envelope, config, now);
}

/** Fixed enums only. Never return payload values or provide diagnostics for legacy lab. */
export function classifyPolicyDrop(envelope, config, now) {
  if (!isHml(config)) return null;
  let code = null;
  evaluateEvent(envelope, config, now, value => { code = value; });
  return code;
}
