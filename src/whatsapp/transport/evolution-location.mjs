import { submitEvolutionMessage } from './evolution-message.mjs';

export function submitEvolutionLocation(options = {}) {
  return submitEvolutionMessage(options, 'STATIC_PIN');
}
