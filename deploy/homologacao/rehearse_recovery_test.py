#!/usr/bin/env python3
"""Controles locais com ZIP/IO temporário e Docker mockado; nenhum contêiner real."""
import copy
from contextlib import nullcontext, redirect_stdout
import io
import json
import os
from pathlib import Path
import tempfile
from subprocess import TimeoutExpired
import unittest
from unittest.mock import Mock, patch
from zipfile import ZipFile

import rehearse_recovery as recovery
import recovery_fixture as fixture
from rehearse_migration import Blocked, encode, read, sha, write_new


def jar():
    out = io.BytesIO()
    with ZipFile(out, 'w') as z:
        z.writestr('BOOT-INF/classes/db/migration/V43__synthetic.sql', 'SELECT 1;')
    return out.getvalue()


class TempTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name).resolve()

    def value(self):
        path = self.root / 'candidate.jar'; path.write_bytes(jar())
        return {'schemaVersion': 1, 'environment': 'wa070-local', 'candidate': {
            'jar': str(path), 'jarSha256': sha(jar()), 'revision': 'a' * 40, 'tree': 'b' * 40, 'flywayVersion': 43},
            'images': {'apiRuntime': recovery.API_REPOSITORY + '@sha256:' + 'a' * 64,
                       'node': fixture.NODE, 'postgres': fixture.POSTGRES, 'redis': fixture.REDIS, 'n8n': fixture.N8N},
            'sourceFiles': fixture.assets(), 'workflows': fixture.bundle()[1], 'privacy': {'state': 'PENDING_V44'}}

    def manifest(self, value):
        raw = encode(value); path = self.root / 'manifest.json'; path.write_bytes(raw)
        return recovery.manifest(path, sha(raw))


class ManifestTest(TempTest):
    def test_exact_manifest_anchors_real_sources_bundle_and_jar(self):
        value = self.value(); loaded, raw_jar, raw, bundle, workflows = self.manifest(value)
        self.assertEqual(loaded, value); self.assertEqual(raw_jar, jar()); self.assertEqual(raw, encode(value))
        self.assertEqual(bundle['n8nVersion'], '2.45.4'); self.assertEqual(set(workflows), set(fixture.FLOWS))

    def test_unreviewed_inputs_never_reach_docker(self):
        value = self.value()
        mutations = [lambda v: v.update(command='echo unsafe'), lambda v: v.update(environment='vanrides-mvp-hml'),
                     lambda v: v.update(schemaVersion=True), lambda v: v.update(privacy={'state': 'PASS'}),
                     lambda v: v['candidate'].update(flywayVersion=44), lambda v: v['candidate'].update(jarSha256='0' * 64),
                     lambda v: v['images'].update(n8n='n8nio/n8n:latest'), lambda v: v['images'].update(redis='redis:latest'),
                     lambda v: v['images'].update(apiRuntime='repo@sha256:' + '0' * 64),
                     lambda v: v['sourceFiles'].update({'unreviewed.py': 'a' * 64}), lambda v: v['workflows'].update({'W01.json': '0' * 64})]
        with patch.object(recovery, 'Docker') as docker:
            for mutate in mutations:
                candidate = copy.deepcopy(value); mutate(candidate)
                with self.subTest(mutate=mutate), self.assertRaises(Blocked): self.manifest(candidate)
            docker.assert_not_called()

    def test_hash_symlink_duplicate_key_and_path_are_rejected(self):
        value = self.value(); self.manifest(value)
        path = self.root / 'manifest.json'
        with self.assertRaisesRegex(Blocked, 'REC_MANIFEST_CHANGED'): recovery.manifest(path, '0' * 64)
        raw = encode(value).replace(b'"schemaVersion":1', b'"schemaVersion":1,"schemaVersion":1')
        path.write_bytes(raw)
        with self.assertRaisesRegex(Blocked, 'DUPLICATE_JSON_KEY'): recovery.manifest(path, sha(raw))
        linked = self.root / 'linked.jar'; linked.symlink_to(self.root / 'candidate.jar')
        value['candidate']['jar'] = str(linked)
        with self.assertRaises(OSError): self.manifest(value)

    def test_inventory_is_read_only_and_no_docker_or_host_needed(self):
        with patch.object(recovery, 'Docker') as docker, patch.object(recovery, 'host_identity') as identity, redirect_stdout(io.StringIO()) as out:
            self.assertEqual(recovery.main(['--inventory']), 0)
        self.assertEqual(json.loads(out.getvalue())['sourceFiles'], fixture.assets())
        docker.assert_not_called(); identity.assert_not_called()

    def test_exact_raw_manifest_is_private_and_pass_is_never_implicit(self):
        value = self.value(); raw = json.dumps(value, indent=3).encode() + b'\n'
        manifest = self.root / 'manifest.json'; manifest.write_bytes(raw); output = self.root / 'output'
        result = {'status': 'CORE_VERIFIED_PRIVACY_PENDING', 'checks': [], 'privacy': {'state': 'PENDING_V44'}, 'releaseEligible': False}
        with patch.object(recovery, 'host_identity'), patch.object(recovery, 'deploy_lock', return_value=nullcontext()), \
                patch.object(recovery, 'Docker') as docker, patch.object(recovery, 'rehearse', return_value=result) as run, redirect_stdout(io.StringIO()):
            self.assertEqual(recovery.main(['--manifest', str(manifest), '--sha256', sha(raw), '--output', str(output)]), 0)
        self.assertEqual((output / 'manifest.json').read_bytes(), raw)
        self.assertEqual((output / 'manifest.json').stat().st_mode & 0o777, 0o600)
        self.assertEqual(output.stat().st_mode & 0o777, 0o700)
        self.assertEqual(run.call_args.kwargs['manifest_sha'], sha(raw)); docker.assert_called_once()


