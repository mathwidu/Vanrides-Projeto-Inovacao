#!/usr/bin/env node
// Reuse the closed loopback harness; the card and complete source hashes are
// explicit in the receipt. The historical wa070-local namespace is test-only.
import { runSmoke } from './070-worker-smoke.mjs';
import { runDriverScenarios } from './080-driver-scenarios.mjs';
import { safeCode } from './070-worker-common.mjs';

runSmoke(runDriverScenarios, 'VR-WA-080-DRIVER-ROUTINES').catch(error => {
  console.error(JSON.stringify({ status: 'FAIL', code: safeCode(error, 'DRIVER_SMOKE_PREFLIGHT_FAILURE') }));
  process.exitCode = 1;
});
