#!/usr/bin/env python3
"""Privacy V44 harness contract; mocks/ZIP only, never starts Java or Docker."""
import copy
import io
import json
from pathlib import Path
import tempfile
from contextlib import nullcontext
from datetime import datetime, timedelta, timezone
import unittest
from unittest.mock import Mock
from zipfile import ZipFile

from rehearse_migration import Blocked, encode, sha, write_new
import recovery_privacy as privacy
import rehearse_recovery as recovery


class ManifestTest(unittest.TestCase):
    def jar(self):
        data = io.BytesIO()
        with ZipFile(data, 'w') as archive:
            archive.writestr(privacy.LAUNCHER, b'launcher')
            for path in privacy.CLASSES: archive.writestr(path, path.encode())
        return data.getvalue()

    def test_v2_requires_exact_cli_and_real_ports_from_same_candidate(self):
        jar = self.jar()
        value = {'schemaVersion': 2, 'candidate': {'flywayVersion': 44},
                 'privacy': {'state': 'REQUIRED_V44', 'classes': {path: sha(path.encode()) for path in privacy.CLASSES}}}
        privacy.validate_manifest(value, jar)
        for mutate in (lambda v: v['candidate'].update(flywayVersion=43),
                       lambda v: v['privacy'].update(state='PASS'), lambda v: v['privacy']['classes'].pop(privacy.CLASSES[0]),
                       lambda v: v['privacy']['classes'].update({privacy.CLASSES[0]: '0' * 64})):
            changed = copy.deepcopy(value); mutate(changed)
            with self.subTest(mutate=mutate), self.assertRaises(Blocked): privacy.validate_manifest(changed, jar)

    def test_v1_keeps_pending_contract_without_privacy_side_effects(self):
        privacy.validate_manifest({'schemaVersion': 1, 'privacy': {'state': 'PENDING_V44'}}, b'not-a-jar')
        with self.assertRaises(Blocked): privacy.validate_manifest({'schemaVersion': 1, 'privacy': {'state': 'PASS'}}, b'')


class RuntimeProbeTest(unittest.TestCase):
    def session(self):
        session = Mock(); session.owned.budget.side_effect = lambda seconds: nullcontext()
        session.keys = {'SYSTEM_JOB': 'private-token'}; session.sql.return_value = 'stable-database-fingerprint'
        def http(channel, host, path, body, token=None):
            if token is None: return {'status': 401, 'body': {'code': 'UNAUTHORIZED'}}
            return {'status': 200, 'body': {'status': 'DISABLED', 'family': body['family'], 'examined': 0, 'erased': 0,
                'deleted': 0, 'deferred': 0, 'wouldChange': 0, 'complete': False, 'serverTime': '2026-11-16T00:00:00Z',
                'nextCursor': None, 'reasonCodes': []}}
        session.http.side_effect = http; return session

    def test_five_families_disabled_auth401_and_database_unchanged(self):
        session = self.session(); result = privacy.probe_disabled(session)
        self.assertEqual(result['families'], list(privacy.FAMILIES)); self.assertEqual(session.http.call_count, 6)
        self.assertEqual(session.sql.call_count, 2); self.assertEqual(result['unauthenticatedStatus'], 401)

    def test_no_auth_nonzero_counters_cursor_or_mutation_cannot_pass(self):
        for mode in ('auth', 'counter', 'cursor', 'database'):
            session = self.session(); original = session.http.side_effect
            def http(*args):
                result = original(*args)
                if mode == 'auth' and result['status'] == 401: result['status'] = 200
                if result['status'] == 200 and 'examined' in result['body']:
                    if mode == 'counter': result['body']['examined'] = 1
                    if mode == 'cursor': result['body']['nextCursor'] = 'unexpected-cursor'
                return result
            session.http.side_effect = http
            if mode == 'database': session.sql.side_effect = ['before', 'after']
            with self.subTest(mode=mode), self.assertRaises(Blocked): privacy.probe_disabled(session)


