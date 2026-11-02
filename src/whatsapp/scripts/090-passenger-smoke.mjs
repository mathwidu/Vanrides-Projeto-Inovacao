#!/usr/bin/env node
import { runSmoke } from './070-worker-smoke.mjs';
import { safeCode } from './070-worker-common.mjs';
import { runPassengerScenarios } from './090-passenger-scenarios.mjs';

runSmoke(runPassengerScenarios, 'VR-WA-090-PASSENGERS').catch(error => {
  console.error(JSON.stringify({ status: 'FAIL', code: safeCode(error, 'PASSENGER_SMOKE_PREFLIGHT_FAILURE') }));
  process.exitCode = 1;
});
