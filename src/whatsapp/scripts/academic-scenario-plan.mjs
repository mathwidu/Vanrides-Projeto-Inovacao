import { runScenarios } from './070-worker-scenarios.mjs';
import { runDriverScenarios } from './080-driver-scenarios.mjs';
import { runAttendanceScenarios } from './100-attendance-scenarios.mjs';
import { runDeliveryScenarios } from './120-delivery-scenarios.mjs';
import { runApproachScenarios } from './130-approach-scenarios.mjs';
import { demand } from './070-worker-common.mjs';

export const trackingChecks = Object.freeze([
  'driver_routines_and_two_passengers_from_chat_device_bound_by_pkce_and_dpop',
  'driver_correction_preserves_personal_attendance_and_delivered_link_does_not_start_a_run',
  'reviewed_snapshot_activated_once_by_bound_device_without_early_gps_confirmation',
  'first_valid_acquisition_confirms_driver_once_and_ack_replay_preserves_partial_results',
  'api_watchdog_degrades_in_silence_without_n8n_and_replay_does_not_restore_freshness',
  'new_acquisition_recovers_same_run_and_stop_requires_distinct_device_ack',
]);

// 130 calls the complete 110 journey and adds location checks in its lifecycle
// hooks. Record that one execution honestly instead of repeating its setup.
export const academicScenarios = Object.freeze([
  ['070', ['070'], runScenarios],
  ['080', ['080'], runDriverScenarios],
  ['100', ['100'], runAttendanceScenarios],
  ['120', ['120'], runDeliveryScenarios],
  ['130', ['110', '130'], runApproachScenarios],
].map(([scenario, covers, execute]) => Object.freeze({ scenario, covers: Object.freeze(covers), execute })));

export function academicCheck(execution, check) {
  const scenario = trackingChecks.includes(check) ? '110' : execution.scenario;
  demand(execution.covers.includes(scenario), 'SMOKE_CHECK_OUTSIDE_EXECUTION');
  return { scenario, execution: execution.scenario, check, status: 'PASS' };
}

export function assertTrackingChecks(execution, checks) {
  if (execution.scenario === '130')
    demand(trackingChecks.every(check => checks.some(row => row.scenario === '110'
      && row.execution === '130' && row.check === check && row.status === 'PASS')), 'SMOKE_TRACKING_CHECKS_MISSING');
}
