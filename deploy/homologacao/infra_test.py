"""Pure synthetic evidence and fake Docker; no network, JVM, build or container."""
import copy
import datetime as dt
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import signal
from unittest.mock import patch

import prepare as P
import backup_restore as B

SHA, TREE = 'a' * 40, 'b' * 40
JAVA = 'eclipse-temurin@sha256:' + '1' * 64
PG = 'postgres@sha256:' + '2' * 64
API = P.API_REPOSITORY + '@sha256:' + '3' * 64
CID = '4' * 64
REAL_SOURCE_CHECK = P.source_checkout


def fixtures():
    """Invented test data, never a receipt of a real CI run."""
    source = {'repository': P.M.REPOSITORY, 'sha': SHA, 'tree': TREE, 'files': {
        'src/backend/scripts/qa_integration_profile.sh': {'gitBlob': 'c' * 40, 'sha256': 'd' * 64, 'bytes': 1}}}
    ci = {'repository': P.M.REPOSITORY, 'sha': SHA, 'runId': 101, 'runAttempt': 1,
          'workflowRef': P.M.REPOSITORY + '/' + P.M.WORKFLOW + '@refs/heads/unit-fixture',
          'event': 'workflow_dispatch', 'job': 'candidate'}
    totals = {'tests': 2, 'skipped': 0, 'failures': 0, 'errors': 0}
    row = {'className': 'fixture.IntegrationTest', 'tests': 2, 'sha256': 'e' * 64}
    manifest = {'status': 'passed', 'suite': 'integration', 'commandProfile': 'maven-integration-all-tests',
        'retentionClass': 'ci', 'eligibleForRelease': False,
        'source': {'gitSha': SHA, 'gitTree': TREE, 'runStartGitSha': SHA, 'runStartGitTree': TREE,
                   'clean': True, 'dirtyPathCount': 0, 'cleanRequired': True},
        'environment': {'scope': 'all-tests', 'userHomeIsolated': True, 'mavenRcSkipped': True,
                        'dockerEndpointTransport': 'unix'},
        'totals': totals, 'reports': [row],
        'allReports': {'requiredGreen': True, 'reportCount': 1, 'totals': totals, 'reports': [{**row, **totals}]}}
    gate = {'schemaVersion': 1, 'profile': 'MONOREPO_CANONICAL_GATE_V1', 'status': 'PASS', 'source': source,
            'ci': ci, 'manifestSha256': 'f' * 64, 'manifest': manifest, 'requiredClasses': {row['className']: 2}}
    jar = b'UNIT FIXTURE ONLY - NOT A REAL JAR'
    scenarios = [{'scenario': item, 'syntheticFixtures': True, 'realWhatsApp': False, 'physicalDevices': False}
                 for item in P.M.SCENARIOS]
    scenarios[-2].update(events=9, attempts=7, providerSubmissions=3, connectionReads=21, pacing={'policy': 'API_ONLY'})
    scenarios[-1].update(pinQueries=3, t43=True)
    smoke = {'schemaVersion': 1, 'profile': 'VANRIDES_ACADEMIC_INTEGRATION_V1', 'status': 'PASS',
             'sourceSha': SHA, 'sourceTree': TREE, 'jar': {'sha256': P.sha(jar), 'bytes': len(jar)},
             'checks': [{'scenario': scenario, 'check': check, 'status': 'PASS'} for scenario in P.M.SCENARIOS
                        for check in sorted(P.M.REQUIRED_CHECKS.get(scenario, {'fixture_check'}))],
             'scenarios': scenarios, 'cleanup': {'status': 'OWNED_RESOURCES_REMOVED', 'errors': []},
             'boundaries': {'realWhatsApp': False, 'physicalDevices': False, 'provider': 'FAKE_LOOPBACK', 'model': 'DISABLED'},
             'runtime': {'node': 'v22.23.2', 'platform': 'linux', 'arch': 'x64', 'api': 'LOCAL_JAR', 'database': 'OWNED_POSTGRES_CONTAINER'}}
    candidate = {'schemaVersion': 1, 'profile': P.M.PROFILE, 'status': 'CANDIDATE_FOR_BOUNDED_LAB',
                 'source': source, 'ci': ci, 'jar': smoke['jar'], 'gateSha256': P.sha(P.encode(gate)),
                 'smokeSha256': P.sha(P.encode(smoke)), 'releaseEligible': False}
    return {'backend.jar': jar, 'gate.json': P.encode(gate), 'smoke.json': P.encode(smoke), 'candidate.json': P.encode(candidate)}


