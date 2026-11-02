// Product rules independent of any language-model provider. No I/O or secrets.
import { DRIVER_PAYLOADS, decideDriverSetup, isDriverCommand, validContextOptions, validDriverDecision } from './driver-setup.mjs';
import { PASSENGER_PAYLOADS, decidePassengerSetup, isPassengerCommand, validPassengerDecision } from './passenger-setup.mjs';
import { decideAttendance, isAttendanceChoice, isAttendanceCommand, validAttendanceDecision } from './attendance.mjs';

const commands = new Set([
  'START_ONBOARDING', 'SET_PROFILE_NAME', 'ACCEPT_NOTICE', 'CLAIM_DRIVER_INVITE',
  'CONFIRM_PAIRING', 'REQUEST_SUPPORT', 'RESPOND_TO_MESSAGE', 'ADVANCE_ATTENDANCE', 'SET_ATTENDANCE',
  ...Object.keys(DRIVER_PAYLOADS), ...Object.keys(PASSENGER_PAYLOADS),
]);
const runCommands = new Set(['ADVANCE_RUN_SETUP', 'REQUEST_START', 'REQUEST_RESUME', 'SET_OPERATIONAL_ATTENDANCE']);
const isRunCommand = value => runCommands.has(value);
const steps = new Set(['WELCOME', 'ASK_NAME', 'ASK_NOTICE', 'PAIRING', 'READY', 'PAUSED']);
const kinds = new Set(['TEXT', 'REPLY', 'UNSUPPORTED', 'STATIC_PIN_REFERENCE']);
const responseCodes = new Set(['HELP', 'CLARIFY', 'UNSUPPORTED_CONTENT', 'STATIC_PIN_REQUIRED',
  'FEATURE_PENDING', 'STALE_MESSAGE', 'CONTINUE', 'DRIVER_INVITE_REQUIRED']);
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
export const normalizeText = value => value.normalize('NFD').replace(/\p{M}/gu, '')
  .toLocaleLowerCase('pt-BR').trim().replace(/\s+/gu, ' ');

export function validateContext(context, eventId) {
  if (!context || typeof context !== 'object' || Array.isArray(context)
      || !uuid.test(eventId) || context.inboxEventId !== eventId
      || !Number.isSafeInteger(context.entityVersion) || context.entityVersion < 0
      || !['UNREGISTERED', 'PASSENGER', 'DRIVER'].includes(context.actorState)
      || !['ONBOARDING', 'PAIRING', 'READY', 'PAUSED'].includes(context.step)
      || !steps.has(context.registrationStep)
      || !['CURRENT', 'STALE', 'UNKNOWN'].includes(context.eventFreshness)
      || !Array.isArray(context.allowedCommands) || context.allowedCommands.length > 31
      || !context.allowedCommands.every(x => typeof x === 'string')
      || new Set(context.allowedCommands).size !== context.allowedCommands.length
      || !validContextOptions(context.options)
      || Object.hasOwn(context, 'attendanceChoice') && (!isAttendanceChoice(context.attendanceChoice)
        || !context.allowedCommands.includes('SET_ATTENDANCE'))
      || !Number.isFinite(Date.parse(context.serverTime))) throw new Error('INVALID_CONTEXT');
  const permitted = new Set(['inboxEventId', 'entityVersion', 'actorState', 'step',
    'registrationStep', 'eventFreshness', 'allowedCommands', 'options', 'serverTime', 'attendanceChoice']);
  if (Object.keys(context).some(key => !permitted.has(key))) throw new Error('INVALID_CONTEXT');
}

