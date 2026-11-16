import { singleOfferedCommand, payloadFromOptions } from './context-offer.mjs';

export const isRunFinishCommand = command => command === 'FINISH_RUN';

// The server owns both the review and its confirmation. No run is selected from text.
export function decideRunFinish(context) {
  if (context.actorState !== 'DRIVER' || context.eventFreshness !== 'CURRENT'
      || singleOfferedCommand(context, isRunFinishCommand) !== 'FINISH_RUN') return { kind: 'NO_ACTION' };
  const refs = payloadFromOptions({ runRef: 'RUN' }, context.options);
  return refs ? { kind: 'COMMAND', commandType: 'FINISH_RUN', payload: { ...refs, reason: 'MANUAL_COMPLETION' } }
    : { kind: 'NO_ACTION' };
}

export function validRunFinishDecision(decision, context) {
  const offered = decideRunFinish(context);
  return offered.kind === 'COMMAND' && decision.commandType === offered.commandType
    && Object.keys(decision.payload).length === 2 && decision.payload.runRef === offered.payload.runRef
    && decision.payload.reason === 'MANUAL_COMPLETION';
}
