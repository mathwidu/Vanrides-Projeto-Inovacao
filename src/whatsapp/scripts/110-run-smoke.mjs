#!/usr/bin/env node
import { safeCode } from './070-worker-common.mjs';
import { runSmoke } from './070-worker-smoke.mjs';
import { runTrackingScenarios } from './110-run-scenarios.mjs';
import { runRedmiScenarios } from './110-run-redmi.mjs';
runSmoke(process.env.WA110_DEVICE_APK ? runRedmiScenarios : runTrackingScenarios, 'VR-WA-110-RUN-TRACKING').catch(error => {
  console.error(JSON.stringify({ status:'FAIL',code:safeCode(error,'RUN_SMOKE_PREFLIGHT_FAILURE') }));
  process.exitCode=1;
});
