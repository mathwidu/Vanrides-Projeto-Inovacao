// References are opaque API capabilities. Text and draft values stay in the API.
const kinds = new Set(['ROUTINE', 'RUN', 'OCCURRENCE', 'LINK', 'PICKUP', 'INVITE',
  'QUESTION', 'CONFIRMATION', 'STOP', 'SUMMARY', 'PASSENGER']);
const ref = value => typeof value === 'string' && /^[A-Za-z0-9_-]{8,160}$/u.test(value);
const generalCommands = new Set(['START_ONBOARDING', 'SET_PROFILE_NAME', 'ACCEPT_NOTICE',
  'CLAIM_DRIVER_INVITE', 'CONFIRM_PAIRING', 'REQUEST_SUPPORT', 'RESPOND_TO_MESSAGE']);

export function validContextOptions(options) {
  return Array.isArray(options) && options.length <= 100
    && options.every(option => option && typeof option === 'object' && !Array.isArray(option)
      && Object.keys(option).length === 3 && ['ref', 'kind', 'label'].every(key => Object.hasOwn(option, key))
      && ref(option.ref) && kinds.has(option.kind)
      && typeof option.label === 'string' && [...option.label].length >= 1 && [...option.label].length <= 80)
    && new Set(options.map(option => option.ref)).size === options.length;
}

export function singleOfferedCommand(context, accepts) {
  // A second domain command, including an unknown future one, is a conflict.
  const offered = context.allowedCommands.filter(command => !generalCommands.has(command));
  return offered.length === 1 && accepts(offered[0]) ? offered[0] : null;
}

export function payloadFromOptions(fields, options) {
  if (!validContextOptions(options)) return null;
  const payload = {};
  for (const [field, kind] of Object.entries(fields)) {
    const matches = options.filter(option => option.kind === kind);
    if (matches.length !== 1) return null;
    payload[field] = matches[0].ref;
  }
  return payload;
}

export function matchesOfferedPayload(decision, fields, options) {
  const expected = payloadFromOptions(fields, options);
  return expected !== null && Object.keys(decision.payload).length === Object.keys(expected).length
    && Object.entries(expected).every(([field, value]) => decision.payload[field] === value);
}
