"""Strict, offline evidence for one bounded monorepo lab; never release approval."""
import hashlib
import json
from pathlib import Path
import re

from plan import Blocked, CARD, require, strict_object, hash_file

PROFILE = 'MONOREPO_WA120_V1'
REPOSITORY = 'mathwidu/Vanrides-Projeto-Inovacao'
WORKFLOW = '.github/workflows/laboratorio-whatsapp.yml'
SCENARIOS = ('070', '080', '100', '110', '120', '130')
EXECUTION_COVERAGE_V2 = {'070': ('070',), '080': ('080',), '100': ('100',),
                         '120': ('120',), '130': ('110', '130')}
MAX_JSON_BYTES = 1000000
POLICY = {'maximumWindowSeconds': 1800, 'minimumHostAvailableMiB': 2048,
          'publicPorts': [], 'testCohortOnly': False, 'modelMode': 'disabled',
          'automaticMessagePostRetry': False, 'fixturePeople': False,
          'driverBootstrap': False, 'dockerExecutionImplemented': True, 'newStartsEnabled': False}
TOTAL_KEYS = {'tests', 'skipped', 'failures', 'errors'}
REQUIRED_CHECKS = {
    '120': {'unknown_connection_blocks_reservation_until_authenticated_open',
            'three_safe_attempts_zero_posts_backoff_restart_and_distinct_generations',
            'reconnection_reauthorizes_same_outbox_and_submits_once',
            'provider_effect_before_400_stays_unknown_after_worker_restart_without_resend',
            'closed_channel_health_expires_unknown_abandoned_and_pending_without_provider_traffic',
            'delivery_cases_are_deduplicated_private_paginated_metadata_without_pausing_conversation',
            'late_first_submitted_preserves_expired_outbox_and_immutable_result_receipts',
            'correlated_late_read_and_older_statuses_preserve_expired_outbox_without_resend'},
    '130': {'location_query_sends_one_fresh_static_pin_to_its_passenger_and_replay_does_not_resend',
            'second_command_refreshes_after_twenty_second_queue_delay_without_coordinates_in_outbox_or_receipt',
            'owner_driver_can_query_its_own_active_vehicle_without_selecting_another_run',
            'passenger_removed_from_run_snapshot_cannot_query_the_vehicle',
            'stopped_device_session_returns_unavailability_instead_of_its_last_pin'},
}
TRACKING_CHECKS = {
    'driver_routines_and_two_passengers_from_chat_device_bound_by_pkce_and_dpop',
    'driver_correction_preserves_personal_attendance_and_delivered_link_does_not_start_a_run',
    'reviewed_snapshot_activated_once_by_bound_device_without_early_gps_confirmation',
    'first_valid_acquisition_confirms_driver_once_and_ack_replay_preserves_partial_results',
    'api_watchdog_degrades_in_silence_without_n8n_and_replay_does_not_restore_freshness',
    'new_acquisition_recovers_same_run_and_stop_requires_distinct_device_ack',
}
COUNT_FIELDS = {'uniqueEvents', 'duplicateEvents', 'commandReceipts', 'outbox', 'supportCases',
                'providerRequests', 'submittedAttempts', 'workerStarts', 'modelCalls', 'controlledBusinessRejections',
                'crossActorDeliveries', 'actors', 'ticks', 'reminderSubmissions', 'seededReminderWindows',
                'sessionGenerations', 'events', 'attempts', 'providerSubmissions', 'connectionReads', 'pinQueries'}
TRUE_FIELDS = {'syntheticFixtures', 'privateInviteHashVerified', 'rawInviteAbsentFromDurablePlaintext', 't43'}


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def object_json(raw):
    require(isinstance(raw, bytes) and 0 < len(raw) <= MAX_JSON_BYTES, 'MONOREPO_JSON_INVALID')
    try:
        value = json.loads(raw.decode('utf-8'), object_pairs_hook=strict_object,
                           parse_constant=lambda _: (_ for _ in ()).throw(ValueError()))
    except Exception:
        raise Blocked('MONOREPO_JSON_INVALID') from None
    require(isinstance(value, dict), 'MONOREPO_JSON_INVALID')
    return value


def exact(value, keys):
    return isinstance(value, dict) and set(value) == set(keys)


def sha(value, length=64):
    return isinstance(value, str) and re.fullmatch('[a-f0-9]{' + str(length) + '}', value) is not None


def positive(value):
    return type(value) is int and value > 0


