"""Offline evidence, private-files, receiver and cleanup regression checks."""
import ast
import copy
from contextlib import contextmanager
import io
import inspect
import json
import os
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import unittest
from unittest.mock import patch

import control
import plan
import plan_test
import prepare
import private_files as private
import receipt
import stage


def envelope_fixture(root):
    """Test-only fabricated CI documents; never persisted as project evidence."""
    contract, _ = plan.load_contract()
    jar = root / 'fake.jar'
    jar.write_bytes(b'UNIT TEST FIXTURE - NEVER A JVM ARTIFACT')
    b = contract['backend']
    b.update(gateAndSameArtifactSmoke='PASS', jarSha256=plan.hash_file(jar), jarBytes=jar.stat().st_size)
    totals = {'tests': 1640, 'failures': 0, 'errors': 0, 'skipped': 0}
    gate = {'state': 'PASS', 'repository': 'mathwidu/vanrides-backend', 'sha': b['sourceSha'], 'run': b['ciRun'],
        'url': f"https://github.com/mathwidu/vanrides-backend/actions/runs/{b['ciRun']}", 'workflowEvent': 'workflow_dispatch',
        'jobs': {'test': 'success', 'build_and_push': 'skipped', 'deploy_swarm': 'skipped'},
        'allTests': totals, 'postgresTests': {**totals, 'tests': 531}, 'allClasses': 232, 'postgresClasses': 71,
        'artifactId': 1, 'artifactSha256': 'sha256:' + 'a' * 64, 'manifestSha256': 'b' * 64,
        'jobStarted': '2026-11-01T10:00:00Z', 'jobFinished': '2026-11-01T10:01:00Z'}
    pins = {'backendTree': 'c' * 40, 'coordinationCommit': 'd' * 40, 'coordinationTree': 'e' * 40,
        'gate': {key: copy.deepcopy(gate[key]) for key in ('allTests', 'allClasses', 'postgresTests', 'postgresClasses',
                                                         'artifactId', 'artifactSha256', 'manifestSha256')},
        'smoke': {'repository': 'mathwidu/vanrides-backend', 'run': 2, 'url': 'https://github.com/mathwidu/vanrides-backend/actions/runs/2',
            'headSha': 'f' * 40, 'headTree': 'a' * 40, 'headBranch': 'codex/wa120-synthetic-journey',
            'workflowPath': '.github/workflows/wa120-synthetic-journey.yml', 'runAttempt': 1,
            'artifactId': 2, 'artifactSha256': 'sha256:' + 'c' * 64,
            'bundleManifestSha256': 'd' * 64, 'fileMatrixSha256': 'e' * 64}}
    smoke = {'schemaVersion': 1, 'status': 'PASS', 'cardId': plan.CARD,
        'profile': 'LINUX_LOOPBACK_SYNTHETIC_DELIVERY_RECOVERY_V1',
        'canonicalMavenSuite': 'NOT_RERUN_SEPARATE_EVIDENCE',
        'runtime': {'platform': 'linux/amd64', 'node': '22.23.2', 'java': 'openjdk 17.0.16 2025-07-15'},
        'backend': {'commit': b['sourceSha'], 'tree': pins['backendTree']},
        'coordination': {'commit': pins['coordinationCommit'], 'tree': pins['coordinationTree']},
        'harness': {'commit': pins['smoke']['headSha'], 'tree': pins['smoke']['headTree']},
        'jarSha256': b['jarSha256'], 'bundleManifestSha256': pins['smoke']['bundleManifestSha256'],
        'fileMatrixSha256': pins['smoke']['fileMatrixSha256'], 'checks': [{'check': key, 'status': 'PASS'} for key in receipt.CHECKS],
        'codes': [], 'boundaries': dict(realWhatsApp=False, openai=False, nativeDevices=False, rawGps=False, vps=False, stores=False),
        'delivery': {**receipt.TOTALS, 'sink': 'LOOPBACK_SYNTHETIC_RECEIVER', 'externalBusinessSubmissions': 0,
                     'evidence': 'PINNED_ALLOWLIST_AND_LOOPBACK_RECEIVER_COUNTS'},
        'cleanup': {'apiStopped': True, 'smokeProcessesStopped': True, 'containerRemoved': True,
            'portsClosed': True, 'privateRuntimeRemoved': True, 'cohortsReset': 1, 'issuedCredentials': 4,
            'revokedCredentials': 4, 'graph': dict.fromkeys(receipt.TABLES, 0)},
        'memory': {'aborted': False}, 'stages': [{'stage': key, 'status': 'PASS'} for key in ('BUILD', 'POSTGRES', 'API', 'JOURNEY', 'CLEANUP')]}
    b['verificationPins'] = pins
    provenance = {key: pins['smoke'][key] for key in ('repository', 'run', 'url', 'headSha', 'headBranch', 'workflowPath', 'runAttempt', 'artifactId', 'artifactSha256')}
    provenance.update(workflowEvent='push', status='completed', conclusion='success')
    return contract, jar, gate, smoke, provenance


