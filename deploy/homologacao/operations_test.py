#!/usr/bin/env python3
"""CLI/plan contract with fake executor only; never proves API pause or runs Docker."""
import copy
import json
import os
from pathlib import Path
from subprocess import TimeoutExpired
import tempfile
import unittest
from unittest.mock import patch
import operations as O


def value():
    return {'schemaVersion': 1, 'environment': O.ENVIRONMENT, 'instance': O.INSTANCE,
            'apiImage': O.API_REPOSITORY + '@sha256:' + 'a' * 64, 'schema': 44}


class Clock:
    elapsed = 0
    def __call__(self): return self.elapsed


class Executor:
    def __init__(self):
        self.calls = []; self.clock = Clock(); self.duration = 0
        self.metrics = dict.fromkeys(O.METRICS, 0); self.metrics.update(dict.fromkeys(O.ROLES, 1)); self.metrics['schemaVersion'] = 44
        self.changed_image = False; self.health = {'status': 'UP'}; self.target = {'target': True, 'technicalBatch': True}
        self.error = None

    def __call__(self, args, data=None, **limits):
        self.calls.append((args, data, limits))
        if self.duration > limits['timeout']:
            self.clock.elapsed += limits['timeout']; raise TimeoutExpired('private-command', limits['timeout'], stderr=b'PRIVATE_TOKEN')
        self.clock.elapsed += self.duration
        if self.error: raise self.error
        if args[:2] == ['service', 'inspect']:
            role = next(role for role, target in O.TARGETS.items() if target[0] == args[-1])
            index = list(O.TARGETS).index(role); name, namespace = O.TARGETS[role]
            image = value()['apiImage'] if role == 'api' else O.NODE if role == 'worker' else O.POSTGRES
            return O.encode({'id': chr(97 + index) * 25, 'name': name, 'namespace': namespace,
                'image': 'foreign:latest' if self.changed_image else image, 'replicas': 1})
        if args[0] == 'ps':
            index = ord(args[3].split('=')[-1][0]) - 97
            return (str(index + 1) * 64 + '\n').encode()
        if args[0] == 'inspect':
            index = int(args[-1][0]) - 1; role = list(O.TARGETS)[index]; name, namespace = O.TARGETS[role]
            image = value()['apiImage'] if role == 'api' else O.NODE if role == 'worker' else O.POSTGRES
            return O.encode({'id': args[-1], 'image': image, 'running': True, 'service': chr(97 + index) * 25,
                             'name': name, 'namespace': namespace})
        if 'psql' in args:
            return O.encode(self.metrics if data.decode() == O.AGGREGATES else self.target)
        if 'wget' in args:
            # Swarm DNS contains an underscore; Tomcat rejects that default Host.
            header = args.index('--header') if '--header' in args else -1
            if header < 0 or args[header + 1] != 'Host: vanrides-inovacao-api.matheusduarte.dev.br':
                raise O.Blocked('OPS_API_INVALID_HOST')
            return O.encode(self.health)
        if 'node' in args: return O.encode({'status': 'UP', 'role': 'WA070_WORKER'})
        raise AssertionError('Unexpected command')