def safe_path(value):
    return isinstance(value, str) and 0 < len(value) <= 240 and '\\' not in value \
        and re.fullmatch('[A-Za-z0-9_./-]+', value) is not None \
        and not Path(value).is_absolute() and Path(value).as_posix() == value \
        and not ({'.', '..'} & set(value.split('/')))


def source(value):
    require(exact(value, ('repository', 'sha', 'tree', 'files'))
            and value['repository'] == REPOSITORY and sha(value['sha'], 40) and sha(value['tree'], 40)
            and isinstance(value['files'], dict) and 1 <= len(value['files']) <= 200, 'MONOREPO_SOURCE_INVALID')
    for name, row in value['files'].items():
        require(safe_path(name) and exact(row, ('gitBlob', 'sha256', 'bytes'))
                and sha(row['gitBlob'], 40) and sha(row['sha256']) and positive(row['bytes']), 'MONOREPO_SOURCE_INVALID')
    return value


def ci(value):
    require(exact(value, ('repository', 'sha', 'runId', 'runAttempt', 'workflowRef', 'event', 'job'))
            and value['repository'] == REPOSITORY and sha(value['sha'], 40)
            and positive(value['runId']) and positive(value['runAttempt'])
            and isinstance(value['workflowRef'], str)
            and value['workflowRef'].startswith(REPOSITORY + '/' + WORKFLOW + '@refs/heads/')
            and re.fullmatch('[A-Za-z0-9_./@-]+', value['workflowRef'])
            and value['event'] == 'workflow_dispatch' and value['job'] == 'candidate', 'MONOREPO_CI_INVALID')
    return value


def green(totals):
    require(exact(totals, TOTAL_KEYS) and positive(totals['tests'])
            and all(type(totals[key]) is int and totals[key] == 0 for key in TOTAL_KEYS - {'tests'}), 'MONOREPO_TESTS_NOT_GREEN')


def gate(value):
    require(exact(value, ('schemaVersion', 'profile', 'status', 'source', 'ci', 'manifestSha256', 'manifest', 'requiredClasses'))
            and type(value['schemaVersion']) is int and value['schemaVersion'] == 1 and value['profile'] == 'MONOREPO_CANONICAL_GATE_V1'
            and value['status'] == 'PASS' and sha(value['manifestSha256']), 'MONOREPO_GATE_INVALID')
    src, origin = source(value['source']), ci(value['ci'])
    require(origin['sha'] == src['sha'], 'MONOREPO_GATE_SOURCE_CHANGED')
    manifest = value['manifest']
    require(exact(manifest, ('status', 'suite', 'commandProfile', 'retentionClass', 'eligibleForRelease',
                             'source', 'totals', 'reports', 'allReports', 'environment'))
            and manifest['status'] == 'passed' and manifest['suite'] == 'integration'
            and manifest['commandProfile'] == 'maven-integration-all-tests'
            and manifest['retentionClass'] == 'ci' and manifest['eligibleForRelease'] is False, 'MONOREPO_GATE_NOT_TERMINAL')
    msrc = manifest['source']
    require(exact(msrc, ('gitSha', 'gitTree', 'runStartGitSha', 'runStartGitTree', 'clean', 'dirtyPathCount', 'cleanRequired'))
            and msrc['gitSha'] == msrc['runStartGitSha'] == src['sha']
            and msrc['gitTree'] == msrc['runStartGitTree'] == src['tree']
            and msrc['clean'] is True and msrc['cleanRequired'] is True
            and type(msrc['dirtyPathCount']) is int and msrc['dirtyPathCount'] == 0, 'MONOREPO_GATE_SOURCE_CHANGED')
    env = manifest['environment']
    require(isinstance(env, dict) and env.get('scope') == 'all-tests' and env.get('userHomeIsolated') is True
            and env.get('mavenRcSkipped') is True and env.get('dockerEndpointTransport') == 'unix', 'MONOREPO_GATE_ENVIRONMENT_INVALID')
    green(manifest['totals'])
    required, reports = value['requiredClasses'], manifest['reports']
    require(isinstance(required, dict) and required and all(isinstance(name, str)
            and re.fullmatch('[A-Za-z_$][A-Za-z0-9_.$]*', name) and positive(count)
            for name, count in required.items()) and isinstance(reports, list), 'MONOREPO_GATE_MATRIX_INVALID')
    require(len(reports) == len(required) and all(isinstance(row, dict) and row.get('className') in required
            and row.get('tests') == required[row['className']] and sha(row.get('sha256')) for row in reports)
            and {row['className'] for row in reports} == set(required)
            and sum(required.values()) == manifest['totals']['tests'], 'MONOREPO_GATE_MATRIX_INVALID')
    all_reports = manifest['allReports']
    require(isinstance(all_reports, dict) and all_reports.get('requiredGreen') is True
            and positive(all_reports.get('reportCount')) and isinstance(all_reports.get('reports'), list)
            and len(all_reports['reports']) == all_reports['reportCount'], 'MONOREPO_ALL_REPORTS_INVALID')
    green(all_reports.get('totals'))
    names, counts = set(), dict.fromkeys(TOTAL_KEYS, 0)
    all_by_name = {}
    for row in all_reports['reports']:
        require(isinstance(row, dict) and isinstance(row.get('className'), str) and row['className'] not in names
                and sha(row.get('sha256')), 'MONOREPO_ALL_REPORTS_INVALID')
        green({key: row.get(key) for key in TOTAL_KEYS})
        names.add(row['className']); all_by_name[row['className']] = row
        for key in TOTAL_KEYS:
            counts[key] += row[key]
    require(counts == all_reports['totals'] and all(name in all_by_name
            and all_by_name[name]['tests'] == count for name, count in required.items())
            and all(all_by_name[row['className']]['sha256'] == row['sha256'] for row in reports), 'MONOREPO_ALL_REPORTS_INVALID')
    return value


