#!/usr/bin/env node
import { runSmoke } from './070-worker-smoke.mjs';
import { pairingScenarios } from './060-pairing-scenarios.mjs';
import { safeCode } from './070-worker-common.mjs';
import { demand } from './070-worker-common.mjs';

try {
  const drop = process.env.VANRIDES_PAIRING_LAB_DROP_REFRESH_RESPONSE;
  demand(drop === undefined || drop === 'once', 'LAB_REFRESH_DROP_MODE_INVALID');
  await runSmoke(pairingScenarios({ tlsDirectory: process.env.VANRIDES_PAIRING_LAB_TLS_DIR,
    caFile: process.env.VANRIDES_PAIRING_LAB_CA, mode: process.env.VANRIDES_PAIRING_LAB_MODE ?? 'host',
    dropFirstRefreshResponse: drop === 'once' }), 'VR-WA-060-DRIVER-APP');
} catch (error) {
  console.error(JSON.stringify({ status: 'FAIL', code: safeCode(error, 'LAB_PREFLIGHT_FAILED') })); process.exitCode = 1;
}