class FixtureTest(TempTest):
    def test_derived_identifiers_respect_n8n_2454_postgresql_varchar36_contract(self):
        copies, credential = fixture.workflows(fixture.bundle()[2], 'a' * 32)
        ids = {workflow['id'] for workflow in copies.values()}
        self.assertEqual(len(ids), 8); self.assertNotIn(credential, ids)
        self.assertTrue(all(len(identifier) <= 36 for identifier in (*ids, credential)))
        self.assertEqual(len(credential), 34); self.assertEqual({len(x) for x in ids}, {30})
        for key, workflow in copies.items():
            self.assertEqual(set(workflow['settings']['callerIds'].split(',')), {copies[x]['id'] for x in ('W01', 'W08')})
            self.assertEqual(workflow['settings'].get('errorWorkflow'), None if key == 'W07' else copies['W07']['id'])
            for node in workflow['nodes']:
                if node['type'] == 'n8n-nodes-base.httpRequest': self.assertEqual(node['credentials']['httpHeaderAuth']['id'], credential)
        self.assertNotEqual(fixture.workflows(fixture.bundle()[2], 'b' * 32)[1], credential)

    def test_only_copied_workflow_identifiers_url_and_credentials_change(self):
        before = fixture.bundle()[1:]; original = before[1]; frozen = copy.deepcopy(original)
        copies, credential = fixture.workflows(original, 'f' * 32)
        self.assertEqual(original, frozen); self.assertEqual(fixture.bundle()[1:], before)
        for key, copied in copies.items():
            self.assertFalse(copied['active']); self.assertNotEqual(copied['id'], original[key]['id'])
            self.assertEqual(copied['connections'], original[key]['connections'])
            for node, old in zip(copied['nodes'], original[key]['nodes']):
                if node['type'] == 'n8n-nodes-base.httpRequest':
                    self.assertEqual(node['parameters']['url'], old['parameters']['url'].replace('vanrides-wa070-worker:7080', 'worker:7081'))
                    self.assertEqual(node['credentials']['httpHeaderAuth']['id'], credential)
                    node['parameters']['url'] = old['parameters']['url']; node['credentials'] = old['credentials']
                self.assertEqual(node, old)
        self.assertEqual(set(fixture.SCHEDULES), {'W01', 'W04', 'W06', 'W08'})

    def test_generated_env_has_queue_postgres_shared_synthetic_key_no_external_provider(self):
        keys = fixture.secrets_for_run(); env = fixture.n8n_env('postgres', 'redis', keys)
        self.assertEqual(env['EXECUTIONS_MODE'], 'queue'); self.assertEqual(env['DB_TYPE'], 'postgresdb')
        self.assertEqual(env['QUEUE_BULL_REDIS_HOST'], 'redis'); self.assertEqual(env['N8N_ENCRYPTION_KEY'], keys['n8n'])
        self.assertEqual(env['N8N_USE_WORKFLOW_PUBLICATION_SERVICE'], 'true')
        api = fixture.api_env('postgres', keys)
        for key in ('WA_MVP_ONLINE', 'WA_APPROACH_ENABLED', 'WA_AUTO_FINISH_ENABLED', 'WA_MVP_REHEARSAL_ROUTINES'):
            self.assertEqual(api[key], 'false')
        self.assertEqual(api['WA_ENVIRONMENT'], 'wa070-local')
        self.assertEqual(api['WA_OPERATOR_CREDENTIAL'], keys['operator']); self.assertIn('WA_OPERATOR_EXPIRES_AT', api)
        # PublicRuntimeConfigurationGuard rejects disabled outside the explicit published MVP modes.
        guard = (fixture.ROOT / 'src/backend/src/main/java/com/vanrides/vanrides/config/PublicRuntimeConfigurationGuard.java').read_text()
        self.assertIn('email.equals("disabled") && !publishedMvp', guard)
        self.assertEqual(tuple(api[k] for k in ('SPRING_PROFILES_ACTIVE', 'WA_TEAM_API_ONLY', 'WA_MVP_ONLINE', 'VANRIDES_EMAIL_PROVIDER')),
                         ('local', 'false', 'false', 'log'))
        self.assertNotIn('VANRIDES_EMAIL_RESEND_API_KEY', api); self.assertEqual(api['VANRIDES_PUSH_PROVIDER'], 'none')
        self.assertFalse(any('OPENAI' in key or 'EVOLUTION' in key for key in api))

    def test_seed_is_synthetic_distinct_credentials_one_hour_and_zero_phone(self):
        keys = fixture.secrets_for_run(); sql, identity, instance = fixture.seed('a' * 32, keys)
        self.assertIn("'wa070-local',true", sql); self.assertIn(fixture.ADDRESS, sql)
        for key in ('CHANNEL', 'CONVERSATION_WORKER', 'DELIVERY_WORKER', 'SYSTEM_JOB'):
            self.assertIn(sha(keys[key].encode()), sql); self.assertNotIn(keys[key], sql)
        self.assertEqual(sql.count("interval '1 hour'"), 4)
        event = fixture.event(instance, 'oi')
        self.assertEqual(event['data']['key']['remoteJid'], fixture.ADDRESS); self.assertEqual(event['data']['message']['conversation'], 'oi')

    def test_services_use_internal_network_no_ports_loopback_provider_and_real_n8n_worker(self):
        value = self.value(); owned = Mock(); owned.nonce = 'a' * 32
        owned.volume.side_effect = lambda role: 'owned-' + role
        owned.remaining.side_effect = lambda maximum: maximum
        owned.create.side_effect = lambda role, *args: 'c' * 64
        session = recovery.Session(owned, value, self.root, fixture.bundle()[2], pause=lambda _: None)
        session.wait = Mock(); session.sql = Mock(return_value='43|0')
        session.services('restored', 'restored-pg', 'owned-internal', restored=True)
        calls = {call.args[0]: call.args[1:] for call in owned.create.call_args_list}
        for role, (image, options, *command) in calls.items():
            self.assertNotIn('-p', options); self.assertNotIn('--publish', options); self.assertIn('--memory', options)
            self.assertIn('--read-only', options) if role != 'restored-redis' else None
        self.assertEqual(calls['restored-n8n-worker'][-1], ['worker', '--concurrency=1'])
        for role in ('restored-main', 'restored-n8n-worker'):
            options = calls[role][1]
            self.assertEqual(options[options.index('--user') + 1], f'{os.getuid()}:{os.getgid()}')
            self.assertIn(f'/home/node/.cache:rw,nosuid,nodev,size=128m,mode=700,uid={os.getuid()},gid={os.getgid()}',
                          [options[i + 1] for i, value in enumerate(options) if value == '--tmpfs'])
            self.assertIn('--read-only', options); self.assertEqual(options[options.index('--memory') + 1], '1024m')
            self.assertEqual(options[options.index('--memory-swap') + 1], '1024m')
        self.assertIn('--appendfsync', calls['restored-redis'][-1]); self.assertIn('always', calls['restored-redis'][-1])
        self.assertIn('container:' + 'c' * 64, calls['restored-wa'][1])
        env = (self.root / 'restored-worker.env').read_text()
        self.assertIn('WA070_EVOLUTION_BASE_URL=http://127.0.0.1:7081\n', env)
        self.assertIn('WA070_MODEL_MODE=disabled\n', env); self.assertIn('_TOKEN_FILE=', env)
        self.assertNotIn('source', (self.root / 'restored-n8n.env').read_text())
        self.assertIn('VANRIDES_JAR_SHA256=' + value['candidate']['jarSha256'] + '\n', (self.root / 'restored-api.env').read_text())


