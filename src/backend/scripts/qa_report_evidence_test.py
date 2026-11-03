#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import io
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
from contextlib import redirect_stderr, redirect_stdout
from dataclasses import replace
from pathlib import Path
from unittest.mock import patch

import qa_report_evidence as evidence_module
from qa_report_evidence import (
    EvidenceRequest,
    EvidenceValidationError,
    FailureEvidenceRequest,
    SourceState,
    _parse_metadata,
    finalize_success_evidence,
    main,
    prepare_success_evidence,
    record_failure_diagnostic,
    validate_and_archive,
)


class QaReportEvidenceTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.reports = self.root / "reports"
        self.reports.mkdir()
        self.evidence = self.root / "evidence" / "run-1"
        self.started_epoch = 1_700_000_000
        self.source = SourceState("a" * 40, "b" * 40, 0)

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def test_capture_source_state_uses_raw_porcelain_and_sanitized_git_environment(
            self) -> None:
        porcelain = b" M tracked.txt\n M trailing-space.txt \n"
        observed_calls: list[tuple[list[str], dict[str, object]]] = []

        def fake_run(command: list[str], **kwargs: object) -> subprocess.CompletedProcess:
            observed_calls.append((command, kwargs))
            git_arguments = command[command.index("-C") + 2:]
            if git_arguments == ["rev-parse", "--show-toplevel"]:
                stdout = os.fsencode(str(self.root.resolve())) + b"\n"
            elif git_arguments == ["rev-parse", "--verify", "HEAD"]:
                stdout = ("a" * 40 + "\n").encode("ascii")
            elif git_arguments == ["rev-parse", "--verify", "HEAD^{tree}"]:
                stdout = ("b" * 40 + "\n").encode("ascii")
            elif git_arguments[0] == "status":
                stdout = porcelain
            else:
                stdout = b""
            return subprocess.CompletedProcess(command, 0, stdout=stdout, stderr=b"")

        hostile_git_environment = {
            "GIT_DIR": "/attacker/repository.git",
            "GIT_WORK_TREE": "/attacker/worktree",
            "GIT_INDEX_FILE": "/attacker/index",
            "GIT_OBJECT_DIRECTORY": "/attacker/objects",
            "GIT_CONFIG_COUNT": "1",
            "GIT_NO_REPLACE_OBJECTS": "0",
        }
        with patch.dict(os.environ, hostile_git_environment, clear=False):
            with patch.object(
                evidence_module.shutil,
                "which",
                return_value=sys.executable,
            ):
                with patch.object(
                    evidence_module.subprocess,
                    "run",
                    side_effect=fake_run,
                ):
                    source = evidence_module.capture_source_state(self.root)

        self.assertEqual(2, source.dirty_path_count)
        self.assertEqual("a" * 40, source.git_sha)
        self.assertEqual("b" * 40, source.git_tree)
        self.assertRegex(source.dirty_state_sha256, r"^[0-9a-f]{64}$")
        self.assertGreaterEqual(len(observed_calls), 14)
        for command, kwargs in observed_calls:
            self.assertEqual(self.root.resolve(), Path(str(kwargs["cwd"])))
            self.assertIn(str(self.root.resolve()), command)
            environment = kwargs["env"]
            self.assertIsInstance(environment, dict)
            for key, hostile_value in hostile_git_environment.items():
                if key in evidence_module.CONTROLLED_GIT_ENVIRONMENT:
                    self.assertEqual(
                        evidence_module.CONTROLLED_GIT_ENVIRONMENT[key],
                        environment.get(key),
                    )
                    self.assertNotEqual(hostile_value, environment.get(key))
                else:
                    self.assertNotIn(key, environment)
            self.assertNotIn("text", kwargs)

    def test_capture_source_state_ignores_alternate_repository_git_variables(self) -> None:
        primary = self.root / "primary"
        alternate = self.root / "alternate"
        self._initialize_git_repository(primary, b"primary\n")
        self._initialize_git_repository(alternate, b"alternate\n")
        primary_sha = self._git(primary, "rev-parse", "HEAD")
        alternate_sha = self._git(alternate, "rev-parse", "HEAD")
        self.assertNotEqual(primary_sha, alternate_sha)

        hostile = {
            "GIT_DIR": str(alternate / ".git"),
            "GIT_WORK_TREE": str(alternate),
            "GIT_INDEX_FILE": str(alternate / ".git" / "index"),
            "GIT_OBJECT_DIRECTORY": str(alternate / ".git" / "objects"),
            "GIT_ALTERNATE_OBJECT_DIRECTORIES": str(primary / ".git" / "objects"),
            "GIT_CONFIG_GLOBAL": str(alternate / ".git" / "config"),
        }
        with patch.dict(os.environ, hostile, clear=False):
            source = evidence_module.capture_source_state(primary)

        self.assertEqual(primary_sha, source.git_sha)
        self.assertNotEqual(alternate_sha, source.git_sha)

    def test_capture_source_state_rejects_parent_repository_discovery(self) -> None:
        repository = self.root / "repository"
        self._initialize_git_repository(repository, b"tracked\n")
        nested = repository / "not-the-root"
        nested.mkdir()

        with self.assertRaisesRegex(
            EvidenceValidationError,
            "top-level does not match",
        ):
            evidence_module.capture_source_state(nested)

    def test_capture_source_state_accepts_symlinked_root_and_linked_worktree(self) -> None:
        repository = self.root / "repository"
        self._initialize_git_repository(repository, b"tracked\n")
        symlink = self.root / "repository-link"
        symlink.symlink_to(repository, target_is_directory=True)

        direct = evidence_module.capture_source_state(repository)
        through_symlink = evidence_module.capture_source_state(symlink)
        self.assertEqual(direct, through_symlink)

        linked = self.root / "linked-worktree"
        self._git(
            repository,
            "worktree",
            "add",
            "-q",
            "-b",
            "evidence-linked-worktree",
            str(linked),
        )
        linked_state = evidence_module.capture_source_state(linked)
        self.assertEqual(direct.git_tree, linked_state.git_tree)
        self.assertEqual(0, linked_state.dirty_path_count)

    def test_dirty_fingerprint_covers_staged_worktree_and_untracked_bytes(self) -> None:
        repository = self.root / "repository"
        tracked = self._initialize_git_repository(repository, b"original\n")

        tracked.write_bytes(b"worktree-one\n")
        worktree_one = evidence_module.capture_source_state(repository)
        tracked.write_bytes(b"worktree-two\n")
        worktree_two = evidence_module.capture_source_state(repository)
        self.assertEqual(worktree_one.dirty_path_count, worktree_two.dirty_path_count)
        self.assertNotEqual(
            worktree_one.dirty_state_sha256,
            worktree_two.dirty_state_sha256,
        )

        self._git(repository, "add", "tracked.txt")
        staged_one = evidence_module.capture_source_state(repository)
        tracked.write_bytes(b"staged-two\n")
        self._git(repository, "add", "tracked.txt")
        staged_two = evidence_module.capture_source_state(repository)
        self.assertEqual(staged_one.dirty_path_count, staged_two.dirty_path_count)
        self.assertNotEqual(staged_one.dirty_state_sha256, staged_two.dirty_state_sha256)

        self._git(repository, "reset", "--hard", "-q", "HEAD")
        untracked = repository / "untracked.txt"
        untracked.write_bytes(b"untracked-one\n")
        untracked_one = evidence_module.capture_source_state(repository)
        untracked.write_bytes(b"untracked-two\n")
        untracked_two = evidence_module.capture_source_state(repository)
        self.assertEqual(untracked_one.dirty_path_count, untracked_two.dirty_path_count)
        self.assertNotEqual(
            untracked_one.dirty_state_sha256,
            untracked_two.dirty_state_sha256,
        )

    def test_archives_exact_sanitized_bytes_and_manifest_atomically(self) -> None:
        first = self._write_report("example.FirstTest", 2)
        second = self._write_report("example.SecondTest", 1)

        manifest = validate_and_archive(self._request({
            "example.FirstTest": 2,
            "example.SecondTest": 1,
        }))

        self.assertEqual(3, manifest["totals"]["tests"])
        self.assertEqual("a" * 40, manifest["source"]["gitSha"])
        self.assertTrue(manifest["source"]["clean"])
        archived_first = (self.evidence / "TEST-example.FirstTest.xml").read_bytes()
        archived_second = (self.evidence / "TEST-example.SecondTest.xml").read_bytes()
        self.assertNotEqual(first, archived_first)
        self.assertNotEqual(second, archived_second)
        self._assert_minimal_sanitized_report(archived_first, "example.FirstTest", 2)
        self._assert_minimal_sanitized_report(archived_second, "example.SecondTest", 1)
        persisted = json.loads((self.evidence / "manifest.json").read_text(encoding="utf-8"))
        self.assertEqual("run-1", persisted["runId"])
        self.assertEqual(
            hashlib.sha256(archived_first).hexdigest(),
            persisted["reports"][0]["sha256"],
        )
        self.assertFalse(any(path.name.startswith(".run-1.") for path in self.evidence.parent.iterdir()))

    def test_canonical_bundle_contains_no_surefire_properties_or_output_secrets(
            self) -> None:
        sentinel = b"VR_SECRET_SENTINEL_7f90c2"
        secret_text = sentinel.decode("ascii")
        original = self._write_report(
            "example.SecurityTest",
            1,
            user_home="/private/" + secret_text,
        )
        contaminated = original.replace(
            b"</properties>",
            b'<property name="user.dir" value="VR_SECRET_SENTINEL_7f90c2" />'
            b'<property name="java.class.path" value="VR_SECRET_SENTINEL_7f90c2" />'
            b"</properties>",
        ).replace(
            b"</testsuite>",
            b"<system-out>VR_SECRET_SENTINEL_7f90c2</system-out>"
            b"<system-err>VR_SECRET_SENTINEL_7f90c2</system-err>"
            b"</testsuite>",
        ).replace(
            b'name="case-0" classname="example.SecurityTest"',
            b'name="VR_SECRET_SENTINEL_7f90c2" '
            b'classname="VR_SECRET_SENTINEL_7f90c2"',
        )
        report_path = self.reports / "TEST-example.SecurityTest.xml"
        report_path.write_bytes(contaminated)
        os.utime(
            report_path,
            (self.started_epoch + 1, self.started_epoch + 1),
        )

        request = replace(
            self._request({"example.SecurityTest": 1}),
            expected_user_home="/private/" + secret_text,
        )
        manifest = validate_and_archive(request)

        archived_report = (
            self.evidence / "TEST-example.SecurityTest.xml"
        ).read_bytes()
        serialized_bundle = b"".join(
            path.read_bytes() for path in sorted(self.evidence.iterdir())
        )
        self.assertNotIn(sentinel, serialized_bundle)
        self.assertNotIn(b"<properties", archived_report)
        self.assertNotIn(b"<system-out", archived_report)
        self.assertNotIn(b"<system-err", archived_report)
        self.assertNotIn(b"java.class.path", archived_report)
        self.assertNotIn(b"classname=", archived_report)
        self.assertNotIn(b"name=\"VR_SECRET", archived_report)
        self.assertNotIn(b'"command"', serialized_bundle)
        self.assertNotIn(b'"userHome"', serialized_bundle)
        self.assertEqual("maven-rbac-suite", manifest["commandProfile"])
        self.assertTrue(manifest["environment"]["userHomeIsolated"])
        self.assertEqual(
            hashlib.sha256(archived_report).hexdigest(),
            manifest["reports"][0]["sha256"],
        )
        self._assert_minimal_sanitized_report(
            archived_report,
            "example.SecurityTest",
            1,
        )

    def test_success_manifest_has_explicit_local_retention_and_never_implies_release(self) -> None:
        self._write_report("example.SecurityTest", 1)

        manifest = validate_and_archive(self._request({"example.SecurityTest": 1}))

        self.assertEqual(2, manifest["schemaVersion"])
        self.assertEqual("passed", manifest["status"])
        self.assertEqual("local", manifest["retentionClass"])
        self.assertFalse(manifest["eligibleForRelease"])
        self.assertEqual("maven-rbac-suite", manifest["commandProfile"])
        self.assertEqual(
            {"userHomeIsolated": False},
            manifest["environment"],
        )
        self.assertNotIn("command", manifest)

    def test_integration_manifest_publishes_only_allowlisted_execution_metadata(
            self) -> None:
        self._write_report(
            "example.SecurityTest",
            1,
            user_home="/private/isolated-home",
        )
        request = replace(
            self._request({"example.SecurityTest": 1}),
            suite="integration",
            expected_user_home="/private/isolated-home",
            require_all_reports_green=True,
            metadata={
                "scope": "all-tests",
                "testcontainersApiVersion": "1.44",
                "dockerMinApiVersion": "1.24",
                "dockerMaxApiVersion": "1.51",
                "dockerEndpointTransport": "unix",
                "mavenHomeIsolated": "true",
                "mavenRcSkipped": "true",
            },
        )

        manifest = validate_and_archive(request)

        self.assertEqual("maven-integration-all-tests", manifest["commandProfile"])
        self.assertEqual(
            {
                "scope": "all-tests",
                "testcontainersApiVersion": "1.44",
                "dockerMinApiVersion": "1.24",
                "dockerMaxApiVersion": "1.51",
                "dockerEndpointTransport": "unix",
                "mavenRcSkipped": True,
                "userHomeIsolated": True,
            },
            manifest["environment"],
        )
        serialized = json.dumps(manifest, sort_keys=True)
        self.assertNotIn("/private/isolated-home", serialized)
        self.assertNotIn("mavenHomeIsolated", serialized)

    def test_integration_metadata_rejects_freeform_values_without_publishing(
            self) -> None:
        sentinel = "VR_METADATA_SECRET_SENTINEL"
        self._write_report(
            "example.SecurityTest",
            1,
            user_home="/private/isolated-home",
        )
        request = replace(
            self._request({"example.SecurityTest": 1}),
            suite="integration",
            expected_user_home="/private/isolated-home",
            metadata={
                "scope": sentinel,
                "testcontainersApiVersion": "1.44",
                "dockerMinApiVersion": "1.24",
                "dockerMaxApiVersion": "1.51",
                "dockerEndpointTransport": "unix",
                "mavenHomeIsolated": "true",
                "mavenRcSkipped": "true",
            },
        )

        with self.assertRaisesRegex(
            EvidenceValidationError,
            "invalid integration metadata: scope",
        ) as raised:
            validate_and_archive(request)

        self.assertNotIn(sentinel, str(raised.exception))
        self.assertFalse(self.evidence.exists())

    def test_prepare_is_nonterminal_private_and_contains_no_pass_payload(self) -> None:
        raw_report = self._write_report("example.SecurityTest", 1)

        manifest = prepare_success_evidence(
            self._request({"example.SecurityTest": 1})
        )

        prepared = self._prepared_dir()
        self.assertFalse(self.evidence.exists())
        self.assertTrue(prepared.is_dir())
        self.assertEqual(0o700, prepared.parent.stat().st_mode & 0o777)
        self.assertEqual("prepared", manifest["status"])
        self.assertFalse(manifest["eligibleForRelease"])
        self.assertRegex(manifest["preparedSourceStateSha256"], r"^[0-9a-f]{64}$")
        persisted = json.loads(
            (prepared / "manifest.json").read_text(encoding="utf-8")
        )
        self.assertEqual("prepared", persisted["status"])
        self.assertFalse(persisted["eligibleForRelease"])
        self.assertEqual(
            {"manifest.json", "TEST-example.SecurityTest.xml"},
            {path.name for path in prepared.iterdir()},
        )
        archived_report = (prepared / "TEST-example.SecurityTest.xml").read_bytes()
        self.assertNotEqual(raw_report, archived_report)
        self._assert_minimal_sanitized_report(
            archived_report,
            "example.SecurityTest",
            1,
        )
        serialized_bundle = b"".join(
            path.read_bytes() for path in sorted(prepared.iterdir())
        )
        self.assertNotIn(b'"status": "passed"', serialized_bundle)
        self.assertNotIn(b'"eligibleForRelease": true', serialized_bundle)

    def test_finalize_commits_passed_manifest_and_uses_only_prepared_report_bytes(self) -> None:
        original = self._write_report("example.SecurityTest", 1)
        prepare_success_evidence(self._request({"example.SecurityTest": 1}))
        prepared_report = (
            self._prepared_dir() / "TEST-example.SecurityTest.xml"
        ).read_bytes()
        (self.reports / "TEST-example.SecurityTest.xml").unlink()

        manifest = finalize_success_evidence(
            **self._finalize_arguments(lambda: self.source)
        )

        self.assertEqual("passed", manifest["status"])
        self.assertFalse(manifest["eligibleForRelease"])
        self.assertNotIn("preparedAtUtc", manifest)
        self.assertNotIn("preparedSourceStateSha256", manifest)
        self.assertFalse(self._prepared_dir().exists())
        self.assertFalse(self._committing_dir().exists())
        self.assertNotEqual(original, prepared_report)
        self.assertEqual(
            prepared_report,
            (self.evidence / "TEST-example.SecurityTest.xml").read_bytes(),
        )
        persisted = json.loads(
            (self.evidence / "manifest.json").read_text(encoding="utf-8")
        )
        self.assertEqual("passed", persisted["status"])

    def test_finalize_rejects_source_drift_between_phases_and_records_only_failure(self) -> None:
        self._write_report("example.SecurityTest", 1)
        prepare_success_evidence(self._request({"example.SecurityTest": 1}))
        drifted = SourceState("c" * 40, "b" * 40, 0, "d" * 64)

        with self.assertRaisesRegex(
            EvidenceValidationError,
            "dirty or drifted.*downgraded to failed",
        ):
            finalize_success_evidence(
                **self._finalize_arguments(lambda: drifted)
            )

        self._assert_failed_only()
        self.assertFalse(self._prepared_dir().exists())

    def test_finalize_accepts_identical_dirty_local_snapshot_but_never_release_eligible(self) -> None:
        dirty = SourceState("a" * 40, "b" * 40, 2, "c" * 64)
        self._write_report("example.SecurityTest", 1)
        request = replace(
            self._request({"example.SecurityTest": 1}),
            source_state=dirty,
        )
        prepare_success_evidence(request)

        manifest = finalize_success_evidence(
            **self._finalize_arguments(lambda: dirty)
        )

        self.assertEqual("passed", manifest["status"])
        self.assertFalse(manifest["source"]["clean"])
        self.assertEqual(2, manifest["source"]["dirtyPathCount"])
        self.assertFalse(manifest["eligibleForRelease"])

    def test_finalize_rejects_same_dirty_count_with_different_porcelain_digest(self) -> None:
        prepared_source = SourceState("a" * 40, "b" * 40, 2, "c" * 64)
        changed_dirty_bytes = SourceState("a" * 40, "b" * 40, 2, "d" * 64)
        self._write_report("example.SecurityTest", 1)
        prepare_success_evidence(replace(
            self._request({"example.SecurityTest": 1}),
            source_state=prepared_source,
        ))

        with self.assertRaisesRegex(EvidenceValidationError, "downgraded to failed"):
            finalize_success_evidence(
                **self._finalize_arguments(lambda: changed_dirty_bytes)
            )

        self._assert_failed_only()

    def test_finalize_rejects_changed_tracked_bytes_with_same_porcelain_and_count(
            self) -> None:
        repository = self.root / "repository"
        tracked = self._initialize_git_repository(repository, b"original\n")
        tracked.write_bytes(b"dirty-one\n")
        prepared_porcelain = self._git(
            repository,
            "status",
            "--porcelain=v1",
            "--untracked-files=all",
        )
        prepared_source = evidence_module.capture_source_state(repository)
        self.assertEqual(1, prepared_source.dirty_path_count)

        self._write_report(
            "example.SecurityTest",
            1,
            git_sha=prepared_source.git_sha,
        )
        request = replace(
            self._request({"example.SecurityTest": 1}),
            source_state=prepared_source,
            expected_git_sha=prepared_source.git_sha,
            expected_git_tree=prepared_source.git_tree,
        )
        prepare_success_evidence(request)

        tracked.write_bytes(b"dirty-two\n")
        changed_porcelain = self._git(
            repository,
            "status",
            "--porcelain=v1",
            "--untracked-files=all",
        )
        changed_source = evidence_module.capture_source_state(repository)
        self.assertEqual(" M tracked.txt", prepared_porcelain)
        self.assertEqual(prepared_porcelain, changed_porcelain)
        self.assertEqual(1, changed_source.dirty_path_count)
        self.assertNotEqual(
            prepared_source.dirty_state_sha256,
            changed_source.dirty_state_sha256,
        )

        with self.assertRaisesRegex(EvidenceValidationError, "downgraded to failed"):
            finalize_success_evidence(
                evidence_dir=self.evidence,
                suite="rbac",
                run_id="run-1",
                started_epoch=self.started_epoch,
                expected_git_sha=prepared_source.git_sha,
                expected_git_tree=prepared_source.git_tree,
                retention_class="local",
                clean_required=False,
                source_state_reader=lambda: evidence_module.capture_source_state(
                    repository
                ),
            )

        self._assert_failed_only()

    def test_failure_replaces_pending_prepared_bundle_with_manifest_only(self) -> None:
        self._write_report("example.SecurityTest", 1)
        prepare_success_evidence(self._request({"example.SecurityTest": 1}))

        manifest = record_failure_diagnostic(self._failure_request())

        self.assertEqual("failed", manifest["status"])
        self._assert_failed_only()
        self.assertFalse(self._prepared_dir().exists())

    def test_failure_replaces_final_prepared_bundle_with_manifest_only(self) -> None:
        self._write_report("example.SecurityTest", 1)
        prepare_success_evidence(self._request({"example.SecurityTest": 1}))
        self.evidence.parent.mkdir(parents=True, exist_ok=True)
        os.replace(self._prepared_dir(), self.evidence)

        record_failure_diagnostic(self._failure_request())

        self._assert_failed_only()

    def test_failure_refuses_to_replace_existing_final_passed_bundle(self) -> None:
        self._write_report("example.SecurityTest", 1)
        original = validate_and_archive(self._request({"example.SecurityTest": 1}))

        with self.assertRaisesRegex(
            EvidenceValidationError,
            "not safely replaceable",
        ):
            record_failure_diagnostic(self._failure_request())

        persisted = json.loads(
            (self.evidence / "manifest.json").read_text(encoding="utf-8")
        )
        self.assertEqual(original, persisted)
        self.assertEqual("passed", persisted["status"])

    def test_precommit_source_drift_never_publishes_passed_manifest(self) -> None:
        self._write_report("example.SecurityTest", 1)
        prepare_success_evidence(self._request({"example.SecurityTest": 1}))
        drifted = SourceState("a" * 40, "c" * 40, 0, "d" * 64)
        states = iter((self.source, self.source, drifted))
        observed_canonical_statuses: list[str] = []

        def read_source() -> SourceState:
            state = next(states)
            if state is drifted:
                observed_canonical_statuses.append(json.loads(
                    (self.evidence / "manifest.json").read_text(encoding="utf-8")
                )["status"])
            return state

        with self.assertRaisesRegex(
            EvidenceValidationError,
            "before terminal PASS commit.*downgraded to failed",
        ):
            finalize_success_evidence(
                **self._finalize_arguments(read_source)
            )

        self.assertEqual(["prepared"], observed_canonical_statuses)
        self._assert_failed_only()

    def test_sigkill_equivalent_between_canonical_move_and_commit_exposes_no_pass(self) -> None:
        class SimulatedSigkill(BaseException):
            pass

        self._write_report("example.SecurityTest", 1)
        prepare_success_evidence(self._request({"example.SecurityTest": 1}))
        calls = 0

        def read_source() -> SourceState:
            nonlocal calls
            calls += 1
            if calls == 3:
                persisted = json.loads(
                    (self.evidence / "manifest.json").read_text(encoding="utf-8")
                )
                self.assertEqual("prepared", persisted["status"])
                self.assertFalse(persisted["eligibleForRelease"])
                self.assertTrue(self._passed_candidate().is_file())
                raise SimulatedSigkill()
            return self.source

        with self.assertRaises(SimulatedSigkill):
            finalize_success_evidence(
                **self._finalize_arguments(read_source)
            )

        persisted_bytes = (self.evidence / "manifest.json").read_bytes()
        self.assertIn(b'"status": "prepared"', persisted_bytes)
        self.assertNotIn(b'"status": "passed"', persisted_bytes)
        self.assertNotIn(b'"eligibleForRelease": true', persisted_bytes)

        # The ordinary failure path deterministically removes the outside
        # candidate left by an uncatchable interruption and finalizes FAILED.
        record_failure_diagnostic(self._failure_request())
        self.assertFalse(self._passed_candidate().exists())
        self._assert_failed_only()

    def test_prepared_manifest_symlink_fails_closed_without_publishing_final(self) -> None:
        self._write_report("example.SecurityTest", 1)
        prepare_success_evidence(self._request({"example.SecurityTest": 1}))
        manifest_path = self._prepared_dir() / "manifest.json"
        outside = self.root / "outside.json"
        outside.write_text('{}\n', encoding="utf-8")
        manifest_path.unlink()
        manifest_path.symlink_to(outside)

        with self.assertRaisesRegex(EvidenceValidationError, "safe evidence file"):
            finalize_success_evidence(
                **self._finalize_arguments(lambda: self.source)
            )

        self.assertFalse(self.evidence.exists())

    def test_success_cli_requires_phase_and_prepare_finalize_are_explicit(self) -> None:
        self._write_report("example.SecurityTest", 1)
        common = [
            "--suite", "rbac",
            "--run-id", "run-1",
            "--started-epoch", str(self.started_epoch),
            "--evidence-dir", str(self.evidence),
            "--repo-root", str(self.root),
            "--expected-git-sha", "a" * 40,
            "--expected-git-tree", "b" * 40,
        ]
        stderr = io.StringIO()
        with redirect_stderr(stderr):
            missing_phase = main(common)
        self.assertEqual(1, missing_phase)
        self.assertIn("requires explicit --phase", stderr.getvalue())

        prepare_args = [
            *common,
            "--phase", "prepare",
            "--reports-dir", str(self.reports),
            "--expected", "example.SecurityTest=1",
        ]
        stdout = io.StringIO()
        with redirect_stdout(stdout):
            with patch.object(
                evidence_module,
                "capture_source_state",
                return_value=self.source,
            ):
                self.assertEqual(0, main(prepare_args))
            with patch.object(
                evidence_module,
                "capture_source_state",
                side_effect=(self.source, self.source, self.source),
            ):
                self.assertEqual(0, main([*common, "--phase", "finalize"]))
        self.assertNotIn(str(self.root), stdout.getvalue())
        self.assertEqual(
            "passed",
            json.loads(
                (self.evidence / "manifest.json").read_text(encoding="utf-8")
            )["status"],
        )

    def test_release_success_is_eligible_only_when_clean_was_required(self) -> None:
        self._write_report("example.SecurityTest", 1)
        request = replace(
            self._request({"example.SecurityTest": 1}),
            require_clean=True,
            require_all_reports_green=True,
            retention_class="release",
        )

        manifest = validate_and_archive(request)

        self.assertEqual("passed", manifest["status"])
        self.assertEqual("release", manifest["retentionClass"])
        self.assertTrue(manifest["eligibleForRelease"])

    def test_release_focused_success_is_archived_but_not_release_eligible(self) -> None:
        self._write_report("example.SecurityTest", 1)
        request = replace(
            self._request({"example.SecurityTest": 1}),
            require_clean=True,
            retention_class="release",
        )

        manifest = validate_and_archive(request)

        self.assertEqual("passed", manifest["status"])
        self.assertFalse(manifest["eligibleForRelease"])

    def test_release_success_rejects_missing_clean_requirement(self) -> None:
        self._write_report("example.SecurityTest", 1)
        request = replace(
            self._request({"example.SecurityTest": 1}),
            retention_class="release",
        )

        with self.assertRaisesRegex(
            EvidenceValidationError,
            "release retention requires clean source enforcement",
        ):
            validate_and_archive(request)
        self.assertFalse(self.evidence.exists())

    def test_failure_diagnostic_is_atomic_minimal_and_never_release_eligible(self) -> None:
        request = FailureEvidenceRequest(
            suite="integration",
            run_id="integration-run-1",
            started_epoch=self.started_epoch,
            evidence_dir=self.root / "evidence" / "integration-run-1",
            source_state=self.source,
            clean_required=True,
            failure_stage="maven",
            exit_code=17,
            retention_class="release",
            expected_git_sha="a" * 40,
            expected_git_tree="b" * 40,
        )

        manifest = record_failure_diagnostic(request)

        self.assertEqual(
            {
                "schemaVersion",
                "status",
                "suite",
                "runId",
                "startedAtEpoch",
                "archivedAtUtc",
                "retentionClass",
                "eligibleForRelease",
                "source",
                "failure",
            },
            set(manifest),
        )
        self.assertEqual("failed", manifest["status"])
        self.assertEqual("release", manifest["retentionClass"])
        self.assertFalse(manifest["eligibleForRelease"])
        self.assertEqual({"stage": "maven", "exitCode": 17}, manifest["failure"])
        self.assertEqual(
            {
                "gitSha": "a" * 40,
                "gitTree": "b" * 40,
                "runStartGitSha": "a" * 40,
                "runStartGitTree": "b" * 40,
                "clean": True,
                "dirtyPathCount": 0,
                "cleanRequired": True,
            },
            manifest["source"],
        )
        persisted = json.loads(
            (request.evidence_dir / "manifest.json").read_text(encoding="utf-8")
        )
        self.assertEqual(manifest, persisted)
        serialized = json.dumps(persisted, sort_keys=True)
        for forbidden in ("command", "environment", "token", "credential", str(self.root)):
            self.assertNotIn(forbidden, serialized)
        self.assertFalse(
            any(
                path.name.startswith(".integration-run-1.")
                for path in request.evidence_dir.parent.iterdir()
            )
        )

    def test_each_frozen_failure_stage_is_supported(self) -> None:
        for index, stage in enumerate(("preflight", "maven", "validation", "signal"), start=1):
            with self.subTest(stage=stage):
                evidence_dir = self.root / "evidence" / f"failed-{stage}"
                manifest = record_failure_diagnostic(FailureEvidenceRequest(
                    suite="integration",
                    run_id=f"failed-{stage}",
                    started_epoch=self.started_epoch,
                    evidence_dir=evidence_dir,
                    source_state=self.source,
                    clean_required=False,
                    failure_stage=stage,
                    exit_code=120 + index,
                    retention_class="local",
                    expected_git_sha="a" * 40,
                    expected_git_tree="b" * 40,
                ))

                self.assertEqual(stage, manifest["failure"]["stage"])
                self.assertFalse(manifest["eligibleForRelease"])

    def test_failure_diagnostic_rejects_unsafe_stage_and_nonfailure_exit_code(self) -> None:
        base = FailureEvidenceRequest(
            suite="integration",
            run_id="failed-run",
            started_epoch=self.started_epoch,
            evidence_dir=self.root / "evidence" / "failed-run",
            source_state=self.source,
            clean_required=False,
            failure_stage="maven",
            exit_code=1,
            retention_class="local",
            expected_git_sha="a" * 40,
            expected_git_tree="b" * 40,
        )
        cases = (
            (replace(base, failure_stage="post deploy"), "invalid failure stage"),
            (replace(base, exit_code=0), "failure exit code"),
        )

        for request, message in cases:
            with self.subTest(message=message):
                with self.assertRaisesRegex(EvidenceValidationError, message):
                    record_failure_diagnostic(request)
                self.assertFalse(request.evidence_dir.exists())

    def test_failure_diagnostic_records_source_drift_without_becoming_release_evidence(self) -> None:
        request = FailureEvidenceRequest(
            suite="integration",
            run_id="failed-drift",
            started_epoch=self.started_epoch,
            evidence_dir=self.root / "evidence" / "failed-drift",
            source_state=self.source,
            clean_required=True,
            failure_stage="validation",
            exit_code=1,
            retention_class="release",
            expected_git_sha="c" * 40,
            expected_git_tree="d" * 40,
        )

        manifest = record_failure_diagnostic(request)

        self.assertFalse(manifest["eligibleForRelease"])
        self.assertEqual("a" * 40, manifest["source"]["gitSha"])
        self.assertEqual("b" * 40, manifest["source"]["gitTree"])
        self.assertEqual("c" * 40, manifest["source"]["runStartGitSha"])
        self.assertEqual("d" * 40, manifest["source"]["runStartGitTree"])

    def test_failure_cli_accepts_only_minimal_inputs_and_does_not_print_private_path(self) -> None:
        evidence_dir = self.root / "private-parent" / "failed-cli"
        arguments = [
            "--suite", "integration",
            "--run-id", "failed-cli",
            "--started-epoch", str(self.started_epoch),
            "--evidence-dir", str(evidence_dir),
            "--repo-root", str(self.root),
            "--expected-git-sha", "a" * 40,
            "--expected-git-tree", "b" * 40,
            "--retention-class", "ci",
            "--failure-stage", "preflight",
            "--exit-code", "2",
        ]
        stdout = io.StringIO()
        with patch.object(evidence_module, "capture_source_state", return_value=self.source):
            with redirect_stdout(stdout):
                exit_code = main(arguments)

        self.assertEqual(0, exit_code)
        self.assertTrue((evidence_dir / "manifest.json").is_file())
        self.assertIn("status", json.loads((evidence_dir / "manifest.json").read_text()))
        self.assertNotIn(str(self.root), stdout.getvalue())

        rejected_dir = self.root / "private-parent" / "rejected-cli"
        rejected_arguments = list(arguments)
        rejected_arguments[rejected_arguments.index("--evidence-dir") + 1] = str(
            rejected_dir
        )
        stderr = io.StringIO()
        with patch.object(evidence_module, "capture_source_state", return_value=self.source):
            with redirect_stderr(stderr):
                rejected_code = main([
                    *rejected_arguments,
                    "--metadata", "scope=secret-bearing-command",
                ])
        self.assertEqual(1, rejected_code)
        self.assertIn("reject non-minimal inputs: --metadata", stderr.getvalue())
        self.assertNotIn("secret-bearing-command", stderr.getvalue())
        self.assertFalse(rejected_dir.exists())

    def test_rejects_report_from_another_run_without_creating_evidence(self) -> None:
        self._write_report("example.SecurityTest", 1, run_id="other-run")

        self._assert_rejected(
            {"example.SecurityTest": 1},
            "run-id provenance mismatch",
        )

    def test_raw_surefire_provenance_values_are_never_echoed(self) -> None:
        sentinel = "VR_RAW_XML_SECRET_SENTINEL"
        mutations = (
            (
                b'value="run-1"',
                f'value="{sentinel}"'.encode("ascii"),
                "run-id provenance mismatch",
            ),
            (
                f'value="{"a" * 40}"'.encode("ascii"),
                f'value="{sentinel}"'.encode("ascii"),
                "git-sha provenance mismatch",
            ),
            (
                b'name="example.SecurityTest"',
                f'name="{sentinel}"'.encode("ascii"),
                "testsuite name mismatch",
            ),
            (
                b'tests="1"',
                f'tests="{sentinel}"'.encode("ascii"),
                "invalid tests attribute",
            ),
        )
        for marker, replacement, expected_message in mutations:
            with self.subTest(expected_message=expected_message):
                self._reset_reports()
                raw = self._write_report("example.SecurityTest", 1)
                path = self.reports / "TEST-example.SecurityTest.xml"
                path.write_bytes(raw.replace(marker, replacement, 1))
                os.utime(path, (self.started_epoch + 1, self.started_epoch + 1))

                with self.assertRaisesRegex(
                    EvidenceValidationError,
                    expected_message,
                ) as raised:
                    validate_and_archive(
                        self._request({"example.SecurityTest": 1})
                    )

                self.assertNotIn(sentinel, str(raised.exception))
                self.assertFalse(self.evidence.exists())

    def test_duplicate_critical_provenance_properties_fail_closed(self) -> None:
        sentinel = "VR_DUPLICATE_PROPERTY_SENTINEL"
        critical = (
            ("vanrides.qa.run-id", "run-1", "run-id"),
            ("vanrides.qa.git-sha", "a" * 40, "git-sha"),
            ("user.home", "/same-home", "user.home"),
        )
        for name, expected_value, safe_label in critical:
            for placement in ("first", "last"):
                with self.subTest(name=name, placement=placement):
                    self._reset_reports()
                    raw = self._write_report("example.SecurityTest", 1)
                    marker = (
                        f'<property name="{name}" value="{expected_value}" />'
                    ).encode("utf-8")
                    duplicate = (
                        f'<property name="{name}" value="{sentinel}" />'
                    ).encode("utf-8")
                    replacement = (
                        duplicate + marker if placement == "first"
                        else marker + duplicate
                    )
                    path = self.reports / "TEST-example.SecurityTest.xml"
                    path.write_bytes(raw.replace(marker, replacement, 1))
                    os.utime(
                        path,
                        (self.started_epoch + 1, self.started_epoch + 1),
                    )

                    with self.assertRaisesRegex(
                        EvidenceValidationError,
                        f"{re.escape(safe_label)} property count mismatch",
                    ) as raised:
                        validate_and_archive(
                            self._request({"example.SecurityTest": 1})
                        )

                    self.assertNotIn(sentinel, str(raised.exception))
                    self.assertFalse(self.evidence.exists())

    def test_rejects_stale_report(self) -> None:
        self._write_report(
            "example.SecurityTest",
            1,
            mtime_epoch=self.started_epoch - 1,
        )

        self._assert_rejected({"example.SecurityTest": 1}, "predates this run")

    def test_rejects_mixed_user_home_values(self) -> None:
        self._write_report("example.FirstTest", 1, user_home="/one")
        self._write_report("example.SecondTest", 1, user_home="/two")

        self._assert_rejected(
            {"example.FirstTest": 1, "example.SecondTest": 1},
            "mixed user.home values",
        )

    def test_rejects_user_home_different_from_explicit_expected_home(self) -> None:
        self._write_report("example.SecurityTest", 1, user_home="/global-home")
        request = replace(
            self._request({"example.SecurityTest": 1}),
            expected_user_home="/isolated-home",
        )

        with self.assertRaisesRegex(
            EvidenceValidationError,
            r"user\.home does not match the isolated expected value",
        ) as raised:
            validate_and_archive(request)
        self.assertNotIn("/isolated-home", str(raised.exception))
        self.assertNotIn("/global-home", str(raised.exception))
        self.assertFalse(self.evidence.exists())

    def test_records_only_isolation_boolean_after_matching_expected_home(self) -> None:
        self._write_report("example.SecurityTest", 1, user_home="/isolated-home")
        request = replace(
            self._request({"example.SecurityTest": 1}),
            expected_user_home="/isolated-home",
        )

        manifest = validate_and_archive(request)

        self.assertEqual(
            {"userHomeIsolated": True},
            manifest["environment"],
        )
        self.assertNotIn("/isolated-home", json.dumps(manifest))

    def test_rejects_wrong_attribute_or_testcase_count(self) -> None:
        self._write_report("example.SecurityTest", 1, tests_attribute=2)

        self._assert_rejected(
            {"example.SecurityTest": 1},
            "expected tests=1, attribute=2, testcases=1",
        )

    def test_rejects_skips_failures_and_errors(self) -> None:
        for field in ("skipped", "failures", "errors"):
            with self.subTest(field=field):
                self._reset_reports()
                self._write_report("example.SecurityTest", 1, **{field: 1})
                self._assert_rejected({"example.SecurityTest": 1}, "suite is not green")

    def test_all_report_scan_rejects_skipped_unexpected_class(self) -> None:
        self._write_report("example.SecurityTest", 1)
        self._write_report("example.FutureDockerTest", 1, skipped=1)
        request = replace(
            self._request({"example.SecurityTest": 1}),
            require_all_reports_green=True,
        )

        with self.assertRaisesRegex(
            EvidenceValidationError,
            "all-report scan is not green.*FutureDockerTest.*skipped=1",
        ):
            validate_and_archive(request)
        self.assertFalse(self.evidence.exists())

    def test_all_report_scan_rejects_hidden_child_outcome_with_zero_attribute(self) -> None:
        self._write_report("example.SecurityTest", 1)
        for field in ("skipped", "failure", "error"):
            with self.subTest(field=field):
                self._reset_reports()
                if self.evidence.exists():
                    shutil.rmtree(self.evidence)
                self._write_report("example.SecurityTest", 1)
                keyword = {
                    "skipped": "skipped_elements",
                    "failure": "failure_elements",
                    "error": "error_elements",
                }[field]
                self._write_report(
                    "example.FutureDockerTest",
                    1,
                    **{keyword: 1},
                )
                request = replace(
                    self._request({"example.SecurityTest": 1}),
                    require_all_reports_green=True,
                )

                with self.assertRaisesRegex(
                    EvidenceValidationError,
                    f"FutureDockerTest.*{field}.*attribute=0.*elements=1",
                ):
                    validate_and_archive(request)
                self.assertFalse(self.evidence.exists())

    def test_all_report_scan_rejects_negative_totals(self) -> None:
        self._write_report("example.SecurityTest", 1)
        self._write_report("example.FutureDockerTest", 1, skipped=-1)
        request = replace(
            self._request({"example.SecurityTest": 1}),
            require_all_reports_green=True,
        )

        with self.assertRaisesRegex(EvidenceValidationError, "negative skipped"):
            validate_and_archive(request)
        self.assertFalse(self.evidence.exists())

    def test_all_report_scan_rejects_stale_unexpected_class(self) -> None:
        self._write_report("example.SecurityTest", 1)
        self._write_report(
            "example.FutureDockerTest",
            1,
            mtime_epoch=self.started_epoch - 1,
        )
        request = replace(
            self._request({"example.SecurityTest": 1}),
            require_all_reports_green=True,
        )

        with self.assertRaisesRegex(EvidenceValidationError, "predates this run"):
            validate_and_archive(request)
        self.assertFalse(self.evidence.exists())

    def test_all_report_scan_rejects_unexpected_class_from_another_run(self) -> None:
        self._write_report("example.SecurityTest", 1)
        self._write_report("example.FutureDockerTest", 1, run_id="other-run")
        request = replace(
            self._request({"example.SecurityTest": 1}),
            require_all_reports_green=True,
        )

        with self.assertRaisesRegex(
            EvidenceValidationError,
            "FutureDockerTest.*run-id provenance mismatch",
        ):
            validate_and_archive(request)
        self.assertFalse(self.evidence.exists())

    def test_all_report_scan_records_and_archives_every_validated_report(self) -> None:
        self._write_report("example.SecurityTest", 1)
        extra = self._write_report("example.AnotherGreenTest", 2)
        request = replace(
            self._request({"example.SecurityTest": 1}),
            require_all_reports_green=True,
        )

        manifest = validate_and_archive(request)

        self.assertEqual(2, manifest["allReports"]["reportCount"])
        self.assertEqual(3, manifest["allReports"]["totals"]["tests"])
        self.assertEqual(0, manifest["allReports"]["totals"]["skipped"])
        archived_extra = self.evidence / "TEST-example.AnotherGreenTest.xml"
        archived_extra_bytes = archived_extra.read_bytes()
        self.assertNotEqual(extra, archived_extra_bytes)
        self._assert_minimal_sanitized_report(
            archived_extra_bytes,
            "example.AnotherGreenTest",
            2,
        )
        global_reports = {
            report["className"]: report
            for report in manifest["allReports"]["reports"]
        }
        self.assertEqual(
            hashlib.sha256(archived_extra_bytes).hexdigest(),
            global_reports["example.AnotherGreenTest"]["sha256"],
        )

    def test_all_report_scan_rejects_report_added_during_final_revalidation(self) -> None:
        self._write_report("example.SecurityTest", 1)
        request = replace(
            self._request({"example.SecurityTest": 1}),
            require_all_reports_green=True,
        )
        original_assert = evidence_module._assert_report_unchanged
        injected = False

        def assert_and_inject(path: Path, report: object) -> None:
            nonlocal injected
            original_assert(path, report)
            if not injected:
                injected = True
                self._write_report("example.LateSkippedTest", 1, skipped=1)

        with patch.object(
            evidence_module,
            "_assert_report_unchanged",
            side_effect=assert_and_inject,
        ):
            with self.assertRaisesRegex(
                EvidenceValidationError,
                "report set changed during final report revalidation",
            ):
                validate_and_archive(request)
        self.assertFalse(self.evidence.exists())

    def test_rejects_existing_evidence_directory(self) -> None:
        self._write_report("example.SecurityTest", 1)
        self.evidence.mkdir(parents=True)

        with self.assertRaisesRegex(EvidenceValidationError, "evidence directory already exists"):
            validate_and_archive(self._request({"example.SecurityTest": 1}))
        self.assertTrue(self.evidence.exists())

    def test_require_clean_rejects_dirty_source(self) -> None:
        self._write_report("example.SecurityTest", 1)
        request = self._request({"example.SecurityTest": 1})
        dirty_request = replace(
            request,
            source_state=SourceState("a" * 40, "b" * 40, 2),
            require_clean=True,
        )

        with self.assertRaisesRegex(EvidenceValidationError, "clean source required"):
            validate_and_archive(dirty_request)
        self.assertFalse(self.evidence.exists())

    def test_require_clean_accepts_clean_source_and_records_requirement(self) -> None:
        self._write_report("example.SecurityTest", 1)
        clean_request = replace(
            self._request({"example.SecurityTest": 1}),
            require_clean=True,
        )

        manifest = validate_and_archive(clean_request)

        self.assertTrue(manifest["source"]["clean"])
        self.assertTrue(manifest["source"]["cleanRequired"])
        self.assertEqual(0, manifest["source"]["dirtyPathCount"])
        self.assertEqual("a" * 40, manifest["source"]["runStartGitSha"])
        self.assertEqual("b" * 40, manifest["source"]["runStartGitTree"])
        self.assertTrue((self.evidence / "manifest.json").is_file())

    def test_freeform_or_reserved_request_metadata_is_rejected(self) -> None:
        self._write_report("example.SecurityTest", 1, user_home="/validated-home")
        request = replace(
            self._request({"example.SecurityTest": 1}),
            metadata={"userHome": "/forged-home", "profile": "test"},
        )

        with self.assertRaisesRegex(
            EvidenceValidationError,
            "rbac evidence does not accept public metadata",
        ):
            validate_and_archive(request)
        self.assertFalse(self.evidence.exists())

    def test_cli_metadata_rejects_reserved_user_home_key(self) -> None:
        with self.assertRaisesRegex(EvidenceValidationError, "reserved metadata key: userHome"):
            _parse_metadata(["userHome=/forged-home"])
        with self.assertRaisesRegex(
            EvidenceValidationError,
            "reserved metadata key: userHomeIsolated",
        ):
            _parse_metadata(["userHomeIsolated=true"])

    def test_rejects_class_name_that_can_escape_report_directory(self) -> None:
        self._write_report("example.SecurityTest", 1)

        self._assert_rejected(
            {"../../outside.SecurityTest": 1},
            "invalid expected class entry",
        )

    def test_rejects_report_built_for_another_git_sha(self) -> None:
        self._write_report("example.SecurityTest", 1, git_sha="c" * 40)

        self._assert_rejected(
            {"example.SecurityTest": 1},
            "git-sha provenance mismatch",
        )

    def test_rejects_source_change_between_run_start_and_archive(self) -> None:
        self._write_report("example.SecurityTest", 1)
        request = replace(self._request({"example.SecurityTest": 1}), expected_git_sha="c" * 40)

        with self.assertRaisesRegex(EvidenceValidationError, "git SHA changed during evidence run"):
            validate_and_archive(request)
        self.assertFalse(self.evidence.exists())

    def test_rejects_tree_change_between_run_start_and_archive(self) -> None:
        self._write_report("example.SecurityTest", 1)
        request = replace(
            self._request({"example.SecurityTest": 1}),
            expected_git_tree="c" * 40,
        )

        with self.assertRaisesRegex(EvidenceValidationError, "git tree changed during evidence run"):
            validate_and_archive(request)
        self.assertFalse(self.evidence.exists())

    def _prepared_dir(self) -> Path:
        return self.evidence.parent.parent / ".qa-evidence-prepared" / "run-1"

    def _committing_dir(self) -> Path:
        return (
            self.evidence.parent.parent
            / ".qa-evidence-prepared"
            / ".run-1.committing"
        )

    def _passed_candidate(self) -> Path:
        return (
            self.evidence.parent.parent
            / ".qa-evidence-prepared"
            / ".run-1.manifest.passed"
        )

    def _finalize_arguments(
            self,
            source_state_reader: object) -> dict[str, object]:
        return {
            "evidence_dir": self.evidence,
            "suite": "rbac",
            "run_id": "run-1",
            "started_epoch": self.started_epoch,
            "expected_git_sha": "a" * 40,
            "expected_git_tree": "b" * 40,
            "retention_class": "local",
            "clean_required": False,
            "source_state_reader": source_state_reader,
        }

    def _failure_request(self) -> FailureEvidenceRequest:
        return FailureEvidenceRequest(
            suite="rbac",
            run_id="run-1",
            started_epoch=self.started_epoch,
            evidence_dir=self.evidence,
            source_state=self.source,
            clean_required=False,
            failure_stage="validation",
            exit_code=1,
            retention_class="local",
            expected_git_sha="a" * 40,
            expected_git_tree="b" * 40,
        )

    def _assert_failed_only(self) -> None:
        self.assertTrue(self.evidence.is_dir())
        self.assertEqual(
            {"manifest.json"},
            {path.name for path in self.evidence.iterdir()},
        )
        manifest = json.loads(
            (self.evidence / "manifest.json").read_text(encoding="utf-8")
        )
        self.assertEqual("failed", manifest["status"])
        self.assertFalse(manifest["eligibleForRelease"])

    def _assert_minimal_sanitized_report(
            self,
            content: bytes,
            class_name: str,
            testcase_count: int) -> None:
        suite = ET.fromstring(content)
        self.assertEqual("testsuite", suite.tag)
        self.assertEqual(
            {
                "name": class_name,
                "tests": str(testcase_count),
                "skipped": "0",
                "failures": "0",
                "errors": "0",
            },
            suite.attrib,
        )
        self.assertEqual(testcase_count, len(suite.findall("./testcase")))
        self.assertEqual([], suite.findall("./properties"))
        self.assertEqual([], suite.findall(".//system-out"))
        self.assertEqual([], suite.findall(".//system-err"))
        for index, testcase in enumerate(suite.findall("./testcase"), start=1):
            self.assertEqual({"index": str(index)}, testcase.attrib)
            self.assertEqual([], list(testcase))

    def _initialize_git_repository(self, path: Path, content: bytes) -> Path:
        path.mkdir()
        self._git(path, "init", "-q")
        self._git(path, "config", "user.name", "Evidence Test")
        self._git(path, "config", "user.email", "evidence@example.invalid")
        tracked = path / "tracked.txt"
        tracked.write_bytes(content)
        self._git(path, "add", "tracked.txt")
        self._git(path, "commit", "-q", "-m", "test fixture")
        return tracked

    def _git(self, path: Path, *arguments: str) -> str:
        environment = {
            key: value
            for key, value in os.environ.items()
            if not key.startswith("GIT_")
        }
        result = subprocess.run(
            ["git", "-C", str(path), *arguments],
            check=True,
            capture_output=True,
            text=True,
            env=environment,
        )
        return result.stdout.rstrip("\r\n")

    def _request(self, expected: dict[str, int]) -> EvidenceRequest:
        return EvidenceRequest(
            suite="rbac",
            run_id="run-1",
            started_epoch=self.started_epoch,
            reports_dir=self.reports,
            evidence_dir=self.evidence,
            expected_classes=expected,
            source_state=self.source,
            require_clean=False,
            metadata={},
            expected_git_sha="a" * 40,
            expected_git_tree="b" * 40,
        )

    def _assert_rejected(self, expected: dict[str, int], message: str) -> None:
        with self.assertRaisesRegex(EvidenceValidationError, message):
            validate_and_archive(self._request(expected))
        self.assertFalse(self.evidence.exists())

    def _reset_reports(self) -> None:
        for path in self.reports.iterdir():
            path.unlink()

    def _write_report(
            self,
            class_name: str,
            testcase_count: int,
            *,
            run_id: str = "run-1",
            git_sha: str = "a" * 40,
            user_home: str = "/same-home",
            tests_attribute: int | None = None,
            skipped: int = 0,
            failures: int = 0,
            errors: int = 0,
            skipped_elements: int | None = None,
            failure_elements: int | None = None,
            error_elements: int | None = None,
            mtime_epoch: int | None = None) -> bytes:
        tests_attribute = testcase_count if tests_attribute is None else tests_attribute
        skipped_elements = skipped if skipped_elements is None else skipped_elements
        failure_elements = failures if failure_elements is None else failure_elements
        error_elements = errors if error_elements is None else error_elements
        testcases = []
        for index in range(testcase_count):
            outcomes = ""
            if index < skipped_elements:
                outcomes += "<skipped />"
            if index < failure_elements:
                outcomes += "<failure />"
            if index < error_elements:
                outcomes += "<error />"
            if outcomes:
                testcases.append(
                    f'<testcase name="case-{index}" classname="{class_name}" '
                    f'time="0">{outcomes}</testcase>'
                )
            else:
                testcases.append(
                    f'<testcase name="case-{index}" classname="{class_name}" time="0" />'
                )
        content = (
            f'<testsuite name="{class_name}" tests="{tests_attribute}" '
            f'skipped="{skipped}" failures="{failures}" errors="{errors}">'
            "<properties>"
            f'<property name="vanrides.qa.run-id" value="{run_id}" />'
            f'<property name="vanrides.qa.git-sha" value="{git_sha}" />'
            f'<property name="user.home" value="{user_home}" />'
            "</properties>"
            f"{''.join(testcases)}"
            "</testsuite>"
        ).encode("utf-8")
        path = self.reports / f"TEST-{class_name}.xml"
        path.write_bytes(content)
        timestamp = self.started_epoch + 1 if mtime_epoch is None else mtime_epoch
        os.utime(path, (timestamp, timestamp))
        return content


if __name__ == "__main__":
    unittest.main()
