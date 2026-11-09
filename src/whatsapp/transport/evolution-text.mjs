import { submitEvolutionMessage } from './evolution-message.mjs';

// Compatibility boundary for text-only consumers; a pin needs explicit support.
export function submitEvolutionText(options = {}) {
  return submitEvolutionMessage(options, 'TEXT');
}