class FakeDocker:
    def __init__(self): self.calls, self.containers, self.networks, self.volumes = [], {}, {}, {}
    def __call__(self, args, data=None, **kwargs):
        self.calls.append(args)
        if args[:2] == ['network', 'ls'] or args[:2] == ['volume', 'ls']: return b''
        if args[:2] in (['network', 'create'], ['volume', 'create']):
            labels = dict(args[i + 1].split('=', 1) for i, x in enumerate(args) if x == '--label')
            kind = args[0]; value = {'name': args[-1], 'driver': 'bridge' if kind == 'network' else 'local', 'labels': labels}
            if kind == 'network': value['internal'] = True
            getattr(self, kind + 's')[args[-1]] = value; return args[-1].encode()
        if args[:2] == ['network', 'inspect']: return encode(self.networks[args[-1]])
        if args[:2] == ['volume', 'inspect']: return encode(self.volumes[args[-1]])
        if args[0] == 'create':
            name = args[args.index('--name') + 1]; image = args[-1]; network = args[args.index('--network') + 1]
            labels = dict(args[i + 1].split('=', 1) for i, x in enumerate(args) if x == '--label')
            cid = str(len(self.containers) + 1).zfill(64)
            self.containers[cid] = {'id': cid, 'name': '/' + name, 'image': image, 'labels': labels,
                                    'network': network, 'networks': {network: {}}, 'ports': {}, 'privileged': False}
            return cid.encode()
        if args[0] == 'inspect': return encode(self.containers[args[-1]])
        if args[0] == 'ps':
            if any(x.startswith('name=') for x in args): return b''
            return '\n'.join(self.containers).encode()
        if args[0] == 'rm': self.containers.pop(args[-1]); return b''
        return b''