def smoke(value):
    require(exact(value, ('schemaVersion', 'profile', 'status', 'sourceSha', 'sourceTree', 'jar', 'checks',
                          'scenarios', 'cleanup', 'boundaries', 'runtime'))
            and type(value['schemaVersion']) is int
            and (value['schemaVersion'], value['profile']) in (
                (1, 'VANRIDES_ACADEMIC_INTEGRATION_V1'), (2, 'VANRIDES_ACADEMIC_INTEGRATION_V2'))
            and value['status'] == 'PASS' and sha(value['sourceSha'], 40) and sha(value['sourceTree'], 40), 'MONOREPO_SMOKE_INVALID')
    consolidated = value['schemaVersion'] == 2
    executions = tuple(EXECUTION_COVERAGE_V2) if consolidated else SCENARIOS
    binary(value['jar'])
    require(value['cleanup'] == {'status': 'OWNED_RESOURCES_REMOVED', 'errors': []}
            and value['boundaries'] == {'realWhatsApp': False, 'physicalDevices': False,
                                        'provider': 'FAKE_LOOPBACK', 'model': 'DISABLED'}
            and value['boundaries']['realWhatsApp'] is False
            and value['boundaries']['physicalDevices'] is False, 'MONOREPO_SMOKE_BOUNDARY_INVALID')
    runtime = value['runtime']
    require(exact(runtime, ('node', 'platform', 'arch', 'api', 'database')) and runtime == {
        'node': 'v22.23.2', 'platform': 'linux', 'arch': 'x64', 'api': 'LOCAL_JAR',
        'database': 'OWNED_POSTGRES_CONTAINER'}, 'MONOREPO_SMOKE_RUNTIME_INVALID')
    checks, scenarios = value['checks'], value['scenarios']
    require(isinstance(checks, list) and 6 <= len(checks) <= 300 and isinstance(scenarios, list)
            and len(scenarios) == len(executions), 'MONOREPO_SMOKE_SCENARIOS_INVALID')
    pairs, checked = set(), set()
    for row in checks:
        keys = ('scenario', 'execution', 'check', 'status') if consolidated else ('scenario', 'check', 'status')
        require(exact(row, keys) and row['scenario'] in SCENARIOS
                and isinstance(row['check'], str) and re.fullmatch('[a-z0-9_]{1,160}', row['check'])
                and row['status'] == 'PASS', 'MONOREPO_SMOKE_CHECK_INVALID')
        if consolidated:
            require(row['execution'] in executions
                    and row['scenario'] in EXECUTION_COVERAGE_V2[row['execution']], 'MONOREPO_SMOKE_CHECK_INVALID')
        pair = row['scenario'], row['check']
        require(pair not in pairs, 'MONOREPO_SMOKE_CHECK_INVALID')
        pairs.add(pair); checked.add(row['scenario'])
    require(checked == set(SCENARIOS) and all(isinstance(row, dict) and row.get('scenario') in executions for row in scenarios)
            and {row['scenario'] for row in scenarios} == set(executions), 'MONOREPO_SMOKE_SCENARIOS_INVALID')
    required_checks = {**REQUIRED_CHECKS, **({'110': TRACKING_CHECKS} if consolidated else {})}
    require(all(all((scenario, check) in pairs for check in required)
                for scenario, required in required_checks.items()), 'MONOREPO_SMOKE_CHECK_INVALID')
    for row in scenarios:
        allowed = {'scenario', 'realWhatsApp', 'physicalDevices', 'modelMode', 'pacing'} | ({'covers'} if consolidated else set())
        require(set(row) <= COUNT_FIELDS | TRUE_FIELDS | allowed
                and all(type(row[key]) is int and 0 <= row[key] <= 1000000000 for key in set(row) & COUNT_FIELDS)
                and all(row[key] is True for key in set(row) & TRUE_FIELDS)
                and row.get('realWhatsApp', False) is False and row.get('physicalDevices', False) is False
                and row.get('modelMode', 'disabled') == 'disabled' and row.get('modelCalls', 0) == 0,
                'MONOREPO_SMOKE_SCENARIOS_INVALID')
        if consolidated:
            require(row.get('covers') == list(EXECUTION_COVERAGE_V2[row['scenario']]), 'MONOREPO_SMOKE_SCENARIOS_INVALID')
        if row['scenario'] in ('110', '120', '130'):
            require(row.get('syntheticFixtures') is True and row.get('realWhatsApp') is False
                    and row.get('physicalDevices') is False, 'MONOREPO_SMOKE_SCENARIOS_INVALID')
        if 'pacing' in row:
            if row['scenario'] == '120':
                require(row['pacing'] == {'policy': 'API_ONLY'}, 'MONOREPO_SMOKE_SCENARIOS_INVALID')
            else:
                require(exact(row['pacing'], ('windowWaits', 'waitedMs'))
                        and all(type(item) is int and 0 <= item <= 1000000000 for item in row['pacing'].values()), 'MONOREPO_SMOKE_SCENARIOS_INVALID')
    delivery = next(row for row in scenarios if row['scenario'] == '120')
    require(all(type(delivery.get(key)) is int and delivery[key] == count for key, count in
                {'events': 9, 'attempts': 7, 'providerSubmissions': 3, 'connectionReads': 21}.items()), 'MONOREPO_DELIVERY_COUNTS_INVALID')
    location = next(row for row in scenarios if row['scenario'] == '130')
    require(type(location.get('pinQueries')) is int and location['pinQueries'] == 3 and location.get('t43') is True,
            'MONOREPO_LOCATION_COUNTS_INVALID')
    return value


