"""Ensaio do contrato/guards com subprocess mockado; nenhum Docker ou SSH real."""
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
from zipfile import ZipFile

import rehearse_migration as M

API_IMAGE = M.API_REPOSITORY + '@sha256:' + '1' * 64
PG_IMAGE = 'postgres@sha256:' + '2' * 64
LIVE_API, LIVE_DB = 'a' * 64, 'b' * 64
MIGRATION = M.MIGRATIONS + 'V44__retention_journal.sql'


def jar(extra=False, change=None):
    files = {M.MIGRATIONS + 'V43__previous.sql': b'CREATE TABLE previous(id integer);',
             'BOOT-INF/lib/flyway-core.jar': b'unchanged-library',
             'BOOT-INF/classes/application.properties': b'spring.flyway.enabled=true\n',
             'BOOT-INF/classes/application-homolog.properties': b'spring.jpa.hibernate.ddl-auto=validate\n'}
    if extra: files[MIGRATION] = b'CREATE TABLE retention_journal(id integer);'
    if change: files[change] = b'unreviewed-change'
    output = io.BytesIO()
    with ZipFile(output, 'w') as archive:
        for name, raw in files.items(): archive.writestr(name, raw)
    return output.getvalue()


class DockerProcess:
    def __init__(self, previous, failure=None):
        self.previous, self.failure, self.calls, self.owned, self.schema = previous, failure, [], {}, 43
        self.fingerprints = 0
        self.service_reads = 0

    def __call__(self, command, **kwargs):
        assert tuple(command[:3]) == M.DOCKER
        assert kwargs['env']['DOCKER_CONFIG'].endswith('/docker-config')
        assert callable(kwargs['preexec_fn'])
        self.calls.append(command[3:]); args = command[3:]; operation = args[0]
        stdout = b''
        if operation == 'service':
            service = args[-1]; image = API_IMAGE if service == M.API else PG_IMAGE
            if 'WA_ENVIRONMENT' in args[args.index('--format') + 1]: stdout = b'WA_ENVIRONMENT=' + M.ENVIRONMENT.encode()
            elif 'VANRIDES_JAR_SHA256' in args[args.index('--format') + 1]: stdout = ('VANRIDES_JAR_SHA256=' + M.sha(self.previous)).encode()
            else:
                self.service_reads += 1
                version = 8 if self.failure == 'snapshot-changed' and self.service_reads > 2 else 7
                environment = 'other-environment' if self.failure == 'live-environment' else M.ENVIRONMENT
                stdout = M.encode({'image': image, 'version': version, 'replicas': 1, 'namespace': M.STACK, 'environment': environment})
        elif operation == 'image':
            stdout = M.encode({'org.opencontainers.image.source': M.SOURCE, 'dev.vanrides.source-tree':'7'*40, 'org.opencontainers.image.revision': ('9' if self.failure == 'image-label' else '3') * 40, 'dev.vanrides.jar-sha256': M.sha(self.previous)})
        elif operation == 'ps':
            filters = [args[i+1] for i, value in enumerate(args) if value == '--filter']
            if any('swarm.service.name=' in value for value in filters):
                stdout = ((LIVE_API if filters[0].endswith(M.API) else LIVE_DB) + '\n').encode()
            elif any(value.startswith('name=') for value in filters):
                stdout = (LIVE_DB + '\n').encode() if self.failure == 'collision' else b''
            else: stdout = ''.join(cid + '\n' for cid in self.owned).encode()
        elif operation == 'inspect':
            cid = args[-1]
            if cid in (LIVE_API, LIVE_DB):
                service = M.API if cid == LIVE_API else M.DB
                stdout = M.encode({'image': API_IMAGE if cid == LIVE_API else PG_IMAGE, 'running': True,
                    'labels': {'com.docker.swarm.service.name': service, 'com.docker.stack.namespace': M.STACK, 'dev.vanrides.environment': M.ENVIRONMENT}})
            elif args[args.index('--format')+1] == '{{.State.Running}}': stdout = b'false' if self.failure == 'api-fail' else b'true'
            else: stdout = M.encode(self.owned[cid])
        elif operation == 'create':
            labels = dict(args[i+1].split('=',1) for i, value in enumerate(args) if value == '--label')
            cid = format(len([row for row in self.calls if row[0] == 'create']), '064x')
            image = next(value for value in args if '@sha256:' in value)
            self.owned[cid] = {'id': cid, 'name': '/' + args[args.index('--name')+1], 'image': image, 'labels': labels}
            if self.failure == 'forged': self.owned[cid]['labels']['dev.vanrides.environment'] = 'other-environment'
            if self.failure == 'lost-create': raise subprocess.TimeoutExpired(command, 120)
            stdout = (cid + '\n').encode()
        elif operation == 'cp': Path(args[-1]).write_bytes(self.previous)
        elif operation == 'start':
            if self.owned[args[-1]]['labels']['dev.vanrides.operation'] == 'candidate-on-new': self.schema = 44
        elif operation == 'exec':
            if 'pg_dump' in args: stdout = b'invalid-backup' if self.failure == 'bad-dump' else b'PGDMP-private-real-backup-fixture'
            elif 'psql' in args:
                if kwargs.get('input') == M.FINGERPRINT_SQL.encode():
                    cid = args[args.index('postgres')+1]
                    if cid == LIVE_DB: self.fingerprints += 1
                    changed = self.failure == 'source-changed' and cid == LIVE_DB and self.fingerprints > 1
                    changed |= self.failure == 'restore-changed' and cid != LIVE_DB
                    stdout = ('scope|2|0|1|1\ncredentials|5|0\nunsupported|0\npublic.flyway_schema_history|43|' + '4' * 32
                              + '\nwa.profile|' + ('2' if changed else '1') + '|' + '5' * 32 + '\n').encode()
                else: stdout = (str(43 if LIVE_DB in args else self.schema) + '|0').encode()
            elif 'sha256sum' in args: stdout = (M.sha(self.previous) + '  /app/app.jar').encode()
            elif 'wget' in args: stdout = b'{"status":"UP"}'
        elif operation == 'logs': stdout = b'private-synthetic-log'
        elif operation == 'rm':
            assert args[-1] not in (LIVE_DB, LIVE_API)
            self.owned.pop(args[-1])
        else: raise AssertionError(args)
        kwargs['stdout'].write(stdout)
        return subprocess.CompletedProcess(command, 0)


class RehearsalTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve(); self.old, self.new = jar(), jar(True)
        candidate = self.root / 'incoming.jar'; candidate.write_bytes(self.new)
        self.value = {'schemaVersion': 1, 'environment': M.ENVIRONMENT,
            'previous': {'apiImage': API_IMAGE, 'postgresImage': PG_IMAGE, 'revision': '3' * 40, 'tree':'7'*40, 'jarSha256': M.sha(self.old)},
            'candidate': {'jar': str(candidate), 'jarSha256': M.sha(self.new), 'revision': '6' * 40, 'tree':'8'*40},
            'migration': {'path': MIGRATION, 'sha256': M.sha(b'CREATE TABLE retention_journal(id integer);')}}
        self.path = self.root / 'manifest.json'; self.path.write_bytes(M.encode(self.value)); self.expected = M.sha(self.path.read_bytes())

    def flow(self, failure=None):
        output = self.root / 'result'; output.mkdir(mode=0o700)
        process = DockerProcess(self.old, failure)
        runner = M.Docker(output, execute=process)
        return output, process, lambda: M.rehearse(self.value, self.new, self.expected, output, runner, pause=lambda _: None)

    def test_manifest_hash_candidate_bytes_exact_fields_and_immutable_images(self):
        self.assertEqual(M.manifest(self.path, self.expected)[1], self.new)
        with self.assertRaises(M.Blocked): M.manifest(self.path, '0' * 64)
        for mutate in (lambda v: v.update(command='arbitrary shell'),
                       lambda v: v['previous'].update(apiImage=M.API_REPOSITORY + ':latest'),
                       lambda v: v['candidate'].update(jarSha256='0' * 64)):
            value = json.loads(json.dumps(self.value)); mutate(value); self.path.write_bytes(M.encode(value))
            with self.assertRaises(M.Blocked): M.manifest(self.path, M.sha(self.path.read_bytes()))

    def test_only_one_next_migration_is_allowed_and_libraries_config_and_old_sql_cannot_change(self):
        old = M.inventory(self.old)
        self.assertEqual(M.migration_versions(old, M.inventory(self.new), self.value['migration']), (43,44))
        for changed in (M.MIGRATIONS+'V43__previous.sql','BOOT-INF/lib/flyway-core.jar','BOOT-INF/classes/application.properties'):
            with self.assertRaises(M.Blocked): M.migration_versions(old, M.inventory(jar(True, changed)), self.value['migration'])
        with self.assertRaises(M.Blocked): M.migration_versions(old, M.inventory(self.new), {'path':MIGRATION,'sha256':'0'*64})

    def test_success_restores_content_checks_three_schemas_and_removes_only_owned_containers(self):
        output, process, run = self.flow(); receipt = run()
        self.assertEqual(receipt['status'], 'RESTORE_AND_MIGRATION_RETURN_VERIFIED')
        self.assertEqual([x['schemaVersion'] for x in receipt['scenarios']], [43,44,44])
        self.assertEqual(receipt['backup']['fingerprint']['cohorts'], {'real':1,'synthetic':1}); self.assertEqual(receipt['backup']['fingerprint']['serviceCredentials'],5)
        self.assertEqual(receipt['ownedCleanup'], 'VERIFIED'); self.assertFalse(receipt['releaseEligible']); self.assertFalse(process.owned)
        self.assertEqual((output/'database.dump').stat().st_mode & 0o777, 0o600)
        for command in process.calls:
            self.assertNotIn(command[0], ('ssh','volume','network','stack','build','pull','push'))
            if command[0] == 'service': self.assertEqual(command[1], 'inspect')
            if command[0] == 'create':
                network = command[command.index('--network')+1]
                self.assertTrue(network == 'none' or re_full_container(network))
                self.assertNotIn('--publish',command); self.assertNotIn('--volumes-from',command)
        self.assertNotIn('private-real-backup-fixture',(output/'receipt.json').read_text())

    def test_source_change_and_restore_difference_preserve_private_backup_and_block_success(self):
        for failure in ('source-changed','restore-changed'):
            with self.subTest(failure=failure):
                # Each run has an exclusive result directory, exactly as on the VPS.
                self.root = self.root / failure; self.root.mkdir()
                output, process, run = self.flow(failure)
                with self.assertRaises(M.Blocked): run()
                self.assertTrue((output/'database.dump').read_bytes().startswith(b'PGDMP'))
                receipt=json.loads((output/'receipt.json').read_bytes())
                self.assertEqual(receipt['status'],'BLOCKED'); self.assertFalse(receipt['releaseEligible']); self.assertFalse(process.owned)
                self.assertTrue(all(path.stat().st_mode & 0o777 == 0o600 for path in output.iterdir() if path.is_file()))

    def test_lost_create_is_reconciled_but_forged_owner_is_never_removed(self):
        output, process, run = self.flow('lost-create')
        with self.assertRaises(subprocess.TimeoutExpired): run()
        self.assertFalse(process.owned); self.assertEqual(json.loads((output/'receipt.json').read_bytes())['ownedCleanup'],'VERIFIED')
        self.root = self.root / 'forged'; self.root.mkdir()
        output, process, run = self.flow('forged')
        with self.assertRaises(M.Blocked): run()
        self.assertTrue(process.owned); self.assertFalse(any(x[0]=='rm' for x in process.calls))
        self.assertEqual(json.loads((output/'receipt.json').read_bytes())['ownedCleanup'],'BLOCKED')

    def test_live_identity_image_label_and_snapshot_change_block_the_rehearsal(self):
        for failure in ('live-environment','image-label','snapshot-changed'):
            self.root = self.root / failure; self.root.mkdir()
            output, process, run = self.flow(failure)
            with self.assertRaises(M.Blocked): run()
            self.assertEqual(json.loads((output/'receipt.json').read_bytes())['status'],'BLOCKED')
            self.assertFalse(process.owned)
            self.assertFalse(any(row[0]=='create' and 'dev.vanrides.operation=postgres' in row for row in process.calls))

    def test_bad_dump_and_failed_api_keep_artifacts_without_success(self):
        for failure in ('bad-dump','api-fail'):
            self.root = self.root / failure; self.root.mkdir()
            output, process, run = self.flow(failure)
            with self.assertRaises(M.Blocked): run()
            self.assertTrue((output/'database.dump').exists()); self.assertFalse(process.owned)
            receipt=json.loads((output/'receipt.json').read_bytes())
            self.assertEqual(receipt['status'],'BLOCKED'); self.assertEqual(receipt['ownedCleanup'],'VERIFIED')
            self.assertFalse(receipt['releaseEligible'])

    def test_host_and_private_deployment_lock_cannot_be_redirected_by_home(self):
        from types import SimpleNamespace
        identity=SimpleNamespace(pw_name='mathwidu',pw_dir=str(self.root))
        (self.root/'.local/share/vanrides/cd').mkdir(parents=True)
        with patch.object(M.pwd,'getpwuid',return_value=identity), patch.object(M.socket,'gethostname',return_value='srv1397065'):
            M.host_identity()
            with patch.dict(os.environ,{'HOME':'/forbidden-home'}), M.deploy_lock():
                with self.assertRaises(BlockingIOError):
                    with M.deploy_lock(): self.fail('Lock was not exclusive')
            self.assertEqual((self.root/'.local/share/vanrides/cd/deploy.lock').stat().st_mode & 0o777,0o600)
        with patch.object(M.socket,'gethostname',return_value='wrong-host'):
            with self.assertRaises(M.Blocked): M.host_identity()

    def test_name_collision_never_creates_or_deletes_a_container(self):
        output, process, run = self.flow('collision')
        with self.assertRaises(M.Blocked): run()
        self.assertFalse(any(x[0] in ('create','rm') for x in process.calls))

    def test_runner_bounds_private_output_and_does_not_inherit_credentials_or_remote_docker(self):
        output = self.root / 'runner'; output.mkdir(mode=0o700)
        def execute(command, **options):
            self.assertNotIn('OPENAI_API_KEY',options['env']); self.assertNotIn('DOCKER_HOST',options['env'])
            self.assertNotIn('LIVE_SECRET',options['env']); options['stdout'].write(b'123456789')
            return subprocess.CompletedProcess(command,0)
        with patch.dict(os.environ,{'OPENAI_API_KEY':'PRIVATE_SENTINEL','DOCKER_HOST':'tcp://forbidden','LIVE_SECRET':'PRIVATE_SENTINEL'}):
            with self.assertRaises(M.Blocked): M.Docker(output,execute)(['version'],maximum=8)
        self.assertTrue(all(path.stat().st_mode & 0o777 == 0o600 for path in output.iterdir() if path.is_file()))

    def test_real_and_synthetic_scope_counts_are_accepted_but_foreign_or_uncovered_data_are_blocked(self):
        valid=('scope|2|0|1|1\ncredentials|5|0\nunsupported|0\npublic.flyway_schema_history|43|'+ '4'*32 +'\nwa.profile|1|'+ '5'*32 +'\n').encode()
        self.assertEqual(M.fingerprint(lambda *args:valid,LIVE_DB)['cohorts'],{'real':1,'synthetic':1})
        for raw in (valid.replace(b'scope|2|0|1|1',b'scope|2|1|1|1'),valid.replace(b'credentials|5|0',b'credentials|5|1'),
                    valid.replace(b'unsupported|0',b'unsupported|1')):
            with self.assertRaises(M.Blocked): M.fingerprint(lambda *args:raw,LIVE_DB)

    def test_cli_requires_explicit_execution_before_any_subprocess(self):
        from contextlib import redirect_stderr
        captured=io.StringIO()
        with patch.object(M.subprocess,'run',side_effect=AssertionError('External command')), patch.object(sys_module(),'argv',
            ['rehearse_migration.py','--manifest',str(self.path),'--expected-manifest-sha256',self.expected,'--output',str(self.root/'never')]), redirect_stderr(captured):
            self.assertEqual(M.main(),2)
        self.assertIn('PREFLIGHT_BLOCKED_NO_DOCKER',captured.getvalue())
        self.assertFalse((self.root/'never').exists())


def re_full_container(value):
    import re
    return re.fullmatch(r'container:[a-f0-9]{64}',value) is not None


def sys_module():
    import sys
    return sys


if __name__ == '__main__':
    unittest.main()
