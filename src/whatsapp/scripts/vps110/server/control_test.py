import argparse
import copy
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch

import control
from protocol import Blocked, hash_file, receipt_bytes


class ResourcesTest(unittest.TestCase):
    @staticmethod
    def fixture():
        runtime = object.__new__(control.Runtime)
        runtime.base = Path('/private/wa110-resource-fixture')
        runtime.project = 'wa110-physical-resource-fixture'
        runtime.args = argparse.Namespace(nonce='A' * 43)
        runtime.binding = {'sourceSha':'a' * 40}
        runtime.contract = {'images':{'java':'fixed-java','postgres':'fixed-postgres'}}
        rows = []
        for service, identity in (('postgres','b' * 64),('api','c' * 64)):
            labels = {'com.docker.compose.service':service,'com.docker.compose.project':runtime.project,
                      'wa110.owner':runtime.args.nonce,'wa110.backend':runtime.binding['sourceSha'],'traefik.enable':'false'}
            ports = {'18083/tcp':[{'HostIp':'127.0.0.1','HostPort':'28083'}]} if service == 'api' else {}
            mounts = ({'/opt/wa110/app.jar':('inputs/backend.jar',False),
                       '/run/wa110/application.properties':('runtime/application.properties',False)} if service == 'api'
                      else {'/run/secrets/db-password':('runtime/db-password',False),'/run/secrets/pgpass':('runtime/pgpass',False)})
            attached = [runtime.project + '_private'] + ([runtime.project + '_edge'] if service == 'api' else [])
            rows.append({'Id':identity,'Config':{'Labels':labels,'Image':runtime.contract['images']['java' if service == 'api' else 'postgres'],
                         'User':str(os.getuid()) + ':' + str(os.getgid()) if service == 'api' else '0'},
                         'State':{'Running':True},
                         'HostConfig':{'LogConfig':{'Type':'none'},'RestartPolicy':{'Name':'no'},'PortBindings':copy.deepcopy(ports),
                           'Privileged':False,'NetworkMode':runtime.project + '_private',
                           'Memory':(1280 if service == 'api' else 512) * 1024**2,'NanoCpus':(2 if service == 'api' else 1) * 10**9},
                         'NetworkSettings':{'Networks':dict.fromkeys(attached,{}),'Ports':copy.deepcopy(ports) if service == 'api' else {'5432/tcp':None}},
                         'Mounts':[{'Type':'bind','Destination':destination,'Source':str(runtime.base / source),'RW':rw}
                                   for destination,(source,rw) in mounts.items()]})
        networks = []
        for kind, identity in (('private','d' * 64),('edge','e' * 64)):
            networks.append({'Id':identity,'Name':runtime.project + '_' + kind,'Driver':'bridge','Scope':'local','Internal':kind == 'private',
                'Labels':{'com.docker.compose.project':runtime.project,'com.docker.compose.network':kind,
                          'wa110.owner':runtime.args.nonce,'traefik.enable':'false'},
                'Containers':{row['Id']:{'Name':runtime.project + '-' + row['Config']['Labels']['com.docker.compose.service'] + '-1'}
                              for row in rows if kind == 'private' or row['Config']['Labels']['com.docker.compose.service'] == 'api'}})
        def command(args, **kwargs):
            if args[:3] == ['docker','ps','-aq']:
                return '\n'.join(row['Id'] for row in rows)
            if args[:2] == ['docker','inspect']:
                return json.dumps(rows)
            if args[:3] == ['docker','network','ls']:
                return '\n'.join(row['Id'] for row in networks)
            if args[:3] == ['docker','network','inspect']:
                return json.dumps([next(row for row in networks if row['Id'] == args[3])])
            raise AssertionError('unexpected resource probe')
        runtime.command = Mock(side_effect=command)
        return runtime, rows, networks

    def test_realistic_private_postgres_and_dual_network_api_are_accepted(self):
        runtime,rows,networks = self.fixture()
        actual,identities = runtime.resources()
        self.assertEqual(rows,actual); self.assertEqual([row['Id'] for row in networks],identities)
        self.assertTrue(all(call.kwargs == {'guarded':False} for call in runtime.command.call_args_list))

    def test_internal_edge_postgres_on_edge_and_foreign_network_members_are_rejected(self):
        for scenario in ('internal-edge','postgres-edge','foreign-edge','foreign-private','postgres-member-edge'):
            with self.subTest(scenario=scenario):
                runtime,rows,networks = self.fixture()
                if scenario == 'internal-edge': networks[1]['Internal'] = True
                elif scenario == 'postgres-edge': rows[0]['NetworkSettings']['Networks'][runtime.project + '_edge'] = {}
                elif scenario == 'postgres-member-edge': networks[1]['Containers'][rows[0]['Id']] = {}
                else: networks[1 if scenario == 'foreign-edge' else 0]['Containers']['f' * 64] = {}
                with self.assertRaises(Blocked): runtime.resources()

    def test_running_api_requires_actual_loopback_publish_and_rejects_public_binding(self):
        for ports in (None,{}, {'18083/tcp':None}, {'18083/tcp':[{'HostIp':'0.0.0.0','HostPort':'28083'}]},
                      {'18083/tcp':[{'HostIp':'::','HostPort':'28083'}]},
                      {'18083/tcp':[{'HostIp':'127.0.0.1','HostPort':'28084'}]}):
            with self.subTest(ports=ports):
                runtime,rows,_ = self.fixture(); rows[1]['NetworkSettings']['Ports'] = ports
                with self.assertRaisesRegex(Blocked,'RUNTIME_EXPOSURE'): runtime.resources()

    def test_dead_api_with_missing_runtime_publish_can_be_cleaned_but_public_mapping_never_passes(self):
        runtime,rows,_ = self.fixture(); rows[1]['State']['Running'] = False
        rows[1]['NetworkSettings']['Ports'] = {'18083/tcp':None}
        runtime.resources()
        rows[1]['NetworkSettings']['Ports'] = {'18083/tcp':[{'HostIp':'0.0.0.0','HostPort':'28083'}]}
        with self.assertRaisesRegex(Blocked,'RUNTIME_EXPOSURE'): runtime.resources()
        rows[1]['NetworkSettings']['Ports'] = {}
        rows[1]['HostConfig']['PortBindings'] = {}
        with self.assertRaisesRegex(Blocked,'CONTAINER_EXPOSURE'): runtime.resources()

    def test_postgres_must_not_publish_a_runtime_port(self):
        runtime,rows,_ = self.fixture()
        rows[0]['NetworkSettings']['Ports'] = {'5432/tcp':[{'HostIp':'127.0.0.1','HostPort':'5432'}]}
        with self.assertRaisesRegex(Blocked,'RUNTIME_EXPOSURE'): runtime.resources()

    def test_network_name_kind_owner_driver_scope_and_traefik_labels_are_checked(self):
        for change in ({'Name':'foreign_edge'},{'Driver':'overlay'},{'Scope':'swarm'},
                       {'Labels':{'com.docker.compose.network':'foreign'}},
                       {'Labels':{'wa110.owner':'B' * 43}}, {'Labels':{'com.docker.compose.project':'foreign'}},
                       {'Labels':{'traefik.enable':'true'}}):
            with self.subTest(change=change):
                runtime,_,networks = self.fixture()
                if 'Labels' in change: networks[1]['Labels'].update(change['Labels'])
                else: networks[1].update(change)
                with self.assertRaisesRegex(Blocked,'NETWORK_OWNERSHIP'): runtime.resources()

    def test_missing_duplicate_or_third_network_is_rejected(self):
        for scenario in ('missing','duplicate','third'):
            with self.subTest(scenario=scenario):
                runtime,_,networks = self.fixture()
                if scenario == 'missing': networks.pop()
                elif scenario == 'duplicate': networks[1].update(Name=networks[0]['Name'],Labels=copy.deepcopy(networks[0]['Labels']))
                else: networks.append({'Id':'f' * 64})
                with self.assertRaises(Blocked): runtime.resources()

    def test_partial_start_and_fully_stopped_resources_remain_eligible_for_cleanup(self):
        runtime,rows,networks = self.fixture(); api = rows.pop()
        for network in networks: network['Containers'].pop(api['Id'],None)
        runtime.resources()
        rows.clear()
        for network in networks: network['Containers'].clear()
        runtime.resources(); networks.clear()
        self.assertEqual(([],[]),runtime.resources())