class OwnershipTest(unittest.TestCase):
    def create(self):
        docker = FakeDocker(); owned = recovery.Owned(docker, 'a' * 32)
        network = owned.network('source-network'); owned.volume('source-pg-data')
        cid = owned.create('source-pg', fixture.POSTGRES, ['--network', network, '--memory', '512m'])
        return docker, owned, cid

    def test_restart_only_owned_and_cleanup_retains_volume_without_prune(self):
        docker, owned, cid = self.create(); owned.action('source-pg', 'restart'); owned.cleanup()
        self.assertIn(['restart', '--time', '30', cid], docker.calls)
        self.assertIn(['rm', '-f', cid], docker.calls); self.assertEqual(len(docker.volumes), 1)
        self.assertFalse(any(x in ('prune', '-v', '--volumes', 'service') for args in docker.calls for x in args))
        self.assertTrue(any(args[:2] == ['network', 'create'] and '--internal' in args for args in docker.calls))

    def test_n8n_diagnostics_precede_cli_without_environment_or_shell(self):
        docker = FakeDocker(); owned = recovery.Owned(docker, 'a' * 32)
        owned.create('source-main', fixture.N8N, ['--network', owned.network('source-network')])
        session = object.__new__(recovery.Session); session.owned = owned; session.n8n_cli('source', 'import:workflow')
        commands = docker.calls
        df = next(i for i, args in enumerate(commands) if args[0] == 'exec' and args[2:4] == ['df', '-k'])
        cli = next(i for i, args in enumerate(commands) if args[0] == 'exec' and args[2:4] == ['n8n', 'import:workflow'])
        self.assertLess(df, cli); self.assertEqual(commands[df][4:], ['/tmp', '/home/node/.n8n', '/home/node/.cache'])
        state = next(args[2] for args in commands if args[0] == 'inspect' and '.State.Running' in args[2])
        self.assertIn('.State.OOMKilled', state); self.assertIn('.State.ExitCode', state); self.assertNotIn('.Env', state)

    def test_diagnostic_errors_do_not_fail_owned_cleanup(self):
        docker, owned, cid = self.create()
        def execute(args, data=None, **limits):
            if args[0] == 'logs' or (args[0] == 'inspect' and '.State.Running' in args[2]): raise Blocked('DIAGNOSTIC_UNAVAILABLE')
            return docker(args, data, **limits)
        owned.execute = execute; owned.cleanup()
        self.assertNotIn(cid, docker.containers); self.assertIn(['rm', '-f', cid], docker.calls)

    def test_foreign_name_label_image_id_or_exposed_network_blocks_every_mutation(self):
        for field, value in (('name', '/live-api'), ('image', 'foreign'), ('id', 'f' * 64), ('labels', {}),
                             ('ports', {'8080/tcp': []}), ('privileged', True), ('networks', {'external': {}})):
            docker, owned, cid = self.create(); docker.containers[cid][field] = value; start = len(docker.calls)
            with self.subTest(field=field), self.assertRaises(Blocked): owned.action('source-pg', 'restart')
            with self.assertRaises(Blocked): owned.cleanup()
            self.assertFalse(any(args[0] in ('restart', 'stop', 'rm') for args in docker.calls[start:]))

    def test_lost_create_ack_is_reconciled_by_exact_name_image_nonce(self):
        docker, owned, cid = self.create(); owned.containers['source-pg']['id'] = None
        owned.cleanup(); self.assertEqual(docker.containers, {})

    def test_foreign_volume_or_network_prevents_cleanup_before_removal(self):
        for kind in ('volumes', 'networks'):
            docker, owned, cid = self.create()
            next(iter(getattr(docker, kind).values()))['labels'] = {}
            with self.subTest(kind=kind), self.assertRaises(Blocked): owned.cleanup()
            self.assertIn(cid, docker.containers)


