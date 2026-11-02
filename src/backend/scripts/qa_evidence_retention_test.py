#!/usr/bin/env python3
from __future__ import annotations

import json
import hashlib
import io
import os
import shutil
import tempfile
import unittest
from contextlib import redirect_stdout
from datetime import datetime, timedelta, timezone
from pathlib import Path
from unittest.mock import patch

from qa_evidence_retention import (
    RetentionValidationError,
    apply_retention_plan,
    build_retention_plan,
    main,
)


class QaEvidenceRetentionTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name) / "qa-evidence"
        self.root.mkdir(mode=0o700)
        self.now = datetime(2026, 7, 18, 12, 0, tzinfo=timezone.utc)

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def test_local_policy_keeps_thirty_days_and_twenty_newest_per_suite(self) -> None:
        for index in range(25):
            self._write_manifest(
                f"rbac-old-{index:02d}",
                suite="rbac",
                archived_at=self.now - timedelta(days=60 - index),
            )
        for index in range(2):
            self._write_manifest(
                f"rbac-recent-{index:02d}",
                suite="rbac",
                archived_at=self.now - timedelta(days=index + 1),
            )

        plan = build_retention_plan(self.root, now=self.now)

        self.assertEqual(
            {f"rbac-old-{index:02d}" for index in range(7)},
            set(plan.delete_run_ids),
        )
        self.assertEqual(27, plan.scanned_count)

    def test_floor_is_independent_per_suite(self) -> None:
        for suite in ("rbac", "integration"):
            for index in range(21):
                self._write_manifest(
                    f"{suite}-old-{index:02d}",
                    suite=suite,
                    archived_at=self.now - timedelta(days=80 - index),
                    status="failed" if index == 0 else "passed",
                )

        plan = build_retention_plan(self.root, now=self.now)

        self.assertEqual(
            {"rbac-old-00", "integration-old-00"},
            set(plan.delete_run_ids),
        )

    def test_exactly_thirty_days_is_preserved(self) -> None:
        for index in range(20):
            self._write_manifest(
                f"rbac-newer-{index:02d}",
                suite="rbac",
                archived_at=self.now - timedelta(days=index),
            )
        self._write_manifest(
            "rbac-threshold",
            suite="rbac",
            archived_at=self.now - timedelta(days=30),
        )

        plan = build_retention_plan(self.root, now=self.now)

        self.assertNotIn("rbac-threshold", plan.delete_run_ids)
        self.assertEqual("within-retention-window", plan.protected_reasons["rbac-threshold"])

    def test_protects_ci_release_unknown_legacy_missing_and_malformed_manifests(self) -> None:
        old = self.now - timedelta(days=90)
        self._write_manifest("ci-run", suite="rbac", archived_at=old, retention="ci")
        self._write_manifest("release-run", suite="rbac", archived_at=old, retention="release")
        self._write_manifest("unknown-run", suite="rbac", archived_at=old, retention="future")
        self._write_manifest(
            "legacy-run",
            suite="rbac",
            archived_at=old,
            include_retention=False,
            schema_version=1,
        )
        self._write_manifest(
            "legacy-labelled-run",
            suite="rbac",
            archived_at=old,
            retention="local",
            schema_version=1,
        )
        (self.root / "missing-run").mkdir()
        malformed = self.root / "malformed-run"
        malformed.mkdir()
        (malformed / "manifest.json").write_text("{broken", encoding="utf-8")
        structurally_invalid = self.root / "structurally-invalid-run"
        structurally_invalid.mkdir()
        (structurally_invalid / "manifest.json").write_text(
            json.dumps({
                "schemaVersion": 2,
                "status": "passed",
                "suite": "rbac",
                "runId": "structurally-invalid-run",
                "startedAtEpoch": int(old.timestamp()) - 60,
                "archivedAtUtc": old.isoformat(),
                "retentionClass": "local",
                "eligibleForRelease": False,
            }),
            encoding="utf-8",
        )

        plan = build_retention_plan(self.root, now=self.now)

        self.assertEqual((), plan.delete_run_ids)
        self.assertEqual("protected-retention-class", plan.protected_reasons["ci-run"])
        self.assertEqual("protected-retention-class", plan.protected_reasons["release-run"])
        self.assertEqual("unknown-retention-class", plan.protected_reasons["unknown-run"])
        self.assertEqual("unknown-retention-class", plan.protected_reasons["legacy-run"])
        self.assertEqual(
            "unknown-retention-class",
            plan.protected_reasons["legacy-labelled-run"],
        )
        self.assertEqual("manifest-missing", plan.protected_reasons["missing-run"])
        self.assertEqual("manifest-invalid", plan.protected_reasons["malformed-run"])
        self.assertEqual(
            "manifest-invalid",
            plan.protected_reasons["structurally-invalid-run"],
        )

    def test_protects_active_run_and_nonterminal_status(self) -> None:
        old = self.now - timedelta(days=90)
        for index in range(20):
            self._write_manifest(
                f"rbac-newer-{index:02d}",
                suite="rbac",
                archived_at=self.now - timedelta(days=index),
            )
        self._write_manifest("active-run", suite="rbac", archived_at=old)
        self._write_manifest(
            "in-progress-run",
            suite="rbac",
            archived_at=old - timedelta(days=1),
            status="running",
        )

        plan = build_retention_plan(
            self.root,
            now=self.now,
            active_run_ids={"active-run"},
        )

        self.assertNotIn("active-run", plan.delete_run_ids)
        self.assertEqual("active-run", plan.protected_reasons["active-run"])
        self.assertEqual("nonterminal-status", plan.protected_reasons["in-progress-run"])

    def test_symlink_directory_manifest_and_nested_symlink_are_never_removed(self) -> None:
        outside = Path(self.temp_dir.name) / "outside"
        outside.mkdir()
        (self.root / "linked-run").symlink_to(outside, target_is_directory=True)

        manifest_link_run = self.root / "manifest-link-run"
        manifest_link_run.mkdir()
        target_manifest = Path(self.temp_dir.name) / "outside-manifest.json"
        target_manifest.write_text("{}", encoding="utf-8")
        (manifest_link_run / "manifest.json").symlink_to(target_manifest)

        nested_link_run = self._write_manifest(
            "nested-link-run",
            suite="rbac",
            archived_at=self.now - timedelta(days=90),
        )
        (nested_link_run / "nested-link").symlink_to(outside, target_is_directory=True)

        plan = build_retention_plan(self.root, now=self.now)

        self.assertEqual("symlink-run", plan.protected_reasons["linked-run"])
        self.assertEqual("manifest-symlink", plan.protected_reasons["manifest-link-run"])
        self.assertEqual("contains-symlink", plan.protected_reasons["nested-link-run"])
        self.assertEqual((), plan.delete_run_ids)

    def test_dry_run_plan_never_deletes_and_apply_removes_only_planned_temporary_runs(self) -> None:
        for index in range(21):
            self._write_manifest(
                f"rbac-old-{index:02d}",
                suite="rbac",
                archived_at=self.now - timedelta(days=80 - index),
            )
        protected = self._write_manifest(
            "release-run",
            suite="rbac",
            archived_at=self.now - timedelta(days=100),
            retention="release",
        )

        plan = build_retention_plan(self.root, now=self.now)

        self.assertTrue((self.root / "rbac-old-00").is_dir())
        removed = apply_retention_plan(plan)

        self.assertEqual(("rbac-old-00",), removed)
        self.assertFalse((self.root / "rbac-old-00").exists())
        self.assertTrue(protected.is_dir())
        self.assertTrue((self.root / "rbac-old-01").is_dir())

    def test_cli_is_dry_run_by_default(self) -> None:
        archived_at = datetime(2020, 1, 1, tzinfo=timezone.utc)
        for index in range(21):
            self._write_manifest(
                f"rbac-cli-{index:02d}",
                suite="rbac",
                archived_at=archived_at + timedelta(minutes=index),
            )
        stdout = io.StringIO()

        with redirect_stdout(stdout):
            exit_code = main(["--root", str(self.root)])

        self.assertEqual(0, exit_code)
        self.assertTrue((self.root / "rbac-cli-00").is_dir())
        self.assertIn("WOULD_REMOVE: rbac-cli-00", stdout.getvalue())
        self.assertIn("mode=dry-run", stdout.getvalue())

    def test_apply_revalidates_manifest_and_aborts_before_any_delete_on_drift(self) -> None:
        for index in range(22):
            self._write_manifest(
                f"rbac-old-{index:02d}",
                suite="rbac",
                archived_at=self.now - timedelta(days=90 - index),
            )
        plan = build_retention_plan(self.root, now=self.now)
        changed = self.root / plan.delete_run_ids[0] / "manifest.json"
        changed.write_text(
            json.dumps({"retentionClass": "release"}),
            encoding="utf-8",
        )

        with self.assertRaisesRegex(RetentionValidationError, "changed after planning"):
            apply_retention_plan(plan)

        for run_id in plan.delete_run_ids:
            self.assertTrue((self.root / run_id).is_dir())

    def test_apply_revalidates_report_bytes_before_any_delete(self) -> None:
        for index in range(22):
            self._write_manifest(
                f"rbac-report-drift-{index:02d}",
                suite="rbac",
                archived_at=self.now - timedelta(days=90 - index),
            )
        plan = build_retention_plan(self.root, now=self.now)
        report = next(plan.candidates[0].path.glob("TEST-*.xml"))
        report.write_bytes(b"changed after planning\n")

        with self.assertRaisesRegex(RetentionValidationError, "changed after planning"):
            apply_retention_plan(plan)

        for run_id in plan.delete_run_ids:
            self.assertTrue((self.root / run_id).is_dir())

    def test_apply_quarantine_preserves_recreated_canonical_run(self) -> None:
        for index in range(21):
            self._write_manifest(
                f"rbac-recreate-{index:02d}",
                suite="rbac",
                archived_at=self.now - timedelta(days=90 - index),
            )
        plan = build_retention_plan(self.root, now=self.now)
        candidate = plan.candidates[0]
        real_rmtree = shutil.rmtree

        def recreate_then_remove(quarantined_name: str) -> None:
            recreated = self.root / candidate.run_id
            recreated.mkdir()
            (recreated / "new-run.marker").write_text("new inode\n", encoding="utf-8")
            real_rmtree(quarantined_name)

        with patch(
            "qa_evidence_retention.shutil.rmtree",
            side_effect=recreate_then_remove,
        ):
            removed = apply_retention_plan(plan)

        self.assertEqual((candidate.run_id,), removed)
        self.assertEqual(
            "new inode\n",
            (self.root / candidate.run_id / "new-run.marker").read_text(
                encoding="utf-8"
            ),
        )

    def test_apply_never_deletes_candidate_replaced_immediately_before_rename(
        self,
    ) -> None:
        for index in range(21):
            self._write_manifest(
                f"rbac-swap-{index:02d}",
                suite="rbac",
                archived_at=self.now - timedelta(days=90 - index),
            )
        plan = build_retention_plan(self.root, now=self.now)
        candidate = plan.candidates[0]
        preserved_original = Path(self.temp_dir.name) / "preserved-original"
        real_rename = os.rename
        swapped = False

        def swap_before_rename(
            source: object,
            destination: object,
            *args: object,
            **kwargs: object,
        ) -> None:
            nonlocal swapped
            if source == candidate.run_id and not swapped:
                swapped = True
                real_rename(candidate.path, preserved_original)
                candidate.path.mkdir()
                (candidate.path / "replacement.marker").write_text(
                    "replacement inode\n",
                    encoding="utf-8",
                )
            real_rename(source, destination, *args, **kwargs)

        with patch("qa_evidence_retention.os.rename", side_effect=swap_before_rename):
            with self.assertRaisesRegex(
                RetentionValidationError,
                "preserved in quarantine for manual recovery",
            ):
                apply_retention_plan(plan)

        self.assertTrue(preserved_original.is_dir())
        self.assertFalse(candidate.path.exists())
        quarantine_root = self.root.parent / ".qa-evidence-retention-quarantine"
        quarantined = tuple(quarantine_root.iterdir())
        self.assertEqual(1, len(quarantined))
        self.assertEqual(
            "replacement inode\n",
            (quarantined[0] / "replacement.marker").read_text(encoding="utf-8"),
        )

    def test_terminal_bundle_with_unexpected_file_is_protected(self) -> None:
        passed = self._write_manifest(
            "passed-with-extra",
            suite="rbac",
            archived_at=self.now - timedelta(days=90),
        )
        failed = self._write_manifest(
            "failed-with-extra",
            suite="rbac",
            archived_at=self.now - timedelta(days=90),
            status="failed",
        )
        (passed / "unexpected.txt").write_text("unexpected\n", encoding="utf-8")
        (failed / "unexpected.txt").write_text("unexpected\n", encoding="utf-8")

        plan = build_retention_plan(self.root, now=self.now)

        self.assertEqual("manifest-invalid", plan.protected_reasons[passed.name])
        self.assertEqual("manifest-invalid", plan.protected_reasons[failed.name])

    def test_public_execution_metadata_rejects_legacy_paths_and_freeform_values(
        self,
    ) -> None:
        old_schema = self._write_manifest(
            "rbac-legacy-public-schema",
            suite="rbac",
            archived_at=self.now - timedelta(days=90),
        )
        old_manifest_path = old_schema / "manifest.json"
        old_manifest = json.loads(old_manifest_path.read_text(encoding="utf-8"))
        old_manifest["command"] = old_manifest.pop("commandProfile")
        old_manifest["environment"] = {
            "userHome": "/private/SENTINEL-HOME",
        }
        old_manifest_path.write_text(
            json.dumps(old_manifest, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        leaked_environment = self._write_manifest(
            "integration-leaked-environment",
            suite="integration",
            archived_at=self.now - timedelta(days=91),
        )
        leaked_manifest_path = leaked_environment / "manifest.json"
        leaked_manifest = json.loads(
            leaked_manifest_path.read_text(encoding="utf-8")
        )
        leaked_manifest["environment"]["userHome"] = "/private/SENTINEL-HOME"
        leaked_manifest_path.write_text(
            json.dumps(leaked_manifest, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        freeform_profile = self._write_manifest(
            "integration-freeform-command",
            suite="integration",
            archived_at=self.now - timedelta(days=92),
        )
        profile_manifest_path = freeform_profile / "manifest.json"
        profile_manifest = json.loads(
            profile_manifest_path.read_text(encoding="utf-8")
        )
        profile_manifest["commandProfile"] = "SENTINEL-FREEFORM-COMMAND"
        profile_manifest_path.write_text(
            json.dumps(profile_manifest, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        plan = build_retention_plan(self.root, now=self.now)

        for run in (old_schema, leaked_environment, freeform_profile):
            self.assertEqual("manifest-invalid", plan.protected_reasons[run.name])

    def test_all_reports_bundle_binds_every_archived_xml(self) -> None:
        run = self._write_manifest(
            "integration-all-reports",
            suite="integration",
            archived_at=self.now - timedelta(days=90),
        )
        manifest_path = run / "manifest.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        primary = manifest["reports"][0]
        second_class = "example.AdditionalGreenTest"
        second_filename = f"TEST-{second_class}.xml"
        second_content = b"<testsuite name='second'/>\n"
        (run / second_filename).write_bytes(second_content)
        expanded_reports = [
            {
                **primary,
                "skipped": 0,
                "failures": 0,
                "errors": 0,
                "inode": 1,
            },
            {
                "className": second_class,
                "file": second_filename,
                "tests": 1,
                "skipped": 0,
                "failures": 0,
                "errors": 0,
                "mtimeNs": 1,
                "inode": 2,
                "sha256": hashlib.sha256(second_content).hexdigest(),
            },
        ]
        manifest["allReports"] = {
            "requiredGreen": True,
            "reportCount": 2,
            "totals": {
                "tests": 2,
                "skipped": 0,
                "failures": 0,
                "errors": 0,
            },
            "reports": expanded_reports,
        }
        manifest_path.write_text(
            json.dumps(manifest, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        plan = build_retention_plan(self.root, now=self.now)

        self.assertEqual(
            "minimum-per-suite",
            plan.protected_reasons[run.name],
        )

    def test_apply_refuses_platform_without_symlink_safe_recursive_removal(self) -> None:
        for index in range(21):
            self._write_manifest(
                f"rbac-old-{index:02d}",
                suite="rbac",
                archived_at=self.now - timedelta(days=80 - index),
            )
        plan = build_retention_plan(self.root, now=self.now)

        with patch("qa_evidence_retention.shutil.rmtree.avoids_symlink_attacks", False):
            with self.assertRaisesRegex(RetentionValidationError, "symlink-safe"):
                apply_retention_plan(plan)

        self.assertTrue((self.root / plan.delete_run_ids[0]).is_dir())

    def test_root_must_be_absolute_owned_secure_directory_and_not_symlink(self) -> None:
        relative = Path("relative-evidence")
        with self.assertRaisesRegex(RetentionValidationError, "absolute"):
            build_retention_plan(relative, now=self.now)

        linked_root = Path(self.temp_dir.name) / "linked-root"
        linked_root.symlink_to(self.root, target_is_directory=True)
        with self.assertRaisesRegex(RetentionValidationError, "symlink"):
            build_retention_plan(linked_root, now=self.now)

        os.chmod(self.root, 0o777)
        with self.assertRaisesRegex(RetentionValidationError, "permissions"):
            build_retention_plan(self.root, now=self.now)

    def _write_manifest(
        self,
        run_id: str,
        *,
        suite: str,
        archived_at: datetime,
        retention: str = "local",
        status: str = "passed",
        include_retention: bool = True,
        schema_version: int = 2,
    ) -> Path:
        run_dir = self.root / run_id
        run_dir.mkdir()
        manifest = {
            "schemaVersion": schema_version,
            "status": status,
            "suite": suite,
            "runId": run_id,
            "startedAtEpoch": int(archived_at.timestamp()) - 60,
            "archivedAtUtc": archived_at.isoformat(),
            "eligibleForRelease": False,
            "source": {
                "gitSha": "a" * 40,
                "gitTree": "b" * 40,
                "runStartGitSha": "a" * 40,
                "runStartGitTree": "b" * 40,
                "clean": True,
                "dirtyPathCount": 0,
                "cleanRequired": False,
            },
        }
        if include_retention:
            manifest["retentionClass"] = retention
        if status == "passed":
            class_name = "example.RetentionEvidenceTest"
            filename = f"TEST-{class_name}.xml"
            report_content = b"<testsuite/>\n"
            if suite == "integration":
                command_profile = "maven-integration-focused"
                environment = {
                    "scope": "focused",
                    "testcontainersApiVersion": "1.44",
                    "dockerMinApiVersion": "1.44",
                    "dockerMaxApiVersion": "1.53",
                    "dockerEndpointTransport": "unix",
                    "mavenRcSkipped": True,
                    "userHomeIsolated": True,
                }
            else:
                command_profile = "maven-rbac-suite"
                environment = {"userHomeIsolated": False}
            manifest.update({
                "commandProfile": command_profile,
                "environment": environment,
                "totals": {
                    "tests": 1,
                    "skipped": 0,
                    "failures": 0,
                    "errors": 0,
                },
                "reports": [{
                    "className": class_name,
                    "file": filename,
                    "tests": 1,
                    "mtimeNs": 1,
                    "sha256": hashlib.sha256(report_content).hexdigest(),
                }],
            })
            (run_dir / filename).write_bytes(report_content)
        elif status == "failed":
            manifest["failure"] = {"stage": "maven", "exitCode": 1}
        (run_dir / "manifest.json").write_text(
            json.dumps(manifest, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        return run_dir


if __name__ == "__main__":
    unittest.main()