def binary(value):
    require(exact(value, ('sha256', 'bytes')) and sha(value['sha256'])
            and positive(value['bytes']) and value['bytes'] <= 100000000, 'MONOREPO_BINARY_INVALID')
    return value


def candidate(value, gate_value, smoke_value):
    require(exact(value, ('schemaVersion', 'profile', 'status', 'source', 'ci', 'jar', 'gateSha256', 'smokeSha256', 'releaseEligible'))
            and type(value['schemaVersion']) is int and value['schemaVersion'] == 1 and value['profile'] == PROFILE
            and value['status'] == 'CANDIDATE_FOR_BOUNDED_LAB' and value['releaseEligible'] is False
            and sha(value['gateSha256']) and sha(value['smokeSha256']), 'MONOREPO_CANDIDATE_INVALID')
    src, origin, jar = source(value['source']), ci(value['ci']), binary(value['jar'])
    gate(gate_value); smoke(smoke_value)
    require(src == gate_value['source'] and origin == gate_value['ci'] and origin['sha'] == src['sha']
            and smoke_value['sourceSha'] == src['sha'] and smoke_value['sourceTree'] == src['tree']
            and smoke_value['jar'] == jar, 'MONOREPO_CANDIDATE_BINDING_CHANGED')
    return value


def images(value):
    require(exact(value, ('java', 'node', 'postgres', 'evolution', 'redis'))
            and all(isinstance(item, str) and re.fullmatch('[A-Za-z0-9_./:-]+@sha256:[a-f0-9]{64}', item)
                    for item in value.values()), 'MONOREPO_IMAGES_INVALID')
    return value


