import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import { academicScenarios, academicCheck, assertTrackingChecks, trackingChecks } from './academic-scenario-plan.mjs';
import { runApproachScenarios } from './130-approach-scenarios.mjs';
const script = fileURLToPath(new URL('./academic-integration-smoke.mjs', import.meta.url));
const sentinel = 'PRIVATE_INPUT_MUST_NOT_APPEAR';
function rejected(args, extra, code) {
  const result = spawnSync(process.execPath, [script, ...args], {
    env: { HOME: '/nonexistent', PATH: '/nonexistent', ...extra }, encoding: 'utf8', timeout: 5000,
  });
  assert.equal(result.error, undefined);
  assert.equal(result.status, 1);
  assert.equal(result.stdout, '');
  assert.deepEqual(JSON.parse(result.stderr), { status: 'FAIL', code });
  assert.equal(result.stderr.includes(sentinel), false);
}
test('extra input is rejected before Git, Java or Docker discovery and without reflecting it', () => {
  rejected([sentinel], {}, 'SMOKE_ARGUMENTS_INVALID');
});
test('invalid opt-in receipt destination is rejected before runtime discovery', () => {
  rejected([], { VANRIDES_ACADEMIC_RECEIPT_PATH: 'relative-' + sentinel }, 'ACADEMIC_RECEIPT_PATH_INVALID');
});
for (const variable of ['GIT_DIR', 'GIT_WORK_TREE', 'GIT_CONFIG_COUNT', 'DOCKER_HOST', 'DOCKER_CONTEXT',
  'DOCKER_TLS_VERIFY', 'DOCKER_CERT_PATH']) {
  test('inherited override ' + variable + ' is rejected without running a command', () => {
    rejected([], { [variable]: sentinel }, 'SMOKE_RUNTIME_OVERRIDE_REJECTED');
  });
}

// A mismatched expected CI revision must fail before clean-tree and Docker checks.
test('a different expected CI SHA is rejected before starting a runtime', () => {
  rejected([], { PATH: process.env.PATH, JAVA_HOME: '/synthetic-java-not-executed',
    VANRIDES_EXPECTED_SOURCE_SHA: '0'.repeat(40) }, 'SMOKE_SOURCE_SHA_MISMATCH');
});

test('five sequential executions retain all six scenario groups through the complete 130 journey', () => {
  assert.deepEqual(academicScenarios.map(row => row.scenario), ['070', '080', '100', '120', '130']);
  assert.deepEqual(academicScenarios.flatMap(row => row.covers).sort(), ['070', '080', '100', '110', '120', '130']);
  assert.equal(academicScenarios.at(-1).execute, runApproachScenarios);
  assert.deepEqual(academicScenarios.at(-1).covers, ['110', '130']);
});

test('tracking evidence names the actual 130 execution and cannot pass with a missing tracking check', () => {
  const execution = academicScenarios.at(-1);
  const rows = trackingChecks.map(check => academicCheck(execution, check));
  assert.ok(rows.every(row => row.scenario === '110' && row.execution === '130' && row.status === 'PASS'));
  assert.doesNotThrow(() => assertTrackingChecks(execution, rows));
  for (const omitted of rows)
    assert.throws(() => assertTrackingChecks(execution, rows.filter(row => row !== omitted)), /SMOKE_TRACKING_CHECKS_MISSING/);
  const check = 'owner_driver_can_query_its_own_active_vehicle_without_selecting_another_run';
  assert.deepEqual(academicCheck(execution, check), { scenario: '130', execution: '130', check, status: 'PASS' });
  assert.throws(() => academicCheck(academicScenarios[0], trackingChecks[0]), /SMOKE_CHECK_OUTSIDE_EXECUTION/);
});
