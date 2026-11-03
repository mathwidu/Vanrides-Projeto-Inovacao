"""Synthetic CI/artifact fixtures only. No GitHub, JVM, Docker, device or secrets."""
import copy
import json
import os
from pathlib import Path
import stat
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import warnings
import zipfile

import collect
import evidence
import monorepo_receipt as M
import plan
import prepare
import stage

E = collect.exporter
SHA, TREE = 'a' * 40, 'b' * 40


def fixture():
    source = {'repository': M.REPOSITORY, 'sha': SHA, 'tree': TREE, 'files': {
        E.MATRIX_PATH: {'gitBlob': 'c' * 40, 'sha256': 'd' * 64, 'bytes': 1}}}
    ci = {'repository': M.REPOSITORY, 'sha': SHA, 'runId': 101, 'runAttempt': 2,
          'workflowRef': M.REPOSITORY + '/' + M.WORKFLOW + '@refs/heads/test-fixture',
          'event': 'workflow_dispatch', 'job': 'candidate'}
    tests = {'tests': 2, 'skipped': 0, 'failures': 0, 'errors': 0}
    row = {'className': 'example.RequiredIntegrationTest', 'tests': 2, 'sha256': 'e' * 64}
    manifest = {'status': 'passed', 'suite': 'integration', 'commandProfile': 'maven-integration-all-tests',
                'retentionClass': 'ci', 'eligibleForRelease': False,
                'source': {'gitSha': SHA, 'gitTree': TREE, 'runStartGitSha': SHA, 'runStartGitTree': TREE,
                           'clean': True, 'dirtyPathCount': 0, 'cleanRequired': True},
                'environment': {'scope': 'all-tests', 'userHomeIsolated': True, 'mavenRcSkipped': True,
                                'dockerEndpointTransport': 'unix'}, 'totals': tests, 'reports': [row],
                'allReports': {'requiredGreen': True, 'reportCount': 1, 'totals': tests,
                               'reports': [{**row, **tests}]}}
    gate = {'schemaVersion': 1, 'profile': 'MONOREPO_CANONICAL_GATE_V1', 'status': 'PASS', 'source': source,
            'ci': ci, 'manifestSha256': 'f' * 64, 'manifest': manifest, 'requiredClasses': {row['className']: 2}}
    jar = b'UNIT TEST ONLY - NOT A JVM ARTIFACT'
    checks = [{'scenario': scenario, 'check': check, 'status': 'PASS'} for scenario in M.SCENARIOS
              for check in sorted(M.REQUIRED_CHECKS.get(scenario, {'fixture_check'}))]
    scenarios = [{'scenario': item, 'syntheticFixtures': True, 'realWhatsApp': False, 'physicalDevices': False} for item in M.SCENARIOS]
    scenarios[-2].update(events=9, attempts=7, providerSubmissions=3, connectionReads=21)
    scenarios[-2]['pacing'] = {'policy': 'API_ONLY'}
    scenarios[-1].update(pinQueries=3, t43=True)
    smoke = {'schemaVersion': 1, 'profile': 'VANRIDES_ACADEMIC_INTEGRATION_V1', 'status': 'PASS',
             'sourceSha': SHA, 'sourceTree': TREE, 'jar': {'sha256': M.digest(jar), 'bytes': len(jar)},
             'checks': checks, 'scenarios': scenarios, 'cleanup': {'status': 'OWNED_RESOURCES_REMOVED', 'errors': []},
             'boundaries': {'realWhatsApp': False, 'physicalDevices': False, 'provider': 'FAKE_LOOPBACK', 'model': 'DISABLED'},
             'runtime': {'node': 'v22.23.2', 'platform': 'linux', 'arch': 'x64', 'api': 'LOCAL_JAR', 'database': 'OWNED_POSTGRES_CONTAINER'}}
    return gate, smoke, jar


def candidate(gate, smoke):
    return copy.deepcopy({'schemaVersion': 1, 'profile': M.PROFILE, 'status': 'CANDIDATE_FOR_BOUNDED_LAB',
            'source': gate['source'], 'ci': gate['ci'], 'jar': smoke['jar'],
            'gateSha256': M.digest(plan.receipt_bytes(gate)), 'smokeSha256': M.digest(plan.receipt_bytes(smoke)), 'releaseEligible': False})


