import assert from 'node:assert/strict';
import test from 'node:test';
import { expectedApiExit } from './academic-process-exit.mjs';
for (const [name, code, signal] of [['zero', 0, null], ['OpenJDK TERM', 143, null], ['POSIX TERM', null, 'SIGTERM']]) {
  test('accept API ' + name + ' only after this harness requested shutdown', () => {
    assert.equal(expectedApiExit({ requested: true, code, signal }), true);
    assert.equal(expectedApiExit({ requested: false, code, signal }), false);
  });
}
for (const [code, signal] of [[1, null], [137, null], [null, 'SIGKILL'], [null, null], [0, 'SIGKILL']]) {
  test('reject abnormal API exit ' + code + '/' + signal + ' even after a requested stop', () => {
    assert.equal(expectedApiExit({ requested: true, code, signal }), false);
  });
}
