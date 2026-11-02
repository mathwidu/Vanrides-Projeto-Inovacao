import { token } from './070-worker-common.mjs';

// Capture the entire displayed candidate. Matching only a fixed-length prefix
// can falsely approve a longer, unusable confirmation or invitation code.
export function extractConfirmationMarker(text) {
  if (typeof text !== 'string') return null;
  const matches = [...text.matchAll(/(?<![\p{L}\p{N}_])CONFIRMAR ([^\s.,;:!?()"'<>]+)/gu)];
  return matches.length === 1 && /^[BCDFGHJKMNPQRSTVWXYZ]{8}$/u.test(matches[0][1]) ? matches[0][1] : null;
}

export function extractPassengerInviteCode(text) {
  if (typeof text !== 'string') return null;
  const matches = [...text.matchAll(/(?<![\p{L}\p{N}_])PASSAGEIRO ([^\s.,;:!?()"'<>]+)/gu)];
  return matches.length === 1 && token(matches[0][1]) ? matches[0][1] : null;
}

export function successfulReceipt(result) {
  return result && typeof result === 'object' && !Array.isArray(result)
    && !Object.hasOwn(result, 'errorCode') && ['APPLIED', 'NO_CHANGE'].includes(result.outcome);
}