def api_fixture(digest, mutate=None):
    run = {'id': 101, 'head_sha': SHA, 'head_branch': 'test-fixture', 'path': M.WORKFLOW, 'event': 'workflow_dispatch',
           'status': 'completed', 'conclusion': 'success', 'run_attempt': 2, 'repository': {'full_name': M.REPOSITORY}}
    jobs = {'total_count': 1, 'jobs': [{'id': 201, 'name': collect.JOB_NAME, 'run_id': 101,
                                      'head_sha': SHA, 'status': 'completed', 'conclusion': 'success'}]}
    artifact = {'id': 301, 'expired': False, 'digest': 'sha256:' + digest, 'name': f'whatsapp-lab-{SHA}-101-2',
                'workflow_run': {'id': 101, 'head_sha': SHA}}
    values = {'runs/101': run, 'runs/101/attempts/2/jobs?per_page=100': jobs, 'artifacts/301': artifact}
    if mutate:
        mutate(run, jobs, artifact)
    calls = []
    def api(route):
        calls.append(route)
        return copy.deepcopy(values[route.split('/actions/', 1)[1]])
    return api, calls


def zip_fixture(root, gate=None, smoke=None, mutate_candidate=None):
    original_gate, original_smoke, jar = fixture()
    gate = original_gate if gate is None else gate
    smoke = original_smoke if smoke is None else smoke
    c = candidate(gate, smoke)
    if mutate_candidate:
        mutate_candidate(c)
    archive = root / 'artifact.zip'
    with zipfile.ZipFile(archive, 'w') as bundle:
        for name, content in (('backend.jar', jar), ('gate.json', plan.receipt_bytes(gate)),
                              ('smoke.json', plan.receipt_bytes(smoke)), ('candidate.json', plan.receipt_bytes(c))):
            bundle.writestr(name, content)
    return archive, plan.hash_file(archive)


def images():
    return {name: 'fixture/' + name + '@sha256:' + 'f' * 64 for name in ('java', 'node', 'postgres', 'evolution', 'redis')}