export function validateMessage(message) {
  if (!message || typeof message !== 'object' || Array.isArray(message)
      || !kinds.has(message.type) || typeof message.sourceMessageId !== 'string'
      || !/^event_[0-9a-f-]{36}$/i.test(message.sourceMessageId)
      || Object.keys(message).some(key => !['type', 'sourceMessageId', 'text', 'replyTo'].includes(key)))
    throw new Error('INVALID_MESSAGE');
  if (message.text !== undefined && (typeof message.text !== 'string'
      || [...message.text].length < 1 || [...message.text].length > 2000
      || !['TEXT', 'REPLY'].includes(message.type))) throw new Error('INVALID_MESSAGE');
  if (message.replyTo !== undefined && (typeof message.replyTo !== 'string'
      || !/^event_[0-9a-f-]{36}$/i.test(message.replyTo))) throw new Error('INVALID_MESSAGE');
}

// Extract intent only. Occurrence/date authorization remains a later API handler.
export function attendanceIntent(text) {
  const normalized = normalizeText(text).replace(/[.!]$/u, '');
  const match = /^(nao vou|vou)(?: (hoje|amanha))?(?: (?:na |de )?(ida|volta))?$/u.exec(normalized);
  return match ? { attendance: match[1] === 'vou' ? 'CONFIRMED' : 'NOT_GOING',
    day: match[2] === 'hoje' ? 'TODAY' : match[2] === 'amanha' ? 'TOMORROW' : null,
    direction: match[3]?.toUpperCase() ?? null } : null;
}

export function validateDecision(decision, context) {
  if (!decision || decision.kind !== 'COMMAND' || !commands.has(decision.commandType)
      || !context.allowedCommands.includes(decision.commandType)
      || Object.keys(decision).some(key => !['kind', 'commandType', 'payload'].includes(key))
      || !decision.payload || typeof decision.payload !== 'object' || Array.isArray(decision.payload))
    throw new Error('INVALID_DECISION');
  const payload = decision.payload;
  const exact = fields => Object.keys(payload).length === fields.length && fields.every(key => Object.hasOwn(payload, key));
  let valid = false;
  switch (decision.commandType) {
    case 'START_ONBOARDING': case 'CLAIM_DRIVER_INVITE': case 'CONFIRM_PAIRING': valid = exact([]); break;
    case 'SET_PROFILE_NAME': valid = exact(['name']) && typeof payload.name === 'string'
      && [...payload.name].length >= 1 && [...payload.name].length <= 80; break;
    case 'ACCEPT_NOTICE': valid = exact(['noticeVersion']) && payload.noticeVersion === 'wa-mvp-v1'; break;
    case 'REQUEST_SUPPORT': valid = exact(['reason', 'detail']) && ['HELP', 'PAIRING', 'LOCATION', 'OTHER'].includes(payload.reason)
      && typeof payload.detail === 'string' && payload.detail.length >= 1 && payload.detail.length <= 240; break;
    case 'RESPOND_TO_MESSAGE': valid = exact(['responseCode']) && responseCodes.has(payload.responseCode); break;
    default: valid = isDriverCommand(decision.commandType) && validDriverDecision(decision, context)
      || isPassengerCommand(decision.commandType) && validPassengerDecision(decision, context)
      || isAttendanceCommand(decision.commandType) && validAttendanceDecision(decision, context);
  }
  if (!valid || decision.commandType !== 'RESPOND_TO_MESSAGE' && context.eventFreshness !== 'CURRENT')
    throw new Error('INVALID_DECISION');
}

