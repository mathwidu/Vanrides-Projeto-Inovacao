import assert from 'node:assert/strict';
import test from 'node:test';
import { summary, SmokeFailure } from './070-worker-common.mjs';
const secret = 'PRIVATE_TEXT_TOKEN_AND_ID_MUST_NOT_APPEAR';
const id = '18bf2966-9724-4b05-ab13-f4dcfcba80cb';
function invalid(value, kind = 'process', expected = 'PROCESSED', http = 200) {
  let error;
  try { summary(value, kind, expected, http); } catch (caught) { error = caught; }
  assert.ok(error instanceof SmokeFailure);
  assert.equal(error.code, 'CONTROL_SUMMARY_INVALID');
  return error.controlSummary;
}
test('valid summaries retain the strict process and dispatch contract', () => {
  assert.equal(summary({ status: 'PROCESSED', claimed: 1, processed: 1, retried: 0, paused: 0 }, 'process', 'PROCESSED', 200), undefined);
  assert.equal(summary({ status: 'SUBMITTED', claimed: 1, submitted: 1, unknown: 0, failed: 0 }, 'dispatch', 'SUBMITTED', 200), undefined);
});
test('a mismatched summary describes only the allowlisted control result', () => {
  assert.deepEqual(invalid({ status: 'UNAVAILABLE', claimed: 1, processed: 0, retried: 0, paused: 0 }), {
    kind: 'process', expected: 'PROCESSED', httpStatus: 200, observedStatus: 'UNAVAILABLE',
    counters: { claimed: 1, processed: 0, retried: 0, paused: 0 },
  });
});
test('unknown text, identities, tokens, bodies and GPS values never enter diagnostics', () => {
  const detail = invalid({ status: secret, claimed: secret, processed: id, retried: -29.5753,
    paused: { token: secret }, identityId: id, token: secret, latitude: -29.5753,
    longitude: -50.7871, body: { text: secret } });
  assert.deepEqual(Object.keys(detail), ['kind', 'expected', 'httpStatus', 'observedStatus', 'counters']);
  assert.deepEqual(detail.counters, { claimed: 'INVALID', processed: 'INVALID', retried: 'INVALID', paused: 'INVALID' });
  const encoded = JSON.stringify(detail);
  for (const value of [secret, id, 'identityId', 'latitude', 'longitude', 'token', 'body', '-29.5753', '-50.7871'])
    assert.equal(encoded.includes(value), false);
});
test('unknown control names and expected status are normalized without reflection', () => {
  assert.deepEqual(invalid({}, secret, secret, secret), {
    kind: 'INVALID', expected: 'INVALID', httpStatus: 'INVALID', observedStatus: 'INVALID', counters: {},
  });
});
for (const value of [null, [], secret, 42]) {
  test('malformed body ' + typeof value + ' remains a strict failure with bounded diagnostics', () => {
    assert.equal(invalid(value).observedStatus, 'INVALID');
  });
}
test('HTTP detail accepts only bounded integer statuses', () => {
  for (const http of [100, 200, 599]) assert.equal(invalid({}, 'process', 'PROCESSED', http).httpStatus, http);
  for (const http of [0, 600, NaN, id, secret]) assert.equal(invalid({}, 'process', 'PROCESSED', http).httpStatus, 'INVALID');
});