def expiry():
    return (dt.datetime.now(dt.timezone.utc) + dt.timedelta(days=1)).strftime('%Y-%m-%dT%H:%M:%SZ')


class InfraTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        self.freeze = patch.object(P, 'source_checkout')
        self.mock_source = self.freeze.start()
        self.addCleanup(self.freeze.stop)
        self.bundle = self.root / 'bundle'; self.bundle.mkdir()
        self.files = fixtures()
        for name, raw in self.files.items():
            (self.bundle / name).write_bytes(raw)

    def prepare(self):
        target = self.root / 'artifact'
        P.artifact(self.bundle, P.sha(self.files['candidate.json']), SHA, TREE, JAVA, target)
        return target, P.sha(P.read(target / 'artifact.json'))

    def make_plan(self):
        directory, digest = self.prepare()
        target = self.root / 'plan'
        P.render(directory, digest, API, PG, expiry(), target)
        return target, P.sha(P.read(target / 'plan.json'))

    def test_artifact_copies_exact_jar_and_validated_receipts_without_release_claim(self):
        output, digest = self.prepare()
        value = P.validated_artifact(output, digest)
        self.assertEqual((output / 'context/backend.jar').read_bytes(), self.files['backend.jar'])
        self.assertFalse(value['releaseEligible'])
        self.assertFalse(value['ciProvenanceExternallyVerified'])
        self.assertEqual({p.name for p in (output / 'context').iterdir()}, {'backend.jar', 'Dockerfile', 'entrypoint.sh'})
        self.assertEqual(output.stat().st_mode & 0o777, 0o700)
        self.assertEqual((output / 'artifact.json').stat().st_mode & 0o777, 0o600)
        self.assertGreaterEqual(self.mock_source.call_count, 3)

    def test_jar_candidate_source_tree_gate_and_release_substitution_are_denied(self):
        mutations = [('backend.jar', lambda raw: raw + b'CHANGED'),
                     ('candidate.json', lambda raw: raw + b' '),
                     ('gate.json', lambda raw: raw.replace(b'"skipped":0', b'"skipped":1')),
                     ('smoke.json', lambda raw: raw.replace(TREE.encode(), ('c' * 40).encode()))]
        for index, (name, change) in enumerate(mutations):
            original = (self.bundle / name).read_bytes()
            (self.bundle / name).write_bytes(change(original))
            with self.subTest(name=name), self.assertRaises(P.Blocked):
                P.artifact(self.bundle, P.sha(self.files['candidate.json']), SHA, TREE, JAVA, self.root / ('bad' + str(index)))
            (self.bundle / name).write_bytes(original)
        for field in ('releaseEligible',):
            value = json.loads(self.files['candidate.json']); value[field] = True
            raw = P.encode(value); (self.bundle / 'candidate.json').write_bytes(raw)
            with self.assertRaises(P.Blocked):
                P.artifact(self.bundle, P.sha(raw), SHA, TREE, JAVA, self.root / 'forged')
        self.assertFalse((self.root / 'forged').exists())

    def test_symlinks_traversal_extra_files_and_overwrite_are_denied(self):
        output, digest = self.prepare()
        (output / 'context/backend.jar').unlink()
        (output / 'context/backend.jar').symlink_to(self.bundle / 'backend.jar')
        with self.assertRaises((P.Blocked, OSError)):
            P.validated_artifact(output, digest)
        with self.assertRaises(P.Blocked):
            P.read(self.root / 'artifact/../bundle/backend.jar')
        (self.bundle / 'private.env').write_text('UNIT_ONLY')
        with self.assertRaises(P.Blocked):
            P.artifact(self.bundle, P.sha(self.files['candidate.json']), SHA, TREE, JAVA, self.root / 'extra')
        with self.assertRaises(P.Blocked):
            P.new_directory(output)

    def test_tags_and_unrelated_images_are_denied(self):
        for value in ('postgres:16-alpine', PG.upper(), 'postgres@sha256:123', 'repo/../postgres@sha256:' + 'a' * 64):
            with self.subTest(value=value), self.assertRaises(P.Blocked):
                P.image(value)
        with self.assertRaises(P.Blocked):
            P.image(PG, P.API_REPOSITORY)

    def test_default_stack_has_no_ingress_or_ports_and_only_owned_resources(self):
        target, digest = self.make_plan()
        plan = B.metadata(target / 'plan.json', digest)
        stack = P.obj(target / 'stack.json')
        self.assertEqual(set(stack['services']), {'api', 'db'})
        self.assertFalse(plan['imageFilesystemVerified'])
        self.assertEqual(stack['services']['api']['networks'], ['private'])
        self.assertEqual(stack['services']['api']['deploy']['labels']['traefik.enable'], 'false')
        self.assertTrue(all('ports' not in svc for svc in stack['services'].values()))
        self.assertNotIn('db_admin_password', [s['source'] for s in stack['services']['api']['secrets']])
        self.assertEqual(stack['services']['db']['environment']['POSTGRES_USER'], 'postgres')
        self.assertEqual(stack['services']['api']['environment']['SPRING_DATASOURCE_USERNAME'], 'vanrides_hml_api')

    def test_opening_requires_image_proof_and_team_requires_narrow_cidrs(self):
        artifact, digest = self.prepare()
        for mode in ('health', 'team'):
            with self.subTest(mode=mode), self.assertRaises(P.Blocked):
                P.render(artifact, digest, API, PG, expiry(), self.root / mode, mode)
        for values in ([], ['0.0.0.0/0'], ['::/0'], ['192.0.2.1/24'], ['192.0.2.0/24'] * 2):
            with self.subTest(values=values), self.assertRaises(P.Blocked):
                P.ingress_labels('team', values)
        labels = P.ingress_labels('team', ['192.0.2.1/32'])
        rule = labels['traefik.http.routers.vanrides-hml-api-team.rule']
        self.assertIn('inbox/claim', rule); self.assertIn('/commands', rule); self.assertIn('/context', rule)
        self.assertIn('/result$', rule); self.assertNotIn('PathPrefix', rule)
        self.assertNotIn('/events', rule); self.assertNotIn('/driver-app', rule)
        self.assertEqual(labels['traefik.http.routers.vanrides-hml-api-health.entrypoints'], 'websecure')
        self.assertEqual(labels['traefik.http.routers.vanrides-hml-api-team.tls.certresolver'], 'letsencryptresolver')
        self.assertFalse(any('ipstrategy' in key.lower() for key in labels))

    def test_stack_policy_rejects_port_route_secret_network_and_env_injection(self):
        target, _ = self.make_plan()
        original = P.obj(target / 'stack.json')
        changes = [lambda s: s['services']['api'].update(ports=['8080:8080']),
                   lambda s: s['services']['api']['environment'].update(VANRIDES_EMAIL_PROVIDER='log'),
                   lambda s: s['services']['api']['deploy']['labels'].update({'traefik.enable': 'true'}),
                   lambda s: s['services']['db']['networks'].append('public'),
                   lambda s: s['secrets']['jwt_secret'].update(name='another-stack-secret'),
                   lambda s: s['volumes']['pgdata'].update(name='vanrides-dev_pgdata')]
        for change in changes:
            changed = copy.deepcopy(original); change(changed)
            with self.subTest(change=change), self.assertRaises(P.Blocked):
                P.validate_stack(changed, 'closed', [], original['services']['api']['environment']['WA_OPERATOR_EXPIRES_AT'],
                                 'v1', SHA[:12], target / 'db-init.sh')

    def image_engine(self, artifact, mutation=None, fail_cleanup=False):
        manifest = P.obj(artifact / 'artifact.json')
        observed = {'id': 'sha256:' + '9' * 64, 'digests': [API], 'os': 'linux', 'arch': 'amd64',
                    'user': '10001:10001', 'entrypoint': ['/app/entrypoint.sh'], 'labels': {
                    'org.opencontainers.image.revision': SHA, 'dev.vanrides.source-tree': TREE,
                    'dev.vanrides.jar-sha256': manifest['jar']['sha256'], 'dev.vanrides.environment': P.ENVIRONMENT}}
        calls = []
        temporary = {}
        def run(args, maximum=1000000):
            calls.append(args)
            if args[:2] == ['image', 'inspect']:
                return P.encode(observed)
            if args[0] == 'ps':
                return (CID + '\n').encode() if temporary and any('label=' in part for part in args) else b''
            if args[0] == 'create':
                temporary.update(name=args[args.index('--name') + 1], image=args[-1],
                    labels=dict(args[i+1].split('=',1) for i,part in enumerate(args) if part == '--label'))
                return CID.encode()
            if args[0] == 'inspect':
                return P.encode({'id': CID, 'name': '/' + temporary['name'], 'image': temporary['image'], 'labels': temporary['labels']})
            if args[0] == 'cp':
                target = Path(args[-1])
                content = P.read(artifact / ('context/backend.jar' if target.name == 'app.jar' else 'context/entrypoint.sh'))
                if mutation == 'jar' and target.name == 'app.jar':
                    content += b'REPLACED'
                target.write_bytes(content)
                return b''
            if args[0] == 'rm':
                if fail_cleanup:
                    raise P.Blocked('UNIT_CLEANUP_FAILURE')
                temporary.clear()
                return b''
            raise AssertionError(args)
        return run, calls, observed

    def test_image_reads_actual_jar_and_never_starts_container(self):
        artifact, digest = self.prepare()
        run, calls, _ = self.image_engine(artifact)
        result = P.verify_image(artifact, digest, API, self.root / 'image', run)
        self.assertTrue(result['filesystemVerified'])
        self.assertFalse(result['releaseEligible'])
        self.assertTrue(any(args[0] == 'cp' for args in calls))
        self.assertFalse(any(args[0] in ('run', 'start', 'build', 'push', 'pull', 'login') for args in calls))
        self.assertIn(['rm', '-f', '-v', CID], calls)
        receipt = self.root / 'image/image.json'
        stack = self.root / 'open'
        P.render(artifact, digest, API, PG, expiry(), stack, 'team', ['192.0.2.1/32'], receipt, P.sha(P.read(receipt)))
        self.assertEqual(P.obj(stack / 'plan.json')['ingress'], 'team')

    def test_replaced_image_jar_wrong_platform_labels_user_and_cleanup_deny_proof(self):
        artifact, digest = self.prepare()
        for index, scenario in enumerate(('jar', 'arch', 'user', 'labels', 'cleanup')):
            run, calls, observed = self.image_engine(artifact, scenario, scenario == 'cleanup')
            if scenario == 'arch': observed['arch'] = 'arm64'
            if scenario == 'user': observed['user'] = 'root'
            if scenario == 'labels': observed['labels']['dev.vanrides.source-tree'] = 'd' * 40
            output = self.root / ('image' + str(index))
            with self.subTest(scenario=scenario), self.assertRaises(P.Blocked):
                P.verify_image(artifact, digest, API, output, run)
            self.assertFalse(output.exists())

    def test_template_bootstrap_uses_nonsuperuser_without_secret_argv(self):
        sql = (P.HERE / 'db-init.sh').read_text()
        self.assertIn('NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION', sql)
        self.assertIn("pg_read_file('/run/secrets/db_password')", sql)
        self.assertNotIn('app_password=', sql)
        self.assertIn('REVOKE CONNECT ON DATABASE postgres FROM PUBLIC', sql)
        dockerfile = (P.HERE / 'Dockerfile').read_text()
        self.assertNotIn('maven', dockerfile.lower())
        self.assertNotIn('mvnw', dockerfile)
        self.assertIn('USER 10001:10001', dockerfile)

    def test_real_source_check_rejects_invented_revision(self):
        with patch.object(P, 'source_checkout', wraps=REAL_SOURCE_CHECK):
            with self.assertRaises(P.Blocked):
                P.source_checkout('0' * 40, '1' * 40)


