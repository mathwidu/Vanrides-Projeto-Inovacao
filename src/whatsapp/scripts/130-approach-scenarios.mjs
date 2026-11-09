import crypto from 'node:crypto';
import { demand, quote } from './070-worker-common.mjs';
import { runTrackingScenarios } from './110-run-scenarios.mjs';

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

/** T43 with the regular chat, device auth and sample endpoints; no seed-only HTTP API. */
export async function runApproachScenarios(runtime) {
  const { tools, interruption, passed } = runtime;
  let pinQueries = 0;
  return runTrackingScenarios({ ...runtime,
    async onTrackingReady({ chat, run, postSamples, batch }) {
      let sequence = 3, latest;
      const acquire = async longitude => {
        const capturedAt = new Date().toISOString();
        const body = { ...batch, batchId: crypto.randomUUID(), deviceTime: capturedAt,
          samples: [{ sequence: ++sequence, capturedAt, latitude: 0, longitude, accuracyM: 6 }] };
        const response = await postSamples(body);
        demand(response.status === 200 && response.data.gpsState === 'FRESH'
          && response.data.accepted?.includes(sequence), 'LOCATION_SAMPLE_NOT_ACCEPTED');
        latest = body.samples[0];
      };
      const verify = body => body.latitude === latest?.latitude && body.longitude === latest?.longitude
        && body.address.startsWith('Capturada em ')
        && Date.parse(body.address.slice('Capturada em '.length)) === Date.parse(latest?.capturedAt)
        && Date.now() - Date.parse(latest.capturedAt) < 20_000;
      const first = await chat.send(chat.passenger, 'onde está?', 'GET_DRIVER_LOCATION', {
        beforeDispatch: () => acquire(0.0001), verifyStaticPin: verify });
      demand(first.pin && first.pin.longitude === 0.0001, 'LOCATION_FIRST_PIN_MISSING'); pinQueries++;
      await chat.replay();
      passed('location_query_sends_one_fresh_static_pin_to_its_passenger_and_replay_does_not_resend');
      const second = await chat.send(chat.passenger, 'onde está?', 'GET_DRIVER_LOCATION', {
        beforeDispatch: async () => {
          // Delay after the command has queued its response, then use a new acquisition.
          await interruption.wait(() => sleep(20_100)); await acquire(0.0002);
        }, verifyStaticPin: verify });
      demand(second.pin && second.pin.longitude === 0.0002 && first.pin.address !== second.pin.address,
        'LOCATION_DELAYED_PIN_NOT_REFRESHED'); pinQueries++;
      const forbidden = tools.count(`SELECT count(*) FROM wa.message_outbox
        WHERE recipient_identity_id=${quote(chat.passenger.actor)} AND purpose IN ('RESPONSE','PIN')
          AND (payload_json ? 'latitude' OR payload_json ? 'longitude' OR payload_json ? 'capturedAt')`);
      demand(forbidden === 0, 'LOCATION_COORDINATES_PERSISTED_IN_OUTBOX');
      const leaked = tools.count(`SELECT count(*) FROM wa.command_receipt
        WHERE identity_id=${quote(chat.passenger.actor)} AND command_type='GET_DRIVER_LOCATION'
          AND (result_json::text LIKE '%latitude%' OR result_json::text LIKE '%longitude%')`);
      demand(leaked === 0, 'LOCATION_COORDINATES_LEAKED_TO_CONVERSATION');
      passed('second_command_refreshes_after_twenty_second_queue_delay_without_coordinates_in_outbox_or_receipt');
      const own = await chat.send(chat.driver, 'onde está a van?', 'GET_DRIVER_LOCATION', {
        beforeDispatch: () => acquire(0.0003), verifyStaticPin: verify });
      demand(own.pin && own.pin.longitude === 0.0003, 'LOCATION_OWNER_DRIVER_PIN_MISSING'); pinQueries++;
      passed('owner_driver_can_query_its_own_active_vehicle_without_selecting_another_run');
      const denied = await chat.send(chat.otherDriver, 'onde está?', 'GET_DRIVER_LOCATION');
      demand(typeof denied.reply === 'string' && /indisponível|nenhuma|não há/iu.test(denied.reply), 'LOCATION_FOREIGN_ROSTER_ALLOWED');
      passed('passenger_removed_from_run_snapshot_cannot_query_the_vehicle');
      // Retain only counts in the public receipt; the synthetic coordinate values stay in memory.
      demand(typeof run.runId === 'string' && pinQueries === 3, 'LOCATION_QUERY_COUNT_INVALID');
    },
    async onTrackingStopped({ chat }) {
      const response = await chat.send(chat.passenger, 'onde está?', 'GET_DRIVER_LOCATION');
      demand(typeof response.reply === 'string' && /indisponível|encerrad|sem.*localiza/iu.test(response.reply),
        'LOCATION_STOPPED_SESSION_RETURNED_PIN');
      passed('stopped_device_session_returns_unavailability_instead_of_its_last_pin');
    },
  }).then(result => ({ ...result, pinQueries, t43: true }));
}
