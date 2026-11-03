"""Lightweight pure-Python checks. No Docker, JVM, browser, network or phone."""
import ast
import base64
import copy
import json
import os
from pathlib import Path
import tempfile
import unittest

import plan


def private_config():
    # Reserved, artificial aliases; never sent or inserted in a product fixture.
    return {'instance': 'wa120-offline-only', 'startsAt': '2026-11-01T12:00:00Z',
            'endsAt': '2026-11-01T12:30:00Z', 'contacts': [{'label': 'A', 'pn': '000000000000@s.whatsapp.net'}],
            'secrets': {key: base64.urlsafe_b64encode(bytes([index]) * 32).decode().rstrip('=')
                        for index, key in enumerate(sorted(plan.SECRETS), 1)}}


def prepared():
    contract, images = plan.load_contract()
    contract['backend'].update(gateAndSameArtifactSmoke='PENDING', jarSha256=None, jarBytes=None,
                               verifiedCiReceiptSha256=None, verificationPins=None)
    return plan.build_plan('offline-test', 'cc33af32-89c7-45bd-bab8-8d7fb42f94d7', 1000, 1000, (contract, images))


def snapshot_for(value):
    """Synthetic Docker inspection shapes only, with no real service identities."""
    registry = {key: {} for key in ('containers', 'networks', 'volumes')}
    snapshot = {key: [] for key in registry}
    snapshot['volumeConsumers'] = {}
    ids = {key: format(index, '064x') for index, key in enumerate(value['containers'], 1)}
    at = '2026-11-01T12:00:00.000000000Z'
    for key, spec in value['containers'].items():
        labels = {**spec['labels'], 'org.opencontainers.image.title': 'fixture-only'}
        registry['containers'][key] = {'id': ids[key], 'createdAt': at, 'labels': labels}
        mode = 'container:' + ids['evolution'] if key == 'worker' else value['networks'][spec['networks'][0]]['name']
        snapshot['containers'].append({'Id': ids[key], 'Name': '/' + spec['name'], 'Created': at,
            'Config': {'Labels': labels, 'Image': spec['image'], 'User': spec['user'], 'Cmd': spec['command']},
            'HostConfig': {'PortBindings': {}, 'Privileged': False, 'LogConfig': {'Type': 'none'},
                'CapAdd': None, 'Devices': [], 'DeviceRequests': None, 'PidMode': '', 'UTSMode': '',
                'IpcMode': 'private', 'SecurityOpt': ['no-new-privileges:true'],
                'RestartPolicy': {'Name': 'no'}, 'Memory': spec['memoryBytes'], 'MemorySwap': spec['memoryBytes'],
                'NanoCpus': spec['nanoCpus'], 'PidsLimit': spec['pidsLimit'], 'ReadonlyRootfs': spec['readOnly'],
                'NetworkMode': mode},
            'NetworkSettings': {'Ports': {'8080/tcp': None},
                'Networks': {value['networks'][net]['name']: {} for net in spec['networks']}},
            'Mounts': [{'Type': m['type'], 'Destination': m['target'], 'RW': not m['readOnly'],
                        ('Source' if m['type'] == 'bind' else 'Name'): m['source']} for m in spec['mounts']]})
    for index, (key, spec) in enumerate(value['networks'].items(), 100):
        identity = format(index, '064x')
        registry['networks'][key] = {'id': identity, 'createdAt': at, 'labels': spec['labels']}
        snapshot['networks'].append({'Id': identity, 'Name': spec['name'], 'Created': at, 'Labels': spec['labels'],
            'Driver': 'bridge', 'Scope': 'local', 'Internal': spec['internal'],
            'Containers': {ids[service]: {} for service, container in value['containers'].items() if key in container['networks']}})
    for key, spec in value['volumes'].items():
        registry['volumes'][key] = {'id': spec['name'], 'createdAt': at, 'labels': spec['labels']}
        snapshot['volumes'].append({'Name': spec['name'], 'CreatedAt': at, 'Labels': spec['labels'],
                                    'Driver': 'local', 'Scope': 'local', 'Options': None})
        snapshot['volumeConsumers'][spec['name']] = [ids[service] for service, container in value['containers'].items()
            if any(mount['type'] == 'volume' and mount['source'] == spec['name'] for mount in container['mounts'])]
    return registry, snapshot