class ControlTest(unittest.TestCase):
    def test_owned_cgroups_are_resolved_only_once_per_validated_running_container(self):
        runtime = object.__new__(control.Runtime)
        runtime.owned_memory = {}
        runtime.pressure_guard = Mock(current_mib=11260)
        pg = {'Id':'a'*64,'State':{'Pid':101},'Config':{'Labels':{'com.docker.compose.service':'postgres'}}}
        api = {'Id':'b'*64,'State':{'Pid':102},'Config':{'Labels':{'com.docker.compose.service':'api'}}}
        runtime.resources = Mock(side_effect=[([pg],[]),([pg,api],[]),([pg,api],[])])
        def create(row,*_args):
            return Mock(container_id=row['Id'],pid=row['State']['Pid'])
        with patch.object(control,'OwnedMemoryGuard',side_effect=create) as reader:
            runtime.bind_owned_memory();runtime.bind_owned_memory();runtime.bind_owned_memory()
        self.assertEqual(reader.call_count,2)
        runtime.resources=Mock(return_value=([pg,{**api,'State':{'Pid':999}}],[]))
        with self.assertRaisesRegex(Blocked,'RUNTIME_CHANGED'):runtime.bind_owned_memory()

    def test_global_windows_are_required_before_hold_and_at_conclusion(self):
        runtime=object.__new__(control.Runtime);runtime.args=argparse.Namespace(hold_seconds=0)
        runtime.owner_path=Mock();runtime.owner_path.exists.return_value=True
        events=[]
        for name,label in [('start','start'),('settle_before_start','global'),('assert_live_health','health'),('stop','stop')]:
            setattr(runtime,name,Mock(side_effect=lambda label=label:events.append(label)))
        runtime.status=Mock(side_effect=lambda:events.append('status') or {})
        runtime.infra_smoke()
        self.assertEqual(events,['start','global','health','global','health','stop','status'])

    def test_pull_is_serial_and_stops_before_next_image_on_guard_failure(self):
        runtime = object.__new__(control.Runtime)
        runtime.docker = ['docker', 'compose', '-p', 'synthetic-fixture']
        stages = []
        def fail(_args, **_kwargs):
            stages.append(runtime.startup_stage)
            raise Blocked('VPS_SUSTAINED_MEMORY_PRESSURE')
        runtime.command = Mock(side_effect=fail)
        with self.assertRaisesRegex(Blocked, 'VPS_SUSTAINED_MEMORY_PRESSURE'):
            runtime.pull_images()
        self.assertEqual(stages, ['PULL_POSTGRES'])
        self.assertEqual(runtime.command.call_count, 1)
        self.assertEqual(runtime.command.call_args.args[0][-4:], ['--parallel', '1', 'pull', 'postgres'])

    def test_pull_reports_exact_image_stage_on_second_failure(self):
        runtime = object.__new__(control.Runtime)
        runtime.docker = ['docker', 'compose']
        stages = []
        def run(args, **kwargs):
            stages.append((runtime.startup_stage, args, kwargs))
            if len(stages) == 2:
                raise Blocked('OWNED_COMMAND_TIMEOUT')
        runtime.command = Mock(side_effect=run)
        with self.assertRaisesRegex(Blocked, 'OWNED_COMMAND_TIMEOUT'):
            runtime.pull_images()
        self.assertEqual([row[0] for row in stages], ['PULL_POSTGRES', 'PULL_API'])
        self.assertEqual([row[1][-4:] for row in stages],
                         [['--parallel', '1', 'pull', 'postgres'], ['--parallel', '1', 'pull', 'api']])
        self.assertEqual(runtime.startup_stage, 'PULL_API')

    def test_cleanup_is_idempotent_after_interruption(self):
        with tempfile.TemporaryDirectory() as folder:
            runtime = object.__new__(control.Runtime)
            runtime.runtime = Path(folder)
            for name in control.PRIVATE_NAMES:
                path = runtime.runtime / name
                path.write_text('synthetic-fixture')
                path.chmod(0o600)
            actual = Path.unlink
            count = 0
            def interrupted(path, *args, **kwargs):
                nonlocal count
                count += 1
                if count == 2:
                    raise OSError('synthetic interruption')
                return actual(path, *args, **kwargs)
            with patch.object(Path, 'unlink', interrupted), self.assertRaises(OSError):
                runtime.clean_private_files()
            with self.assertRaisesRegex(Blocked, 'SECRET_FILES_REMAIN'):
                runtime.assert_no_private_files()
            runtime.clean_private_files()
            runtime.clean_private_files()
            runtime.assert_no_private_files()

    def test_cleanup_does_not_follow_foreign_symlink(self):
        with tempfile.TemporaryDirectory() as folder:
            runtime = object.__new__(control.Runtime)
            runtime.runtime = Path(folder) / 'runtime'
            runtime.runtime.mkdir()
            target = Path(folder) / 'outside'
            target.write_text('keep')
            (runtime.runtime / control.PRIVATE_NAMES[0]).symlink_to(target)
            with self.assertRaisesRegex(Blocked, 'OWNERSHIP'):
                runtime.clean_private_files()
            self.assertEqual(target.read_text(), 'keep')

    def test_no_stopped_marker_when_cleanup_fails(self):
        with tempfile.TemporaryDirectory() as folder:
            runtime = self.stopped_fixture(Path(folder))
            runtime.clean_private_files = Mock(side_effect=OSError('synthetic fixture'))
            with self.assertRaises(OSError):
                runtime.stop()
            self.assertFalse((runtime.runtime / 'stopped.json').exists())
            self.assertFalse((runtime.base / 'receipt.json').exists())

    def test_stopped_status_rechecks_private_absence(self):
        with tempfile.TemporaryDirectory() as folder:
            runtime = self.stopped_fixture(Path(folder))
            runtime.stop()
            self.assertEqual(runtime.status()['status'], 'STOPPED')
            private = runtime.runtime / 'pgpass'
            private.write_text('synthetic')
            private.chmod(0o600)
            with self.assertRaisesRegex(Blocked, 'SECRET_FILES_REMAIN'):
                runtime.status()
            runtime.stop()
            self.assertFalse(private.exists())
            self.assertEqual(runtime.status()['status'], 'STOPPED')

    def test_cleanup_resume_keeps_original_receipt_telemetry_immutable(self):
        with tempfile.TemporaryDirectory() as folder:
            runtime=self.stopped_fixture(Path(folder));actual=control.private_json
            def interrupted(path,value):
                if path.name=='stopped.json':raise OSError('synthetic interruption')
                actual(path,value)
            with patch.object(control,'private_json',side_effect=interrupted),self.assertRaises(OSError):runtime.stop()
            before=(runtime.base/'receipt.json').read_bytes()
            runtime.pressure_guard.snapshot=lambda:(9000,2)
            runtime.pressure_guard.sample(enforce_warning=False)
            runtime.stop()
            self.assertEqual(before,(runtime.base/'receipt.json').read_bytes())

    def test_remove_rejects_unknown_empty_directory(self):
        with tempfile.TemporaryDirectory() as folder:
            runtime = self.stopped_fixture(Path(folder))
            runtime.stop()
            (runtime.base / 'foreign-directory').mkdir()
            with self.assertRaisesRegex(Blocked, 'UNKNOWN_DIRECTORY_OR_TYPE'):
                runtime.remove_private()
            self.assertTrue(runtime.base.exists())

    def test_supervisor_always_stops_after_guard_failure(self):
        runtime = object.__new__(control.Runtime)
        runtime.args = argparse.Namespace(hold_seconds=1)
        runtime.owner_path = Mock()
        runtime.owner_path.exists.return_value = True
        runtime.start = Mock()
        runtime.assert_live_health = Mock()
        runtime.stop = Mock()
        runtime.guard = Mock(side_effect=Blocked('SYNTHETIC_MEMORY_GUARD'))
        with self.assertRaisesRegex(Blocked, 'SYNTHETIC_MEMORY_GUARD'):
            runtime.infra_smoke()
        runtime.stop.assert_called_once()

    def test_api_uid_gid_come_from_effective_server_account(self):
        contract = {'destination': {'ttlSeconds': 1800, 'projectPrefix': 'wa110-physical-'},
                    'backend': {'commit': 'a' * 40}, 'images': {'java': 'fixed-java', 'postgres': 'fixed-postgres'}}
        args = argparse.Namespace(attempt='fixture', nonce='A' * 43)
        with patch.object(control, 'verify_package', return_value=(contract, 'b' * 64)), \
                patch.object(os, 'getuid', return_value=1234), patch.object(os, 'getgid', return_value=5678):
            runtime = control.Runtime(args, Path('/private-fixture'))
            self.assertEqual(runtime.env['WA110_API_UID'], '1234')
            self.assertEqual(runtime.env['WA110_API_GID'], '5678')
            self.assertNotIn('DOCKER_HOST', runtime.env)

    def test_infra_cleanup_refuses_open_bridge(self):
        with tempfile.TemporaryDirectory() as folder:
            runtime = self.stopped_fixture(Path(folder))
            (runtime.runtime / 'bridge-auth-private.json').write_text('{}')
            with self.assertRaisesRegex(Blocked, 'JOURNEY_CLEANUP_MODULE_REQUIRED'):
                runtime.stop()
            runtime.command.assert_not_called()

    def test_hold_health_rejects_exit_oom_and_restart(self):
        for row in [
            {'State': {'Running': False, 'OOMKilled': False}, 'RestartCount': 0},
            {'State': {'Running': True, 'OOMKilled': True}, 'RestartCount': 0},
            {'State': {'Running': True, 'OOMKilled': False}, 'RestartCount': 1}
        ]:
            runtime=object.__new__(control.Runtime);runtime.guard=Mock()
            good={'State': {'Running': True, 'OOMKilled': False}, 'RestartCount':0}
            runtime.resources=Mock(return_value=([good,row],[]))
            with self.assertRaisesRegex(Blocked,'UNHEALTHY_DURING_HOLD'):runtime.assert_live_health()

    def test_package_requires_exact_input_hash(self):
        with tempfile.TemporaryDirectory() as folder:
            base = Path(folder)
            (base / 'inputs').mkdir()
            for name in control.FIXED_FILES:
                (base / name).write_bytes(b'synthetic fixture')
            contract = {'backend': {'jarBytes': 17, 'jarSha256': hash_file(base / 'inputs/backend.jar'),
                                   'verifiedReceiptSha256': hash_file(base / 'inputs/verified-ci-receipt.json')},
                        'apk': {'publicCaSha256': hash_file(base / 'inputs/public-ca.der')}}
            (base / 'contract.json').write_bytes(receipt_bytes(contract))
            seal = {name: hash_file(base / name) for name in control.FIXED_FILES}
            (base / 'SHA256SUMS.json').write_bytes(receipt_bytes(seal))
            control.verify_package(base)
            (base / 'inputs/backend.jar').write_bytes(b'changed')
            with self.assertRaisesRegex(Blocked, 'PACKAGE_DIGEST_MISMATCH'):
                control.verify_package(base)

    @staticmethod
    def stopped_fixture(base):
        runtime = object.__new__(control.Runtime)
        runtime.base, runtime.runtime = base, base / 'runtime'
        runtime.runtime.mkdir()
        (runtime.runtime / 'docker-config').mkdir()
        (base / 'session').mkdir()
        runtime.contract = {'backend': {'jarSha256': 'a' * 64}, 'apk': {'sha256': 'b' * 64, 'publicCaSha256': 'c' * 64}}
        runtime.args = argparse.Namespace(attempt='synthetic-fixture')
        runtime.binding = {'sourceSha': 'd' * 40}
        runtime.package_sha = 'e' * 64
        runtime.docker = ['not-executed']
        runtime.location = Mock()
        runtime.owner = Mock()
        runtime.resources = Mock(return_value=([], []))
        runtime.command = Mock()
        runtime.pressure_guard = control.PressureGuard()
        runtime.owned_memory = {}
        return runtime


if __name__ == '__main__':
    unittest.main()