class BackupOrderingTest(unittest.TestCase):
    def test_both_old_dumps_precede_close_and_no_restore_runs_when_close_fails(self):
        for fail in (False, True):
            with self.subTest(fail=fail), tempfile.TemporaryDirectory() as directory:
                session = object.__new__(recovery.Session); session.output = Path(directory).resolve()
                session.value = {'schemaVersion': 2}; session.owned = Mock(); trace = []
                session.sql = Mock(side_effect=lambda pg, db, sql: '2|0' if 'wa.test_cohort' in sql else 'wa.profile|3|abcdef')
                def execute(role, command, data=None, **options):
                    if command[0] == 'pg_dump':
                        trace.append('dump:' + command[command.index('-d') + 1])
                        write_new(options['sink'], b'PGDMP-old-before-close'); return b'PGDMP-old-before-close'
                    trace.append('restore:' + command[command.index('-d') + 1]); return b''
                session.owned.exec.side_effect = execute
                def close():
                    trace.append('close-and-current-journal')
                    if fail: raise Blocked('SOURCE_ERASURE_UNVERIFIED')
                if fail:
                    with self.assertRaises(Blocked): session.backup_restore('source-pg', 'restored-pg', {}, close)
                else: session.backup_restore('source-pg', 'restored-pg', {}, close)
                expected = ['dump:vanrides_wa070', 'dump:n8n_recovery', 'close-and-current-journal']
                if not fail: expected += ['restore:vanrides_wa070', 'restore:n8n_recovery']
                self.assertEqual(trace, expected)


class LifecycleTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(); self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve(); self.owned = Mock(); self.owned.nonce = 'a' * 32
        self.owned.containers = {name: {'id': str(i).zfill(64)} for i, name in enumerate(
            ('source-pg', 'restored-pg', 'source-api', 'source-wa', 'source-main', 'source-n8n-worker'), 1)}
        self.owned.check.side_effect = lambda role: self.owned.containers[role]['id']
        self.owned.budget.side_effect = lambda seconds: nullcontext()
        self.owned.run.side_effect = lambda args, **limits: b'0' if args[0] == 'wait' else b'false'
        self.session = Mock(); self.session.owned = self.owned; self.session.output = self.root; self.session.uid = '1000:1000'
        self.session.value = {'schemaVersion': 2, 'images': {'apiRuntime': 'pinned-runtime'}, 'candidate': {'jarSha256': sha(b'candidate')}}
        self.session.keys = {'postgres': 'A' * 43}; write_new(self.root / 'candidate.jar', b'candidate')
        self.run = privacy.PrivacyRecovery(self.session); self.owned.create.side_effect = self.fake_command

    def fake_command(self, role, image, options, command):
        name = command[-1].removeprefix('/privacy/').removesuffix('.request.json')
        base = {'schemaVersion': 1, 'environment': 'wa070-local', 'synthetic': True, 'releaseEligible': False}
        if name == 'prepare':
            base.update(status='PREPARED', ready=False, fixtureReady=True)
            write_new(self.run.directory / 'fixture.json', encode({'schemaVersion': 1, 'nonce': self.owned.nonce,
                'baseAt': '2026-11-16T00:00:00Z', 'coreDigest': 'c' * 64, 'betaDigest': 'b' * 64}))
        else:
            base.update(status='SOURCE_ERASURE_VERIFIED' if name == 'close_purge' else 'RESTORED_ERASURE_VERIFIED', ready=True,
                head={'sequence': 1, 'digest': 'f' * 64}, maintenance=dict.fromkeys(('terminalErased', 'technicalChanged', 'historyDeleted', 'historyProjected'), 1),
                alphaAbsent=True, betaPreserved=True, corePreserved=True, originalClosureAndAckPreserved=True, journalUnchangedAfterPurge=True)
            if name == 'close_purge':
                write_new(self.run.directory / 'journal' / 'erasures.jsonl', b'authenticated-synthetic-journal\n')
                write_new(self.run.directory / 'journal' / 'head.json', encode(base['head']))
        write_new(self.run.directory / (name + '.result.json'), encode(base))
        cid = str(len(self.owned.containers) + 1).zfill(64); self.owned.containers[role] = {'id': cid}; return cid

    def test_real_command_protocol_keeps_credentials_in_private_files_and_reapplies_only_restored(self):
        self.run.prepare(); self.run.close_purge(); result = self.run.reapply()
        self.assertEqual(result['state'], 'V44_REAPPLIED_VERIFIED'); self.assertTrue(result['absenceVerified'])
        self.assertEqual(result['source']['head'], result['restored']['head'])
        sql = self.session.sql.call_args.args[2]; self.assertNotIn(self.run.credential, sql)
        for call in self.owned.create.call_args_list:
            role, image, options, command = call.args
            self.assertNotIn(self.run.credential, repr(options + command)); self.assertNotIn(self.session.keys['postgres'], repr(options + command))
            self.assertIn('--read-only', options); self.assertEqual(command[-2], 'org.springframework.boot.loader.launch.PropertiesLauncher')
            request = self.run.directory / Path(command[-1]).name
            self.assertEqual(request.stat().st_mode & 0o777, 0o600)
        restored_options = self.owned.create.call_args.args[2]
        self.assertIn('container:' + self.owned.containers['restored-pg']['id'], restored_options)
        self.assertEqual((self.run.directory / 'retained-head.json').stat().st_mode & 0o777, 0o600)

    def test_running_source_consumer_blocks_before_credential_or_fixture_mutation(self):
        self.owned.run.return_value = b'true'; self.owned.run.side_effect = None
        with self.assertRaisesRegex(Blocked, 'TRAFFIC_NOT_BLOCKED'): self.run.prepare()
        self.session.sql.assert_not_called(); self.owned.create.assert_not_called()

    def test_external_pass_or_wrong_database_cannot_be_used_as_proof(self):
        self.run.directory.mkdir(mode=0o700)
        write_new(self.run.directory / 'prepare.result.json', encode({'status': 'PASS'}))
        with self.assertRaisesRegex(Blocked, 'EXTERNAL_RESULT'): self.run.command('PREPARE', 'source-pg')
        with self.assertRaisesRegex(Blocked, 'TARGET_INVALID'): self.run.command('REAPPLY_PURGE', 'source-pg')
        self.owned.create.assert_not_called()

    def test_missing_tampered_current_journal_or_changed_retained_head_blocks_before_new_cli(self):
        self.run.prepare(); self.run.close_purge(); baseline = self.owned.create.call_count
        (self.run.directory / 'journal' / 'erasures.jsonl').write_bytes(b'tampered\n')
        with self.assertRaisesRegex(Blocked, 'CURRENT_JOURNAL_CHANGED'): self.run.reapply()
        self.assertEqual(self.owned.create.call_count, baseline)

    def test_expiry_preview_never_deletes_dump_or_durable_journal(self):
        self.run.prepare(); self.run.close_purge()
        dump = self.root / 'vanrides_wa070.dump'; write_new(dump, b'PGDMP-synthetic')
        after = datetime.fromtimestamp(dump.stat().st_ctime, timezone.utc) + timedelta(days=8)
        inventory = privacy.artifact_inventory(self.root, after)
        backup = next(row for row in inventory['artifacts'] if row['classification'] == 'database-backup')
        self.assertTrue(backup['expiryPreview']); self.assertFalse(backup['deletionImplemented']); self.assertTrue(dump.exists())
        journal = next(row for row in inventory['artifacts'] if row['classification'] == 'erasure-journal')
        self.assertIsNone(journal['retainUntil']); self.assertFalse(inventory['physicalBackupRotationVerified'])


if __name__ == '__main__': unittest.main()