class PrivateInputTests(unittest.TestCase):
    def test_disjoint_tokens_and_explicit_contacts(self):
        value = private_config()
        plan.validate_private(value)
        value['secrets']['webhookToken'] = value['secrets']['channelToken']
        with self.assertRaisesRegex(plan.Blocked, 'TOKENS_NOT_DISTINCT'):
            plan.validate_private(value)

    def test_window_rejects_over_30_minutes_and_naive_time(self):
        for end in ('2026-11-01T12:30:01Z', '2026-11-01T11:30:00Z', '2026-11-01T12:30:00'):
            value = private_config()
            value['endsAt'] = end
            with self.subTest(end=end), self.assertRaises(plan.Blocked):
                plan.validate_private(value)

    def test_contacts_do_not_learn_lid_or_accept_duplicates(self):
        value = private_config()
        value['contacts'].append({'label': 'B', 'pn': '000000000001@s.whatsapp.net', 'lid': '000000000001@lid'})
        plan.validate_private(value)
        value['contacts'][0]['lid'] = value['contacts'][1]['lid']
        with self.assertRaisesRegex(plan.Blocked, 'CONTACT_SCOPE_INVALID'):
            plan.validate_private(value)

    def test_private_file_owner_mode_symlink_and_duplicate_keys(self):
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / 'private.json'
            file.write_text(json.dumps(private_config()))
            file.chmod(0o600)
            self.assertEqual(plan.private_input(str(file))['instance'], 'wa120-offline-only')
            file.chmod(0o644)
            with self.assertRaisesRegex(plan.Blocked, '^PRIVATE_CONFIG_INVALID$'):
                plan.private_input(str(file))
            file.chmod(0o600)
            alias = file.with_name('link')
            alias.symlink_to(file)
            with self.assertRaises(plan.Blocked):
                plan.private_input(str(alias))
            file.write_text('{"secret":"sentinel-private-value","secret":"second"}')
            with self.assertRaisesRegex(plan.Blocked, '^PRIVATE_CONFIG_INVALID$'):
                plan.private_input(str(file))

    def test_private_render_confines_channel_and_disables_retention(self):
        value = private_config()
        files = plan.render_private_files(value)
        self.assertEqual(set(files), set(plan.SECRET_FILES))
        evo, api = files['evolution.env'], files['application.properties']
        self.assertNotIn(value['secrets']['channelToken'].encode(), evo)
        self.assertNotIn(value['secrets']['webhookToken'].encode(), evo)
        self.assertIn(b'vanrides.wa.test-cohort-only=false\n', api)
        self.assertIn(b'vanrides.wa.new-starts-enabled=false\n', api)
        for flag in ('DATABASE_SAVE_DATA_NEW_MESSAGE', 'DATABASE_SAVE_MESSAGE_UPDATE', 'DATABASE_SAVE_DATA_CONTACTS',
                     'DATABASE_SAVE_DATA_CHATS', 'DATABASE_SAVE_DATA_HISTORIC', 'DATABASE_SAVE_DATA_LABELS',
                     'DATABASE_SAVE_IS_ON_WHATSAPP', 'WEBSOCKET_ENABLED', 'TELEMETRY_ENABLED',
                     'NATS_ENABLED', 'KAFKA_ENABLED', 'PUSHER_ENABLED'):
            self.assertIn((flag + '=false\n').encode(), evo)
        self.assertIn(b'LOG_LEVEL=ERROR,WARN\n', evo)
        self.assertIn(b'LOG_BAILEYS=error\n', evo)
        ingress = json.loads(files['ingress.json'])
        self.assertEqual(ingress['channelToken'], value['secrets']['channelToken'])
        self.assertEqual(ingress['apiOrigin'], 'http://wa120-api:8080')


