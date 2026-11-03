"""Strict offline binding of the two original CI receipts and the tested JAR."""
import hashlib
import json
import re

from plan import CARD, Blocked, require, strict_object, hash_file

CHECKS = ('same_clean_backend_jar_listener_database_and_synthetic_cohort',
 'unknown_connection_blocks_reservation_until_authenticated_open',
 'three_safe_attempts_zero_posts_backoff_restart_and_distinct_generations',
 'reconnection_reauthorizes_same_outbox_and_submits_once',
 'provider_effect_before_400_stays_unknown_after_worker_restart_without_resend',
 'closed_channel_health_expires_unknown_abandoned_and_pending_without_provider_traffic',
 'delivery_cases_are_deduplicated_private_paginated_metadata_without_pausing_conversation',
 'late_first_submitted_preserves_expired_outbox_and_immutable_result_receipts',
 'correlated_late_read_and_older_statuses_preserve_expired_outbox_without_resend',
 'scoped_reset_and_credential_revocation',
 'same_backend_jar_worker_agent_transport_and_smoke_hashes_after')
TOTALS = {'events': 9, 'attempts': 7, 'providerSubmissions': 3, 'connectionReads': 21,
          'syntheticFixtures': True, 'realWhatsApp': False, 'physicalDevices': False}
TABLES = ('identity', 'identity_alias', 'profile', 'driver_authorization', 'driver_device', 'pairing_challenge',
          'device_token', 'driver_bootstrap_invite', 'driver_invite', 'driver_passenger_link', 'pickup_point',
          'routine', 'routine_member', 'routine_occurrence', 'attendance', 'operation_run', 'run_stop',
          'start_intent', 'tracking_session', 'tracking_batch_receipt', 'tracking_sample_receipt',
          'conversation_state', 'message_outbox', 'message_attempt', 'command_receipt', 'support_case',
          'passenger_link_routine', 'common_pickup_point', 'passenger_response_route', 'attendance_question',
          'occurrence_roster_override', 'delivery_connection')


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def object_json(raw):
    require(isinstance(raw, bytes) and 0 < len(raw) <= 262144, 'RECEIPT_TOO_LARGE')
    value = json.loads(raw.decode('utf-8'), object_pairs_hook=strict_object,
                       parse_constant=lambda _: (_ for _ in ()).throw(Blocked('RECEIPT_INVALID')))
    require(isinstance(value, dict), 'RECEIPT_INVALID')
    return value


def ready_pins(contract):
    backend = contract['backend']
    require(backend.get('gateAndSameArtifactSmoke') == 'PASS'
            and re.fullmatch('[a-f0-9]{64}', backend.get('jarSha256') or '')
            and type(backend.get('jarBytes')) is int and backend['jarBytes'] > 0
            and re.fullmatch('[a-f0-9]{64}', backend.get('verifiedCiReceiptSha256') or '')
            and isinstance(backend.get('verificationPins'), dict), 'BACKEND_GATE_SMOKE_JAR_RECEIPT_PENDING')
    pins = backend['verificationPins']
    require(set(pins) == {'backendTree', 'coordinationCommit', 'coordinationTree', 'gate', 'smoke'}, 'VERIFICATION_PINS_INVALID')
    for field in ('backendTree', 'coordinationCommit', 'coordinationTree'):
        require(re.fullmatch('[a-f0-9]{40}', pins.get(field) or ''), 'VERIFICATION_PINS_INVALID')
    for phase in ('gate', 'smoke'):
        require(isinstance(pins[phase], dict), 'VERIFICATION_PINS_INVALID')
        require(re.fullmatch('sha256:[a-f0-9]{64}', pins[phase].get('artifactSha256') or ''), 'VERIFICATION_PINS_INVALID')
        require(re.fullmatch('[a-f0-9]{64}', pins[phase].get('receiptSha256') or ''), 'VERIFICATION_PINS_INVALID')
    return backend, pins


