import { privateSecret } from './config.mjs';
import { invalidConfig } from './errors.mjs';

/** A key alone never enables model calls. No provider URL or model override. */
export function loadModelConfig(env = process.env) {
  const mode = env.WA070_MODEL_MODE ?? 'disabled';
  if (mode === 'disabled') return Object.freeze({ mode });
  if (mode !== 'openai' || env.OPENAI_BASE_URL !== undefined) invalidConfig();
  const direct = env.OPENAI_API_KEY !== undefined;
  const file = env.OPENAI_API_KEY_FILE !== undefined;
  if (direct === file) invalidConfig();
  const key = file ? privateSecret(env.OPENAI_API_KEY_FILE) : env.OPENAI_API_KEY;
  if (typeof key !== 'string' || !/^sk-[A-Za-z0-9_-]{20,4093}$/u.test(key)) invalidConfig();
  const config = { mode, model: 'gpt-6-luna', reasoningEffort: 'low', maxOutputTokens: 600 };
  Object.defineProperty(config, 'apiKey', { value: key, enumerable: false });
  return Object.freeze(config);
}
