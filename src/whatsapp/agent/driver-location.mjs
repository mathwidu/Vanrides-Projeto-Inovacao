import { singleOfferedCommand } from './context-offer.mjs';

export const isDriverLocationCommand = command => command === 'GET_DRIVER_LOCATION';

// The API reads the authenticated question or explicit menu choice. The consumer
// only advances its single offer; it never selects a vehicle, run or coordinate.
export function decideDriverLocation(context) {
  if (!['PASSENGER', 'DRIVER'].includes(context.actorState)
      || context.step !== 'READY' || context.registrationStep !== 'READY'
      || context.eventFreshness !== 'CURRENT'
      || singleOfferedCommand(context, isDriverLocationCommand) !== 'GET_DRIVER_LOCATION')
    return { kind: 'NO_ACTION' };
  return { kind: 'COMMAND', commandType: 'GET_DRIVER_LOCATION', payload: {} };
}

export function validDriverLocationDecision(decision, context) {
  const offered = decideDriverLocation(context);
  return offered.kind === 'COMMAND' && decision.commandType === offered.commandType
    && Object.keys(decision.payload).length === 0;
}