class BackupTest(TempTest):
    def session(self, scope='1|0', unstable=False, changed=False):
        session = object.__new__(recovery.Session); session.output = self.root
        session.value = {'schemaVersion': 1}
        session.owned = Mock()
        def execute(role, command, data=None, **options):
            if command[0] == 'pg_dump':
                raw = b'PGDMP-synthetic'; write_new(options['sink'], raw); return raw
            self.assertEqual(role, 'restored-pg'); self.assertEqual(data, b'PGDMP-synthetic'); return b''
        session.owned.exec.side_effect = execute
        n = {'fingerprint': 0}
        def sql(role, database, statement):
            if 'wa.test_cohort' in statement: return scope
            n['fingerprint'] += 1
            if unstable and n['fingerprint'] == 2: return 'public.foo|0|abc'
            if changed and role == 'restored-pg': return 'public.foo|2|abc'
            return 'public.foo|1|abc'
        session.sql = Mock(side_effect=sql); return session

    def test_real_dump_restore_commands_distinct_target_private_files_comparison(self):
        session = self.session(); receipt = {}; session.backup_restore('source-pg', 'restored-pg', receipt)
        self.assertEqual(len(receipt['backups']), 2)
        self.assertTrue(all(row['sha256'] == sha(b'PGDMP-synthetic') for row in receipt['backups']))
        for database in fixture.DATABASES: self.assertEqual((self.root / (database + '.dump')).stat().st_mode & 0o777, 0o600)
        for call in session.owned.exec.call_args_list:
            if call.args[1][0] == 'pg_restore':
                self.assertEqual(call.args[0], 'restored-pg'); self.assertIn('--single-transaction', call.args[1])

    def test_real_or_cross_environment_scope_never_dumps(self):
        session = self.session(scope='2|1')
        with self.assertRaisesRegex(Blocked, 'SYNTHETIC_SCOPE'): session.backup_restore('source-pg', 'restored-pg', {})
        session.owned.exec.assert_not_called()

    def test_source_cannot_be_a_restore_target(self):
        session = self.session()
        with self.assertRaisesRegex(Blocked, 'RESTORE_TARGET_INVALID'): session.backup_restore('source-pg', 'source-pg', {})
        session.owned.exec.assert_not_called()

    def test_change_during_dump_blocks_before_restore(self):
        session = self.session(unstable=True)
        with self.assertRaisesRegex(Blocked, 'BACKUP_UNSTABLE'): session.backup_restore('source-pg', 'restored-pg', {})
        self.assertFalse(any(call.args[1][0] == 'pg_restore' for call in session.owned.exec.call_args_list))
        self.assertTrue((self.root / (fixture.DATABASES[0] + '.dump')).exists())

    def test_wrong_restore_content_is_not_a_pass(self):
        session = self.session(changed=True)
        with self.assertRaisesRegex(Blocked, 'RESTORE_CONTENT_CHANGED'): session.backup_restore('source-pg', 'restored-pg', {})

    def test_privacy_port_only_reaches_restored_database_and_cannot_accept_a_receipt(self):
        session = Mock(); target = recovery.RestoredDatabase(session, 'restored-pg'); target.sql('SELECT 1')
        session.sql.assert_called_once_with('restored-pg', fixture.DATABASES[0], 'SELECT 1')
        with self.assertRaises(Blocked): recovery.RestoredDatabase(session, 'source-pg')
        self.assertEqual(recovery.privacy_after_restore(target, {'state': 'PENDING_V44'})['absenceVerified'], False)
        with self.assertRaises(Blocked): recovery.privacy_after_restore(target, {'state': 'PASS', 'receipt': {}})

    def test_failure_persists_blocked_receipt_and_only_attempts_owned_cleanup(self):
        value = self.value(); run = Mock(side_effect=Blocked('SYNTHETIC_IMAGE_NOT_LOCAL'))
        owned = Mock(); owned.nonce = 'a' * 32; owned.volumes = {}; owned.containers = {}; owned.networks = {}
        with patch.object(recovery, 'Owned', return_value=owned):
            with self.assertRaises(Blocked): recovery.rehearse(value, jar(), self.root, run, fixture.bundle()[2])
        owned.cleanup.assert_called_once()
        receipt = json.loads((self.root / 'receipt.json').read_bytes())
        self.assertEqual(receipt['status'], 'BLOCKED'); self.assertEqual(receipt['privacy'], {'state': 'NOT_REACHED'})
        self.assertFalse(receipt['releaseEligible']); self.assertEqual(receipt['reason'], 'SYNTHETIC_IMAGE_NOT_LOCAL')


