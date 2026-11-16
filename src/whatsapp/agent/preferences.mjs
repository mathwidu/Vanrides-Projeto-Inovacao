import { singleOfferedCommand } from './context-offer.mjs';

export const isPreferenceCommand = command => command === 'SET_PREFERENCE';
const preferences = new Set(['APPROACH_MESSAGES', 'ATTENDANCE_REMINDERS']);

function eligible(context) {
  return context.actorState === 'PASSENGER' && context.step === 'READY'
    && context.registrationStep === 'READY' && context.eventFreshness === 'CURRENT'
    && context.options.length === 0
    && context.allowedCommands.every(command => ['RESPOND_TO_MESSAGE', 'SET_PREFERENCE'].includes(command))
    && singleOfferedCommand(context, isPreferenceCommand) === 'SET_PREFERENCE';
}

export function decidePreference(context, text) {
  const match = typeof text === 'string' && /^(ligar|desligar) avisos (chegada|presenca)$/u.exec(text);
  return eligible(context) && match ? { kind: 'COMMAND', commandType: 'SET_PREFERENCE', payload: {
    preference: match[2] === 'chegada' ? 'APPROACH_MESSAGES' : 'ATTENDANCE_REMINDERS', enabled: match[1] === 'ligar',
  } } : { kind: 'NO_ACTION' };
}

export function validPreferenceDecision(decision, context) {
  return eligible(context) && Object.keys(decision.payload).length === 2
    && Object.hasOwn(decision.payload, 'preference') && Object.hasOwn(decision.payload, 'enabled')
    && preferences.has(decision.payload.preference) && typeof decision.payload.enabled === 'boolean';
}