class ValidationTests(unittest.TestCase):
    def test_gate_and_six_journeys_accept_only_the_declared_fixture(self):
        gate, smoke, _ = fixture()
        M.candidate(candidate(gate, smoke), gate, smoke)

    def test_receipt_writer_and_actual_scenario_projection_shapes_are_consumable(self):
        # Field sets mirror the six current scenario return values and the
        # academic runner's pacing projection. Counts below are synthetic data,
        # never observations of a gate or journey being run by this test.
        gate, smoke, _ = fixture()
        common = dict(uniqueEvents=1, duplicateEvents=1, commandReceipts=1, submittedAttempts=1,
                      providerRequests=1, workerStarts=1)
        smoke['scenarios'][0] = {'scenario': '070', 'uniqueEvents': 6, 'duplicateEvents': 7,
            'commandReceipts': 6, 'outbox': 6, 'supportCases': 1, 'providerRequests': 6,
            'submittedAttempts': 6, 'workerStarts': 2, 'modelCalls': 0}
        smoke['scenarios'][1] = {'scenario': '080', **common, 'outbox': 1, 'modelCalls': 0,
            'controlledBusinessRejections': 1, 'privateInviteHashVerified': True, 'rawInviteAbsentFromDurablePlaintext': True}
        smoke['scenarios'][2] = {'scenario': '100', **common, 'crossActorDeliveries': 1, 'actors': 3,
            'ticks': 1, 'reminderSubmissions': 1, 'seededReminderWindows': 1, 'modelMode': 'disabled', 'realWhatsApp': False}
        for index in (3, 5):
            smoke['scenarios'][index].update(common, crossActorDeliveries=1, actors=3, sessionGenerations=1)
        for row in smoke['scenarios']:
            row['pacing'] = {'policy': 'API_ONLY'} if row['scenario'] == '120' else {'windowWaits': 0, 'waitedMs': 0}
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder).resolve(); (root / 'checkout').mkdir()
            input_file = root / 'input.json'; input_file.write_bytes(plan.receipt_bytes(smoke))
            target = root / 'smoke.json'
            script = "import fs from 'node:fs';const {prepareAcademicReceipt}=await import(process.argv[1]);prepareAcademicReceipt(process.argv[2],process.argv[3])(JSON.parse(fs.readFileSync(process.argv[4],'utf8')));"
            result = subprocess.run(['node', '--input-type=module', '-e', script,
                                     (plan.ROOT / 'src/whatsapp/scripts/academic-receipt.mjs').as_uri(),
                                     str(target), str(root / 'checkout'), str(input_file)],
                                    capture_output=True, text=True, timeout=10)
            self.assertEqual(result.returncode, 0, result.stderr)
            received = M.object_json(target.read_bytes())
            self.assertEqual(received, smoke)
            M.candidate(candidate(gate, received), gate, received)
        for scenario, pacing in (('120', {'windowWaits': 0, 'waitedMs': 0}), ('070', {'policy': 'API_ONLY'})):
            changed = copy.deepcopy(smoke)
            next(row for row in changed['scenarios'] if row['scenario'] == scenario)['pacing'] = pacing
            with self.subTest(scenario=scenario), self.assertRaises(plan.Blocked):
                M.smoke(changed)
    def test_gate_incomplete_dirty_skipped_reduced_or_unrelated_reports_fail(self):
        changes = [lambda g: g['manifest'].update(status='prepared'),
                   lambda g: g['manifest']['source'].update(dirtyPathCount=1),
                   lambda g: g['manifest']['source'].update(runStartGitTree='c' * 40),
                   lambda g: g['manifest']['totals'].update(skipped=1),
                   lambda g: g['manifest']['allReports']['reports'][0].update(skipped=1),
                   lambda g: g['requiredClasses'].update({'example.RequiredIntegrationTest': 1}),
                   lambda g: g['manifest']['allReports'].update(reports=[]),
                   lambda g: g['manifest'].update(eligibleForRelease=True)]
        for change in changes:
            gate, _, _ = fixture(); change(gate)
            with self.subTest(change=change), self.assertRaises(plan.Blocked):
                M.gate(gate)

    def test_smoke_requires_all_six_checks_counts_cleanup_and_exact_binary_source(self):
        changes = [lambda s: s['checks'].pop(), lambda s: s['checks'].append(copy.deepcopy(s['checks'][0])),
                   lambda s: s['scenarios'].pop(), lambda s: s['scenarios'][-2].update(providerSubmissions=4),
                   lambda s: s['scenarios'][-1].update(pinQueries=1), lambda s: s['cleanup']['errors'].append('fixture_error'),
                   lambda s: s['boundaries'].update(realWhatsApp=True), lambda s: s['runtime'].update(platform='darwin'),
                   lambda s: s['scenarios'][0].update(unexpectedPrivateField='DO_NOT_EXPORT'),
                   lambda s: s.update(sourceSha='c' * 40), lambda s: s['jar'].update(sha256='c' * 64)]
        for change in changes:
            gate, smoke, _ = fixture(); original = candidate(gate, smoke); change(smoke)
            with self.subTest(change=change), self.assertRaises(plan.Blocked):
                M.candidate(original, gate, smoke)

    def test_unknown_profile_never_falls_back_to_historical_receipt(self):
        for contract in ({'schemaVersion': 2}, {'schemaVersion': 1, 'evidenceProfile': M.PROFILE}, {'schemaVersion': 3}):
            with self.assertRaises(plan.Blocked):
                evidence.ready_pins(contract)