class OrchestrationTest(TempTest):
    def run_core(self, fail_restore=False):
        value = self.value(); owned = Mock(); owned.nonce = 'a' * 32
        output = self.root / 'output'; output.mkdir(mode=0o700)
        owned.volumes = {}; owned.networks = {}
        owned.containers = {'source-pg': {'id': '1' * 64}, 'restored-pg': {'id': '2' * 64}}
        owned.network.side_effect = lambda role: role
        session = Mock(); session.instance = 'wa070_synthetic_' + owned.nonce; session.keys = {'CHANNEL': 'synthetic'}
        session.value = value; session.output = output; session.owned = owned
        session.flows = fixture.workflows(fixture.bundle()[2], owned.nonce)[0]
        session.postgres.side_effect = lambda prefix, network: prefix + '-pg'
        session.verify_exports.return_value = 'f' * 64; session.sql.return_value = '1'
        session.http.return_value = {'status': 200, 'body': {'status': 'UP'}}
        state = {'queueWorkerResumed': False, 'restored': False, 'controls': 0}
        def action(role, operation):
            if role == 'source-n8n-worker' and operation == 'start': state['queueWorkerResumed'] = True
        owned.action.side_effect = action
        def services(prefix, pg, network, restored=False):
            if restored: state['restored'] = True
        session.services.side_effect = services
        session.counts.side_effect = lambda pg: '2|2|2' if pg == 'restored-pg' else ('1|1|1' if state['queueWorkerResumed'] else '0|0|0')
        def channel(prefix):
            state['controls'] += 1
            return {'accepted': 2 if prefix == 'restored' else int(state['queueWorkerResumed']),
                    'control': dict.fromkeys(('process', 'dispatch', 'attendance', 'reconcile'), state['controls'])}
        session.channel_state.side_effect = channel
        session.pending_queue.side_effect = lambda prefix, kind='wait': int(not state['queueWorkerResumed'] and kind == 'wait')
        def wait(check, code, seconds=120):
            if not check(): raise Blocked(code)
        session.wait.side_effect = wait
        if fail_restore: session.backup_restore.side_effect = Blocked('RESTORE_CONTENT_CHANGED')
        run = Mock(return_value=b'owned-image-id')
        with patch.object(recovery, 'Owned', return_value=owned), patch.object(recovery, 'Session', return_value=session):
            if fail_restore:
                with self.assertRaisesRegex(Blocked, 'RESTORE_CONTENT_CHANGED'):
                    recovery.rehearse(value, jar(), output, run, fixture.bundle()[2], manifest_sha='e' * 64)
                result = json.loads((output / 'receipt.json').read_bytes())
            else: result = recovery.rehearse(value, jar(), output, run, fixture.bundle()[2], manifest_sha='e' * 64)
        return result, session, owned, run

    def test_core_orders_real_operations_and_never_marks_privacy_complete(self):
        receipt, session, owned, run = self.run_core()
        self.assertEqual(receipt['status'], 'CORE_VERIFIED_PRIVACY_PENDING'); self.assertFalse(receipt['releaseEligible'])
        self.assertFalse(receipt['privacy']['reapplied']); self.assertFalse(receipt['privacy']['absenceVerified'])
        self.assertEqual(receipt['manifestSha256'], 'e' * 64); self.assertEqual(receipt['queueAtBackup'], {'waiting': 0, 'active': 0})
        commands = [call.args[1:] for call in session.n8n_cli.call_args_list]
        self.assertEqual([c for c in commands if c[0] == 'publish:workflow'],
                         [('publish:workflow', '--id=' + session.flows[key]['id']) for key in fixture.SCHEDULES])
        self.assertFalse(any(c[0] in ('execute', 'update:workflow') for c in commands))
        for role in ('source-pg', 'source-redis'): self.assertIn(unittest.mock.call(role, 'restart'), owned.action.call_args_list)
        session.backup_restore.assert_called_once()
        self.assertEqual(session.backup_restore.call_args.args[:2], ('source-pg', 'restored-pg'))
        self.assertEqual(len([call for call in session.send.call_args_list if len(call.args) > 2 and call.args[2] == 'DUPLICATE']), 3)
        self.assertTrue(all(call.args[0][:2] == ['image', 'inspect'] for call in run.call_args_list))
        owned.cleanup.assert_called_once()

    def test_restore_failure_cannot_activate_restored_consumers_or_report_core_verified(self):
        receipt, session, owned, run = self.run_core(fail_restore=True)
        self.assertEqual(receipt['status'], 'BLOCKED'); self.assertEqual(receipt['stage'], 'RESTORE_TO_DIFFERENT_DATABASE')
        self.assertEqual(receipt['privacy'], {'state': 'NOT_REACHED'})
        self.assertFalse(any(call.kwargs.get('restored') for call in session.services.call_args_list))
        owned.cleanup.assert_called_once()


