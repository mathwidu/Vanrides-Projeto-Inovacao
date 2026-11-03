const CODES = new Set(['CONFIG_INVALID', 'INVALID_REQUEST', 'INVALID_RESPONSE', 'TIMEOUT', 'UPSTREAM_UNAVAILABLE']);

/** Closed errors never retain a cause, request, URL, response or original message. */
export class WorkerError extends Error {
  constructor(code) {
    const safe = CODES.has(code) ? code : 'UPSTREAM_UNAVAILABLE';
    super(`WA070_${safe}`);
    this.name = 'WorkerError';
    this.code = safe;
  }
}

export function invalidConfig() { throw new WorkerError('CONFIG_INVALID'); }