def ready_pins(contract):
    require(exact(contract, ('schemaVersion', 'evidenceProfile', 'cardId', 'state', 'source', 'backend', 'verification', 'images', 'policy'))
            and contract['schemaVersion'] == 2 and contract['evidenceProfile'] == PROFILE
            and contract['cardId'] == CARD and contract['state'] == 'PINNED_FOR_PREFLIGHT'
            and contract['policy'] == POLICY, 'MONOREPO_CONTRACT_INVALID')
    src = source(contract['source']); images(contract['images'])
    backend, pins = contract['backend'], contract['verification']
    require(exact(backend, ('sourceSha', 'jarSha256', 'jarBytes', 'verifiedCiReceiptSha256'))
            and backend['sourceSha'] == src['sha'] and sha(backend['verifiedCiReceiptSha256']), 'MONOREPO_CONTRACT_INVALID')
    binary({'sha256': backend['jarSha256'], 'bytes': backend['jarBytes']})
    require(exact(pins, ('runId', 'runAttempt', 'artifactId', 'artifactSha256', 'candidateSha256', 'gateSha256', 'smokeSha256'))
            and all(positive(pins[key]) for key in ('runId', 'runAttempt', 'artifactId'))
            and all(sha(pins[key]) for key in ('artifactSha256', 'candidateSha256', 'gateSha256', 'smokeSha256')), 'MONOREPO_CONTRACT_INVALID')
    return backend, pins


def provenance(value, origin):
    require(exact(value, ('repository', 'runId', 'runAttempt', 'headSha', 'workflowPath', 'event', 'status',
                          'conclusion', 'job', 'jobId', 'jobConclusion', 'artifactId', 'artifactSha256'))
            and value['repository'] == REPOSITORY and value['runId'] == origin['runId']
            and value['runAttempt'] == origin['runAttempt'] and value['headSha'] == origin['sha']
            and value['workflowPath'] == WORKFLOW and value['event'] == 'workflow_dispatch'
            and value['status'] == 'completed' and value['conclusion'] == 'success'
            and value['job'] == 'candidate' and positive(value['jobId']) and value['jobConclusion'] == 'success'
            and positive(value['artifactId']) and sha(value['artifactSha256']), 'MONOREPO_PROVENANCE_INVALID')


def validate_envelope(contract, raw, jar):
    backend, pins = ready_pins(contract)
    require(digest(raw) == backend['verifiedCiReceiptSha256'], 'VERIFIED_RECEIPT_DIGEST_CHANGED')
    value = object_json(raw)
    require(exact(value, ('schemaVersion', 'evidenceProfile', 'status', 'candidateOriginal', 'gateOriginal', 'smokeOriginal', 'provenance'))
            and value['schemaVersion'] == 2 and value['evidenceProfile'] == PROFILE
            and value['status'] == 'VERIFIED_FOR_PREFLIGHT', 'MONOREPO_ENVELOPE_INVALID')
    originals = {}
    for name in ('candidate', 'gate', 'smoke'):
        text = value[name + 'Original']
        require(isinstance(text, str), 'MONOREPO_ORIGINAL_REQUIRED')
        encoded = text.encode('utf-8')
        require(digest(encoded) == pins[name + 'Sha256'], 'MONOREPO_ORIGINAL_CHANGED')
        originals[name] = object_json(encoded)
    c = candidate(originals['candidate'], originals['gate'], originals['smoke'])
    require(c['source'] == contract['source'] and c['gateSha256'] == pins['gateSha256']
            and c['smokeSha256'] == pins['smokeSha256'] and c['jar'] == {'sha256': backend['jarSha256'], 'bytes': backend['jarBytes']}, 'MONOREPO_ENVELOPE_BINDING_CHANGED')
    provenance(value['provenance'], c['ci'])
    require(all(value['provenance'][key] == pins[key] for key in ('runId', 'runAttempt', 'artifactId', 'artifactSha256')), 'MONOREPO_PROVENANCE_CHANGED')
    jar = Path(jar)
    require(jar.is_file() and not jar.is_symlink() and jar.stat().st_size == backend['jarBytes']
            and hash_file(jar) == backend['jarSha256'], 'TESTED_JAR_CHANGED')
    return {'sourceSha': backend['sourceSha'], 'jarSha256': backend['jarSha256'],
            'gateRun': pins['runId'], 'smokeRun': pins['runId']}


def verify_package_sources(contract, files):
    """Every transported source must belong to the independently pinned Git matrix."""
    expected = contract['source']['files']
    for name, actual in files.items():
        if name.startswith('inputs/source/'):
            original = name[len('inputs/source/'):].replace('integrations/whatsapp/', 'src/whatsapp/', 1)
        elif name.endswith(('.py', '-pins.json')):
            original = 'src/whatsapp/lab/live-runtime/' + name
        else:
            continue
        require(original in expected and expected[original]['sha256'] == actual, 'MONOREPO_PACKAGED_SOURCE_CHANGED')
