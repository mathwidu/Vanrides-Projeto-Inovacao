import { singleOfferedCommand, payloadFromOptions, matchesOfferedPayload } from './context-offer.mjs';

export const PASSENGER_PAYLOADS = Object.freeze(Object.fromEntries(Object.entries({
  ADVANCE_PASSENGER_SETUP: {},
  REQUEST_LINK: { confirmationRef: 'CONFIRMATION' },
  APPROVE_LINK: { linkRef: 'LINK', pickupRef: 'PICKUP', confirmationRef: 'CONFIRMATION' },
  CLOSE_LINK: { linkRef: 'LINK', confirmationRef: 'CONFIRMATION' },
  PROPOSE_PICKUP: { sourceMessageId: 'QUESTION', confirmationRef: 'CONFIRMATION' },
  APPROVE_PICKUP: { pickupRef: 'PICKUP', confirmationRef: 'CONFIRMATION' },
}).map(([command, fields]) => [command, Object.freeze(fields)])));

export const isPassengerCommand = type => Object.hasOwn(PASSENGER_PAYLOADS, type);
const setupKinds = new Set(['LINK', 'PICKUP', 'QUESTION', 'CONFIRMATION']);
const registeredStages = context => ['PAIRING', 'READY'].includes(context.step)
  && ['PAIRING', 'READY'].includes(context.registrationStep);

function offered(context) {
  if (context.eventFreshness !== 'CURRENT') return null;
  const command = singleOfferedCommand(context, isPassengerCommand);
  if (!command) return null;
  if (command === 'ADVANCE_PASSENGER_SETUP') {
    if (context.actorState === 'DRIVER') return registeredStages(context) ? command : null;
    return ['UNREGISTERED', 'PASSENGER'].includes(context.actorState)
      && (registeredStages(context) || context.step === 'ONBOARDING'
        && ['WELCOME', 'ASK_NAME', 'ASK_NOTICE', 'PAIRING'].includes(context.registrationStep)) ? command : null;
  }
  if (!registeredStages(context)) return null;
  if (['APPROVE_LINK', 'APPROVE_PICKUP'].includes(command))
    return context.actorState === 'DRIVER' ? command : null;
  if (['REQUEST_LINK', 'PROPOSE_PICKUP'].includes(command))
    return context.actorState === 'PASSENGER' ? command : null;
  return ['PASSENGER', 'DRIVER'].includes(context.actorState) ? command : null;
}

export function decidePassengerSetup(context) {
  if (!context.allowedCommands.some(isPassengerCommand))
    return context.options.some(option => setupKinds.has(option.kind)) ? { kind: 'NO_ACTION' } : null;
  const commandType = offered(context);
  const payload = commandType && payloadFromOptions(PASSENGER_PAYLOADS[commandType], context.options);
  return payload ? { kind: 'COMMAND', commandType, payload } : { kind: 'NO_ACTION' };
}

export function validPassengerDecision(decision, context) {
  return offered(context) === decision.commandType
    && matchesOfferedPayload(decision, PASSENGER_PAYLOADS[decision.commandType], context.options);
}