def bind_envelope(contract, gate, smoke, provenance):
    originals = {'gateOriginal': json.dumps(gate) + '\n', 'smokeOriginal': json.dumps(smoke) + '\n'}
    for phase in ('gate', 'smoke'):
        contract['backend']['verificationPins'][phase]['receiptSha256'] = receipt.digest(originals[phase + 'Original'].encode())
    value = {'schemaVersion': 1, 'binary': {'sha256': contract['backend']['jarSha256'], 'bytes': contract['backend']['jarBytes']},
             **originals, 'smokeProvenance': provenance}
    raw = plan.receipt_bytes(value)
    contract['backend']['verifiedCiReceiptSha256'] = receipt.digest(raw)
    return raw


class EvidenceTests(unittest.TestCase):
    def test_pending_pins_block_before_output_creation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            received = root / 'receipt.json'; received.write_bytes(b'{}')
            jar = root / 'app.jar'; jar.write_bytes(b'fixture')
            contract, _ = plan.load_contract()
            contract['backend'].update(gateAndSameArtifactSmoke='PENDING', jarSha256=None, jarBytes=None,
                                        verifiedCiReceiptSha256=None, verificationPins=None)
            contract_file = root / 'pending-contract.json'
            contract_file.write_bytes(plan.receipt_bytes(contract))
            with self.assertRaisesRegex(plan.Blocked, 'BACKEND_GATE_SMOKE_JAR_RECEIPT_PENDING'):
                prepare.package(root / 'output', jar, received, contract_file)
            self.assertFalse((root / 'output').exists())

    def test_original_receipts_and_actual_binary_must_agree(self):
        with tempfile.TemporaryDirectory() as directory:
            contract, jar, gate, smoke, provenance = envelope_fixture(Path(directory).resolve())
            raw = bind_envelope(contract, gate, smoke, provenance)
            self.assertEqual(receipt.validate_envelope(contract, raw, jar)['jarSha256'], plan.hash_file(jar))
            jar.write_bytes(b'different fixture')
            with self.assertRaisesRegex(plan.Blocked, 'TESTED_JAR_CHANGED'):
                receipt.validate_envelope(contract, raw, jar)

    def test_deep_gate_and_smoke_mutations_fail_even_with_matching_outer_hash(self):
        for change in ('skipped', 'deploy', 'cleanup', 'checks', 'counts', 'graph', 'stage', 'sha', 'provenance'):
            with tempfile.TemporaryDirectory() as directory:
                c, jar, gate, smoke, provenance = envelope_fixture(Path(directory).resolve())
                if change == 'skipped': gate['allTests'] = {**gate['allTests'], 'skipped': 1}
                elif change == 'deploy': gate['jobs']['deploy_swarm'] = 'success'
                elif change == 'cleanup': smoke['cleanup']['privateRuntimeRemoved'] = False
                elif change == 'checks': smoke['checks'].pop()
                elif change == 'counts': smoke['delivery']['providerSubmissions'] = 4
                elif change == 'graph': smoke['cleanup']['graph'].pop('identity')
                elif change == 'stage': smoke['stages'].pop(0)
                elif change == 'sha': smoke['backend']['commit'] = '0' * 40
                else: provenance['conclusion'] = 'failure'
                raw = bind_envelope(c, gate, smoke, provenance)
                with self.subTest(change=change), self.assertRaises(plan.Blocked):
                    receipt.validate_envelope(c, raw, jar)


