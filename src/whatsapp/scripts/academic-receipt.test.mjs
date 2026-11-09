import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { prepareAcademicReceipt } from './academic-receipt.mjs';

const successful = { schemaVersion: 1, status: 'PASS', cleanup: { status: 'OWNED_RESOURCES_REMOVED' }, checks: [] };
function fixture(t) {
  const work = fs.mkdtempSync(path.join(os.tmpdir(), 'vanrides-receipt-test-'));
  const checkout = path.join(work, 'checkout'); fs.mkdirSync(checkout);
  const output = path.join(work, 'receipt.json');
  t.after(() => fs.rmSync(work, { recursive: true, force: true }));
  return { work, checkout, output };
}
test('optional receipt preparation is read-only and successful final write is atomic exclusive 0600 JSON', t => {
  const { work, checkout, output } = fixture(t);
  const write = prepareAcademicReceipt(output, checkout);
  assert.equal(fs.existsSync(output), false);
  write(successful);
  assert.deepEqual(JSON.parse(fs.readFileSync(output)), successful);
  assert.equal(fs.statSync(output).mode & 0o777, 0o600);
  assert.deepEqual(fs.readdirSync(work).sort(), ['checkout', 'receipt.json']);
  assert.throws(() => write(successful), /ACADEMIC_RECEIPT_WRITE_FAILED/);
  assert.deepEqual(JSON.parse(fs.readFileSync(output)), successful);
  assert.doesNotThrow(() => prepareAcademicReceipt(undefined, checkout)(successful));
});
test('failure or incomplete cleanup cannot create a PASS receipt', t => {
  const { checkout, output } = fixture(t);
  const write = prepareAcademicReceipt(output, checkout);
  for (const result of [{ ...successful, status: 'FAIL' }, { ...successful, cleanup: { status: 'FAILED' } }])
    assert.throws(() => write(result), /ACADEMIC_RECEIPT_NOT_SUCCESSFUL/);
  assert.equal(fs.existsSync(output), false);
});
test('relative, existing, checkout and symlinked checkout destinations fail before writing', t => {
  const { work, checkout, output } = fixture(t);
  fs.writeFileSync(output, 'existing');
  const alias = path.join(work, 'alias'); fs.symlinkSync(checkout, alias);
  for (const destination of ['receipt.json', output, path.join(checkout, 'receipt.json'),
    path.join(alias, 'receipt.json'), path.join(work, 'missing/receipt.json')])
    assert.throws(() => prepareAcademicReceipt(destination, checkout), /ACADEMIC_RECEIPT/);
  assert.equal(fs.readFileSync(output, 'utf8'), 'existing');
  assert.equal(fs.readdirSync(checkout).length, 0);
});
