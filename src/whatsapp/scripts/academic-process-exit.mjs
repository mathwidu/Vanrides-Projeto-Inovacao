/** A JVM normally returns 128 + SIGTERM (143). Only our requested stop is acceptable. */
export function expectedApiExit({ requested, code, signal }) {
  return requested === true && ((signal === null && [0, 143].includes(code))
    || code === null && signal === 'SIGTERM');
}