class BackupTests(unittest.TestCase):
    setUp = InfraTests.setUp
    prepare = InfraTests.prepare
    make_plan = InfraTests.make_plan
    def db_engine(self, drift=False, cleanup_error=False):
        calls = []
        fingerprint = (b'scope|1|0\ncredentials|0\npublic.flyway_schema_history|1|' + b'a' * 32
                       + b'\nwa.audit_event|1|' + b'b' * 32 + b'\nwa.command_receipt|1|' + b'c' * 32
                       + b'\nwa.profile|1|' + b'd' * 32 + b'\n')
        fingerprints = 0
        temporary = {}
        def run(args, data=None, maximum=1000000):
            nonlocal fingerprints
            calls.append(args)
            if args[:2] == ['service', 'inspect']: return b'0\n'
            if args[0] == 'ps':
                return (CID + '\n').encode() if temporary and any('label=' in part for part in args) else b''
            if args[0] == 'inspect' and temporary:
                return P.encode({'id': CID, 'name': '/' + temporary['name'], 'image': temporary['image'], 'labels': temporary['labels']})
            if args[0] == 'inspect':
                return P.encode({'running': True, 'image': PG, 'labels': {
                    'com.docker.stack.namespace': P.STACK, 'com.docker.swarm.service.name': B.DB_SERVICE,
                    'dev.vanrides.environment': P.ENVIRONMENT}})
            if args[0] == 'create':
                temporary.update(name=args[args.index('--name') + 1], image=args[-1],
                    labels=dict(args[i+1].split('=',1) for i,part in enumerate(args) if part == '--label'))
                return CID.encode()
            if args[0] == 'rm':
                if cleanup_error: raise P.Blocked('UNIT_CLEANUP_FAILED')
                temporary.clear()
                return b''
            if 'psql' in args:
                fingerprints += 1
                return fingerprint.replace(b'wa.profile|1|', b'wa.profile|2|') if drift and fingerprints > 1 else fingerprint
            if 'pg_dump' in args: return b'PGDMP_UNIT_ONLY'
            if 'pg_restore' in args or 'pg_isready' in args or args[0] == 'start': return b''
            raise AssertionError(args)
        return run, calls

    def make_backup(self, run):
        target, digest = self.make_plan()
        output = self.root / 'backup'
        B.backup(target / 'plan.json', digest, CID, output, run)
        return output, P.sha(P.read(output / 'backup.json'))

    def test_backup_hash_restore_content_and_cleanup_are_required_before_pass(self):
        run, calls = self.db_engine()
        directory, digest = self.make_backup(run)
        result = B.restore(directory, digest, self.root / 'restored', run, lambda _: None)
        self.assertFalse(result['apiFunctionalTested'])
        self.assertFalse(result['releaseEligible'])
        create = next(args for args in calls if args[0] == 'create')
        self.assertEqual(create[create.index('--network') + 1], 'none')
        self.assertNotIn('--publish', create)
        self.assertNotIn('--mount', create)
        self.assertIn(['rm', '-f', '-v', CID], calls)

    def test_running_api_foreign_container_and_drift_block_backup(self):
        target, digest = self.make_plan()
        for case in ('running', 'foreign', 'drift'):
            real, _ = self.db_engine(drift=case == 'drift')
            def run(args, data=None, maximum=1000000):
                if args[:2] == ['service', 'inspect'] and case == 'running': return b'1'
                if args[0] == 'inspect' and case == 'foreign':
                    return P.encode({'running': True, 'image': PG, 'labels': {'com.docker.stack.namespace': 'vanrides-dev'}})
                return real(args, data, maximum)
            with self.subTest(case=case), self.assertRaises(P.Blocked):
                B.backup(target / 'plan.json', digest, CID, self.root / case, run)
            self.assertFalse((self.root / case).exists())

    def test_changed_dump_receipt_world_readable_and_cleanup_failure_never_pass(self):
        run, _ = self.db_engine()
        directory, digest = self.make_backup(run)
        dump = directory / 'database.dump'
        original = dump.read_bytes(); dump.write_bytes(original + b'SWAPPED')
        with self.assertRaises(P.Blocked):
            B.restore(directory, digest, self.root / 'bad1', run)
        dump.write_bytes(original); dump.chmod(0o644)
        with self.assertRaises(P.Blocked):
            B.restore(directory, digest, self.root / 'bad2', run)
        dump.chmod(0o600)
        fail, calls = self.db_engine(cleanup_error=True)
        with self.assertRaises(P.Blocked):
            B.restore(directory, digest, self.root / 'bad3', fail, lambda _: None)
        self.assertFalse((self.root / 'bad3/restore.json').exists())


if __name__ == '__main__':
    unittest.main()