class TopologyTests(unittest.TestCase):
    def test_operator_idle_and_java_exec_share_one_bounded_owned_slot(self):
        value = prepared()
        operator, api = value['containers']['operator'], value['containers']['api']
        self.assertEqual(operator['command'], ['/bin/sleep', '1800'])
        self.assertEqual(operator['image'], api['image'])
        self.assertEqual(operator['memoryBytes'], api['memoryBytes'])
        self.assertEqual(operator['networks'], ['appdb'])
        self.assertEqual(operator['cliCommand'][:len(api['command'])], api['command'])
        self.assertIn('--spring.main.web-application-type=none', operator['cliCommand'])
        self.assertEqual(operator['phase'], 'BOOTSTRAP_ONLY_REMOVED_BEFORE_API_START')

    def test_plan_is_blocked_and_no_runtime_actions_are_executed(self):
        value = prepared()
        self.assertEqual(value['status'], 'PREPARADO_NAO_EXECUTAVEL')
        self.assertTrue(value['reviewOnly'])
        self.assertIsNone(value['backend']['jarSha256'])
        self.assertIn('BACKEND_GATE_SMOKE_JAR_RECEIPT_PENDING', value['blockers'])
        tree = ast.parse((plan.HERE / 'plan.py').read_text())
        names = {item.names[0].name.split('.')[0] for item in ast.walk(tree) if isinstance(item, ast.Import)}
        self.assertFalse(names & {'subprocess', 'socket', 'urllib', 'requests', 'http'})

    def test_private_networks_namespace_no_ports_and_resources(self):
        value = prepared()
        self.assertEqual({key for key, net in value['networks'].items() if not net['internal']}, {'egress'})
        self.assertEqual([key for key, spec in value['containers'].items() if 'egress' in spec['networks']], ['evolution'])
        self.assertEqual(value['containers']['worker']['networks'], [])
        self.assertTrue(value['containers']['worker']['networkMode']['generationLeaseRequired'])
        self.assertEqual(value['containers']['worker']['env']['WA070_EVOLUTION_BASE_URL'], 'http://127.0.0.1:8080')
        self.assertEqual(value['maximumMemoryMiB'], 3584)
        for argv in plan.docker_templates(value):
            self.assertFalse(any(item in ('--publish', '-p', '--publish-all', '--privileged', 'host', 'prune', 'start') for item in argv if isinstance(item, str)))
        for service, spec in value['containers'].items():
            self.assertGreater(spec['pidsLimit'], 0)
            self.assertTrue(spec['image'].split('@sha256:')[1])

    def test_plan_does_not_contain_contacts_credentials_or_fixture_seed(self):
        value = prepared()
        encoded = json.dumps(value)
        for secret in private_config()['secrets'].values():
            self.assertNotIn(secret, encoded)
        self.assertNotIn('@s.whatsapp.net', encoded)
        self.assertNotIn('seed-test-data', encoded)
        self.assertNotIn('driver-bootstrap', encoded)

    def test_generation_change_fences_worker_even_same_container_id(self):
        lease = {'evolutionId': 'a' * 64, 'evolutionStartedAt': '2026-11-01T12:00:00Z',
            'sandboxKey': '/var/run/docker/netns/test-only', 'netnsDevice': 1, 'netnsInode': 2,
            'workerNetnsDevice': 1, 'workerNetnsInode': 2,
            'workerId': 'b' * 64, 'workerNetworkMode': 'container:' + 'a' * 64}
        self.assertTrue(plan.check_namespace_lease(lease, copy.deepcopy(lease)))
        for key, changed in (('evolutionStartedAt', '2026-11-01T12:01:00Z'), ('netnsInode', 3),
                             ('workerNetworkMode', 'host'), ('workerNetnsInode', 3)):
            actual = {**lease, key: changed}
            with self.subTest(key=key), self.assertRaisesRegex(plan.Blocked, 'STOP_AND_RECREATE_WORKER'):
                plan.check_namespace_lease(lease, actual)


class OwnershipTests(unittest.TestCase):
    def test_teardown_exact_ids_and_reverse_dependency_order(self):
        value = prepared()
        registry, snapshot = snapshot_for(value)
        output = plan.teardown_plan(value, registry, snapshot)
        self.assertEqual(output['actions'][0], ['docker', 'stop', '--time', '30', registry['containers']['worker']['id']])
        self.assertNotIn('prune', json.dumps(output))
        self.assertNotIn('rm -rf', json.dumps(output))
        self.assertTrue(output['unknownAttempts'].endswith('NO_AUTOMATIC_POST'))

    def test_foreign_resource_or_mutation_blocks_entire_teardown(self):
        for changed in ('label', 'id', 'created', 'public', 'limit', 'mount', 'netns', 'network_member',
                        'volume_member', 'capabilities', 'pid_namespace'):
            value = prepared()
            registry, snapshot = snapshot_for(value)
            container = snapshot['containers'][0]
            if changed == 'label':
                container['Config']['Labels'] = {**container['Config']['Labels'], 'wa120.owner': 'foreign'}
            elif changed == 'id':
                container['Id'] = 'f' * 64
            elif changed == 'created':
                container['Created'] = 'different-generation'
            elif changed == 'public':
                container['HostConfig']['PortBindings'] = {'5432/tcp': [{'HostIp': '0.0.0.0', 'HostPort': '5432'}]}
            elif changed == 'limit':
                container['HostConfig']['Memory'] = 0
            elif changed == 'mount':
                container['Mounts'][0]['Source'] = '/foreign/private'
            elif changed == 'netns':
                next(row for row in snapshot['containers'] if row['Config']['Labels']['wa120.service'] == 'worker')['HostConfig']['NetworkMode'] = 'container:' + 'f' * 64
            elif changed == 'network_member':
                snapshot['networks'][0]['Containers']['f' * 64] = {}
            elif changed == 'capabilities':
                container['HostConfig']['CapAdd'] = ['SYS_ADMIN']
            elif changed == 'pid_namespace':
                container['HostConfig']['PidMode'] = 'host'
            else:
                snapshot['volumeConsumers'][next(iter(snapshot['volumeConsumers']))].append('f' * 64)
            with self.subTest(changed=changed), self.assertRaises(plan.Blocked):
                plan.teardown_plan(value, registry, snapshot)

    def test_partial_creation_can_be_removed_without_other_resources(self):
        value = prepared()
        registry, snapshot = snapshot_for(value)
        registry['containers'] = {}
        registry['networks'] = {}
        snapshot['containers'] = []
        snapshot['networks'] = []
        snapshot['volumeConsumers'] = {key: [] for key in snapshot['volumeConsumers']}
        output = plan.teardown_plan(value, registry, snapshot)
        self.assertEqual(len(output['actions']), 3)
        self.assertTrue(all(argv[:3] == ['docker', 'volume', 'rm'] for argv in output['actions']))


if __name__ == '__main__':
    unittest.main()
