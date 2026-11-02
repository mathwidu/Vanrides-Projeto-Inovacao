// The API interprets the authenticated message and owns the draft. This module
// only copies the single offered operation and its authorized references.
import { singleOfferedCommand, payloadFromOptions, matchesOfferedPayload } from './context-offer.mjs';
export { validContextOptions } from './context-offer.mjs';
export const DRIVER_PAYLOADS = Object.freeze(Object.fromEntries(Object.entries({
  ADVANCE_DRIVER_SETUP: {},
  ADVANCE_RUN_SETUP: {},
  REQUEST_START: { occurrenceRef: 'OCCURRENCE', confirmationRef: 'CONFIRMATION' },
  REQUEST_RESUME: { occurrenceRef: 'OCCURRENCE', confirmationRef: 'CONFIRMATION' },
  SET_OPERATIONAL_ATTENDANCE: { occurrenceRef: 'OCCURRENCE', passengerRef: 'PASSENGER', confirmationRef: 'CONFIRMATION' },
  CONFIGURE_ROUTINE: { routineRef: 'ROUTINE', confirmationRef: 'CONFIRMATION' },
  SET_STOP_ORDER: { routineRef: 'ROUTINE', confirmationRef: 'CONFIRMATION' },
  SET_ROUTINE_STATE: { routineRef: 'ROUTINE', confirmationRef: 'CONFIRMATION' },
  CREATE_INVITE: { confirmationRef: 'CONFIRMATION' },
  REVOKE_INVITE: { inviteRef: 'INVITE', confirmationRef: 'CONFIRMATION' },
  CANCEL_OCCURRENCE: { occurrenceRef: 'OCCURRENCE', confirmationRef: 'CONFIRMATION' },
  GET_SUMMARY: { summaryRef: 'SUMMARY' },
}).map(([command, fields]) => [command, Object.freeze(fields)])));

const setupKinds = new Set(['ROUTINE', 'OCCURRENCE', 'INVITE', 'QUESTION', 'CONFIRMATION', 'STOP', 'SUMMARY', 'PASSENGER', 'RUN']);
export const isDriverCommand = type => Object.hasOwn(DRIVER_PAYLOADS, type);

function offered(context) {
  if (context.actorState !== 'DRIVER' || context.eventFreshness !== 'CURRENT'
      || !['PAIRING', 'READY'].includes(context.step)
      || !['PAIRING', 'READY'].includes(context.registrationStep)) return null;
  return singleOfferedCommand(context, isDriverCommand);
}

export function decideDriverSetup(context) {
  if (!context.allowedCommands.some(isDriverCommand)) {
    // An incomplete offer must not turn private setup text into an LLM input.
    return context.actorState === 'DRIVER' && context.options.some(option => setupKinds.has(option.kind))
      ? { kind: 'NO_ACTION' } : null;
  }
  const commandType = offered(context);
  const payload = commandType && payloadFromOptions(DRIVER_PAYLOADS[commandType], context.options);
  return payload ? { kind: 'COMMAND', commandType, payload } : { kind: 'NO_ACTION' };
}

export function validDriverDecision(decision, context) {
  if (offered(context) !== decision.commandType) return false;
  return matchesOfferedPayload(decision, DRIVER_PAYLOADS[decision.commandType], context.options);
}
