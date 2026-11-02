#!/usr/bin/env node
import { runSmoke } from './070-worker-smoke.mjs';
import { safeCode } from './070-worker-common.mjs';
import { runAttendanceScenarios } from './100-attendance-scenarios.mjs';

runSmoke(runAttendanceScenarios, 'VR-WA-100-ATTENDANCE').catch(error => {
  console.error(JSON.stringify({ status: 'FAIL', code: safeCode(error, 'ATTENDANCE_SMOKE_PREFLIGHT_FAILURE') }));
  process.exitCode = 1;
});