class Clock:
    def __init__(self): self.now = 0.0
    def __call__(self): return self.now
    def advance(self, seconds): self.now += seconds


class DeadlineTest(TempTest):
    def session(self, execute):
        clock = Clock(); session = object.__new__(recovery.Session)
        session.owned = recovery.Owned(execute, 'a' * 32, clock); session.pause = clock.advance
        return session, clock

    def test_slow_probes_consume_wall_budget_instead_of_120_iterations(self):
        calls = []
        def execute(args, data=None, timeout=120):
            calls.append(timeout); clock.advance(min(40, timeout))
            if timeout <= 40: raise TimeoutExpired('synthetic-probe', timeout)
            return b''
        session, clock = self.session(execute)
        with self.assertRaisesRegex(Blocked, 'WALL_BUDGET'):
            session.wait(lambda: session.owned.run(['probe']) == b'UP', 'WALL_BUDGET', 120)
        self.assertEqual(clock(), 120); self.assertEqual(len(calls), 3)
        self.assertEqual(calls, [120, 79, 38]); self.assertIsNone(session.owned.deadline)

    def test_http_outer_timeout_includes_ownership_inspection_and_cleanup_has_new_budget(self):
        docker = FakeDocker(); calls = []; session, clock = self.session(docker)
        owned = session.owned; network = owned.network('source-network')
        owned.create('source-channel', fixture.NODE, ['--network', network])
        def execute(args, data=None, timeout=120, **limits):
            calls.append((args, data, timeout)); cost = 6 if args[0] == 'inspect' else (40 if args[0] == 'exec' else 0)
            clock.advance(min(cost, timeout))
            if cost >= timeout: raise TimeoutExpired('synthetic-http', timeout)
            return docker(args, data, **limits)
        owned.execute = execute
        with self.assertRaisesRegex(Blocked, 'HTTP_DEADLINE'):
            session.wait(lambda: session.http('source-channel', 'api', '/actuator/health')['status'] == 200, 'HTTP_DEADLINE', 20)
        self.assertEqual(clock(), 20); self.assertEqual([c[2] for c in calls], [20, 14])
        self.assertEqual(json.loads(calls[1][1])['timeoutMs'], 14000)
        owned.cleanup(); self.assertFalse(docker.containers); self.assertIsNone(owned.deadline)
        self.assertLessEqual(clock(), 140)

    def test_multiple_commands_share_remaining_and_no_call_starts_after_deadline(self):
        calls = []
        def execute(args, data=None, timeout=120):
            calls.append(timeout); clock.advance(min(3, timeout))
            if timeout < 3: raise TimeoutExpired('synthetic-command', timeout)
            return b'UP'
        session, clock = self.session(execute)
        with self.assertRaisesRegex(Blocked, 'COMBINED_DEADLINE'):
            session.wait(lambda: bool(session.owned.run(['one']) and session.owned.run(['two'])), 'COMBINED_DEADLINE', 5)
        self.assertEqual(clock(), 5); self.assertEqual(calls, [5, 2])
        with session.owned.budget(0), self.assertRaisesRegex(Blocked, 'DEADLINE_EXPIRED'): session.owned.run(['never'])
        self.assertEqual(calls, [5, 2])

    def test_sql_timeout_and_sleep_use_remaining_and_late_success_is_rejected(self):
        session, clock = self.session(Mock()); inputs = []
        session.owned.exec = Mock(side_effect=lambda role, command, data: inputs.append(data()) or b'1')
        with session.owned.budget(2.75): session.sql('source-pg', fixture.DATABASES[0], 'SELECT 1;')
        self.assertIn(b"statement_timeout='2750ms'", inputs[0])
        def late(): clock.advance(5); return True
        with self.assertRaisesRegex(Blocked, 'LATE_SUCCESS'): session.wait(late, 'LATE_SUCCESS', 5)
        before = clock()
        with self.assertRaisesRegex(Blocked, 'SHORT_PAUSE'): session.wait(lambda: False, 'SHORT_PAUSE', 0.25)
        self.assertEqual(clock() - before, 0.25)

    def test_cleanup_itself_has_one_absolute_budget(self):
        docker = FakeDocker(); session, clock = self.session(docker); owned = session.owned
        owned.create('source-channel', fixture.NODE, ['--network', owned.network('source-network')])
        def execute(args, data=None, timeout=120, **limits):
            clock.advance(min(80, timeout))
            if timeout <= 80: raise TimeoutExpired('synthetic-cleanup', timeout)
            return docker(args, data, **limits)
        owned.execute = execute
        with self.assertRaises(Blocked): owned.cleanup()
        self.assertEqual(clock(), 120); self.assertTrue(docker.containers); self.assertIsNone(owned.deadline)

    def test_wait_timeout_writes_blocked_receipt_and_runs_finally_cleanup(self):
        value = self.value(); output = self.root / 'output'; output.mkdir(mode=0o700)
        def execute(args, data=None, timeout=120): clock.advance(timeout); raise TimeoutExpired('probe', timeout)
        bounded, clock = self.session(execute)
        owned = Mock(); owned.nonce = 'a' * 32; owned.containers = {}; owned.volumes = {}; owned.networks = {}
        session = Mock(); session.keys = {'CHANNEL': 'synthetic'}; session.instance = 'synthetic'
        session.verify_exports.return_value = 'f' * 64
        session.flows = fixture.workflows(fixture.bundle()[2], owned.nonce)[0]
        session.wait.side_effect = lambda check, code, seconds=120: bounded.wait(lambda: bounded.owned.run(['probe']), code, 5)
        with patch.object(recovery, 'Owned', return_value=owned), patch.object(recovery, 'Session', return_value=session):
            with self.assertRaisesRegex(Blocked, 'REC_AGENDA_DID_NOT_ENQUEUE'):
                recovery.rehearse(value, jar(), output, Mock(), fixture.bundle()[2])
        self.assertEqual(clock(), 5); owned.cleanup.assert_called_once()
        receipt = json.loads((output / 'receipt.json').read_bytes())
        self.assertEqual(receipt['status'], 'BLOCKED'); self.assertEqual(receipt['stage'], 'QUEUE_PENDING')


if __name__ == '__main__': unittest.main()
