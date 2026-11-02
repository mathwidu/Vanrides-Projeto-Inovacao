#!/usr/bin/env node
import { safeCode } from './070-worker-common.mjs';
import { runSmoke } from './070-worker-smoke.mjs';
import { runDeliveryScenarios } from './120-delivery-scenarios.mjs';
runSmoke(runDeliveryScenarios, 'VR-WA-120-DELIVERY-RECOVERY').catch(error => {
  console.error(JSON.stringify({ status: 'FAIL', code: safeCode(error, 'DELIVERY_SMOKE_PREFLIGHT_FAILURE') }));
  process.exitCode = 1;
});