class CollectionTests(unittest.TestCase):
    def test_exact_artifact_independent_digest_and_api_provenance_produce_no_release_approval(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder).resolve(); archive, digest = zip_fixture(root)
            api, calls = api_fixture(digest)
            contract = collect.collect(archive, digest, 101, 301, SHA, TREE, images(), root / 'verified', api=api)
            raw = (root / 'verified/verified-ci-receipt.json').read_bytes()
            evidence.validate_envelope(contract, raw, root / 'verified/backend.jar')
            self.assertEqual(len(calls), 3)
            self.assertEqual(contract['state'], 'PINNED_FOR_PREFLIGHT')
            self.assertFalse(contract['policy']['newStartsEnabled'])
            for path in (root / 'verified').iterdir():
                self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o600)
            altered = json.loads(raw); altered['status'] = 'RELEASE_ACCEPTED'
            with self.assertRaisesRegex(plan.Blocked, 'VERIFIED_RECEIPT_DIGEST_CHANGED'):
                evidence.validate_envelope(contract, plan.receipt_bytes(altered), root / 'verified/backend.jar')
            with self.assertRaises(plan.Blocked):
                evidence.validate_envelope(contract, raw, archive)

    def test_rebound_outer_digest_cannot_hide_invalid_embedded_gate_or_provenance(self):
        for change in ('gate', 'provenance', 'source', 'release'):
            with tempfile.TemporaryDirectory() as folder:
                root = Path(folder).resolve(); archive, digest = zip_fixture(root); api, _ = api_fixture(digest)
                contract = collect.collect(archive, digest, 101, 301, SHA, TREE, images(), root / 'verified', api=api)
                raw = (root / 'verified/verified-ci-receipt.json').read_bytes(); value = M.object_json(raw)
                if change == 'gate':
                    inner = json.loads(value['gateOriginal']); inner['manifest']['allReports']['requiredGreen'] = False
                    value['gateOriginal'] = plan.receipt_bytes(inner).decode()
                    contract['verification']['gateSha256'] = M.digest(value['gateOriginal'].encode())
                elif change == 'provenance':
                    value['provenance']['runAttempt'] = 3
                elif change == 'source':
                    contract['source']['tree'] = 'f' * 40
                else:
                    value['status'] = 'RELEASE_ACCEPTED'
                raw = plan.receipt_bytes(value); contract['backend']['verifiedCiReceiptSha256'] = M.digest(raw)
                with self.subTest(change=change), self.assertRaises(plan.Blocked):
                    evidence.validate_envelope(contract, raw, root / 'verified/backend.jar')

    def test_rerun_other_workflow_failed_job_expired_or_other_artifact_rejected_before_output(self):
        changes = [lambda r, j, a: r.update(run_attempt=3), lambda r, j, a: r.update(path='.github/workflows/backend.yml'),
                   lambda r, j, a: r.update(head_sha='c' * 40), lambda r, j, a: r.update(status='in_progress'),
                   lambda r, j, a: r.update(event='pull_request'), lambda r, j, a: j['jobs'][0].update(conclusion='failure'),
                   lambda r, j, a: j.update(total_count=2), lambda r, j, a: a.update(expired=True),
                   lambda r, j, a: a.update(digest='sha256:' + 'c' * 64), lambda r, j, a: a.update(name='old-attempt')]
        for change in changes:
            with tempfile.TemporaryDirectory() as folder:
                root = Path(folder).resolve(); archive, digest = zip_fixture(root)
                api, _ = api_fixture(digest, change)
                with self.subTest(change=change), self.assertRaises(plan.Blocked):
                    collect.collect(archive, digest, 101, 301, SHA, TREE, images(), root / 'output', api=api)
                self.assertFalse((root / 'output').exists())

    def test_zip_traversal_symlink_extra_duplicate_and_wrong_anchor_fail_without_api(self):
        for name, kind in (('../escape', 'file'), ('/absolute', 'file'), ('backend.jar', 'duplicate'), ('link', 'symlink')):
            with tempfile.TemporaryDirectory() as folder:
                root = Path(folder).resolve(); archive, _ = zip_fixture(root)
                with zipfile.ZipFile(archive, 'a') as bundle:
                    if kind == 'symlink':
                        info = zipfile.ZipInfo(name); info.create_system = 3
                        info.external_attr = (stat.S_IFLNK | 0o777) << 16; bundle.writestr(info, 'foreign')
                    else:
                        with warnings.catch_warnings():
                            warnings.simplefilter('ignore', UserWarning); bundle.writestr(name, b'foreign')
                with self.subTest(kind=kind), self.assertRaises(plan.Blocked):
                    collect.inspect_artifact(archive, plan.hash_file(archive))
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder).resolve(); archive, _ = zip_fixture(root)
            with self.assertRaises(plan.Blocked):
                collect.inspect_artifact(archive, '0' * 64)

    def test_a_symlink_in_the_exact_four_file_set_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder).resolve(); archive, _ = zip_fixture(root)
            replacement = root / 'symlink.zip'
            with zipfile.ZipFile(archive) as source, zipfile.ZipFile(replacement, 'w') as target:
                for name in collect.FILES - {'backend.jar'}:
                    target.writestr(name, source.read(name))
                row = zipfile.ZipInfo('backend.jar'); row.create_system = 3
                row.external_attr = (stat.S_IFLNK | 0o777) << 16; target.writestr(row, 'foreign')
            with self.assertRaisesRegex(plan.Blocked, 'ARTIFACT_FILES_INVALID'):
                collect.inspect_artifact(replacement, plan.hash_file(replacement))

    def test_archive_and_original_hashes_cannot_be_replaced_with_self_declared_pass(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder).resolve()
            archive, digest = zip_fixture(root, mutate_candidate=lambda c: c.update(gateSha256='0' * 64))
            with self.assertRaises(plan.Blocked):
                collect.inspect_artifact(archive, digest)


