import { SmokeFailure } from './070-worker-common.mjs';

/** Signals fence new work; cleanup deliberately uses no aborted HTTP signal. SIGKILL cannot run cleanup. */
export function createInterruption(emitter = process) {
  const controller = new AbortController();
  const interrupt = () => controller.abort();
  emitter.on('SIGINT', interrupt); emitter.on('SIGTERM', interrupt);
  const assertActive = () => { if (controller.signal.aborted) throw new SmokeFailure('SMOKE_INTERRUPTED'); };
  return {
    signal: controller.signal,
    assertActive,
    async wait(action) {
      assertActive();
      let abort;
      const interrupted = new Promise((_, reject) => {
        abort = () => reject(new SmokeFailure('SMOKE_INTERRUPTED'));
        controller.signal.addEventListener('abort', abort, { once: true });
      });
      try {
        const value = await Promise.race([Promise.resolve().then(() => { assertActive(); return action(); }), interrupted]);
        assertActive(); return value;
      } finally { controller.signal.removeEventListener('abort', abort); }
    },
    dispose() { emitter.off('SIGINT', interrupt); emitter.off('SIGTERM', interrupt); },
  };
}