export function decideDeterministically(message, context) {
  validateContext(context, context?.inboxEventId);
  validateMessage(message);
  const allowed = new Set(context.allowedCommands);
  const command = (commandType, payload = {}) => {
    if (!commands.has(commandType) || !allowed.has(commandType)) return { kind: 'NO_ACTION' };
    return { kind: 'COMMAND', commandType, payload };
  };
  const reply = responseCode => command('RESPOND_TO_MESSAGE', { responseCode });
  if (context.step === 'PAUSED' || context.registrationStep === 'PAUSED') return { kind: 'NO_ACTION' };
  if (context.eventFreshness !== 'CURRENT')
    return reply(message.type === 'UNSUPPORTED' && !context.allowedCommands.some(isPassengerCommand)
      ? 'UNSUPPORTED_CONTENT' : 'STALE_MESSAGE');
  const text = typeof message.text === 'string' ? normalizeText(message.text).replace(/[.!?]$/u, '') : null;
  if (['ajuda', 'help', 'menu', 'o que voce faz'].includes(text)) return reply('HELP');
  if (['falar com suporte', 'quero falar com suporte', 'falar com atendente',
    'quero atendimento humano', 'atendimento humano'].includes(text))
    return command('REQUEST_SUPPORT', { reason: 'HELP', detail: 'Pedido explícito na mensagem autenticada.' });
  // The server recognizes the invitation and interprets every private draft field.
  // Its offer must be copied even when the guest has no profile or text projection.
  // Even private replies without a text projection can advance an API-bound run review.
  if (context.allowedCommands.some(isRunCommand)) return decideDriverSetup(context);
  if (context.allowedCommands.some(isAttendanceCommand)) return decideAttendance(context);
  if (context.allowedCommands.some(isPassengerCommand)) return decidePassengerSetup(context);
  // The API advertises these only when a matching hash belongs to this event.
  if (context.eventFreshness === 'CURRENT') {
    if (allowed.has('CLAIM_DRIVER_INVITE')) return command('CLAIM_DRIVER_INVITE');
    if (allowed.has('CONFIRM_PAIRING')) return command('CONFIRM_PAIRING');
  }
  if (message.type === 'UNSUPPORTED') return reply('UNSUPPORTED_CONTENT');
  if (message.type === 'STATIC_PIN_REFERENCE') return decideDriverSetup(context) ?? reply('FEATURE_PENDING');
  if (typeof message.text !== 'string') return reply('CLARIFY');
  const driver = decideDriverSetup(context);
  if (driver) return driver;
  const passenger = decidePassengerSetup(context);
  if (passenger) return passenger;
  // A copied/expired confirmation marker is never a free-text model request.
  if (/^confirmar [a-z]{8}$/u.test(text)) return reply('CONTINUE');
  if (['continuar', 'continuar cadastro', 'voltar', 'nao aceito'].includes(text)) return reply('CONTINUE');
  if (['sou motorista', 'quero ser motorista', 'cadastro de motorista'].includes(text))
    return reply(context.actorState === 'DRIVER' ? 'CONTINUE' : 'DRIVER_INVITE_REQUIRED');
  if (attendanceIntent(message.text)) return reply('FEATURE_PENDING');
  if (text !== 'iniciar cadastro'
      && /^(iniciar|comecar|finalizar|parar|cancelar|apagar|excluir|alterar ponto|onde esta)\b/u.test(text))
    return reply('FEATURE_PENDING');
  if (context.registrationStep === 'WELCOME') {
    if (['oi', 'ola', 'bom dia', 'boa tarde', 'boa noite', 'cadastrar', 'quero me cadastrar',
      'sou passageiro', 'cadastro', 'iniciar cadastro'].includes(text)) return command('START_ONBOARDING');
    return reply('CLARIFY');
  }
  if (context.registrationStep === 'ASK_NOTICE')
    return text === 'aceito' && allowed.has('ACCEPT_NOTICE')
      ? command('ACCEPT_NOTICE', { noticeVersion: 'wa-mvp-v1' }) : reply('CONTINUE');
  if (context.registrationStep === 'ASK_NAME') {
    const name = message.text.trim().replace(/^(?:me chamo|meu nome [ée]|pode me chamar de)\s+/iu, '');
    const words = normalizeText(name).split(' ');
    const controlWords = new Set(['ignore', 'instrucoes', 'instrucao', 'sistema', 'admin', 'administrador',
      'motorista', 'passageiro', 'usuario', 'mude', 'troque', 'quero', 'nao', 'aceito', 'senha', 'token']);
    if ([...name].length <= 80 && words.length <= 6 && !words.some(word => controlWords.has(word))
        && /^\p{L}[\p{L}\p{M} '’-]*$/u.test(name)) return command('SET_PROFILE_NAME', { name });
  }
  return reply('CLARIFY');
}