class ExportTests(unittest.TestCase):
    def environment(self, source):
        return {'GITHUB_REPOSITORY': M.REPOSITORY, 'GITHUB_SHA': source['sha'], 'GITHUB_RUN_ID': '101',
                'GITHUB_RUN_ATTEMPT': '2', 'GITHUB_WORKFLOW_REF': M.REPOSITORY + '/' + M.WORKFLOW + '@refs/heads/test-fixture',
                'GITHUB_EVENT_NAME': 'workflow_dispatch', 'GITHUB_JOB': 'candidate'}

    def test_explicit_gate_root_and_bundle_never_select_latest_or_overwrite(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder).resolve(); gate, smoke, jar = fixture()
            evidence_root = root / 'evidence'; run = evidence_root / 'integration-20261103T100000Z-1'; run.mkdir(parents=True)
            manifest = {**gate['manifest'], 'schemaVersion': 2, 'runId': run.name}
            manifest['environment']['privateSentinel'] = 'MUST_NOT_EXPORT'
            (run / 'manifest.json').write_bytes(plan.receipt_bytes(manifest))
            env = self.environment(gate['source'])
            repository = root / 'repository'; repository.mkdir()
            with patch.object(E, 'source_snapshot', return_value=gate['source']), patch.object(E, 'required_classes', return_value=gate['requiredClasses']):
                E.export_gate(evidence_root, repository, root / 'gate.json', env)
                self.assertNotIn(b'MUST_NOT_EXPORT', (root / 'gate.json').read_bytes())
                (root / 'smoke.json').write_bytes(plan.receipt_bytes(smoke)); (root / 'input.jar').write_bytes(jar)
                E.export_bundle(root / 'gate.json', root / 'smoke.json', root / 'input.jar', repository, root / 'bundle', env)
                self.assertEqual({path.name for path in (root / 'bundle').iterdir()}, collect.FILES)
                (evidence_root / 'integration-20261103T100001Z-2').mkdir()
                with self.assertRaisesRegex(plan.Blocked, 'EXACTLY_ONE_RUN'):
                    E.export_gate(evidence_root, repository, root / 'second.json', env)
            with self.assertRaises(plan.Blocked):
                E.write_new(root / 'gate.json', b'overwrite')

    def test_ci_fields_and_output_paths_cannot_be_used_to_claim_another_source_or_release(self):
        gate, _, _ = fixture(); env = self.environment(gate['source'])
        for name, value in (('GITHUB_SHA', 'bad'), ('GITHUB_RUN_ATTEMPT', '0'),
                            ('GITHUB_EVENT_NAME', 'pull_request'), ('GITHUB_JOB', 'deploy')):
            with self.subTest(field=name), self.assertRaises(plan.Blocked):
                E.ci_context({**env, name: value})
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder).resolve(); (root / '.git').mkdir()
            with self.assertRaisesRegex(plan.Blocked, 'OUTPUT_INSIDE_GIT'):
                E.write_new(root / 'receipt.json', b'fixture')


