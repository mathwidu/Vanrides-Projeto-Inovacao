/** Small bounded JSON parser: rejects duplicate decoded keys, invalid UTF-8 and excessive nesting. */
export function strictJson(bytes) {
  const source = new TextDecoder('utf-8', { fatal: true, ignoreBOM: true }).decode(bytes);
  let cursor = 0;
  const invalid = () => { throw new Error('INVALID_JSON'); };
  const space = () => { while (/[\x20\t\r\n]/.test(source[cursor] ?? '\0')) cursor++; };
  function string() {
    const start = cursor++;
    while (cursor < source.length) {
      const char = source[cursor++];
      if (char === '\\') { cursor++; continue; }
      if (char === '"') {
        const value = JSON.parse(source.slice(start, cursor));
        for (const point of value) if (point.codePointAt(0) >= 0xd800 && point.codePointAt(0) <= 0xdfff) invalid();
        return value;
      }
    }
    invalid();
  }
  function value(depth) {
    if (depth > 32) invalid();
    space();
    const char = source[cursor];
    if (char === '"') return string();
    if (char === '{') {
      cursor++; space();
      const result = Object.create(null), keys = new Set();
      if (source[cursor] === '}') { cursor++; return result; }
      while (true) {
        space(); if (source[cursor] !== '"') invalid();
        const key = string();
        if (keys.has(key)) invalid();
        keys.add(key); space(); if (source[cursor++] !== ':') invalid();
        result[key] = value(depth + 1); space();
        const delimiter = source[cursor++];
        if (delimiter === '}') return result;
        if (delimiter !== ',') invalid();
      }
    }
    if (char === '[') {
      cursor++; space(); const result = [];
      if (source[cursor] === ']') { cursor++; return result; }
      while (true) {
        result.push(value(depth + 1)); space();
        const delimiter = source[cursor++];
        if (delimiter === ']') return result;
        if (delimiter !== ',') invalid();
      }
    }
    for (const [word, literal] of [['true', true], ['false', false], ['null', null]]) {
      if (source.startsWith(word, cursor)) { cursor += word.length; return literal; }
    }
    const number = /^-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?/.exec(source.slice(cursor));
    if (!number || !Number.isFinite(Number(number[0]))) invalid();
    cursor += number[0].length; return Number(number[0]);
  }
  const result = value(0); space(); if (cursor !== source.length) invalid();
  return result;
}

export const object = value => value !== null && typeof value === 'object' && !Array.isArray(value);
export const exact = (value, keys) => object(value) && Object.keys(value).length === keys.length
  && keys.every(key => Object.hasOwn(value, key));

/** Calendar-valid RFC3339, compatible with the provider's OffsetDateTime timestamp. */
export function timestamp(value) {
  if (typeof value !== 'string' || value.length > 80) return null;
  const match = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?(Z|[+-]\d{2}:\d{2})$/.exec(value);
  if (!match) return null;
  const calendar = new Date(`${match[1]}.${(match[2] ?? '').padEnd(3, '0').slice(0, 3)}Z`);
  if (!Number.isFinite(calendar.getTime()) || calendar.toISOString().slice(0, 19) !== match[1]) return null;
  const offset = match[3];
  if (offset !== 'Z' && (Number(offset.slice(1, 3)) > 18 || Number(offset.slice(4)) > 59
      || Number(offset.slice(1, 3)) === 18 && Number(offset.slice(4)) !== 0)) return null;
  const result = Date.parse(value);
  return Number.isSafeInteger(result) ? result : null;
}
