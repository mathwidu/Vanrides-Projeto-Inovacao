#!/usr/bin/env python3
"""Only explicitly disposable synthetic files in TemporaryDirectory; no live data."""
from datetime import datetime, timedelta, timezone
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import backup_retention as R

NOW = datetime(2026, 11, 16, 12, tzinfo=timezone.utc)
OLD = NOW - timedelta(days=8)


class BackupRetentionTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='vanrides-disposable-retention-')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.root.chmod(0o700)

    def dump(self, filename):
        path = self.root / filename
        path.write_bytes(b'PGDMP synthetic disposable fixture ' + filename.encode())
        path.chmod(0o600)
        return R.sha(path.read_bytes())

    def register(self, fd, target, filename, at, known=True, restorable=True, dataset='api'):
        digest = self.dump(filename)
        return R.register(fd, target, filename, dataset, R.utc(at) if known else None,
                          digest if restorable else None, 'a' * 64 if restorable else None, at)

    def planned(self, fd, target):
        self.register(fd, target, 'old.dump', OLD)
        self.register(fd, target, 'new.dump', NOW)
        value = R.plan(fd, target, NOW)
        digest = R.write(fd, 'plan.json', value)
        R.approve(fd, target, 'plan.json', digest, 'approval.json', NOW)
        return value, digest

    def test_closed_selection_simulation_then_explicit_synthetic_purge(self):
        with R.scope(self.root) as (fd, target):
            value, digest = self.planned(fd, target)
            for name in ('external.dump', 'journal.jsonl', 'head.json'):
                self.dump(name)
            self.assertEqual([entry['name'] for entry in value['delete']], ['old.dump'])
            result = R.apply(fd, target, 'plan.json', digest, 'approval.json', now=NOW)
            self.assertEqual(result['status'], 'SIMULATION_ONLY')
            self.assertTrue((self.root / 'old.dump').exists())
            result = R.apply(fd, target, 'plan.json', digest, 'approval.json', execute=True, now=NOW)
            self.assertEqual(result['deletedCount'], 1)
            self.assertFalse((self.root / 'old.dump').exists())
            self.assertTrue(all((self.root / name).exists() for name in ('new.dump', 'external.dump', 'journal.jsonl', 'head.json')))
            catalog, _ = R.catalogue(fd, target)
            self.assertEqual([entry['name'] for entry in catalog['backups']], ['new.dump'])
            self.assertEqual(catalog['backups'][0]['recordedAt'], R.utc(NOW))
            for path in self.root.glob('*.json'):
                self.assertEqual(path.stat().st_mode & 0o777, 0o600)

    def test_legacy_age_uses_stable_registration_floor_not_filename_or_ctime(self):
        with R.scope(self.root) as (fd, target):
            self.register(fd, target, 'old-19990101.dump', NOW, known=False, restorable=False)
            self.register(fd, target, 'fresh.dump', NOW)
            initial = R.plan(fd, target, NOW)
            legacy = next(entry for entry in initial['keep'] if entry['createdAt'] is None)
            self.assertEqual(legacy['ageBasis'], 'UNKNOWN_CREATION_REGISTRATION_FLOOR')
            self.assertEqual(legacy['retainUntil'], R.utc(NOW + timedelta(days=7)))
            self.assertEqual(initial['delete'], [])
            tomorrow = NOW + timedelta(days=1)
            self.register(fd, target, 'tomorrow.dump', tomorrow)
            unchanged = next(entry for entry in R.plan(fd, target, tomorrow)['keep'] if entry['createdAt'] is None)
            self.assertEqual((unchanged['recordedAt'], unchanged['retainUntil']), (legacy['recordedAt'], legacy['retainUntil']))

    def test_modified_plan_cannot_reuse_reviewed_hash(self):
        with R.scope(self.root) as (fd, target):
            value, digest = self.planned(fd, target)
            value['delete'] = []
            (self.root / 'plan.json').write_bytes(R.encode(value))
            with self.assertRaisesRegex(ValueError, 'PLAN_HASH_MISMATCH'):
                R.apply(fd, target, 'plan.json', digest, 'approval.json', True, NOW)
            self.assertTrue((self.root / 'old.dump').exists())

    def test_changed_content_or_same_content_replacement_blocks_before_staging(self):
        with R.scope(self.root) as (fd, target):
            _, digest = self.planned(fd, target)
            old = self.root / 'old.dump'
            old.write_bytes(b'PGDMP changed synthetic content')
            with self.assertRaisesRegex(ValueError, 'REGISTERED_BACKUP_CHANGED'):
                R.apply(fd, target, 'plan.json', digest, 'approval.json', True, NOW)
            old.unlink()
            self.dump('old.dump')
            with self.assertRaisesRegex(ValueError, 'REGISTERED_BACKUP_CHANGED'):
                R.apply(fd, target, 'plan.json', digest, 'approval.json', True, NOW)
            self.assertEqual(list(self.root.glob('.retention-purge-*')), [])

    def test_escape_symlink_and_hardlink_are_rejected(self):
        with R.scope(self.root) as (fd, target):
            self.dump('own.dump')
            for name in ('../own.dump', str(self.root / 'own.dump')):
                with self.assertRaisesRegex(ValueError, 'DIRECT_CHILD_NAME_REQUIRED'):
                    R.register(fd, target, name, 'api', now=NOW)
            (self.root / 'link.dump').symlink_to(self.root / 'own.dump')
            with self.assertRaises(OSError):
                R.register(fd, target, 'link.dump', 'api', now=NOW)
            os.link(self.root / 'own.dump', self.root / 'hard.dump')
            with self.assertRaisesRegex(ValueError, 'FILE_SIZE_OR_LINK_INVALID'):
                R.register(fd, target, 'hard.dump', 'api', now=NOW)

    def test_last_restorable_copy_is_not_deletable(self):
        with R.scope(self.root) as (fd, target):
            self.register(fd, target, 'only.dump', OLD)
            self.register(fd, target, 'unverified.dump', NOW, restorable=False)
            self.register(fd, target, 'other-database.dump', NOW, dataset='n8n')
            with self.assertRaisesRegex(ValueError, 'LAST_RESTORABLE_COPY_GENERATE_AND_RESTORE_NEW_BACKUP'):
                R.plan(fd, target, NOW)
            self.assertTrue((self.root / 'only.dump').exists())

    def test_approval_is_for_exact_list_and_cannot_authorize_a_future_plan(self):
        with R.scope(self.root) as (fd, target):
            _, digest = self.planned(fd, target)
            other = R.write(fd, 'later-plan.json', R.plan(fd, target, NOW + timedelta(hours=1)))
            with self.assertRaisesRegex(ValueError, 'EXACT_PLAN_APPROVAL_REQUIRED'):
                R.apply(fd, target, 'later-plan.json', other, 'approval.json', True, NOW + timedelta(hours=1))
            with self.assertRaisesRegex(ValueError, 'PLAN_EXPIRED_OR_SCOPE_CHANGED'):
                R.apply(fd, target, 'plan.json', digest, 'approval.json', True, NOW + timedelta(days=2))
            self.register(fd, target, 'future.dump', OLD)
            with self.assertRaisesRegex(ValueError, 'PLAN_OR_BACKUPS_CHANGED'):
                R.apply(fd, target, 'plan.json', digest, 'approval.json', True, NOW)
            self.assertTrue((self.root / 'future.dump').exists())

    def test_late_replacement_is_retained_without_any_unlink(self):
        with R.scope(self.root) as (fd, target):
            _, digest = self.planned(fd, target)
            original_rename = os.rename

            def replaced(*args, **kwargs):
                (self.root / 'old.dump').unlink()
                self.dump('old.dump')
                return original_rename(*args, **kwargs)

            with patch.object(R.os, 'rename', side_effect=replaced):
                with self.assertRaisesRegex(ValueError, 'LATE_CHANGE_RETAINED_IN_PURGE_DIRECTORY'):
                    R.apply(fd, target, 'plan.json', digest, 'approval.json', True, NOW)
            staged = list(self.root.glob('.retention-purge-*/old.dump'))
            self.assertEqual(len(staged), 1)
            self.assertTrue((self.root / 'new.dump').exists())

    def test_scope_identity_and_private_catalog_are_required(self):
        with R.scope(self.root) as (fd, target):
            _, digest = self.planned(fd, target)
            with self.assertRaisesRegex(ValueError, 'PLAN_EXPIRED_OR_SCOPE_CHANGED'):
                R.reviewed(fd, {**target, 'inode': -1}, 'plan.json', digest, NOW)
            (self.root / R.CATALOG).chmod(0o644)
            with self.assertRaisesRegex(ValueError, 'PRIVATE_OWNED_PATH_REQUIRED'):
                R.plan(fd, target, NOW)


if __name__ == '__main__':
    unittest.main()