class WriterTests(unittest.TestCase):
    def test_exclusive_owner_private_and_replacement_checks(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            identity = private.write_new(root, 'token', b'unit-private-sentinel')
            self.assertEqual((root / 'token').stat().st_mode & 0o777, 0o600)
            with self.assertRaises(FileExistsError): private.write_new(root, 'token', b'overwrite')
            (root / 'token').unlink(); (root / 'token').symlink_to(root / 'foreign')
            with self.assertRaises(Exception): private.replace_owned(root, identity, b'overwrite')
            self.assertTrue((root / 'token').is_symlink())
            self.assertFalse((root / 'foreign').exists())

    def test_bootstrap_contains_no_fake_issued_principal(self):
        value = plan_test.private_config()
        value['secrets'] = {key: raw for key, raw in value['secrets'].items() if key not in plan.ISSUED_SECRETS}
        files = plan.render_private_files(value, issued=False)
        self.assertNotIn('ingress.json', files)
        self.assertNotIn('delivery-token', files)
        with self.assertRaises(plan.Blocked): plan.validate_private(value)


class PackagingTests(unittest.TestCase):
    def make_package(self, root):
        c, jar, gate, smoke, provenance = envelope_fixture(root)
        raw = bind_envelope(c, gate, smoke, provenance)
        received = root / 'verified.json'; received.write_bytes(raw)
        contract = root / 'contract.json'; contract.write_bytes(plan.receipt_bytes(c))
        output = root / 'bundle'
        report = prepare.package(output, jar, received, contract)
        return output, report

    def test_sealed_package_stage_and_unused_removal_reuse_110(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            output, report = self.make_package(root)
            archive = output.with_suffix('.tar')
            files, captured = stage.inspect_archive(archive, report['archiveSha256'])
            receiver = stage.configured_receiver(files, captured['contract.json'], captured['source-manifest.json'])
            destination = root / 'remote'; destination.mkdir(mode=0o700)
            with archive.open('rb') as source:
                staged = receiver.stage(destination, 'offline-a1', report['archiveSha256'], report['sourceManifestSha256'], archive.stat().st_size, files, source)
            self.assertFalse(staged['servicesStarted'])
            result = receiver.unstage_unused(destination, 'offline-a1', report['archiveSha256'], report['sourceManifestSha256'], files, staged)
            self.assertTrue(result['unusedPackageRemoved'])
            self.assertFalse((destination / 'offline-a1').exists())
            self.assertIn('wa120-live', stage.receiver_program(files, captured))

    def test_extracted_package_imports_and_mounts_its_own_sources_without_checkout(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            output, report = self.make_package(root)
            archive = output.with_suffix('.tar')
            files, captured = stage.inspect_archive(archive, report['archiveSha256'])
            receiver = stage.configured_receiver(files, captured['contract.json'], captured['source-manifest.json'])
            destination = root / 'remote'; destination.mkdir(mode=0o700)
            with archive.open('rb') as source:
                receiver.stage(destination, 'offline-import', report['archiveSha256'], report['sourceManifestSha256'],
                               archive.stat().st_size, files, source)
            extracted = destination / 'offline-import'
            # A fresh isolated interpreter cannot satisfy imports from this checkout
            # or cached modules. Import/inspection must not start any live operation.
            script = '''import json,sys
from pathlib import Path
sys.path.insert(0,str(Path.cwd()))
import control,plan,stage
base=Path.cwd()
assert plan.ROOT == base/'inputs/source'
contract,files=control.verify_package(base)
captured={name:(base/name).read_bytes() for name in ('contract.json','source-manifest.json','SHA256SUMS.json')}
assert 'wa120-live' in stage.receiver_program(files,captured)
value=plan.build_plan('offline-import','cc33af32-89c7-45bd-bab8-8d7fb42f94d7',1000,1000)
for service,entry in (('worker','worker/main.mjs'),('ingress','lab/live-ingress/main.mjs')):
    mount=next(item for item in value['containers'][service]['mounts'] if item['target']=='/opt/whatsapp')
    source=base/Path(mount['source']).relative_to(value['root'])
    assert source.is_dir() and (source/entry).is_file()
print(json.dumps({'status':'EXTRACTED_IMPORT_AND_MOUNTS_PASS','runtimeStarted':False}))
'''
            result = subprocess.run([sys.executable, '-I', '-B', '-c', script], cwd=extracted,
                                    env={'PATH': os.defpath, 'HOME': str(root / 'unused-home')},
                                    capture_output=True, text=True, timeout=10)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(json.loads(result.stdout),
                             {'status': 'EXTRACTED_IMPORT_AND_MOUNTS_PASS', 'runtimeStarted': False})
            self.assertFalse((extracted / 'private').exists())
            self.assertFalse(list(extracted.rglob('__pycache__')))

    def test_archive_sha_mismatch_is_rejected_before_reading_members(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            output, report = self.make_package(root)
            archive = output.with_suffix('.tar')
            self.assertNotEqual(report['archiveSha256'], '0' * 64)
            with self.assertRaisesRegex(plan.Blocked, '^ARCHIVE_DIGEST_CHANGED$'):
                stage.inspect_archive(archive, '0' * 64)
            link = root / 'linked.tar'; link.symlink_to(archive)
            with self.assertRaisesRegex(plan.Blocked, '^ARCHIVE_DIGEST_CHANGED$'):
                stage.inspect_archive(link, report['archiveSha256'])

    def test_corrupted_transfer_rolls_back_owned_files_only(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            output, report = self.make_package(root)
            archive = output.with_suffix('.tar')
            files, captured = stage.inspect_archive(archive, report['archiveSha256'])
            receiver = stage.configured_receiver(files, captured['contract.json'], captured['source-manifest.json'])
            remote = root / 'remote'; remote.mkdir(mode=0o700)
            (remote / 'foreign').write_text('preserve')
            raw = bytearray(archive.read_bytes()); raw[-1] ^= 1
            with self.assertRaises(ValueError):
                receiver.stage(remote, 'offline-a1', report['archiveSha256'], report['sourceManifestSha256'], len(raw), files, io.BytesIO(raw))
            self.assertFalse((remote / 'offline-a1').exists())
            self.assertEqual((remote / 'foreign').read_text(), 'preserve')

    def test_links_traversal_and_duplicate_tar_members_fail_before_staging(self):
        for name, kind in (('../escape', 'file'), ('inside/../escape', 'file'), ('/absolute', 'file'),
                           ('./noncanonical', 'file'), ('same', 'link'), ('duplicate', 'duplicate')):
            with tempfile.TemporaryDirectory() as directory:
                archive = Path(directory) / 'bad.tar'
                with tarfile.open(archive, 'w') as target:
                    item = tarfile.TarInfo(name); item.size = 1
                    if kind == 'link': item.type = tarfile.SYMTYPE; item.linkname = '/foreign'
                    target.addfile(item, io.BytesIO(b'x'))
                    if kind == 'duplicate': target.addfile(item, io.BytesIO(b'x'))
                with self.subTest(kind=kind), self.assertRaises(plan.Blocked):
                    stage.inspect_archive(archive, plan.hash_file(archive))


class CleanupTests(unittest.TestCase):
    def runtime(self):
        runtime = control.Runtime(plan.HERE, 'offline-test', 'cc33af32-89c7-45bd-bab8-8d7fb42f94d7')
        registry, snapshot = plan_test.snapshot_for(runtime.plan)
        runtime.state = {'attempt': runtime.attempt, 'ownerId': runtime.owner, 'registry': registry,
                         'pending': None, 'status': 'READY_WITHOUT_QR_OR_DISPATCH'}
        runtime.save = lambda: None
        runtime.snapshot = lambda guarded=True: snapshot
        return runtime

    def test_unknown_keeps_all_volumes_and_private_data(self):
        runtime = self.runtime(); commands = []
        runtime.command = lambda argv, **_: commands.append(argv) or ''
        runtime.sql = lambda *_args, **_kwargs: '1'
        runtime.stop()
        self.assertEqual(runtime.state['status'], 'STOPPED_DATA_HELD_FOR_RECONCILIATION')
        self.assertFalse(any(argv[:2] == ['volume', 'rm'] for argv in commands))
        self.assertEqual(len(runtime.state['registry']['volumes']), 3)
        self.assertFalse(runtime.state['registry']['containers'])

    def test_second_stop_never_unlocks_previously_held_data(self):
        runtime = self.runtime()
        _, original = plan_test.snapshot_for(runtime.plan)
        runtime.state.update(status='STOPPED_DATA_HELD_FOR_RECONCILIATION', holdData=True, unresolved=1)
        runtime.state['registry']['containers'] = {}; runtime.state['registry']['networks'] = {}
        snapshot = {'containers': [], 'networks': [], 'volumes': original['volumes'],
                    'volumeConsumers': {key: [] for key in original['volumeConsumers']}}
        runtime.snapshot = lambda guarded=True: snapshot
        commands = []
        runtime.command = lambda argv, **_: commands.append(argv) or ''
        runtime.stop()
        self.assertEqual(runtime.state['status'], 'STOPPED_DATA_HELD_FOR_RECONCILIATION')
        self.assertFalse(any(argv[:2] == ['volume', 'rm'] for argv in commands))

    def test_private_removal_preserves_foreign_objects(self):
        runtime = self.runtime()
        with tempfile.TemporaryDirectory() as folder:
            runtime.directory = Path(folder).resolve() / 'private'; runtime.directory.mkdir(mode=0o700)
            identity = private.write_new(runtime.directory, 'owned', b'unit-private-sentinel')
            runtime.state_identity = private.write_new(runtime.directory, 'state.json', b'{}')
            runtime.state = {'status': 'STOPPED_CLEAN', 'pending': None, 'files': {'owned': identity}, 'directories': {}}
            runtime.load = lambda: None
            runtime.snapshot = lambda guarded=True: None
            foreign = runtime.directory / 'foreign'; foreign.write_text('preserve')
            with self.assertRaisesRegex(plan.Blocked, 'UNRECOGNIZED_CONTENTS'):
                runtime.remove_private()
            self.assertTrue((runtime.directory / 'owned').exists())
            self.assertEqual(foreign.read_text(), 'preserve')

    def test_clean_stop_uses_registered_ids_and_removes_only_owned_volumes(self):
        runtime = self.runtime(); commands = []
        runtime.command = lambda argv, **_: commands.append(argv) or ''
        runtime.sql = lambda *_args, **_kwargs: '0'
        runtime.stop()
        self.assertEqual(runtime.state['status'], 'STOPPED_CLEAN')
        self.assertTrue(all(not rows for rows in runtime.state['registry'].values()))
        self.assertEqual(sum(argv[:2] == ['volume', 'rm'] for argv in commands), 3)
        self.assertFalse(any('prune' in argv for argv in commands))

    def test_startup_has_no_pairing_connection_manufacture_or_provider_post(self):
        source = (plan.HERE / 'control.py').read_text()
        self.assertNotIn('/instance/connect', source)
        self.assertNotIn('/instance/create', source)
        self.assertNotIn('/message/send', source)
        self.assertNotIn("'seed-test-data'", source)
        self.assertNotIn("'driver-bootstrap'", source)
        self.assertNotIn('worker_once', inspect.getsource(control.Runtime.run))
        self.assertEqual(control.GUARDS.OwnedMemoryGuard.__module__, 'wa120_original_guards110')


class OperatorExecTests(unittest.TestCase):
    def test_derived_idle_duration_is_restored_exactly_for_snapshot_and_pending_cleanup(self):
        for pending in (False, True):
            with self.subTest(pending=pending), tempfile.TemporaryDirectory() as folder:
                runtime = control.Runtime(plan.HERE, 'offline-load', 'cc33af32-89c7-45bd-bab8-8d7fb42f94d7')
                runtime.directory = Path(folder).resolve()
                registry, snapshot = plan_test.snapshot_for(runtime.plan)
                slot = registry['containers']['operator']
                value = {'attempt': runtime.attempt, 'ownerId': runtime.owner,
                    'packageFiles': {'contract.json': plan.hash_file(plan.HERE / 'contract.json')},
                    'expiresAtEpoch': control.time.time() + 300, 'operatorKeepaliveSeconds': 123,
                    'registry': {'containers': {} if pending else {'operator': slot}, 'networks': {}, 'volumes': {}},
                    'pending': {'kind': 'containers', 'key': 'operator', 'id': slot['id']} if pending else None}
                private.write_new(runtime.directory, 'state.json', plan.receipt_bytes(value))
                runtime.load()
                self.assertEqual(runtime.plan['containers']['operator']['command'], ['/bin/sleep', '123'])
                row = next(row for row in snapshot['containers'] if row['Config']['Labels']['wa120.service'] == 'operator')
                row['Config']['Cmd'] = ['/bin/sleep', '123']
                bindings = {'containers': {'operator': slot}, 'networks': {}, 'volumes': {}}
                observed = {'containers': [row], 'networks': [], 'volumes': [], 'volumeConsumers': {}}
                plan.teardown_plan(runtime.plan, bindings, observed)
                row['Config']['Cmd'][-1] = '124'
                with self.assertRaisesRegex(plan.Blocked, '^CONTAINER_POLICY_CHANGED$'):
                    plan.teardown_plan(runtime.plan, bindings, observed)

    def test_idle_duration_cannot_exceed_the_window_bound(self):
        runtime = control.Runtime(plan.HERE, 'offline-duration', 'cc33af32-89c7-45bd-bab8-8d7fb42f94d7')
        for invalid in (None, 0, -1, 1801, True, '123'):
            with self.subTest(invalid=invalid), self.assertRaisesRegex(plan.Blocked, '^OPERATOR_KEEPALIVE_INVALID$'):
                runtime.operator_keepalive(invalid)

    @contextmanager
    def operator(self, before_sample=None, observed=None, result_status='COMMITTED',
                 autostart=True, dynamic_result=False, exec_fails=False):
        """Real operate/guard110 on local files; Docker exec is the sole fake seam."""
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder).resolve()
            runtime = control.Runtime(plan.HERE, 'offline-exec', 'cc33af32-89c7-45bd-bab8-8d7fb42f94d7')
            runtime.directory = root / 'private'; runtime.directory.mkdir(mode=0o700)
            operator = runtime.directory / 'operator'; operator.mkdir(mode=0o700)
            registry, snapshot = plan_test.snapshot_for(runtime.plan)
            running = next(row for row in snapshot['containers'] if row['Config']['Labels']['wa120.service'] == 'operator')
            running.update(RestartCount=0, State={'Running': True, 'Pid': 123456, 'StartedAt': '2026-11-01T12:00:00.000000000Z',
                'ExitCode': 0, 'OOMKilled': False, 'Restarting': False, 'Dead': False, 'Status': 'running'})
            runtime.state = {'batchId': 'ebac6049-c95c-46eb-842b-03f8077f0114', 'credentialIds': [], 'files': {},
                'registry': {'containers': {'operator': registry['containers']['operator']}, 'networks': {}, 'volumes': {}}}
            runtime.save = lambda: None
            runtime.pressure.current_mib = 8192
            def write_result():
                request = receipt.object_json((operator / 'request.json').read_bytes()) if dynamic_result else {
                    'operation': 'create-test-cohort', 'batchId': runtime.state['batchId']}
                result = {'status': result_status, 'operation': request['operation'], 'environment': 'wa070-local',
                    'batchId': request['batchId'], 'resourceId': request['batchId'], 'serverTime': '2026-11-01T12:00:01Z'}
                if request['operation'] == 'provision-service-credential':
                    role = request['principal']; field = control.TECHNICAL[role]
                    credential_id = str(__import__('uuid').uuid4())
                    result.update(resourceId=credential_id, credentialId=credential_id, principal=role,
                                  allowedInstanceId=request['allowedInstanceId'], expiresAt=request['expiresAt'],
                                  credential=plan_test.private_config()['secrets'][field])
                if result_status is not None:
                    private.write_new(operator, 'result.json', plan.receipt_bytes(result))
            if not dynamic_result:
                write_result()
            commands = []
            def command(argv, **kwargs):
                commands.append((argv, kwargs))
                if argv[0] == 'exec':
                    if exec_fails:
                        raise plan.Blocked('DOCKER_COMMAND_FAILED')
                    if dynamic_result:
                        write_result()
                elif argv[0] == 'stop':
                    self.assertNotIn('operator', runtime.memory)
                    self.assertEqual(len(runtime.state['operatorCalls']), 5)
                    running['State'].update(Running=False, Pid=0, Status='exited', ExitCode=137)
                return ''
            runtime.command_override = command
            proc = root / 'proc'; (proc / '123456').mkdir(parents=True)
            (proc / '123456/cgroup').write_text('0::/docker/' + running['Id'] + '\n')
            (proc / '123456/stat').write_text('123456 (sleep) ' + ' '.join(['S'] + ['0'] * 18 + ['1234']) + '\n')
            cgroup = root / 'cgroup'; cgroup.mkdir(); (cgroup / 'cgroup.controllers').write_text('memory\n')
            metrics = cgroup / 'docker' / running['Id']; metrics.mkdir(parents=True)
            for name, raw in {'memory.max': str(running['HostConfig']['Memory']), 'memory.current': '100',
                'memory.peak': '200', 'memory.events': 'low 0\nhigh 0\nmax 0\noom 0\noom_kill 0\noom_group_kill 0\n',
                'memory.pressure': 'some avg10=0 avg60=0 avg300=0 total=0\nfull avg10=0 avg60=0 avg300=0 total=0\n'}.items():
                (metrics / name).write_text(raw)
            original_guard = control.GUARDS.OwnedMemoryGuard
            guards = []
            def bind(row, limit, available):
                guard = original_guard(row, limit, available, proc_root=proc, cgroup_root=cgroup)
                sample = guard.sample
                if before_sample:
                    def sampling(headroom):
                        before_sample(proc / '123456', metrics)
                        return sample(headroom)
                    guard.sample = sampling
                guards.append(guard)
                return guard
            first = True
            def inspect_row(*_args, **_kwargs):
                nonlocal first
                row = copy.deepcopy(running)
                if not first and observed:
                    observed(row)
                first = False
                return row
            with patch.object(control.GUARDS, 'OwnedMemoryGuard', side_effect=bind), \
                 patch.object(runtime, 'inspect', side_effect=inspect_row) as inspected:
                if autostart:
                    runtime.start_container('operator')
                yield runtime, guards, inspected, operator, commands

    def test_completed_java_exec_keeps_pid1_and_checks_final_metrics(self):
        with self.operator() as (runtime, guards, inspected, folder, commands):
            result = runtime.operate(plan_test.private_config(), 'create-test-cohort', {'synthetic': True})
            self.assertEqual(result['status'], 'COMMITTED')
            execs = [(argv, options) for argv, options in commands if argv[0] == 'exec']
            self.assertEqual(len(execs), 1)
            self.assertEqual(execs[0][0][2:], runtime.plan['containers']['operator']['cliCommand'])
            self.assertEqual(execs[0][1]['timeout'], 60)
            self.assertEqual(guards[0].samples, 3)  # initial binding and two post-exec samples
            self.assertEqual(inspected.call_count, 4)
            self.assertFalse((folder / 'request.json').exists())
            self.assertIs(runtime.memory['operator'], guards[0])
            self.assertEqual(len(runtime.state['operatorCalls']), 1)

    def test_exec_nonzero_cannot_be_accepted_from_committed_file(self):
        with self.operator(exec_fails=True) as (runtime, _guards, _inspected, folder, _commands):
            with self.assertRaisesRegex(plan.Blocked, '^DOCKER_COMMAND_FAILED$'):
                runtime.operate(plan_test.private_config(), 'create-test-cohort', {'synthetic': True})
            self.assertNotIn('operatorCalls', runtime.state)
            self.assertTrue((folder / 'result.json').exists())

    def test_java_exec_does_not_accept_prepared_or_missing_result(self):
        for status in ('PREPARED', None):
            with self.subTest(status=status), self.operator(result_status=status) as (runtime, _guards, _inspected, folder, _commands):
                with self.assertRaises(Exception):
                    runtime.operate(plan_test.private_config(), 'create-test-cohort', {'synthetic': True})
                self.assertEqual(runtime.state['credentialIds'], [])
                self.assertNotIn('operatorCalls', runtime.state)
                self.assertTrue((folder / 'request.json').exists())

    def test_persistent_operator_requires_same_owned_live_generation(self):
        for change in ('exited', 'id', 'created', 'started', 'restart', 'oom', 'dead', 'pid', 'owner'):
            def changed(row):
                if change == 'exited': row['State'].update(Running=False, Pid=0, Status='exited')
                elif change == 'id': row['Id'] = 'f' * 64
                elif change == 'created': row['Created'] = '2026-11-01T12:00:02Z'
                elif change == 'started': row['State']['StartedAt'] = '2026-11-01T12:00:02Z'
                elif change == 'restart': row['RestartCount'] = 1
                elif change == 'oom': row['State']['OOMKilled'] = True
                elif change == 'dead': row['State']['Dead'] = True
                elif change == 'pid': row['State']['Pid'] = 456789
                else: row['Config']['Labels']['wa120.owner'] = 'foreign-owner'
            with self.subTest(change=change), self.operator(observed=changed) as (runtime, _guards, _inspected, _folder, commands):
                with self.assertRaises(plan.Blocked):
                    runtime.operate(plan_test.private_config(), 'create-test-cohort', {'synthetic': True})
                self.assertFalse(any(argv[0] == 'exec' for argv, _ in commands))
                self.assertEqual(runtime.state['credentialIds'], [])

    def test_final_memory_pressure_and_cgroup_failures_are_never_recovered(self):
        for failure, code in (('events', 'OWNED_CGROUP_MEMORY_EVENT'), ('pressure', 'VPS_CRITICAL_MEMORY_PRESSURE'),
                              ('limit', 'OWNED_CGROUP_LIMIT_CHANGED'), ('pid', 'OWNED_CGROUP_PROCESS_CHANGED'),
                              ('measurement', 'OWNED_CGROUP_MEASUREMENT_UNAVAILABLE')):
            def damaged(proc, metrics):
                if failure == 'events':
                    path = metrics / 'memory.events'; path.write_text(path.read_text().replace('max 0', 'max 1'))
                elif failure == 'pressure':
                    (metrics / 'memory.pressure').write_text('some avg10=0 avg60=0 avg300=0 total=0\nfull avg10=6 avg60=0 avg300=0 total=1\n')
                elif failure == 'limit': (metrics / 'memory.max').write_text('1')
                elif failure == 'pid': (proc / 'stat').write_text('123456 (sleep) ' + ' '.join(['S'] + ['0'] * 18 + ['9999']) + '\n')
                else: (metrics / 'memory.current').unlink()
            with self.subTest(failure=failure), self.operator(before_sample=damaged) as (runtime, _guards, _inspected, _folder, _commands):
                with self.assertRaisesRegex(plan.Blocked, '^' + code + '$'):
                    runtime.operate(plan_test.private_config(), 'create-test-cohort', {'synthetic': True})
                self.assertNotIn('operatorCalls', runtime.state)

    def test_process_loss_cannot_hide_final_metrics_fault_or_missing_evidence(self):
        for failure in ('events', 'pressure', 'measurement', 'cgroup-identity'):
            def finished_and_faulted(proc, metrics):
                (proc / 'cgroup').unlink(missing_ok=True)
                if failure == 'events':
                    path = metrics / 'memory.events'; path.write_text(path.read_text().replace('max 0', 'max 1'))
                elif failure == 'pressure':
                    (metrics / 'memory.pressure').write_text('some avg10=0 avg60=0 avg300=0 total=0\nfull avg10=6 avg60=0 avg300=0 total=1\n')
                elif failure == 'measurement': (metrics / 'memory.current').unlink()
                else:
                    metrics.rename(metrics.with_name(metrics.name + '-old')); metrics.mkdir()
            with self.subTest(failure=failure), self.operator(before_sample=finished_and_faulted) as (runtime, _guards, _inspected, folder, _commands):
                with self.assertRaisesRegex(plan.Blocked, '^OWNED_CGROUP_PROCESS_UNAVAILABLE$'):
                    runtime.operate(plan_test.private_config(), 'create-test-cohort', {'synthetic': True})
                self.assertNotIn('operatorCalls', runtime.state)
                self.assertTrue((folder / 'request.json').exists())

    def test_api_and_worker_process_loss_still_fails_in_general_guard_check(self):
        for service in ('api', 'worker'):
            with self.subTest(service=service), self.operator() as (runtime, guards, inspected, _folder, _commands):
                guard = guards[0]; (guard.proc / 'cgroup').unlink()
                runtime.memory = {service: guard}
                with patch.object(runtime.pressure, 'sample', return_value=True):
                    with self.assertRaisesRegex(plan.Blocked, '^OWNED_CGROUP_PROCESS_UNAVAILABLE$'):
                        runtime.check()
                self.assertEqual(inspected.call_count, 1)

    def test_five_execs_use_one_guard_and_planned_stop_precedes_removal(self):
        with self.operator(autostart=False, dynamic_result=True) as (runtime, guards, _inspected, _folder, commands):
            runtime.create_container = lambda *_args: runtime.state['registry']['containers']['operator']['id']
            runtime.snapshot = lambda *_args, **_kwargs: None
            runtime.sql = lambda *_args, **_kwargs: '0'
            runtime.write_private = lambda *_args: None
            runtime.provision(plan_test.private_config())
            self.assertEqual(len(guards), 1)
            self.assertEqual(len(runtime.state['operatorCalls']), 5)
            self.assertEqual({call.get('principal') for call in runtime.state['operatorCalls'][1:]}, set(control.TECHNICAL))
            self.assertEqual(len({call['containerId'] for call in runtime.state['operatorCalls']}), 1)
            self.assertEqual(len([argv for argv, _ in commands if argv[0] == 'start']), 1)
            self.assertEqual(len([argv for argv, _ in commands if argv[0] == 'exec']), 5)
            actions = [argv[0] for argv, _ in commands]
            self.assertLess(actions.index('stop'), actions.index('rm'))
            self.assertGreaterEqual(guards[0].samples, 12)
            self.assertNotIn('operator', runtime.memory)
            self.assertNotIn('operator', runtime.state['registry']['containers'])


if __name__ == '__main__':
    unittest.main()
