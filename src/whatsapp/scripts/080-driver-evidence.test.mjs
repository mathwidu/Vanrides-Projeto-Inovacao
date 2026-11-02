import test from 'node:test';
import assert from 'node:assert/strict';
import { extractConfirmationMarker, extractPassengerInviteCode, successfulReceipt } from './080-driver-evidence.mjs';

const marker = 'BCDFGHJK';
const code = Buffer.alloc(32, 7).toString('base64url');

test('displayed confirmation and invite must contain exactly one complete canonical code', () => {
  assert.equal(extractConfirmationMarker(`Confirme o resumo: CONFIRMAR ${marker}.`), marker);
  assert.equal(extractPassengerInviteCode(`Encaminhe: PASSAGEIRO ${code}. Validade de teste.`), code);
  for (const suffix of ['B', '9', '_', '-', '+', 'é', '\u200B']) {
    assert.equal(extractConfirmationMarker(`CONFIRMAR ${marker}${suffix}`), null, `marker suffix ${JSON.stringify(suffix)}`);
    assert.equal(extractPassengerInviteCode(`PASSAGEIRO ${code}${suffix}`), null, `invite suffix ${JSON.stringify(suffix)}`);
  }
  assert.equal(extractConfirmationMarker(`CONFIRMAR ${marker}\nCONFIRMAR ${marker}B`), null);
  assert.equal(extractPassengerInviteCode(`PASSAGEIRO ${code}\nPASSAGEIRO ${code}B`), null);
  assert.equal(extractConfirmationMarker(`XCONFIRMAR ${marker}`), null);
  assert.equal(extractPassengerInviteCode(`XPASSAGEIRO ${code}`), null);
  assert.equal(extractPassengerInviteCode('PASSAGEIRO ' + 'A'.repeat(42) + 'B'), null);
});

test('a processed inbox with a durable rejected command is not a successful business smoke', () => {
  assert.equal(successfulReceipt({ outcome: 'APPLIED' }), true);
  assert.equal(successfulReceipt({ outcome: 'NO_CHANGE' }), true);
  for (const result of [null, {}, [], { errorCode: 'ACTION_NOT_ALLOWED' },
    { outcome: 'APPLIED', errorCode: 'VERSION_CONFLICT' }, { outcome: 'PROCESSED' }])
    assert.equal(Boolean(successfulReceipt(result)), false);
});