class OperationsTest(unittest.TestCase):
    def operation(self, runner=None):
        runner = runner or Executor(); return O.Operations(runner, value(), runner.clock), runner

    def test_healthy_report_is_aggregate_and_does_not_claim_channel_schedule_or_stop_proof(self):
        operation, runner = self.operation(); result = operation.diagnose()
        self.assertEqual(result['status'], 'TECHNICAL_CHECKS_OK'); self.assertEqual(result['alerts'], [])
        for key in ('mutationsExecuted', 'whatsappDeliveryVerified', 'n8nSchedulesVerified', 'stopAckVerified'):
            self.assertIs(result[key], False)
        for args, data, limits in runner.calls:
            self.assertLessEqual(limits['timeout'], 6)
            self.assertEqual(limits['maximum'], 16000)
            self.assertNotIn('Env', repr(args)); self.assertNotIn('Args', repr(args)); self.assertNotIn('Command', repr(args))
            self.assertNotIn('logs', args); self.assertNotIn('update', args); self.assertNotIn('restart', args)
            if data: self.assertIn('READ ONLY', data.decode())

    def test_failed_monitor_has_fixed_operator_action_without_returning_raw_body(self):
        operation, runner = self.operation(); runner.health = {'status': 'DOWN', 'secret': 'PRIVATE_TOKEN 5511999999999'}
        result = operation.diagnose(); text = json.dumps(result)
        self.assertEqual(result['status'], 'ATTENTION'); self.assertNotIn('PRIVATE_TOKEN', text); self.assertNotIn('5511', text)
        alert = next(a for a in result['alerts'] if a['code'] == 'API_UNHEALTHY')
        self.assertIn('STOP', alert['action'])

    def test_image_or_owner_drift_prevents_exec_and_returns_only_scope_alert(self):
        operation, runner = self.operation(); runner.changed_image = True
        result = operation.diagnose()
        self.assertEqual(len(result['alerts']), 3); self.assertIsNone(result['counts'])
        self.assertFalse(any(args[0] == 'exec' for args, _, _ in runner.calls))

    def test_foreign_database_or_extra_private_columns_fail_closed(self):
        for change in ({'foreignScope': 1}, {'token': 'PRIVATE_TOKEN'}):
            operation, runner = self.operation(); runner.metrics.update(change)
            result = operation.diagnose()
            self.assertIsNone(result['counts']); self.assertNotIn('PRIVATE_TOKEN', json.dumps(result))
            self.assertIn('DATABASE_SCOPE_OR_QUERY_UNVERIFIED', [a['code'] for a in result['alerts']])

    def test_unknown_inflight_stop_and_credentials_have_distinct_actions(self):
        operation, runner = self.operation()
        runner.metrics.update(unknown=2, submitting=1, staleSubmitting=1, stopOld=1, expiringCredentials=1, delivery=0)
        result = operation.diagnose(); codes = {a['code'] for a in result['alerts']}
        self.assertTrue({'DELIVERY_UNKNOWN', 'INFLIGHT_MAY_COMPLETE_25S', 'STOP_ACK_PENDING_OVER_60S',
                         'CREDENTIAL_EXPIRING_7D', 'DELIVERY_PAUSED_OR_AMBIGUOUS'} <= codes)
        self.assertIn('não reenviar', next(a['action'] for a in result['alerts'] if a['code'] == 'DELIVERY_UNKNOWN'))

    def test_absolute_deadline_limits_total_wall_clock_and_redacts_timeout_details(self):
        operation, runner = self.operation(); runner.duration = 4
        result = operation.diagnose()
        self.assertEqual(runner.clock.elapsed, 45)
        self.assertEqual(runner.calls[-1][2]['timeout'], 1)
        self.assertEqual(result['status'], 'ATTENTION'); self.assertNotIn('PRIVATE_TOKEN', json.dumps(result))

    def test_pause_plan_preserves_stop_and_recovery_roles_without_mutating_any_service(self):
        operation, runner = self.operation(); report = operation.diagnose()
        request = {'credentialId': '11111111-1111-4111-8111-111111111111', 'batchId': '22222222-2222-4222-8222-222222222222'}
        result = operation.plan_pause(request, 'c' * 64, report)
        self.assertEqual(result['status'], 'PAUSE_PLAN_READY_NOT_EXECUTED')
        self.assertEqual(result['preserveRoles'], ['CHANNEL', 'CONVERSATION_WORKER', 'SYSTEM_JOB', 'DEVICE'])
        self.assertIn('APP_STOP_ACK', result['preservePaths']); self.assertIn('W08_HEALTH_RECONCILE', result['preservePaths'])
        self.assertTrue(result['revocationPermanent']); self.assertFalse(result['mutationsExecuted'])
        self.assertIn('NEW_DELIVERY_CREDENTIAL', result['resume'])
        self.assertNotIn(request['credentialId'], json.dumps(result)); self.assertNotIn(request['batchId'], json.dumps(result))
        self.assertTrue(all(args[0] in ('service', 'ps', 'inspect', 'exec') for args, _, _ in runner.calls))

    def test_wrong_principal_batch_duplicate_delivery_or_missing_job_cannot_produce_pause_plan(self):
        request = {'credentialId': '11111111-1111-4111-8111-111111111111', 'batchId': '22222222-2222-4222-8222-222222222222'}
        for mode in ('role', 'batch', 'duplicate', 'job'):
            operation, runner = self.operation()
            if mode == 'role': runner.target['target'] = False
            if mode == 'batch': runner.target['technicalBatch'] = False
            if mode == 'duplicate': runner.metrics['delivery'] = 2
            if mode == 'job': runner.metrics['job'] = 0
            with self.subTest(mode=mode), self.assertRaises(O.Blocked): operation.plan_pause(request, 'a'*64, operation.diagnose())


class FileContractTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(); self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve(); self.root.chmod(0o700)

    def write(self, name, value):
        path = self.root / name; O.write_new(path, O.encode(value)); return path

    def test_manifest_is_exact_hash_bound_and_has_no_custom_target_or_url(self):
        path = self.write('manifest.json', value()); O.manifest(path, O.sha(path.read_bytes()))
        for changed in ({**value(), 'environment': 'production'}, {**value(), 'url': 'http://foreign'},
                        {**value(), 'apiImage': 'foreign:latest'}):
            new = self.write(str(len(list(self.root.iterdir())))+'.json', changed)
            with self.assertRaises(O.Blocked): O.manifest(new, O.sha(new.read_bytes()))
        with self.assertRaises(O.Blocked): O.manifest(path, '0'*64)

    def test_private_pause_request_rejects_secrets_other_operations_and_permissions(self):
        request = {'operation': 'revoke-service-credential', 'environment': O.ENVIRONMENT,
            'batchId': '22222222-2222-4222-8222-222222222222', 'credentialId': '11111111-1111-4111-8111-111111111111', 'reasonCode': 'OPERATIONAL_PAUSE'}
        path = self.write('request.json', request); self.assertEqual(O.pause_request(path)[0], request)
        path.chmod(0o644)
        with self.assertRaises(O.Blocked): O.pause_request(path)
        for change in ({'operatorCredential': 'PRIVATE_TOKEN'}, {'operation': 'reset-test-data'}, {'credentialId': "';DELETE"}):
            bad = self.write(str(len(list(self.root.iterdir())))+'.json', {**request, **change})
            with self.assertRaises((O.Blocked, ValueError)): O.pause_request(bad)

    def test_main_emits_no_raw_error_or_arguments_when_preflight_fails(self):
        path = self.write('manifest.json', value())
        with patch.object(O, 'host_identity', side_effect=O.Blocked('PRIVATE_TOKEN')), patch('builtins.print') as output:
            code = O.main(['diagnose', '--manifest', str(path), '--sha256', O.sha(path.read_bytes()), '--output', str(self.root/'out')])
        self.assertEqual(code, 2); self.assertNotIn('PRIVATE_TOKEN', output.call_args.args[0])


if __name__ == '__main__': unittest.main()