def validate_envelope(contract, raw, jar):
    """No network trust is inferred: principal pins independently retrieved provenance."""
    backend, pins = ready_pins(contract)
    require(digest(raw) == backend['verifiedCiReceiptSha256'], 'VERIFIED_RECEIPT_DIGEST_CHANGED')
    value = object_json(raw)
    require(set(value) == {'schemaVersion', 'binary', 'gateOriginal', 'smokeOriginal', 'smokeProvenance'}
            and value['schemaVersion'] == 1 and value['binary'] == {'sha256': backend['jarSha256'], 'bytes': backend['jarBytes']}, 'VERIFIED_RECEIPT_SCHEMA_INVALID')
    originals = {}
    for phase, key in (('gate', 'gateOriginal'), ('smoke', 'smokeOriginal')):
        require(isinstance(value[key], str), 'ORIGINAL_RECEIPT_REQUIRED')
        original = value[key].encode('utf-8')
        require(digest(original) == pins[phase]['receiptSha256'], 'ORIGINAL_RECEIPT_CHANGED')
        originals[phase] = object_json(original)
    gate = originals['gate']
    require(gate.get('state') == 'PASS' and gate.get('repository') == 'mathwidu/vanrides-backend'
            and gate.get('sha') == backend['sourceSha'] and gate.get('run') == backend['ciRun']
            and gate.get('url') == f"https://github.com/mathwidu/vanrides-backend/actions/runs/{backend['ciRun']}"
            and gate.get('workflowEvent') == 'workflow_dispatch'
            and gate.get('jobs') == {'test': 'success', 'build_and_push': 'skipped', 'deploy_swarm': 'skipped'}, 'BACKEND_GATE_NOT_EXACT')
    for field in ('allTests', 'allClasses', 'postgresTests', 'postgresClasses', 'artifactId', 'artifactSha256', 'manifestSha256'):
        require(field in pins['gate'] and gate.get(field) == pins['gate'][field], 'BACKEND_GATE_MATRIX_CHANGED')
    for field in ('allTests', 'postgresTests'):
        totals = gate[field]
        require(isinstance(totals, dict) and set(totals) == {'tests', 'failures', 'errors', 'skipped'}
                and all(type(item) is int for item in totals.values()) and totals['tests'] > 0
                and all(totals[key] == 0 for key in ('failures', 'errors', 'skipped')), 'BACKEND_GATE_NOT_COMPLETE')
    require(all(type(gate[key]) is int and gate[key] > 0 for key in ('allClasses', 'postgresClasses', 'artifactId'))
            and isinstance(gate.get('jobStarted'), str) and isinstance(gate.get('jobFinished'), str)
            and gate['jobFinished'] > gate['jobStarted'], 'BACKEND_GATE_PROVENANCE_INVALID')
    smoke, sp = originals['smoke'], pins['smoke']
    provenance = value['smokeProvenance']
    require(isinstance(provenance, dict) and set(provenance) == {'repository', 'run', 'url', 'workflowEvent', 'status',
            'conclusion', 'headSha', 'headBranch', 'workflowPath', 'runAttempt', 'artifactId', 'artifactSha256'}
            and provenance['status'] == 'completed' and provenance['conclusion'] == 'success'
            and provenance['workflowEvent'] == 'push' and provenance['repository'] == 'mathwidu/vanrides-backend'
            and provenance['headBranch'] == 'codex/wa120-synthetic-journey'
            and provenance['workflowPath'] == '.github/workflows/wa120-synthetic-journey.yml'
            and type(provenance['runAttempt']) is int and provenance['runAttempt'] > 0, 'SMOKE_PROVENANCE_INVALID')
    for key in ('repository', 'run', 'url', 'headSha', 'headBranch', 'workflowPath', 'runAttempt', 'artifactId', 'artifactSha256'):
        require(key in sp and provenance[key] == sp[key], 'SMOKE_PROVENANCE_CHANGED')
    require(re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', provenance['repository'])
            and type(provenance['run']) is int and provenance['run'] > 0
            and provenance['url'] == f"https://github.com/{provenance['repository']}/actions/runs/{provenance['run']}"
            and re.fullmatch('[a-f0-9]{40}', provenance['headSha'])
            and re.fullmatch('[a-f0-9]{40}', sp['headTree'])
            and all(re.fullmatch('[a-f0-9]{64}', sp[key]) for key in ('bundleManifestSha256', 'fileMatrixSha256')),
            'SMOKE_PROVENANCE_INVALID')
    require(smoke.get('schemaVersion') == 1 and smoke.get('status') == 'PASS' and smoke.get('cardId') == CARD
            and smoke.get('profile') == 'LINUX_LOOPBACK_SYNTHETIC_DELIVERY_RECOVERY_V1'
            and smoke.get('backend') == {'commit': backend['sourceSha'], 'tree': pins['backendTree']}
            and smoke.get('coordination') == {'commit': pins['coordinationCommit'], 'tree': pins['coordinationTree']}
            and smoke.get('harness') == {'commit': sp['headSha'], 'tree': sp['headTree']}
            and smoke.get('jarSha256') == backend['jarSha256']
            and smoke.get('canonicalMavenSuite') == 'NOT_RERUN_SEPARATE_EVIDENCE'
            and smoke.get('bundleManifestSha256') == sp['bundleManifestSha256']
            and smoke.get('fileMatrixSha256') == sp['fileMatrixSha256']
            and smoke.get('checks') == [{'check': key, 'status': 'PASS'} for key in CHECKS]
            and smoke.get('codes') == [], 'SMOKE_NOT_EXACT')
    runtime = smoke.get('runtime', {})
    require(set(runtime) == {'platform', 'node', 'java'} and runtime['platform'] == 'linux/amd64'
            and runtime['node'] == '22.23.2' and re.fullmatch(r'openjdk 17\.[A-Za-z0-9.+_ -]+', runtime['java']), 'SMOKE_RUNTIME_CHANGED')
    require(smoke.get('boundaries') == dict(realWhatsApp=False, openai=False, nativeDevices=False, rawGps=False, vps=False, stores=False), 'SMOKE_BOUNDARY_CHANGED')
    delivery = {**TOTALS, 'sink': 'LOOPBACK_SYNTHETIC_RECEIVER', 'externalBusinessSubmissions': 0,
                'evidence': 'PINNED_ALLOWLIST_AND_LOOPBACK_RECEIVER_COUNTS'}
    require(smoke.get('delivery') == delivery, 'SMOKE_COUNTS_CHANGED')
    cleanup = smoke.get('cleanup', {})
    require(all(cleanup.get(key) is True for key in ('apiStopped', 'smokeProcessesStopped', 'containerRemoved', 'portsClosed', 'privateRuntimeRemoved'))
            and cleanup.get('cohortsReset') == 1 and cleanup.get('issuedCredentials') == cleanup.get('revokedCredentials') == 4
            and isinstance(cleanup.get('graph'), dict) and set(cleanup['graph']) == set(TABLES)
            and all(type(count) is int and count == 0 for count in cleanup['graph'].values())
            and smoke.get('memory', {}).get('aborted') is False
            and smoke.get('stages') == [{'stage': key, 'status': 'PASS'} for key in ('BUILD', 'POSTGRES', 'API', 'JOURNEY', 'CLEANUP')], 'SMOKE_CLEANUP_NOT_COMPLETE')
    require(jar.is_file() and not jar.is_symlink() and jar.stat().st_size == backend['jarBytes']
            and hash_file(jar) == backend['jarSha256'], 'TESTED_JAR_CHANGED')
    return {'sourceSha': backend['sourceSha'], 'jarSha256': backend['jarSha256'], 'gateRun': backend['ciRun'], 'smokeRun': sp['run']}