class PackageTests(unittest.TestCase):
    def test_new_profile_packages_extracts_imports_and_preserves_legacy_validator(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder).resolve(); gate, smoke, _ = fixture()
            paths = {name: plan.ROOT / name for name in plan.source_hashes()}
            paths.update({'src/whatsapp/lab/live-runtime/' + name: plan.HERE / name for name in prepare.RUNTIME_FILES})
            originals = json.loads((plan.HERE / 'canonical110-pins.json').read_bytes())
            paths.update({name.replace('integrations/whatsapp/', 'src/whatsapp/', 1): plan.resolve_source(name) for name in originals})
            matrix = {name: {'gitBlob': 'c' * 40, 'sha256': plan.hash_file(path), 'bytes': path.stat().st_size}
                      for name, path in paths.items()}
            gate['source']['files'] = matrix
            archive, digest = zip_fixture(root, gate=gate, smoke=smoke); api, _ = api_fixture(digest)
            contract = collect.collect(archive, digest, 101, 301, SHA, TREE, images(), root / 'verified', api=api)
            with patch.object(E, 'source_snapshot', return_value=contract['source']):
                report = prepare.package(root / 'package', root / 'verified/backend.jar',
                                         root / 'verified/verified-ci-receipt.json', root / 'verified/contract.json')
            files, captured = stage.inspect_archive(root / 'package.tar', report['archiveSha256'])
            self.assertEqual(json.loads(captured['source-manifest.json'])['profile'], M.PROFILE)
            receiver = stage.configured_receiver(files, captured['contract.json'], captured['source-manifest.json'])
            remote = root / 'remote'; remote.mkdir(mode=0o700)
            with (root / 'package.tar').open('rb') as stream:
                receiver.stage(remote, 'monorepo-fixture', report['archiveSha256'], report['sourceManifestSha256'],
                               (root / 'package.tar').stat().st_size, files, stream)
            extracted = remote / 'monorepo-fixture'
            script = "import sys;sys.path.insert(0,'.');import control;from pathlib import Path;control.verify_package(Path.cwd())"
            result = subprocess.run([sys.executable, '-I', '-B', '-c', script], cwd=extracted,
                                    env={'PATH': os.defpath}, capture_output=True, text=True, timeout=10)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertFalse((extracted / 'private').exists())
            changed = dict(files); changed['evidence.py'] = '0' * 64
            with self.assertRaisesRegex(plan.Blocked, 'PACKAGED_SOURCE_CHANGED'):
                M.verify_package_sources(contract, changed)
            with patch.object(E, 'source_snapshot', return_value={**contract['source'], 'sha': 'c' * 40}):
                with self.assertRaisesRegex(plan.Blocked, 'CHECKOUT_SOURCE_CHANGED'):
                    prepare.package(root / 'wrong-source', root / 'verified/backend.jar',
                                    root / 'verified/verified-ci-receipt.json', root / 'verified/contract.json')
            self.assertFalse((root / 'wrong-source').exists())

    def test_real_git_source_snapshot_rejects_dirty_and_tracked_symlink(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder).resolve()
            subprocess.run(['git', 'init', '-q', str(root)], check=True, capture_output=True)
            names = [*E.CANONICAL, E.MATRIX_PATH, 'src/whatsapp/worker/main.mjs', 'src/whatsapp/agent/main.mjs',
                     'src/whatsapp/transport/main.mjs', 'src/whatsapp/lab/live-ingress/main.mjs']
            for name in names:
                path = root / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_text('fixture\n')
            def commit_fixture():
                subprocess.run(['git', '-C', str(root), 'add', '--all'], check=True, capture_output=True)
                subprocess.run(['git', '-C', str(root), '-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid',
                                '-c', 'commit.gpgsign=false', 'commit', '-qm', 'synthetic fixture only'], check=True, capture_output=True)
            commit_fixture()
            self.assertEqual(set(E.source_snapshot(root)['files']), set(names))
            (root / names[0]).write_text('changed\n')
            with self.assertRaisesRegex(plan.Blocked, 'CLEAN_CHECKOUT'):
                E.source_snapshot(root)
            (root / names[0]).unlink(); (root / names[0]).symlink_to('/nonexistent')
            commit_fixture()
            with self.assertRaisesRegex(plan.Blocked, 'SOURCE_TYPE_INVALID'):
                E.source_snapshot(root)
