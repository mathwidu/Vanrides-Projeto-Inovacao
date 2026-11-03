import { singleOfferedCommand, validContextOptions } from './context-offer.mjs';

export const isAttendanceCommand = command => ['SET_ATTENDANCE', 'ADVANCE_ATTENDANCE'].includes(command);
export const isAttendanceChoice = value => value === 'GOING' || value === 'NOT_GOING';
const question = /^question_[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/u;

// The API derives the choice and resolves the day, direction and recipient from
// the authenticated event. Neither message text nor a model supplies authority.
export function decideAttendance(context) {
  const none = { kind: 'NO_ACTION' };
  const passenger = context.actorState === 'PASSENGER' && context.step === 'READY'
    && context.registrationStep === 'READY';
  const driver = context.actorState === 'DRIVER' && ['READY', 'PAIRING'].includes(context.step)
    && ['READY', 'PAIRING'].includes(context.registrationStep);
  if (!passenger && !driver || context.eventFreshness !== 'CURRENT' || !validContextOptions(context.options)) return none;
  const commandType = singleOfferedCommand(context, isAttendanceCommand);
  if (commandType === 'ADVANCE_ATTENDANCE' && context.attendanceChoice === undefined && !context.options.length)
    return { kind: 'COMMAND', commandType, payload: {} };
  if (passenger && commandType === 'SET_ATTENDANCE' && isAttendanceChoice(context.attendanceChoice)
      && context.options.length === 1 && context.options[0].kind === 'QUESTION' && question.test(context.options[0].ref))
    return { kind: 'COMMAND', commandType, payload: { questionRef: context.options[0].ref, choice: context.attendanceChoice } };
  return none;
}

export function validAttendanceDecision(decision, context) {
  const offered = decideAttendance(context);
  return offered.kind === 'COMMAND' && offered.commandType === decision.commandType
    && Object.keys(decision.payload).length === Object.keys(offered.payload).length
    && Object.entries(offered.payload).every(([key, value]) => decision.payload[key] === value);
}
